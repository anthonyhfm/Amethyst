package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.core.util.Version

object GemBuiltInPresets {
    fun getAll(): List<GemAsset> = listOf(
        buildPassthroughPreset(),
        buildBrightnessPreset()
    )

    fun buildPassthroughPreset(): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = "gem://preset/passthrough",
            name = "Passthrough",
            description = "Unpacks the incoming LED signal and repacks it unchanged.",
            category = GemCategory(id = "starter", label = "Starter Presets"),
            assetVersion = Version(1, 0, 0),
            tags = listOf("led", "starter")
        ),
        definition = GemDefinition(
            rootGraph = GemGraph(
                id = Gem.rootGraphId,
                kind = GemGraphKind.ROOT,
                label = "Passthrough Root",
                nodes = listOf(
                    GemBuiltInNodes.hostLedInput.instantiate(
                        nodeId = "led-in",
                        serializedState = GemBuiltInNodes.hostPortState("led-in")
                    ),
                    GemBuiltInNodes.ledUnpack.instantiate(nodeId = "unpack", label = "LED Unpack"),
                    GemBuiltInNodes.ledPack.instantiate(nodeId = "pack", label = "LED Pack"),
                    GemBuiltInNodes.hostLedOutput.instantiate(
                        nodeId = "led-out",
                        serializedState = GemBuiltInNodes.hostPortState("led-out")
                    )
                ),
                connections = listOf(
                    GemConnection("in-to-unpack", GemPinRef("led-in", "signal"), GemPinRef("unpack", "signal")),
                    GemConnection("r-to-pack", GemPinRef("unpack", "r"), GemPinRef("pack", "r")),
                    GemConnection("g-to-pack", GemPinRef("unpack", "g"), GemPinRef("pack", "g")),
                    GemConnection("b-to-pack", GemPinRef("unpack", "b"), GemPinRef("pack", "b")),
                    GemConnection("x-to-pack", GemPinRef("unpack", "x"), GemPinRef("pack", "x")),
                    GemConnection("y-to-pack", GemPinRef("unpack", "y"), GemPinRef("pack", "y")),
                    GemConnection("layer-to-pack", GemPinRef("unpack", "layer"), GemPinRef("pack", "layer")),
                    GemConnection("pack-to-out", GemPinRef("pack", "signal"), GemPinRef("led-out", "signal"))
                )
            ),
            host = ledProcessorHost()
        )
    )

    fun buildBrightnessPreset(): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = "gem://preset/brightness",
            name = "Brightness Scale",
            description = "Scales the brightness of the incoming LED signal by multiplying R, G, B by a factor.",
            category = GemCategory(id = "starter", label = "Starter Presets"),
            assetVersion = Version(1, 0, 0),
            tags = listOf("brightness", "led", "starter")
        ),
        definition = GemDefinition(
            rootGraph = GemGraph(
                id = Gem.rootGraphId,
                kind = GemGraphKind.ROOT,
                label = "Brightness Scale Root",
                nodes = listOf(
                    GemBuiltInNodes.hostLedInput.instantiate(
                        nodeId = "led-in",
                        serializedState = GemBuiltInNodes.hostPortState("led-in")
                    ),
                    GemBuiltInNodes.ledUnpack.instantiate(nodeId = "unpack", label = "LED Unpack"),
                    GemBuiltInNodes.constantNumber.instantiate(
                        nodeId = "factor",
                        label = "Factor",
                        serializedState = GemBuiltInNodes.constantNumberState(0.5)
                    ),
                    GemBuiltInNodes.numberMultiply.instantiate(nodeId = "mul-r", label = "Multiply R"),
                    GemBuiltInNodes.numberMultiply.instantiate(nodeId = "mul-g", label = "Multiply G"),
                    GemBuiltInNodes.numberMultiply.instantiate(nodeId = "mul-b", label = "Multiply B"),
                    GemBuiltInNodes.ledPack.instantiate(nodeId = "pack", label = "LED Pack"),
                    GemBuiltInNodes.hostLedOutput.instantiate(
                        nodeId = "led-out",
                        serializedState = GemBuiltInNodes.hostPortState("led-out")
                    )
                ),
                connections = listOf(
                    GemConnection("in-to-unpack", GemPinRef("led-in", "signal"), GemPinRef("unpack", "signal")),
                    GemConnection("r-to-mul", GemPinRef("unpack", "r"), GemPinRef("mul-r", "a")),
                    GemConnection("g-to-mul", GemPinRef("unpack", "g"), GemPinRef("mul-g", "a")),
                    GemConnection("b-to-mul", GemPinRef("unpack", "b"), GemPinRef("mul-b", "a")),
                    GemConnection("factor-to-mul-r", GemPinRef("factor", "value"), GemPinRef("mul-r", "b")),
                    GemConnection("factor-to-mul-g", GemPinRef("factor", "value"), GemPinRef("mul-g", "b")),
                    GemConnection("factor-to-mul-b", GemPinRef("factor", "value"), GemPinRef("mul-b", "b")),
                    GemConnection("mul-r-to-pack", GemPinRef("mul-r", "result"), GemPinRef("pack", "r")),
                    GemConnection("mul-g-to-pack", GemPinRef("mul-g", "result"), GemPinRef("pack", "g")),
                    GemConnection("mul-b-to-pack", GemPinRef("mul-b", "result"), GemPinRef("pack", "b")),
                    GemConnection("x-to-pack", GemPinRef("unpack", "x"), GemPinRef("pack", "x")),
                    GemConnection("y-to-pack", GemPinRef("unpack", "y"), GemPinRef("pack", "y")),
                    GemConnection("layer-to-pack", GemPinRef("unpack", "layer"), GemPinRef("pack", "layer")),
                    GemConnection("pack-to-out", GemPinRef("pack", "signal"), GemPinRef("led-out", "signal"))
                )
            ),
            host = ledProcessorHost()
        )
    )

    private fun ledProcessorHost(): GemHostIoContract = GemHostIoContract(
        assetShape = GemHostAssetShape.PROCESSOR,
        supportedDomains = listOf(GemSignalDomain.LED),
        inputs = listOf(GemHostPort(id = "led-in", domain = GemSignalDomain.LED, required = true)),
        outputs = listOf(GemHostPort(id = "led-out", domain = GemSignalDomain.LED, required = true))
    )
}
