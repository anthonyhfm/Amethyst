package dev.anthonyhfm.amethyst.core.heaven

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.heaven.elements.Screen
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import dev.anthonyhfm.amethyst.core.util.StopWatch

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.max

object Heaven {
    var devices: List<LaunchpadViewportElement> = emptyList()
        set(value) {
            field = value
            wake()
        }

    private val signalQueue = Channel<List<Signal>>(UNLIMITED)
    private val jobQueue = Channel<Pair<Long, () -> Unit>>(UNLIMITED)

    private val jobsMutex = Mutex()
    private val jobsList = mutableListOf<Pair<Long, MutableList<() -> Unit>>>()

    @Volatile
    private var prev: Long = 0L

    @Volatile
    private var lastRender: Long = -1L

    @Volatile
    private var renderAt: Long = -1L

    private val deviceMutex = Mutex()

    var fps: Int = GlobalSettings.perforanceFPS

    private val stopWatch = StopWatch()
    private val renderScope = CoroutineScope(Dispatchers.Main.limitedParallelism(1) + SupervisorJob())

    private fun msToTicks(ms: Double): Long = (ms / 1000 * stopWatch.frequency).toLong()

    fun midiEnter(signals: List<Signal>) {
        renderScope.launch {
            signalQueue.send(signals)
            cancel()
        }
        wake()
    }

    fun schedule(delayInMs: Double, job: () -> Unit) {
        val targetTime = prev + msToTicks(delayInMs)
        renderScope.launch {
            jobQueue.send(targetTime to job)
            cancel()
        }
        wake()
    }

    @Volatile private var renderJob: Job? = null
    private val isAwake: Boolean get() = renderJob?.isActive == true

    val time: Double
        get() {
            if (!isAwake) prev = stopWatch.elapsedTicks() - 1
            return prev * 1000.0 / stopWatch.frequency
        }

    private fun wake() {
        if (isAwake) return

        prev = stopWatch.elapsedTicks() - 1

        renderJob = renderScope.launch {
            try {
                while (true) {
                    val hasNewJobs = processJobQueue()

                    val hasWork = renderAt >= 0 || hasNewJobs ||
                            jobsList.isNotEmpty() || !signalQueue.isEmpty

                    if (!hasWork) {
                        delay(10)
                        if (renderAt < 0 && jobsList.isEmpty() &&
                            jobQueue.isEmpty && signalQueue.isEmpty) {
                            break
                        }
                    }

                    prev = stopWatch.elapsedTicks()

                    executeReadyJobs(prev)

                    val changed = processSignals()

                    if (changed && renderAt < 0) {
                        renderAt = max(
                            prev + msToTicks(250.0 / fps),
                            lastRender + msToTicks(1000.0 / fps)
                        )
                    } else if (renderAt >= 0 && prev > renderAt) {
                        Screen.draw()
                        lastRender = prev
                        renderAt = -1L
                    }

                    yield()
                }

                if (lastRender < prev) {
                    Screen.draw()
                }
            } catch (e: Exception) {
                println("RenderJob Exception: ${e.message}")
                e.printStackTrace()
            }

            cancel()
        }
    }

    private suspend fun processJobQueue(): Boolean {
        var processed = false

        while (!jobQueue.isEmpty) {
            val (targetTime, job) = jobQueue.tryReceive().getOrNull() ?: break

            jobsMutex.withLock {
                val insertIndex = findInsertPosition(targetTime)

                if (insertIndex < jobsList.size && jobsList[insertIndex].first == targetTime) {
                    jobsList[insertIndex].second.add(job)
                } else {
                    jobsList.add(insertIndex, targetTime to mutableListOf(job))
                }
            }

            processed = true
        }

        return processed
    }

    private fun findInsertPosition(targetTime: Long): Int {
        var low = 0
        var high = jobsList.size

        while (low < high) {
            val mid = (low + high) / 2
            if (jobsList[mid].first < targetTime) {
                low = mid + 1
            } else {
                high = mid
            }
        }

        return low
    }

    private suspend fun executeReadyJobs(currentTime: Long) {
        val jobsToExecute = jobsMutex.withLock {
            val result = mutableListOf<() -> Unit>()
            var splitIndex = 0

            for (i in jobsList.indices) {
                if (jobsList[i].first <= currentTime) {
                    result.addAll(jobsList[i].second)
                    splitIndex++
                } else {
                    break
                }
            }

            if (splitIndex > 0) {
                jobsList.subList(0, splitIndex).clear()
            }

            result
        }

        jobsToExecute.forEach { job ->
            try {
                job.invoke()
            } catch (e: Exception) {
                println("Error executing job: ${e.message}")
            }
        }
    }

    private suspend fun processSignals(): Boolean {
        var changed = false

        while (!signalQueue.isEmpty) {
            val signals = signalQueue.tryReceive().getOrNull() ?: break

            data class MidiCall(val device: LaunchpadViewportElement, val signal: Signal)
            val midiCalls = mutableListOf<MidiCall>()

            deviceMutex.withLock {
                val currentDevices = devices

                signals.forEach { signal ->
                    currentDevices.forEach { device ->
                        if (isSignalInDevice(signal, device)) {
                            val posX = signal.x - device.position.value.x.toInt() + device.layout.offsetX
                            val posY = abs(signal.y - 9 - device.position.value.y.toInt()) - device.layout.offsetY

                            midiCalls.add(MidiCall(
                                device,
                                signal.copy(x = posX, y = posY)
                            ))

                            changed = true
                        }
                    }
                }
            }

            midiCalls.forEach { (device, signal) ->
                device.screen.midiEnter(signal)
            }
        }

        return changed
    }

    private fun isSignalInDevice(signal: Signal, device: LaunchpadViewportElement): Boolean {
        val deviceX = device.position.value.x.toInt()
        val deviceY = device.position.value.y.toInt()
        return signal.x in deviceX until deviceX + device.layout.cols &&
                signal.y in deviceY until deviceY + device.layout.rows
    }

    fun clear() {
        renderScope.launch {
            deviceMutex.withLock {
                devices.forEach { it.screen.clear() }
            }

            while (!signalQueue.isEmpty) {
                signalQueue.tryReceive()
            }
            while (!jobQueue.isEmpty) {
                jobQueue.tryReceive()
            }

            jobsMutex.withLock {
                jobsList.clear()
            }

            prev = 0L
            lastRender = -1L
            renderAt = -1L

            cancel()
        }
    }

    fun shutdown() {
        renderScope.cancel()
    }
}