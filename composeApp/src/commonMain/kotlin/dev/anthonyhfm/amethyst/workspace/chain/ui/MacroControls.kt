package dev.anthonyhfm.amethyst.workspace.chain.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.workspace.data.Macro

@Composable
fun MacroControls() {
    var macrosVisible by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .height(32.dp)
            .width(200.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer.compositeOver(MaterialTheme.colorScheme.surfaceColorAtElevation(24.dp)))
            .border(1.dp, MaterialTheme.colorScheme.surfaceBright, RoundedCornerShape(12.dp))
    ) {

    }
}