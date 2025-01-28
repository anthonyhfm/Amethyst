package dev.anthonyhfm.amethyst.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OSXTitleBar() {
    Spacer(
        modifier = Modifier
            .height(26.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    )
}