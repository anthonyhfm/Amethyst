package dev.anthonyhfm.amethyst.settings.data

object LanguageOptionCodec : SettingCodec<LanguageOption> {
    override fun encode(value: LanguageOption): String = value.languageTag

    override fun decode(raw: String): LanguageOption = LanguageOptions.fromLanguageTag(raw)
}
