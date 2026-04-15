package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

private val LocalAvatarSize = compositionLocalOf { 40.dp }

/**
 * Slot-based Avatar container. Use with [AvatarFallback] and [AvatarImage] children.
 */
@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalAvatarSize provides size) {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Theme[colors][muted]),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * Convenience Avatar that renders a [painter] image or falls back to initials from [fallbackText].
 */
@Composable
fun Avatar(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    fallbackText: String = "",
    contentDescription: String? = null,
    size: Dp = 40.dp,
) {
    Avatar(modifier = modifier, size = size) {
        if (painter != null) {
            AvatarImage(painter = painter, contentDescription = contentDescription)
        } else {
            AvatarFallback(text = fallbackText)
        }
    }
}

@Composable
fun AvatarImage(
    painter: Painter,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val size = LocalAvatarSize.current

    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun AvatarFallback(
    text: String,
    modifier: Modifier = Modifier,
) {
    val size = LocalAvatarSize.current
    val initials = text
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    Text(
        text = initials,
        style = Theme[typography][small],
        color = Theme[colors][mutedForeground],
        fontWeight = FontWeight.SemiBold,
        fontSize = (size.value * 0.4f).sp,
        modifier = modifier,
    )
}
