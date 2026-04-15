package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.destructive
import dev.anthonyhfm.amethyst.ui.theme.destructiveForeground
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlinx.coroutines.delay

enum class SonnerToastType {
    Success,
    Error,
    Info,
    Warning,
}

data class SonnerToastData(
    val id: Long,
    val type: SonnerToastType,
    val title: String,
    val description: String? = null,
    val durationMs: Long = 4000L,
)

@Stable
class SonnerState {
    internal val toasts = mutableStateListOf<SonnerToastData>()
    private var nextId = 0L

    fun success(title: String, description: String? = null, durationMs: Long = 4000L) {
        show(SonnerToastType.Success, title, description, durationMs)
    }

    fun error(title: String, description: String? = null, durationMs: Long = 4000L) {
        show(SonnerToastType.Error, title, description, durationMs)
    }

    fun info(title: String, description: String? = null, durationMs: Long = 4000L) {
        show(SonnerToastType.Info, title, description, durationMs)
    }

    fun warning(title: String, description: String? = null, durationMs: Long = 4000L) {
        show(SonnerToastType.Warning, title, description, durationMs)
    }

    private fun show(
        type: SonnerToastType,
        title: String,
        description: String?,
        durationMs: Long,
    ) {
        toasts.add(
            SonnerToastData(
                id = nextId++,
                type = type,
                title = title,
                description = description,
                durationMs = durationMs,
            )
        )
    }

    fun dismiss(id: Long) {
        toasts.removeAll { it.id == id }
    }
}

@Composable
fun rememberSonnerState(): SonnerState = remember { SonnerState() }

private const val MAX_VISIBLE_TOASTS = 3
private val TOAST_STACK_OFFSET = 8.dp
private val TOAST_MAX_WIDTH = 356.dp

/**
 * Container that renders stacked toasts at the bottom of the screen.
 * Place at the root of your layout so it overlays all content.
 */
@Composable
fun Toaster(
    state: SonnerState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .widthIn(max = TOAST_MAX_WIDTH),
        ) {
            val visibleToasts = state.toasts.takeLast(MAX_VISIBLE_TOASTS)

            visibleToasts.forEachIndexed { index, toast ->
                val stackIndex = visibleToasts.size - 1 - index
                val yOffset = -(stackIndex * TOAST_STACK_OFFSET.value).dp
                val scale = 1f - (stackIndex * 0.05f)

                SonnerToastItem(
                    toast = toast,
                    modifier = Modifier
                        .zIndex((visibleToasts.size - stackIndex).toFloat())
                        .offset { IntOffset(0, yOffset.roundToPx()) }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    onDismiss = { state.dismiss(toast.id) },
                )
            }
        }
    }
}

@Composable
private fun SonnerToastItem(
    toast: SonnerToastData,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(toast.id) {
        visible = true
        delay(toast.durationMs)
        visible = false
        delay(300)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300)),
        modifier = modifier,
    ) {
        SonnerToast(
            toast = toast,
            onClose = {
                visible = false
            },
        )
    }
}

@Composable
fun SonnerToast(
    toast: SonnerToastData,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val bg: Color
    val fg: Color
    val iconTint: Color
    val borderColor: Color

    when (toast.type) {
        SonnerToastType.Success -> {
            bg = Theme[colors][background]
            fg = Theme[colors][foreground]
            iconTint = Theme[colors][primary]
            borderColor = Theme[colors][border]
        }
        SonnerToastType.Error -> {
            bg = Theme[colors][destructive]
            fg = Theme[colors][destructiveForeground]
            iconTint = Theme[colors][destructiveForeground]
            borderColor = Theme[colors][destructive]
        }
        SonnerToastType.Info -> {
            bg = Theme[colors][background]
            fg = Theme[colors][foreground]
            iconTint = Theme[colors][mutedForeground]
            borderColor = Theme[colors][border]
        }
        SonnerToastType.Warning -> {
            bg = Theme[colors][background]
            fg = Theme[colors][foreground]
            iconTint = Color(0xFFF59E0B)
            borderColor = Theme[colors][border]
        }
    }

    val icon = when (toast.type) {
        SonnerToastType.Success -> Icons.Default.Check
        SonnerToastType.Error -> Icons.Default.Close
        SonnerToastType.Info -> Icons.Default.Info
        SonnerToastType.Warning -> Icons.Default.Warning
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, DefaultShape)
            .clip(DefaultShape)
            .border(1.dp, borderColor, DefaultShape)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = iconTint,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ProvideTextStyle(Theme[typography][small].copy(color = fg)) {
                Text(
                    text = toast.title,
                    style = Theme[typography][p].copy(
                        color = fg,
                        fontSize = Theme[typography][small].fontSize,
                        lineHeight = Theme[typography][small].lineHeight,
                    ),
                )

                if (toast.description != null) {
                    Text(
                        text = toast.description,
                        style = Theme[typography][small].copy(
                            color = if (toast.type == SonnerToastType.Error) {
                                fg.copy(alpha = 0.9f)
                            } else {
                                Theme[colors][mutedForeground]
                            },
                        ),
                    )
                }
            }
        }
    }
}
