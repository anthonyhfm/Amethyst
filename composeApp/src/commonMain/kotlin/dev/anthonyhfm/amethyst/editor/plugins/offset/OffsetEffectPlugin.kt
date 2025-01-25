package dev.anthonyhfm.amethyst.editor.plugins.offset

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.midi.data.MidiEffectData
import dev.anthonyhfm.amethyst.editor.plugins.EffectPlugin
import dev.anthonyhfm.amethyst.ui.previewdevices.LaunchpadPro
import kotlinx.coroutines.flow.MutableStateFlow

class OffsetEffectPlugin : EffectPlugin() {
    override var isEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    private val offsetX: MutableState<Int> = mutableStateOf(0)
    private val offsetY: MutableState<Int> = mutableStateOf(0)

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .fillMaxHeight()
                .width(150.dp)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp), RoundedCornerShape(6.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            ) {
                Text(
                    text = "Offset",
                    style = MaterialTheme.typography.labelLarge,
                    lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                    modifier = Modifier
                        .align(Alignment.Center)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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