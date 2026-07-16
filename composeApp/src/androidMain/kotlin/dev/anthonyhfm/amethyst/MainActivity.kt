package dev.anthonyhfm.amethyst

import android.graphics.Color
import android.Manifest
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import dev.anthonyhfm.amethyst.core.midi.AndroidMidiAccessProvider
import dev.anthonyhfm.amethyst.settings.AppLocaleProvider
import dev.anthonyhfm.amethyst.ui.theme.ComposeAmethystTheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        initializeSentry()

        FileKit.init(this)
        AndroidMidiAccessProvider.initialize(applicationContext)
        requestBluetoothMidiPermissionIfNeeded()

        setContent {
            val darkMode = true

            ApplySystemBarStyle(
                window = window,
                darkMode = darkMode,
            )

            ComposeAmethystTheme(
                darkMode = darkMode,
            ) {
                AppLocaleProvider {
                    App()
                }
            }
        }
    }

    private fun requestBluetoothMidiPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), BLUETOOTH_MIDI_PERMISSION_REQUEST)
        }
    }

    private companion object {
        const val BLUETOOTH_MIDI_PERMISSION_REQUEST = 4101
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
