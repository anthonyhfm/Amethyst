package dev.anthonyhfm.amethyst.timeline.data

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.DeviceRegistry
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

/** A timeline-owned source and processor chain. It never references the workspace lights chain. */
@Serializable
data class ChainEffectEntry(
    val clipId: String = UUID.randomUUID(),
    override val startTimeMs: Long,
    override val durationMs: Long,
    val source: @Polymorphic DeviceState? = null,
    val processors: StateChain = StateChain(),
    /** Sticky user/collision choke point. Null means follow the natural device duration. */
    val maxDurationMs: Long? = null,
    var name: String = "Chain Effect",
) : TimelineEntry {
    val isPlayable: Boolean get() = source != null
    val isCapped: Boolean get() = maxDurationMs != null

    override fun start(
        startAt: Long?,
        automation: dev.anthonyhfm.amethyst.timeline.automation.TimelineTrackAutomationState,
    ) = Unit

    override fun stop() = Unit

    fun deepCopy(
        clipId: String = this.clipId,
        startTimeMs: Long = this.startTimeMs,
        durationMs: Long = this.durationMs,
        maxDurationMs: Long? = this.maxDurationMs,
    ): ChainEffectEntry = copy(
        clipId = clipId,
        startTimeMs = startTimeMs,
        durationMs = durationMs,
        source = source?.let(DeviceRegistry::deepCopyState),
        processors = processors.copy(
            devices = processors.devices.map(DeviceRegistry::deepCopyState),
            mutedDeviceIndices = processors.mutedDeviceIndices.toList(),
        ),
        maxDurationMs = maxDurationMs,
    )
}
