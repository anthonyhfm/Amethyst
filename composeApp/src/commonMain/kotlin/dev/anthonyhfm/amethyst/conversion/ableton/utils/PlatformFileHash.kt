package dev.anthonyhfm.amethyst.conversion.ableton.utils

import io.github.vinceglb.filekit.PlatformFile

expect fun PlatformFile.getFileHash(): String

expect fun ByteArray.toFileHash(): String