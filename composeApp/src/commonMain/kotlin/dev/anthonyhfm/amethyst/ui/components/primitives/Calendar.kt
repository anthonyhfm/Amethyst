package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.anthonyhfm.amethyst.ui.theme.primary
import dev.anthonyhfm.amethyst.ui.theme.primaryForeground
import dev.anthonyhfm.amethyst.ui.theme.small
import dev.anthonyhfm.amethyst.ui.theme.typography

data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int,
)

class CalendarState(
    initialSelectedDate: CalendarDate? = null,
    initialDisplayedYear: Int,
    initialDisplayedMonth: Int,
) {
    var selectedDate: CalendarDate? by mutableStateOf(initialSelectedDate)
    var displayedYear: Int by mutableStateOf(initialDisplayedYear)
    var displayedMonth: Int by mutableStateOf(initialDisplayedMonth)

    fun goToPreviousMonth() {
        if (displayedMonth == 1) {
            displayedMonth = 12
            displayedYear -= 1
        } else {
            displayedMonth -= 1
        }
    }

    fun goToNextMonth() {
        if (displayedMonth == 12) {
            displayedMonth = 1
            displayedYear += 1
        } else {
            displayedMonth += 1
        }
    }
}

@Composable
fun rememberCalendarState(
    initialSelectedDate: CalendarDate? = null,
    initialDisplayedYear: Int? = null,
    initialDisplayedMonth: Int? = null,
    today: CalendarDate? = null,
): CalendarState {
    return remember {
        val year = initialDisplayedYear ?: initialSelectedDate?.year ?: today?.year ?: 2025
        val month = initialDisplayedMonth ?: initialSelectedDate?.month ?: today?.month ?: 1
        CalendarState(
            initialSelectedDate = initialSelectedDate,
            initialDisplayedYear = year,
            initialDisplayedMonth = month,
        )
    }
}

@Composable
fun Calendar(
    state: CalendarState,
    modifier: Modifier = Modifier,
    today: CalendarDate? = null,
    onDateSelected: ((CalendarDate) -> Unit)? = null,
) {
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val dayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    Column(
        modifier = modifier
            .clip(DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .background(Theme[colors][background])
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Month navigation header
        CalendarHeader(
            title = "${monthNames[state.displayedMonth - 1]} ${state.displayedYear}",
            onPreviousMonth = { state.goToPreviousMonth() },
            onNextMonth = { state.goToNextMonth() },
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
        val daysInMonth = daysInMonth(state.displayedYear, state.displayedMonth)
        val firstDayOfWeek = dayOfWeek(state.displayedYear, state.displayedMonth, 1)

        val cells = mutableListOf<CalendarDate?>()
        // Leading empty cells (Monday = 0)
        for (i in 0 until firstDayOfWeek) {
            cells.add(null)
        }
        for (day in 1..daysInMonth) {
            cells.add(CalendarDate(state.displayedYear, state.displayedMonth, day))
        }
        // Trailing empty cells
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
                        val isSelected = cell == state.selectedDate
                        val isToday = today != null && cell == today

                        CalendarDayCell(
                            day = cell.day,
                            isSelected = isSelected,
                            isToday = isToday,
                            onClick = {
                                state.selectedDate = cell
                                onDateSelected?.invoke(cell)
                            },
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
private fun CalendarHeader(
    title: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarNavButton(
            onClick = onPreviousMonth,
            content = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                    modifier = Modifier.size(16.dp),
                    tint = Theme[colors][foreground],
                )
            }
        )

        ProvideTextStyle(Theme[typography][small].copy(color = Theme[colors][foreground])) {
            Text(text = title)
        }

        CalendarNavButton(
            onClick = onNextMonth,
            content = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next month",
                    modifier = Modifier.size(16.dp),
                    tint = Theme[colors][foreground],
                )
            }
        )
    }
}

@Composable
private fun CalendarNavButton(
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
        }
    )
}

@Composable
private fun CalendarDayCell(
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
        }
    )
}

// -- Date utilities (no external dependencies) --

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1 -> 31; 2 -> if (isLeapYear(year)) 29 else 28; 3 -> 31
    4 -> 30; 5 -> 31; 6 -> 30; 7 -> 31; 8 -> 31
    9 -> 30; 10 -> 31; 11 -> 30; 12 -> 31
    else -> 30
}

/** Returns day-of-week for the given date: 0 = Monday … 6 = Sunday (ISO-8601). */
private fun dayOfWeek(year: Int, month: Int, day: Int): Int {
    // Tomohiko Sakamoto's algorithm (returns 0 = Sunday … 6 = Saturday)
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    @Suppress("NAME_SHADOWING")
    var year = year
    if (month < 3) year -= 1
    val dow = (year + year / 4 - year / 100 + year / 400 + t[month - 1] + day) % 7
    // Convert: Sunday(0)->6, Monday(1)->0, …, Saturday(6)->5
    return (dow + 6) % 7
}
