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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

data class SimpleDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

@Composable
fun DatePicker(
    selectedDate: SimpleDate?,
    onDateSelected: (SimpleDate) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pick a date",
    today: SimpleDate? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var displayedYear by remember {
        mutableStateOf(selectedDate?.year ?: today?.year ?: 2025)
    }
    var displayedMonth by remember {
        mutableStateOf(selectedDate?.month ?: today?.month ?: 1)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = DefaultShape

    UnstyledDropdownMenu(
        onExpandRequest = { expanded = true },
        modifier = modifier,
    ) {
        // Trigger button (outline variant style)
        UnstyledButton(
            onClick = { expanded = true },
            shape = shape,
            interactionSource = interactionSource,
            indication = null,
            contentPadding = PaddingValues(horizontal = 12.dp),
            borderColor = Theme[colors][input],
            borderWidth = 1.dp,
            modifier = Modifier
                .height(40.dp)
                .clip(shape)
                .background(if (hovered) Theme[colors][accent] else Theme[colors][background]),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (selectedDate == null) Theme[colors][mutedForeground] else Theme[colors][foreground],
                )
                Spacer(Modifier.width(8.dp))

                val displayText = if (selectedDate != null) {
                    formatDate(selectedDate)
                } else {
                    placeholder
                }
                val textColor = if (selectedDate == null) {
                    Theme[colors][mutedForeground]
                } else {
                    if (hovered) Theme[colors][accentForeground] else Theme[colors][foreground]
                }

                Text(
                    text = displayText,
                    style = Theme[typography][small],
                    color = textColor,
                )
            }
        }

        // Calendar popover panel
        UnstyledDropdownMenuPanel(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = shape,
            backgroundColor = Theme[colors][popover],
            contentColor = Theme[colors][popoverForeground],
            contentPadding = PaddingValues(0.dp),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
            modifier = Modifier
                .border(1.dp, Theme[colors][border], shape)
                .shadow(8.dp, shape),
        ) {
            DatePickerCalendar(
                displayedYear = displayedYear,
                displayedMonth = displayedMonth,
                selectedDate = selectedDate,
                today = today,
                onPreviousMonth = {
                    if (displayedMonth == 1) {
                        displayedMonth = 12
                        displayedYear -= 1
                    } else {
                        displayedMonth -= 1
                    }
                },
                onNextMonth = {
                    if (displayedMonth == 12) {
                        displayedMonth = 1
                        displayedYear += 1
                    } else {
                        displayedMonth += 1
                    }
                },
                onDateClick = { date ->
                    onDateSelected(date)
                    expanded = false
                },
            )
        }
    }
}

// -- Inline calendar grid --

@Composable
private fun DatePickerCalendar(
    displayedYear: Int,
    displayedMonth: Int,
    selectedDate: SimpleDate?,
    today: SimpleDate?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateClick: (SimpleDate) -> Unit,
) {
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val dayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Month navigation header
        DatePickerHeader(
            title = "${monthNames[displayedMonth - 1]} $displayedYear",
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
        )

        // Day-of-week labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (label in dayLabels) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = Theme[typography][small],
                        color = Theme[colors][mutedForeground],
                    )
                }
            }
        }

        // Day grid
        val daysInMonth = datePickerDaysInMonth(displayedYear, displayedMonth)
        val firstDayOfWeek = datePickerDayOfWeek(displayedYear, displayedMonth, 1)

        val cells = mutableListOf<SimpleDate?>()
        for (i in 0 until firstDayOfWeek) {
            cells.add(null)
        }
        for (day in 1..daysInMonth) {
            cells.add(SimpleDate(displayedYear, displayedMonth, day))
        }
        while (cells.size % 7 != 0) {
            cells.add(null)
        }

        val rows = cells.chunked(7)
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (cell in row) {
                    if (cell != null) {
                        val isSelected = cell == selectedDate
                        val isToday = today != null && cell == today

                        DatePickerDayCell(
                            day = cell.day,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = { onDateClick(cell) },
                        )
                    } else {
                        Box(modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePickerHeader(
    title: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DatePickerNavButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                modifier = Modifier.size(16.dp),
                tint = Theme[colors][foreground],
            )
        }

        ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][foreground])) {
            Text(text = title)
        }

        DatePickerNavButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                modifier = Modifier.size(16.dp),
                tint = Theme[colors][foreground],
            )
        }
    }
}

@Composable
private fun DatePickerNavButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    UnstyledButton(
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        modifier = Modifier
            .size(32.dp)
            .clip(DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .background(if (hovered) Theme[colors][accent] else Color.Transparent),
        content = {
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        },
    )
}

@Composable
private fun DatePickerDayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val bg = when {
        isSelected -> Theme[colors][primary]
        isToday -> Theme[colors][accent]
        hovered -> Theme[colors][accent]
        else -> Color.Transparent
    }

    val fg = when {
        isSelected -> Theme[colors][primaryForeground]
        isToday -> Theme[colors][accentForeground]
        hovered -> Theme[colors][accentForeground]
        else -> Theme[colors][foreground]
    }

    UnstyledButton(
        onClick = onClick,
        interactionSource = interactionSource,
        indication = null,
        modifier = Modifier
            .size(36.dp)
            .clip(SmallShape)
            .background(bg),
        content = {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.toString(),
                    style = Theme[typography][small],
                    color = fg,
                )
            }
        },
    )
}

// -- Date formatting --

private fun formatDate(date: SimpleDate): String {
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    return "${monthNames[date.month - 1]} ${date.day}, ${date.year}"
}

// -- Date utilities (self-contained, no external dependencies) --

private fun datePickerIsLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

private fun datePickerDaysInMonth(year: Int, month: Int): Int = when (month) {
    1 -> 31; 2 -> if (datePickerIsLeapYear(year)) 29 else 28; 3 -> 31
    4 -> 30; 5 -> 31; 6 -> 30; 7 -> 31; 8 -> 31
    9 -> 30; 10 -> 31; 11 -> 30; 12 -> 31
    else -> 30
}

/** Returns day-of-week for the given date: 0 = Monday … 6 = Sunday (ISO-8601). */
private fun datePickerDayOfWeek(year: Int, month: Int, day: Int): Int {
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    @Suppress("NAME_SHADOWING")
    var year = year
    if (month < 3) year -= 1
    val dow = (year + year / 4 - year / 100 + year / 400 + t[month - 1] + day) % 7
    return (dow + 6) % 7
}
