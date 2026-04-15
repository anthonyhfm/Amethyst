package dev.anthonyhfm.amethyst.gem

import java.io.File

actual object GemFixtureResourceLoader {
    actual fun load(resourcePath: String): String {
        GemFixtureResourceLoader::class.java.classLoader.getResource(resourcePath)?.let { resource ->
            return resource.readText()
        }

        val fallbackPaths = listOf(
            File("composeApp/src/commonTest/resources/$resourcePath"),
            File("src/commonTest/resources/$resourcePath")
        )
        val fallback = fallbackPaths.firstOrNull(File::exists)
        requireNotNull(fallback) {
            "Missing gem fixture resource '$resourcePath'."
        }
        return fallback.readText()
    }
}
