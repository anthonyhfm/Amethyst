package dev.anthonyhfm.amethyst.home.ui.views

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.composeunstyled.Icon
import com.composeunstyled.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Lucide
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.home.data.Tutorial
import dev.anthonyhfm.amethyst.home.data.TutorialsRepository
import dev.anthonyhfm.amethyst.ui.components.primitives.*
import dev.anthonyhfm.amethyst.ui.theme.*

@Composable
fun TutorialsView() {
    var loadedTutorials by remember { mutableStateOf<List<Tutorial>>(emptyList()) }
    var selectedTutorial by remember { mutableStateOf<Tutorial?>(null) }
    var isWindowOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadedTutorials = TutorialsRepository.getTutorials()
    }

    if (selectedTutorial != null && isWindowOpen) {
        TutorialWindow(
            tutorial = selectedTutorial!!,
            onClose = { isWindowOpen = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, top = 24.dp, end = 12.dp, bottom = 24.dp),
        ) {
            ScrollArea(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TypographyH2(stringResource(Res.string.home_tutorials_title))
                        TypographyLead(stringResource(Res.string.home_tutorials_subtitle))
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (loadedTutorials.isEmpty()) {
                        Text(stringResource(Res.string.home_tutorials_loading), color = Theme[colors][mutedForeground])
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            loadedTutorials.forEach { tutorial ->
                                TutorialItem(
                                    tutorial = tutorial,
                                    onClick = {
                                        selectedTutorial = tutorial
                                        isWindowOpen = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialItem(
    tutorial: Tutorial,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, DefaultShape)
            .clip(DefaultShape)
            .border(1.dp, Theme[colors][border], DefaultShape)
            .background(Theme[colors][card])
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Lucide.BookOpen,
            contentDescription = null,
            tint = Theme[colors][mutedForeground],
            modifier = Modifier.size(20.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = tutorial.title,
                style = Theme[typography][p],
                fontWeight = FontWeight.SemiBold,
                color = Theme[colors][cardForeground],
            )
            Text(
                text = stringResource(Res.string.home_tutorials_card_action_desc),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = Theme[typography][small],
                color = Theme[colors][mutedForeground],
            )
        }
    }
}
