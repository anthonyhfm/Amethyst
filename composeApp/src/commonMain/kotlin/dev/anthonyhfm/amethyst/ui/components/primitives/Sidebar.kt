package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

// -- State --

class SidebarState(initialOpen: Boolean = true) {
    var open by mutableStateOf(initialOpen)

    val expanded: Boolean get() = open

    val collapsed: Boolean get() = !open

    fun toggle() {
        open = !open
    }
}

@Composable
fun rememberSidebarState(initialOpen: Boolean = true): SidebarState {
    return remember { SidebarState(initialOpen) }
}

val LocalSidebarState = compositionLocalOf<SidebarState> {
    error("SidebarState not provided. Wrap your content in SidebarProvider.")
}
val LocalSidebarIsAnimating = compositionLocalOf { false }

// -- Constants --

private val SidebarExpandedWidth: Dp = 256.dp
private val SidebarCollapsedWidth: Dp = 64.dp
private val SidebarExpandedItemHeight: Dp = 36.dp
private val SidebarCollapsedItemSize: Dp = 48.dp
private const val AnimationDurationMs = 200

// -- Provider --

@Composable
fun SidebarProvider(
    state: SidebarState = rememberSidebarState(),
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalSidebarState provides state) {
        Row(modifier = modifier, content = content)
    }
}

// -- Sidebar Container --

@Composable
fun Sidebar(
    modifier: Modifier = Modifier,
    state: SidebarState = LocalSidebarState.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    val targetWidth = if (state.open) SidebarExpandedWidth else SidebarCollapsedWidth
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = AnimationDurationMs),
    )
    val isAnimating = animatedWidth != targetWidth
    val borderColor = Theme[colors][border]

    CompositionLocalProvider(LocalSidebarIsAnimating provides isAnimating) {
        Column(
            modifier = modifier
                .width(animatedWidth)
                .fillMaxHeight()
                .background(Theme[colors][background])
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = borderColor,
                        start = Offset(size.width - strokeWidth / 2, 0f),
                        end = Offset(size.width - strokeWidth / 2, size.height),
                        strokeWidth = strokeWidth,
                    )
                },
        ) {
            content()
        }
    }
}

// -- Header / Footer --

@Composable
fun SidebarHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = LocalSidebarState.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (state.expanded) 12.dp else 8.dp, vertical = 8.dp),
        content = content,
    )
}

@Composable
fun SidebarFooter(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = LocalSidebarState.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (state.expanded) 12.dp else 8.dp, vertical = 8.dp),
        content = content,
    )
}

// -- Content (scrollable) --

@Composable
fun ColumnScope.SidebarContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(scrollState),
        content = content,
    )
}

// -- Group --

@Composable
fun SidebarGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = LocalSidebarState.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (state.expanded) 12.dp else 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun SidebarGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val state = LocalSidebarState.current

    if (state.expanded) {
        Text(
            text = text,
            style = Theme[typography][small],
            color = Theme[colors][mutedForeground],
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun SidebarGroupContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

// -- Menu --

@Composable
fun SidebarMenu(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@Composable
fun SidebarMenuItem(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun SidebarMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit = {},
) {
    val state = LocalSidebarState.current
    val isAnimating = LocalSidebarIsAnimating.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        isActive -> Theme[colors][accent]
        hovered && !isAnimating -> Theme[colors][accent]
        else -> Theme[colors][background]
    }
    val fgColor = when {
        isActive -> Theme[colors][accentForeground]
        else -> Theme[colors][foreground]
    }

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = if (state.expanded) {
            PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        } else {
            PaddingValues(6.dp)
        },
        modifier = modifier
            .fillMaxWidth()
            .height(
                if (state.expanded) {
                    SidebarExpandedItemHeight
                } else {
                    SidebarCollapsedItemSize
                }
            )
            .clip(SmallShape)
            .background(bgColor),
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = fgColor)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (state.expanded) {
                    Arrangement.spacedBy(8.dp)
                } else {
                    Arrangement.Center
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier.size(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        icon()
                    }
                }
                if (state.expanded) {
                    content()
                }
            }
        }
    }
}

// -- Separator --

@Composable
fun SidebarSeparator(
    modifier: Modifier = Modifier,
) {
    val state = LocalSidebarState.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (state.expanded) 12.dp else 8.dp, vertical = 4.dp)
            .height(1.dp)
            .background(Theme[colors][border]),
    )
}

// -- Trigger --

@Composable
fun SidebarTrigger(
    modifier: Modifier = Modifier,
    state: SidebarState = LocalSidebarState.current,
) {
    val isAnimating = LocalSidebarIsAnimating.current
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bgColor = if (hovered && !isAnimating) Theme[colors][accent] else Theme[colors][background]

    UnstyledButton(
        onClick = { state.toggle() },
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(8.dp),
        modifier = modifier
            .clip(SmallShape)
            .background(bgColor),
    ) {
        Icon(
            imageVector = if (state.expanded) {
                Icons.AutoMirrored.Filled.KeyboardArrowLeft
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            },
            contentDescription = if (state.expanded) "Collapse sidebar" else "Expand sidebar",
            tint = Theme[colors][foreground],
        )
    }
}
