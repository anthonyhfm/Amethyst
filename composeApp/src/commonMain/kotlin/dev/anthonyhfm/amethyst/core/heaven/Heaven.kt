package dev.anthonyhfm.amethyst.core.heaven

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.heaven.elements.Screen
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevice
import dev.anthonyhfm.amethyst.core.util.StopWatch
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.ArrayList
import kotlin.collections.MutableList
import kotlin.collections.mutableMapOf
import kotlin.math.abs
import kotlin.math.max

object Heaven {
    var devices: List<LaunchpadViewportElement> = emptyList()

    private val signalQueue: MutableList<List<Signal>> = mutableListOf()
    private val jobs: MutableMap<Long, MutableList<() -> Unit>> = mutableMapOf()
    private val jobQueue: MutableList<Pair<Long, () -> Unit>> = mutableListOf()

    private val deviceMutex = Mutex()
    private val signalMutex = Mutex()
    private val jobMutex = Mutex()

    private var prev: Long = 0L
    private var lastRender: Long = -1L
    private var renderAt: Long = -1L
    var fps: Int = GlobalSettings.perforanceFPS

    private val stopWatch = StopWatch()
    private val renderScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private fun msToTicks(ms: Double): Long = (ms / 1000 * stopWatch.frequency).toLong()

    fun midiEnter(signals: List<Signal>) {
        renderScope.launch {
            signalMutex.withLock {
                signalQueue.add(signals)
            }
        }
        wake()
    }

    fun schedule(delayInMs: Double, job: () -> Unit) {
        val targetTime = prev + msToTicks(delayInMs)
        renderScope.launch {
            jobMutex.withLock {
                jobQueue.add(targetTime to job)
            }
        }
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

        renderJob = renderScope.launch {
            try {
                while (true) {
                    prev = max(0, stopWatch.elapsedTicks())

                    if (renderAt < 0 && jobQueue.isEmpty() && jobs.isEmpty() && signalQueue.isEmpty()) {
                        delay(1)
                        continue
                    }

                    jobMutex.withLock {
                        while (jobQueue.isNotEmpty()) {
                            val (targetTime, job) = jobQueue.removeFirstOrNull() ?: continue
                            jobs.getOrPut(targetTime) { ArrayList() }.add(job)
                        }
                    }

                    prev = max(0, stopWatch.elapsedTicks())

                    jobMutex.withLock {
                        val keysToProcess = jobs.keys.filter { it <= prev }.toList()
                        keysToProcess.forEach { key ->
                            val jobList = jobs[key]
                            if (jobList != null) {
                                jobList.forEach {
                                    try {
                                        it.invoke()
                                    } catch (e: Exception) {
                                        println("Error executing job: ${e.message}")
                                    }
                                }
                                jobs.remove(key)
                            }
                        }
                    }

                    var changed = false

                    signalMutex.withLock {
                        while (signalQueue.isNotEmpty()) {
                            val signals = signalQueue.removeFirstOrNull() ?: continue

                            signals.forEach { signal ->
                                deviceMutex.withLock {
                                    devices.forEach { device ->
                                        if (signal.x in device.position.value.x.toInt() until device.position.value.x.toInt() + device.layout.x &&
                                            signal.y in device.position.value.y.toInt() until device.position.value.y.toInt() + device.layout.y) {

                                            val posX = signal.x - device.position.value.x.toInt() + device.layout.offsetX
                                            val posY = abs(signal.y - 9 - device.position.value.y.toInt())

                                            renderScope.launch {
                                                try {
                                                    device.screen.midiEnter(signal.copy(
                                                        x = posX,
                                                        y = posY
                                                    ))
                                                } catch (e: Exception) {
                                                    println("Error in midiEnter: ${e.message}")
                                                }
                                            }

                                            changed = true
                                        }
                                    }
                                }
                            }
                        }
                    }

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

                    delay(1)

                    if (renderAt < 0 && jobQueue.isEmpty() && jobs.isEmpty() && signalQueue.isEmpty()) {
                        Screen.draw()
                        lastRender = prev
                    }
                }
            } catch (e: Exception) {
                println("RenderJob Exception: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun clear() {
        renderScope.launch {
            deviceMutex.withLock {
                devices.forEach { it.screen.clear() }
            }
            signalMutex.withLock {
                signalQueue.clear()
            }
            jobMutex.withLock {
                jobs.clear()
                jobQueue.clear()
            }
            prev = 0L
            lastRender = -1L
            renderAt = -1L
        }
    }
}