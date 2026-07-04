package dev.anthonyhfm.amethyst.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults

actual object LocalAppLocale {
    private const val LanguageKey = "AppleLanguages"
    private const val default = "en"
    private val LocalAppLocale = staticCompositionLocalOf { default }

    actual val current: String
        @Composable get() = LocalAppLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val newLanguage = value ?: default
        if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LanguageKey)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(
                listOf(newLanguage),
                forKey = LanguageKey,
            )
        }
        NSUserDefaults.standardUserDefaults.synchronize()
        return LocalAppLocale.provides(newLanguage)
    }
}
