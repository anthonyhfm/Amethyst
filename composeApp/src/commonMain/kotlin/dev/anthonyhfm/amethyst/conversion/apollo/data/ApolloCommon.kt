package dev.anthonyhfm.amethyst.conversion.apollo.data

object ApolloCommon {
    val APOLLO_VERSION: Int = 32

    enum class ApolloType {
        Preferences,
        Copyable,
        Project,
        Track,
        Chain,
        Device,
        Launchpad,
        Group,
        Copy,
        Delay,
        Fade,
        Flip,
        Hold,
        KeyFilter,
        Layer,
        Move,
        Multi,
        Output,
        MacroFilter,
        Switch,
        Paint,
        Pattern,
        Preview,
        Rotate,
        Tone,
        Color,
        Frame,
        Length,
        Offset,
        Time,
        Choke,
        ColorFilter,
        Clear,
        LayerFilter,
        Loop,
        Refresh,
        UndoManager
    }
}