package dev.anthonyhfm.amethyst

import dev.anthonyhfm.amethyst.core.util.amethystVersion
import io.sentry.kotlin.multiplatform.Sentry

fun initializeSentry() {
    Sentry.init { options ->
        options.dsn = "https://889766658773bf17d4fe3a0f92e07684@o4511429901811712.ingest.de.sentry.io/4511429935104080"
        options.release = amethystVersion.let { "${it.major}.${it.minor}.${it.hotfix}" }

    }
}
