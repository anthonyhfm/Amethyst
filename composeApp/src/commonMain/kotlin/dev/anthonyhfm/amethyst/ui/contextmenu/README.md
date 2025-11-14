# Context Menu Implementation Guide

This guide explains how to implement context menus (right-click menus) in Amethyst using the modern `Dropdown` component.

## Overview

Amethyst uses the `io.androidpoet.dropdown` library for context menus. This provides a consistent, modern implementation across the application with proper state management and cleanup.

## Basic Implementation

### 1. Import Required Components

```kotlin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import dev.anthonyhfm.amethyst.ui.modifier.rightClickable
import io.androidpoet.dropdown.Dropdown
import io.androidpoet.dropdown.dropDownMenu
```

### 2. Add State Variables

```kotlin
@Composable
fun MyComponent() {
    val density = LocalDensity.current.density
    var showRightClickMenu by remember { mutableStateOf(false) }
    var rightClickMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    
    // Your component implementation...
}
```

### 3. Create the Context Menu

```kotlin
Dropdown(
    isOpen = showRightClickMenu,
    menu = dropDownMenu {
        item("action1", "Action 1") {
            icon(Icons.Default.ContentCopy)
        }
        
        item("action2", "Action 2") {
            icon(Icons.Default.Delete)
            enabled(someCondition) // Optional: conditionally enable/disable
        }
        
        horizontalDivider() // Optional: add visual separator
        
        item("action3", "Action 3") {
            icon(Icons.Default.Info)
        }
    },
    offset = rightClickMenuOffset,
    onItemSelected = { itemKey ->
        when (itemKey) {
            "action1" -> performAction1()
            "action2" -> performAction2()
            "action3" -> performAction3()
        }
        showRightClickMenu = false
    },
    onDismiss = {
        showRightClickMenu = false
    }
)
```

### 4. Attach Right-Click Handler

```kotlin
Modifier
    .rightClickable {
        rightClickMenuOffset = DpOffset((it.x / density).dp, (it.y / density).dp)
        showRightClickMenu = true
    }
```

## Advanced Features

### Conditional Menu Items

Show menu items only when certain conditions are met:

```kotlin
if (hasItemsInClipboard) {
    item("paste", "Paste") {
        icon(Icons.Default.ContentPaste)
    }
}
```

### Enabled/Disabled States

Disable menu items based on context:

```kotlin
item("delete", "Delete") {
    icon(Icons.Default.Delete)
    enabled(canDelete) // Item will be grayed out when canDelete is false
}
```

### Closing on Drag Start

If your component supports drag-and-drop, close the menu when dragging starts:

```kotlin
LaunchedEffect(dragAndDropState.draggedItem) {
    showRightClickMenu = false
    // Other drag-related logic...
}
```

## Best Practices

1. **Always handle `onDismiss`**: Set `showRightClickMenu = false` to ensure proper cleanup
2. **Always handle `onItemSelected`**: Close the menu after action: `showRightClickMenu = false`
3. **Use meaningful item keys**: The first parameter is the item ID used in `onItemSelected`
4. **Provide icons**: Use Material Icons for visual clarity
5. **Use dividers sparingly**: Group related actions together
6. **Disable don't hide**: Use `enabled()` instead of removing items when they're temporarily unavailable
7. **Calculate offset in density-independent pixels**: Convert mouse coordinates using `LocalDensity`

## Examples

See these files for complete implementation examples:

- `WorkspaceChainEditor.kt` - Chain device context menu
- `ExpandingChainDevicePicker.kt` - Slot paste context menu  
- `GroupChainDevice.kt` - Group item context menu
- `MultiGroupChainDevice.kt` - Multi-group item context menu
- `FramePreviewButton.kt` - Keyframe frame context menu

## Automatic Behaviors

The `Dropdown` component automatically handles:

- **ESC key**: Closes the menu when ESC is pressed
- **Outside clicks**: Closes the menu when clicking outside its bounds
- **Positioning**: Adjusts position to stay within viewport bounds
- **Focus management**: Proper focus handling for accessibility

## Migration from Legacy ContextMenuArea

If migrating from the old `ContextMenuArea`:

1. Remove imports: `ContextMenuArea` and `ContextMenuItem`
2. Add new imports: `Dropdown`, `dropDownMenu`, `rightClickable`
3. Replace `ContextMenuArea` wrapper with state-based approach
4. Convert `ContextMenuItem` list to `dropDownMenu` DSL
5. Add `.rightClickable` modifier to the target element
6. Implement proper state management with `remember` and `mutableStateOf`

### Before (Legacy):
```kotlin
ContextMenuArea(
    items = listOf(
        ContextMenuItem("Copy") { copyAction() },
        ContextMenuItem("Delete") { deleteAction() }
    )
) {
    MyComponent()
}
```

### After (Modern):
```kotlin
var showMenu by remember { mutableStateOf(false) }
var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

Dropdown(
    isOpen = showMenu,
    menu = dropDownMenu {
        item("copy", "Copy") { icon(Icons.Default.ContentCopy) }
        item("delete", "Delete") { icon(Icons.Default.Delete) }
    },
    offset = menuOffset,
    onItemSelected = {
        when (it) {
            "copy" -> copyAction()
            "delete" -> deleteAction()
        }
        showMenu = false
    },
    onDismiss = { showMenu = false }
)

MyComponent(
    modifier = Modifier.rightClickable {
        menuOffset = DpOffset((it.x / density).dp, (it.y / density).dp)
        showMenu = true
    }
)
```

## Troubleshooting

**Menu doesn't close on ESC/outside click:**
- Ensure `onDismiss` sets `showRightClickMenu = false`
- Check that `isOpen` is bound to the state variable

**Menu position is incorrect:**
- Verify density conversion: `DpOffset((it.x / density).dp, (it.y / density).dp)`
- Ensure `LocalDensity.current.density` is captured correctly

**Menu appears behind other content:**
- The `Dropdown` component handles z-index automatically
- Check parent composable stacking order if issues persist
