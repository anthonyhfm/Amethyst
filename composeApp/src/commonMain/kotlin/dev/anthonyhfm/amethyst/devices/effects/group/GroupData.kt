package dev.anthonyhfm.amethyst.devices.effects.group

import dev.anthonyhfm.amethyst.devices.effects.EffectDevice
import kotlinx.coroutines.flow.MutableStateFlow

data class GroupData(
    val name: String,
    val effects: MutableStateFlow<List<EffectDevice>> = MutableStateFlow(emptyList())
)