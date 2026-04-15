package dev.anthonyhfm.amethyst.devices.gem

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
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
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemReferenceFixtures
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValueType
import dev.anthonyhfm.amethyst.gem.data.GemJsonPersistence
import dev.anthonyhfm.amethyst.gem.host.GemDeviceIssueCode
import dev.anthonyhfm.amethyst.gem.host.GemDeviceRuntimeState
import dev.anthonyhfm.amethyst.gem.host.GemDeviceState
import dev.anthonyhfm.amethyst.gem.host.GemDeviceValidationState
import dev.anthonyhfm.amethyst.gem.host.GemHostResolver
import dev.anthonyhfm.amethyst.gem.runtime.GemPreviewContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GemChainDeviceTest {
    @Test
    fun signalEnterBridgesHostSignalsDeterministically() {
        val asset = buildHostPassthroughAsset()
        val state = GemDeviceState.fromAsset(asset = asset, hostDomain = GemSignalDomain.LED)
        val device = GemChainDevice(
            initialState = state,
            resolver = { GemHostResolver.resolve(it, listOf(asset)) }
        )
        val inputSignals = listOf(
            Signal.LED(origin = "test", x = 1, y = 2, color = Color.Red),
            Signal.LED(origin = "test", x = 3, y = 4, color = Color.Blue)
        )
        var emittedSignals: List<Signal> = emptyList()
        device.signalExit = { emittedSignals = it }

        device.signalEnter(inputSignals)
        val firstStatus = device.runtimeStatus.value
        val firstExecution = device.lastExecutionResult.value

        assertEquals(inputSignals, emittedSignals)
        assertEquals(GemDeviceRuntimeState.READY, firstStatus.state)
        // Per-signal execution: lastExecutionResult reflects only the last processed signal
        assertEquals(listOf(inputSignals.last()), firstExecution?.hostOutput("led-out")?.signals)

        device.signalEnter(inputSignals)

        assertEquals(inputSignals, emittedSignals)
        assertEquals(firstStatus, device.runtimeStatus.value)
        assertEquals(firstExecution, device.lastExecutionResult.value)
    }

    @Test
    fun signalEnterSurfacesMissingAssetsAtDeviceLevel() {
        val asset = buildHostPassthroughAsset()
        val device = GemChainDevice(
            initialState = GemDeviceState.fromAsset(asset = asset, hostDomain = GemSignalDomain.LED),
            resolver = { GemHostResolver.resolve(it, emptyList()) }
        )
        var emittedSignals: List<Signal> = listOf(Signal.LED(origin = "before", x = 0, y = 0, color = Color.Black))
        device.signalExit = { emittedSignals = it }

        device.signalEnter(listOf(Signal.LED(origin = "test", x = 1, y = 1, color = Color.Green)))

        assertEquals(emptyList(), emittedSignals)
        assertEquals(GemDeviceRuntimeState.BLOCKED, device.runtimeStatus.value.state)
        assertTrue(device.runtimeStatus.value.issues.any { it.code == GemDeviceIssueCode.MISSING_ASSET })
        assertNull(device.lastExecutionResult.value)
    }

    @Test
    fun signalEnterSurfacesInvalidContractsAtDeviceLevel() {
        val asset = buildInvalidContractAsset()
        val device = GemChainDevice(
            initialState = GemDeviceState.fromAsset(asset = asset, hostDomain = GemSignalDomain.LED),
            resolver = { GemHostResolver.resolve(it, listOf(asset)) }
        )

        device.signalEnter(listOf(Signal.LED(origin = "test", x = 2, y = 2, color = Color.Yellow)))

        assertEquals(GemDeviceValidationState.INVALID, device.validationStatus.value.state)
        assertEquals(GemDeviceRuntimeState.BLOCKED, device.runtimeStatus.value.state)
        assertTrue(device.validationStatus.value.issues.any { it.code == GemDeviceIssueCode.INVALID_HOST_CONTRACT })
        assertNull(device.lastExecutionResult.value)
    }

    @Test
    fun signalEnterSurfacesCompilationFailuresAtDeviceLevel() {
        val asset = buildCompileFailureAsset()
        val device = GemChainDevice(
            initialState = GemDeviceState.fromAsset(asset = asset, hostDomain = GemSignalDomain.LED),
            resolver = { GemHostResolver.resolve(it, listOf(asset)) }
        )

        device.signalEnter(listOf(Signal.LED(origin = "test", x = 0, y = 0, color = Color.Magenta)))

        assertEquals(GemDeviceValidationState.INVALID, device.validationStatus.value.state)
        assertEquals(GemDeviceRuntimeState.BLOCKED, device.runtimeStatus.value.state)
        assertTrue(device.validationStatus.value.issues.any { it.code == GemDeviceIssueCode.ASSET_COMPILE_ERROR })
        assertNull(device.lastExecutionResult.value)
    }

    @Test
    fun signalEnterSurfacesRuntimeFailuresAtDeviceLevel() {
        val asset = buildHostPassthroughAsset()
        val readyResolution = GemHostResolver.resolve(
            deviceState = GemDeviceState.fromAsset(asset = asset, hostDomain = GemSignalDomain.LED),
            assets = listOf(asset)
        )
        val device = GemChainDevice(
            initialState = GemDeviceState.fromAsset(asset = asset, hostDomain = GemSignalDomain.LED),
            resolver = { readyResolution },
            executor = { _: dev.anthonyhfm.amethyst.gem.runtime.GemExecutionPlan, _: GemPreviewContext ->
                throw IllegalStateException("boom")
            }
        )
        var emittedSignals: List<Signal> = listOf(Signal.LED(origin = "before", x = 0, y = 0, color = Color.Black))
        device.signalExit = { emittedSignals = it }

        device.signalEnter(listOf(Signal.LED(origin = "test", x = 9, y = 9, color = Color.Cyan)))

        assertEquals(emptyList(), emittedSignals)
        assertEquals(GemDeviceRuntimeState.ERROR, device.runtimeStatus.value.state)
        assertTrue(device.runtimeStatus.value.issues.any { it.code == GemDeviceIssueCode.RUNTIME_FAILURE })
        assertNull(device.lastExecutionResult.value)
    }

    private fun buildHostPassthroughAsset(assetId: String = "gem://tests/device-host-passthrough"): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = assetId,
            name = "Device Host Passthrough",
            category = GemCategory(id = "test", label = "Test")
        ),
        definition = GemDefinition(
            rootGraph = GemGraph(
                id = "root",
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

    private fun buildCompileFailureAsset(): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = "gem://tests/device-compile-failure",
            name = "Device Compile Failure",
            category = GemCategory(id = "test", label = "Test")
        ),
        definition = GemDefinition(
            rootGraph = GemGraph(
                id = "root",
                kind = GemGraphKind.ROOT,
                nodes = listOf(
                    GemBuiltInNodes.hostLedInput.instantiate(
                        nodeId = "host-led-in",
                        serializedState = GemBuiltInNodes.hostPortState("led-in")
                    ),
                    GemBuiltInNodes.hostLedOutput.instantiate(
                        nodeId = "host-led-out",
                        serializedState = GemBuiltInNodes.hostPortState("led-out")
                    ),
                    GemBuiltInNodes.numberAdd.instantiate(nodeId = "add"),
                    GemBuiltInNodes.constantNumber.instantiate(
                        nodeId = "constant",
                        serializedState = mapOf(
                            "value" to GemJsonPersistence.json.encodeToJsonElement(
                                serializer = GemValue.serializer(),
                                value = GemValue.Number(1.0)
                            )
                        )
                    )
                ),
                connections = listOf(
                    GemConnection(
                        id = "pass-through",
                        from = GemPinRef(nodeId = "host-led-in", pinId = "signal"),
                        to = GemPinRef(nodeId = "host-led-out", pinId = "signal")
                    ),
                    GemConnection(
                        id = "constant-into-add",
                        from = GemPinRef(nodeId = "constant", pinId = "value"),
                        to = GemPinRef(nodeId = "add", pinId = "a")
                    )
                )
            ),
            host = GemHostIoContract(
                assetShape = GemHostAssetShape.PROCESSOR,
                supportedDomains = listOf(GemSignalDomain.LED),
                inputs = listOf(GemHostPort(id = "led-in", domain = GemSignalDomain.LED, required = true)),
                outputs = listOf(GemHostPort(id = "led-out", domain = GemSignalDomain.LED, required = true))
            ),
            exposedParameters = listOf(
                GemExposedParameter(
                    id = "amount",
                    label = "Amount",
                    type = GemValueType.Number,
                    defaultValue = GemValue.Number(2.0),
                    bindings = listOf(
                        GemGraphBinding(
                            graphId = "root",
                            nodeId = "add",
                            pinId = "a",
                            targetKind = GemBindingTargetKind.INPUT_PIN
                        )
                    )
                )
            )
        )
    )

    private fun buildInvalidContractAsset(): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = "gem://tests/device-invalid-contract",
            name = "Invalid Contract Gem",
            category = GemCategory(id = "test", label = "Test")
        ),
        definition = GemDefinition(
            rootGraph = GemGraph(
                id = "root",
                kind = GemGraphKind.ROOT,
                nodes = emptyList(),
                connections = emptyList()
            ),
            host = GemHostIoContract(
                assetShape = GemHostAssetShape.SOURCE,
                supportedDomains = listOf(GemSignalDomain.LED),
                inputs = emptyList(),
                outputs = emptyList()
            )
        )
    )
}
