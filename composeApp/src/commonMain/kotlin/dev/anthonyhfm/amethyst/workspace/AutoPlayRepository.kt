package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven

object AutoPlayRepository {
    fun startAutoPlay() {
        val autoplay = WorkspaceRepository.saveableWorkspaceData?.autoPlay ?: return

        autoplay.actions.entries
            .sortedBy { it.key }
            .take(10).forEach { map ->
                map.value.forEach {
                    println("$${map.key}: ${it.x}, ${it.y}, ${if (it.down) "down" else "up"}")
                }
            }

        autoplay.actions.entries.forEach {
            Heaven.schedule(it.key, this) {
                Heaven.midiEnter(
                    signals = it.value.map {
                        Signal.LED(
                            origin = this,
                            x = it.x,
                            y = it.y,
                            color = if (it.down) Color.White else Color.Black,
                        )
                    }
                )
            }
        }
    }
}