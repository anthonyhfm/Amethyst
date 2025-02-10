package dev.anthonyhfm.amethyst.ui.modifier

import android.annotation.SuppressLint
import androidx.compose.ui.Modifier

@SuppressLint("ModifierFactoryUnreferencedReceiver")
actual fun Modifier.platformPaddingTop(): Modifier {
    return this
}