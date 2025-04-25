package dev.anthonyhfm.amethyst.devices.effects.keyframes

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class KeyframesWorkspaceModeViewModel : ViewModel() {
    val state = MutableStateFlow(KeyframesUIState())
}

data class KeyframesUIState(
    val drawColor: Color = Color.White
)