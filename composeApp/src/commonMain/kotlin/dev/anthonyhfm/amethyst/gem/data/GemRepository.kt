package dev.anthonyhfm.amethyst.gem.data

import dev.anthonyhfm.amethyst.gem.GemAsset
import kotlinx.serialization.json.Json

object GemRepository {
    val json: Json
        get() = GemJsonPersistence.json

    fun encode(asset: GemAsset): String = GemJsonPersistence.encode(asset)

    fun decode(serialized: String): GemAsset {
        val result = GemJsonPersistence.decode(serialized)
        val error = result.loadError
        if (error != null) {
            // Re-throw so existing callers using runCatching / try-catch continue to work.
            val cause = when (error) {
                is GemLoadError.ParseError -> error.cause
                is GemLoadError.IoError -> error.cause
                is GemLoadError.MigrationError -> null
            }
            throw IllegalStateException(error.message, cause)
        }
        return result.asset
    }
}
