package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.ProvideTextStyle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.accent
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.foreground
import dev.anthonyhfm.amethyst.ui.theme.muted
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground
import dev.anthonyhfm.amethyst.ui.theme.p
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

// ---------------------------------------------------------------------------
// Private table layout helpers (internal to DataTable, avoids conflict with Table.kt)
// ---------------------------------------------------------------------------

@Composable
private fun DtTable(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun DtHeader(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun DtBody(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun DtRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun DtHead(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        ProvideTextStyle(
            Theme[typography][small].copy(color = Theme[colors][mutedForeground])
        ) {
            content()
        }
    }
}

@Composable
private fun DtCell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        ProvideTextStyle(Theme[typography][p].copy(color = Theme[colors][foreground])) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// DataTable column definition
// ---------------------------------------------------------------------------

data class DataTableColumn<T>(
    val header: String,
    val sortable: Boolean = false,
    val weight: Float = 1f,
    val cell: @Composable (T) -> Unit,
)

enum class SortDirection { Ascending, Descending }

data class SortState(
    val columnIndex: Int,
    val direction: SortDirection,
)

// ---------------------------------------------------------------------------
// DataTable composable
// ---------------------------------------------------------------------------

@Composable
fun <T> DataTable(
    data: List<T>,
    columns: List<DataTableColumn<T>>,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    selectedRows: List<Int> = emptyList(),
    onSelectedRowsChange: ((List<Int>) -> Unit)? = null,
    sortState: SortState? = null,
    onSortChange: ((SortState?) -> Unit)? = null,
    sortComparator: ((columnIndex: Int, direction: SortDirection, a: T, b: T) -> Int)? = null,
) {
    val borderColor = Theme[colors][border]
    val headerBg = Theme[colors][muted]
    val bodyBg = Theme[colors][background]
    val hoverBg = Theme[colors][accent]

    val displayData = remember(data, sortState, sortComparator) {
        if (sortState != null && sortComparator != null) {
            data.sortedWith(Comparator { a, b ->
                sortComparator(sortState.columnIndex, sortState.direction, a, b)
            })
        } else {
            data
        }
    }

    Box(
        modifier = modifier
            .clip(DefaultShape)
            .border(1.dp, borderColor, DefaultShape),
    ) {
        val scrollState = rememberScrollState()

        DtTable {
            // Header
            DtHeader(
                modifier = Modifier
                    .background(headerBg)
                    .border(width = 0.dp, color = Color.Transparent)
            ) {
                DtRow(
                    modifier = Modifier.borderBottom(borderColor),
                ) {
                    if (selectable) {
                        DtHead(modifier = Modifier.width(48.dp)) {
                            Checkbox(
                                checked = selectedRows.size == data.size && data.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    onSelectedRowsChange?.invoke(
                                        if (checked) data.indices.toList() else emptyList()
                                    )
                                },
                            )
                        }
                    }

                    columns.forEachIndexed { colIndex, column ->
                        DtHead(modifier = Modifier.weight(column.weight)) {
                            if (column.sortable && onSortChange != null) {
                                SortableHeaderCell(
                                    label = column.header,
                                    currentSort = sortState?.takeIf { it.columnIndex == colIndex },
                                    onToggle = {
                                        val newSort = when {
                                            sortState?.columnIndex != colIndex ->
                                                SortState(colIndex, SortDirection.Ascending)
                                            sortState.direction == SortDirection.Ascending ->
                                                SortState(colIndex, SortDirection.Descending)
                                            else -> null
                                        }
                                        onSortChange(newSort)
                                    },
                                )
                            } else {
                                Text(column.header)
                            }
                        }
                    }
                }
            }

            // Body
            DtBody(modifier = Modifier.verticalScroll(scrollState)) {
                if (displayData.isEmpty()) {
                    DtRow {
                        DtCell(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No results.",
                                    style = Theme[typography][small].copy(
                                        color = Theme[colors][mutedForeground]
                                    ),
                                )
                            }
                        }
                    }
                } else {
                    displayData.forEachIndexed { rowIndex, item ->
                        val originalIndex = data.indexOf(item)
                        val isSelected = originalIndex in selectedRows
                        val isLast = rowIndex == displayData.lastIndex

                        DataTableBodyRow(
                            item = item,
                            columns = columns,
                            rowIndex = originalIndex,
                            isSelected = isSelected,
                            isLast = isLast,
                            selectable = selectable,
                            bodyBg = bodyBg,
                            hoverBg = hoverBg,
                            borderColor = borderColor,
                            selectedRows = selectedRows,
                            onSelectedRowsChange = onSelectedRowsChange,
                        )
                    }
                }
            }
        }
    }

    // Selection footer
    if (selectable) {
        Box(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "${selectedRows.size} of ${data.size} row(s) selected.",
                style = Theme[typography][small].copy(color = Theme[colors][mutedForeground]),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Internal composables
// ---------------------------------------------------------------------------

@Composable
private fun <T> DataTableBodyRow(
    item: T,
    columns: List<DataTableColumn<T>>,
    rowIndex: Int,
    isSelected: Boolean,
    isLast: Boolean,
    selectable: Boolean,
    bodyBg: Color,
    hoverBg: Color,
    borderColor: Color,
    selectedRows: List<Int>,
    onSelectedRowsChange: ((List<Int>) -> Unit)?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val rowBg = when {
        isSelected -> hoverBg.copy(alpha = 0.5f)
        hovered -> hoverBg
        else -> bodyBg
    }

    DtRow(
        modifier = Modifier
            .background(rowBg)
            .hoverable(interactionSource)
            .then(if (!isLast) Modifier.borderBottom(borderColor) else Modifier),
    ) {
        if (selectable) {
            DtCell(modifier = Modifier.width(48.dp)) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                        val updated = if (checked) {
                            selectedRows + rowIndex
                        } else {
                            selectedRows - rowIndex
                        }
                        onSelectedRowsChange?.invoke(updated)
                    },
                )
            }
        }

        columns.forEach { column ->
            DtCell(modifier = Modifier.weight(column.weight)) {
                column.cell(item)
            }
        }
    }
}

@Composable
private fun SortableHeaderCell(
    label: String,
    currentSort: SortState?,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    UnstyledButton(
        onClick = onToggle,
        interactionSource = interactionSource,
        indication = null,
        modifier = Modifier.hoverable(interactionSource),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label)

            val icon = when (currentSort?.direction) {
                SortDirection.Ascending -> Icons.Default.KeyboardArrowUp
                SortDirection.Descending -> Icons.Default.KeyboardArrowDown
                null -> Icons.Default.UnfoldMore
            }

            Icon(
                imageVector = icon,
                contentDescription = when (currentSort?.direction) {
                    SortDirection.Ascending -> "Sorted ascending"
                    SortDirection.Descending -> "Sorted descending"
                    null -> "Sort"
                },
                modifier = Modifier
                    .height(16.dp)
                    .width(16.dp),
                tint = if (currentSort != null) {
                    Theme[colors][foreground]
                } else {
                    Theme[colors][mutedForeground]
                },
            )
        }
    }
}

private fun Modifier.borderBottom(color: Color): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1f,
        )
    }
)
