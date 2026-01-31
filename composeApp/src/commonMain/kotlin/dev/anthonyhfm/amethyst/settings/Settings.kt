package dev.anthonyhfm.amethyst.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import dev.anthonyhfm.amethyst.settings.ui.views.AudioSettingsView
import dev.anthonyhfm.amethyst.settings.ui.views.DiscordSettingsView
import dev.anthonyhfm.amethyst.settings.ui.views.ExperimentalSettingsView
import dev.anthonyhfm.amethyst.settings.ui.views.GeneralSettingsView
import dev.anthonyhfm.amethyst.ui.theme.AMETHYST_THEME

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    onBack: (() -> Unit)? = null
) {
    MaterialTheme(
        colorScheme = AMETHYST_THEME
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBackIosNew, null)
                            }
                        }
                    },
                    title = {
                        Text("Settings")
                    }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                    .verticalScroll(rememberScrollState())
            ) {
                GeneralSettingsView()

                AudioSettingsView()

                if (platform !is Platform.Desktop) {
                    DiscordSettingsView()
                }

                ExperimentalSettingsView()
            }
        }
    }
}