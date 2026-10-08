package dev.anthonyhfm.amethyst.timeline

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import dev.anthonyhfm.amethyst.core.controls.shortcuts.ShortcutManager
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class PianoRollToggleShortcutTest {
    @Test
    fun shiftTabRoutesThroughTimelineAndGlobalFallbackButRespectsTextFocusAndPlainTab() {
        val originalToggle = TimelineKeyHandler.togglePianoRollView
        val originalFocus = WorkspaceRepository.isInputFocused
        var toggles = 0
        try {
            TimelineKeyHandler.togglePianoRollView = {
                toggles++
                true
            }
            WorkspaceRepository.isInputFocused = false
            val shiftTab = KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown, isShiftPressed = true)
            assertTrue(actual = TimelineKeyHandler.handleKeyInput(keyEvent = shiftTab))
            assertTrue(actual = ShortcutManager.handleShortcut(keyEvent = shiftTab))
            assertEquals(expected = 2, actual = toggles)
            val tab = KeyEvent(key = Key.Tab, type = KeyEventType.KeyDown)
            assertFalse(actual = TimelineKeyHandler.handleKeyInput(keyEvent = tab))
            assertFalse(actual = ShortcutManager.handleShortcut(keyEvent = tab))
            WorkspaceRepository.isInputFocused = true
            assertFalse(actual = TimelineKeyHandler.handleKeyInput(keyEvent = shiftTab))
            assertFalse(actual = ShortcutManager.handleShortcut(keyEvent = shiftTab))
            assertEquals(expected = 2, actual = toggles)
        } finally {
            TimelineKeyHandler.togglePianoRollView = originalToggle
            WorkspaceRepository.isInputFocused = originalFocus
        }
    }
}
