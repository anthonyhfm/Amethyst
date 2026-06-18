package dev.anthonyhfm.amethyst.workspace

import androidx.compose.runtime.Composable

@Composable
expect fun ForceScreenOrientation(landscape: Boolean)

@Composable
expect fun isMobilePhone(): Boolean

expect fun triggerSettingsShow(onShowCommonDialog: () -> Unit)
