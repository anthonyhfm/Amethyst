package dev.anthonyhfm.amethyst.core.util

expect val platform: Platform

/**
 * Represents the platform on which the application is running.
 *
 * This API is only supposed to be used in a few cases. Most of the time, you should use the actual / expect functionality of Kotlin Multiplatform.
 */
sealed interface Platform {
    data object Android : Platform
    data object iOS : Platform

    sealed interface Desktop : Platform {
        data object Linux : Desktop
        data object Windows : Desktop
        data object MacOS : Desktop
    }
}