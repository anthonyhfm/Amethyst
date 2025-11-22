package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.ui.input.pointer.PointerIcon
import org.jetbrains.skiko.Cursor

actual val PointerIcon.Companion.ResizeLeft: PointerIcon
    get() = PointerIcon(Cursor(Cursor.W_RESIZE_CURSOR))

actual val PointerIcon.Companion.ResizeRight: PointerIcon
    get() = PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR))

actual val PointerIcon.Companion.VerticalDrag: PointerIcon
    get() = PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR))