package dev.anthonyhfm.amethyst.core.util

import androidx.compose.ui.graphics.Color
import dev.anthonyhfm.amethyst.core.heaven.elements.RawUpdate

object FastLED {
    fun compress(updates: List<RawUpdate>, factor: Int = 63): ByteArray {
        val mapped: MutableMap<Color, Array<Byte>> = mutableMapOf()

        updates.forEach { update ->
            mapped[update.color] = mapped[update.color]?.plus(update.index) ?: arrayOf(update.index)
        }

        return mutableListOf<Byte>().apply {
            mapped.forEach { entry ->
                // RR GG BB NN format
                addAll(
                    arrayOf(
                        (entry.key.red * factor).toInt().toByte(),
                        (entry.key.green * factor).toInt().toByte(),
                        (entry.key.blue * factor).toInt().toByte(),
                        (entry.value.size).toByte()
                    )
                )

                addAll(entry.value) // <- All following buttons with the color defined before
            }
        }.toByteArray()
    }
}