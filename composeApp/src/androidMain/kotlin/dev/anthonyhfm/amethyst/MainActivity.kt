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
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ProcessLifecycleOwner
import dev.anthonyhfm.amethyst.core.engine.echo.AndroidAudioLifecycleObserver
import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.midi.AndroidMidiAccessProvider
import dev.anthonyhfm.amethyst.core.util.MobileFileStorage
import dev.anthonyhfm.amethyst.home.data.HomeRepository
import dev.anthonyhfm.amethyst.settings.AppLocaleProvider
import dev.anthonyhfm.amethyst.settings.data.AudioSettings
import dev.anthonyhfm.amethyst.ui.theme.ComposeAmethystTheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        initializeSentry()

        try {
            System.loadLibrary("c++_shared")
        } catch (_: UnsatisfiedLinkError) {
            // Ignored if c++_shared is statically linked or unavailable
        }

        FileKit.init(this)
        AndroidMidiAccessProvider.initialize(applicationContext)
        requestBluetoothMidiPermissionIfNeeded()

        Echo.setPreferredBufferFrames(AudioSettings.renderBufferFrames.value)
        ProcessLifecycleOwner.get().lifecycle.addObserver(AndroidAudioLifecycleObserver)

        handleFileIntent(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleFileIntent(intent)
    }

    private fun handleFileIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        val action = intent.action
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@runCatching
                    val bytes = inputStream.use { it.readBytes() }
                    val filename = resolveFileName(uri) ?: "imported_project.ame"
                    val persistentFile = MobileFileStorage.copyBytesToPersistentStorage(bytes, filename)
                    val workspace = HomeRepository.loadWorkspaceData(persistentFile)
                    HomeRepository.openWorkspace(workspace, rememberRecent = true)
                }
            }
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        }
        return uri.path?.substringAfterLast('/')
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
