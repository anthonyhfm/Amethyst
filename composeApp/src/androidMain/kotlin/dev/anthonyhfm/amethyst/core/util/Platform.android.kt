package dev.anthonyhfm.amethyst.core.util

actual val platform: Platform
    get() = Platform.Android

actual val isDevMode: Boolean
    get() = false