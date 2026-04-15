package dev.anthonyhfm.amethyst.gem

import okio.FileSystem
import okio.Path.Companion.toPath

actual object GemFixtureResourceLoader {
    actual fun load(resourcePath: String): String {
        val fallbackPaths = listOf(
            "composeApp/src/commonTest/resources/$resourcePath".toPath(),
            "src/commonTest/resources/$resourcePath".toPath()
        )
        val path = fallbackPaths.firstOrNull(FileSystem.SYSTEM::exists)
        requireNotNull(path) {
            "Missing gem fixture resource '$resourcePath'."
        }

        return FileSystem.SYSTEM.read(path) { readUtf8() }
    }
}
