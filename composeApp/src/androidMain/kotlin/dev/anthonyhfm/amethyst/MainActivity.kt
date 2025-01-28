package dev.anthonyhfm.amethyst

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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