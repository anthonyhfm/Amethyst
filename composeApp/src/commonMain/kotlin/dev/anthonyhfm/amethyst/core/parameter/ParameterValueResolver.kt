package dev.anthonyhfm.amethyst.core.parameter

import dev.anthonyhfm.amethyst.workspace.data.ParameterMapping
import dev.anthonyhfm.amethyst.workspace.data.ParameterMappingMode

/** Deterministic base -> macros -> direct automation -> clamp resolution. */
object ParameterValueResolver {
    fun resolve(
        baseValue: Float,
        descriptor: ParameterDescriptor,
        target: ParameterAddress,
        mappings: List<ParameterMapping>,
        macroValues: Map<String, Float>,
        directAutomation: Float? = null,
        directAutomationAdditive: Boolean = false,
    ): Float {
        var normalized = descriptor.normalize(baseValue)
        var index = 0
        while (index < mappings.size) {
            val mapping = mappings[index]
            if (mapping.target == target) {
                val macro = macroValues[mapping.macroId]?.coerceIn(0f, 1f)
                if (macro != null) {
                val input = if (mapping.inverted) 1f - macro else macro
                val mapped = mapping.minimum + (mapping.maximum - mapping.minimum) * input
                normalized = when (mapping.mode) {
                    ParameterMappingMode.Absolute -> mapped
                    ParameterMappingMode.Additive -> normalized + mapped
                }
                }
            }
            index++
        }
        directAutomation?.takeIf(Float::isFinite)?.let { value ->
            normalized = if (directAutomationAdditive) normalized + value else value
        }
        return descriptor.denormalize(normalized.coerceIn(0f, 1f))
    }
}
