package dev.anthonyhfm.amethyst.editor.plugins.group

import dev.anthonyhfm.amethyst.editor.plugins.EffectDevice
import kotlinx.coroutines.flow.MutableStateFlow

data class GroupData(
    val name: String,
    val effects: MutableStateFlow<List<EffectDevice>> = MutableStateFlow(emptyList())
)