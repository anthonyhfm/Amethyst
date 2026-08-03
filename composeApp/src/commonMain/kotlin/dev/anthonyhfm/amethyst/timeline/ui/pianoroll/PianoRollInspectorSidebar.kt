package dev.anthonyhfm.amethyst.timeline.ui.pianoroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Blend
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Lucide
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.devices.effects.gradient.GradientSmoothness
import dev.anthonyhfm.amethyst.devices.effects.keyframes.ui.components.ColorControls
import dev.anthonyhfm.amethyst.timeline.data.GradientInterpolator
import dev.anthonyhfm.amethyst.timeline.data.MidiNote
import dev.anthonyhfm.amethyst.timeline.data.NoteGradientStop
import dev.anthonyhfm.amethyst.timeline.ui.NoteGradientEditorBar
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsContent
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsTrigger
import dev.anthonyhfm.amethyst.ui.theme.background
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.muted

@Composable
fun PianoRollInspectorSidebar(
    gradientMode: Boolean,
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    workingGradient: List<NoteGradientStop>?,
    selectedGradientStopUUID: String?,
    onSelectGradientStop: (String?) -> Unit,
    onStopMoved: (uuid: String, newPos: Float) -> Unit,
    onAddStop: (position: Float) -> Unit,
    onDeleteStop: (uuid: String) -> Unit,
    onSmoothnessChange: (uuid: String, smoothness: GradientSmoothness) -> Unit,
    onGradientDragStart: () -> Unit,
    onGradientDragFinish: () -> Unit,
    onSolidTabSelected: () -> Unit,
    onGradientTabSelected: () -> Unit,
    enabled: Boolean,
    hasMultipleSelection: Boolean,
    onApplyTransform: ((List<MidiNote>) -> List<MidiNote>) -> Unit,
    onGradientSpread: () -> Unit,
    onRandomizeColors: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 12.dp, bottom = 12.dp)
            .width(300.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Theme[colors][background].copy(alpha = 0.95f))
            .border(1.dp, Theme[colors][border], RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Color / Gradient Tab Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Theme[colors][muted])
                .border(1.dp, Theme[colors][border], RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Tabs(
                selectedTab = if (gradientMode) "gradient" else "solid",
                tabs = listOf("solid", "gradient"),
                modifier = Modifier.fillMaxWidth()
            ) {
                TabsList(modifier = Modifier.fillMaxWidth()) {
                    TabsTrigger(
                        key = "solid",
                        selected = !gradientMode,
                        onSelected = onSolidTabSelected,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Lucide.Droplet, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Solid")
                        }
                    }
                    TabsTrigger(
                        key = "gradient",
                        selected = gradientMode,
                        onSelected = onGradientTabSelected,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Lucide.Blend, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("Gradient")
                        }
                    }
                }
                TabsContent("solid") {
                    ColorControls(
                        color = selectedColor,
                        onColorChange = onColorChange
                    )
                }
                TabsContent("gradient") {
                    if (workingGradient != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            NoteGradientEditorBar(
                                selectedStopUUID = selectedGradientStopUUID,
                                onSelectionChange = { uuid ->
                                    onSelectGradientStop(uuid)
                                    val stop = workingGradient.find { it.selectionUUID == uuid }
                                    if (stop != null) onColorChange(Color(stop.r, stop.g, stop.b))
                                },
                                stops = workingGradient,
                                onStopMoved = onStopMoved,
                                onAddStop = onAddStop,
                                onDeleteStop = onDeleteStop,
                                onSmoothnessChange = onSmoothnessChange,
                                onDragStart = onGradientDragStart,
                                onDragFinish = onGradientDragFinish
                            )
                            if (selectedGradientStopUUID != null) {
                                ColorControls(
                                    color = selectedColor,
                                    onColorChange = onColorChange
                                )
                            }
                        }
                    }
                }
            }
        }

        // Transforms section
        PianoRollTransformPanel(
            enabled = enabled,
            hasMultipleSelection = hasMultipleSelection,
            onApplyTransform = onApplyTransform,
            onGradientSpread = onGradientSpread,
            onRandomizeColors = onRandomizeColors
        )
    }
}
