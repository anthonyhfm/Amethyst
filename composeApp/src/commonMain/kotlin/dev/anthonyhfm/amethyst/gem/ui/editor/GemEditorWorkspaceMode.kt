package dev.anthonyhfm.amethyst.gem.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.rememberDialogState
import dev.anthonyhfm.amethyst.gem.GemNodeRegistry
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialog
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogAction
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogCancel
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogDescription
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogFooter
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogHeader
import dev.anthonyhfm.amethyst.ui.components.primitives.AlertDialogTitle
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonVariant
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyH2
import dev.anthonyhfm.amethyst.ui.components.primitives.TypographyMuted
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

class GemEditorWorkspaceMode(
    initialAssetId: String? = null,
    private val entryContext: EntryContext = EntryContext.Workspace(),
    createNewAsset: Boolean = false,
    override val displayName: String = "Gem Editor",
    override val selectable: Boolean = false
) : WorkspaceContract.WorkspaceMode {
    sealed interface EntryContext {
        val preferredHostDomain: GemSignalDomain?
        val breadcrumbLabel: String
        val summaryLabel: String

        data class Workspace(
            val sourceLabel: String = "Workspace Gems",
            override val preferredHostDomain: GemSignalDomain? = null
        ) : EntryContext {
            override val breadcrumbLabel: String = sourceLabel
            override val summaryLabel: String = buildString {
                append("Opened from ")
                append(sourceLabel)
                preferredHostDomain?.let {
                    append(" in ")
                    append(it.label())
                    append(" context")
                }
            }
        }

        data class HostDevice(
            override val preferredHostDomain: GemSignalDomain,
            val deviceLabel: String = "Gem Device",
            val referencedAssetId: String,
            val referencedAssetName: String = referencedAssetId
        ) : EntryContext {
            override val breadcrumbLabel: String = "$deviceLabel · ${preferredHostDomain.label()}"
            override val summaryLabel: String =
                "Opened from $deviceLabel in ${preferredHostDomain.label()} host context"
        }
    }

    private var activeAssetId by mutableStateOf(
        initialAssetId ?: if (createNewAsset) {
            WorkspaceRepository.createGemAsset(entryContext.preferredHostDomain).metadata.id
        } else {
            null
        }
    )

    private var keyHandler: ((KeyEvent) -> Boolean)? = null
    private var onToolbarOpenNodePalette: (() -> Unit)? = null
    private var onToolbarCreateAsset: (() -> Unit)? = null
    private var onToolbarDiscard: (() -> Unit)? = null
    private var onToolbarUndo: (() -> Unit)? = null
    private var onToolbarRedo: (() -> Unit)? = null

    internal var canOpenNodePalette by mutableStateOf(false)
        private set
    internal var canCreateAsset by mutableStateOf(false)
        private set
    internal var canDiscard by mutableStateOf(false)
        private set
    internal var canUndo by mutableStateOf(false)
        private set
    internal var canRedo by mutableStateOf(false)
        private set

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type == KeyEventType.KeyDown &&
            (event.isCtrlPressed || event.isMetaPressed) &&
            event.key == Key.W) {
            WorkspaceRepository.switchToPreviousMode()
            return true
        }
        return keyHandler?.invoke(event) == true || super.onKeyEvent(event)
    }

    internal fun openNodePalette() {
        onToolbarOpenNodePalette?.invoke()
    }

    internal fun createAsset() {
        onToolbarCreateAsset?.invoke()
    }

    internal fun discard() {
        onToolbarDiscard?.invoke()
    }

    internal fun undo() {
        onToolbarUndo?.invoke()
    }

    internal fun redo() {
        onToolbarRedo?.invoke()
    }

    private fun clearToolbarState() {
        onToolbarOpenNodePalette = null
        onToolbarCreateAsset = null
        onToolbarDiscard = null
        onToolbarUndo = null
        onToolbarRedo = null
        canOpenNodePalette = false
        canCreateAsset = false
        canDiscard = false
        canUndo = false
        canRedo = false
    }

    @Composable
    fun ModeContent(paddingValues: PaddingValues) {
        val registry = remember { GemNodeRegistry.builtIns }
        val workspaceAssets by WorkspaceRepository.gemAssets.collectAsState()
        var nodePaletteVisible by remember { mutableStateOf(false) }
        var fitToContentTrigger by remember { mutableStateOf(0) }
        var session by remember { mutableStateOf<GemEditorSession?>(null) }
        var showDiscardConfirmDialog by remember { mutableStateOf(false) }

        val eligibleAssets = remember(workspaceAssets, activeAssetId, entryContext) {
            workspaceAssets.filter { asset ->
                entryContext.preferredHostDomain == null ||
                    entryContext.preferredHostDomain in asset.definition.host.supportedDomains ||
                    asset.metadata.id == activeAssetId
            }
        }
        val workspaceAsset = remember(workspaceAssets, activeAssetId) {
            workspaceAssets.firstOrNull { it.metadata.id == activeAssetId }
        }

        LaunchedEffect(workspaceAssets, eligibleAssets, activeAssetId) {
            val currentExists = activeAssetId != null && workspaceAssets.any { it.metadata.id == activeAssetId }
            if (!currentExists) {
                eligibleAssets.firstOrNull()?.let { activeAssetId = it.metadata.id }
            }
        }

        if (workspaceAsset == null) {
            SideEffect {
                keyHandler = null
                clearToolbarState()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TypographyH2("No Gem selected")
                    TypographyMuted("Create a workspace Gem to start editing on the canvas.")
                    Button(
                        onClick = {
                            activeAssetId = WorkspaceRepository.createGemAsset(
                                entryContext.preferredHostDomain
                            ).metadata.id
                        }
                    ) {
                        Text("New Gem")
                    }
                }
            }
            return
        }

        LaunchedEffect(workspaceAsset) {
            val current = session
            if (current == null || (!current.state.value.isDirty && current.state.value.originalAsset != workspaceAsset)) {
                session = GemEditorSession(
                    initialAsset = workspaceAsset,
                    registry = registry,
                    persistAsset = { asset, previousAssetId ->
                        val persisted = WorkspaceRepository.saveGemAsset(
                            asset = asset,
                            previousAssetId = previousAssetId
                        )
                        activeAssetId = persisted.metadata.id
                        persisted
                    }
                )
            }
        }

        val editorSession = session
        if (editorSession == null) {
            SideEffect {
                keyHandler = null
                clearToolbarState()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                TypographyMuted("Starting Gem editor session…")
            }
            return
        }

        val sessionState by editorSession.state.collectAsState()
        val asset = sessionState.editedAsset

        fun handleTransitionResult(result: GemEditorTransitionResult) {
            when (result) {
                is GemEditorTransitionResult.AssetOpened -> activeAssetId = result.asset.metadata.id
                GemEditorTransitionResult.ExitApproved -> WorkspaceRepository.switchToPreviousMode()
                GemEditorTransitionResult.Cancelled,
                GemEditorTransitionResult.NoOp,
                is GemEditorTransitionResult.PromptRequired -> Unit
            }
        }

        LaunchedEffect(sessionState.originalAsset.metadata.id) {
            if (sessionState.originalAsset.metadata.id.isNotBlank()) {
                activeAssetId = sessionState.originalAsset.metadata.id
            }
            nodePaletteVisible = false
        }

        SideEffect {
            keyHandler = { event ->
                handleGemEditorKeyEvent(
                    event = event,
                    session = editorSession,
                    onShowNodePalette = { nodePaletteVisible = true },
                    onFitToContent = { fitToContentTrigger++ },
                    onDuplicateAsset = {
                        val duplicate = WorkspaceRepository.duplicateGemAsset(asset)
                        handleTransitionResult(editorSession.requestAssetSwitch(duplicate))
                    },
                    onNewAsset = {
                        handleTransitionResult(
                            editorSession.requestAssetSwitch(
                                WorkspaceRepository.createGemAsset(entryContext.preferredHostDomain)
                            )
                        )
                    }
                )
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                keyHandler = null
                clearToolbarState()
            }
        }

        SideEffect {
            canOpenNodePalette = true
            canCreateAsset = true
            canDiscard = sessionState.isDirty
            canUndo = sessionState.canUndo
            canRedo = sessionState.canRedo
            onToolbarOpenNodePalette = { nodePaletteVisible = true }
            onToolbarCreateAsset = {
                handleTransitionResult(
                    editorSession.requestAssetSwitch(
                        WorkspaceRepository.createGemAsset(entryContext.preferredHostDomain)
                    )
                )
            }
            onToolbarDiscard = { showDiscardConfirmDialog = true }
            onToolbarUndo = { editorSession.undo() }
            onToolbarRedo = { editorSession.redo() }
        }

        LaunchedEffect(sessionState.editedAsset) {
            if (sessionState.isDirty) {
                kotlinx.coroutines.delay(500)
                editorSession.save()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            GemCanvasEditor(
                session = editorSession,
                registry = registry,
                nodePaletteVisible = nodePaletteVisible,
                onNodePaletteVisibilityChange = { nodePaletteVisible = it },
                fitToContentTrigger = fitToContentTrigger,
                modifier = Modifier.fillMaxSize()
            )
        }

        sessionState.pendingTransition?.let { pending ->
            GemUnsavedChangesDialog(
                pending = pending,
                onSave = {
                    handleTransitionResult(
                        editorSession.resolvePendingTransition(GemEditorPendingDecision.Save)
                    )
                },
                onDiscard = {
                    handleTransitionResult(
                        editorSession.resolvePendingTransition(GemEditorPendingDecision.Discard)
                    )
                },
                onCancel = {
                    editorSession.resolvePendingTransition(GemEditorPendingDecision.Cancel)
                }
            )
        }

        if (showDiscardConfirmDialog) {
            val dialogState = rememberDialogState(initiallyVisible = true)
            AlertDialog(
                state = dialogState,
                onDismiss = { showDiscardConfirmDialog = false }
            ) {
                AlertDialogHeader {
                    AlertDialogTitle("Discard changes?")
                    AlertDialogDescription(
                        "All unsaved edits to \"${asset.metadata.name.ifBlank { "this Gem" }}\" will be permanently lost."
                    )
                }
                AlertDialogFooter {
                    AlertDialogCancel(onClick = { showDiscardConfirmDialog = false }) {
                        Text("Cancel")
                    }
                    AlertDialogAction(
                        onClick = {
                            showDiscardConfirmDialog = false
                            editorSession.discardChanges()
                        },
                        variant = ButtonVariant.Destructive
                    ) {
                        Text("Discard")
                    }
                }
            }
        }
    }
}

private fun GemSignalDomain.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }
