package dev.anthonyhfm.amethyst.workspace.chain

import androidx.compose.ui.geometry.Offset
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.core.midi.data.MidiInputData
import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import dev.anthonyhfm.amethyst.workspace.ui.viewport.elements.LaunchpadViewportElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.collections.plus
import kotlin.math.abs

class WorkspaceChain {
    val launchpadElements: MutableStateFlow<List<LaunchpadViewportElement>> = MutableStateFlow(listOf())

    val devices: MutableStateFlow<List<EffectDevice<*>>> = MutableStateFlow(listOf())

    fun onMidiInput(inputData: MidiInputData, offset: Offset) {
        val x = inputData.pitch % 10
        val y = inputData.pitch / 10

        val posX = offset.x.toInt()
        val posY = offset.y.toInt()

        // This will be improved by the time im reimplementing heaven. just testing
        val effect = MidiEffectData(
            x = posX + x,
            y = posY + (9 - y),
            r = if (inputData.velocity == 0) 0 else 63,
            g = if (inputData.velocity == 0) 0 else 63,
            b = if (inputData.velocity == 0) 0 else 63
        )

        if (devices.value.isEmpty()) {
            sendToOutput(effect)
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                devices.value[0].passData(effect)
            }
        }
    }

    fun sendToOutput(effect: MidiEffectData) {
        launchpadElements.value.forEach { device ->
            if (
                effect.x - device.layout.offsetX in device.position.value.x.toInt() .. (device.position.value.x.toInt() + device.size.width.toInt() - 1)
                && effect.y - device.layout.offsetY in device.position.value.y.toInt() .. (device.position.value.y.toInt() + device.size.height.toInt() - 1)
            ) {
                val x: Int = effect.x - device.position.value.x.toInt()
                val y: Int = abs(effect.y - 9 - device.position.value.y.toInt())

                effect.copy(x = x, y = y).let { correctedEffect ->
                    device.previewState.sendToPreview(correctedEffect)

                    device.deviceConfig.type?.getEffectSysEx(correctedEffect)?.let {
                        device.deviceConfig.output?.send(it, 0, it.size, 0L)
                    }
                }

                return
            }
        }
    }

    fun addDevice(device: EffectDevice<*>, atIndex: Int?) {
        runBlocking {
            if (atIndex == null) {
                devices.emit(
                    value = devices.value.plus(device)
                )
            } else {
                val mutableList = devices.value.toMutableList()
                mutableList.add(atIndex, device)

                devices.emit(mutableList)
            }

            devices.emit(
                devices.value.mapIndexed { index, effectPlugin ->
                    if (index + 1 < devices.value.size) {
                        effectPlugin.midiOutput = {
                            CoroutineScope(Dispatchers.IO).launch {
                                devices.value[index + 1].passData(it)
                            }
                        }

                        return@mapIndexed effectPlugin
                    } else {
                        effectPlugin.midiOutput = {
                            sendToOutput(it)
                        }

                        return@mapIndexed effectPlugin
                    }
                }
            )
        }
    }
}