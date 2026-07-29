package dev.anthonyhfm.amethyst.devices

import dev.anthonyhfm.amethyst.core.controls.automation.AutomationParameter
import kotlin.reflect.KClass

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Automatable(
    val parameter: KClass<out AutomationParameter>,
)
