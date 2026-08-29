@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacroIdentityTest {
    @Test
    fun protobufRoundTripPreservesId() {
        val macro = Macro(value = 91, id = "macro-kick")

        val bytes = AmethystProtoBuf.encodeToByteArray(Macro.serializer(), macro)
        val restored = AmethystProtoBuf.decodeFromByteArray(Macro.serializer(), bytes)

        assertEquals(macro, restored)
    }

    @Test
    fun legacyMacroWithoutIdReceivesOneDuringMigration() {
        val legacyBytes = AmethystProtoBuf.encodeToByteArray(
            LegacyMacro.serializer(),
            LegacyMacro(value = 42),
        )

        val migrated = AmethystProtoBuf.decodeFromByteArray(
            Macro.serializer(),
            legacyBytes,
        )

        assertEquals(42, migrated.value)
        assertTrue(migrated.id.isNotBlank())
    }

    @Test
    fun remoteStructureUpdatesIdsWithoutReplacingLocalValues() {
        val current = listOf(
            Macro(value = 12, id = "macro-a"),
            Macro(value = 34, id = "macro-b"),
        )
        val remote = listOf(
            Macro(value = 0, id = "macro-b"),
            Macro(value = 0, id = "macro-a"),
            Macro(value = 0, id = "macro-c"),
        )

        val merged = mergeMacroStructure(current, remote)

        assertEquals(listOf("macro-b", "macro-a", "macro-c"), merged.map(Macro::id))
        assertEquals(listOf(34, 12, 0), merged.map(Macro::value))
    }

    @Test
    fun indexFallbackMigratesLocalMacrosThatDoNotShareRemoteIdsYet() {
        val current = listOf(
            Macro(value = 23, id = "old-local-a"),
            Macro(value = 67, id = "old-local-b"),
        )
        val remote = listOf(
            Macro(value = 0, id = "host-a"),
            Macro(value = 0, id = "host-b"),
        )

        val merged = mergeMacroStructure(current, remote)

        assertEquals(listOf("host-a", "host-b"), merged.map(Macro::id))
        assertEquals(listOf(23, 67), merged.map(Macro::value))
    }
}

@Serializable
private data class LegacyMacro(
    @ProtoNumber(1)
    val value: Int,
)
