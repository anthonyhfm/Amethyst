package dev.anthonyhfm.amethyst

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.core.koin.amethystKoinModule
import dev.anthonyhfm.amethyst.workspace.Workspace
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication

@Composable
@Preview
fun App() {
    KoinApplication(
        application = {
            modules(amethystKoinModule)
        }
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Workspace()
            }
        }
    }
}