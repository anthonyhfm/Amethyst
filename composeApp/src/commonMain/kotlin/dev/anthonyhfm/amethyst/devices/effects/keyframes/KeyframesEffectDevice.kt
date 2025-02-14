package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.data.Keyframe
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.KeyframeEditorDialog
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.KeyframeEditorViewModel
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class KeyframesEffectDevice : EffectDevice() {
    val keyframeData: MutableStateFlow<List<Keyframe>> = MutableStateFlow(
        value = listOf(
            Keyframe()
        )
    )

    val renderedData: MutableList<Pair<Duration, MutableList<MidiEffectData>>> = mutableListOf()

    @Composable
    override fun Content() {
        var editorVisible: Boolean by remember { mutableStateOf(false) }
        val editorViewModel = remember { KeyframeEditorViewModel(keyframeData) }

        LaunchedEffect(keyframeData.collectAsState().value) {
            prerenderKeyframes()
        }

        AmethystPlugin(
            title = "Keyframes",
            modifier = Modifier
                .width(120.dp)
        ) {
            KeyframeEditorDialog(
                viewModel = editorViewModel,
                visible = editorVisible,
                onDismissRequest = {
                    editorVisible = false
                }
            )

            Button(
                onClick = {
                    editorVisible = true
                },
                modifier = Modifier
                    .align(Alignment.Center)
            ) {
                Text("Open")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun prerenderKeyframes() {
        renderedData.clear()

        GlobalScope.launch {
            keyframeData.value.forEachIndexed { keyframeIndex, it ->
                val frame = it.frame.flatten()
                val lastFrame = keyframeData.value.getOrNull(keyframeIndex - 1)?.frame?.flatten()

                renderedData.add(
                    Pair<Duration, MutableList<MidiEffectData>>(
                        first = 30.milliseconds,
                        second = frame.filterIndexed { index, effect ->
                            !effect.isEmpty() || (lastFrame?.get(index)?.isEmpty() == false && effect.isEmpty())
                        }.mapIndexed { index, effect ->
                            if (effect.isEmpty() && lastFrame?.get(index)?.isEmpty() == false) {
                                effect.copy(r = 0, g = 0, b = 0)
                            } else {
                                effect
                            }
                        }.toMutableList()
                    )
                )
            }

            renderedData.add(
                renderedData.last().copy(
                    second = renderedData
                        .last()
                        .second
                        .filter {
                            !it.isEmpty()
                        }
                        .map {
                            it.copy(r = 0, g = 0, b = 0)
                        }.toMutableList()
                )
            )
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        if (data.r != 0 || data.g != 0 || data.b != 0) {
            CoroutineScope(Dispatchers.Main).launch {
                renderedData.forEach { (duration, frame) ->
                    frame.forEach {
                        midiOutput(it)
                    }

                    delay(duration)
                }
            }
        }
    }
}