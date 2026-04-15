package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.blockquote
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.h1
import dev.anthonyhfm.amethyst.ui.theme.h2
import dev.anthonyhfm.amethyst.ui.theme.h3
import dev.anthonyhfm.amethyst.ui.theme.h4
import dev.anthonyhfm.amethyst.ui.theme.inlineCode
import dev.anthonyhfm.amethyst.ui.theme.large
import dev.anthonyhfm.amethyst.ui.theme.lead
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.mutedText
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun TypographyH1(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][h1].copy(color = Theme[colors][foreground]),
        modifier = modifier,
    )
}

@Composable
fun TypographyH2(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][h2].copy(color = Theme[colors][foreground]),
        modifier = modifier,
    )
}

@Composable
fun TypographyH3(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][h3].copy(color = Theme[colors][foreground]),
        modifier = modifier,
    )
}

@Composable
fun TypographyH4(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][h4].copy(color = Theme[colors][foreground]),
        modifier = modifier,
    )
}

@Composable
fun TypographyP(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][p].copy(color = Theme[colors][foreground]),
        modifier = modifier,
    )
}

@Composable
fun TypographyBlockquote(text: String, modifier: Modifier = Modifier) {
    val borderColor = Theme[colors][border]

    Box(
        modifier = modifier
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = text,
            style = Theme[typography][blockquote].copy(color = Theme[colors][foreground]),
        )
    }
}

@Composable
fun TypographyLead(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][lead].copy(color = Theme[colors][mutedForeground]),
        modifier = modifier,
    )
}

@Composable
fun TypographyLarge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][large].copy(color = Theme[colors][foreground]),
        modifier = modifier,
    )
}

@Composable
fun TypographySmall(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][small].copy(color = Theme[colors][foreground]),
        modifier = modifier,
    )
}

@Composable
fun TypographyMuted(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][mutedText].copy(color = Theme[colors][mutedForeground]),
        modifier = modifier,
    )
}

@Composable
fun TypographyInlineCode(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = Theme[typography][inlineCode].copy(color = Theme[colors][foreground]),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Theme[colors][muted])
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
fun TypographyList(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.padding(start = 24.dp),
    ) {
        content()
    }
}

@Composable
fun TypographyListItem(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "•",
            style = Theme[typography][p].copy(color = Theme[colors][foreground]),
            modifier = Modifier.width(16.dp),
        )
        Text(
            text = text,
            style = Theme[typography][p].copy(color = Theme[colors][foreground]),
        )
    }
}
