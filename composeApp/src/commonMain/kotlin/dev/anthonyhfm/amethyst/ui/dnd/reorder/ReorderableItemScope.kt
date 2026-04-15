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
package com.mohamedrejeb.compose.dnd.reorder

import androidx.compose.ui.Modifier
import com.mohamedrejeb.compose.dnd.DragAndDropState
import com.mohamedrejeb.compose.dnd.drag.DraggableItemScopeImpl
import com.mohamedrejeb.compose.dnd.drag.DraggableItemScopeShadowImpl
import com.mohamedrejeb.compose.dnd.drag.DraggableItemState
import com.mohamedrejeb.compose.dnd.drag.DraggableItemScope

interface ReorderableItemScope : DraggableItemScope

internal class ReorderableItemScopeImpl<T>(
    override val key: Any,
    state: DragAndDropState<T>,
    draggableItemState: DraggableItemState<T>,
    enabled: Boolean,
    dragAfterLongPress: Boolean,
    requireFirstDownUnconsumed: Boolean,
) : ReorderableItemScope {
    private val delegate = DraggableItemScopeImpl(
        state = state,
        key = key,
        draggableItemState = draggableItemState,
        enabled = enabled,
        dragAfterLongPress = dragAfterLongPress,
        requireFirstDownUnconsumed = requireFirstDownUnconsumed,
    )

    override val isDragging: Boolean
        get() = delegate.isDragging

    override fun Modifier.dragAnchor(): Modifier {
        return with(delegate) {
            this@dragAnchor.dragAnchor()
        }
    }
}

internal class ReorderableItemScopeShadowImpl(
    override val key: Any,
) : ReorderableItemScope {
    private val delegate = DraggableItemScopeShadowImpl(key)

    override val isDragging: Boolean
        get() = delegate.isDragging

    override fun Modifier.dragAnchor(): Modifier {
        return with(delegate) {
            this@dragAnchor.dragAnchor()
        }
    }
}
