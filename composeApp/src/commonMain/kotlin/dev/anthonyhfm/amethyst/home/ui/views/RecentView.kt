package dev.anthonyhfm.amethyst.home.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.workspace.data.RecentWorkspace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentView() {
    var recentProjects: List<RecentWorkspace> by remember { mutableStateOf(GlobalSettings.recentWorkspaces) }

    LaunchedEffect(recentProjects) {
        GlobalSettings.recentWorkspaces = recentProjects
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Recent Projects")
                }
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .padding(it)
        ) {
            itemsIndexed(recentProjects) { index, it ->
                ListItem(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .height(64.dp)
                        .clickable {

                        },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    ),
                    headlineContent = {
                        Text(
                            text = it.title,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                            lineHeight = MaterialTheme.typography.titleMedium.fontSize
                        )
                    },
                    supportingContent = {
                        Text(
                            text = it.path,
                            maxLines = 1,
                            overflow = TextOverflow.StartEllipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {

                                }
                            ) {
                                Icon(Icons.Default.Edit, null)
                            }

                            IconButton(
                                onClick = {

                                }
                            ) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }
                    }
                )
            }
        }
    }
}