package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.ui.input.pointer.PointerIcon

actual val PointerIcon.Companion.ResizeLeft: PointerIcon
    get() = PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)

actual val PointerIcon.Companion.ResizeRight: PointerIcon
    get() = PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)