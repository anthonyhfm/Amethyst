package dev.anthonyhfm.amethyst.start.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowScope.ProjectsView(
    onClickCreateProject: () -> Unit,
    onClickOpenProject: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Recent Projects")
                },
                actions = {
                    IconButton(
                        onClick = {

                        }
                    ) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        onClickCreateProject()
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = "New Project",
                        style = MaterialTheme.typography.titleSmall,
                        lineHeight = MaterialTheme.typography.titleSmall.fontSize
                    )
                }

                Button(
                    onClick = {
                        onClickOpenProject()
                    },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = "Open Project",
                        style = MaterialTheme.typography.titleSmall,
                        lineHeight = MaterialTheme.typography.titleSmall.fontSize
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp),

            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 1 .. 3) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .fillMaxWidth()
                        .height(46.dp)
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
                        .padding(start = 12.dp),

                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Project Name",
                            style = MaterialTheme.typography.labelLarge,
                            lineHeight = MaterialTheme.typography.labelLarge.fontSize
                        )

                        Text(
                            text = "~/desktop/project.aspj",
                            style = MaterialTheme.typography.labelSmall,
                            lineHeight = MaterialTheme.typography.labelSmall.fontSize,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.6f)
                        )
                    }

                    IconButton(
                        onClick = {

                        }
                    ) {
                        Icon(Icons.Default.MoreVert, null)
                    }
                }
            }
        }
    }
}