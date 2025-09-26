package dev.anthonyhfm.amethyst

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FileKit.init(this)

        context = this

        setContent {
            App()
        }
    }

    companion object {
        lateinit var context: Context
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}