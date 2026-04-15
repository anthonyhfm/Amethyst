package dev.anthonyhfm.amethyst.gem.ui.editor

import androidx.compose.ui.input.key.Key
import dev.anthonyhfm.amethyst.gem.Gem
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemAssetMetadata
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemConnection
import dev.anthonyhfm.amethyst.gem.GemDefinition
import dev.anthonyhfm.amethyst.gem.GemExposedParameter
import dev.anthonyhfm.amethyst.gem.GemGraph
import dev.anthonyhfm.amethyst.gem.GemGraphKind
import dev.anthonyhfm.amethyst.gem.GemHostIoContract
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValueType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GemEditorWorkspaceModeTest {
    @Test
    fun `entry contexts expose workspace and host labels for session breadcrumbs`() {
        val workspaceContext = GemEditorWorkspaceMode.EntryContext.Workspace(
            sourceLabel = "Lights Chain",
            preferredHostDomain = GemSignalDomain.LED
        )
        val hostContext = GemEditorWorkspaceMode.EntryContext.HostDevice(
            preferredHostDomain = GemSignalDomain.LED,
            deviceLabel = "Gem Device",
            referencedAssetId = "gem://workspace/led",
            referencedAssetName = "LED Gem"
        )

        assertEquals("Lights Chain", workspaceContext.breadcrumbLabel)
        assertEquals("Opened from Lights Chain in Led context", workspaceContext.summaryLabel)
        assertEquals("Gem Device · Led", hostContext.breadcrumbLabel)
        assertEquals("Opened from Gem Device in Led host context", hostContext.summaryLabel)
    }

    @Test
    fun `shortcut resolver maps graph-first additions and selection controls`() {
        assertEquals(
            GemEditorShortcutAction.SelectAllNodes,
            resolveGemEditorShortcutAction(
                key = Key.A,
                isPrimaryShortcut = true,
                isShiftPressed = false,
                hasNodeSelection = false,
                hasCanvasSelection = false,
                hasSelection = false
            )
        )
        assertEquals(
            GemEditorShortcutAction.ClearSelection,
            resolveGemEditorShortcutAction(
                key = Key.Escape,
                isPrimaryShortcut = false,
                isShiftPressed = false,
                hasNodeSelection = false,
                hasCanvasSelection = false,
                hasSelection = true
            )
        )
    }

    @Test
    fun `shortcut resolver gates duplicate and delete actions behind active canvas selection`() {
        assertNull(
            resolveGemEditorShortcutAction(
                key = Key.D,
                isPrimaryShortcut = true,
                isShiftPressed = false,
                hasNodeSelection = false,
                hasCanvasSelection = true,
                hasSelection = true
            )
        )
        assertEquals(
            GemEditorShortcutAction.DuplicateSelection,
            resolveGemEditorShortcutAction(
                key = Key.D,
                isPrimaryShortcut = true,
                isShiftPressed = false,
                hasNodeSelection = true,
                hasCanvasSelection = true,
                hasSelection = true
            )
        )
        assertNull(
            resolveGemEditorShortcutAction(
                key = Key.Delete,
                isPrimaryShortcut = false,
                isShiftPressed = false,
                hasNodeSelection = false,
                hasCanvasSelection = false,
                hasSelection = false
            )
        )
        assertEquals(
            GemEditorShortcutAction.DeleteSelection,
            resolveGemEditorShortcutAction(
                key = Key.Delete,
                isPrimaryShortcut = false,
                isShiftPressed = false,
                hasNodeSelection = false,
                hasCanvasSelection = true,
                hasSelection = true
            )
        )
    }

    @Test
    fun `redo shortcuts support both ctrl-shift-z and ctrl-y ergonomics`() {
        assertEquals(
            GemEditorShortcutAction.Redo,
            resolveGemEditorShortcutAction(
                key = Key.Z,
                isPrimaryShortcut = true,
                isShiftPressed = true,
                hasNodeSelection = false,
                hasCanvasSelection = false,
                hasSelection = false
            )
        )
        assertEquals(
            GemEditorShortcutAction.Redo,
            resolveGemEditorShortcutAction(
                key = Key.Y,
                isPrimaryShortcut = true,
                isShiftPressed = false,
                hasNodeSelection = false,
                hasCanvasSelection = false,
                hasSelection = false
            )
        )
    }

    @Test
    fun `select all and clear selection shortcuts operate on the current graph session`() {
        val session = GemEditorSession(
            initialAsset = Gem.emptyAsset(
                assetId = "gem://workspace/shortcuts",
                name = "Shortcut Gem"
            ).copy(
                definition = GemDefinition(
                    rootGraph = GemGraph(
                        id = Gem.rootGraphId,
                        kind = GemGraphKind.ROOT,
                        nodes = listOf(
                            GemBuiltInNodes.constantNumber.instantiate("constant"),
                            GemBuiltInNodes.numberAdd.instantiate("add")
                        ),
                        connections = listOf(
                            GemConnection(
                                id = "scale-value",
                                from = GemPinRef(nodeId = "constant", pinId = "value"),
                                to = GemPinRef(nodeId = "add", pinId = "a")
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            true,
            performGemEditorShortcutAction(
                action = GemEditorShortcutAction.SelectAllNodes,
                session = session,
                onShowNodePalette = {}
            )
        )
        assertEquals(linkedSetOf("constant", "add"), session.state.value.selection.nodeIds)

        assertEquals(
            true,
            performGemEditorShortcutAction(
                action = GemEditorShortcutAction.ClearSelection,
                session = session,
                onShowNodePalette = {}
            )
        )
        assertEquals(emptySet(), session.state.value.selection.nodeIds)
        assertEquals(emptySet(), session.state.value.selection.connectionIds)
    }

    @Test
    fun `clear selection shortcut also clears docked parameter focus`() {
        val session = GemEditorSession(
            initialAsset = Gem.emptyAsset(
                assetId = "gem://workspace/clear",
                name = "Clear Gem"
            ).copy(
                definition = GemDefinition(
                    rootGraph = GemGraph(
                        id = Gem.rootGraphId,
                        kind = GemGraphKind.ROOT,
                        nodes = listOf(
                            GemBuiltInNodes.constantNumber.instantiate("constant"),
                            GemBuiltInNodes.numberAdd.instantiate("add")
                        ),
                        connections = listOf(
                            GemConnection(
                                id = "scale-value",
                                from = GemPinRef(nodeId = "constant", pinId = "value"),
                                to = GemPinRef(nodeId = "add", pinId = "a")
                            )
                        )
                    ),
                    exposedParameters = listOf(
                        GemExposedParameter(
                            id = "gain",
                            type = GemValueType.Number,
                            defaultValue = GemValue.Number(1.0)
                        )
                    )
                )
            )
        )

        session.selectConnections(setOf("scale-value"))
        session.selectExposedParameter("gain")
        assertEquals("gain", session.state.value.selection.exposedParameterId)
        performGemEditorShortcutAction(
            action = GemEditorShortcutAction.ClearSelection,
            session = session,
            onShowNodePalette = {}
        )

        assertEquals(emptySet(), session.state.value.selection.nodeIds)
        assertEquals(emptySet(), session.state.value.selection.connectionIds)
        assertNull(session.state.value.selection.exposedParameterId)
    }

    @Test
    fun `delete selection shortcut removes connection-only graph selections`() {
        val session = GemEditorSession(
            initialAsset = Gem.emptyAsset(
                assetId = "gem://workspace/delete",
                name = "Delete Gem"
            ).copy(
                definition = GemDefinition(
                    rootGraph = GemGraph(
                        id = Gem.rootGraphId,
                        kind = GemGraphKind.ROOT,
                        nodes = listOf(
                            GemBuiltInNodes.constantNumber.instantiate("constant"),
                            GemBuiltInNodes.numberAdd.instantiate("add")
                        ),
                        connections = listOf(
                            GemConnection(
                                id = "scale-value",
                                from = GemPinRef(nodeId = "constant", pinId = "value"),
                                to = GemPinRef(nodeId = "add", pinId = "a")
                            )
                        )
                    )
                )
            )
        )

        session.selectConnections(setOf("scale-value"))
        assertEquals(
            true,
            performGemEditorShortcutAction(
                action = GemEditorShortcutAction.DeleteSelection,
                session = session,
                onShowNodePalette = {}
            )
        )
        assertEquals(emptyList(), session.state.value.editedAsset.graph()!!.connections)
        assertEquals(emptySet(), session.state.value.selection.connectionIds)
    }

    private fun asset(
        assetId: String,
        name: String,
        supportedDomains: List<GemSignalDomain>
    ): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = assetId,
            name = name
        ),
        definition = GemDefinition(
            rootGraph = GemGraph(
                id = "root",
                kind = GemGraphKind.ROOT
            ),
            host = GemHostIoContract(
                supportedDomains = supportedDomains
            )
        )
    )
}
