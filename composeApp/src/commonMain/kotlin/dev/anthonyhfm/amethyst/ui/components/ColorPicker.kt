package dev.anthonyhfm.amethyst.ui.components

import dev.anthonyhfm.amethyst.ui.modifier.trackInputFocus
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val HUE_PICKER_MAX = 359.999f

@Suppress("unused")
class ColorPickerState internal constructor(initialColor: Color) {
    private var _hue by mutableStateOf(0f)
    val hue: Float get() = _hue

    private var _saturation by mutableStateOf(1f)
    val saturation: Float get() = _saturation

    private var _value by mutableStateOf(1f)
    val value: Float get() = _value

    private var _color by mutableStateOf(initialColor)
    val color: Color get() = _color

    init { setColor(initialColor) }

    val hex: String
        get() = buildString {
            append("#")
            val r = (color.red * 255).roundToInt().coerceIn(0, 255)
            val g = (color.green * 255).roundToInt().coerceIn(0, 255)
            val b = (color.blue * 255).roundToInt().coerceIn(0, 255)
            append(r.toString(16).padStart(2, '0').uppercase())
            append(g.toString(16).padStart(2, '0').uppercase())
            append(b.toString(16).padStart(2, '0').uppercase())
        }

    val hueColor: Color get() = hsvToColor(_hue, 1f, 1f)

    fun setHue(newHue: Float) {
        _hue = when {
            newHue < 0f -> 0f
            newHue >= 360f -> HUE_PICKER_MAX
            else -> newHue
        }
        updateColorFromHsv()
    }

    fun setSaturationAndValue(s: Float, v: Float) {
        _saturation = s.coerceIn(0f, 1f)
        _value = v.coerceIn(0f, 1f)
        updateColorFromHsv()
    }

    fun setColor(newColor: Color) {
        val (h, s, v) = newColor.toHsv()
        _hue = h
        _saturation = s
        _value = v
        _color = newColor
    }

    fun setHex(hexString: String) {
        val cleaned = hexString.removePrefix("#").trim()
        if (cleaned.length == 6 && cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            runCatching {
                val r = cleaned.substring(0, 2).toInt(16)
                val g = cleaned.substring(2, 4).toInt(16)
                val b = cleaned.substring(4, 6).toInt(16)
                setColor(Color(r / 255f, g / 255f, b / 255f))
            }
        }
    }

    private fun updateColorFromHsv() {
        _color = hsvToColor(_hue, _saturation, _value)
    }
}

@Composable
fun rememberColorPickerState(initialColor: Color = Color.Red): ColorPickerState = remember(initialColor) {
    ColorPickerState(initialColor)
}

@Composable
fun ColorPicker(
    modifier: Modifier = Modifier,
    state: ColorPickerState,
    onSelectionStart: () -> Unit = {},
    onSelectionFinish: (Color) -> Unit = {},
) {
    val currentState by rememberUpdatedState(state)
    val paddingDp = 4.dp

    fun updateFromPosition(x: Float, y: Float, width: Int, height: Int) {
        if (width <= 0f || height <= 0f) return
        val s = (x / width).coerceIn(0f, 1f)
        val v = (1f - y / height).coerceIn(0f, 1f)
        currentState.setSaturationAndValue(s, v)
    }

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = -10.dp + state.saturation * (this.maxWidth - paddingDp),
                    y = -10.dp + (1f - state.value) * (this.maxHeight - paddingDp)
                )
                .zIndex(1f)
                .padding(2.dp)
                .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                .padding(2.dp)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .size(18.dp)
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = 4.dp,
                        spread = 2.dp,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                )
                .clip(CircleShape)
                .background(state.color)
        )

        Canvas(
            modifier = Modifier
                .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
                .padding(paddingDp)
                .clip(RoundedCornerShape(4.dp))
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { // Drag
                    detectDragGestures(
                        onDragStart = { offset ->
                            onSelectionStart()
                            updateFromPosition(offset.x, offset.y, size.width, size.height)
                        },
                        onDrag = { change, _ ->
                            updateFromPosition(change.position.x, change.position.y, size.width, size.height)
                        },
                        onDragEnd = {
                            onSelectionFinish(state.color)
                        }
                    )
                }
                .pointerInput(Unit) { // Tap
                    detectTapGestures { offset ->
                        updateFromPosition(offset.x, offset.y, size.width, size.height)
                    }
                }
        ) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.White, state.hueColor),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f)
                ),
                size = size
            )
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    start = Offset.Zero,
                    end = Offset(0f, size.height)
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
    state: ColorPickerState,
    onSelectionStart: () -> Unit = {},
    onSelectionFinish: (Color) -> Unit = {},
) {
    var componentSize by remember { mutableStateOf(IntSize.Zero) }
    val currentState by rememberUpdatedState(state)

    fun updateHueFrom(pos: Offset, width: Int, height: Int) {
        if (width <= 0f || height <= 0f) return
        val ratio = if (vertical) (pos.y / height).coerceIn(0f, 1f) else (pos.x / width).coerceIn(0f, 1f)
        currentState.setHue((ratio * 360f).coerceIn(0f, HUE_PICKER_MAX))
    }

    BoxWithConstraints(
        modifier = modifier
            .onSizeChanged { componentSize = it }
    ) {
        Box(
            modifier = Modifier
                .offset(
                    x = if (vertical) 0.dp else (state.hue / 360f) * (this.maxWidth - 12.dp),
                    y = if (vertical) (state.hue / 360f) * (this.maxHeight - 12.dp) else 0.dp
                )
                .zIndex(1f)
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = 0.dp,
                        spread = 2.dp,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                )
                .clip(CircleShape)
                .background(state.hueColor)
                .size(
                    width = if (vertical) 24.dp else 12.dp,
                    height = if (vertical) 12.dp else 24.dp
                )
                .border(2.dp, Color.Black, CircleShape)
                .padding(2.dp)
        )

        Canvas(
            modifier = Modifier
                .border(2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
                .then(if (vertical) Modifier.width(24.dp) else Modifier.height(24.dp))
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(vertical) { // Drag
                    detectDragGestures(
                        onDragStart = { offset ->
                            onSelectionStart()
                            updateHueFrom(offset, size.width, size.height)
                        },
                        onDrag = { change, _ -> updateHueFrom(change.position, size.width, size.height) },
                        onDragEnd = {
                            onSelectionFinish(state.color)
                        }
                    )
                }
                .pointerInput(vertical) { // Tap
                    detectTapGestures { offset ->
                        onSelectionStart()
                        updateHueFrom(offset, size.width, size.height)
                        onSelectionFinish(state.color)
                    }
                }
        ) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Red, Color.Yellow, Color.Green,
                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                    ),
                    start = Offset(0f, 0f),
                    end = if (vertical) Offset(0f, size.height) else Offset(size.width, 0f)
                ),
                size = Size(componentSize.width.toFloat(), componentSize.height.toFloat())
            )
        }
    }
}

@Composable
fun HexColorEditor(
    state: ColorPickerState,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(state.hex.uppercase())) }
    var lastColor by remember { mutableStateOf(state.color) }

    if (state.color != lastColor) {
        val newHex = state.hex.uppercase()
        if (textFieldValue.text != newHex) {
            textFieldValue = textFieldValue.copy(text = newHex)
        }
        lastColor = state.color
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            // Keep prefix "#" if possible, limit length to 9 to prevent long pastes
            var text = newValue.text
            if (!text.startsWith("#")) {
                text = "#" + text.replace("#", "")
            }
            text = text.uppercase().take(9)
            
            textFieldValue = newValue.copy(text = text)
            state.setHex(text)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        ),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 6.dp)
            .trackInputFocus()
    )
}

private fun Color.toHsv(): Triple<Float, Float, Float> {
    val r = red; val g = green; val b = blue
    val max = max(r, max(g, b)); val min = min(r, min(g, b)); val delta = max - min
    var h = 0f; val s: Float; val v = max
    s = if (max == 0f) 0f else delta / max
    if (delta != 0f) {
        h = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        } * 60f
        if (h < 0f) h += 360f
    }
    return Triple(h, s, v)
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1 - abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r1 + m, g1 + m, b1 + m)
}
