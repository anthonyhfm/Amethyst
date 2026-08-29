package dev.anthonyhfm.amethyst.core.engine.audio.trigger

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.AtomicLongArray
import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationTarget

enum class TriggerPhase {
    Down,
    Up,
}

/** Stable identity shared by the Down and Up event of one physical or virtual pad. */
data class PadTriggerKey(
    val originId: String,
    val x: Int,
    val y: Int,
)

/** A hardware-independent trigger scheduled on the absolute audio frame clock. */
data class PadTriggerEvent(
    val key: PadTriggerKey,
    val phase: TriggerPhase,
    val velocity: Int,
    val targetFrame: Long,
) {
    init {
        require(velocity in 0..127)
        require(targetFrame >= 0L)
    }
}

fun Signal.Midi.toPadTriggerEvent(
    targetFrame: Long,
    originId: String = stablePadOriginId(origin),
): PadTriggerEvent = PadTriggerEvent(
    key = PadTriggerKey(originId = originId, x = x, y = y),
    phase = if (velocity > 0) TriggerPhase.Down else TriggerPhase.Up,
    velocity = velocity.coerceIn(0, 127),
    targetFrame = targetFrame,
)

private fun stablePadOriginId(origin: Any?): String = when (origin) {
    null -> "unknown"
    is Selectable -> origin.selectionUUID
    is String -> origin
    else -> "${origin::class.simpleName}:${origin.hashCode()}"
}

interface ChokeVoiceSource {
    val persistentSourceId: String
    val chokeGroup: Int
    fun enqueueChoke(targetFrame: Long)
}

/** Workspace-local trigger clock and choke-group router. */
class AudioTriggerRuntime {
    private val publishedFrame = atomic(0L)
    private val sources = atomic(emptyArray<ChokeSourceRegistration>())
    private val automationSources = atomic(emptyArray<LiveAutomationSource>())
    private val sidechainSinks = atomic(emptyArray<SidechainTriggerRegistration>())
    private val publishedSampleRate = atomic(44_100)

    val currentFrame: Long get() = publishedFrame.value
    val sampleRate: Int get() = publishedSampleRate.value

    fun publishFrame(frame: Long) {
        publishedFrame.value = frame.coerceAtLeast(0L)
    }

    fun publishSampleRate(sampleRate: Int) {
        publishedSampleRate.value = sampleRate.coerceAtLeast(1)
    }

    fun replaceSources(registrations: Array<ChokeSourceRegistration>) {
        sources.value = registrations.copyOf()
    }

    fun replaceAutomationSources(registrations: Array<LiveAutomationSource>) {
        automationSources.value = registrations.copyOf()
    }

    fun replaceSidechainSinks(registrations: Array<SidechainTriggerRegistration>) {
        sidechainSinks.value = registrations.copyOf()
    }

    fun automationValue(target: LiveAutomationTarget, frame: Long): Float? {
        val snapshot = automationSources.value
        var index = snapshot.lastIndex
        while (index >= 0) {
            val source = snapshot[index]
            if (source.target == target && source.isAutomationRunning) {
                return source.automationValueAt(frame)
            }
            index--
        }
        return null
    }

    fun onSourceTriggered(sourceId: String, chokeGroup: Int, targetFrame: Long) {
        val sinks = sidechainSinks.value
        var sinkIndex = 0
        while (sinkIndex < sinks.size) {
            val registration = sinks[sinkIndex]
            if (sourceId in registration.allowedSourceIds) {
                registration.sink.enqueueSidechainTrigger(sourceId, targetFrame)
            }
            sinkIndex++
        }
        if (chokeGroup !in 1..16) return
        val snapshot = sources.value
        var index = 0
        while (index < snapshot.size) {
            val registration = snapshot[index]
            if (registration.source.chokeGroup == chokeGroup) {
                registration.source.enqueueChoke(targetFrame)
            }
            index++
        }
    }
}

data class ChokeSourceRegistration(
    val source: ChokeVoiceSource,
)

interface AudioTriggerRuntimeAware {
    var audioTriggerRuntime: AudioTriggerRuntime?
}

interface LiveAutomationSource {
    val target: LiveAutomationTarget
    val isAutomationRunning: Boolean
    fun automationValueAt(frame: Long): Float
}

interface SidechainTriggerSink {
    val sidechainSourceId: String?
    fun enqueueSidechainTrigger(sourceId: String, targetFrame: Long)
}

data class SidechainTriggerRegistration(
    val sink: SidechainTriggerSink,
    val allowedSourceIds: Set<String>,
)

/** Bounded single-producer/single-consumer frame queue used by trigger-rate audio controls. */
class AudioFrameTriggerQueue(private val capacity: Int = 32) {
    private val frames = AtomicLongArray(capacity)
    private val sequences = AtomicLongArray(capacity)
    private val writeSequence = atomic(0L)
    private val readSequence = atomic(0L)
    private val dropped = atomic(0L)

    init {
        require(capacity > 0)
        var index = 0
        while (index < capacity) {
            sequences[index].value = index.toLong()
            index++
        }
    }

    val droppedCount: Long get() = dropped.value

    fun offer(frame: Long): Boolean {
        while (true) {
            val write = writeSequence.value
            val slot = (write % capacity).toInt()
            val difference = sequences[slot].value - write
            when {
                difference == 0L -> if (writeSequence.compareAndSet(write, write + 1L)) {
                    frames[slot].value = frame.coerceAtLeast(0L)
                    sequences[slot].value = write + 1L
                    return true
                }
                difference < 0L -> {
                    dropped.incrementAndGet()
                    return false
                }
            }
        }
    }

    fun peek(): Long? {
        val read = readSequence.value
        val slot = (read % capacity).toInt()
        return if (sequences[slot].value == read + 1L) frames[slot].value else null
    }

    fun poll(): Long? {
        val read = readSequence.value
        val slot = (read % capacity).toInt()
        if (sequences[slot].value != read + 1L) return null
        val frame = frames[slot].value
        sequences[slot].value = read + capacity
        readSequence.value = read + 1L
        return frame
    }

    fun clear() {
        while (poll() != null) {
            // Drain stale triggers without retaining them.
        }
    }
}
