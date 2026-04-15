package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.gem.data.GemRepository
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class GemRepositoryTest {
    @Test
    fun gemAssetRoundTripsThroughJsonWithPhase1Schema() {
        val enumDefinition = GemEnumDefinition(
            id = "mode",
            label = "Mode",
            options = listOf(
                GemEnumOption(id = "pulse", label = "Pulse"),
                GemEnumOption(id = "hold", label = "Hold")
            )
        )
        val asset = GemAsset(
            metadata = GemAssetMetadata(
                id = "gem://test/phase-1",
                name = "Phase 1 Test Gem",
                description = "Schema regression fixture",
                category = GemCategory(id = "led", label = "LED"),
                assetVersion = dev.anthonyhfm.amethyst.core.util.Version(2, 1, 0)
            ),
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(
                        GemNodeInstance(
                            id = "input-node",
                            type = GemNodeTypeId(typeId = "amethyst.parameter.in", version = Gem.phase1SchemaVersion),
                            pins = listOf(
                                GemPin(
                                    id = "mode-in",
                                    direction = GemPinDirection.OUTPUT,
                                    type = GemPinType.Value(GemValueType.Enum(enumDefinition))
                                )
                            )
                        ),
                        GemNodeInstance(
                            id = "led-node",
                            type = GemNodeTypeId(typeId = "amethyst.led.out", version = Gem.phase1SchemaVersion),
                            pins = listOf(
                                GemPin(
                                    id = "mode-in",
                                    direction = GemPinDirection.INPUT,
                                    type = GemPinType.Value(GemValueType.Enum(enumDefinition)),
                                    required = true,
                                    defaultValue = GemValue.Enum(enumId = "mode", optionId = "pulse")
                                ),
                                GemPin(
                                    id = "signal-out",
                                    direction = GemPinDirection.OUTPUT,
                                    type = GemPinType.Signal(GemSignalDomain.LED)
                                )
                            )
                        )
                    ),
                    connections = listOf(
                        GemConnection(
                            id = "connection-1",
                            from = GemPinRef(nodeId = "input-node", pinId = "mode-in"),
                            to = GemPinRef(nodeId = "led-node", pinId = "mode-in")
                        )
                    )
                ),
                subgraphs = listOf(
                    GemGraph(
                        id = "subgraph-preview",
                        kind = GemGraphKind.SUBGRAPH,
                        label = "Preview Subgraph"
                    )
                ),
                host = GemHostIoContract(
                    assetShape = GemHostAssetShape.SOURCE,
                    supportedDomains = listOf(GemSignalDomain.LED, GemSignalDomain.MIDI),
                    outputs = listOf(
                        GemHostPort(id = "led-out", domain = GemSignalDomain.LED, required = true),
                        GemHostPort(id = "midi-out", domain = GemSignalDomain.MIDI)
                    )
                ),
                exposedParameters = listOf(
                    GemExposedParameter(
                        id = "mode",
                        label = "Mode",
                        type = GemValueType.Enum(enumDefinition),
                        defaultValue = GemValue.Enum(enumId = "mode", optionId = "pulse"),
                        groupId = "behavior",
                        sortOrder = 10,
                        bindings = listOf(
                            GemGraphBinding(
                                nodeId = "led-node",
                                pinId = "mode-in"
                            )
                        )
                    ),
                    GemExposedParameter(
                        id = "fade-time",
                        label = "Fade Time",
                        type = GemValueType.Timing,
                        defaultValue = GemValue.TimingValue(Timing.Duration(250.milliseconds)),
                        sortOrder = 20,
                        bindings = listOf(
                            GemGraphBinding(
                                nodeId = "led-node",
                                pinId = "mode-in",
                                targetKind = GemBindingTargetKind.INPUT_PIN
                            )
                        )
                    )
                ),
                defaultState = GemDefaultState(
                    exposedParameterValues = mapOf(
                        "mode" to GemValue.Enum(enumId = "mode", optionId = "hold")
                    )
                )
            )
        )

        val encoded = GemRepository.encode(asset)
        val decoded = GemRepository.decode(encoded)

        assertEquals(asset, decoded)
    }

    @Test
    fun gemAssetRoundTripsNodeLayoutMetadataThroughJson() {
        val asset = Gem.emptyAsset(
            assetId = "gem://test/layout",
            name = "Layout Test Gem"
        ).copy(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(
                        GemNodeInstance(
                            id = "positioned-node",
                            type = GemNodeTypeId(typeId = "amethyst.test.positioned"),
                            pins = listOf(
                                GemPin(
                                    id = "value",
                                    direction = GemPinDirection.OUTPUT,
                                    type = GemPinType.Value(GemValueType.Number)
                                )
                            ),
                            layout = GemNodeLayout(
                                position = GemNodePosition(x = 192.5f, y = -48f)
                            )
                        ),
                        GemNodeInstance(
                            id = "default-node",
                            type = GemNodeTypeId(typeId = "amethyst.test.default")
                        )
                    )
                )
            )
        )

        val encoded = GemRepository.encode(asset)
        val decoded = GemRepository.decode(encoded)
        val nodesJson = GemRepository.json
            .parseToJsonElement(encoded)
            .jsonObject["definition"]
            ?.jsonObject
            ?.get("rootGraph")
            ?.jsonObject
            ?.get("nodes")
            ?.jsonArray
            .orEmpty()

        assertEquals(asset, decoded)
        assertTrue("layout" in nodesJson[0].jsonObject)
        assertFalse("layout" in nodesJson[1].jsonObject)
    }

    @Test
    fun gemAssetRoundTripsSubgraphLayoutMetadataThroughJson() {
        val asset = Gem.emptyAsset(
            assetId = "gem://test/subgraph-layout",
            name = "Subgraph Layout Test Gem"
        ).copy(
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(
                        positionedNode(
                            nodeId = "root-positioned",
                            typeId = "amethyst.test.root.positioned",
                            position = GemNodePosition(x = 96f, y = 128f)
                        ),
                        positionedNode(
                            nodeId = "root-default",
                            typeId = "amethyst.test.root.default"
                        )
                    )
                ),
                subgraphs = listOf(
                    GemGraph(
                        id = "preview",
                        kind = GemGraphKind.SUBGRAPH,
                        nodes = listOf(
                            positionedNode(
                                nodeId = "preview-positioned",
                                typeId = "amethyst.test.preview.positioned",
                                position = GemNodePosition(x = -24f, y = 320f)
                            ),
                            positionedNode(
                                nodeId = "preview-default",
                                typeId = "amethyst.test.preview.default"
                            )
                        )
                    )
                )
            )
        )

        val encoded = GemRepository.encode(asset)
        val decoded = GemRepository.decode(encoded)
        val definitionJson = GemRepository.json
            .parseToJsonElement(encoded)
            .jsonObject["definition"]
            ?.jsonObject
            ?: error("definition JSON missing")
        val rootNodesJson = definitionJson["rootGraph"]
            ?.jsonObject
            ?.get("nodes")
            ?.jsonArray
            .orEmpty()
        val previewNodesJson = definitionJson["subgraphs"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("nodes")
            ?.jsonArray
            .orEmpty()

        assertEquals(asset, decoded)
        assertTrue("layout" in rootNodesJson[0].jsonObject)
        assertFalse("layout" in rootNodesJson[1].jsonObject)
        assertTrue("layout" in previewNodesJson[0].jsonObject)
        assertFalse("layout" in previewNodesJson[1].jsonObject)
    }

    private fun positionedNode(
        nodeId: String,
        typeId: String,
        position: GemNodePosition = GemNodePosition()
    ): GemNodeInstance = GemNodeInstance(
        id = nodeId,
        type = GemNodeTypeId(typeId = typeId),
        pins = listOf(
            GemPin(
                id = "value",
                direction = GemPinDirection.OUTPUT,
                type = GemPinType.Value(GemValueType.Number)
            )
        ),
        layout = GemNodeLayout(position = position)
    )
}
