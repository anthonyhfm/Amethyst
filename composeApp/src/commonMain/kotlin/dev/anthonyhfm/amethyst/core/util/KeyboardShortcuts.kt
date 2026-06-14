package dev.anthonyhfm.amethyst.core.util

import androidx.compose.ui.input.key.Key

val usesMacKeyboardShortcuts: Boolean
    get() = platform is Platform.Desktop.MacOS

fun primaryModifierLabel(): String =
    if (usesMacKeyboardShortcuts) "⌘" else "Ctrl"

fun primaryModifierShortcutLabel(key: String): String =
    if (usesMacKeyboardShortcuts) {
        primaryModifierLabel() + key
    } else {
        "${primaryModifierLabel()}+$key"
    }

