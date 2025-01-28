package dev.anthonyhfm.amethyst.editor.plugins.group

import dev.anthonyhfm.amethyst.editor.plugins.EffectPlugin
import kotlinx.coroutines.flow.MutableStateFlow

data class GroupData(
    val name: String,
    val effects: MutableStateFlow<List<EffectPlugin>> = MutableStateFlow(emptyList())
)