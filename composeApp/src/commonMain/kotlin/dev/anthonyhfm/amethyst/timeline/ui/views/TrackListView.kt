package dev.anthonyhfm.amethyst.timeline.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TrackListView() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .border(1.dp, MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0..2) {
            TrackInfo()
        }
    }
}

@Composable
fun TrackInfo() {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .width(160.dp)
            .height(96.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(8.dp),
    ) {
        Text(
            text = "Some Track",
            style = MaterialTheme.typography.labelLarge.copy(
                lineHeight = MaterialTheme.typography.labelLarge.fontSize,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier
                .padding(4.dp)
        )

        Spacer(
            modifier = Modifier
                .weight(1f)
        )

        Icon(
            imageVector = Icons.Default.Audiotrack,
            contentDescription = "Audio Track Icon",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.End)
                .padding(8.dp)
                .size(24.dp)
        )
    }
}