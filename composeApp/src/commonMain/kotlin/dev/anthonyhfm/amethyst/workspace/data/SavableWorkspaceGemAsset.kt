package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.data.GemRepository
import kotlinx.serialization.Serializable

@Serializable
data class SavableWorkspaceGemAsset(
    val assetId: String,
    val assetVersion: Version,
    val schemaVersion: Version,
    val serializedAsset: String
) {
    fun toGemAsset(): GemAsset = GemRepository.decode(serializedAsset)

    companion object {
        fun from(asset: GemAsset): SavableWorkspaceGemAsset = SavableWorkspaceGemAsset(
            assetId = asset.metadata.id,
            assetVersion = asset.metadata.assetVersion,
            schemaVersion = asset.schemaVersion,
            serializedAsset = GemRepository.encode(asset)
        )
    }
}
