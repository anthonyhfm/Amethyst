package dev.anthonyhfm.amethyst.gem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GemGraphMutationTest {
    @Test
    fun graphMutationHelpersAreImmutableAndDisconnectConflictingInputs() {
        val original = Gem.emptyAsset(
            assetId = "gem://test/mutations",
            name = "Mutation Test Gem"
        ).putNode(sourceNode("source-a"))
            .putNode(sourceNode("source-b"))
            .putNode(targetNode("target"))

        val moved = original.moveNode(
            nodeId = "target",
            position = GemNodePosition(x = 64f, y = 96f)
        )
        val firstConnection = GemConnection(
            id = "connection-a",
            from = GemPinRef(nodeId = "source-a", pinId = "value"),
            to = GemPinRef(nodeId = "target", pinId = "input")
        )
        val secondConnection = GemConnection(
            id = "connection-b",
            from = GemPinRef(nodeId = "source-b", pinId = "value"),
            to = GemPinRef(nodeId = "target", pinId = "input")
        )

        val reconnected = moved
            .connect(firstConnection)
            .connect(secondConnection)

        assertEquals(GemNodePosition(), original.graph()!!.node("target")!!.layout.position)
        assertEquals(GemNodePosition(x = 64f, y = 96f), moved.graph()!!.node("target")!!.layout.position)
        assertEquals(listOf(secondConnection), reconnected.graph()!!.connections)

        val disconnected = reconnected.disconnect(secondConnection.id)
        assertTrue(disconnected.graph()!!.connections.isEmpty())

        val removed = reconnected.removeNode("target")
        assertNull(removed.graph()!!.node("target"))
        assertTrue(removed.graph()!!.connections.isEmpty())
        assertNotNull(reconnected.graph()!!.node("target"))
    }

    @Test
    fun assetMutationHelpersCanTargetSubgraphs() {
        val asset = Gem.emptyAsset(
            assetId = "gem://test/subgraph-mutations",
            name = "Subgraph Mutation Test Gem"
        ).copy(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT
                ),
                subgraphs = listOf(
                    GemGraph(
                        id = "preview",
                        kind = GemGraphKind.SUBGRAPH,
                        label = "Preview"
                    )
                )
            )
        )
        val previewNode = sourceNode("preview-node").moveTo(GemNodePosition(x = 12f, y = 24f))

        val updated = asset.putNode(
            node = previewNode,
            graphId = "preview"
        )

        assertTrue(asset.graph("preview")!!.nodes.isEmpty())
        assertEquals(previewNode, updated.graph("preview")!!.node("preview-node"))
        assertTrue(updated.graph()!!.nodes.isEmpty())
    }

    @Test
    fun removingNodeDropsBindingsForThatGraphNodePair() {
        val asset = Gem.emptyAsset(
            assetId = "gem://test/binding-cleanup",
            name = "Binding Cleanup Test Gem"
        ).copy(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(targetNode("bound-node"))
                ),
                exposedParameters = listOf(
                    GemExposedParameter(
                        id = "amount",
                        type = GemValueType.Number,
                        defaultValue = GemValue.Number(1.0),
                        bindings = listOf(
                            GemGraphBinding(
                                graphId = Gem.rootGraphId,
                                nodeId = "bound-node",
                                pinId = "input"
                            ),
                            GemGraphBinding(
                                graphId = "preview",
                                nodeId = "bound-node",
                                pinId = "input"
                            )
                        )
                    )
                )
            )
        )

        val updated = asset.removeNode("bound-node")

        assertEquals(
            listOf(
                GemGraphBinding(
                    graphId = "preview",
                    nodeId = "bound-node",
                    pinId = "input"
                )
            ),
            updated.definition.exposedParameters.single().bindings
        )
    }

    private fun sourceNode(nodeId: String): GemNodeInstance = GemNodeInstance(
        id = nodeId,
        type = GemNodeTypeId(typeId = "amethyst.test.source"),
        pins = listOf(
            GemPin(
                id = "value",
                direction = GemPinDirection.OUTPUT,
                type = GemPinType.Value(GemValueType.Number)
            )
        )
    )

    private fun targetNode(nodeId: String): GemNodeInstance = GemNodeInstance(
        id = nodeId,
        type = GemNodeTypeId(typeId = "amethyst.test.target"),
        pins = listOf(
            GemPin(
                id = "input",
                direction = GemPinDirection.INPUT,
                type = GemPinType.Value(GemValueType.Number),
                required = true
            )
        )
    )
}
