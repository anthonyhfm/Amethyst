package dev.anthonyhfm.amethyst.gem

import dev.anthonyhfm.amethyst.core.util.Version

object Gem {
    val phase1SchemaVersion: Version = Version(1, 0, 0)
    val phase4SchemaVersion: Version = Version(1, 1, 0)
    const val rootGraphId: String = "root"

    fun emptyAsset(
        assetId: String = "",
        name: String = "Untitled Gem"
    ): GemAsset = GemAsset(
        metadata = GemAssetMetadata(
            id = assetId,
            name = name
        )
    )
}
