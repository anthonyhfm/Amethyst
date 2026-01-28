package dev.anthonyhfm.amethyst.conversion.apollo.data


sealed interface ApolloModel {
    data class Project(
        val bpm: Int,
        val macros: List<Int>,
        val tracks: List<Track>,
        val author: String,
        val timeSpentSeconds: Long,
        val startedUnixSeconds: Long,
        val undoManager: UndoManager
    ) : ApolloModel

    data class Track(
        val chain: Chain,
        val launchpad: Launchpad,
        val name: String,
        val enabled: Boolean
    ) : ApolloModel

    data class Launchpad(
        val name: String,
        val format: Int, // Midi Input Format
        val rotation: Int
    ) : ApolloModel

    data class Chain(
        val devices: List<DeviceWrapper>,
        val name: String,
        val enabled: Boolean,
        val secretMultiFilter: List<Boolean> // 101 items
    ) : ApolloModel

    data class DeviceWrapper(
        val collapsed: Boolean,
        val enabled: Boolean,
        val device: Device
    ) : ApolloModel

    data class UndoManager(
        val version: Int,
        val size: Int
    ) : ApolloModel

    data class Color(
        val r: Byte,
        val g: Byte,
        val b: Byte
    ) : ApolloModel

    data class Length(
        val step: Int
    ) : ApolloModel

    data class Offset(
        val x: Int,
        val y: Int,
        val isAbsolute: Boolean,
        val absoluteX: Int,
        val absoluteY: Int,
        val angle: Int
    ) : ApolloModel

    data class Time(
        val free: Boolean,
        val length: Length,
        val divisor: Int
    ) : ApolloModel

    data class Frame(
        val time: Time,
        val colors: List<Color> // 101 items
    ) : ApolloModel

    sealed interface Device : ApolloModel {
        data class Group(
            val chains: List<Chain>,
            val expanded: Boolean,
            val expandedIndex: Int
        ) : Device

        data class Choke(
            val target: Int,
            val chain: Chain
        ) : Device

        data class KeyFilter(
            val filters: List<Boolean>
        ) : Device

        data class Delay(
            val time: Time,
            val gate: Double
        ) : Device

        data class Loop(
            val time: Time,
            val gate: Double,
            val repeats: Int,
            val hold: Boolean
        ) : Device

        data class Copy(
            val time: Time,
            val gate: Double,
            val pinch: Double,
            val bilateral: Boolean,
            val reverse: Boolean,
            val infinite: Boolean,
            val mode: Int,
            val gridMode: Int,
            val wrap: Boolean,
            val offsets: List<Offset>
        ) : Device

        data class Hold(
            val time: Time,
            val gate: Double,
            val mode: Int,
            val release: Boolean
        ) : Device

        data class Paint(
            val color: Color
        ) : Device

        data class Fade(
            val time: Time,
            val gate: Double,
            val playMode: Int,
            val count: Int,
            val colors: List<Color>,
            val positions: List<Double>,
            val fadeTypes: List<Int>,
            val hasExpanded: Boolean,
            val expandedIndex: Int
        ) : Device

        data class Pattern(
            val repeats: Int,
            val gate: Double,
            val pinch: Double,
            val bilateral: Boolean,
            val frames: List<Frame>,
            val playbackMode: Int,
            val infinite: Boolean,
            val rootKey: Int?,
            val wrap: Boolean,
            val expandedIndex: Int
        ) : Device

        data object Preview : Device

        data class Switch(
            val target: Int,
            val value: Int
        ) : Device
    }
}