package dev.anthonyhfm.amethyst.home

import androidx.compose.runtime.Composable

@Composable
expect fun Home(
    onOpenWorkspace: () -> Unit = { }
)
