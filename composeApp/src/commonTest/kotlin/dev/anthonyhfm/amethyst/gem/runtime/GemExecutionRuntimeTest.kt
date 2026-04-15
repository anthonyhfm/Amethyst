package dev.anthonyhfm.amethyst.gem.runtime

import dev.anthonyhfm.amethyst.core.util.Timing
import dev.anthonyhfm.amethyst.gem.Gem
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemAssetMetadata
import dev.anthonyhfm.amethyst.gem.GemBindingTargetKind
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemCategory
import dev.anthonyhfm.amethyst.gem.GemConnection
import dev.anthonyhfm.amethyst.gem.GemDefinition
import dev.anthonyhfm.amethyst.gem.GemExposedParameter
import dev.anthonyhfm.amethyst.gem.GemGraph
import dev.anthonyhfm.amethyst.gem.GemGraphBinding
import dev.anthonyhfm.amethyst.gem.GemGraphKind
import dev.anthonyhfm.amethyst.gem.GemHostAssetShape
import dev.anthonyhfm.amethyst.gem.GemHostIoContract
import dev.anthonyhfm.amethyst.gem.GemHostPort
import dev.anthonyhfm.amethyst.gem.GemNodeRegistry
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemReferenceFixtures
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValueType
import dev.anthonyhfm.amethyst.gem.data.GemJsonPersistence
import dev.anthonyhfm.amethyst.gem.gemNodeDescriptor
import dev.anthonyhfm.amethyst.gem.outputPin
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class GemExecutionRuntimeTest {
    @Test
    fun compilerProducesDeterministicExecutionPlanForReferenceFixtures() {
        GemReferenceFixtures.all.forEach { fixture ->
            val first = GemCompiler.compile(fixture.asset())
            val second = GemCompiler.compile(fixture.asset())

            assertTrue(first.isSuccess, fixture.title)
            assertTrue(second.isSuccess, fixture.title)
            assertEquals(first.plan, second.plan, fixture.title)
        }

        val passthroughPlan = assertNotNull(GemCompiler.compile(GemReferenceFixtures.passthroughGem.asset()).plan)
        val order = passthroughPlan.graph(Gem.rootGraphId)?.executionOrder
        assertNotNull(order)
        assertTrue("led-in" in order)
        assertTrue("unpack" in order)
        assertTrue("pack" in order)
        assertTrue("led-out" in order)
    }

    @Test
    fun previewEvaluatesReferenceFixturesDeterministically() {
        val passthroughPlan = assertNotNull(GemCompiler.compile(GemReferenceFixtures.passthroughGem.asset()).plan)
        val brightnessPlan = assertNotNull(GemCompiler.compile(GemReferenceFixtures.brightnessGem.asset()).plan)

        // Preview is deterministic even for signal-processing gems (empty signal batch)
        val passthroughFirst = GemExecutor.preview(passthroughPlan)
        val passthroughSecond = GemExecutor.preview(passthroughPlan)
        assertEquals(passthroughFirst, passthroughSecond)

        val brightnessFirst = GemExecutor.preview(brightnessPlan)
        val brightnessSecond = GemExecutor.preview(brightnessPlan)
        assertEquals(brightnessFirst, brightnessSecond)

        // The factor constant node should always resolve to 0.5 regardless of signal availability
        assertEquals(GemValue.Number(0.5), brightnessFirst.value(Gem.rootGraphId, "factor", "value"))
    }

    @Test
    fun previewCapturesHostOutputSignalBatches() {
        val asset = GemAsset(
            metadata = GemAssetMetadata(
                id = "gem://tests/host-output",
                name = "Host Output",
                category = GemCategory(id = "test", label = "Test")
            ),
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(
                        GemBuiltInNodes.hostLedInput.instantiate(
                            nodeId = "host-led-in",
                            serializedState = GemBuiltInNodes.hostPortState("led-in")
                        ),
                        GemBuiltInNodes.hostLedOutput.instantiate(
                            nodeId = "host-led-out",
                            serializedState = GemBuiltInNodes.hostPortState("led-out")
                        )
                    ),
                    connections = listOf(
                        GemConnection(
                            id = "pass-through",
                            from = GemPinRef(nodeId = "host-led-in", pinId = "signal"),
                            to = GemPinRef(nodeId = "host-led-out", pinId = "signal")
                        )
                    )
                ),
                host = GemHostIoContract(
                    assetShape = GemHostAssetShape.PROCESSOR,
                    supportedDomains = listOf(GemSignalDomain.LED),
                    inputs = listOf(GemHostPort(id = "led-in", domain = GemSignalDomain.LED, required = true)),
                    outputs = listOf(GemHostPort(id = "led-out", domain = GemSignalDomain.LED, required = true))
                )
            )
        )
        val plan = assertNotNull(GemCompiler.compile(asset).plan)
        val inputSignals = listOf(
            Signal.LED(origin = "runtime-test", x = 0, y = 1, color = Color.Red),
            Signal.LED(origin = "runtime-test", x = 2, y = 3, color = Color.Blue)
        )

        val result = GemExecutor.preview(
            plan = plan,
            context = GemPreviewContext(
                hostInputs = mapOf(
                    "led-in" to GemSignalBatch(
                        domain = GemSignalDomain.LED,
                        signals = inputSignals
                    )
                )
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(inputSignals, result.hostOutput("led-out")?.signals)
        assertEquals(
            GemSignalBatch(domain = GemSignalDomain.LED, signals = inputSignals),
            result.signalBatch(Gem.rootGraphId, "host-led-out", "signal")
        )
    }

    @Test
    fun executionSurfacesUnsupportedNodeSemantics() {
        val customDescriptor = gemNodeDescriptor(
            typeId = "amethyst.test.custom.preview",
            label = "Custom Preview Node",
            outputs = listOf(outputPin(id = "value", type = dev.anthonyhfm.amethyst.gem.GemPinType.Value(GemValueType.Number)))
        )
        val registry = GemNodeRegistry.of(listOf(customDescriptor))
        val asset = GemAsset(
            metadata = GemAssetMetadata(
                id = "gem://tests/custom-runtime",
                name = "Custom Runtime",
                category = GemCategory(id = "test", label = "Test")
            ),
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(customDescriptor.instantiate("custom"))
                ),
                host = GemHostIoContract(
                    assetShape = GemHostAssetShape.PROCESSOR,
                    supportedDomains = listOf(GemSignalDomain.LED)
                )
            )
        )

        val compiled = GemCompiler.compile(asset, registry)
        assertTrue(compiled.isSuccess)

        val result = GemExecutor.preview(assertNotNull(compiled.plan))
        assertFalse(result.isSuccess)
        assertTrue(result.diagnostics.any { it.code == GemRuntimeDiagnosticCode.UNSUPPORTED_NODE_SEMANTICS })
    }

    @Test
    fun compilerSupportsDirectExposedParameterInputBindings() {
        val asset = GemAsset(
            metadata = GemAssetMetadata(
                id = "gem://tests/input-binding",
                name = "Input Binding",
                category = GemCategory(id = "test", label = "Test")
            ),
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(GemBuiltInNodes.numberAdd.instantiate("scale"))
                ),
                host = GemHostIoContract(
                    assetShape = GemHostAssetShape.PROCESSOR,
                    supportedDomains = listOf(GemSignalDomain.LED)
                ),
                exposedParameters = listOf(
                    GemExposedParameter(
                        id = "amount",
                        label = "Amount",
                        type = GemValueType.Number,
                        defaultValue = GemValue.Number(2.5),
                        bindings = listOf(
                            GemGraphBinding(
                                graphId = Gem.rootGraphId,
                                nodeId = "scale",
                                pinId = "a",
                                targetKind = GemBindingTargetKind.INPUT_PIN
                            )
                        )
                    )
                )
            )
        )

        val compiled = GemCompiler.compile(asset)
        val plan = assertNotNull(compiled.plan)
        assertTrue(compiled.isSuccess)

        val result = GemExecutor.preview(plan)
        assertTrue(result.isSuccess)
        assertEquals(GemValue.Number(2.5), result.value(Gem.rootGraphId, "scale", "result"))
    }

    @Test
    fun compilerRejectsDuplicateExposedParameterIds() {
        val asset = GemAsset(
            metadata = GemAssetMetadata(
                id = "gem://tests/duplicate-parameters",
                name = "Duplicate Parameters",
                category = GemCategory(id = "test", label = "Test")
            ),
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(GemBuiltInNodes.numberAdd.instantiate("scale"))
                ),
                host = GemHostIoContract(
                    assetShape = GemHostAssetShape.PROCESSOR,
                    supportedDomains = listOf(GemSignalDomain.LED)
                ),
                exposedParameters = listOf(
                    GemExposedParameter(
                        id = "amount",
                        label = "Amount A",
                        type = GemValueType.Number,
                        defaultValue = GemValue.Number(1.0),
                        bindings = listOf(
                            GemGraphBinding(
                                graphId = Gem.rootGraphId,
                                nodeId = "scale",
                                pinId = "a",
                                targetKind = GemBindingTargetKind.INPUT_PIN
                            )
                        )
                    ),
                    GemExposedParameter(
                        id = "amount",
                        label = "Amount B",
                        type = GemValueType.Number,
                        defaultValue = GemValue.Number(2.0)
                    )
                )
            )
        )

        val compiled = GemCompiler.compile(asset)

        assertFalse(compiled.isSuccess)
        assertTrue(compiled.diagnostics.any { it.code == GemRuntimeDiagnosticCode.VALIDATION_ERROR })
    }

    @Test
    fun compilerRejectsInvalidDefaultStateParameterValues() {
        val asset = GemAsset(
            metadata = GemAssetMetadata(
                id = "gem://tests/invalid-default-state",
                name = "Invalid Default State",
                category = GemCategory(id = "test", label = "Test")
            ),
            definition = GemDefinition(
                rootGraph = GemGraph(
                    id = Gem.rootGraphId,
                    kind = GemGraphKind.ROOT,
                    nodes = listOf(GemBuiltInNodes.numberAdd.instantiate("scale"))
                ),
                host = GemHostIoContract(
                    assetShape = GemHostAssetShape.PROCESSOR,
                    supportedDomains = listOf(GemSignalDomain.LED)
                ),
                exposedParameters = listOf(
                    GemExposedParameter(
                        id = "amount",
                        label = "Amount",
                        type = GemValueType.Number,
                        defaultValue = GemValue.Number(1.0),
                        bindings = listOf(
                            GemGraphBinding(
                                graphId = Gem.rootGraphId,
                                nodeId = "scale",
                                pinId = "a",
                                targetKind = GemBindingTargetKind.INPUT_PIN
                            )
                        )
                    )
                ),
                defaultState = dev.anthonyhfm.amethyst.gem.GemDefaultState(
                    exposedParameterValues = mapOf(
                        "amount" to GemValue.Boolean(false)
                    )
                )
            )
        )

        val compiled = GemCompiler.compile(asset)

        assertFalse(compiled.isSuccess)
        assertTrue(compiled.diagnostics.any { it.code == GemRuntimeDiagnosticCode.VALIDATION_ERROR })
    }

    private fun jsonValue(value: GemValue) = GemJsonPersistence.json.encodeToJsonElement(
        serializer = GemValue.serializer(),
        value = value
    )
}
