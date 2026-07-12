package dev.anthonyhfm.amethyst.devices.effects.composition.nodes
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.composition.*
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial
import kotlin.math.floor
object LoopNode:CompositionNodeDefinition { override val type="loop"; override val label="Loop"; override val hasInput=true; override val hasOutput=true; override val pickerCategory=CompositionNodePickerCategory.Time; override val bodyWidth=160.dp; override val bodyHeight=128.dp; override fun defaultState()=LoopNodeState(); override fun acceptsState(state:CompositionNodeState)=state is LoopNodeState
 override fun inputContext(node:CompositionNode,context:EvaluationContext):EvaluationContext { val s=node.state as? LoopNodeState?:return context; val a=s.startProgress.coerceIn(0f,.99f); val b=s.endProgress.coerceIn(a+.01f,1f); val p=context.progress.coerceIn(0f,1f); val phase=if(p>=1f)1f else p*s.repeats.coerceIn(1,16)-floor(p*s.repeats.coerceIn(1,16)); return context.copy(progress=a+(b-a)*phase) }
 @Composable override fun NodeBody(node:CompositionNode,onNodeChange:(CompositionNode)->Unit){val s=node.state as? LoopNodeState?:return; Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.Center){Dial(DialType.Steps((1..16).toList()),s.repeats,{onNodeChange(node.copy(state=s.copy(repeats=it)))},title="Repeats",text=s.repeats.toString(),defaultValue=2)}} }
