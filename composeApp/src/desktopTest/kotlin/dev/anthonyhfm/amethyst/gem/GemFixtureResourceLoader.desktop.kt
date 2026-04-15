package dev.anthonyhfm.amethyst.gem

actual object GemFixtureResourceLoader {
    actual fun load(resourcePath: String): String = requireNotNull(
        GemFixtureResourceLoader::class.java.classLoader.getResource(resourcePath)
    ) {
        "Missing gem fixture resource '$resourcePath'."
    }.readText()
}
