package dev.anthonyhfm.amethyst.core.heaven

import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.core.heaven.elements.Screen
import dev.anthonyhfm.amethyst.core.heaven.elements.Signal
import dev.anthonyhfm.amethyst.core.heaven.utils.ConcurrentQueue
import dev.anthonyhfm.amethyst.core.heaven.utils.SortedDictionary
import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDevice
import dev.anthonyhfm.amethyst.core.util.StopWatch
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement

import kotlinx.coroutines.*
import kotlinx.atomicfu.*
import kotlin.math.abs
import kotlin.math.max

object Heaven {
    var devices: List<LaunchpadViewportElement> = emptyList()

    // Lock-free concurrent collections
    private val signalQueue = ConcurrentQueue<List<Signal>>()
    private val jobs = SortedDictionary<Long, MutableList<() -> Unit>>()
    private val jobQueue = ConcurrentQueue<Pair<Long, () -> Unit>>()

    // Atomic variables for lock-free access
    private val prev = atomic(0L)
    private val lastRender = atomic(-1L)
    private val renderAt = atomic(-1L)
    private val isRenderJobActive = atomic(false)

    var fps: Int = GlobalSettings.perforanceFPS

    private val stopWatch = StopWatch()

    // Use single thread for rendering to avoid context switching overhead
    private val renderDispatcher = newSingleThreadContext("RenderThread")
    private val renderScope = CoroutineScope(renderDispatcher + SupervisorJob())

    private fun msToTicks(ms: Double): Long = (ms / 1000 * stopWatch.frequency).toLong()

    fun midiEnter(signals: List<Signal>) {
        signalQueue.enqueue(signals)
        wake()
    }

    fun schedule(delayInMs: Double, job: () -> Unit) {
        val currentTime = prev.value
        val targetTime = currentTime + msToTicks(delayInMs)
        jobQueue.enqueue(targetTime to job)
        wake()
    }

    val time: Double
        get() {
            val currentTicks = stopWatch.elapsedTicks()
            prev.compareAndSet(prev.value, max(prev.value, currentTicks - 1))
            return prev.value * 1000.0 / stopWatch.frequency
        }

    private fun wake() {
        if (isRenderJobActive.compareAndSet(false, true)) {
            val currentTicks = stopWatch.elapsedTicks() - 1
            prev.compareAndSet(prev.value, max(prev.value, currentTicks))

            renderScope.launch {
                try {
                    renderLoop()
                } finally {
                    isRenderJobActive.value = false
                }
            }
        }
    }

    private suspend fun renderLoop() {
        while (true) {
            val currentTicks = max(0, stopWatch.elapsedTicks())
            prev.value = currentTicks

            // Process all pending jobs from the queue
            while (true) {
                val jobPair = jobQueue.tryDequeue() ?: break
                val (targetTime, job) = jobPair

                val existingJobs = jobs.get(targetTime) ?: mutableListOf<() -> Unit>().also {
                    jobs.put(targetTime, it)
                }
                existingJobs.add(job)
            }

            // Execute ready jobs
            val keysToProcess = jobs.getKeysUpTo(currentTicks)
            keysToProcess.forEach { key ->
                jobs.get(key)?.let { jobList ->
                    jobList.forEach { job ->
                        try {
                            job.invoke()
                        } catch (e: Exception) {
                            println("Error executing job: ${e.message}")
                        }
                    }
                    jobs.remove(key)
                }
            }

            // Process signals with minimal allocations
            var changed = false
            while (true) {
                val signals = signalQueue.tryDequeue() ?: break

                signals.forEach { signal ->
                    // Cache devices list to avoid repeated access
                    val currentDevices = devices

                    currentDevices.forEach { device ->
                        val deviceX = device.position.value.x.toInt()
                        val deviceY = device.position.value.y.toInt()
                        val layoutX = device.layout.x
                        val layoutY = device.layout.y

                        if (signal.x >= deviceX && signal.x < deviceX + layoutX &&
                            signal.y >= deviceY && signal.y < deviceY + layoutY) {

                            val posX = signal.x - deviceX + device.layout.offsetX
                            val posY = abs(signal.y - 9 - deviceY)

                            // Process immediately without launching new coroutine
                            try {
                                runBlocking {
                                    device.screen.midiEnter(signal.copy(
                                        x = posX,
                                        y = posY
                                    ))
                                }
                            } catch (e: Exception) {
                                println("Error in midiEnter: ${e.message}")
                            }

                            changed = true
                        }
                    }
                }
            }

            val currentRenderAt = renderAt.value

            // Handle rendering with minimal atomic operations
            if (changed && currentRenderAt < 0) {
                val newRenderAt = max(
                    currentTicks + msToTicks(250.0 / fps),
                    lastRender.value + msToTicks(1000.0 / fps)
                )
                renderAt.compareAndSet(-1L, newRenderAt)
            } else if (currentRenderAt >= 0 && currentTicks > currentRenderAt) {
                Screen.draw()
                lastRender.value = currentTicks
                renderAt.value = -1L
            }

            // Exit condition check with minimal overhead
            if (currentRenderAt < 0 &&
                jobQueue.isEmpty() &&
                jobs.isEmpty() &&
                signalQueue.isEmpty()) {

                // Final render and exit
                Screen.draw()
                lastRender.value = currentTicks
                break
            }

            // Yield control briefly to prevent 100% CPU usage
            yield()
        }
    }

    fun clear() {
        // Clear all queues
        while (!signalQueue.isEmpty()) {
            signalQueue.tryDequeue()
        }
        while (!jobQueue.isEmpty()) {
            jobQueue.tryDequeue()
        }

        // Clear devices screens
        devices.forEach {
            runBlocking { it.screen.clear() }
        }

        // Reset atomic variables
        prev.value = 0L
        lastRender.value = -1L
        renderAt.value = -1L
        isRenderJobActive.value = false
    }
}