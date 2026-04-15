package dev.anthonyhfm.amethyst.gem.data

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.gem.GemAsset

/**
 * Productized export/import support for [GemAsset] values.
 *
 * Export serializes an asset to a self-contained JSON string.
 * Import deserializes and always assigns a fresh workspace-scoped ID to prevent
 * conflicts with assets already present in the workspace.
 */
object GemAssetExport {

    /** Serializes [asset] to a pretty-printed JSON string suitable for file export. */
    fun exportToString(asset: GemAsset): String = GemJsonPersistence.encode(asset)

    /**
     * Deserializes a [GemAsset] from [json], reassigning a new workspace-scoped ID
     * to avoid conflicts with existing workspace assets.
     *
     * Returns [GemImportResult.Success] with the ready-to-use asset, or
     * [GemImportResult.Failure] with a human-readable error description.
     * Never throws.
     */
    fun importFromString(json: String): GemImportResult {
        val decoded = GemJsonPersistence.decode(json)
        val error = decoded.loadError
        if (error != null) {
            return GemImportResult.Failure(error = error.message)
        }

        val imported = decoded.asset.copy(
            metadata = decoded.asset.metadata.copy(
                id = "gem://workspace/${UUID.randomUUID()}"
            )
        )
        return GemImportResult.Success(asset = imported)
    }
}

/** Result of a [GemAssetExport.importFromString] call. */
sealed class GemImportResult {
    /** Import succeeded. [asset] has a fresh workspace-scoped ID. */
    data class Success(val asset: GemAsset) : GemImportResult()

    /** Import failed. [error] is a human-readable description suitable for display. */
    data class Failure(val error: String) : GemImportResult()
}
