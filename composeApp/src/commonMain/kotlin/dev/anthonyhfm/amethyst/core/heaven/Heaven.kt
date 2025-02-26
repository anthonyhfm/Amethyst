import dev.anthonyhfm.amethyst.core.heaven.elements.Screen
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevice
import dev.anthonyhfm.amethyst.core.util.StopWatch

import kotlinx.coroutines.*
import kotlin.collections.MutableList
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max

object Heaven {
    private val devices: MutableList<LaunchpadDevice> = mutableListOf()

    private val signalQueue: ArrayDeque<List<Signal>> = ArrayDeque()
    private val jobs: MutableMap<Long, MutableList<() -> Unit>> = mutableMapOf()
    private val jobQueue: ArrayDeque<Pair<Long, () -> Unit>> = ArrayDeque()

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

        prev = max(0, stopWatch.elapsedTicks() - 1)

        renderJob = GlobalScope.launch {
            var noDrawCounter = 0

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

                jobs.keys.filter { it <= prev }.toList().forEach { key ->
                    jobs[key]?.forEach { it.invoke() }
                    jobs.remove(key)
                }

                var changed = false

                while (signalQueue.isNotEmpty()) {
                    val signals = signalQueue.removeFirst()

                    signals.forEach { signal ->
                        devices.forEach {
                            if (signal.x in it.position.first until it.position.first + 10 && signal.y in it.position.second until it.position.second + 10) {
                                val posX = signal.x - it.position.first
                                val posY = abs(signal.y - 9 - it.position.second)

                                it.screen.midiEnter(signal.copy(
                                    x = posX,
                                    y = posY
                                ))

                                changed = true
                            }
                        }
                    }
                }

                if (changed && renderAt < 0) {
                    val tick250 = msToTicks(250.0 / 60)
                    val tick1000 = msToTicks(1000.0 / 60)

                    renderAt = max(prev + tick250, lastRender + tick1000)
                }

                if (renderAt >= 0 && prev > renderAt) {
                    Screen.draw()

                    lastRender = prev
                    renderAt = -1L
                    noDrawCounter = 0
                } else {
                    noDrawCounter++

                    if (noDrawCounter > 100) {
                        Screen.draw()
                        lastRender = prev
                        renderAt = -1L
                        noDrawCounter = 0
                    }
                }
            }
        }
    }
}
