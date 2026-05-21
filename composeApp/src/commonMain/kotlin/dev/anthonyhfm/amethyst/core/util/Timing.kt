package dev.anthonyhfm.amethyst.core.util

import kotlinx.serialization.Serializable

@Serializable
sealed interface Timing {
    @Serializable
    data class Duration(val duration: kotlin.time.Duration) : Timing

    @Serializable
    data class Rythm(val timing: RythmTiming) : Timing {
        enum class RythmTiming(val text: String, val factor: Float) {
            _1_128("1/128", 1 / 128f),
            _1_96("1/96", 1 / 96f),
            _1_64("1/64", 1 / 64f),
            _1_48("1/64", 1 / 48f),
            _1_32("1/32", 1 / 32f),
            _1_24("1/24", 1 / 24f),
            _1_16("1/16", 1 / 16f),
            _1_12("1/12", 1 / 12f),
            _1_8("1/8", 1 / 8f),
            _1_6("1/6", 1 / 6f),
            _1_4("1/4", 1 / 4f),
            _1_3("1/3", 1 / 3f),
            _1_2("1/2", 1 / 2f),
            _1_1("1/1", 1f),
            _2_1("2/1", 2f),
            _4_1("4/1", 4f),
        }
    }
}