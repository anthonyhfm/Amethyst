package dev.anthonyhfm.amethyst.devices.effects.composition.nodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anthonyhfm.amethyst.devices.effects.composition.EvaluationContext
import dev.anthonyhfm.amethyst.devices.effects.composition.GeometryFrame
import dev.anthonyhfm.amethyst.devices.effects.composition.Vec2
import dev.anthonyhfm.amethyst.devices.effects.composition.graph.CompositionNode
import dev.anthonyhfm.amethyst.ui.components.DialType
import dev.anthonyhfm.amethyst.ui.components.primitives.Dial

object MoveNode : CompositionNodeDefinition {
 override val type="move"; override val label="Move"; override val hasInput=true; override val hasOutput=true; override val pickerCategory=CompositionNodePickerCategory.Transform; override val bodyWidth=224.dp; override val bodyHeight=128.dp
 override fun defaultState()=MoveNodeState(); override fun acceptsState(state: CompositionNodeState)=state is MoveNodeState
 override fun transformFrames(node: CompositionNode, context: EvaluationContext, inputFrames: List<GeometryFrame>): List<GeometryFrame> { val s=node.state as? MoveNodeState?:return inputFrames; return inputFrames.map { f->f.copy(strokes=f.strokes.map { it.copy(points=it.points.map { p->Vec2(p.x+s.offsetX,p.y+s.offsetY) }) }) } }
 @Composable override fun NodeBody(node: CompositionNode,onNodeChange:(CompositionNode)->Unit) { val s=node.state as? MoveNodeState?:return; Row(Modifier.fillMaxSize(), Arrangement.SpaceEvenly, Alignment.CenterVertically) { Dial(DialType.Steps((-64..64).toList()),s.offsetX,{ onNodeChange(node.copy(state=s.copy(offsetX=it))) },title="X",text=s.offsetX.toString(),defaultValue=0); Dial(DialType.Steps((-64..64).toList()),s.offsetY,{ onNodeChange(node.copy(state=s.copy(offsetY=it))) },title="Y",text=s.offsetY.toString(),defaultValue=0) } }
}
