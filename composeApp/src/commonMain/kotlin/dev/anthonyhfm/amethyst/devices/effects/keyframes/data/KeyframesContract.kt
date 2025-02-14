package dev.anthonyhfm.amethyst.devices.effects.keyframes.data

import androidx.compose.ui.graphics.Color

interface KeyframesContract {
    sealed interface Event {
        data class SetColor(val x: Int, val y: Int, val color: Color)
    }
}