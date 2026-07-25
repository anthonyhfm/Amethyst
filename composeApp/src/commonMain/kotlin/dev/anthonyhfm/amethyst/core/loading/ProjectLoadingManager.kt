package dev.anthonyhfm.amethyst.core.loading

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.ranges.coerceIn

object ProjectLoadingManager {
    private val _loadingProgress = MutableStateFlow<ProgressReport?>(null)
    val loadingProgress: StateFlow<ProgressReport?> = _loadingProgress.asStateFlow()

    fun startLoading(
        initialTitle: String = "Loading Project",
        initialStatus: String = "Preparing...",
    ) {
        _loadingProgress.value = ProgressReport(
            progress = 0f,
            title = initialTitle,
            statusText = initialStatus,
            isIndeterminate = false
        )
    }

    fun updateProgress(
        progress: Float,
        statusText: String,
        detailText: String? = null,
        title: String? = null,
    ) {
        _loadingProgress.update { current ->
            current?.copy(
                progress = progress.coerceIn(0f, 1f),
                statusText = statusText,
                detailText = detailText ?: current.detailText,
                title = title ?: current.title,
                isIndeterminate = false
            ) ?: ProgressReport(
                progress = progress.coerceIn(0f, 1f),
                title = title ?: "Loading Project",
                statusText = statusText,
                detailText = detailText,
                isIndeterminate = false
            )
        }
    }

    fun setIndeterminate(
        statusText: String,
        title: String? = null,
    ) {
        _loadingProgress.update { current ->
            current?.copy(
                statusText = statusText,
                title = title ?: current.title,
                isIndeterminate = true
            ) ?: ProgressReport(
                progress = 0f,
                title = title ?: "Loading Project",
                statusText = statusText,
                isIndeterminate = true
            )
        }
    }

    fun finishLoading() {
        _loadingProgress.value = null
    }

    val reporter: ProgressReporter = object : ProgressReporter {
        override fun update(
            progress: Float,
            statusText: String,
            detailText: String?,
            title: String?,
        ) {
            updateProgress(progress, statusText, detailText, title)
        }

        override fun setIndeterminate(statusText: String, title: String?) {
            ProjectLoadingManager.setIndeterminate(statusText, title)
        }

        override fun subReporter(startProgress: Float, endProgress: Float): ProgressReporter {
            return ScaledProgressReporter(this, startProgress, endProgress)
        }
    }
}
