package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography
import kotlinx.coroutines.delay

enum class ToastVariant {
    Default,
    Destructive,
}

private object ToastIdGenerator {
    private var counter = 0L
    fun next(): Long = ++counter
}

data class ToastData(
    val id: Long = ToastIdGenerator.next(),
    val title: String,
    val description: String? = null,
    val variant: ToastVariant = ToastVariant.Default,
    val durationMs: Long = 5000L,
    val action: (@Composable () -> Unit)? = null,
)

@Stable
class ToastState {
    internal val toasts = mutableStateListOf<ToastData>()

    fun show(
        title: String,
        description: String? = null,
        variant: ToastVariant = ToastVariant.Default,
        durationMs: Long = 5000L,
        action: (@Composable () -> Unit)? = null,
    ) {
        toasts.add(
            ToastData(
                title = title,
                description = description,
                variant = variant,
                durationMs = durationMs,
                action = action,
            )
        )
    }

    internal fun dismiss(id: Long) {
        toasts.removeAll { it.id == id }
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }

@Composable
fun ToastProvider(
    state: ToastState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        content()

        Column(
            modifier = modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.toasts.forEach { toast ->
                ToastItem(
                    data = toast,
                    onDismiss = { state.dismiss(toast.id) },
                )
            }
        }
    }
}

@Composable
private fun ToastItem(
    data: ToastData,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var dismissRequested by remember { mutableStateOf(false) }

    LaunchedEffect(data.id) {
        visible = true
        delay(data.durationMs)
        if (!dismissRequested) {
            visible = false
            delay(300)
            onDismiss()
        }
    }

    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            visible = false
            delay(300)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        val bg = when (data.variant) {
            ToastVariant.Default -> Theme[colors][background]
            ToastVariant.Destructive -> Theme[colors][destructive]
        }
        val fg = when (data.variant) {
            ToastVariant.Default -> Theme[colors][foreground]
            ToastVariant.Destructive -> Theme[colors][destructiveForeground]
        }

        Row(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(DefaultShape)
                .border(1.dp, Theme[colors][border], DefaultShape)
                .background(bg)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ProvideTextStyle(Theme[typography][p].copy(color = fg)) {
                    Text(data.title)
                }
                if (data.description != null) {
                    Text(
                        text = data.description,
                        style = Theme[typography][small],
                        color = when (data.variant) {
                            ToastVariant.Default -> Theme[colors][mutedForeground]
                            ToastVariant.Destructive -> fg.copy(alpha = 0.9f)
                        },
                    )
                }
                if (data.action != null) {
                    Box(modifier = Modifier.padding(top = 4.dp)) {
                        data.action.invoke()
                    }
                }
            }
            Button(
                onClick = { dismissRequested = true },
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Icon,
            ) {
                Text("\u2715")
            }
        }
    }
}

@Composable
fun ToastAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        variant = ButtonVariant.Outline,
        size = ButtonSize.Small,
    ) {
        content()
    }
}
