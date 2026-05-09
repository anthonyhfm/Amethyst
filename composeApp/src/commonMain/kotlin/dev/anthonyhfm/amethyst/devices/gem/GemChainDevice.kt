package dev.anthonyhfm.amethyst.devices.gem

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.gem.GemSignalDomain
import dev.anthonyhfm.amethyst.gem.host.GemDeviceIssue
import dev.anthonyhfm.amethyst.gem.host.GemDeviceIssueCode
import dev.anthonyhfm.amethyst.gem.host.GemDeviceIssuePhase
import dev.anthonyhfm.amethyst.gem.host.GemDeviceIssueSeverity
import dev.anthonyhfm.amethyst.gem.host.GemDeviceResolution
import dev.anthonyhfm.amethyst.gem.host.GemDeviceRuntimeState
import dev.anthonyhfm.amethyst.gem.host.GemDeviceRuntimeStatus
import dev.anthonyhfm.amethyst.gem.host.GemDeviceState
import dev.anthonyhfm.amethyst.gem.host.GemDeviceValidationState
import dev.anthonyhfm.amethyst.gem.host.GemDeviceValidationStatus
import dev.anthonyhfm.amethyst.gem.runtime.GemExecutionPlan
import dev.anthonyhfm.amethyst.gem.runtime.GemExecutionResult
import dev.anthonyhfm.amethyst.gem.runtime.GemExecutor
import dev.anthonyhfm.amethyst.gem.runtime.GemPreviewContext
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnostic
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeDiagnosticCode
import dev.anthonyhfm.amethyst.gem.runtime.GemRuntimeSeverity
import dev.anthonyhfm.amethyst.gem.runtime.GemSignalBatch
import dev.anthonyhfm.amethyst.gem.ui.editor.GemEditorWorkspaceMode
import dev.anthonyhfm.amethyst.ui.components.primitives.Button
import dev.anthonyhfm.amethyst.ui.components.primitives.ButtonSize
import dev.anthonyhfm.amethyst.ui.components.primitives.ChainDeviceShell
import androidx.compose.material3.Icon
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import dev.anthonyhfm.amethyst.workspace.chain.ui.LocalTitleBarModifier
import kotlinx.coroutines.flow.MutableStateFlow
import dev.anthonyhfm.amethyst.devices.ChainDeviceFactory

class GemChainDevice(
    initialState: GemDeviceState = GemDeviceState(),
    initialHostContextDomain: GemSignalDomain? = null,
    private val resolver: (GemDeviceState) -> GemDeviceResolution = WorkspaceRepository::resolveGemDevice,
    private val executor: (GemExecutionPlan, GemPreviewContext) -> GemExecutionResult = GemExecutor::preview
) : GenericChainDevice<GemDeviceState>() {
    override val state: MutableStateFlow<GemDeviceState> = MutableStateFlow(initialState)
    private var hostContextDomain: GemSignalDomain? = initialHostContextDomain

    val resolution: MutableStateFlow<GemDeviceResolution> = MutableStateFlow(GemDeviceResolution())
    val validationStatus: MutableStateFlow<GemDeviceValidationStatus> = MutableStateFlow(
        GemDeviceValidationStatus(GemDeviceValidationState.INVALID)
    )
    val runtimeStatus: MutableStateFlow<GemDeviceRuntimeStatus> = MutableStateFlow(
        GemDeviceRuntimeStatus(GemDeviceRuntimeState.UNRESOLVED)
    )
    val lastExecutionResult: MutableStateFlow<GemExecutionResult?> = MutableStateFlow(null)

    init {
        refreshResolution()
    }

    override fun onAddedToChain(parentChain: Chain) {
        attachToHostContext(WorkspaceRepository.hostDomainForChain(parentChain))
    }

    override fun onStateRestored() {
        refreshResolution()
    }

    fun attachToHostContext(hostContextDomain: GemSignalDomain?) {
        this.hostContextDomain = hostContextDomain
        refreshResolution()
    }

    fun refreshResolution(): GemDeviceResolution {
        val resolved = try {
            resolver(state.value)
        } catch (throwable: Throwable) {
            resolutionFailure(
                message = "Gem resolution failed: ${throwable.message ?: throwable.toString()}"
            )
        }

        val contextualizedResolution = applyHostContextValidation(resolved)
        resolution.value = contextualizedResolution
        validationStatus.value = contextualizedResolution.validation
        runtimeStatus.value = contextualizedResolution.runtime
        return contextualizedResolution
    }

    @Composable
    override fun Content() {
        val selections by SelectionManager.selections.collectAsState()
        val gemAssets by WorkspaceRepository.gemAssets.collectAsState()
        val currentResolution by resolution.collectAsState()
        val isSelected = selections.any { it.selectionUUID == selectionUUID }

        LaunchedEffect(state.value, gemAssets) {
            refreshResolution()
        }

        ChainDeviceShell(
            title = currentResolution.asset?.metadata?.name ?: state.value.assetReference.assetId.ifBlank { "Gem" },
            isSelected = isSelected,
            isDragging = isDragging.value,
            modifier = Modifier.width(120.dp),
            titleBarModifier = LocalTitleBarModifier.current
        ) {
            Button(
                onClick = { openGemAuthoring(currentResolution) },
                size = ButtonSize.IconLarge,
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                )
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        val compatibleSignals = filterCompatibleSignals(n)

        val resolved = refreshResolution()
        val plan = resolved.plan
        val contract = resolved.contract

        if (resolved.runtime.state == GemDeviceRuntimeState.BLOCKED || plan == null || contract == null) {
            lastExecutionResult.value = null
            runtimeStatus.value = resolved.runtime
            signalExit?.invoke(emptyList())
            return
        }

        // For gems with an input port, skip execution if no compatible signals arrived.
        // Source gems (no input port) always run once to produce their own output.
        if (contract.inputPortId != null && compatibleSignals.isEmpty()) {
            return
        }

        // Build per-execution host input maps:
        //  - processor / sink  → one run per incoming signal
        //  - source (no input) → one run with empty host inputs
        val executionContexts: List<Map<String, GemSignalBatch>> = if (contract.inputPortId != null) {
            compatibleSignals.map { signal ->
                mapOf(contract.inputPortId to GemSignalBatch(domain = contract.hostDomain, signals = listOf(signal)))
            }
        } else {
            listOf(emptyMap())
        }

        val outputSignals = mutableListOf<Signal>()
        var lastExecutionResultValue: GemExecutionResult? = null
        val allDiagnostics = mutableListOf<GemRuntimeDiagnostic>()

        for (hostInputs in executionContexts) {
            val result = try {
                executor(
                    plan,
                    GemPreviewContext(
                        parameterValues = resolved.parameterValues,
                        hostInputs = hostInputs,
                        signalScheduler = { signals, ms -> Heaven.schedule(ms) { signalExit?.invoke(signals) } }
                    )
                )
            } catch (throwable: Throwable) {
                val runtimeIssue = runtimeFailureIssue(
                    "Gem execution failed: ${throwable.message ?: throwable.toString()}"
                )
                lastExecutionResult.value = null
                runtimeStatus.value = GemDeviceRuntimeStatus(
                    state = GemDeviceRuntimeState.ERROR,
                    issues = resolved.runtime.issues + runtimeIssue
                )
                signalExit?.invoke(emptyList())
                return
            }

            lastExecutionResultValue = result
            allDiagnostics += result.diagnostics
            val portId = contract.outputPortId
            if (portId != null) {
                val signals = result.hostOutput(portId)?.signals
                if (signals != null) outputSignals += signals
            }
        }

        lastExecutionResult.value = lastExecutionResultValue

        val executionIssues = allDiagnostics.map(::mapExecutionDiagnostic)
        val missingOutputIssue = if (contract.outputPortId != null &&
            lastExecutionResultValue?.isSuccess == true && outputSignals.isEmpty()) {
            listOf(
                runtimeFailureIssue(
                    "Gem execution produced no '${contract.outputPortId}' host output signals."
                )
            )
        } else {
            emptyList()
        }
        val mergedIssues = resolved.runtime.issues + executionIssues + missingOutputIssue

        runtimeStatus.value = GemDeviceRuntimeStatus(
            state = when {
                mergedIssues.any { it.severity == GemDeviceIssueSeverity.ERROR } -> GemDeviceRuntimeState.ERROR
                mergedIssues.any { it.severity == GemDeviceIssueSeverity.WARNING } ||
                    resolved.runtime.state == GemDeviceRuntimeState.DEGRADED -> GemDeviceRuntimeState.DEGRADED

                else -> GemDeviceRuntimeState.READY
            },
            issues = mergedIssues
        )

        if (lastExecutionResultValue?.isSuccess != true) {
            signalExit?.invoke(emptyList())
            return
        }

        signalExit?.invoke(outputSignals)
    }

    private fun updateDeviceState(transform: (GemDeviceState) -> GemDeviceState) {
        val before = state.value
        val after = transform(before)
        if (before == after) {
            return
        }

        state.value = after
        pushStateChange(before, after)
        refreshResolution()
    }

    private fun openGemAuthoring(resolved: GemDeviceResolution) {
        resolved.asset?.let { asset ->
            WorkspaceRepository.switchMode(
                GemEditorWorkspaceMode(
                    initialAssetId = asset.metadata.id,
                    entryContext = GemEditorWorkspaceMode.EntryContext.HostDevice(
                        preferredHostDomain = state.value.hostDomain,
                        referencedAssetId = asset.metadata.id,
                        referencedAssetName = asset.metadata.name.ifBlank { asset.metadata.id }
                    )
                )
            )
        }
    }

    private fun filterCompatibleSignals(signals: List<Signal>): List<Signal> = when (state.value.hostDomain) {
        GemSignalDomain.LED -> signals.filterIsInstance<Signal.LED>()
        GemSignalDomain.MIDI -> signals.filterIsInstance<Signal.Midi>()
    }

    private fun mapExecutionDiagnostic(diagnostic: GemRuntimeDiagnostic): GemDeviceIssue = GemDeviceIssue(
        severity = when (diagnostic.severity) {
            GemRuntimeSeverity.INFO -> GemDeviceIssueSeverity.INFO
            GemRuntimeSeverity.WARNING -> GemDeviceIssueSeverity.WARNING
            GemRuntimeSeverity.ERROR -> GemDeviceIssueSeverity.ERROR
        },
        phase = GemDeviceIssuePhase.RUNTIME,
        code = when (diagnostic.code) {
            GemRuntimeDiagnosticCode.UNSUPPORTED_NODE_SEMANTICS -> GemDeviceIssueCode.UNSUPPORTED_SEMANTICS
            else -> GemDeviceIssueCode.RUNTIME_FAILURE
        },
        message = diagnostic.message,
        parameterId = diagnostic.parameterId
    )

    private fun resolutionFailure(message: String): GemDeviceResolution {
        val issue = GemDeviceIssue(
            severity = GemDeviceIssueSeverity.ERROR,
            phase = GemDeviceIssuePhase.VALIDATION,
            code = GemDeviceIssueCode.ASSET_COMPILE_ERROR,
            message = message
        )
        return GemDeviceResolution(
            validation = GemDeviceValidationStatus(
                state = GemDeviceValidationState.INVALID,
                issues = listOf(issue)
            ),
            runtime = GemDeviceRuntimeStatus(
                state = GemDeviceRuntimeState.BLOCKED,
                issues = listOf(issue)
            )
        )
    }

    private fun applyHostContextValidation(resolved: GemDeviceResolution): GemDeviceResolution {
        val expectedHostContext = hostContextDomain ?: return resolved
        if (state.value.hostDomain == expectedHostContext) {
            return resolved
        }

        val issue = GemDeviceIssue(
            severity = GemDeviceIssueSeverity.ERROR,
            phase = GemDeviceIssuePhase.VALIDATION,
            code = GemDeviceIssueCode.HOST_CONTEXT_MISMATCH,
            message = "Gem device is configured for ${state.value.hostDomain} but is hosted inside a $expectedHostContext chain context."
        )
        val validationIssues = resolved.validation.issues.appendDistinct(issue)
        val runtimeIssues = resolved.runtime.issues.appendDistinct(issue)

        return resolved.copy(
            validation = GemDeviceValidationStatus(
                state = GemDeviceValidationState.INVALID,
                issues = validationIssues
            ),
            runtime = GemDeviceRuntimeStatus(
                state = GemDeviceRuntimeState.BLOCKED,
                issues = runtimeIssues
            )
        )
    }

    private fun runtimeFailureIssue(message: String): GemDeviceIssue = GemDeviceIssue(
        severity = GemDeviceIssueSeverity.ERROR,
        phase = GemDeviceIssuePhase.RUNTIME,
        code = GemDeviceIssueCode.RUNTIME_FAILURE,
        message = message
    )

    companion object : ChainDeviceFactory<GemDeviceState> {
        override val stateClass = GemDeviceState::class
        override val serializer = GemDeviceState.serializer()
        override fun create() = GemChainDevice()
        override fun unpack(state: GemDeviceState) = GemChainDevice(initialState = state)
    }
}

private fun List<GemDeviceIssue>.appendDistinct(issue: GemDeviceIssue): List<GemDeviceIssue> {
    return if (any { existing ->
            existing.code == issue.code &&
                existing.phase == issue.phase &&
                existing.parameterId == issue.parameterId &&
                existing.message == issue.message
        }
    ) {
        this
    } else {
        this + issue
    }
}
