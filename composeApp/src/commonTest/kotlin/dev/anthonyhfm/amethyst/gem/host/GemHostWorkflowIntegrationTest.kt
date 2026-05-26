package dev.anthonyhfm.amethyst.gem.host

import dev.anthonyhfm.amethyst.core.controls.clipboard.ClipboardManager
import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.controls.shortcuts.handleDuplicateShortcut
import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
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
import dev.anthonyhfm.amethyst.workspace.WorkspaceContract
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GemHostWorkflowIntegrationTest {
    @AfterTest
    fun tearDown() {
        SelectionManager.clear()
        UndoManager.clear()
        WorkspaceRepository.clean()
    }

    @Test
    fun workspaceLoadBlocksGemDevicesInWrongHostContext() {
        val asset = buildHostPassthroughAsset(assetId = "gem://tests/host-context-mismatch")

        WorkspaceRepository.loadWorkspace(
            SavableWorkspaceData(
                gemAssets = listOf(SavableWorkspaceGemAsset.from(asset)),
                lights = StateChain(
                    devices = listOf(
                        GemDeviceState.fromAsset(
                            asset = asset,
                            hostDomain = GemSignalDomain.MIDI
                        )
                    )
                )
            )
        )

        val device = assertIs<GemChainDevice>(WorkspaceRepository.lightsChain.devices.value.single())

        assertEquals(GemDeviceValidationState.INVALID, device.validationStatus.value.state)
        assertEquals(GemDeviceRuntimeState.BLOCKED, device.runtimeStatus.value.state)
        assertTrue(device.validationStatus.value.issues.any { it.code == GemDeviceIssueCode.HOST_CONTEXT_MISMATCH })
    }

    @Test
    fun workspaceLoadKeepsGemDeviceWhenEmbeddedAssetPayloadIsInvalid() {
        val asset = buildHostPassthroughAsset(assetId = "gem://tests/invalid-embedded-asset")

        WorkspaceRepository.loadWorkspace(
            SavableWorkspaceData(
                gemAssets = listOf(
                    SavableWorkspaceGemAsset(
                        assetId = asset.metadata.id,
                        assetVersion = asset.metadata.assetVersion,
                        schemaVersion = asset.schemaVersion,
                        serializedAsset = "{ definitely-not-a-gem }"
                    )
                ),
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

        val device = assertIs<GemChainDevice>(WorkspaceRepository.lightsChain.devices.value.single())

        assertTrue(WorkspaceRepository.gemAssets.value.isEmpty())
        assertEquals(GemDeviceRuntimeState.BLOCKED, device.runtimeStatus.value.state)
        assertTrue(device.validationStatus.value.issues.any { it.code == GemDeviceIssueCode.MISSING_ASSET })
    }

    @Test
    fun copyPastePreservesGemDeviceStateAndHostResolution() {
        val asset = buildHostPassthroughAsset(assetId = "gem://tests/copy-paste")
        WorkspaceRepository.loadWorkspace(
            SavableWorkspaceData(
                gemAssets = listOf(SavableWorkspaceGemAsset.from(asset))
            )
        )
        WorkspaceRepository.switchMode(WorkspaceContract.WorkspaceMode.LightsChain(), undoable = false)

        val original = GemChainDevice(
            initialState = GemDeviceState.fromAsset(
                asset = asset,
                hostDomain = GemSignalDomain.LED
            )
        )
        WorkspaceRepository.lightsChain.add(original)

        ClipboardManager.copy(
            listOf(
                Selectable.ChainDevice(
                    parent = WorkspaceRepository.lightsChain,
                    device = original
                )
            )
        )
        SelectionManager.clear()

        ClipboardManager.paste()

        assertEquals(2, WorkspaceRepository.lightsChain.devices.value.size)

        val pasted = assertIs<GemChainDevice>(
            WorkspaceRepository.lightsChain.devices.value.first { it.selectionUUID != original.selectionUUID }
        )
        assertEquals(original.state.value, pasted.state.value)
        assertTrue(pasted.selectionUUID != original.selectionUUID)
        assertEquals(GemDeviceRuntimeState.READY, pasted.runtimeStatus.value.state)
    }

    @Test
    fun duplicateShortcutCreatesResolvedGemDeviceCopy() {
        val asset = buildHostPassthroughAsset(assetId = "gem://tests/duplicate-shortcut")
        WorkspaceRepository.loadWorkspace(
            SavableWorkspaceData(
                gemAssets = listOf(SavableWorkspaceGemAsset.from(asset))
            )
        )
        WorkspaceRepository.switchMode(WorkspaceContract.WorkspaceMode.LightsChain(), undoable = false)

        val original = GemChainDevice(
            initialState = GemDeviceState.fromAsset(
                asset = asset,
                hostDomain = GemSignalDomain.LED
            )
        )
        WorkspaceRepository.lightsChain.add(original)
        SelectionManager.select(
            Selectable.ChainDevice(
                parent = WorkspaceRepository.lightsChain,
                device = original
            )
        )

        assertTrue(handleDuplicateShortcut())
        assertEquals(2, WorkspaceRepository.lightsChain.devices.value.size)

        val duplicated = assertIs<GemChainDevice>(
            WorkspaceRepository.lightsChain.devices.value.first { it.selectionUUID != original.selectionUUID }
        )
        assertEquals(original.state.value, duplicated.state.value)
        assertEquals(GemDeviceRuntimeState.READY, duplicated.runtimeStatus.value.state)
    }

    @Test
    fun removingWorkspaceGemAssetLeavesExistingDeviceAsMissingPlaceholder() {
        val asset = buildHostPassthroughAsset(assetId = "gem://tests/remove-asset")
        WorkspaceRepository.loadWorkspace(
            SavableWorkspaceData(
                gemAssets = listOf(SavableWorkspaceGemAsset.from(asset))
            )
        )

        val device = GemChainDevice(
            initialState = GemDeviceState.fromAsset(
                asset = asset,
                hostDomain = GemSignalDomain.LED
            )
        )
        WorkspaceRepository.lightsChain.add(device)

        assertEquals(GemDeviceRuntimeState.READY, device.runtimeStatus.value.state)

        WorkspaceRepository.removeGemAsset(asset.metadata.id)

        assertEquals(listOf(device), WorkspaceRepository.lightsChain.devices.value)
        assertEquals(GemDeviceRuntimeState.BLOCKED, device.runtimeStatus.value.state)
        assertTrue(device.validationStatus.value.issues.any { it.code == GemDeviceIssueCode.MISSING_ASSET })
    }

    @Test
    fun undoRedoRestoresGemDeviceWithResolvedHostContext() {
        val asset = buildHostPassthroughAsset(assetId = "gem://tests/undo-redo")
        WorkspaceRepository.loadWorkspace(
            SavableWorkspaceData(
                gemAssets = listOf(SavableWorkspaceGemAsset.from(asset))
            )
        )

        WorkspaceRepository.lightsChain.add(
            GemChainDevice(
                initialState = GemDeviceState.fromAsset(
                    asset = asset,
                    hostDomain = GemSignalDomain.LED
                )
            )
        )

        assertEquals(1, WorkspaceRepository.lightsChain.devices.value.size)

        UndoManager.undo()
        assertTrue(WorkspaceRepository.lightsChain.devices.value.isEmpty())

        UndoManager.redo()

        val restored = assertIs<GemChainDevice>(WorkspaceRepository.lightsChain.devices.value.single())
        assertEquals(GemDeviceRuntimeState.READY, restored.runtimeStatus.value.state)
    }

    private fun buildHostPassthroughAsset(assetId: String): GemAsset = GemAsset(
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
}
