package dev.anthonyhfm.amethyst.editor.ui.projectsettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Expand
import androidx.compose.material.icons.rounded.ExpandCircleDown
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.editor.ui.projectsettings.launchpadconfig.LaunchpadConfigSettings
import kotlin.math.exp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSettingsPanel() {
    var expanded: Boolean by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .padding(start = 12.dp, top = 12.dp, end = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .width(
                animateDpAsState(
                    targetValue = if (expanded) {
                        350.dp
                    } else {
                        56.dp
                    }
                ).value
            )
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(0.2.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp), RoundedCornerShape(12.dp)),
    ) {
        TopAppBar(
            title = {
                Text("Project")
            },
            colors = TopAppBarDefaults.topAppBarColors().copy(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(0.2.dp)
            ),
            navigationIcon = {
                IconButton(
                    onClick = {
                        expanded = !expanded
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ExpandCircleDown,
                        contentDescription = "Expand",
                        modifier = Modifier
                            .rotate(
                                degrees = animateFloatAsState(
                                    targetValue = if (expanded) {
                                        -90f
                                    } else {
                                        90f
                                    }
                                ).value
                            )
                    )
                }
            }
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = ExitTransition.None,
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
            ) {
                LaunchpadConfigSettings()
            }
        }
    }
}