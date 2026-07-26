package dev.anthonyhfm.amethyst.core.util

import io.github.vinceglb.filekit.PlatformFile

/**
 * Multiplatform helper for handling persistent mobile file storage and dynamic sandbox path resolution.
 */
expect object MobileFileStorage {
    fun resolvePath(path: String): PlatformFile

    suspend fun copyToPersistentStorage(file: PlatformFile): PlatformFile

    suspend fun copyBytesToPersistentStorage(bytes: ByteArray, filename: String): PlatformFile
}
