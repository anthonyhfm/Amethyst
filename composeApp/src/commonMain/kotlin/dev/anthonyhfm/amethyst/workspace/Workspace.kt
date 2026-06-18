package dev.anthonyhfm.amethyst.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.core.network.presence.CollaborationPresence
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesWorkspaceMode
import dev.anthonyhfm.amethyst.devices.effects.composition.CompositionWorkspaceMode
import dev.anthonyhfm.amethyst.timeline.Timeline
import dev.anthonyhfm.amethyst.timeline.PianoRollWorkspaceMode
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.workspace.help.GetHelpWorkspaceMode
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract.Event
import dev.anthonyhfm.amethyst.workspace.ui.components.DeviceSettingsDialog
import dev.anthonyhfm.amethyst.workspace.chain.ui.WorkspaceChainEditor
import dev.anthonyhfm.amethyst.workspace.chain.ui.MobileWorkspaceChainEditor
import dev.anthonyhfm.amethyst.workspace.ui.components.AutoPlayButtons
import dev.anthonyhfm.amethyst.workspace.ui.components.ActivityToastOverlay
import dev.anthonyhfm.amethyst.workspace.ui.components.InsertLaunchpadDialog
import dev.anthonyhfm.amethyst.workspace.ui.components.AddDeviceButton
import dev.anthonyhfm.amethyst.workspace.ui.components.WorkspaceTopAppBar
import dev.anthonyhfm.amethyst.workspace.ui.viewport.WorkspaceViewport
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberScrollAreaState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Zap
import dev.anthonyhfm.amethyst.settings.SettingsDialog
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.chainColorTokens
import dev.anthonyhfm.amethyst.ui.theme.chainCanvas

@Composable
fun Workspace(onBack: () -> Unit = {}) {
    val viewModel: WorkspaceViewModel = viewModel { WorkspaceViewModel() }

    val state by viewModel.state.collectAsState()
    val activityToasts by CollaborationPresence.activityToasts.collectAsState()

    val lightsChainScrollState = rememberScrollAreaState()
    val samplingChainScrollState = rememberScrollAreaState()

    val isLandscapeForced = isMobilePhone() && (state.mode is WorkspaceContract.WorkspaceMode.LightsChain || state.mode is WorkspaceContract.WorkspaceMode.SamplingChain)

    if (isLandscapeForced) {
        ForceScreenOrientation(landscape = true)

        var showSettingsDialog by remember { mutableStateOf(false) }

        if (showSettingsDialog) {
            SettingsDialog(
                visible = showSettingsDialog,
                onDismiss = { showSettingsDialog = false }
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme[chainColorTokens][chainCanvas])
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val isTooNarrow = maxHeight < 340.dp

            if (isTooNarrow) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Sidebar(
                        mode = state.mode,
                        onBack = onBack,
                        onSettingsClick = {
                            triggerSettingsShow {
                                showSettingsDialog = true
                            }
                        }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val requiredHeight = 280.dp
                            val scale = maxHeight.value / requiredHeight.value

                            val currentDensity = LocalDensity.current
                            val customDensity = remember(currentDensity, scale) {
                                Density(
                                    density = currentDensity.density * scale,
                                    fontScale = currentDensity.fontScale * scale
                                )
                            }

                            CompositionLocalProvider(LocalDensity provides customDensity) {
                                if (state.mode is WorkspaceContract.WorkspaceMode.LightsChain) {
                                    MobileWorkspaceChainEditor(
                                        devices = WorkspaceRepository.lightsChain.devices.value,
                                        scrollState = lightsChainScrollState,
                                        modifier = Modifier.fillMaxWidth(),
                                        onEvent = { viewModel.onEvent(it) }
                                    )
                                } else if (state.mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
                                    MobileWorkspaceChainEditor(
                                        devices = WorkspaceRepository.samplingChain.devices.value,
                                        scrollState = samplingChainScrollState,
                                        modifier = Modifier.fillMaxWidth(),
                                        onEvent = { viewModel.onEvent(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    WorkspaceTopAppBar(
                        mode = state.mode,
                        onBack = onBack,
                        onEvent = {
                            viewModel.onEvent(it)
                        }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val requiredHeight = 280.dp
                            val scale = maxHeight.value / requiredHeight.value

                            val currentDensity = LocalDensity.current
                            val customDensity = remember(currentDensity, scale) {
                                Density(
                                    density = currentDensity.density * scale,
                                    fontScale = currentDensity.fontScale * scale
                                )
                            }

                            CompositionLocalProvider(LocalDensity provides customDensity) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.mode is WorkspaceContract.WorkspaceMode.LightsChain) {
                                        MobileWorkspaceChainEditor(
                                            devices = WorkspaceRepository.lightsChain.devices.value,
                                            scrollState = lightsChainScrollState,
                                            modifier = Modifier.fillMaxWidth(),
                                            onEvent = { viewModel.onEvent(it) }
                                        )
                                    } else if (state.mode is WorkspaceContract.WorkspaceMode.SamplingChain) {
                                        MobileWorkspaceChainEditor(
                                            devices = WorkspaceRepository.samplingChain.devices.value,
                                            scrollState = samplingChainScrollState,
                                            modifier = Modifier.fillMaxWidth(),
                                            onEvent = { viewModel.onEvent(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize(),

            containerColor = Theme[colors][background],
            contentColor = Theme[colors][foreground],

            topBar = {
                WorkspaceTopAppBar(
                    mode = state.mode,
                    onBack = onBack,
                    onEvent = {
                        viewModel.onEvent(it)
                    },
                )
            }
        ) { paddingValues ->
            if (state.showDeviceConfigurator != null) {
                DeviceSettingsDialog(
                    uuid = state.showDeviceConfigurator!!,
                    onEvent = { viewModel.onEvent(it) }
                )
            }

            if (state.showDevicePicker) {
                InsertLaunchpadDialog(
                    onEvent = {
                        viewModel.onEvent(it)
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                WorkspaceViewport(
                    viewportState = state.viewportState,
                    elements = state.viewportElements,
                    onEvent = {
                        viewModel.onEvent(it)
                    }
                )

                ActivityToastOverlay(
                    toasts = activityToasts,
                    onDismiss = CollaborationPresence::dismissToast,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp),
                )

                AnimatedVisibility(
                    visible = state.mode is WorkspaceContract.WorkspaceMode.Layout,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(24.dp),
                ) {
                    AddDeviceButton(
                        onClick = {
                            viewModel.onEvent(Event.OpenVirtualDevicePicker)
                        }
                    )
                }

                AnimatedVisibility(
                    visible = state.mode is WorkspaceContract.WorkspaceMode.Performance,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                ) {
                    AutoPlayButtons()
                }

                AnimatedVisibility(
                    visible = state.mode is WorkspaceContract.WorkspaceMode.LightsChain,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                ) {
                    WorkspaceChainEditor(
                        devices = WorkspaceRepository.lightsChain.devices.value,
                        scrollState = lightsChainScrollState,
                        onEvent = { viewModel.onEvent(it) }
                    )
                }

                AnimatedVisibility(
                    visible = state.mode is WorkspaceContract.WorkspaceMode.SamplingChain,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                ) {
                    WorkspaceChainEditor(
                        devices = WorkspaceRepository.samplingChain.devices.value,
                        scrollState = samplingChainScrollState,
                        onEvent = { viewModel.onEvent(it) }
                    )
                }

                AnimatedVisibility(
                    visible = state.mode is WorkspaceContract.WorkspaceMode.Timeline,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                ) {
                    Timeline()
                }

                if (state.mode is KeyframesWorkspaceMode) {
                    (state.mode as KeyframesWorkspaceMode).ModeContent(paddingValues)
                }

                if (state.mode is CompositionWorkspaceMode) {
                    (state.mode as CompositionWorkspaceMode).ModeContent(paddingValues)
                }

                if (state.mode is PianoRollWorkspaceMode) {
                    (state.mode as PianoRollWorkspaceMode).ModeContent(paddingValues)
                }

                if (state.mode is GetHelpWorkspaceMode) {
                    (state.mode as GetHelpWorkspaceMode).ModeContent(paddingValues)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    mode: WorkspaceContract.WorkspaceMode,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Theme[colors][background])
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = {
                    if (mode.selectable) {
                        onBack()
                    } else {
                        WorkspaceRepository.switchToPreviousMode()
                    }
                }
            ) {
                Icon(
                    imageVector = Lucide.ChevronLeft,
                    contentDescription = "Back",
                    tint = Theme[colors][foreground]
                )
            }

            IconButton(
                onClick = {
                    // No action for now
                }
            ) {
                Icon(
                    imageVector = Lucide.LayoutGrid,
                    contentDescription = "Switch Mode",
                    tint = Theme[colors][foreground]
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onSettingsClick
            ) {
                Icon(
                    imageVector = Lucide.Settings,
                    contentDescription = "Settings",
                    tint = Theme[colors][foreground]
                )
            }
        }

        Spacer(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Theme[colors][border])
        )
    }
}
