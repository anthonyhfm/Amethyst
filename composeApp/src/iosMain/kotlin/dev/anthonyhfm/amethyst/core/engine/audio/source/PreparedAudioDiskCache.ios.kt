package dev.anthonyhfm.amethyst.core.engine.audio.source

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.posix.SEEK_END
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.rename
import platform.posix.rewind

@OptIn(ExperimentalForeignApi::class)
internal actual object PreparedAudioDiskCache {
    actual fun read(root: String, key: String, expectedBytes: Int): ByteArray? {
        val file = fopen("$root/$key.pcm", "rb") ?: return null
        return try {
            if (fseek(file, 0, SEEK_END) != 0 || ftell(file) != expectedBytes.toLong()) return null
            rewind(file)
            val result = ByteArray(expectedBytes)
            result.usePinned { pinned ->
                var offset = 0
                while (offset < result.size) {
                    val size = minOf(1024 * 1024, result.size - offset)
                    val count = fread(pinned.addressOf(offset), 1uL, size.toULong(), file).toInt()
                    if (count <= 0) return null
                    offset += count
                }
            }
            result
        } finally {
            fclose(file)
        }
    }

    actual fun copyFile(root: String, key: String, sourcePath: String) {
        check(NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null))
        val staging = "$root/.${NSUUID().UUIDString}.part"
        try {
            check(NSFileManager.defaultManager.copyItemAtPath(sourcePath, staging, null))
            check(rename(staging, "$root/$key.pcm") == 0)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(staging, null)
        }
    }

    actual fun write(root: String, key: String, bytes: ByteArray) {
        check(NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null))
        val target = "$root/$key.pcm"
        val staging = "$root/.${NSUUID().UUIDString}.part"
        val file = fopen(staging, "wb") ?: error("Could not create prepared audio file")
        try {
            bytes.usePinned { pinned ->
                var offset = 0
                while (offset < bytes.size) {
                    val size = minOf(1024 * 1024, bytes.size - offset)
                    check(fwrite(pinned.addressOf(offset), 1uL, size.toULong(), file).toInt() == size)
                    offset += size
                }
            }
        } finally {
            fclose(file)
        }
        try {
            check(rename(staging, target) == 0)
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(staging, null)
        }
    }
}
