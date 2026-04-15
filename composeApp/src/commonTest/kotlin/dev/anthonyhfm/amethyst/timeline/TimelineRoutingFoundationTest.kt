package dev.anthonyhfm.amethyst.timeline

import dev.anthonyhfm.amethyst.core.controls.undo.UndoManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

class TimelineRoutingFoundationTest {
    @BeforeTest
    fun setUp() {
        UndoManager.clear()
        TimelineRepository.stop()
        TimelineRepository.loadTracks(emptyList())
    }

    @AfterTest
    fun tearDown() {
        UndoManager.clear()
        TimelineRepository.stop()
        TimelineRepository.loadTracks(emptyList())
    }
}
