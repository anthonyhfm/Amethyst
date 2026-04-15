package dev.anthonyhfm.amethyst.gem.host

import dev.anthonyhfm.amethyst.gem.GemAsset
import dev.anthonyhfm.amethyst.gem.GemBuiltInNodes
import dev.anthonyhfm.amethyst.gem.GemHostAssetShape
import dev.anthonyhfm.amethyst.gem.GemNodeRegistry
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.GemValue
import dev.anthonyhfm.amethyst.gem.GemValueType
import dev.anthonyhfm.amethyst.gem.GemValidator
import dev.anthonyhfm.amethyst.gem.runtime.GemCompiler
import dev.anthonyhfm.amethyst.gem.runtime.GemExecutionNodePlan
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnostic
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeSeverity

object GemHostResolver {
    fun catalog(assets: List<GemAsset>): GemWorkspaceAssetCatalog {
        val groupedById = linkedMapOf<String, MutableList<GemAsset>>()
        val issues = mutableListOf<GemWorkspaceAssetIssue>()

        assets.forEach { asset ->
            val assetId = asset.metadata.id.trim()
            if (assetId.isBlank()) {
                issues += GemWorkspaceAssetIssue(
                    code = GemWorkspaceAssetIssueCode.MISSING_ASSET_ID,
                    message = "Workspace gem asset '${asset.metadata.name}' is missing a stable asset ID."
                )
                return@forEach
            }

            groupedById.getOrPut(assetId) { mutableListOf() }.add(asset)
        }

        val assetsById = linkedMapOf<String, GemAsset>()
        groupedById.forEach { (assetId, matchingAssets) ->
            if (matchingAssets.size > 1) {
                issues += GemWorkspaceAssetIssue(
                    code = GemWorkspaceAssetIssueCode.DUPLICATE_ASSET_ID,
                    message = "Workspace gem asset ID '$assetId' is declared more than once.",
                    assetId = assetId
                )
            } else {
                assetsById[assetId] = matchingAssets.single()
            }
        }

        return GemWorkspaceAssetCatalog(
            assetsById = assetsById,
            issues = issues
        )
    }

    fun resolve(
        deviceState: GemDeviceState,
        assets: List<GemAsset>,
        registry: GemNodeRegistry = GemNodeRegistry.builtIns
    ): GemDeviceResolution = resolve(
        deviceState = deviceState,
        catalog = catalog(assets),
        registry = registry
    )

    fun resolve(
        deviceState: GemDeviceState,
        catalog: GemWorkspaceAssetCatalog,
        registry: GemNodeRegistry = GemNodeRegistry.builtIns
    ): GemDeviceResolution {
        val issues = mutableListOf<GemDeviceIssue>()

        val duplicateAssetIssue = catalog.issues.firstOrNull {
            it.code == GemWorkspaceAssetIssueCode.DUPLICATE_ASSET_ID &&
                it.assetId == deviceState.assetReference.assetId
        }
        if (duplicateAssetIssue != null) {
            issues += GemDeviceIssue(
                severity = GemDeviceIssueSeverity.ERROR,
                phase = GemDeviceIssuePhase.RESOLUTION,
                code = GemDeviceIssueCode.DUPLICATE_WORKSPACE_ASSET,
                message = duplicateAssetIssue.message
            )
            return blockedResolution(issues = issues)
        }

        val asset = catalog.assetsById[deviceState.assetReference.assetId]
        if (asset == null) {
            issues += GemDeviceIssue(
                severity = GemDeviceIssueSeverity.ERROR,
                phase = GemDeviceIssuePhase.RESOLUTION,
                code = GemDeviceIssueCode.MISSING_ASSET,
                message = "Workspace gem asset '${deviceState.assetReference.assetId}' could not be resolved."
            )
            return blockedResolution(issues = issues)
        }

        if (deviceState.assetReference.assetVersion != asset.metadata.assetVersion) {
            issues += GemDeviceIssue(
                severity = GemDeviceIssueSeverity.WARNING,
                phase = GemDeviceIssuePhase.RESOLUTION,
                code = GemDeviceIssueCode.ASSET_VERSION_CHANGED,
                message = "Gem asset '${asset.metadata.id}' changed from version ${deviceState.assetReference.assetVersion} to ${asset.metadata.assetVersion}."
            )
        }

        if (deviceState.assetReference.schemaVersion != asset.schemaVersion) {
            issues += GemDeviceIssue(
                severity = GemDeviceIssueSeverity.WARNING,
                phase = GemDeviceIssuePhase.RESOLUTION,
                code = GemDeviceIssueCode.SCHEMA_VERSION_CHANGED,
                message = "Gem asset '${asset.metadata.id}' changed schema version from ${deviceState.assetReference.schemaVersion} to ${asset.schemaVersion}."
            )
        }

        if (deviceState.hostDomain !in asset.definition.host.supportedDomains) {
            issues += GemDeviceIssue(
                severity = GemDeviceIssueSeverity.ERROR,
                phase = GemDeviceIssuePhase.VALIDATION,
                code = GemDeviceIssueCode.HOST_DOMAIN_MISMATCH,
                message = "Gem asset '${asset.metadata.id}' does not support host domain ${deviceState.hostDomain}."
            )
        }

        val parameters = asset.definition.exposedParameters.map { parameter ->
            parameter.toResolvedParameter(
                defaultValue = asset.definition.defaultState.exposedParameterValues[parameter.id] ?: parameter.defaultValue
            )
        }
        val resolvedParameterValues = resolveParameterValues(
            deviceState = deviceState,
            parameters = parameters,
            issues = issues
        )

        val validation = GemValidator.validate(asset, registry)
        if (!validation.isValid) {
            issues += validation.errors.map { error ->
                GemDeviceIssue(
                    severity = GemDeviceIssueSeverity.ERROR,
                    phase = GemDeviceIssuePhase.VALIDATION,
                    code = GemDeviceIssueCode.ASSET_VALIDATION_ERROR,
                    message = error.message,
                    parameterId = error.exposedParameterId
                )
            }
        }

        val compilation = if (validation.isValid) {
            GemCompiler.compile(asset = asset, validation = validation)
        } else {
            null
        }

        if (compilation != null) {
            issues += compilation.diagnostics.map(::mapDiagnostic)
        }

        val runtimeContract = if (compilation?.plan != null) {
            resolveRuntimeContract(
                deviceState = deviceState,
                asset = asset,
                plan = compilation.plan,
                issues = issues
            )
        } else {
            null
        }

        val validationStatus = GemDeviceValidationStatus(
            state = if (issues.any { it.severity == GemDeviceIssueSeverity.ERROR }) {
                GemDeviceValidationState.INVALID
            } else {
                GemDeviceValidationState.VALID
            },
            issues = issues.toList()
        )

        val runtimeIssues = issues.toList()
        val runtimeState = when {
            issues.any { it.severity == GemDeviceIssueSeverity.ERROR } -> GemDeviceRuntimeState.BLOCKED
            issues.any { it.severity == GemDeviceIssueSeverity.WARNING } ||
                compilation?.diagnostics?.any { it.severity == GemRuntimeSeverity.WARNING } == true -> GemDeviceRuntimeState.DEGRADED
            compilation?.plan != null && runtimeContract != null -> GemDeviceRuntimeState.READY
            else -> GemDeviceRuntimeState.UNRESOLVED
        }

        return GemDeviceResolution(
            asset = asset,
            plan = compilation?.plan,
            contract = runtimeContract,
            parameters = parameters,
            parameterValues = resolvedParameterValues,
            validation = validationStatus,
            runtime = GemDeviceRuntimeStatus(
                state = runtimeState,
                issues = runtimeIssues
            )
        )
    }

    private fun blockedResolution(issues: List<GemDeviceIssue>): GemDeviceResolution = GemDeviceResolution(
        validation = GemDeviceValidationStatus(
            state = GemDeviceValidationState.INVALID,
            issues = issues
        ),
        runtime = GemDeviceRuntimeStatus(
            state = GemDeviceRuntimeState.BLOCKED,
            issues = issues
        )
    )

    private fun resolveParameterValues(
        deviceState: GemDeviceState,
        parameters: List<GemDeviceResolvedParameter>,
        issues: MutableList<GemDeviceIssue>
    ): Map<String, GemValue> {
        val parameterById = parameters.associateBy { it.id }
        val values = linkedMapOf<String, GemValue>()

        parameters.forEach { parameter ->
            val stateValue = deviceState.exposedParameterValues[parameter.id]
            values[parameter.id] = when {
                stateValue == null -> parameter.defaultValue
                stateValue.matchesDeclaredType(parameter.type) -> stateValue
                else -> {
                    issues += GemDeviceIssue(
                        severity = GemDeviceIssueSeverity.ERROR,
                        phase = GemDeviceIssuePhase.VALIDATION,
                        code = GemDeviceIssueCode.INCOMPATIBLE_EXPOSED_PARAMETER_VALUE,
                        message = "Exposed parameter '${parameter.id}' no longer matches the declared host type.",
                        parameterId = parameter.id
                    )
                    parameter.defaultValue
                }
            }
        }

        deviceState.exposedParameterValues.keys
            .filterNot(parameterById::containsKey)
            .sorted()
            .forEach { parameterId ->
                issues += GemDeviceIssue(
                    severity = GemDeviceIssueSeverity.ERROR,
                    phase = GemDeviceIssuePhase.VALIDATION,
                    code = GemDeviceIssueCode.UNKNOWN_EXPOSED_PARAMETER,
                    message = "Exposed parameter '$parameterId' no longer exists on the referenced gem asset.",
                    parameterId = parameterId
                )
            }

        return values
    }

    private fun resolveRuntimeContract(
        deviceState: GemDeviceState,
        asset: GemAsset,
        plan: dev.anthonyhfm.amethyst.gem.runtime.GemExecutionPlan,
        issues: MutableList<GemDeviceIssue>
    ): GemDeviceRuntimeContract? {
        if (asset.definition.host.assetShape != GemHostAssetShape.PROCESSOR) {
            issues += GemDeviceIssue(
                severity = GemDeviceIssueSeverity.ERROR,
                phase = GemDeviceIssuePhase.VALIDATION,
                code = GemDeviceIssueCode.INVALID_HOST_CONTRACT,
                message = "Phase 2 chain runtime currently supports processor gems only."
            )
            return null
        }

        val bindings = plan.graphPlans
            .flatMap { graphPlan -> graphPlan.nodePlans.mapNotNull(::toRuntimeBinding) }

        val mismatchedBindings = bindings.filter { it.domain != deviceState.hostDomain }
        if (mismatchedBindings.isNotEmpty()) {
            issues += GemDeviceIssue(
                severity = GemDeviceIssueSeverity.ERROR,
                phase = GemDeviceIssuePhase.VALIDATION,
                code = GemDeviceIssueCode.INVALID_HOST_CONTRACT,
                message = "Phase 2 chain runtime cannot host gem '${asset.metadata.id}' because it binds host ports outside the selected ${deviceState.hostDomain} domain."
            )
            return null
        }

        val inputBindings = bindings.filter { it.kind == GemRuntimePortKind.INPUT }
        val outputBindings = bindings.filter { it.kind == GemRuntimePortKind.OUTPUT }

        if (inputBindings.size > 1 || outputBindings.size > 1) {
            issues += GemDeviceIssue(
                severity = GemDeviceIssueSeverity.ERROR,
                phase = GemDeviceIssuePhase.VALIDATION,
                code = GemDeviceIssueCode.INVALID_HOST_CONTRACT,
                message = "Ambiguous ${deviceState.hostDomain} host bindings: at most one input and one output node are allowed."
            )
            return null
        }

        if (inputBindings.isEmpty() && outputBindings.isEmpty()) {
            issues += GemDeviceIssue(
                severity = GemDeviceIssueSeverity.ERROR,
                phase = GemDeviceIssuePhase.VALIDATION,
                code = GemDeviceIssueCode.INVALID_HOST_CONTRACT,
                message = "The gem has no ${deviceState.hostDomain} host bindings. Add at least one LED In or LED Out node."
            )
            return null
        }

        return GemDeviceRuntimeContract(
            hostDomain = deviceState.hostDomain,
            inputPortId = inputBindings.singleOrNull()?.portId,
            outputPortId = outputBindings.singleOrNull()?.portId
        )
    }

    private fun mapDiagnostic(diagnostic: GemRuntimeDiagnostic): GemDeviceIssue = GemDeviceIssue(
        severity = when (diagnostic.severity) {
            GemRuntimeSeverity.INFO -> GemDeviceIssueSeverity.INFO
            GemRuntimeSeverity.WARNING -> GemDeviceIssueSeverity.WARNING
            GemRuntimeSeverity.ERROR -> GemDeviceIssueSeverity.ERROR
        },
        phase = when (diagnostic.code) {
            GemRuntimeDiagnosticCode.UNSUPPORTED_NODE_SEMANTICS -> GemDeviceIssuePhase.RUNTIME
            else -> GemDeviceIssuePhase.VALIDATION
        },
        code = when (diagnostic.code) {
            GemRuntimeDiagnosticCode.UNSUPPORTED_NODE_SEMANTICS -> GemDeviceIssueCode.UNSUPPORTED_SEMANTICS
            else -> GemDeviceIssueCode.ASSET_COMPILE_ERROR
        },
        message = diagnostic.message,
        parameterId = diagnostic.parameterId
    )
}

private fun GemValue.matchesDeclaredType(type: GemValueType): Boolean = when {
    this is GemValue.Number && type == GemValueType.Number -> true
    this is GemValue.Boolean && type == GemValueType.Boolean -> true
    this is GemValue.Color && type == GemValueType.Color -> true
    this is GemValue.TimingValue && type == GemValueType.Timing -> true
    this is GemValue.Enum && type is GemValueType.Enum ->
        enumId == type.definition.id && type.definition.options.any { it.id == optionId }
    else -> false
}

private fun toRuntimeBinding(nodePlan: GemExecutionNodePlan): GemRuntimePortBinding? {
    val kind = when (nodePlan.type.typeId) {
        GemBuiltInNodes.TypeIds.HOST_LED_INPUT,
        GemBuiltInNodes.TypeIds.HOST_MIDI_INPUT -> GemRuntimePortKind.INPUT

        GemBuiltInNodes.TypeIds.HOST_LED_OUTPUT,
        GemBuiltInNodes.TypeIds.HOST_MIDI_OUTPUT -> GemRuntimePortKind.OUTPUT

        else -> return null
    }
    val domain = when (nodePlan.type.typeId) {
        GemBuiltInNodes.TypeIds.HOST_LED_INPUT,
        GemBuiltInNodes.TypeIds.HOST_LED_OUTPUT -> GemSignalDomain.LED

        GemBuiltInNodes.TypeIds.HOST_MIDI_INPUT,
        GemBuiltInNodes.TypeIds.HOST_MIDI_OUTPUT -> GemSignalDomain.MIDI

        else -> GemSignalDomain.MIDI
    }
    val portId = nodePlan.hostPortId ?: return null

    return GemRuntimePortBinding(
        kind = kind,
        domain = domain,
        portId = portId
    )
}

private data class GemRuntimePortBinding(
    val kind: GemRuntimePortKind,
    val domain: GemSignalDomain,
    val portId: String
)

private enum class GemRuntimePortKind {
    INPUT,
    OUTPUT
}
