package dev.anthonyhfm.amethyst.editor.plugins.keyframes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.PopupProperties
import dev.anthonyhfm.amethyst.editor.plugins.keyframes.ui.components.KeyframesPlaybackControls
import dev.anthonyhfm.amethyst.editor.plugins.keyframes.ui.components.VerticalKeyframeList
import dev.anthonyhfm.amethyst.ui.modifier.platformPaddingTop
import dev.anthonyhfm.amethyst.ui.previewdevices.LaunchpadPro
import dev.anthonyhfm.amethyst.ui.previewdevices.rememberPreviewState
import kotlinx.coroutines.selects.select

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyframeEditorDialog(
    viewModel: KeyframeEditorViewModel,
    visible: Boolean,
    onDismissRequest: () -> Unit
) {
    val previewState = rememberPreviewState()
    val keyframes by viewModel.keyframeData.collectAsState()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(keyframes[state.selectedKeyframe].frame) {
        keyframes[state.selectedKeyframe].frame.forEach {
            it.forEach {
                previewState.sendToPreview(it)
            }
        }
    }

    if (visible) {
        Popup(
            onDismissRequest = {
                onDismissRequest()
            },
            properties = PopupProperties(
                focusable = true,
            )
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .platformPaddingTop(),
                topBar = {
                    TopAppBar(
                        title = {
                            Text("Keyframes Editor")
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    onDismissRequest()
                                }
                            ) {
                                Icon(Icons.Default.Close, "Close Keyframe Editor")
                            }
                        }
                    )
                },
                bottomBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        KeyframesPlaybackControls()
                    }
                }
            ) { paddingValues ->
                Row(
                    modifier = Modifier
                        .padding(paddingValues),

                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VerticalKeyframeList(
                        selectedIndex = state.selectedKeyframe,
                        keyframes = keyframes,
                        onSelect = {
                            viewModel.selectKeyframe(it)
                        },
                        onAddKeyframe = {
                            viewModel.addKeyframe(it)
                        }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),

                        contentAlignment = Alignment.Center
                    ) {
                        LaunchpadPro(
                            modifier = Modifier
                                .fillMaxHeight(),
                            previewState = previewState,
                            onClick = { x, y ->
                                viewModel.setKeyframeLight(x, y)
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .fillMaxHeight()
                            .width(250.dp)
                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.2.dp))
                            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp), RoundedCornerShape(12.dp)),
                    ) {

                    }
                }
            }
        }
    }
}