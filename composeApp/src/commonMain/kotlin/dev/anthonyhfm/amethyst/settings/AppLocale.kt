package dev.anthonyhfm.amethyst.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import dev.anthonyhfm.amethyst.settings.data.GeneralSettings

@Composable
fun AppLocaleProvider(content: @Composable () -> Unit) {
    val language by GeneralSettings.language.flow.collectAsState()

    CompositionLocalProvider(
        LocalAppLocale provides language.languageTag,
    ) {
        content()
    }
}

@Composable
fun AppLocaleRefreshBoundary(content: @Composable () -> Unit) {
    val language by GeneralSettings.language.flow.collectAsState()

    key(language.languageTag) {
        content()
    }
}

expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}
