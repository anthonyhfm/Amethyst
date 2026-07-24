package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.DeviceState
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

sealed interface KeyframesChainDeviceContract {
    sealed interface Event {
        data class OnPaintButton(val x: Int, val y: Int) : Event
        data class OnColorUpdate(val color: Color) : Event
        data class OnSelectFrame(
            val frameIndex: Int,
            val multiSelect: Boolean = false,
            val rangeSelect: Boolean = false
        ) : Event
        data class OnDeleteFrame(val frameIndex: Int) : Event
        data class OnAddFrame(val atIndex: Int? = null) : Event
        data class OnDuplicateFrame(val frameIndex: Int? = null) : Event
        data class OnChangeFrameTiming(val frameIndex: Int, val timing: Timing, val gate: Float) : Event
        data class OnChangeMultiFrameTiming(val frameIndices: List<Int>, val timing: Timing, val gate: Float) : Event
        data class OnChangeInfinity(val checked: Boolean) : Event
        data class OnChangeFramePosition(
            val from: Int,
            val to: Int
        ) : Event
        data class OnChangePinch(val pinch: Float) : Event
        data object OnTogglePinchBilateral : Event

        data object OnImportMidiFile : Event
        data class OnChangeRepeats(val repeats: Int) : Event
        data class OnChangePlaybackMode(val playbackMode: PlaybackMode) : Event
        data class OnChangeRootKey(val rootKey: Int?) : Event
        data class OnChangeWrap(val wrap: Boolean) : Event
        data class OnChangeIsolate(val isolate: Boolean) : Event
    }

    enum class PlaybackMode {
        Mono, Poly, Loop
    }

    @Serializable
    data class KeyframesChainDeviceState(
        val selectedColor: Triple<Float, Float, Float> = Triple(1f, 1f, 1f),
        val currentFrameIndex: Int = 0,
        val frames: List<Frame> = listOf(
            Frame(
                timing = Timing.Rythm(Timing.Rythm.RythmTiming._1_4),
            )
        ),
        val repeats: Int = 1,
        val playbackMode: PlaybackMode = PlaybackMode.Mono,
        val rootKey: Int? = null,
        val wrap: Boolean = false,
        val infinity: Boolean = false,
        val isolate: Boolean = false,
        val pinch: Float = 0f, // Range [-2,2]
        val bilateralPinch: Boolean = false,
        val useOwnershipTracking: Boolean = false,
        val ownershipId: String = "",
        @Transient
        val renderedAnimation: List<Pair<Int, List<Signal>>> = emptyList(),
    ) : DeviceState()

    @Serializable
    data class Frame(
        val timing: Timing,
        val gate: Float = 0.5f,
        val entries: List<KeyframesEntry> = emptyList(),
        @Transient
        val _internalUuid: String = UUID.randomUUID()
    )

    @Serializable
    data class KeyframesEntry(
        val x: Int,
        val y: Int,
        val r: Float,
        val g: Float,
        val b: Float,
        /** Device-anchored storage: non-null for new entries drawn after this schema. */
        val launchpadId: String? = null,
        val localX: Int? = null,
        val localY: Int? = null,
    ) {
        /** True when this entry carries device-local coordinate data. */
        val isDeviceAnchored: Boolean get() = launchpadId != null && localX != null && localY != null
    }
}