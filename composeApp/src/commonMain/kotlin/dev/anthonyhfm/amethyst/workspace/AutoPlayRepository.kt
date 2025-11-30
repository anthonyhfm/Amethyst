package dev.anthonyhfm.amethyst.workspace

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import dev.anthonyhfm.amethyst.core.engine.heaven.Heaven

object AutoPlayRepository {
    fun startAutoPlay() {
        val autoplay = WorkspaceRepository.saveableWorkspaceData?.autoPlay ?: return

        autoplay.actions.entries.forEach {
            Heaven.schedule(it.key, this) {
                WorkspaceRepository.samplingChain.signalEnter(
                    it.value.map {
                        Signal.Midi(
                            origin = this,
                            x = it.x,
                            y = it.y,
                            velocity = if (it.down) 127 else 0,
                        )
                    }
                )

                WorkspaceRepository.lightsChain.signalEnter(
                    it.value.map {
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