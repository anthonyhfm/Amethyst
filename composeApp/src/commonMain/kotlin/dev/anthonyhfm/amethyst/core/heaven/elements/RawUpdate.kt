package dev.anthonyhfm.amethyst.core.heaven.elements

import androidx.compose.ui.graphics.Color

data class RawUpdate(var index: Byte, var color: Color) {
    fun offset(offset: Int) {
        index = (index + offset).toByte()
    }

    constructor(index: Int, color: Color) : this(index.toByte(), color.copy())

    constructor(n: RawUpdate, offset: Int) : this((n.index + offset).toByte(), n.color.copy())
}