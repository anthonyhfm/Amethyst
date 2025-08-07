package dev.anthonyhfm.amethyst.core.controls.clipboard

import dev.anthonyhfm.amethyst.core.controls.selection.Selectable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object ClipboardManager {
    private val _clipboardData: MutableStateFlow<ClipboardData?> = MutableStateFlow(null)
    val clipboardData = _clipboardData.asStateFlow()

    fun setClipboardData(data: ClipboardData) {

    }

    fun copy(data: List<Selectable>) {
        when {
            data.any { it is Selectable.ChainDevice } -> {

            }

            data.any { it is Selectable.VirtualViewportDevice } -> {

            }
        }
    }
}