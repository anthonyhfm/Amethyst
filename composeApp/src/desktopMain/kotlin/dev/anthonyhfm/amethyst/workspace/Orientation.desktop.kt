package dev.anthonyhfm.amethyst.workspace

import androidx.compose.runtime.Composable

@Composable
actual fun ForceScreenOrientation(landscape: Boolean) {
    // No-op on desktop
}

@Composable
actual fun isMobilePhone(): Boolean {
    return false
}

actual fun triggerSettingsShow(onShowCommonDialog: () -> Unit) {
    onShowCommonDialog()
}
