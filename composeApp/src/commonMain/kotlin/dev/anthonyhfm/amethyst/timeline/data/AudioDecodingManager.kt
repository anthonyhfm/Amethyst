package dev.anthonyhfm.amethyst.timeline.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DecodingClipState(
    val sourceId: String,
    val fileName: String,
    val progress: Float = 0f, // 0.0f .. 1.0f
    val isDecoding: Boolean = true,
    val error: String? = null,
)

/**
 * Tracks asynchronous audio decoding tasks for clips in the timeline.
 * Allows UI components (like [AudioClip]) to render skeleton previews
 * and progressive loading animations while raw PCM audio is being decoded.
 */
object AudioDecodingManager {
    private val _loadingStates = MutableStateFlow<Map<String, DecodingClipState>>(emptyMap())
    val loadingStates: StateFlow<Map<String, DecodingClipState>> = _loadingStates.asStateFlow()

    fun startDecoding(sourceId: String, fileName: String) {
        _loadingStates.update { current ->
            current + (sourceId to DecodingClipState(
                sourceId = sourceId,
                fileName = fileName,
                progress = 0.05f,
                isDecoding = true
            ))
        }
    }

    fun updateProgress(sourceId: String, progress: Float) {
        _loadingStates.update { current ->
            val existing = current[sourceId] ?: return@update current
            current + (sourceId to existing.copy(progress = progress.coerceIn(0f, 1f)))
        }
    }

    fun markComplete(sourceId: String) {
        _loadingStates.update { current ->
            current - sourceId
        }
    }

    fun markError(sourceId: String, error: String) {
        _loadingStates.update { current ->
            val existing = current[sourceId] ?: return@update current
            current + (sourceId to existing.copy(isDecoding = false, error = error))
        }
    }

    fun getLoadingState(sourceId: String): DecodingClipState? = _loadingStates.value[sourceId]
}
