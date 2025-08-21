package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
actual fun WorkspaceChainScroller(scrollState: ScrollState) {
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .fillMaxWidth()
            .height(24.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceBright.copy(0.6f), RoundedCornerShape(12.dp))
            .padding(6.dp),
        style = ScrollbarStyle(
            unhoverColor = MaterialTheme.colorScheme.onSurface.copy(0.2f),
            hoverColor = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            minimalHeight = 12.dp,
            hoverDurationMillis = 200,
            shape = RoundedCornerShape(8.dp),
            thickness = 12.dp
        )
    )
}