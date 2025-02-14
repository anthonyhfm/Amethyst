package dev.anthonyhfm.amethyst.devices.effects.delay

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin
import dev.anthonyhfm.amethyst.ui.components.TextDial
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class DelayEffectDevice : EffectDevice() {
    val delay: MutableStateFlow<Int> = MutableStateFlow(0)

    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val delayState by delay.collectAsState()

        AmethystPlugin(
            title = "Delay",
            modifier = Modifier
                .width(100.dp)
        ) {
            TextDial(
                headline = "Delay",
                text = "$delayState ms",
                value = delayState / 1000f,
                onValueChange = {
                    scope.launch {
                        delay.emit((it * 1000).toInt())
                    }
                }
            )
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        delay(delay.value.milliseconds)

        midiOutput(data)
    }
}