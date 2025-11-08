package dev.anthonyhfm.amethyst.devices.effects.macro_filter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.StepTextDial
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class MacroFilterChainDevice : GenericChainDevice<MacroFilterChainDeviceState>() {
    override val state = MutableStateFlow(MacroFilterChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val macros by WorkspaceRepository.macros.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Macro Filter",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(120.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),

                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (macros.isNotEmpty()) {
                    if (macros.size > 1) {
                        var beforeMacro = deviceState.copy().macro
                        StepTextDial(
                            headline = "Macro",
                            value = deviceState.macro,
                            steps = IntArray(macros.size) { it }.toList(),
                            text = "${deviceState.macro + 1}",
                            onStartValueChange = {
                                beforeMacro = it
                            },
                            onResolveTextValue = {
                                val macroText = it.trim().toIntOrNull()

                                macroText?.let { macro ->
                                    if (macro in 1..macros.size) {
                                        state.update {
                                            it.copy(macro = macro - 1)
                                        }
                                    }
                                }
                            },
                            onFinishValueChange = {
                                pushStateChange(state.value.copy(macro = beforeMacro), state.value)
                            },
                            onValueChange = { value ->
                                state.update {
                                    it.copy(macro = value)
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Macro 1",
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    var beforeValue = deviceState.copy().value
                    StepTextDial(
                        headline = "Value",
                        value = deviceState.value,
                        steps = IntArray(128) { it }.toList(),
                        text = deviceState.value.toString(),
                        onStartValueChange = {
                            beforeValue = it
                        },
                        onResolveTextValue = {
                            val valueText = it.trim().toIntOrNull()

                            valueText?.let { value ->
                                if (value in 0..127) {
                                    state.update {
                                        it.copy(value = value)
                                    }
                                }
                            }
                        },
                        onValueChange = { value ->
                            state.update {
                                it.copy(value = value)
                            }
                        },
                        onFinishValueChange = {
                            pushStateChange(state.value.copy(value = beforeValue), state.value)
                        }
                    )
                } else {
                    Text(
                        text = "No macros available",
                        modifier = Modifier
                            .padding(horizontal = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    override fun signalEnter(n: List<Signal>) {
        if (WorkspaceRepository.macros.value.getOrNull(state.value.macro)?.value == state.value.value) {
            signalExit?.invoke(n)
        }
    }
}

@Serializable
data class MacroFilterChainDeviceState(
    val macro: Int = 0,
    val value: Int = 0
) : DeviceState()