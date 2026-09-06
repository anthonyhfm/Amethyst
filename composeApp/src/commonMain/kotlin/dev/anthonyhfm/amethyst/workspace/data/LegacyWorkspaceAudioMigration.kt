@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.devices.DeviceSerializationModule
import dev.anthonyhfm.amethyst.timeline.data.AudioEntry
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.MidiEntry
import dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.timeline.data.msToUs
import dev.anthonyhfm.amethyst.timeline.data.samplesToUs
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Decodes workspaces written before timeline clips referenced AudioSource IDs.
 *
 * The old AudioEntry schema reused field numbers that later carried different
 * wire types, so it cannot be represented by defaults on the current model.
 * This isolated decoder preserves that wire schema and immediately upgrades it
 * to the current project graph.
 */
internal fun decodeLegacyInlineAudioWorkspace(bytes: ByteArray): SavableWorkspaceData {
    val legacy = LegacyWorkspaceProtoBuf.decodeFromByteArray<LegacySavableWorkspaceData>(bytes)
    val sources = mutableListOf<AudioSource>()
    val tracks = legacy.timelineData.map { track ->
        when (track) {
            is LegacyAudioTimelineTrack -> track.toCurrent(sources)
            is LegacyMidiTimelineTrack -> track.toCurrent()
            else -> error("Unsupported legacy timeline track: ${track::class.simpleName}")
        }
    }

    return SavableWorkspaceData(
        version = legacy.version,
        title = legacy.title,
        author = legacy.author,
        settings = legacy.settings,
        timelineData = tracks,
        lights = legacy.lights,
        sampling = legacy.sampling,
        autoPlay = legacy.autoPlay,
        launchpadDevices = legacy.launchpadDevices.map(LegacySavableViewportLaunchpad::toCurrent),
        macros = legacy.macros,
        audioSources = sources,
    )
}

private val LegacyWorkspaceProtoBuf = ProtoBuf {
    serializersModule = SerializersModule {
        include(DeviceSerializationModule)
        polymorphic(LegacyTimelineTrack::class) {
            subclass(LegacyAudioTimelineTrack::class)
            subclass(LegacyMidiTimelineTrack::class)
        }
    }
}

@Serializable
private data class LegacySavableWorkspaceData(
    @ProtoNumber(1) val version: Version = amethystVersion,
    @ProtoNumber(2) val title: String = "Untitled Workspace",
    @ProtoNumber(3) val author: String = "Unknown Author",
    @ProtoNumber(4) val settings: WorkspaceSettings = WorkspaceSettings(),
    @ProtoNumber(5) val timelineData: List<@Polymorphic LegacyTimelineTrack<*>> = emptyList(),
    @ProtoNumber(6) val lights: StateChain = StateChain(),
    @ProtoNumber(7) val sampling: StateChain = StateChain(),
    @ProtoNumber(8) val autoPlay: AutoPlayData = AutoPlayData(emptyMap()),
    @ProtoNumber(9) val launchpadDevices: List<LegacySavableViewportLaunchpad> = emptyList(),
    @ProtoNumber(10) val macros: List<Macro> = listOf(Macro(0)),
)

private abstract class LegacyTimelineTrack<E>

@Serializable
@SerialName("dev.anthonyhfm.amethyst.timeline.data.AudioTimelineTrack")
private class LegacyAudioTimelineTrack : LegacyTimelineTrack<LegacyAudioEntry>() {
    @ProtoNumber(1)
    val entries: MutableMap<Long, LegacyAudioEntry> = mutableMapOf()
}

@Serializable
@SerialName("dev.anthonyhfm.amethyst.timeline.data.MidiTimelineTrack")
private class LegacyMidiTimelineTrack : LegacyTimelineTrack<MidiEntry>() {
    @ProtoNumber(1)
    val entries: MutableMap<Long, MidiEntry> = mutableMapOf()
}

@Serializable
private data class LegacyAudioEntry(
    @ProtoNumber(1) val startTimeMs: Long,
    @ProtoNumber(2) val durationMs: Long,
    @ProtoNumber(3) val fileName: String,
    @ProtoNumber(4) val rawData: ByteArray? = null,
    @ProtoNumber(5) val sampleRate: Int = 44_100,
    @ProtoNumber(6) val channels: Int = 2,
    @ProtoNumber(7) val bitDepth: Int = 16,
    @ProtoNumber(8) val name: String = "",
    @ProtoNumber(9) val sourceStartMs: Long = 0L,
    @ProtoNumber(10) val sourceDurationMs: Long = durationMs,
)

@Serializable
private data class LegacySavableViewportLaunchpad(
    @ProtoNumber(1) val positionX: Float,
    @ProtoNumber(2) val positionY: Float,
    @ProtoNumber(3) val type: ViewportDeviceType,
) {
    enum class ViewportDeviceType {
        LAUNCHPAD_PRO,
        LAUNCHPAD_PRO_MK3,
        LAUNCHPAD_X,
        LAUNCHPAD_MK2,
        MYSTRIX,
        MIDIFIGHTER64,
    }

    fun toCurrent(): SavableWorkspaceData.SavableViewportLaunchpad = when (type) {
        ViewportDeviceType.LAUNCHPAD_PRO -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro(positionX, positionY)
        ViewportDeviceType.LAUNCHPAD_PRO_MK3 -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadProMk3(positionX, positionY)
        ViewportDeviceType.LAUNCHPAD_X -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadX(positionX, positionY)
        ViewportDeviceType.LAUNCHPAD_MK2 -> SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadMk2(positionX, positionY)
        ViewportDeviceType.MYSTRIX -> SavableWorkspaceData.SavableViewportLaunchpad.Mystrix(positionX, positionY)
        ViewportDeviceType.MIDIFIGHTER64 -> SavableWorkspaceData.SavableViewportLaunchpad.MidiFighter64(positionX, positionY)
    }
}

private fun LegacyAudioTimelineTrack.toCurrent(sources: MutableList<AudioSource>): AudioTimelineTrack =
    AudioTimelineTrack().also { currentTrack ->
        entries.forEach { (mapStart, legacyEntry) ->
            val bytes = legacyEntry.rawData ?: return@forEach
            val source = sources.firstOrNull { candidate ->
                candidate.sampleRate == legacyEntry.sampleRate &&
                    candidate.channels == legacyEntry.channels &&
                    candidate.bitDepth == legacyEntry.bitDepth &&
                    candidate.rawData.contentEquals(bytes)
            } ?: AudioSource(
                id = UUID.randomUUID(),
                fileName = legacyEntry.fileName,
                rawData = bytes,
                sampleRate = legacyEntry.sampleRate,
                channels = legacyEntry.channels,
                bitDepth = legacyEntry.bitDepth,
            ).also(sources::add)

            val startFrame = (legacyEntry.sourceStartMs * legacyEntry.sampleRate / 1_000L)
                .coerceIn(0L, source.totalSamples)
            val requestedDurationMs = legacyEntry.sourceDurationMs
                .takeIf { it > 0L }
                ?: legacyEntry.durationMs
            val durationFrames = requestedDurationMs * legacyEntry.sampleRate / 1_000L
            val endFrame = (startFrame + durationFrames)
                .coerceIn(startFrame, source.totalSamples)
                .takeIf { it > startFrame }
                ?: source.totalSamples
            val timelineStart = legacyEntry.startTimeMs.takeIf { it == mapStart } ?: mapStart
            currentTrack.entries[timelineStart] = AudioEntry(
                startTimeMs = timelineStart,
                durationMs = legacyEntry.durationMs,
                fileName = legacyEntry.fileName,
                sourceId = source.id,
                clipStartSample = startFrame,
                clipEndSample = endFrame,
                sampleRate = legacyEntry.sampleRate,
                channels = legacyEntry.channels,
                bitDepth = legacyEntry.bitDepth,
                name = legacyEntry.name,
                startTimeUs = msToUs(timelineStart),
                durationUs = samplesToUs((endFrame - startFrame).coerceAtLeast(0L), legacyEntry.sampleRate),
            )
        }
    }

private fun LegacyMidiTimelineTrack.toCurrent(): MidiTimelineTrack =
    MidiTimelineTrack().also { it.entries.putAll(entries) }
