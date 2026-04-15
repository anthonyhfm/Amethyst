package dev.anthonyhfm.amethyst.gem.host

import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
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
import dev.anthonyhfm.amethyst.gem.GemNodePosition
import dev.anthonyhfm.amethyst.gem.GemPinRef
import dev.anthonyhfm.amethyst.gem.GemExposedParameter
import dev.anthonyhfm.amethyst.gem.GemReferenceFixtures
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValueType
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.AutoPlayData
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceGemAsset
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GemHostFoundationTest {
    @AfterTest
    fun tearDown() {
        WorkspaceRepository.clean()
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun stateChainSerializationPreservesGemDeviceStateScaffolding() {
        val asset = GemReferenceFixtures.passthroughGem.asset()
        val deviceState = GemDeviceState.fromAsset(
            asset = asset,
            hostDomain = GemSignalDomain.LED,
            exposedParameterValues = mapOf(
                "intensity" to GemValue.Number(0.35)
            )
        )
        val stateChain = StateChain(devices = listOf(deviceState))

        val encoded = AmethystProtoBuf.encodeToByteArray(StateChain.serializer(), stateChain)
        val decoded = AmethystProtoBuf.decodeFromByteArray(StateChain.serializer(), encoded)

        assertEquals(deviceState, decoded.devices.single())
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun workspaceSerializationPreservesEmbeddedGemAssets() {
        val workspace = SavableWorkspaceData(
            gemAssets = listOf(
                SavableWorkspaceGemAsset.from(GemReferenceFixtures.passthroughGem.asset()),
                SavableWorkspaceGemAsset.from(GemReferenceFixtures.passthroughGem.asset())
            )
        )

        val encoded = AmethystProtoBuf.encodeToByteArray(SavableWorkspaceData.serializer(), workspace)
        val decoded = AmethystProtoBuf.decodeFromByteArray(SavableWorkspaceData.serializer(), encoded)

        assertEquals(workspace.gemAssets, decoded.gemAssets)
        assertEquals(
            workspace.gemAssets.map(SavableWorkspaceGemAsset::toGemAsset),
            decoded.gemAssets.map(SavableWorkspaceGemAsset::toGemAsset)
        )
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun workspaceDeserializationRemainsCompatibleWithLegacyProtoFieldNumbers() {
        val legacyWorkspace = LegacySavableWorkspaceData(
            title = "Legacy Workspace",
            author = "Legacy Author",
            settings = WorkspaceSettings(bpm = 140.0),
            autoPlay = AutoPlayData(
                actions = mapOf(
                    1.0 to listOf(AutoPlayData.Action(x = 1, y = 2, down = true))
                )
            ),
            launchpadDevices = listOf(
                SavableWorkspaceData.SavableViewportLaunchpad(
                    positionX = 10f,
                    positionY = 20f,
                    type = SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                )
            ),
            macros = listOf(Macro(1), Macro(2))
        )

        val encoded = AmethystProtoBuf.encodeToByteArray(LegacySavableWorkspaceData.serializer(), legacyWorkspace)
        val decoded = AmethystProtoBuf.decodeFromByteArray(SavableWorkspaceData.serializer(), encoded)

        assertEquals(legacyWorkspace.title, decoded.title)
        assertEquals(legacyWorkspace.author, decoded.author)
        assertEquals(legacyWorkspace.settings, decoded.settings)
        assertEquals(legacyWorkspace.autoPlay, decoded.autoPlay)
        assertEquals(legacyWorkspace.launchpadDevices, decoded.launchpadDevices)
        assertEquals(legacyWorkspace.macros, decoded.macros)
        assertTrue(decoded.gemAssets.isEmpty())
    }

    @Test
    fun resolverUsesWorkspaceAssetsAndBuildsReadyRuntimeStatus() {
        val asset = buildHostPassthroughAsset()
        val resolution = GemHostResolver.resolve(
            deviceState = GemDeviceState.fromAsset(
                asset = asset,
                hostDomain = GemSignalDomain.LED
            ),
            assets = listOf(asset)
        )

        assertEquals(GemDeviceValidationState.VALID, resolution.validation.state)
        assertEquals(GemDeviceRuntimeState.READY, resolution.runtime.state)
        assertNotNull(resolution.plan)
        assertNotNull(resolution.contract)
        assertEquals("led-in", resolution.contract?.inputPortId)
        assertEquals("led-out", resolution.contract?.outputPortId)
        assertTrue(resolution.parameters.isEmpty())
    }

    @Test
    fun resolverSurfacesInvalidRuntimeContracts() {
        val asset = GemReferenceFixtures.passthroughGem.asset()
        val resolution = GemHostResolver.resolve(
            deviceState = GemDeviceState.fromAsset(
                asset = asset,
                hostDomain = GemSignalDomain.MIDI
            ),
            assets = listOf(asset)
        )

        assertEquals(GemDeviceValidationState.INVALID, resolution.validation.state)
        assertEquals(GemDeviceRuntimeState.BLOCKED, resolution.runtime.state)
        assertTrue(resolution.validation.issues.any { it.code == GemDeviceIssueCode.INVALID_HOST_CONTRACT })
    }

    @Test
    fun resolverSurfacesMissingAssets() {
        val asset = GemReferenceFixtures.passthroughGem.asset()
        val resolution = GemHostResolver.resolve(
            deviceState = GemDeviceState.fromAsset(
                asset = asset,
                hostDomain = GemSignalDomain.LED
            ),
            assets = emptyList()
        )

        assertEquals(GemDeviceValidationState.INVALID, resolution.validation.state)
        assertEquals(GemDeviceRuntimeState.BLOCKED, resolution.runtime.state)
        assertTrue(resolution.validation.issues.any { it.code == GemDeviceIssueCode.MISSING_ASSET })
    }

    @Test
    fun resolverSurfacesDomainAndParameterCompatibilityProblems() {
        val baseAsset = GemReferenceFixtures.passthroughGem.asset()
        val asset = baseAsset.copy(
            definition = baseAsset.definition.copy(
                exposedParameters = listOf(
                    GemExposedParameter(id = "intensity", type = GemValueType.Number, defaultValue = GemValue.Number(1.0))
                )
            )
        )
        val resolution = GemHostResolver.resolve(
            deviceState = GemDeviceState.fromAsset(
                asset = asset,
                hostDomain = GemSignalDomain.MIDI,
                exposedParameterValues = mapOf(
                    "intensity" to GemValue.Boolean(true),
                    "removed-parameter" to GemValue.Number(1.0)
                )
            ),
            assets = listOf(asset)
        )

        assertEquals(GemDeviceValidationState.INVALID, resolution.validation.state)
        assertEquals(GemDeviceRuntimeState.BLOCKED, resolution.runtime.state)
        assertTrue(resolution.validation.issues.any { it.code == GemDeviceIssueCode.HOST_DOMAIN_MISMATCH })
        assertTrue(resolution.validation.issues.any { it.code == GemDeviceIssueCode.INCOMPATIBLE_EXPOSED_PARAMETER_VALUE })
        assertTrue(resolution.validation.issues.any { it.code == GemDeviceIssueCode.UNKNOWN_EXPOSED_PARAMETER })
    }

    @Test
    fun workspaceRepositoryPersistsAndResolvesWorkspaceGemAssets() {
        val asset = buildHostPassthroughAsset(assetId = "gem://tests/workspace-host-passthrough")

        WorkspaceRepository.loadWorkspace(
            SavableWorkspaceData(
                title = "Gem Host Workspace",
                gemAssets = listOf(SavableWorkspaceGemAsset.from(asset))
            )
        )

        assertEquals(listOf(asset), WorkspaceRepository.gemAssets.value)
        assertEquals(asset, WorkspaceRepository.resolveGemAsset(GemAssetReference.from(asset)))

        val resolution = WorkspaceRepository.resolveGemDevice(
            GemDeviceState.fromAsset(
                asset = asset,
                hostDomain = GemSignalDomain.LED
            )
        )

        assertEquals(GemDeviceRuntimeState.READY, resolution.runtime.state)
        assertEquals(listOf(asset), WorkspaceRepository.saveWorkspace().gemAssets.map(SavableWorkspaceGemAsset::toGemAsset))
    }

    @Test
    fun workspaceLoadUnpacksGemDevicesIntoRuntimeWrapper() {
        val asset = buildHostPassthroughAsset(assetId = "gem://tests/unpack-host-passthrough")
        WorkspaceRepository.loadWorkspace(
            SavableWorkspaceData(
                gemAssets = listOf(SavableWorkspaceGemAsset.from(asset)),
                lights = StateChain(
                    devices = listOf(
                        GemDeviceState.fromAsset(
                            asset = asset,
                            hostDomain = GemSignalDomain.LED
                        )
                    )
                )
            )
        )
        val inputSignals = listOf(
            Signal.LED(origin = "workspace-test", x = 0, y = 0, color = Color.Red),
            Signal.LED(origin = "workspace-test", x = 1, y = 1, color = Color.Blue)
        )
        var emittedSignals: List<Signal> = emptyList()
        WorkspaceRepository.lightsChain.signalExit = { emittedSignals = it }

        val device = assertIs<GemChainDevice>(WorkspaceRepository.lightsChain.devices.value.single())
        WorkspaceRepository.lightsChain.signalEnter(inputSignals)

        assertEquals(inputSignals, emittedSignals)
        assertEquals(GemDeviceRuntimeState.READY, device.runtimeStatus.value.state)
    }

    @Test
    fun workspaceRepositoryCreatesWorkspaceScopedGemAssets() {
        val first = WorkspaceRepository.createGemAsset(preferredHostDomain = GemSignalDomain.LED)
        val second = WorkspaceRepository.createGemAsset(preferredHostDomain = GemSignalDomain.LED)

        assertEquals(listOf(GemSignalDomain.LED), first.definition.host.supportedDomains)
        assertEquals(listOf(GemSignalDomain.LED), second.definition.host.supportedDomains)
        assertTrue(first.metadata.id.startsWith("gem://workspace/"))
        assertTrue(second.metadata.id.startsWith("gem://workspace/"))
        assertNotEquals(first.metadata.id, second.metadata.id)
        assertEquals(listOf("Untitled Gem", "Untitled Gem 2"), WorkspaceRepository.gemAssets.value.map { it.metadata.name })
    }

    @Test
    fun workspaceRepositorySaveGemAssetRenamesWithoutDuplicatingCatalogEntries() {
        val original = WorkspaceRepository.createGemAsset(preferredHostDomain = GemSignalDomain.LED)
        val sibling = WorkspaceRepository.createGemAsset(preferredHostDomain = GemSignalDomain.LED)

        val renamed = WorkspaceRepository.saveGemAsset(
            asset = original.copy(
                metadata = original.metadata.copy(
                    id = "gem://workspace/renamed",
                    name = "Renamed Gem"
                )
            ),
            previousAssetId = original.metadata.id
        )

        assertEquals("gem://workspace/renamed", renamed.metadata.id)
        assertEquals("Renamed Gem", renamed.metadata.name)
        assertEquals(2, WorkspaceRepository.gemAssets.value.size)
        assertTrue(WorkspaceRepository.gemAssets.value.none { it.metadata.id == original.metadata.id })
        assertTrue(WorkspaceRepository.gemAssets.value.any { it.metadata.id == sibling.metadata.id })
        assertEquals(renamed, WorkspaceRepository.gemAssets.value.first { it.metadata.id == renamed.metadata.id })
    }

    @Test
    fun workspaceSaveAndReloadPreservesGemLayoutMetadata() {
        val positionedAsset = buildHostPassthroughAsset(assetId = "gem://tests/layout-persistence")
            .moveNode(
                nodeId = "host-led-in",
                position = GemNodePosition(x = 48f, y = 96f)
            )
            .moveNode(
                nodeId = "host-led-out",
                position = GemNodePosition(x = 320f, y = 180f)
            )

        WorkspaceRepository.loadWorkspace(
            SavableWorkspaceData(
                title = "Layout Workspace"
            )
        )
        WorkspaceRepository.saveGemAsset(positionedAsset)

        val savedWorkspace = WorkspaceRepository.saveWorkspace()
        WorkspaceRepository.clean()
        WorkspaceRepository.loadWorkspace(savedWorkspace)

        val restoredAsset = WorkspaceRepository.gemAssets.value.single()
        assertEquals(positionedAsset, restoredAsset)
        assertEquals(
            GemNodePosition(x = 48f, y = 96f),
            restoredAsset.graph()!!.node("host-led-in")!!.layout.position
        )
        assertEquals(
            GemNodePosition(x = 320f, y = 180f),
            restoredAsset.graph()!!.node("host-led-out")!!.layout.position
        )
    }

    private fun buildHostPassthroughAsset(assetId: String = "gem://tests/host-passthrough"): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = assetId,
            name = "Host Passthrough",
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

    @Serializable
    private data class LegacySavableWorkspaceData(
        @ProtoNumber(1)
        val version: dev.anthonyhfm.amethyst.core.util.Version = dev.anthonyhfm.amethyst.core.util.amethystVersion,
        @ProtoNumber(2)
        val title: String = "Untitled Workspace",
        @ProtoNumber(3)
        val author: String = "Unknown Author",
        @ProtoNumber(4)
        val settings: WorkspaceSettings = WorkspaceSettings(),
        @ProtoNumber(5)
        val timelineData: List<String> = emptyList(),
        @ProtoNumber(6)
        val lights: StateChain = StateChain(),
        @ProtoNumber(7)
        val sampling: StateChain = StateChain(),
        @ProtoNumber(8)
        val autoPlay: AutoPlayData = AutoPlayData(emptyMap()),
        @ProtoNumber(9)
        val launchpadDevices: List<SavableWorkspaceData.SavableViewportLaunchpad> = emptyList(),
        @ProtoNumber(10)
        val macros: List<Macro> = listOf(Macro(0))
    )
}
