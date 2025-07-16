package dev.anthonyhfm.amethyst.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import org.koin.compose.koinInject

@Composable
fun AmethystDevice(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    titleBarModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val titleModifier = LocalTitleBarModifier.current.then(titleBarModifier)

    // Bestimme Farben basierend auf Auswahlstatus
    val titleBarColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(titleBarColor)
                .then(titleModifier)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),

            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
