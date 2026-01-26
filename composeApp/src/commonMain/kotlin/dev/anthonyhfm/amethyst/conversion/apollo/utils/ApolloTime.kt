package dev.anthonyhfm.amethyst.conversion.apollo.utils

import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloModel
import dev.anthonyhfm.amethyst.core.util.Timing
import kotlin.time.Duration.Companion.milliseconds

fun ApolloModel.Time.toTiming(): Timing {
    return if (free) {
        Timing.Rythm(
            timing = listOf(
                Timing.Rythm.RythmTiming._1_128,
                Timing.Rythm.RythmTiming._1_64,
                Timing.Rythm.RythmTiming._1_32,
                Timing.Rythm.RythmTiming._1_16,
                Timing.Rythm.RythmTiming._1_8,
                Timing.Rythm.RythmTiming._1_4,
                Timing.Rythm.RythmTiming._1_2,
                Timing.Rythm.RythmTiming._1_1,
                Timing.Rythm.RythmTiming._2_1,
                Timing.Rythm.RythmTiming._4_1,
            ).getOrElse(length.step) {
                Timing.Rythm.RythmTiming._1_4
            }
        )
    } else {
        Timing.Duration(divisor.milliseconds)
    }
}