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

/** Published only after every sample reached by one MIDI dispatch has been queued.
 * The render thread resolves all ready batches once, before rendering any source. */
class AudioTriggerBatch(
    val requestedTargetFrame: Long? = null,
) {
    init {
        require(requestedTargetFrame == null || requestedTargetFrame >= 0L)
    }

    internal var next: AudioTriggerBatch? = null
    private val commandsQueued = atomic(false)
    private val routedScopedChokes = atomic(emptySet<ScopedChokeRoute>())
    internal val hasCommands: Boolean get() = commandsQueued.value
    fun markCommandQueued() { commandsQueued.value = true }
    private val resolvedFrame = atomic(Long.MAX_VALUE)
    val targetFrame: Long get() = resolvedFrame.value
    internal fun resolve(frame: Long) { resolvedFrame.value = frame }

    internal fun resolveAtOrAfter(frame: Long) {
        resolve(maxOf(requestedTargetFrame ?: frame, frame))
    }

    internal fun claimScopedChoke(
        scopeId: String,
        group: Int,
        ownerId: String,
        targetFrame: Long,
    ): Boolean {
        // Delay/repeat devices may retain the batch after its synchronous dispatch.
        // Those later triggers are independent and must route their own choke wave.
        if (this.targetFrame != Long.MAX_VALUE) return true
        val route = ScopedChokeRoute(scopeId, group, ownerId, targetFrame)
        while (true) {
            val previous = routedScopedChokes.value
            if (route in previous) return false
            if (routedScopedChokes.compareAndSet(previous, previous + route)) return true
        }
    }
}

private data class ScopedChokeRoute(
    val scopeId: String,
    val group: Int,
    val ownerId: String,
    val targetFrame: Long,
)

interface ChokeVoiceSource {
    val persistentSourceId: String
    val chokeGroup: Int
    /** Null keeps the historical workspace-global choke namespace. */
    val chokeScopeId: String? get() = null
    /** Samples with the same scoped owner are layers of one trigger and do not choke each other. */
    val chokeOwnerId: String? get() = null
    fun enqueueChoke(targetFrame: Long)
    fun enqueueChoke(targetFrame: Long, batch: AudioTriggerBatch?) = enqueueChoke(targetFrame)
}

/** Workspace-local trigger clock and choke-group router. */
class AudioTriggerRuntime {
    private val readyBatches = atomic<AudioTriggerBatch?>(null)

    fun commitBatch(batch: AudioTriggerBatch) {
        if (!batch.hasCommands) {
            // A delay/repeat device may retain a copied signal after synchronous
            // routing ends. Keep that token usable without registering an empty
            // batch: its eventual command will clamp this past frame to playback.
            batch.resolveAtOrAfter(currentFrame)
            return
        }
        while (true) {
            val previous = readyBatches.value
            batch.next = previous
            if (readyBatches.compareAndSet(previous, batch)) return
        }
    }

    fun beginRenderBlock(frame: Long) {
        publishFrame(frame)
        var ready = readyBatches.getAndSet(null)
        while (ready != null) {
            val next = ready.next
            ready.next = null
            ready.resolveAtOrAfter(frame)
            ready = next
        }
    }

    fun clearBatches() { readyBatches.value = null }

    private val publishedFrame = atomic(0L)
    private val sources = atomic(emptyArray<ChokeSourceRegistration>())
    private val automationSources = atomic(emptyArray<LiveAutomationSource>())
    private val publishedSampleRate = atomic(44_100)
    private val automationSequence = atomic(0L)

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

    fun automationValue(target: LiveAutomationTarget, frame: Long): Float? {
        val snapshot = automationSources.value
        var selected: LiveAutomationSource? = null
        var index = 0
        while (index < snapshot.size) {
            val source = snapshot[index]
            // Most graph automation sources are idle. Check the allocation-free
            // runtime flag before asking devices to materialize their target.
            if (source.isAutomationRunning && source.target == target) {
                if (selected == null || source.activationSequence >= selected.activationSequence) {
                    selected = source
                }
            }
            index++
        }
        return selected?.automationValueAt(frame)
    }

    fun isAutomationRunning(target: LiveAutomationTarget): Boolean {
        val snapshot = automationSources.value
        var index = 0
        while (index < snapshot.size) {
            val source = snapshot[index]
            if (source.isAutomationRunning && source.target == target) return true
            index++
        }
        return false
    }

    fun nextAutomationSequence(): Long = automationSequence.incrementAndGet()

    fun clearAutomationTarget(target: LiveAutomationTarget) {
        automationSources.value.forEach { source ->
            if (source.target == target) source.clearAutomationOverride()
        }
    }

    fun clearAutomationOverrides() {
        automationSources.value.forEach(LiveAutomationSource::clearAutomationOverride)
    }

    fun onSourceTriggered(
        sourceId: String,
        chokeGroup: Int,
        targetFrame: Long,
        batch: AudioTriggerBatch? = null,
        chokeScopeId: String? = null,
        chokeOwnerId: String? = null,
    ) {
        if (chokeGroup !in 1..16) return
        if (
            batch != null && chokeScopeId != null && chokeOwnerId != null &&
            !batch.claimScopedChoke(chokeScopeId, chokeGroup, chokeOwnerId, targetFrame)
        ) return
        val snapshot = sources.value
        var index = 0
        while (index < snapshot.size) {
            val registration = snapshot[index]
            val source = registration.source
            val isSameScopedOwner = chokeScopeId != null && chokeOwnerId != null &&
                source.chokeScopeId == chokeScopeId && source.chokeOwnerId == chokeOwnerId
            if (
                source.chokeGroup == chokeGroup &&
                source.chokeScopeId == chokeScopeId &&
                !isSameScopedOwner
            ) {
                source.enqueueChoke(targetFrame, batch)
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
    val participatesInAudioAutomation: Boolean get() = true
    val activationSequence: Long get() = 0L
    fun automationValueAt(frame: Long): Float
    fun clearAutomationOverride() = Unit
}

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
