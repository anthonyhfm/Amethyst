package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.gem.data.GemJsonPersistence

object GemReferenceFixtures {
    data class Entry(
        val id: String,
        val title: String,
        val jsonResourcePath: String,
        val asset: () -> GemAsset
    )

    val passthroughGem: Entry = Entry(
        id = "passthrough-gem",
        title = "Passthrough Gem",
        jsonResourcePath = "dev/anthonyhfm/amethyst/gem/fixtures/passthrough-gem.json",
        asset = ::buildPassthroughGem
    )

    val brightnessGem: Entry = Entry(
        id = "brightness-gem",
        title = "Brightness Gem",
        jsonResourcePath = "dev/anthonyhfm/amethyst/gem/fixtures/brightness-gem.json",
        asset = ::buildBrightnessGem
    )

    val all: List<Entry> = listOf(passthroughGem, brightnessGem)

    fun buildPassthroughGem(): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = "gem://reference/passthrough",
            name = "Passthrough Gem",
            description = "Reference fixture: unpacks and repacks LED signal unchanged.",
            category = GemCategory(id = "reference", label = "Reference Gems")
        ),
        definition = GemDefinition(
            rootGraph = GemGraph(
                id = Gem.rootGraphId,
                kind = GemGraphKind.ROOT,
                label = "Passthrough Root",
                nodes = listOf(
                    ledIn(),
                    GemBuiltInNodes.ledUnpack.instantiate(nodeId = "unpack", label = "LED Unpack"),
                    GemBuiltInNodes.ledPack.instantiate(nodeId = "pack", label = "LED Pack"),
                    ledOut()
                ),
                connections = listOf(
                    connection("in-to-unpack", "led-in", "signal", "unpack", "signal"),
                    connection("r-fwd", "unpack", "r", "pack", "r"),
                    connection("g-fwd", "unpack", "g", "pack", "g"),
                    connection("b-fwd", "unpack", "b", "pack", "b"),
                    connection("x-fwd", "unpack", "x", "pack", "x"),
                    connection("y-fwd", "unpack", "y", "pack", "y"),
                    connection("layer-fwd", "unpack", "layer", "pack", "layer"),
                    connection("pack-to-out", "pack", "signal", "led-out", "signal")
                )
            ),
            host = ledHost()
        )
    )

    fun buildBrightnessGem(): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = "gem://reference/brightness",
            name = "Brightness Gem",
            description = "Reference fixture: scales R, G, B by a constant factor of 0.5.",
            category = GemCategory(id = "reference", label = "Reference Gems")
        ),
        definition = GemDefinition(
            rootGraph = GemGraph(
                id = Gem.rootGraphId,
                kind = GemGraphKind.ROOT,
                label = "Brightness Root",
                nodes = listOf(
                    ledIn(),
                    GemBuiltInNodes.ledUnpack.instantiate(nodeId = "unpack", label = "LED Unpack"),
                    GemBuiltInNodes.constantNumber.instantiate(
                        nodeId = "factor",
                        label = "Factor",
                        serializedState = mapOf("value" to jsonValue(GemValue.Number(0.5)))
                    ),
                    GemBuiltInNodes.numberMultiply.instantiate(nodeId = "mul-r", label = "×R"),
                    GemBuiltInNodes.numberMultiply.instantiate(nodeId = "mul-g", label = "×G"),
                    GemBuiltInNodes.numberMultiply.instantiate(nodeId = "mul-b", label = "×B"),
                    GemBuiltInNodes.ledPack.instantiate(nodeId = "pack", label = "LED Pack"),
                    ledOut()
                ),
                connections = listOf(
                    connection("in-to-unpack", "led-in", "signal", "unpack", "signal"),
                    connection("r-to-mul", "unpack", "r", "mul-r", "a"),
                    connection("g-to-mul", "unpack", "g", "mul-g", "a"),
                    connection("b-to-mul", "unpack", "b", "mul-b", "a"),
                    connection("factor-r", "factor", "value", "mul-r", "b"),
                    connection("factor-g", "factor", "value", "mul-g", "b"),
                    connection("factor-b", "factor", "value", "mul-b", "b"),
                    connection("mul-r-pack", "mul-r", "result", "pack", "r"),
                    connection("mul-g-pack", "mul-g", "result", "pack", "g"),
                    connection("mul-b-pack", "mul-b", "result", "pack", "b"),
                    connection("x-fwd", "unpack", "x", "pack", "x"),
                    connection("y-fwd", "unpack", "y", "pack", "y"),
                    connection("layer-fwd", "unpack", "layer", "pack", "layer"),
                    connection("pack-to-out", "pack", "signal", "led-out", "signal")
                )
            ),
            host = ledHost()
        )
    )

    private fun connection(id: String, fromNode: String, fromPin: String, toNode: String, toPin: String) =
        GemConnection(id, GemPinRef(fromNode, fromPin), GemPinRef(toNode, toPin))

    private fun ledHost(): GemHostIoContract = GemHostIoContract(
        assetShape = GemHostAssetShape.PROCESSOR,
        supportedDomains = listOf(GemSignalDomain.LED),
        inputs = listOf(GemHostPort(id = "led-in", domain = GemSignalDomain.LED, required = true)),
        outputs = listOf(GemHostPort(id = "led-out", domain = GemSignalDomain.LED, required = true))
    )

    private fun ledIn(): GemNodeInstance = GemBuiltInNodes.hostLedInput.instantiate(
        nodeId = "led-in",
        serializedState = GemBuiltInNodes.hostPortState("led-in")
    )

    private fun ledOut(): GemNodeInstance = GemBuiltInNodes.hostLedOutput.instantiate(
        nodeId = "led-out",
        serializedState = GemBuiltInNodes.hostPortState("led-out")
    )

    private fun jsonValue(value: GemValue) = GemJsonPersistence.json.encodeToJsonElement(serializer = GemValue.serializer(), value = value)
}
