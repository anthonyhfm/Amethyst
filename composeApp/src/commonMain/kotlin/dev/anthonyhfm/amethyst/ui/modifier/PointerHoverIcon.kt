package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.ui.input.pointer.PointerIcon

/**
 * Extending on the pointerHoverIcon Modifier to add more icon support.
 */

expect val PointerIcon.Companion.ResizeLeft: PointerIcon
expect val PointerIcon.Companion.ResizeRight: PointerIcon
expect val PointerIcon.Companion.VerticalDrag: PointerIcon