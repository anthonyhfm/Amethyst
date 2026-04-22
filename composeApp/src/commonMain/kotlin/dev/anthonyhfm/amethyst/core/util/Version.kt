package dev.anthonyhfm.amethyst.core.util

import kotlinx.serialization.Serializable

@Serializable
data class Version(
    val major: Int,
    val minor: Int,
    val hotfix: Int
)

val amethystVersion = Version(0, 4, 0)

val Version.displayString: String
    get() = "$major.$minor.$hotfix"
