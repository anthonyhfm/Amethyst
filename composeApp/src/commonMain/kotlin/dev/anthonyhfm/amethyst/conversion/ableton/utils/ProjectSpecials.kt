package dev.anthonyhfm.amethyst.conversion.ableton.utils

data class ProjectSpecials(
    /**
     * If kaskobis page switcher was used in the project. It does super weird stuff in ableton
     */
    val kaskobiWeirdAssPageSwitch: Boolean = false,
)