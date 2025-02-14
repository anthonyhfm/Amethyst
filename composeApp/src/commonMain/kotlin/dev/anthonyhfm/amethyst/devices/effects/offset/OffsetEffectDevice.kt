package dev.anthonyhfm.amethyst.devices.effects.offset

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystPlugin

class OffsetEffectDevice : EffectDevice() {
    private val offsetX: MutableState<Int> = mutableStateOf(0)
    private val offsetY: MutableState<Int> = mutableStateOf(0)

    @Composable
    override fun Content() {
        AmethystPlugin(
            title = "Offset",
            modifier = Modifier
                .width(145.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Text(
                    text = "No UI for this yet lmao"
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Offset X: ${offsetX.value}"
                )

                Text(
                    text = "Offset Y: ${offsetY.value}"
                )

                Spacer(
                    modifier = Modifier
                        .weight(1f)
                )

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = null,
                        modifier = Modifier
                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                            .padding(2.dp)
                            .clickable {
                                offsetY.value += 1
                            }
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier
                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                            .padding(2.dp)
                            .clickable {
                                offsetY.value -= 1
                            }
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier
                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                            .padding(2.dp)
                            .clickable {
                                offsetX.value -= 1
                            }
                    )

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier
                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                            .padding(2.dp)
                            .clickable {
                                offsetX.value += 1
                            }
                    )
                }
            }
        }
    }

    override suspend fun passData(data: MidiEffectData) {
        midiOutput(
            data.copy(
                x = data.x + offsetX.value,
                y = data.y + offsetY.value
            )
        )
    }
}