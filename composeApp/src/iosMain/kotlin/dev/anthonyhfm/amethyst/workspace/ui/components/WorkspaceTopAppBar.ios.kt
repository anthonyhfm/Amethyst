package dev.anthonyhfm.amethyst.workspace.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.modes.WorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.PerformanceWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.TimelineWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LightsChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.SamplingChainWorkspaceMode
import dev.anthonyhfm.amethyst.workspace.modes.defaults.LayoutWorkspaceMode
import platform.UIKit.*

private data class IosModeEntry(
    val mode: WorkspaceMode,
    val label: String,
    val iconName: String,
)

private val selectableModes = listOf(
    IosModeEntry(PerformanceWorkspaceMode(), "Performance", "play.fill"),
    IosModeEntry(TimelineWorkspaceMode(), "Timeline", "chart.bar.doc.horizontal"),
    IosModeEntry(LightsChainWorkspaceMode(), "Lights", "lightbulb.fill"),
    IosModeEntry(SamplingChainWorkspaceMode(), "Sampling", "waveform.path"),
    IosModeEntry(LayoutWorkspaceMode(), "Layout", "square.grid.3x3.fill"),
)

private fun modeMatches(
    current: WorkspaceMode,
    candidate: WorkspaceMode,
): Boolean = when {
    current is PerformanceWorkspaceMode && candidate is PerformanceWorkspaceMode -> true
    current is TimelineWorkspaceMode && candidate is TimelineWorkspaceMode -> true
    current is LightsChainWorkspaceMode && candidate is LightsChainWorkspaceMode -> true
    current is SamplingChainWorkspaceMode && candidate is SamplingChainWorkspaceMode -> true
    current is LayoutWorkspaceMode && candidate is LayoutWorkspaceMode -> true
    else -> false
}

@OptIn(ExperimentalComposeUiApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
@Composable
actual fun WorkspaceTopAppBar(
    onBack: () -> Unit,
    mode: WorkspaceMode,
    onEvent: (WorkspaceContract.Event) -> Unit,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp + statusBarHeight + 16.dp),
    ) {
        UIKitView(
            factory = {
                UIView().apply {
                    backgroundColor = UIColor.clearColor
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { containerView ->
                containerView.rebuildWorkspaceTopAppBar(
                    mode = mode,
                    onBack = onBack,
                )
            },
            properties = UIKitInteropProperties(
                placedAsOverlay = true,
            ),
        )
    }
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun UIView.rebuildWorkspaceTopAppBar(
    mode: WorkspaceMode,
    onBack: () -> Unit,
) {
    subviews.forEach { (it as UIView).removeFromSuperview() }

    val glassContainer = IosWorkspaceBridge.createLiquidGlassContainerEffect?.invoke()?.let { effect ->
        UIVisualEffectView(effect = effect)
    } ?: UIView()
    glassContainer.translatesAutoresizingMaskIntoConstraints = false
    glassContainer.backgroundColor = UIColor.clearColor
    addSubview(glassContainer)

    NSLayoutConstraint.activateConstraints(
        listOf(
            glassContainer.leftAnchor.constraintEqualToAnchor(leftAnchor),
            glassContainer.rightAnchor.constraintEqualToAnchor(rightAnchor),
            glassContainer.topAnchor.constraintEqualToAnchor(topAnchor),
            glassContainer.bottomAnchor.constraintEqualToAnchor(bottomAnchor),
        ),
    )

    val stackParent = (glassContainer as? UIVisualEffectView)?.contentView ?: glassContainer
    val stackView = UIStackView()
    stackView.translatesAutoresizingMaskIntoConstraints = false
    stackView.axis = 0
    stackView.alignment = UIStackViewAlignmentCenter
    stackView.distribution = UIStackViewDistributionFill
    stackView.spacing = if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) 18.0 else 16.0
    stackParent.addSubview(stackView)

    NSLayoutConstraint.activateConstraints(
        listOf(
            stackView.leftAnchor.constraintEqualToAnchor(stackParent.leftAnchor, constant = 16.0),
            stackView.rightAnchor.constraintEqualToAnchor(stackParent.rightAnchor, constant = -16.0),
            stackView.topAnchor.constraintEqualToAnchor(safeAreaLayoutGuide.topAnchor, constant = 8.0),
            stackView.heightAnchor.constraintEqualToConstant(50.0),
        ),
    )

    val leftButton = UIButton.buttonWithType(UIButtonTypeSystem)
    leftButton.configuration = liquidGlassButtonConfiguration().apply {
        image = UIImage.systemImageNamed(if (mode.selectableMode) "chevron.left" else "xmark")
        baseForegroundColor = UIColor.labelColor
    }
    leftButton.setAccessibilityLabel(if (mode.selectableMode) "Back to home" else "Close ${mode.displayName}")
    leftButton.addAction(
        UIAction.actionWithHandler {
            if (mode.selectableMode) {
                onBack()
            } else {
                WorkspaceRepository.switchToPreviousMode()
            }
        },
        forControlEvents = UIControlEventTouchUpInside,
    )
    stackView.addArrangedSubview(leftButton)
    leftButton.constrainSize(width = 50.0, height = 50.0)

    stackView.addArrangedSubview(
        if (mode.selectableMode) {
            modeMenuButton(mode)
        } else {
            modeTitleButton(mode.displayName)
        },
    )

    val spacer = UIView()
    stackView.addArrangedSubview(spacer)

    val settingsButton = UIButton.buttonWithType(UIButtonTypeSystem)
    settingsButton.configuration = liquidGlassButtonConfiguration().apply {
        image = UIImage.systemImageNamed("gearshape")
        baseForegroundColor = UIColor.labelColor
    }
    settingsButton.setAccessibilityLabel("Open settings")
    settingsButton.addAction(
        UIAction.actionWithHandler {
            IosWorkspaceBridge.onShowSettings?.invoke()
        },
        forControlEvents = UIControlEventTouchUpInside,
    )
    stackView.addArrangedSubview(settingsButton)
    settingsButton.constrainSize(width = 50.0, height = 50.0)
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun modeMenuButton(mode: WorkspaceMode): UIView {
    val selectedEntry = selectableModes.firstOrNull { modeMatches(mode, it.mode) }
    val menuButton = UIButton.buttonWithType(UIButtonTypeSystem)
    menuButton.configuration = liquidGlassButtonConfiguration().apply {
        title = selectedEntry?.label ?: mode.displayName
        image = selectedEntry?.let { UIImage.systemImageNamed(it.iconName) }
        imagePadding = 7.0
        baseForegroundColor = UIColor.labelColor
        contentInsets = NSDirectionalEdgeInsetsMake(8.0, 20.0, 8.0, 20.0)
    }
    menuButton.setAccessibilityLabel("Switch workspace mode")

    val menuActions = selectableModes.map { entry ->
        UIAction.actionWithTitle(
            title = entry.label,
            image = UIImage.systemImageNamed(entry.iconName),
            identifier = null,
            handler = {
                WorkspaceRepository.switchMode(entry.mode)
            },
        ).apply {
            state = if (modeMatches(mode, entry.mode)) {
                UIMenuElementState.UIMenuElementStateOn
            } else {
                UIMenuElementState.UIMenuElementStateOff
            }
        }
    }
    menuButton.setMenu(UIMenu.menuWithTitle(title = "", children = menuActions))
    menuButton.setShowsMenuAsPrimaryAction(true)
    menuButton.setChangesSelectionAsPrimaryAction(false)
    menuButton.setContentHuggingPriority(1000.0f, forAxis = 0)
    menuButton.setContentCompressionResistancePriority(1000.0f, forAxis = 0)
    menuButton.constrainHeight(50.0)

    return menuButton
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun modeTitleButton(title: String): UIView {
    val titleButton = UIButton.buttonWithType(UIButtonTypeSystem)
    titleButton.configuration = liquidGlassButtonConfiguration().apply {
        this.title = title
        baseForegroundColor = UIColor.labelColor
        contentInsets = NSDirectionalEdgeInsetsMake(8.0, 20.0, 8.0, 20.0)
    }
    titleButton.userInteractionEnabled = false
    titleButton.setAccessibilityLabel(title)
    titleButton.setContentHuggingPriority(1000.0f, forAxis = 0)
    titleButton.setContentCompressionResistancePriority(1000.0f, forAxis = 0)
    titleButton.constrainHeight(50.0)

    return titleButton
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun UIView.constrainSize(width: Double, height: Double) {
    translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activateConstraints(
        listOf(
            widthAnchor.constraintEqualToConstant(width),
            heightAnchor.constraintEqualToConstant(height),
        ),
    )
}

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private fun UIView.constrainHeight(height: Double) {
    translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activateConstraints(
        listOf(
            heightAnchor.constraintEqualToConstant(height),
        ),
    )
}

private fun liquidGlassButtonConfiguration(): UIButtonConfiguration =
    IosWorkspaceBridge.createLiquidGlassButtonConfiguration?.invoke()
        ?: UIButtonConfiguration.borderedButtonConfiguration().apply {
            cornerStyle = UIButtonConfigurationCornerStyleCapsule
        }
