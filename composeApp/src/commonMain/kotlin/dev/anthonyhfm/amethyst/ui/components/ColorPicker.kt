package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ColorPicker(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
    ) {
        Canvas(
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // draw linear gradient
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color.White,
                        Color.Red
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f)
                ),
                size = size
            )

            drawRect(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, size.height)
                ),
                size = size
            )
        }
    }
}

@Composable
fun HuePickerBar(
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    Box(
        modifier = modifier
            .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
            .then(
                other = if (vertical) {
                    Modifier.width(24.dp)
                } else {
                    Modifier.height(24.dp)
                }
            )
    ) {
        Canvas(
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // draw linear gradient
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = if (vertical) {
                        androidx.compose.ui.geometry.Offset(0f, size.height)
                    } else {
                        androidx.compose.ui.geometry.Offset(size.width, 0f)
                    }
                ),
                size = size
            )
        }
    }
}

@Composable
fun HexColorEditor(
    hex: String,
    onEditHex: (String) -> Unit
) {
    BasicTextField(
        value = hex.uppercase(),
        onValueChange = onEditHex,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 6.dp)
    )
}
