package dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.keyframes.data.Keyframe
import dev.anthonyhfm.amethyst.ui.previewdevices.LaunchpadPro
import dev.anthonyhfm.amethyst.ui.previewdevices.rememberPreviewState
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun VerticalKeyframeList(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onPositionChange: (before: Int, after: Int) -> Unit,
    keyframes: List<Keyframe>,
    onAddKeyframe: (Int?) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .fillMaxHeight()
            .width(220.dp)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.2.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp), RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp)
    ) {
        ReorderableColumn(
            list = keyframes,
            onSettle = { from, to ->
                onPositionChange(from, to)
            },
        ) { index, item, _ ->
            key(item.uuid) {
                val previewState = rememberPreviewState()

                LaunchedEffect(item.frame) {
                    item.frame.forEach {
                        it.forEach {
                            previewState.sendToPreview(it)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                ) {
                    AddKeyframeButton(
                        onAddKeyframe = {
                            onAddKeyframe(index)
                        }
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .aspectRatio(1f / 1f)
                            .fillMaxWidth()
                            .background(
                                if (selectedIndex == index) {
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp)
                                } else {
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                                }
                            )
                            .clickable {
                                onSelect(index)
                            }
                            .draggableHandle()
                    ) {
                        LaunchpadPro(
                            previewState = previewState,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxSize()
                                .alpha(
                                    alpha = if (selectedIndex == index) {
                                        1f
                                    } else {
                                        0.7f
                                    }
                                )
                        )
                    }
                }
            }
        }

        AddKeyframeButton(
            expanded = true,
            onAddKeyframe = {
                onAddKeyframe(null)
            }
        )
    }
}