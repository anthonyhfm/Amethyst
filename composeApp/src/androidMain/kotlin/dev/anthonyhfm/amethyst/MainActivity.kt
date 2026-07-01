package dev.anthonyhfm.amethyst

import android.graphics.Color
import android.os.Bundle
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import dev.anthonyhfm.amethyst.core.midi.platformMidiAccess
import dev.anthonyhfm.amethyst.ui.theme.ComposeAmethystTheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        initializeSentry()

        FileKit.init(this)

        setContent {
            val darkMode = true

            ApplySystemBarStyle(
                window = window,
                darkMode = darkMode,
            )

            ComposeAmethystTheme(
                darkMode = darkMode,
            ) {
                App()
            }
        }
    }
}

@Composable
private fun ApplySystemBarStyle(
    window: Window,
    darkMode: Boolean,
) {
    SideEffect {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkMode
            isAppearanceLightNavigationBars = !darkMode
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    ComposeAmethystTheme {
        App()
    }
}
