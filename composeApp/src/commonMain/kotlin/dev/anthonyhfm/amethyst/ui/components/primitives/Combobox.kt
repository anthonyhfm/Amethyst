package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuPanel
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.accentForeground
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.input
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.popover
import dev.anthonyhfm.amethyst.ui.theme.popoverForeground
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

@Composable
fun <T> Combobox(
    items: List<T>,
    selectedItem: T?,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = "Select…",
    searchPlaceholder: String = "Search…",
    emptyMessage: String = "No items found.",
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = DefaultShape
    val density = LocalDensity.current
    var triggerWidth by remember { mutableStateOf(0.dp) }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { itemLabel(it).contains(searchQuery, ignoreCase = true) }
    }

    UnstyledDropdownMenu(
        onExpandRequest = { if (enabled) expanded = true },
        modifier = modifier,
    ) {
        // Trigger button
        UnstyledButton(
            onClick = { if (enabled) expanded = true },
            shape = shape,
            interactionSource = interactionSource,
            indication = null,
            contentPadding = PaddingValues(horizontal = 12.dp),
            borderColor = Theme[colors][input],
            borderWidth = 1.dp,
            modifier = Modifier
                .height(40.dp)
                .clip(shape)
                .background(Theme[colors][background])
                .onSizeChanged { triggerWidth = with(density) { it.width.toDp() } },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val displayText = if (selectedItem != null) itemLabel(selectedItem) else placeholder
                val textColor = if (selectedItem == null) Theme[colors][mutedForeground] else Theme[colors][foreground]

                Text(
                    text = displayText,
                    style = Theme[typography][small],
                    color = textColor,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Theme[colors][mutedForeground],
                )
            }
        }

        // Dropdown panel
        UnstyledDropdownMenuPanel(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                searchQuery = ""
            },
            shape = shape,
            backgroundColor = Theme[colors][popover],
            contentColor = Theme[colors][popoverForeground],
            contentPadding = PaddingValues(0.dp),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .then(if (triggerWidth > 0.dp) Modifier.width(triggerWidth) else Modifier)
                .border(1.dp, Theme[colors][border], shape)
                .shadow(8.dp, shape),
        ) {
            Column {
                ComboboxSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = searchPlaceholder,
                    expanded = expanded,
                )

                Separator(modifier = Modifier.fillMaxWidth())

                Column(
                    modifier = Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp),
                ) {
                    if (filteredItems.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        ) {
                            Text(
                                text = emptyMessage,
                                style = Theme[typography][small],
                                color = Theme[colors][mutedForeground],
                            )
                        }
                    } else {
                        filteredItems.forEach { item ->
                            ComboboxItem(
                                text = itemLabel(item),
                                selected = item == selectedItem,
                                onClick = {
                                    onItemSelected(item)
                                    expanded = false
                                    searchQuery = ""
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Combobox(
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    modifier: Modifier = Modifier,
    placeholder: String = "Select…",
    searchPlaceholder: String = "Search…",
    emptyMessage: String = "No items found.",
    enabled: Boolean = true,
) {
    Combobox(
        items = options,
        selectedItem = value.ifEmpty { null },
        onItemSelected = onValueChange,
        itemLabel = { it },
        modifier = modifier,
        placeholder = placeholder,
        searchPlaceholder = searchPlaceholder,
        emptyMessage = emptyMessage,
        enabled = enabled,
    )
}

@Composable
private fun ComboboxSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    expanded: Boolean,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Theme[colors][mutedForeground],
        )
        Spacer(Modifier.width(8.dp))

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .focusRequester(focusRequester),
            singleLine = true,
            textStyle = Theme[typography][small].copy(color = Theme[colors][foreground]),
            cursorBrush = SolidColor(Theme[colors][primary]),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = Theme[typography][small],
                            color = Theme[colors][mutedForeground],
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun ComboboxItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    UnstyledButton(
        onClick = onClick,
        shape = SmallShape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallShape)
            .then(
                if (hovered) Modifier.background(Theme[colors][accent])
                else Modifier
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) Theme[colors][foreground] else Theme[colors][popover],
            )
            Spacer(Modifier.width(8.dp))

            ProvideTextStyle(
                Theme[typography][small].copy(
                    color = if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]
                )
            ) {
                Text(text)
            }
        }
    }
}
