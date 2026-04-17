package dev.anthonyhfm.amethyst.workspace.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import amethyst.composeapp.generated.resources.Res

/**
 * Custom [ImageTransformer] that loads images from compose resources.
 *
 * Markdown images using the scheme `res://filename.png` are resolved from
 * `composeResources/files/images/filename.png`. All other URLs are ignored
 * (returns `null` so the renderer can fall back to its default behaviour).
 */
object ResourceImageTransformer : ImageTransformer {

    private const val RES_SCHEME = "res://"

    @Composable
    override fun transform(link: String): ImageData? {
        if (!link.startsWith(RES_SCHEME)) return null

        val fileName = link.removePrefix(RES_SCHEME)
        var bitmap by remember(fileName) { mutableStateOf<ImageBitmap?>(null) }

        LaunchedEffect(fileName) {
            try {
                val bytes = Res.readBytes("files/images/$fileName")
                bitmap = decodeImageBytes(bytes)
            } catch (e: Exception) {
                println("ResourceImageTransformer: failed to load '$fileName': ${e.message}")
            }
        }

        val currentBitmap = bitmap ?: return null

        return ImageData(
            painter = BitmapPainter(currentBitmap),
        )
    }
}

expect suspend fun decodeImageBytes(bytes: ByteArray): ImageBitmap
