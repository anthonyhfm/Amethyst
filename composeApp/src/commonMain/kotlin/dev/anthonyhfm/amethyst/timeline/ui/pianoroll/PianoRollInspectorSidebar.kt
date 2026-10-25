package dev.anthonyhfm.amethyst.timeline.ui.pianoroll

import amethyst.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.*
import dev.anthonyhfm.amethyst.ui.components.primitives.Separator
import com.composeunstyled.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.anthonyhfm.amethyst.ui.components.primitives.ScrollArea
import dev.anthonyhfm.amethyst.ui.components.primitives.Tabs
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsContent
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsList
import dev.anthonyhfm.amethyst.ui.components.primitives.TabsTrigger
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberScrollAreaState
import dev.anthonyhfm.amethyst.ui.theme.border
import dev.anthonyhfm.amethyst.ui.theme.colors
import dev.anthonyhfm.amethyst.ui.theme.mutedForeground

@Composable
fun PianoRollInspectorSidebar(
    gradientMode: Boolean,
    selectedColor: Color,
    onSolidColorChange: (Color) -> Unit,
    onGradientStopColorChange: (Color) -> Unit,
    onColorInteractionStart: () -> Unit,
    onColorInteractionFinish: () -> Unit,
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
    selectionCount: Int,
    hasMultipleSelection: Boolean,
    onApplyTransform: ((List<MidiNote>) -> List<MidiNote>, Boolean) -> Unit,
    pinchValue: Float,
    onPinchValueChange: (Float) -> Unit,
    pinchBilateral: Boolean,
    onPinchBilateralChange: (Boolean) -> Unit,
    onGradientSpread: () -> Unit,
    onRandomizeColors: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollAreaState()
    var selectedPaintTab by remember { mutableStateOf(if (gradientMode) "gradient" else "solid") }

    LaunchedEffect(workingGradient, gradientMode) {
        selectedPaintTab = if (gradientMode) "gradient" else "solid"
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
    ) {
        ScrollArea(
            modifier = Modifier.fillMaxSize(),
            state = scrollState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (enabled) {
                    Text(
                        text = stringResource(
                            resource = Res.string.piano_roll_selection_count,
                            formatArgs = arrayOf(selectionCount),
                        ),
                        color = Theme[colors][mutedForeground],
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tabs(
                        selectedTab = selectedPaintTab,
                        tabs = listOf("solid", "gradient"),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TabsList(modifier = Modifier.fillMaxWidth()) {
                            TabsTrigger(
                                key = "solid",
                                selected = selectedPaintTab == "solid",
                                onSelected = {
                                    selectedPaintTab = "solid"
                                    if (gradientMode) {
                                        onSolidTabSelected()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Lucide.Droplet, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(text = stringResource(resource = Res.string.piano_roll_solid))
                                }
                            }
                            TabsTrigger(
                                key = "gradient",
                                selected = selectedPaintTab == "gradient",
                                onSelected = {
                                    selectedPaintTab = "gradient"
                                    if (!gradientMode || workingGradient == null) {
                                        onGradientTabSelected()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Lucide.Blend, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Text(text = stringResource(resource = Res.string.piano_roll_gradient))
                                }
                            }
                        }
                        TabsContent("solid") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorControls(
                                    color = selectedColor,
                                    onColorChange = onSolidColorChange,
                                    onInteractionStart = onColorInteractionStart,
                                    onInteractionFinish = onColorInteractionFinish,
                                )
                            }
                        }
                        TabsContent("gradient") {
                            if (workingGradient != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NoteGradientEditorBar(
                                        selectedStopUUID = selectedGradientStopUUID,
                                        onSelectionChange = onSelectGradientStop,
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
                                            onColorChange = onGradientStopColorChange,
                                            onInteractionStart = onColorInteractionStart,
                                            onInteractionFinish = onColorInteractionFinish,
                                        )
                                    }
                                }
                            } else {
                                Text(if (enabled) "Preparing gradient…" else "Select one or more notes")
                            }
                        }
                    }
                }

                Separator()

                PianoRollTransformPanel(
                    enabled = enabled,
                    hasMultipleSelection = hasMultipleSelection,
                    onApplyTransform = onApplyTransform,
                    pinchValue = pinchValue,
                    onPinchValueChange = onPinchValueChange,
                    pinchBilateral = pinchBilateral,
                    onPinchBilateralChange = onPinchBilateralChange,
                    onGradientSpread = onGradientSpread,
                    onRandomizeColors = onRandomizeColors
                )
            }
        }
    }
}
