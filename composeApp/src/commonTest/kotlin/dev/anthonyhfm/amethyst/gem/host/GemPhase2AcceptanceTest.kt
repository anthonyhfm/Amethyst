package dev.anthonyhfm.amethyst.gem.host

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.devices.gem.GemChainDevice
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemAssetMetadata
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemCategory
import dev.anthonyhfm.amethyst.gem.GemConnection
import dev.anthonyhfm.amethyst.gem.GemDefinition
import dev.anthonyhfm.amethyst.gem.GemGraph
import dev.anthonyhfm.amethyst.gem.GemGraphKind
import dev.anthonyhfm.amethyst.gem.GemHostAssetShape
import dev.anthonyhfm.amethyst.gem.GemHostIoContract
import dev.anthonyhfm.amethyst.gem.GemHostPort
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GemPhase2AcceptanceTest {
    @AfterTest
    fun tearDown() {
        WorkspaceRepository.clean()
    }

    @Test
    fun versionWarningsDegradeReferenceGemWithoutBlockingRuntime() {
        val originalAsset = buildHostCompatibleReferenceAsset()
        val upgradedAsset = originalAsset.copy(
            schemaVersion = Version(2, 0, 0),
            metadata = originalAsset.metadata.copy(assetVersion = Version(2, 0, 0))
        )
        val device = GemChainDevice(
            initialState = GemDeviceState.fromAsset(
                asset = originalAsset,
                hostDomain = GemSignalDomain.LED
            ),
            initialHostContextDomain = GemSignalDomain.LED,
            resolver = { GemHostResolver.resolve(it, listOf(upgradedAsset)) }
        )
        val inputSignals = listOf(
            Signal.LED(origin = "version-warning", x = 2, y = 3, color = Color.Cyan)
        )
        var emittedSignals: List<Signal> = emptyList()
        device.signalExit = { emittedSignals = it }

        device.signalEnter(inputSignals)

        assertEquals(GemDeviceValidationState.VALID, device.validationStatus.value.state)
        assertEquals(GemDeviceRuntimeState.DEGRADED, device.runtimeStatus.value.state)
        assertEquals(inputSignals, emittedSignals)
        assertTrue(device.runtimeStatus.value.issues.any { it.code == GemDeviceIssueCode.ASSET_VERSION_CHANGED })
        assertTrue(device.runtimeStatus.value.issues.any { it.code == GemDeviceIssueCode.SCHEMA_VERSION_CHANGED })
    }

    private fun buildHostCompatibleReferenceAsset(): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = "gem://tests/phase2-reference",
            name = "Reference Gem",
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
                        id = "host-led-pass-through",
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
}
