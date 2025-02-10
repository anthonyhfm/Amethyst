package dev.anthonyhfm.amethyst.editor.plugins.keyframes.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthonyhfm.amethyst.editor.plugins.keyframes.data.Keyframe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class KeyframeEditorViewModel(
    val keyframeData: MutableStateFlow<List<Keyframe>>
) : ViewModel() {
    val state = MutableStateFlow(KeyframeEditorState())

    fun addKeyframe(atIndex: Int? = null) {
        viewModelScope.launch {
            val keyframes = keyframeData.value.toMutableList()

            if (atIndex != null) {
                keyframes.add(atIndex, Keyframe())
            } else {
                keyframes.add(Keyframe())
            }

            keyframeData.emit(keyframes)

            selectKeyframe(atIndex ?: keyframeData.value.lastIndex)
        }
    }

    fun setKeyframeLight(x: Int, y: Int) {
        viewModelScope.launch {
            val keyframes = keyframeData.value.toMutableList()
            val frame = keyframes[state.value.selectedKeyframe].frame

            keyframes[state.value.selectedKeyframe] = keyframes[state.value.selectedKeyframe].copy(
                frame = frame.mapIndexed { array_x, it ->
                    it.mapIndexed { array_y, data ->
                        if (array_x == x && array_y == y) {
                            data.copy(
                                r = 63,
                                g = 63,
                                b = 63,
                            )
                        } else {
                            data
                        }
                    }
                }
            )

            keyframeData.emit(keyframes)
        }
    }

    fun selectKeyframe(index: Int) {
        viewModelScope.launch {
            state.update {
                it.copy(
                    selectedKeyframe = index
                )
            }
        }
    }
}

data class KeyframeEditorState(
    val selectedKeyframe: Int = 0,
    val selectedColor: Color = Color.White
)