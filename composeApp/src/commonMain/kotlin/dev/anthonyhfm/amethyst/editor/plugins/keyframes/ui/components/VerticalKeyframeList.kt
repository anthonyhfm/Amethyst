package dev.anthonyhfm.amethyst.editor.plugins.keyframes.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.editor.plugins.keyframes.data.Keyframe
import dev.anthonyhfm.amethyst.ui.previewdevices.LaunchpadPro
import dev.anthonyhfm.amethyst.ui.previewdevices.rememberPreviewState

@Composable
fun VerticalKeyframeList(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    keyframes: List<Keyframe>,
    onAddKeyframe: (Int?) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .fillMaxHeight()
            .width(250.dp)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.2.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp), RoundedCornerShape(12.dp))
            .padding(top = 8.dp),
    ) {
        itemsIndexed(keyframes) { index, it ->
            val previewState = rememberPreviewState()

            LaunchedEffect(it.frame) {
                it.frame.forEach {
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

                if (index == keyframes.size - 1) {
                    AddKeyframeButton(
                        expanded = true,
                        onAddKeyframe = {
                            onAddKeyframe(null)
                        }
                    )
                }
            }
        }
    }
}