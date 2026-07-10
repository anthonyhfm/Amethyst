package dev.anthonyhfm.amethyst.ui.components

sealed interface DialType<out T> {
    data object Continuous : DialType<Float>

    data class Steps<T>(val values: List<T>) : DialType<T> {
        init {
            require(values.isNotEmpty()) { "A stepped dial needs at least one value." }
        }
    }

    data object Knob : DialType<Float>
}
