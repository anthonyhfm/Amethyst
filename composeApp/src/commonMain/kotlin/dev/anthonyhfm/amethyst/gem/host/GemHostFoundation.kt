package dev.anthonyhfm.amethyst.gem.host

import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.gem.Gem
import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemExposedParameter
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValueType
import dev.anthonyhfm.amethyst.gem.runtime.GemExecutionPlan
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GemAssetReference(
    val assetId: String = "",
    val assetVersion: Version = Version(1, 0, 0),
    val schemaVersion: Version = Gem.phase1SchemaVersion,
    val location: GemAssetLocation = GemAssetLocation.WorkspaceAsset()
) {
    companion object {
        fun from(
            asset: GemAsset,
            location: GemAssetLocation = GemAssetLocation.WorkspaceAsset(entryId = asset.metadata.id)
        ): GemAssetReference = GemAssetReference(
            assetId = asset.metadata.id,
            assetVersion = asset.metadata.assetVersion,
            schemaVersion = asset.schemaVersion,
            location = location
        )
    }
}

@Serializable
sealed interface GemAssetLocation {
    @Serializable
    @SerialName("workspace-asset")
    data class WorkspaceAsset(
        val entryId: String = ""
    ) : GemAssetLocation
}

@Serializable
data class GemDeviceState(
    val assetReference: GemAssetReference = GemAssetReference(),
    val hostDomain: GemSignalDomain = GemSignalDomain.LED,
    val exposedParameterValues: Map<String, GemValue> = emptyMap(),
    val hostMetadata: Map<String, JsonElement> = emptyMap(),
    val hostUiState: Map<String, JsonElement> = emptyMap()
) : DeviceState() {
    companion object {
        fun fromAsset(
            asset: GemAsset,
            hostDomain: GemSignalDomain,
            exposedParameterValues: Map<String, GemValue> = asset.definition.defaultState.exposedParameterValues,
            hostMetadata: Map<String, JsonElement> = emptyMap(),
            hostUiState: Map<String, JsonElement> = emptyMap()
        ): GemDeviceState = GemDeviceState(
            assetReference = GemAssetReference.from(asset),
            hostDomain = hostDomain,
            exposedParameterValues = exposedParameterValues,
            hostMetadata = hostMetadata,
            hostUiState = hostUiState
        )
    }
}

data class GemDeviceResolvedParameter(
    val id: String,
    val label: String,
    val type: GemValueType,
    val defaultValue: GemValue,
    val groupId: String? = null,
    val sortOrder: Int? = null
)

enum class GemDeviceIssueSeverity {
    INFO,
    WARNING,
    ERROR
}

enum class GemDeviceIssuePhase {
    RESOLUTION,
    VALIDATION,
    RUNTIME
}

enum class GemDeviceIssueCode {
    MISSING_ASSET,
    DUPLICATE_WORKSPACE_ASSET,
    ASSET_VERSION_CHANGED,
    SCHEMA_VERSION_CHANGED,
    INVALID_HOST_CONTRACT,
    HOST_DOMAIN_MISMATCH,
    HOST_CONTEXT_MISMATCH,
    UNKNOWN_EXPOSED_PARAMETER,
    INCOMPATIBLE_EXPOSED_PARAMETER_VALUE,
    ASSET_VALIDATION_ERROR,
    ASSET_COMPILE_ERROR,
    UNSUPPORTED_SEMANTICS,
    RUNTIME_FAILURE
}

data class GemDeviceIssue(
    val severity: GemDeviceIssueSeverity,
    val phase: GemDeviceIssuePhase,
    val code: GemDeviceIssueCode,
    val message: String,
    val parameterId: String? = null
)

enum class GemDeviceValidationState {
    VALID,
    INVALID
}

data class GemDeviceValidationStatus(
    val state: GemDeviceValidationState,
    val issues: List<GemDeviceIssue> = emptyList()
)

enum class GemDeviceRuntimeState {
    UNRESOLVED,
    READY,
    DEGRADED,
    BLOCKED,
    ERROR
}

data class GemDeviceRuntimeStatus(
    val state: GemDeviceRuntimeState,
    val issues: List<GemDeviceIssue> = emptyList()
)

data class GemDeviceRuntimeContract(
    val hostDomain: GemSignalDomain,
    val inputPortId: String? = null,
    val outputPortId: String? = null
)

enum class GemWorkspaceAssetIssueCode {
    MISSING_ASSET_ID,
    DUPLICATE_ASSET_ID
}

data class GemWorkspaceAssetIssue(
    val code: GemWorkspaceAssetIssueCode,
    val message: String,
    val assetId: String? = null
)

data class GemWorkspaceAssetCatalog(
    val assetsById: Map<String, GemAsset>,
    val issues: List<GemWorkspaceAssetIssue> = emptyList()
)

data class GemDeviceResolution(
    val asset: GemAsset? = null,
    val plan: GemExecutionPlan? = null,
    val contract: GemDeviceRuntimeContract? = null,
    val parameters: List<GemDeviceResolvedParameter> = emptyList(),
    val parameterValues: Map<String, GemValue> = emptyMap(),
    val validation: GemDeviceValidationStatus = GemDeviceValidationStatus(GemDeviceValidationState.INVALID),
    val runtime: GemDeviceRuntimeStatus = GemDeviceRuntimeStatus(GemDeviceRuntimeState.UNRESOLVED)
)

internal fun GemExposedParameter.toResolvedParameter(defaultValue: GemValue): GemDeviceResolvedParameter =
    GemDeviceResolvedParameter(
        id = id,
        label = label,
        type = type,
        defaultValue = defaultValue,
        groupId = groupId,
        sortOrder = sortOrder
    )
