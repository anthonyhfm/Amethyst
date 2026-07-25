package dev.anthonyhfm.amethyst.core.loading

import kotlin.ranges.coerceIn

data class ProgressReport(
    val progress: Float = 0f,
    val title: String = "",
    val statusText: String = "",
    val detailText: String? = null,
    val isIndeterminate: Boolean = false,
)

interface ProgressReporter {
    fun update(
        progress: Float,
        statusText: String,
        detailText: String? = null,
        title: String? = null,
    )

    fun setIndeterminate(
        statusText: String,
        title: String? = null,
    )

    fun subReporter(startProgress: Float, endProgress: Float): ProgressReporter
}

class ScaledProgressReporter(
    private val parent: ProgressReporter,
    private val startProgress: Float,
    private val endProgress: Float,
) : ProgressReporter {

    override fun update(
        progress: Float,
        statusText: String,
        detailText: String?,
        title: String?,
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        val scaled = startProgress + clampedProgress * (endProgress - startProgress)
        parent.update(
            progress = scaled,
            statusText = statusText,
            detailText = detailText,
            title = title
        )
    }

    override fun setIndeterminate(statusText: String, title: String?) {
        parent.setIndeterminate(statusText, title)
    }

    override fun subReporter(startProgress: Float, endProgress: Float): ProgressReporter {
        val clampedStart = startProgress.coerceIn(0f, 1f)
        val clampedEnd = endProgress.coerceIn(0f, 1f)
        val subStart = this.startProgress + clampedStart * (this.endProgress - this.startProgress)
        val subEnd = this.startProgress + clampedEnd * (this.endProgress - this.startProgress)
        return ScaledProgressReporter(parent, subStart, subEnd)
    }
}
