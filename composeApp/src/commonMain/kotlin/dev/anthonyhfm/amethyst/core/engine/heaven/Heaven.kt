package dev.anthonyhfm.amethyst.core.engine.heaven

import dev.anthonyhfm.amethyst.settings.data.GeneralSettings
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.mainDispatcherOrDefault
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.workspace.ViewportRepository
import dev.anthonyhfm.amethyst.core.util.StopWatch
import dev.anthonyhfm.amethyst.core.util.platform
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.roundToLong

data class ScheduledJob(
    val id: String,
    val targetTimeNanos: Long,
    val sequence: Long,
    val job: () -> Unit,
    val owner: Any? = null,
    val identifier: Any? = null,
    val ownerGeneration: Long? = null,
    val identifierGeneration: Long? = null,
    val globalGeneration: Int,
)

object Heaven {
    private sealed interface SchedulerCommand {
        data class Add(val scheduledJob: ScheduledJob) : SchedulerCommand
        data class Cancel(val filter: (ScheduledJob) -> Boolean) : SchedulerCommand
        data object Clear : SchedulerCommand
    }

    private data class JobOwnerKey(
        val owner: Any,
        val identifier: Any?
    )

    private fun Throwable.isRecoverablePlatformInitFailure(): Boolean {
        val typeName = this::class.simpleName.orEmpty()
        return this is IllegalStateException ||
            this is NullPointerException ||
            typeName == "ExceptionInInitializerError" ||
            typeName == "NoClassDefFoundError"
    }

    private fun defaultFps(): Int {
        return if (platform is Platform.iOS || platform is Platform.Android) {
            90
        } else {
            120
        }
    }

    private fun loadInitialFps(): Int {
        return try {
            GeneralSettings.performanceFPS.value
        } catch (exception: Throwable) {
            if (!exception.isRecoverablePlatformInitFailure()) throw exception
            println(
                "Heaven: settings unavailable during initialization, using default FPS (${exception.message ?: exception::class.simpleName})"
            )
            defaultFps()
        }
    }

    @Volatile var devices: List<LaunchpadViewportElement> = emptyList()
        private set

    private val signalQueue = Channel<List<Signal.LED>>(UNLIMITED)
    private val schedulerCommands = Channel<SchedulerCommand>(UNLIMITED)
    private val pendingJobsCount = atomic(0)
    private val globalGeneration = atomic(0)
    private val schedulerMutationLock = SynchronizedObject()
    private val ownerGenerationLock = SynchronizedObject()
    private val ownerGenerations = mutableMapOf<JobOwnerKey, Long>()
    private val rendererWakeLock = SynchronizedObject()

    @Volatile
    private var lastRenderNanos: Long = -1L

    @Volatile
    private var renderAtNanos: Long = -1L

    var fps: Int = loadInitialFps()

    private val stopWatch = StopWatch()
    private val renderScope = CoroutineScope(
        mainDispatcherOrDefault(owner = "Heaven", parallelism = 1) + SupervisorJob()
    )
    private val schedulerScope = CoroutineScope(
        Dispatchers.Default.limitedParallelism(1) + SupervisorJob()
    )

    init {
        schedulerScope.launch {
            runScheduler()
        }

        renderScope.launch {
            ViewportRepository.devices.collect { newDevices ->
                devices = newDevices
                wakeRenderer()
            }
        }
    }

    private fun msToNanos(ms: Double): Long =
        (ms * StopWatch.NANOS_PER_MILLISECOND).roundToLong()

    private fun snapshotJobGenerations(owner: Any?, identifier: Any?): Pair<Long?, Long?> {
        if (owner == null) return null to null

        return synchronized(ownerGenerationLock) {
            val ownerGeneration = ownerGenerations[JobOwnerKey(owner, null)] ?: 0L
            val identifierGeneration = identifier?.let {
                ownerGenerations[JobOwnerKey(owner, it)] ?: 0L
            }
            ownerGeneration to identifierGeneration
        }
    }

    private fun invalidateJobGeneration(owner: Any, identifier: Any?) {
        synchronized(ownerGenerationLock) {
            val key = JobOwnerKey(owner, identifier)
            ownerGenerations[key] = (ownerGenerations[key] ?: 0L) + 1L
        }
    }

    private fun isJobCurrent(job: ScheduledJob): Boolean {
        if (job.globalGeneration != globalGeneration.value) {
            return false
        }

        val owner = job.owner ?: return true

        return synchronized(ownerGenerationLock) {
            val currentOwnerGeneration = ownerGenerations[JobOwnerKey(owner, null)] ?: 0L
            if (job.ownerGeneration != currentOwnerGeneration) {
                return@synchronized false
            }

            val identifier = job.identifier
            if (identifier != null) {
                val currentIdentifierGeneration = ownerGenerations[JobOwnerKey(owner, identifier)] ?: 0L
                if (job.identifierGeneration != currentIdentifierGeneration) {
                    return@synchronized false
                }
            }

            true
        }
    }

    fun midiEnter(signals: List<Signal.LED>) {
        signalQueue.trySend(signals)
        wakeRenderer()
    }

    private val jobIdCounter = atomic(0L)

    fun schedule(delayInMs: Double, owner: Any? = null, identifier: Any? = null, job: () -> Unit): String {
        return scheduleAt(
            targetTimeNanos = timeNanos + msToNanos(delayInMs),
            owner = owner,
            identifier = identifier,
            job = job,
        )
    }

    fun scheduleAt(
        targetTimeNanos: Long,
        owner: Any? = null,
        identifier: Any? = null,
        job: () -> Unit,
    ): String {
        val sequence = jobIdCounter.incrementAndGet()
        val jobId = "job_$sequence"
        synchronized(schedulerMutationLock) {
            val (ownerGeneration, identifierGeneration) = snapshotJobGenerations(owner, identifier)
            val scheduledJob = ScheduledJob(
                id = jobId,
                targetTimeNanos = targetTimeNanos,
                sequence = sequence,
                job = job,
                owner = owner,
                identifier = identifier,
                ownerGeneration = ownerGeneration,
                identifierGeneration = identifierGeneration,
                globalGeneration = globalGeneration.value,
            )

            pendingJobsCount.incrementAndGet()
            if (schedulerCommands.trySend(SchedulerCommand.Add(scheduledJob)).isFailure) {
                pendingJobsCount.decrementAndGet()
                error("Heaven scheduler is unavailable")
            }
        }
        return jobId
    }

    fun cancelJobs(filter: (ScheduledJob) -> Boolean) {
        schedulerCommands.trySend(SchedulerCommand.Cancel(filter))
    }

    fun cancelJobsForOwner(owner: Any, identifier: Any? = null) {
        synchronized(schedulerMutationLock) {
            invalidateJobGeneration(owner, identifier)
            schedulerCommands.trySend(
                SchedulerCommand.Cancel {
                    it.owner === owner && (identifier == null || it.identifier == identifier)
                }
            )
        }
    }

    fun cancelJob(jobId: String) {
        cancelJobs { it.id == jobId }
    }

    @Volatile private var renderJob: Job? = null
    private val isRendererAwake: Boolean get() = renderJob?.isActive == true

    val timeNanos: Long
        get() = stopWatch.elapsedNanos()

    val time: Double
        get() = timeNanos / StopWatch.NANOS_PER_MILLISECOND.toDouble()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun wakeRenderer() {
        synchronized(rendererWakeLock) {
            if (isRendererAwake) return

            renderJob = renderScope.launch {
                try {
                    while (true) {
                        val changed = processSignals()
                        val nowNanos = timeNanos

                        if (changed && renderAtNanos < 0) {
                            renderAtNanos = max(
                                nowNanos + msToNanos(250.0 / fps),
                                lastRenderNanos + msToNanos(1000.0 / fps)
                            )
                        }

                        if (renderAtNanos >= 0 && nowNanos >= renderAtNanos) {
                            Screen.draw()
                            lastRenderNanos = nowNanos
                            renderAtNanos = -1L
                        }

                        if (renderAtNanos < 0 && signalQueue.isEmpty) {
                            break
                        }

                        val remainingNanos = renderAtNanos - timeNanos
                        if (remainingNanos > StopWatch.NANOS_PER_MILLISECOND) {
                            delay((remainingNanos / StopWatch.NANOS_PER_MILLISECOND).coerceAtLeast(1L))
                        } else {
                            yield()
                        }
                    }
                } catch (e: Exception) {
                    println("RenderJob Exception: ${e.message}")
                    e.printStackTrace()
                } finally {
                    val shouldWake = synchronized(rendererWakeLock) {
                        renderJob = null
                        !signalQueue.isEmpty
                    }
                    if (shouldWake) {
                        wakeRenderer()
                    }
                }
            }
        }
    }

    private fun handleSchedulerCommand(
        command: SchedulerCommand,
        jobs: ScheduledJobQueue,
    ) {
        when (command) {
            is SchedulerCommand.Add -> {
                if (!isJobCurrent(command.scheduledJob)) {
                    pendingJobsCount.decrementAndGet()
                    return
                }

                jobs.add(command.scheduledJob)
            }
            is SchedulerCommand.Cancel -> {
                repeat(jobs.removeAll(command.filter)) {
                    pendingJobsCount.decrementAndGet()
                }
            }
            SchedulerCommand.Clear -> {
                repeat(jobs.clear()) {
                    pendingJobsCount.decrementAndGet()
                }
            }
        }
    }

    private suspend fun drainSchedulerCommands(jobs: ScheduledJobQueue) {
        while (true) {
            val command = schedulerCommands.tryReceive().getOrNull() ?: return
            handleSchedulerCommand(command, jobs)
        }
    }

    private suspend fun runScheduler() {
        val jobs = ScheduledJobQueue()

        while (currentCoroutineContext().isActive) {
            drainSchedulerCommands(jobs)

            val nowNanos = timeNanos
            while (jobs.peek()?.targetTimeNanos?.let { it <= nowNanos } == true) {
                val scheduledJob = jobs.removeFirst()
                pendingJobsCount.decrementAndGet()

                if (!isJobCurrent(scheduledJob)) {
                    continue
                }

                try {
                    scheduledJob.job.invoke()
                } catch (e: Exception) {
                    println("Error executing job ${scheduledJob.id}: ${e.message}")
                    e.printStackTrace()
                }
            }

            if (jobs.isEmpty()) {
                handleSchedulerCommand(schedulerCommands.receive(), jobs)
                continue
            }

            val remainingNanos = jobs.peek()!!.targetTimeNanos - timeNanos
            if (remainingNanos > FINAL_DEADLINE_WINDOW_NANOS) {
                val waitMillis = (
                    (remainingNanos - FINAL_DEADLINE_WINDOW_NANOS) /
                        StopWatch.NANOS_PER_MILLISECOND
                    ).coerceAtLeast(1L)
                val command = withTimeoutOrNull(waitMillis) {
                    schedulerCommands.receive()
                }
                if (command != null) {
                    handleSchedulerCommand(command, jobs)
                }
            } else if (remainingNanos > 0L) {
                while (timeNanos < jobs.peek()!!.targetTimeNanos) {
                    // Bounded to the final millisecond to avoid coroutine wake-up jitter.
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun processSignals(): Boolean {
        var changed = false

        while (!signalQueue.isEmpty) {
            val signals = signalQueue.tryReceive().getOrNull() ?: break

            data class MidiCall(val device: LaunchpadViewportElement, val signal: Signal.LED)
            val midiCalls = mutableListOf<MidiCall>()

            val currentDevices = devices

            signals.forEach { signal ->
                currentDevices.forEach { device ->
                    if (isSignalInDevice(signal, device)) {
                        val localX = signal.x - device.position.value.x.toInt()
                        val localY = signal.y - device.position.value.y.toInt()
                        val posX = localX + device.layout.offsetX
                        val posY = (device.layout.rows - 1 - localY) + device.layout.offsetY

                        midiCalls.add(MidiCall(
                            device,
                            signal.copy(x = posX, y = posY)
                        ))

                        changed = true
                    }
                }
            }

            midiCalls.forEach { (device, signal) ->
                device.screen.midiEnter(signal)
            }
        }

        return changed
    }

    private fun isSignalInDevice(signal: Signal.LED, device: LaunchpadViewportElement): Boolean {
        val deviceX = device.position.value.x.toInt()
        val deviceY = device.position.value.y.toInt()
        return signal.x in deviceX until deviceX + device.layout.cols &&
                signal.y in deviceY until deviceY + device.layout.rows
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clear() {
        synchronized(schedulerMutationLock) {
            globalGeneration.incrementAndGet()
            synchronized(ownerGenerationLock) {
                ownerGenerations.clear()
            }
            schedulerCommands.trySend(SchedulerCommand.Clear)
        }

        renderScope.launch {
            devices.forEach { it.screen.clear() }

            while (!signalQueue.isEmpty) {
                signalQueue.tryReceive()
            }

            lastRenderNanos = -1L
            renderAtNanos = -1L
        }
    }

    fun shutdown() {
        schedulerScope.cancel()
        renderScope.cancel()
    }

    internal fun pendingJobCountForTesting(): Int = pendingJobsCount.value

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun waitUntilIdleForTesting(timeoutMs: Long = 2_000L) {
        withTimeout(timeoutMs) {
            while (true) {
                if (pendingJobCountForTesting() == 0 &&
                    signalQueue.isEmpty &&
                    schedulerCommands.isEmpty &&
                    !isRendererAwake) {
                    return@withTimeout
                }

                delay(5)
            }
        }
    }

    internal suspend fun resetForTesting() {
        clear()
        waitUntilIdleForTesting()
    }

    private const val FINAL_DEADLINE_WINDOW_NANOS = 1_000_000L
}
