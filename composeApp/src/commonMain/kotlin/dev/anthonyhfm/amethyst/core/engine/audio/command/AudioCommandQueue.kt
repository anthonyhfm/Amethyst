package dev.anthonyhfm.amethyst.core.engine.audio.command

import kotlinx.atomicfu.AtomicLongArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

/**
 * Bounded lock-free MPSC queue with a single real-time consumer.
 *
 * When full, the newest command is rejected and [droppedCommandCount] is
 * incremented. Emergency stop bypasses the ring so it remains deliverable.
 */
class AudioCommandQueue(
    val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0)
    }

    private val elements = atomicArrayOfNulls<AudioRenderCommand>(capacity)
    private val sequences = AtomicLongArray(capacity)
    private val enqueuePosition = atomic(0L)
    private val dequeuePosition = atomic(0L)
    private val dropped = atomic(0L)
    private val emergencyStop = atomic(false)

    val droppedCommandCount: Long
        get() = dropped.value

    val approximateSize: Int
        get() = (enqueuePosition.value - dequeuePosition.value)
            .coerceIn(0L, capacity.toLong())
            .toInt()

    init {
        var index = 0
        while (index < capacity) {
            sequences[index].value = index.toLong()
            index++
        }
    }

    fun offer(command: AudioRenderCommand): Boolean {
        require(command.targetFrame >= 0L)
        while (true) {
            val position = enqueuePosition.value
            val index = (position % capacity).toInt()
            val sequence = sequences[index].value
            val difference = sequence - position
            when {
                difference == 0L -> {
                    if (enqueuePosition.compareAndSet(position, position + 1L)) {
                        elements[index].value = command
                        sequences[index].value = position + 1L
                        return true
                    }
                }

                difference < 0L -> {
                    dropped.incrementAndGet()
                    return false
                }
            }
        }
    }

    fun poll(): AudioRenderCommand? {
        val position = dequeuePosition.value
        val index = (position % capacity).toInt()
        val sequence = sequences[index].value
        if (sequence - (position + 1L) != 0L) return null

        val command = elements[index].getAndSet(null)
        sequences[index].value = position + capacity
        dequeuePosition.value = position + 1L
        return command
    }

    fun requestEmergencyStop() {
        emergencyStop.value = true
    }

    fun consumeEmergencyStop(): Boolean = emergencyStop.getAndSet(false)

    /**
     * Lifecycle-only operation. Producers must be stopped before clearing.
     */
    fun clear() {
        while (poll() != null) {
            // Drain all published slots.
        }
        emergencyStop.value = false
    }

    companion object {
        const val DEFAULT_CAPACITY = 4096
    }
}
