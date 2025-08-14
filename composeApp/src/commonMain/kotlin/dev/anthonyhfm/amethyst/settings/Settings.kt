package dev.anthonyhfm.amethyst.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.ui.theme.AMETHYST_THEME

@Composable
fun Settings() {
    MaterialTheme(
        colorScheme = AMETHYST_THEME
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
        ) {

        }
    }
}