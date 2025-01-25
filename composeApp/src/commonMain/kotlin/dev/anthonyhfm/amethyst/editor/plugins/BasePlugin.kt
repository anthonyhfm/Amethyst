package dev.anthonyhfm.amethyst.editor.plugins

import kotlinx.coroutines.flow.MutableStateFlow

interface BasePlugin <Data> {
    var isEnabled: MutableStateFlow<Boolean>

    suspend fun passData(data: Data)
}