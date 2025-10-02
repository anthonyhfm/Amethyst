/*
 * Copyright 2023, Mohamed Ben Rejeb and the Compose Dnd project contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohamedrejeb.compose.dnd.drag

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.compose.dnd.DragAndDropInfoImpl
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.LocalDragAndDropInfo

@Composable
internal fun <T> DraggedItemShadow(
    state: DragAndDropState<T>,
) {
    val density = LocalDensity.current
    val draggedItemPositionInRoot = remember { mutableStateOf(Offset.Zero) }

    // Ziel-Translation berechnen (rohe Werte)
    val rawX = (state.dragPositionAnimatable.value.x + state.dragPosition.value.x) - draggedItemPositionInRoot.value.x
    val rawY = (state.dragPositionAnimatable.value.y + state.dragPosition.value.y) - draggedItemPositionInRoot.value.y

    // Sanfte Feder-Animation für Translation
    val animatedX by animateFloatAsState(
        targetValue = rawX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.82f), label = "dragX"
    )
    val animatedY by animateFloatAsState(
        targetValue = rawY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.82f), label = "dragY"
    )

    // Scale & Elevation für optisches Herauslösen des Items
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(state.draggedItem) { appeared = state.draggedItem != null }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1.05f else 0.95f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = 0.7f), label = "dragScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "dragAlpha"
    )
    val elevation by animateFloatAsState(
        targetValue = if (appeared) 18f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow), label = "dragElevation"
    )

    Box(
        modifier = Modifier
            .size(with(density) { state.dragSizeAnimatable.value.toDpSize() })
            .onPlaced { draggedItemPositionInRoot.value = it.positionInRoot() }
            .graphicsLayer {
                translationX = animatedX
                translationY = animatedY
                scaleX = scale
                scaleY = scale
                shadowElevation = elevation
                this.alpha = alpha
            }
    ) {
        CompositionLocalProvider(LocalDragAndDropInfo provides DragAndDropInfoImpl(isShadow = true)) {
            // Overlay leichte Tönung hinter Content für „Card“ Effekt
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.04f))
            )
            state.currentDraggableItem?.content?.invoke()
        }
    }
}
