package dev.anthonyhfm.amethyst.core.parameter

import dev.anthonyhfm.amethyst.core.controls.automation.LiveAutomationTarget
import dev.anthonyhfm.amethyst.devices.AudioChainDevice
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.data.ParameterMappingMode

/** Allocation-free parameter resolution for the audio callback. */
fun AudioChainDevice<*>.resolveRealtimeParameter(
    descriptor: ParameterDescriptor,
    baseValue: Float,
    frame: Long,
): Float {
    val address = ParameterAddress(selectionUUID, descriptor.id)
    val mappings = WorkspaceRepository.parameterMappings.value
    val macros = WorkspaceRepository.macros.value
    var normalized = descriptor.normalize(baseValue)
    var mappingIndex = 0
    while (mappingIndex < mappings.size) {
        val mapping = mappings[mappingIndex]
        if (mapping.target == address) {
            var macroValue: Float? = audioTriggerRuntime?.automationValue(
                LiveAutomationTarget.Macro(mapping.macroId),
                frame,
            )
            if (macroValue == null) {
                var macroIndex = 0
                while (macroIndex < macros.size) {
                    if (macros[macroIndex].id == mapping.macroId) {
                        macroValue = macros[macroIndex].normalizedValue
                        break
                    }
                    macroIndex++
                }
            }
            macroValue?.let { value ->
                val input = if (mapping.inverted) 1f - value else value
                val mapped = mapping.minimum + (mapping.maximum - mapping.minimum) * input
                normalized = when (mapping.mode) {
                    ParameterMappingMode.Absolute -> mapped
                    ParameterMappingMode.Additive -> normalized + mapped
                }
            }
        }
        mappingIndex++
    }
    audioTriggerRuntime?.automationValue(
        LiveAutomationTarget.Parameter(address),
        frame,
    )?.let { normalized = it }
    normalized = evaluateAutomatedDialValueAtFrame(descriptor.id, normalized, frame)
    return descriptor.denormalize(normalized.coerceIn(0f, 1f))
}

/** Shared macro/direct-automation resolution for non-audio devices on their signal thread. */
fun GenericChainDevice<*>.resolveControlParameter(
    descriptor: ParameterDescriptor,
    baseValue: Float,
): Float {
    val address = ParameterAddress(selectionUUID, descriptor.id)
    val mappings = WorkspaceRepository.parameterMappings.value
    val macros = WorkspaceRepository.macros.value
    var normalized = descriptor.normalize(baseValue)
    mappings.forEach { mapping ->
        if (mapping.target == address) {
            val macro = macros.firstOrNull { it.id == mapping.macroId }
            macro?.let {
                val input = if (mapping.inverted) 1f - it.normalizedValue else it.normalizedValue
                val mapped = mapping.minimum + (mapping.maximum - mapping.minimum) * input
                normalized = when (mapping.mode) {
                    ParameterMappingMode.Absolute -> mapped
                    ParameterMappingMode.Additive -> normalized + mapped
                }
            }
        }
    }
    normalized = evaluateAutomatedDialValue(
        parameterId = descriptor.id,
        manualNormalizedValue = normalized.coerceIn(0f, 1f),
        bpm = WorkspaceRepository.bpm.value.toFloat(),
    )
    return descriptor.denormalize(normalized.coerceIn(0f, 1f))
}
