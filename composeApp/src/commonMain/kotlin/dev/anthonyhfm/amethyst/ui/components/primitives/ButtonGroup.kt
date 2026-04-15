package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

enum class ButtonGroupOrientation {
    Horizontal,
    Vertical,
}

class ButtonGroupScope internal constructor() {
    internal sealed class Entry {
        class Item(val content: @Composable () -> Unit) : Entry()
        data object Separator : Entry()
    }

    internal val entries = mutableListOf<Entry>()

    fun item(content: @Composable () -> Unit) {
        entries.add(Entry.Item(content))
    }

    fun separator() {
        entries.add(Entry.Separator)
    }
}

/**
 * A container that groups related buttons together with connected styling.
 * First and last items receive rounded corners; middle items have flat edges.
 * The outer clip constrains inner children so existing Button shapes are visually overridden.
 */
@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    orientation: ButtonGroupOrientation = ButtonGroupOrientation.Horizontal,
    content: ButtonGroupScope.() -> Unit,
) {
    val scope = ButtonGroupScope()
    scope.content()

    val itemCount = scope.entries.count { it is ButtonGroupScope.Entry.Item }

    val renderContent: @Composable () -> Unit = {
        var itemIndex = 0
        scope.entries.forEach { entry ->
            when (entry) {
                is ButtonGroupScope.Entry.Item -> {
                    val shape = buttonGroupItemShape(itemIndex, itemCount, orientation)
                    Box(modifier = Modifier.clip(shape)) {
                        entry.content()
                    }
                    itemIndex++
                }
                is ButtonGroupScope.Entry.Separator -> {
                    ButtonGroupSeparator(orientation = orientation)
                }
            }
        }
    }

    when (orientation) {
        ButtonGroupOrientation.Horizontal -> {
            Row(
                modifier = modifier.height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                renderContent()
            }
        }
        ButtonGroupOrientation.Vertical -> {
            Column(
                modifier = modifier.width(IntrinsicSize.Min),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                renderContent()
            }
        }
    }
}

/**
 * A visual divider between buttons within a [ButtonGroup].
 * Renders perpendicular to the group orientation.
 */
@Composable
fun ButtonGroupSeparator(
    modifier: Modifier = Modifier,
    orientation: ButtonGroupOrientation = ButtonGroupOrientation.Horizontal,
) {
    val color = Theme[colors][border]
    when (orientation) {
        ButtonGroupOrientation.Horizontal -> {
            Box(modifier.fillMaxHeight().width(1.dp).background(color))
        }
        ButtonGroupOrientation.Vertical -> {
            Box(modifier.fillMaxWidth().height(1.dp).background(color))
        }
    }
}

/**
 * Styled text element for displaying labels within a [ButtonGroup].
 */
@Composable
fun ButtonGroupText(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][foreground])) {
            content()
        }
    }
}

private fun buttonGroupItemShape(
    index: Int,
    count: Int,
    orientation: ButtonGroupOrientation,
): RoundedCornerShape {
    val radius = 6.dp
    return when {
        count == 1 -> DefaultShape
        index == 0 && orientation == ButtonGroupOrientation.Horizontal ->
            RoundedCornerShape(topStart = radius, bottomStart = radius, topEnd = 0.dp, bottomEnd = 0.dp)
        index == 0 && orientation == ButtonGroupOrientation.Vertical ->
            RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 0.dp, bottomEnd = 0.dp)
        index == count - 1 && orientation == ButtonGroupOrientation.Horizontal ->
            RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = radius, bottomEnd = radius)
        index == count - 1 && orientation == ButtonGroupOrientation.Vertical ->
            RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = radius, bottomEnd = radius)
        else -> RoundedCornerShape(0.dp)
    }
}
