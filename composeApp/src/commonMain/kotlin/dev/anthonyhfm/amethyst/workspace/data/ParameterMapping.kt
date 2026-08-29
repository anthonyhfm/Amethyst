package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.parameter.ParameterAddress
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class ParameterMappingMode {
    Absolute,
    Additive,
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ParameterMapping(
    @ProtoNumber(1)
    val id: String = UUID.randomUUID(),
    @ProtoNumber(2)
    val macroId: String,
    @ProtoNumber(3)
    val target: ParameterAddress,
    @ProtoNumber(4)
    val minimum: Float = 0f,
    @ProtoNumber(5)
    val maximum: Float = 1f,
    @ProtoNumber(6)
    val inverted: Boolean = false,
    @ProtoNumber(7)
    val mode: ParameterMappingMode = ParameterMappingMode.Absolute,
) {
    init {
        require(id.isNotBlank())
        require(macroId.isNotBlank())
        require(minimum.isFinite() && maximum.isFinite() && minimum <= maximum)
    }
}
