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
import dev.anthonyhfm.amethyst.nativeengine.AndroidNativeContext
import dev.anthonyhfm.amethyst.settings.AppLocaleProvider
import dev.anthonyhfm.amethyst.settings.data.AudioSettings
import dev.anthonyhfm.amethyst.ui.theme.ComposeAmethystTheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashView = splashScreenViewProvider.view
            val iconView = splashScreenViewProvider.iconView

            val scaleX = android.animation.ObjectAnimator.ofFloat(iconView, android.view.View.SCALE_X, 1.0f, 1.75f)
            val scaleY = android.animation.ObjectAnimator.ofFloat(iconView, android.view.View.SCALE_Y, 1.0f, 1.75f)
            val fadeOut = android.animation.ObjectAnimator.ofFloat(splashView, android.view.View.ALPHA, 1.0f, 0.0f)

            val exitAnimatorSet = android.animation.AnimatorSet().apply {
                playTogether(scaleX, scaleY, fadeOut)
                duration = 500L
                interpolator = android.view.animation.DecelerateInterpolator(1.5f)
                startDelay = 100L
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        splashScreenViewProvider.remove()
                    }
                })
            }
            exitAnimatorSet.start()
        }

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        initializeSentry()

        check(AndroidNativeContext.initialize(applicationContext)) {
            "Cannot initialize the native Android context"
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
