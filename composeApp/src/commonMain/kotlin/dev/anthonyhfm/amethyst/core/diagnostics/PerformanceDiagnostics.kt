package dev.anthonyhfm.amethyst.core.diagnostics

import kotlinx.atomicfu.atomic

object PerformanceDiagnostics {
    private val heavenFrames = atomic(0)

    fun recordHeavenFrame() {
        heavenFrames.incrementAndGet()
    }

    fun consumeHeavenFrames(): Int {
        return heavenFrames.getAndSet(0)
    }
}
