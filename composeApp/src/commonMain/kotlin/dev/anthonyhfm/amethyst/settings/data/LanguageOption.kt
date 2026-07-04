package dev.anthonyhfm.amethyst.settings.data

data class LanguageOption(
    val languageTag: String,
    val displayName: String,
)

object LanguageOptions {
    val English = LanguageOption(
        languageTag = "en",
        displayName = "English",
    )

    val all: List<LanguageOption> = listOf(
        English,
    )

    fun fromLanguageTag(languageTag: String): LanguageOption =
        all.firstOrNull { it.languageTag == languageTag } ?: English
}
