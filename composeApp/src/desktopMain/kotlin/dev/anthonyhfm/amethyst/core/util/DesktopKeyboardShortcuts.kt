package dev.anthonyhfm.amethyst.core.util

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut

fun primaryKeyShortcut(
    key: Key,
    shift: Boolean = false,
    alt: Boolean = false,
): KeyShortcut =
    if (usesMacKeyboardShortcuts) {
        KeyShortcut(key = key, meta = true, shift = shift, alt = alt)
    } else {
        KeyShortcut(key = key, ctrl = true, shift = shift, alt = alt)
    }

fun redoKeyShortcut(): KeyShortcut =
    if (usesMacKeyboardShortcuts) {
        KeyShortcut(key = Key.Z, meta = true, shift = true)
    } else {
        KeyShortcut(key = Key.Y, ctrl = true)
    }
