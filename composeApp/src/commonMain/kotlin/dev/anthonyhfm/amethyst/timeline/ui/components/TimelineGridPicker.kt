package dev.anthonyhfm.amethyst.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.timeline.utils.GridUtils
import io.androidpoet.dropdown.Dropdown
import io.androidpoet.dropdown.MenuItem
import io.androidpoet.dropdown.dropDownMenu
import io.androidpoet.dropdown.EnterAnimation
import io.androidpoet.dropdown.ExitAnimation
import io.androidpoet.dropdown.Easing

@Composable
fun TimelineGridPicker() {
    val current by WorkspaceRepository.gridType.collectAsState()
    var open by remember { mutableStateOf(false) }

    val menu = remember {
        buildGridMenu()
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(0.25f), CircleShape)
            .clickable { open = true }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.TwoTone.GridView, contentDescription = "Grid Type", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
    }

    Dropdown(
        isOpen = open,
        menu = menu,
        onItemSelected = { key ->
            open = false
            if (key != null) {
                menuToGridType(key)?.let { WorkspaceRepository.setGridType(it) }
            }
        },
        onDismiss = { open = false },
        enter = EnterAnimation.SharedAxisXForward,
        exit = ExitAnimation.SharedAxisXBackward,
        easing = Easing.FastOutSlowInEasing,
        enterDuration = 300,
        exitDuration = 300
    )
}

private fun buildGridMenu(): MenuItem<String> = dropDownMenu {
    item("flex", "Flexible") {
        item("flex_smallest", "Smallest") {}
        item("flex_small", "Small") {}
        item("flex_medium", "Medium") {}
        item("flex_large", "Large") {}
        item("flex_largest", "Largest") {}
    }
    item("fixed", "Fixed") {
        item("fixed_bar1", "1 Bar") {}
        item("fixed_bar2", "2 Bars") {}
        item("fixed_bar4", "4 Bars") {}
        item("fixed_bar8", "8 Bars") {}
        item("fixed_1_2", "1/2 Bar") {}
        item("fixed_1_4", "1/4 Bar") {}
        item("fixed_1_8", "1/8 Bar") {}
        item("fixed_1_16", "1/16 Bar") {}
        item("fixed_1_32", "1/32 Bar") {}
    }
}

private fun menuToGridType(key: String): GridUtils.GridType? = when (key) {
    "flex_smallest" -> GridUtils.GridType.Flexible.Smallest
    "flex_small" -> GridUtils.GridType.Flexible.Small
    "flex_medium" -> GridUtils.GridType.Flexible.Medium
    "flex_large" -> GridUtils.GridType.Flexible.Large
    "flex_largest" -> GridUtils.GridType.Flexible.Largest
    "fixed_bar1" -> GridUtils.GridType.Fixed.Bar_1
    "fixed_bar2" -> GridUtils.GridType.Fixed.Bar_2
    "fixed_bar4" -> GridUtils.GridType.Fixed.Bar_4
    "fixed_bar8" -> GridUtils.GridType.Fixed.Bar_8
    "fixed_1_2" -> GridUtils.GridType.Fixed._1_2
    "fixed_1_4" -> GridUtils.GridType.Fixed._1_4
    "fixed_1_8" -> GridUtils.GridType.Fixed._1_8
    "fixed_1_16" -> GridUtils.GridType.Fixed._1_16
    "fixed_1_32" -> GridUtils.GridType.Fixed._1_32
    else -> null
}