package dev.anthonyhfm.amethyst.conversion.ableton.utils

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import java.io.File
import java.security.MessageDigest

actual fun PlatformFile.getFileHash(): String {
    val file = File(this.path)
    val digest = MessageDigest.getInstance("MD5")

    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
}

actual fun ByteArray.toFileHash(): String {
    TODO("Not yet implemented")
}