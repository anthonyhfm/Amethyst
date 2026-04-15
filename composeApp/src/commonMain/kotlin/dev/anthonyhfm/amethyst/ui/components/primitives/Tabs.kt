package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Tab
import com.composeunstyled.TabGroup
import com.composeunstyled.TabList
import com.composeunstyled.TabPanel
import com.composeunstyled.theme.NoIndication
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground

@Composable
fun Tabs(
    selectedTab: String,
    tabs: List<String>,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    TabGroup(
        selectedTab = selectedTab,
        tabs = tabs,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun TabsList(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    TabList(
        modifier = modifier,
        shape = DefaultShape,
        backgroundColor = Theme[colors][muted],
        contentColor = Theme[colors][mutedForeground],
        contentPadding = PaddingValues(4.dp),
        content = content,
    )
}

@Composable
fun TabsTrigger(
    key: String,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bg = if (selected) Theme[colors][background] else Color.Transparent
    val fg = if (selected) Theme[colors][foreground] else Theme[colors][mutedForeground]

    Tab(
        key = key,
        selected = selected,
        onSelected = onSelected,
        modifier = modifier,
        shape = SmallShape,
        backgroundColor = bg,
        contentColor = fg,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        indication = NoIndication,
        content = content,
    )
}

@Composable
fun TabsContent(
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    TabPanel(
        key = key,
        modifier = modifier,
        contentPadding = PaddingValues(top = 8.dp),
        content = content,
    )
}
