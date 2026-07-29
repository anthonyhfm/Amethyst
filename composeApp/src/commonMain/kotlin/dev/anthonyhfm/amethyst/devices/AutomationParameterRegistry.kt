package dev.anthonyhfm.amethyst.devices

import dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

object AutomationParameterRegistry {
    private val cache = mutableMapOf<KClass<*>, List<AutomationParameter>>()

    fun parametersFor(stateClass: KClass<*>): List<AutomationParameter> {
        return cache.getOrPut(stateClass) {
            stateClass.memberProperties.mapNotNull { prop ->
                val ann = prop.findAnnotation<Automatable>() ?: return@mapNotNull null
                ann.parameter.objectInstance
                    ?: error("@Automatable parameter ${ann.parameter.simpleName} must be an object, not a class")
            }
        }
    }

    fun clearCache() = cache.clear()
}
