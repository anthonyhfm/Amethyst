import dev.anthonyhfm.amethyst.core.heaven.elements.Screen
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevice
import dev.anthonyhfm.amethyst.core.util.StopWatch

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.ArrayList
import kotlin.collections.MutableList
import kotlin.collections.mutableMapOf
import kotlin.math.abs
import kotlin.math.max

object Heaven {
    private val devices: MutableList<LaunchpadDevice> = mutableListOf()

    private val signalQueue: ArrayDeque<List<Signal>> = ArrayDeque()
    private val jobs: MutableMap<Long, MutableList<() -> Unit>> = mutableMapOf()
    private val jobQueue: ArrayDeque<Pair<Long, () -> Unit>> = ArrayDeque()

    private val jobMutex = Mutex()

    private var prev: Long = 0L
    private var lastRender: Long = -1L
    private var renderAt: Long = -1L

    private val stopWatch = StopWatch()

    private fun msToTicks(ms: Double): Long = (ms / 1000 * stopWatch.frequency).toLong()

    fun registerDevice(device: LaunchpadDevice) {
        devices.add(device)
    }

    fun unregisterDevice(device: LaunchpadDevice) {
        devices.remove(device)
    }

    fun midiEnter(signals: List<Signal>) {
        signalQueue.add(signals)
        wake()
    }

    fun schedule(job: () -> Unit, time: Double) {
        jobQueue.add(msToTicks(time) to job)
        wake()
    }

    private var renderJob: Job? = null
    private val isAwake: Boolean get() = renderJob?.isActive == true

    val time: Double
        get() {
            if (!isAwake) prev = stopWatch.elapsedTicks() - 1
            return prev * 1000.0 / stopWatch.frequency
        }

    @OptIn(DelicateCoroutinesApi::class)
    private fun wake() {
        if (isAwake) return

        prev = stopWatch.elapsedTicks() - 1

        renderJob = GlobalScope.launch {
            try {
                while (true) {
                    prev = max(0, stopWatch.elapsedTicks())

                    if (renderAt < 0 && jobQueue.isEmpty() && jobs.isEmpty() && signalQueue.isEmpty()) {
                        delay(1)
                        continue
                    }

                    while (jobQueue.isNotEmpty()) {
                        val (time, job) = jobQueue.removeFirst()
                        jobs.getOrPut(time) { ArrayList() }.add(job)
                    }

                    prev = max(0, stopWatch.elapsedTicks())

                    jobMutex.withLock {
                        val keysToProcess = jobs.keys.filter { it <= prev }.toList()
                        keysToProcess.forEach { key ->
                            val jobList = jobs[key]
                            if (jobList != null) {
                                jobList.forEach { it.invoke() }
                                jobs.remove(key)
                            }
                        }
                    }

                    var changed = false

                    while (signalQueue.isNotEmpty()) {
                        val signals = signalQueue.removeFirst()

                        signals.forEach { signal ->
                            devices.forEach { device ->
                                if (signal.x in device.position.first until device.position.first + 10 &&
                                    signal.y in device.position.second until device.position.second + 10) {

                                    val posX = signal.x - device.position.first
                                    val posY = abs(signal.y - 9 - device.position.second)

                                    CoroutineScope(Dispatchers.IO).launch {
                                        device.screen.midiEnter(signal.copy(
                                            x = posX,
                                            y = posY
                                        ))
                                    }

                                    changed = true
                                }
                            }
                        }
                    }

                    if (changed && renderAt < 0) {
                        renderAt = max(
                            prev + msToTicks(250.0 / 60),
                            lastRender + msToTicks(1000.0 / 60)
                        )
                    } else if (renderAt >= 0 && prev > renderAt) {
                        Screen.draw()

                        lastRender = prev
                        renderAt = -1L
                    }

                    delay(1) // Vermeidet Busy-Waiting und verbessert die Performance
                }
            } catch (e: Exception) {
                println("RenderJob Exception: ${e.message}")
                e.printStackTrace()
            }
        }
    }
}
