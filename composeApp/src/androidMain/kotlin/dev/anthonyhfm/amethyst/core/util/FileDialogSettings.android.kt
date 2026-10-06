package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings

actual fun fileDialogSettings(title: String): FileKitDialogSettings = FileKitDialogSettings.createDefault()
