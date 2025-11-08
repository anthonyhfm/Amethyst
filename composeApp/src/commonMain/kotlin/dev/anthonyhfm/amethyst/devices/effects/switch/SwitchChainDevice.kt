package dev.anthonyhfm.amethyst.devices.effects.switch

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.controls.selection.SelectionManager
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven
import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.ui.components.AmethystDevice
import dev.anthonyhfm.amethyst.ui.components.StepTextDial
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

class SwitchChainDevice : GenericChainDevice<SwitchChainDeviceState>() {
    override val state = MutableStateFlow(SwitchChainDeviceState())

    @Composable
    override fun Content() {
        val deviceState by state.collectAsState()
        val macros by WorkspaceRepository.macros.collectAsState()
        val selections by SelectionManager.selections.collectAsState()

        AmethystDevice(
            title = "Switch",
            isSelected = selections.any { it.selectionUUID == this.selectionUUID },
            isDragging = isDragging.value,
            modifier = Modifier
                .width(100.dp)
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
                            onStartValueChange = {
                                beforeMacro = it
                            },
                            onFinishValueChange = {
                                pushStateChange(
                                    before = state.value.copy(macro = beforeMacro),
                                    after = state.value
                                )
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
                        onStartValueChange = {
                            beforeValue = it
                        },
                        onFinishValueChange = {
                            pushStateChange(
                                before = state.value.copy(value = beforeValue),
                                after = state.value
                            )
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
        signalExit?.invoke(n)

        n.forEach {
            val down: Boolean = when (it) {
                is Signal.LED -> it.color != Color.Black
                is Signal.Midi -> it.velocity != 0
                else -> false
            }

            if (down) {
                Heaven.schedule(1.0) {
                    WorkspaceRepository.setMacroValue(
                        index = state.value.macro,
                        macro = WorkspaceRepository.macros.value[state.value.macro].copy(
                            value = state.value.value
                        )
                    )
                }
            }
        }
    }
}

@Serializable
data class SwitchChainDeviceState(
    val macro: Int = 0,
    val value: Int = 0
) : DeviceState()