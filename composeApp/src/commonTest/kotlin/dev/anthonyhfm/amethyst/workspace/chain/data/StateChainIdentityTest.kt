@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.workspace.chain.data

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDevice
import dev.anthonyhfm.amethyst.devices.audio.sample.SampleChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StateChainIdentityTest {
    @Test
    fun protobufRoundTripPreservesRootAndNestedDeviceIds() {
        val source = nestedSampleChain()
        val packed = StateChain.pack(source)
        val encoded = AmethystProtoBuf.encodeToByteArray(StateChain.serializer(), packed)
        val decoded = AmethystProtoBuf.decodeFromByteArray(StateChain.serializer(), encoded)

        val restored = decoded.unpack()
        val restoredGroup = restored.devices.value.single() as GroupChainDevice

        assertEquals(GROUP_ID, restoredGroup.selectionUUID)
        assertEquals(SAMPLE_ID, nestedSample(restoredGroup).selectionUUID)
    }

    @Test
    fun unpackCopyRegeneratesIdsForTheCompleteSubtree() {
        val source = nestedSampleChain()

        val copied = StateChain.pack(source).unpackCopy()
        val copiedGroup = copied.devices.value.single() as GroupChainDevice
        val copiedSample = nestedSample(copiedGroup)

        assertNotEquals(GROUP_ID, copiedGroup.selectionUUID)
        assertNotEquals(SAMPLE_ID, copiedSample.selectionUUID)
        assertNotEquals(copiedGroup.selectionUUID, copiedSample.selectionUUID)
    }

    @Test
    fun unpackDeviceRegeneratesNestedIdsForClipboardCopies() {
        val originalGroup = nestedSampleChain().devices.value.single() as GroupChainDevice

        val copiedGroup = StateChain.unpackDevice(
            StateChain.packDevice(originalGroup),
        ) as GroupChainDevice

        assertNotEquals(GROUP_ID, copiedGroup.selectionUUID)
        assertNotEquals(SAMPLE_ID, nestedSample(copiedGroup).selectionUUID)
    }

    @Test
    fun duplicateOrBlankSavedIdsAreRepaired() {
        val restored = StateChain(
            devices = listOf(SampleChainDeviceState(), SampleChainDeviceState()),
            deviceIds = listOf("duplicate", "duplicate"),
        ).unpack()
        val ids = restored.devices.value.map { it.selectionUUID }

        assertEquals("duplicate", ids.first())
        assertTrue(ids.all(String::isNotBlank))
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun duplicateIdsAcrossParallelNestedBranchesAreRepaired() {
        val duplicateChildState = StateChain(
            devices = listOf(SampleChainDeviceState()),
            deviceIds = listOf("nested-duplicate"),
        )
        val groupState = GroupChainDeviceState(
            groups = listOf(
                Group(name = "A", stateChain = duplicateChildState),
                Group(name = "B", stateChain = duplicateChildState),
            ),
        )

        val restored = StateChain(
            devices = listOf(groupState),
            deviceIds = listOf("group"),
        ).unpack()
        val group = restored.devices.value.single() as GroupChainDevice
        val nestedIds = group.state.value.groups.map { branch ->
            branch.chain.devices.value.single().selectionUUID
        }

        assertEquals("nested-duplicate", nestedIds.first())
        assertEquals(nestedIds.size, nestedIds.distinct().size)
    }

    @Test
    fun legacyStateChainWithoutDeviceIdsMigratesOnLoad() {
        val legacyBytes = AmethystProtoBuf.encodeToByteArray(
            LegacyStateChain.serializer(),
            LegacyStateChain(devices = listOf(SampleChainDeviceState())),
        )

        val migrated = AmethystProtoBuf.decodeFromByteArray(
            StateChain.serializer(),
            legacyBytes,
        )
        val restored = migrated.unpack()

        assertTrue(migrated.deviceIds.isEmpty())
        assertTrue(restored.devices.value.single().selectionUUID.isNotBlank())
        assertEquals(
            restored.devices.value.single().selectionUUID,
            StateChain.pack(restored).deviceIds.single(),
        )
    }

    private fun nestedSampleChain(): Chain {
        val child = Chain().apply {
            add(
                SampleChainDevice().apply { selectionUUID = SAMPLE_ID },
                fromUser = false,
            )
        }
        val group = GroupChainDevice().apply {
            selectionUUID = GROUP_ID
            loadFromState(
                GroupChainDeviceState(
                    groups = listOf(
                        Group(
                            name = "Sample branch",
                            stateChain = StateChain.pack(child),
                        ),
                    ),
                ),
            )
        }
        return Chain().apply { add(group, fromUser = false) }
    }

    private fun nestedSample(group: GroupChainDevice): SampleChainDevice =
        group.state.value.groups.single().chain.devices.value.single() as SampleChainDevice

    companion object {
        private const val GROUP_ID = "group-device-id"
        private const val SAMPLE_ID = "sample-device-id"
    }
}

@Serializable
private data class LegacyStateChain(
    @ProtoNumber(1)
    val devices: List<@Polymorphic DeviceState> = emptyList(),
    @ProtoNumber(2)
    val mutedDeviceIndices: List<Int> = emptyList(),
)
