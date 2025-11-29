package dev.anthonyhfm.amethyst.core.engine.echo

import dev.anthonyhfm.amethyst.core.engine.elements.Signal
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED

object Echo {
    private val audioSignalQueue = Channel<List<Signal.AudioSignal>>(UNLIMITED)
    private val audioScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    fun audioEnter(signals: List<Signal.AudioSignal>) {
        audioScope.launch {
            audioSignalQueue.send(signals)
            cancel()
        }
        processAudioSignals()
    }

    private fun processAudioSignals() {
        audioScope.launch {
            val signalBatch = mutableListOf<Signal.AudioSignal>()

            while (true) {
                val result = audioSignalQueue.tryReceive()
                if (result.isSuccess) {
                    signalBatch.addAll(result.getOrNull() ?: emptyList())
                } else {
                    break
                }
            }

            if (signalBatch.isNotEmpty()) {
                signalBatch.forEach { signal ->
                    signal.rawData?.let {
                        try {
                            AudioOutput.play(signal)
                        } catch (e: Exception) {
                            println("Audio output error: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun cancel(signalOrigin: Any?) {
        if (signalOrigin == null) return
        try {
            AudioOutput.stopByOrigin(signalOrigin)
        } catch (e: Exception) {
            println("Audio cancel error: ${e.message}")
        }
    }
}
