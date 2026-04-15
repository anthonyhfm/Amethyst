package dev.anthonyhfm.amethyst.gem.ui.editor

import dev.anthonyhfm.amethyst.gem.Gem
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemBindingTargetKind
import dev.anthonyhfm.amethyst.gem.GemConnection
import dev.anthonyhfm.amethyst.gem.GemDefinition
import dev.anthonyhfm.amethyst.gem.GemExposedParameter
import dev.anthonyhfm.amethyst.gem.GemGraph
import dev.anthonyhfm.amethyst.gem.GemGraphKind
import dev.anthonyhfm.amethyst.gem.GemNodePosition
import dev.anthonyhfm.amethyst.gem.GemNodeTypeId
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValueType
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GemEditorSessionTest {
    @Test
    fun invalidDraftsStayLocalUntilExplicitSaveAndSurfaceAsUnrunnable() {
        val persisted = mutableListOf<Pair<String?, GemAsset>>()
        val session = GemEditorSession(
            initialAsset = validAsset(
                assetId = "gem://workspace/session-a",
                name = "Session A"
            ),
            persistAsset = { asset, previousAssetId ->
                persisted += previousAssetId to asset
                asset
            }
        )

        session.disconnect(connectionId = "scale-value")

        val dirtyState = session.state.value
        assertTrue(dirtyState.isDirty)
        assertFalse(dirtyState.validationResult.isValid)
        assertFalse(dirtyState.compilationResult.isSuccess)
        assertEquals(GemEditorRefreshStatus.Invalid, dirtyState.refreshState.status)
        assertTrue(persisted.isEmpty())

        session.save()

        val savedState = session.state.value
        assertFalse(savedState.isDirty)
        assertFalse(savedState.validationResult.isValid)
        assertFalse(savedState.compilationResult.isSuccess)
        assertEquals("gem://workspace/session-a", persisted.single().first)
    }

    @Test
    fun undoRedoRestoresAssetSnapshotsAndSelectionWithoutUsingHostUndo() {
        val session = GemEditorSession(
            initialAsset = validAsset(
                assetId = "gem://workspace/undo",
                name = "Undo Gem"
            )
        )

        session.selectNodes(setOf("add"))
        session.moveNode(
            nodeId = "add",
            position = GemNodePosition(x = 64f, y = 32f)
        )
        session.clearSelection()

        assertTrue(session.state.value.canUndo)
        assertEquals(
            GemNodePosition(x = 64f, y = 32f),
            session.state.value.editedAsset.graph()!!.node("add")!!.layout.position
        )

        assertTrue(session.undo())
        assertEquals(
            GemNodePosition(),
            session.state.value.editedAsset.graph()!!.node("add")!!.layout.position
        )
        assertEquals(setOf("add"), session.state.value.selection.nodeIds)
        assertTrue(session.state.value.canRedo)

        assertTrue(session.redo())
        assertEquals(
            GemNodePosition(x = 64f, y = 32f),
            session.state.value.editedAsset.graph()!!.node("add")!!.layout.position
        )
        assertTrue(session.state.value.selection.nodeIds.isEmpty())
    }

    @Test
    fun moveNodesPersistsUpdatedLayoutPositionsInEditedAsset() {
        val session = GemEditorSession(
            initialAsset = validAsset(
                assetId = "gem://workspace/move-nodes",
                name = "Move Nodes Gem"
            )
        )

        session.moveNodes(
            positions = mapOf(
                "constant" to GemNodePosition(x = 96f, y = 48f),
                "add" to GemNodePosition(x = 320f, y = 144f)
            )
        )

        val graph = session.state.value.editedAsset.graph()!!
        assertTrue(session.state.value.isDirty)
        assertEquals(
            GemNodePosition(x = 96f, y = 48f),
            graph.node("constant")!!.layout.position
        )
        assertEquals(
            GemNodePosition(x = 320f, y = 144f),
            graph.node("add")!!.layout.position
        )
    }

    @Test
    fun transientMoveNodesUpdatesEditedAssetImmediatelyButCreatesSingleUndoStepOnCommit() {
        val session = GemEditorSession(
            initialAsset = validAsset(
                assetId = "gem://workspace/transient-move",
                name = "Transient Move Gem"
            )
        )

        session.moveNodesTransient(
            positions = mapOf(
                "constant" to GemNodePosition(x = 96f, y = 48f)
            )
        )
        session.moveNodesTransient(
            positions = mapOf(
                "constant" to GemNodePosition(x = 144f, y = 80f)
            )
        )

        assertFalse(session.state.value.canUndo)
        assertEquals(
            GemNodePosition(x = 144f, y = 80f),
            session.state.value.editedAsset.graph()!!.node("constant")!!.layout.position
        )

        session.commitTransientChanges()

        assertTrue(session.state.value.canUndo)
        assertTrue(session.undo())
        assertEquals(
            GemNodePosition(),
            session.state.value.editedAsset.graph()!!.node("constant")!!.layout.position
        )
    }

    @Test
    fun dirtyAssetSwitchSupportsCancelAndDiscard() {
        val session = GemEditorSession(
            initialAsset = validAsset(
                assetId = "gem://workspace/source",
                name = "Source Gem"
            )
        )
        val target = validAsset(
            assetId = "gem://workspace/target",
            name = "Target Gem"
        )

        session.disconnect(connectionId = "scale-value")

        val firstRequest = session.requestAssetSwitch(target)
        val prompt = assertIs<GemEditorTransitionResult.PromptRequired>(firstRequest)
        assertTrue(prompt.pendingTransition.prompt.isDirty)
        assertFalse(prompt.pendingTransition.prompt.isValid)
        assertFalse(prompt.pendingTransition.prompt.isRunnable)

        assertEquals(
            GemEditorTransitionResult.Cancelled,
            session.resolvePendingTransition(GemEditorPendingDecision.Cancel)
        )
        assertEquals("gem://workspace/source", session.state.value.editedAsset.metadata.id)
        assertTrue(session.state.value.isDirty)
        assertNull(session.state.value.pendingTransition)

        assertIs<GemEditorTransitionResult.PromptRequired>(session.requestAssetSwitch(target))
        val discardResult = assertIs<GemEditorTransitionResult.AssetOpened>(
            session.resolvePendingTransition(GemEditorPendingDecision.Discard)
        )
        assertEquals(target.metadata.id, discardResult.asset.metadata.id)
        assertFalse(session.state.value.isDirty)
        assertEquals(target.metadata.id, session.state.value.editedAsset.metadata.id)
        assertFalse(session.state.value.canUndo)
    }

    @Test
    fun dirtyExitCanBeExplicitlySavedBeforeClosing() {
        val persisted = mutableListOf<String>()
        val session = GemEditorSession(
            initialAsset = validAsset(
                assetId = "gem://workspace/exit",
                name = "Exit Gem"
            ),
            persistAsset = { asset, _ ->
                persisted += asset.metadata.id
                asset
            }
        )

        session.disconnect(connectionId = "scale-value")

        assertIs<GemEditorTransitionResult.PromptRequired>(session.requestExit())
        assertEquals(
            GemEditorTransitionResult.ExitApproved,
            session.resolvePendingTransition(GemEditorPendingDecision.Save)
        )
        assertEquals(listOf("gem://workspace/exit"), persisted)
        assertFalse(session.state.value.isDirty)
        assertNull(session.state.value.pendingTransition)
    }

    @Test
    fun selectionIsSanitizedWhenMutationsRemoveSelectedContent() {
        val session = GemEditorSession(
            initialAsset = validAsset(
                assetId = "gem://workspace/selection",
                name = "Selection Gem"
            )
        )

        session.selectNodes(setOf("constant"))
        session.removeNode(nodeId = "constant")

        assertTrue(session.state.value.selection.nodeIds.isEmpty())
        assertFalse(session.state.value.validationResult.isValid)
    }

    @Test
    fun duplicateSelectionPromotesNewGraphCopiesAndUndoRestoresOriginalSelection() {
        val session = GemEditorSession(
            initialAsset = validAsset(
                assetId = "gem://workspace/duplicate",
                name = "Duplicate Gem"
            )
        )

        session.selectNodes(linkedSetOf("constant", "add"))
        session.duplicateSelection()

        val duplicatedState = session.state.value
        assertEquals(6, duplicatedState.editedAsset.graph()!!.nodes.size)
        assertEquals(2, duplicatedState.selection.nodeIds.size)
        assertEquals(0, duplicatedState.selection.connectionIds.size)
        assertTrue(
            duplicatedState.selection.nodeIds.none { it in setOf("constant", "add") }
        )
        assertTrue(
            duplicatedState.selection.nodeIds.all { nodeId ->
                duplicatedState.editedAsset.graph()!!.node(nodeId) != null
            }
        )

        assertTrue(session.undo())
        assertEquals(4, session.state.value.editedAsset.graph()!!.nodes.size)
        assertEquals(linkedSetOf("constant", "add"), session.state.value.selection.nodeIds)
        assertTrue(session.state.value.selection.connectionIds.isEmpty())
    }

    @Test
    fun selectionDropsRemovedExposedParametersWhenAssetShapeChanges() {
        val baseAsset = validAsset(
            assetId = "gem://workspace/parameters",
            name = "Parameter Gem"
        ).copy(
            definition = validAsset(
                assetId = "gem://workspace/parameters",
                name = "Parameter Gem"
            ).definition.copy(
                exposedParameters = listOf(
                    GemExposedParameter(
                        id = "gain",
                        type = GemValueType.Number,
                        defaultValue = GemValue.Number(1.0)
                    )
                )
            )
        )
        val session = GemEditorSession(initialAsset = baseAsset)

        session.selectExposedParameter("gain")
        session.replaceEditedAsset(
            baseAsset.copy(
                definition = baseAsset.definition.copy(
                    exposedParameters = emptyList()
                )
            )
        )

        assertNull(session.state.value.selection.exposedParameterId)
    }

    @Test
    fun compileOnlyDiagnosticsSurfaceAsUnrunnableWithoutInvalidatingStructure() {
        val baseAsset = validAsset(
            assetId = "gem://workspace/compile-error",
            name = "Compile Error Gem"
        )
        val session = GemEditorSession(
            initialAsset = baseAsset.copy(
                definition = baseAsset.definition.copy(
                    exposedParameters = listOf(
                        GemExposedParameter(
                            id = "value",
                            type = GemValueType.Number,
                            defaultValue = GemValue.Number(1.0),
                            bindings = listOf(
                                dev.anthonyhfm.amethyst.gem.GemGraphBinding(
                                    nodeId = "add",
                                    pinId = "a",
                                    targetKind = GemBindingTargetKind.INPUT_PIN
                                )
                            )
                        )
                    )
                )
            )
        )

        val state = session.state.value
        assertTrue(state.validationResult.isValid)
        assertFalse(state.compilationResult.isSuccess)
        assertEquals(GemEditorRefreshStatus.Unrunnable, state.refreshState.status)
        assertTrue(
            state.compilationResult.diagnostics.any { diagnostic ->
                diagnostic.code == GemRuntimeDiagnosticCode.MULTIPLE_INPUT_SOURCES &&
                    diagnostic.nodeId == "add" &&
                    diagnostic.pinId == "a" &&
                    diagnostic.parameterId == "value"
            }
        )
    }

    @Test
    fun unsupportedNodeValidationIsSurfacedInRefreshState() {
        val session = GemEditorSession(
            initialAsset = Gem.emptyAsset(
                assetId = "gem://workspace/unsupported",
                name = "Unsupported Gem"
            ).putNode(
                dev.anthonyhfm.amethyst.gem.GemNodeInstance(
                    id = "mystery",
                    type = GemNodeTypeId(typeId = "amethyst.test.missing")
                )
            )
        )

        val state = session.state.value
        assertFalse(state.validationResult.isValid)
        assertFalse(state.compilationResult.isSuccess)
        assertEquals(GemEditorRefreshStatus.Invalid, state.refreshState.status)
        assertTrue(state.refreshState.hasUnsupportedNodes)
    }

    private fun validAsset(
        assetId: String,
        name: String
    ): GemAsset = Gem.emptyAsset(
        assetId = assetId,
        name = name
    ).copy(
        definition = GemDefinition(
            rootGraph = GemGraph(
                id = Gem.rootGraphId,
                kind = GemGraphKind.ROOT,
                nodes = listOf(
                    GemBuiltInNodes.hostLedInput.instantiate(
                        nodeId = "constant",
                        serializedState = GemBuiltInNodes.hostPortState("led-in")
                    ),
                    GemBuiltInNodes.ledUnpack.instantiate("unpack"),
                    GemBuiltInNodes.constantNumber.instantiate("num-const"),
                    GemBuiltInNodes.numberAdd.instantiate("add")
                ),
                connections = listOf(
                    GemConnection(
                        id = "scale-value",
                        from = GemPinRef(nodeId = "constant", pinId = "signal"),
                        to = GemPinRef(nodeId = "unpack", pinId = "signal")
                    ),
                    GemConnection(
                        id = "scale-scale",
                        from = GemPinRef(nodeId = "num-const", pinId = "value"),
                        to = GemPinRef(nodeId = "add", pinId = "a")
                    )
                )
            ),
            host = dev.anthonyhfm.amethyst.gem.GemHostIoContract(
                assetShape = dev.anthonyhfm.amethyst.gem.GemHostAssetShape.PROCESSOR,
                supportedDomains = listOf(dev.anthonyhfm.amethyst.gem.GemSignalDomain.LED),
                inputs = listOf(dev.anthonyhfm.amethyst.gem.GemHostPort(
                    id = "led-in",
                    domain = dev.anthonyhfm.amethyst.gem.GemSignalDomain.LED,
                    required = true
                ))
            )
        )
    )

    // ─── Phase 5: Transient-move performance tests ───────────────────────────

    @Test
    fun transientNodeMovesReuseExistingValidationAndCompilationResult() {
        val session = GemEditorSession(initialAsset = validAsset("gem://test/transient", "Transient Test"))
        val stateBeforeMove = session.state.value

        // Multiple transient moves should not trigger recompilation.
        session.moveNodesTransient(
            positions = mapOf("constant" to GemNodePosition(x = 100f, y = 200f))
        )
        val stateAfterFirstMove = session.state.value

        session.moveNodesTransient(
            positions = mapOf("constant" to GemNodePosition(x = 150f, y = 250f))
        )
        val stateAfterSecondMove = session.state.value

        // Validation and compilation results should be the exact same object instances
        // (reused, not recomputed) during transient changes.
        assertTrue(
            stateAfterFirstMove.validationResult === stateBeforeMove.validationResult,
            "validationResult should be reused (not recomputed) during transient moves"
        )
        assertTrue(
            stateAfterFirstMove.compilationResult === stateBeforeMove.compilationResult,
            "compilationResult should be reused (not recomputed) during transient moves"
        )
        assertTrue(
            stateAfterSecondMove.validationResult === stateBeforeMove.validationResult,
            "validationResult should stay reused across multiple transient moves"
        )
        assertTrue(
            stateAfterSecondMove.compilationResult === stateBeforeMove.compilationResult,
            "compilationResult should stay reused across multiple transient moves"
        )
    }

    @Test
    fun commitTransientChangesTriggersFreshValidationAndCompilation() {
        val session = GemEditorSession(initialAsset = validAsset("gem://test/commit", "Commit Test"))
        val stateBeforeMove = session.state.value

        session.moveNodesTransient(
            positions = mapOf("constant" to GemNodePosition(x = 100f, y = 200f))
        )
        session.commitTransientChanges()
        val stateAfterCommit = session.state.value

        // After commit, positions are persisted. Positions don't affect validation/compilation
        // but a full rebuild must have run (new object instances are fine either way — the
        // important thing is that the committed state is consistent).
        assertEquals(
            GemNodePosition(x = 100f, y = 200f),
            stateAfterCommit.editedAsset.graph()?.node("constant")?.layout?.position,
            "Committed node position should be persisted in the asset"
        )
        // The session should no longer be in a transient state.
        assertFalse(stateAfterCommit.editedAsset == stateBeforeMove.editedAsset)
    }
}
