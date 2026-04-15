package dev.anthonyhfm.amethyst.core.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

fun mainDispatcherOrDefault(
    owner: String,
    parallelism: Int? = null,
): CoroutineDispatcher {
    return try {
        val mainDispatcher = Dispatchers.Main
        if (parallelism != null) {
            mainDispatcher.limitedParallelism(parallelism)
        } else {
            mainDispatcher
        }
    } catch (exception: IllegalStateException) {
        println("$owner: Dispatchers.Main unavailable, falling back to Dispatchers.Default (${exception.message})")
        if (parallelism != null) {
            Dispatchers.Default.limitedParallelism(parallelism)
        } else {
            Dispatchers.Default
        }
    }
}
