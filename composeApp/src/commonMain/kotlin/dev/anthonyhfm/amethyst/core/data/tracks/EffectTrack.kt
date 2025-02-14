package dev.anthonyhfm.amethyst.core.data.tracks

import kotlinx.coroutines.flow.MutableStateFlow
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.core.midi.devices.DeviceType
import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import dev.atsushieno.ktmidi.MidiOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EffectTrack(
    override val name: String,
    var projectDeviceIndex: Int? = null
) : Track {
    var midiOutput: MidiOutput? = null
    var deviceType: DeviceType? = null

    private val _effects = MutableStateFlow<List<EffectDevice>>(emptyList())
    val effects = _effects.asStateFlow()

    fun <T : EffectDevice> addEffect(effect: T, atIndex: Int? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            if (atIndex == null) {
                _effects.emit(
                    value = _effects.value.plus(effect)
                )
            } else {
                val mutableList = _effects.value.toMutableList()

                mutableList.add(atIndex, effect)

                _effects.emit(mutableList)
            }

            _effects.emit(
                _effects.value.mapIndexed { index, effectPlugin ->
                    if (index + 1 < _effects.value.size) {
                        effectPlugin.midiOutput = {
                            CoroutineScope(Dispatchers.IO).launch {
                                _effects.value[index + 1].passData(it)
                            }
                        }

                        return@mapIndexed effectPlugin
                    } else {
                        effectPlugin.midiOutput = {
                            outputMidiEffectData(it)
                        }

                        return@mapIndexed effectPlugin
                    }
                }
            )
        }
    }

    fun processMidiInputData(midiInputData: MidiInputData) {
        val white = if (midiInputData.velocity == 0) 0 else 63

        val midiEffectData = MidiEffectData(
            x = midiInputData.pitch % 10,
            y = midiInputData.pitch / 10,
            r = white,
            g = white,
            b = white
        )

        if (effects.value.isEmpty()) {
            outputMidiEffectData(midiEffectData)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                effects.value[0].passData(midiEffectData)
            }
        }
    }

    private fun outputMidiEffectData(data: MidiEffectData) {
        CoroutineScope(Dispatchers.IO).launch {
            val outputData = deviceType?.getEffectSysEx(data) ?: return@launch

            midiOutput?.send(
                mevent = outputData,
                length = outputData.size,
                offset = 0,
                timestampInNanoseconds = 0
            )
        }
    }
}