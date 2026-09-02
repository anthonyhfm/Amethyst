package dev.anthonyhfm.amethyst.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.anthonyhfm.amethyst.timeline.ui.views.TimelineView

@Composable
fun Timeline() {
    val viewModel: TimelineViewModel = viewModel { TimelineViewModel() }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        TimelineView(viewModel)
    }
}