package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile

/**
 * # File Helper
 *
 * The filesystem on iOS and Android is a little bit tricky with permissions and access to files.
 *
 * This object contains indexed files so the data is read once and then cached for future access.
 *
 * Make sure to clear the cache as soon as the files are not needed anymore to reduce memory usage.
 */
object FileHelper {
    val indexedFiles = mutableMapOf<String, PlatformFile>()

    fun clearCache() {
        indexedFiles.clear()
    }
}