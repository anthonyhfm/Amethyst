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

    sealed interface Device : ApolloModel {
        data class KeyFilter(
            val filters: List<Boolean>
        ) : Device

        data class Group(
            val chains: List<Chain>,
            val expanded: Boolean,
            val expandedIndex: Int
        ) : Device

        data class Paint(
            val color: Color
        ) : Device
    }
}