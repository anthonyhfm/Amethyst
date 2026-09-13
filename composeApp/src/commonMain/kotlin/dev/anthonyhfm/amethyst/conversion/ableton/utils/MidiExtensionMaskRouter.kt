package dev.anthonyhfm.amethyst.conversion.ableton.utils

import androidx.compose.ui.unit.IntOffset
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.GenericMidiExtAdapter
import dev.anthonyhfm.amethyst.conversion.ableton.adapters.kaskobi.GenericMidiExtAdapter.MidiExtensionMode
import dev.anthonyhfm.amethyst.conversion.ableton.data.MidiTrack
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MidiEffectGroupDevice
import dev.anthonyhfm.amethyst.conversion.ableton.data.devices.MxDeviceMidiEffect
import dev.anthonyhfm.amethyst.devices.effects.color.ColorChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.PlaybackMode
import dev.anthonyhfm.amethyst.devices.effects.mask.MaskChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.transmit.TransmitChainDeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID

object MidiExtensionMaskRouter {
    private data class Definition(
        val sourceTrackId: Int,
        val midiExtension: MxDeviceMidiEffect,
        val loop: Boolean,
    )

    private val definitions = mutableMapOf<Int, Definition>()
    private val tracksById = mutableMapOf<Int, MidiTrack>()
    private val outputOffsetsBySinkTrack = mutableMapOf<Int, IntOffset>()
    private val readyMasks = mutableMapOf<Int, MaskChainDeviceState>()
    private var busId: String = ""

    val channels: List<Int>
        get() = readyMasks.keys.sorted()

    fun prepare(tracks: List<MidiTrack>) {
        clear()
        busId = UUID.randomUUID()
        tracksById.putAll(tracks.associateBy(MidiTrack::id))

        tracks.forEach { track ->
            MidiChainReader.getAllDevicesOfType<MidiEffectGroupDevice>(track).forEach { group ->
                group.branches.branches.forEach { branch ->
                    val devices = branch.deviceChain.deviceChain.devices.devices
                    devices.zipWithNext().forEach { (mixerCandidate, extensionCandidate) ->
                        val mixer = mixerCandidate as? MxDeviceMidiEffect ?: return@forEach
                        val extension = extensionCandidate as? MxDeviceMidiEffect ?: return@forEach
                        if (!isNamed(mixer, "Depths Mixer.amxd")) return@forEach
                        if (!isNamed(extension, "MIDIext5.0.amxd")) return@forEach
                        val settings = GenericMidiExtAdapter.readSettings(extension)
                        if (settings.mode != MidiExtensionMode.Mask) {
                            return@forEach
                        }

                        val channel = channelFromBlob(mixer.decodeBlob()) ?: return@forEach
                        definitions[channel] = Definition(track.id, extension, settings.loop)
                    }
                }
            }
        }
    }

    fun registerOutputTarget(track: MidiTrack?, offset: IntOffset) {
        if (track == null) return
        outputOffsetsBySinkTrack[finalSinkTrackId(track.id)] = offset
    }

    fun materializeMasks() {
        definitions.forEach { (channel, definition) ->
            val outputOffset = outputOffsetsBySinkTrack[finalSinkTrackId(definition.sourceTrackId)]
                ?: return@forEach
            val keyframes = GenericMidiExtAdapter(definition.midiExtension, outputOffset)
                .importKeyframes()
                ?.copy(
                    playbackMode = if (definition.loop) PlaybackMode.Continuous else PlaybackMode.Mono,
                )
                ?: return@forEach
            readyMasks[channel] = MaskChainDeviceState(
                colorStateChain = StateChain(devices = listOf(keyframes)),
                shapeStateChain = StateChain(
                    devices = listOf(
                        CoordinateFilterChainDeviceState(),
                        TransmitChainDeviceState(
                            mode = TransmitChainDeviceState.Mode.Receive,
                            channel = channel,
                            busId = busId,
                        ),
                        ColorChainDeviceState(r = 1f, g = 1f, b = 1f),
                    )
                ),
            )
        }
    }

    fun isMaskChannel(channel: Int): Boolean = channel in readyMasks

    fun createSender(channel: Int): TransmitChainDeviceState {
        require(channel in readyMasks)
        return TransmitChainDeviceState(
            mode = TransmitChainDeviceState.Mode.Send,
            channel = channel,
            busId = busId,
        )
    }

    fun createMask(channel: Int): MaskChainDeviceState = checkNotNull(readyMasks[channel])

    fun clear() {
        definitions.clear()
        tracksById.clear()
        outputOffsetsBySinkTrack.clear()
        readyMasks.clear()
    }

    private fun finalSinkTrackId(startTrackId: Int): Int {
        var current = startTrackId
        val seen = mutableSetOf<Int>()
        while (seen.add(current)) {
            val target = tracksById[current]?.deviceChain?.midiOutputRouting?.target?.value.orEmpty()
            val next = Regex("MidiOut/Track\\.(\\d+)/TrackIn")
                .matchEntire(target)
                ?.groupValues?.get(1)
                ?.toIntOrNull()
                ?: return current
            current = next
        }
        return current
    }

    private fun isNamed(device: MxDeviceMidiEffect, expectedFileName: String): Boolean {
        val ref = device.patchSlot.value.patchRef?.fileRef ?: return false
        val referencedPath = ref.relativePath.value ?: ref.path?.value.orEmpty()
        return referencedPath.substringAfterLast('/').substringAfterLast('\\')
            .equals(expectedFileName, ignoreCase = true)
    }

    private fun channelFromBlob(blob: String): Int? =
        Regex("\\\"live\\.numbox\\\"\\s*:\\s*\\[\\s*(\\d+)")
            .find(blob)
            ?.groupValues?.get(1)
            ?.toIntOrNull()
}
