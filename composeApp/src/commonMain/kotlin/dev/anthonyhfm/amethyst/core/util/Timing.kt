package dev.anthonyhfm.amethyst.core.util

import kotlinx.serialization.Serializable

@Serializable
sealed interface Timing {
    @Serializable
    data class Duration(val duration: kotlin.time.Duration) : Timing

    @Serializable
    data class Rythm(val timing: RythmTiming) : Timing {
        enum class RythmTiming {
            /**
             * # 1 / 128
             */
            _1_128,
            /**
             * # 1 / 64
             */
            _1_64,
            /**
             * # 1 / 32
             */
            _1_32,
            /**
             * # 1 / 16
             */
            _1_16,
            /**
             * # 1 / 8
             */
            _1_8,
            /**
             * # 1 / 4
             */
            _1_4,
            /**
             * # 1 / 2
             */
            _1_2,
            /**
             * # 1 / 1
             */
            _1_1,
            /**
             * # 2 / 1
             */
            _2_1,
            /**
             * # 4 / 1
             */
            _4_1,
        }
    }
}