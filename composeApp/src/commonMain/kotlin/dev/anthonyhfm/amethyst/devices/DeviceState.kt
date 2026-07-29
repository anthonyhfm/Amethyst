package dev.anthonyhfm.amethyst.devices

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
abstract class DeviceState {
    @Transient
    open var isMuted: Boolean = false

    @Transient
    open var isCollapsed: Boolean = false

    @Transient
    open val automations: Map<String, dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane>
        get() = emptyMap()

    open fun withAutomations(
        automations: Map<String, dev.anthonyhfm.amethyst.core.controls.automation.DialAutomationLane>
    ): DeviceState = this
}