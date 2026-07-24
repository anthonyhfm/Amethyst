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

    val Chinese = LanguageOption(
        languageTag = "zh",
        displayName = "中文",
    )

    val German = LanguageOption(
        languageTag = "de",
        displayName = "Deutsch",
    )

    val all: List<LanguageOption> = listOf(
        English,
        Chinese,
        German,
    )

    fun fromLanguageTag(languageTag: String): LanguageOption =
        all.firstOrNull {
            it.languageTag.equals(languageTag, ignoreCase = true) ||
                (languageTag.startsWith("zh") && it.languageTag == "zh") ||
                (languageTag.startsWith("de") && it.languageTag == "de")
        } ?: English
}
