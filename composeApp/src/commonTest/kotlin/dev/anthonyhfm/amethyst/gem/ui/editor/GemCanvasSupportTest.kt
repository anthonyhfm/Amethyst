package dev.anthonyhfm.amethyst.gem.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import dev.anthonyhfm.amethyst.gem.Gem
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemConnection
import dev.anthonyhfm.amethyst.gem.GemDefinition
import dev.anthonyhfm.amethyst.gem.GemGraph
import dev.anthonyhfm.amethyst.gem.GemGraphKind
import dev.anthonyhfm.amethyst.gem.GemNodePosition
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemValidationErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GemCanvasSupportTest {
    @Test
    fun `viewport transforms round-trip through screen space and keep zoom focus stable`() {
        val viewport = GemCanvasViewportState(
            offset = Offset(x = 180f, y = 96f),
            zoom = 1.25f
        )
        val world = Offset(x = 144f, y = 88f)
        val screen = viewport.worldToScreen(world)

        assertOffsetEquals(world, viewport.screenToWorld(screen))

        val zoomed = viewport.zoomBy(
            delta = 0.4f,
            focus = screen
        )
        assertOffsetEquals(world, zoomed.screenToWorld(screen))
    }

    @Test
    fun `viewport zoom stays within supported graph-first bounds`() {
        val zoomedIn = GemCanvasViewportState(zoom = 2.4f).zoomBy(
            delta = 1f,
            focus = Offset.Zero
        )
        val zoomedOut = GemCanvasViewportState(zoom = 0.4f).zoomBy(
            delta = -0.9f,
            focus = Offset.Zero
        )

        assertEquals(2.5f, zoomedIn.zoom, absoluteTolerance = 0.001f)
        assertEquals(0.35f, zoomedOut.zoom, absoluteTolerance = 0.001f)
    }

    @Test
    fun `scaled node drag deltas are converted back to screen space before persisting`() {
        val viewport = GemCanvasViewportState(
            offset = Offset(x = 120f, y = 80f),
            zoom = 2f
        )
        val startWorld = Offset(x = 200f, y = 160f)
        val startScreen = viewport.worldToScreen(startWorld)
        val localScaledDrag = Offset(x = 24f, y = -18f)

        val persistedWorld = viewport.screenToWorld(
            startScreen + gemCanvasScaledDragDeltaToScreenSpace(
                dragDelta = localScaledDrag,
                viewport = viewport
            )
        )

        assertOffsetEquals(
            Offset(x = 224f, y = 142f),
            persistedWorld
        )
    }

    @Test
    fun `new node placement prefers the last graph interaction point`() {
        val anchored = resolveGemCanvasNodePlacement(
            metrics = GemCanvasNodeMetrics(
                width = 240f,
                height = 120f
            ),
            viewport = GemCanvasViewportState(offset = Offset.Zero, zoom = 1f),
            canvasSize = IntSize(width = 960, height = 640),
            anchorWorld = Offset(x = 420f, y = 220f)
        )

        assertEquals(GemNodePosition(x = 300f, y = 160f), anchored)
    }

    @Test
    fun `pin labels surface inline defaults until the input is connected`() {
        val pin = GemBuiltInNodes.numberAdd.instantiate("add")
            .pins
            .first { it.id == "b" }

        assertEquals("B · 0", gemCanvasPinLabel(pin = pin, hasConnection = false))
        assertEquals("B", gemCanvasPinLabel(pin = pin, hasConnection = true))
    }

    @Test
    fun boxSelectionUsesPersistedNodePositions() {
        val graph = GemGraph(
            id = Gem.rootGraphId,
            kind = GemGraphKind.ROOT,
            nodes = listOf(
                GemBuiltInNodes.constantNumber.instantiate("left").moveTo(
                    GemNodePosition(x = 24f, y = 24f)
                ),
                GemBuiltInNodes.constantNumber.instantiate("right").moveTo(
                    GemNodePosition(x = 420f, y = 48f)
                )
            )
        )

        val selected = selectGemCanvasNodes(
            graph = graph,
            selectionRect = Rect(
                left = 0f,
                top = 0f,
                right = 320f,
                bottom = 220f
            )
        )

        assertEquals(linkedSetOf("left"), selected)
    }

    @Test
    fun `connection hit testing follows rendered node positions`() {
        val graph = GemGraph(
            id = Gem.rootGraphId,
            kind = GemGraphKind.ROOT,
            nodes = listOf(
                GemBuiltInNodes.constantNumber.instantiate("constant"),
                GemBuiltInNodes.numberAdd.instantiate("scale")
            ),
            connections = listOf(
                GemConnection(
                    id = "scale-value",
                    from = GemPinRef(nodeId = "constant", pinId = "value"),
                    to = GemPinRef(nodeId = "scale", pinId = "a")
                )
            )
        )
        val renderedPositions = mapOf(
            "constant" to Offset(x = 420f, y = 180f),
            "scale" to Offset(x = 720f, y = 240f)
        )
        val renderedStart = gemCanvasPinPosition(
            node = graph.node("constant")!!,
            pinId = "value",
            position = renderedPositions.getValue("constant")
        )!!

        assertNull(findGemCanvasConnectionAt(graph = graph, worldPoint = renderedStart))
        assertEquals(
            "scale-value",
            findGemCanvasConnectionAt(
                graph = graph,
                worldPoint = renderedStart,
                nodePositions = renderedPositions
            )?.id
        )
    }

    @Test
    fun `pin positions respect input and output columns with row spacing`() {
        val node = GemBuiltInNodes.numberAdd.instantiate("scale").moveTo(
            GemNodePosition(x = 100f, y = 60f)
        )

        assertOffsetEquals(
            Offset(x = 138f, y = 172f),
            gemCanvasPinPosition(node = node, pinId = "a")!!
        )
        assertOffsetEquals(
            Offset(x = 138f, y = 228f),
            gemCanvasPinPosition(node = node, pinId = "b")!!
        )
        assertOffsetEquals(
            Offset(x = 371f, y = 172f),
            gemCanvasPinPosition(node = node, pinId = "result")!!
        )
    }

    @Test
    fun duplicateSelectionCopiesNodesAndInternalConnectionsWithOffset() {
        val asset = Gem.emptyAsset(
            assetId = "gem://test/duplicate",
            name = "Duplicate Test"
        ).copy(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(
                        GemBuiltInNodes.constantNumber.instantiate("constant").moveTo(
                            GemNodePosition(x = 32f, y = 40f)
                        ),
                        GemBuiltInNodes.numberAdd.instantiate("scale").moveTo(
                            GemNodePosition(x = 260f, y = 96f)
                        )
                    ),
                    connections = listOf(
                        GemConnection(
                            id = "scale-value",
                            from = GemPinRef(nodeId = "constant", pinId = "value"),
                            to = GemPinRef(nodeId = "scale", pinId = "a")
                        ),
                        GemConnection(
                            id = "scale-scale",
                            from = GemPinRef(nodeId = "constant", pinId = "value"),
                            to = GemPinRef(nodeId = "scale", pinId = "b")
                        )
                    )
                )
            )
        )

        var nextNodeIdIndex = 0
        val nextNodeIds = listOf("constant-copy", "scale-copy")
        var nextConnectionIdIndex = 0
        val nextConnectionIds = listOf("scale-value-copy", "scale-scale-copy")
        val result = duplicateGemSelection(
            asset = asset,
            selection = GemEditorSelection(
                graphId = Gem.rootGraphId,
                nodeIds = linkedSetOf("constant", "scale")
            ),
            nodeIdFactory = { nextNodeIds[nextNodeIdIndex++] },
            connectionIdFactory = { nextConnectionIds[nextConnectionIdIndex++] }
        )

        assertEquals(linkedSetOf("constant-copy", "scale-copy"), result.duplicatedNodeIds)
        assertEquals(linkedSetOf("scale-value-copy", "scale-scale-copy"), result.duplicatedConnectionIds)
        assertEquals(
            GemNodePosition(x = 80f, y = 88f),
            result.asset.graph()!!.node("constant-copy")!!.layout.position
        )
        assertEquals(
            GemNodePosition(x = 308f, y = 144f),
            result.asset.graph()!!.node("scale-copy")!!.layout.position
        )
        assertTrue(
            result.asset.graph()!!.connections.any { connection ->
                connection.id == "scale-value-copy" &&
                    connection.from == GemPinRef(nodeId = "constant-copy", pinId = "value") &&
                    connection.to == GemPinRef(nodeId = "scale-copy", pinId = "a")
            }
        )
        assertTrue(
            result.asset.graph()!!.connections.any { connection ->
                connection.id == "scale-scale-copy" &&
                    connection.from == GemPinRef(nodeId = "constant-copy", pinId = "value") &&
                    connection.to == GemPinRef(nodeId = "scale-copy", pinId = "b")
            }
        )
    }

    @Test
    fun connectionEvaluationRejectsIncompatiblePins() {
        val asset = Gem.emptyAsset(
            assetId = "gem://test/incompatible",
            name = "Incompatible Pins"
        ).copy(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(
                        GemBuiltInNodes.constantBoolean.instantiate("bool-source"),
                        GemBuiltInNodes.numberAdd.instantiate("number-target")
                    )
                )
            )
        )

        val evaluation = evaluateGemCanvasConnection(
            asset = asset,
            graphId = Gem.rootGraphId,
            first = GemPinRef(nodeId = "bool-source", pinId = "value"),
            second = GemPinRef(nodeId = "number-target", pinId = "a")
        )

        assertEquals(GemValidationErrorCode.INCOMPATIBLE_PIN_TYPES, evaluation.errorCode)
    }

    @Test
    fun connectionEvaluationBlocksNewCycles() {
        val asset = Gem.emptyAsset(
            assetId = "gem://test/cycle",
            name = "Cycle Guard"
        ).copy(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(
                        GemBuiltInNodes.constantNumber.instantiate("constant"),
                        GemBuiltInNodes.numberAdd.instantiate("scale-a"),
                        GemBuiltInNodes.numberAdd.instantiate("scale-b")
                    ),
                    connections = listOf(
                        GemConnection(
                            id = "constant-to-a",
                            from = GemPinRef(nodeId = "constant", pinId = "value"),
                            to = GemPinRef(nodeId = "scale-a", pinId = "a")
                        ),
                        GemConnection(
                            id = "a-to-b",
                            from = GemPinRef(nodeId = "scale-a", pinId = "result"),
                            to = GemPinRef(nodeId = "scale-b", pinId = "a")
                        )
                    )
                )
            )
        )

        val evaluation = evaluateGemCanvasConnection(
            asset = asset,
            graphId = Gem.rootGraphId,
            first = GemPinRef(nodeId = "scale-b", pinId = "result"),
            second = GemPinRef(nodeId = "scale-a", pinId = "b")
        )

        assertEquals(GemValidationErrorCode.GRAPH_CYCLE, evaluation.errorCode)
    }

    @Test
    fun connectionEvaluationNormalizesRewiresIntoOccupiedInputs() {
        val asset = Gem.emptyAsset(
            assetId = "gem://test/rewire",
            name = "Rewire Guard"
        ).copy(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(
                        GemBuiltInNodes.constantNumber.instantiate("constant-a"),
                        GemBuiltInNodes.constantNumber.instantiate("constant-b"),
                        GemBuiltInNodes.numberAdd.instantiate("scale")
                    ),
                    connections = listOf(
                        GemConnection(
                            id = "constant-a-to-scale",
                            from = GemPinRef(nodeId = "constant-a", pinId = "value"),
                            to = GemPinRef(nodeId = "scale", pinId = "a")
                        )
                    )
                )
            )
        )

        val evaluation = evaluateGemCanvasConnection(
            asset = asset,
            graphId = Gem.rootGraphId,
            first = GemPinRef(nodeId = "scale", pinId = "a"),
            second = GemPinRef(nodeId = "constant-b", pinId = "value")
        )

        assertTrue(evaluation.isValid)
        assertEquals(
            GemCanvasNormalizedConnection(
                from = GemPinRef(nodeId = "constant-b", pinId = "value"),
                to = GemPinRef(nodeId = "scale", pinId = "a")
            ),
            evaluation.normalizedConnection
        )
        assertEquals(null, evaluation.errorCode)
    }

    private fun assertOffsetEquals(
        expected: Offset,
        actual: Offset,
        tolerance: Float = 0.001f
    ) {
        assertEquals(expected.x, actual.x, absoluteTolerance = tolerance)
        assertEquals(expected.y, actual.y, absoluteTolerance = tolerance)
    }
}
