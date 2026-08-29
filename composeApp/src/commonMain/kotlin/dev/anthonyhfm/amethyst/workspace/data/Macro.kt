package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Macro(
    @ProtoNumber(1)
    val value: Int,
    @ProtoNumber(2)
    val id: String = UUID.randomUUID(),
    @ProtoNumber(3)
    val name: String = "",
) {
    val normalizedValue: Float get() = value.coerceIn(0, 127) / 127f
    val displayName: String get() = name.ifBlank { "Macro" }
    fun withoutLocalValue(): Macro = copy(value = 0)
}

/**
 * Applies synchronized macro structure while keeping this client's performance
 * values local. IDs are matched first; the index fallback migrates sessions
 * whose in-memory macros predate persistent identities.
 */
internal fun mergeMacroStructure(
    current: List<Macro>,
    remoteStructure: List<Macro>,
): List<Macro> {
    val currentById = current.associateBy(Macro::id)
    return remoteStructure.mapIndexed { index, remote ->
        val local = currentById[remote.id] ?: current.getOrNull(index)
        remote.copy(value = local?.value ?: 0)
    }
}
