package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.timeline.utils.GridUtils

fun gridTypeToNetworkKey(gridType: GridUtils.GridType): String = when (gridType) {
    GridUtils.GridType.None              -> "None"
    GridUtils.GridType.NoGrid            -> "NoGrid"
    GridUtils.GridType.Flexible.Smallest -> "Flexible.Smallest"
    GridUtils.GridType.Flexible.Small    -> "Flexible.Small"
    GridUtils.GridType.Flexible.Medium   -> "Flexible.Medium"
    GridUtils.GridType.Flexible.Large    -> "Flexible.Large"
    GridUtils.GridType.Flexible.Largest  -> "Flexible.Largest"
    GridUtils.GridType.Fixed.Bar_1       -> "Fixed.Bar_1"
    GridUtils.GridType.Fixed.Bar_2       -> "Fixed.Bar_2"
    GridUtils.GridType.Fixed.Bar_4       -> "Fixed.Bar_4"
    GridUtils.GridType.Fixed.Bar_8       -> "Fixed.Bar_8"
    GridUtils.GridType.Fixed._1_2        -> "Fixed._1_2"
    GridUtils.GridType.Fixed._1_4        -> "Fixed._1_4"
    GridUtils.GridType.Fixed._1_8        -> "Fixed._1_8"
    GridUtils.GridType.Fixed._1_16       -> "Fixed._1_16"
    GridUtils.GridType.Fixed._1_32       -> "Fixed._1_32"
}

fun gridTypeFromNetworkKey(key: String): GridUtils.GridType = when (key) {
    "None"              -> GridUtils.GridType.None
    "NoGrid"            -> GridUtils.GridType.NoGrid
    "Flexible.Smallest" -> GridUtils.GridType.Flexible.Smallest
    "Flexible.Small"    -> GridUtils.GridType.Flexible.Small
    "Flexible.Medium"   -> GridUtils.GridType.Flexible.Medium
    "Flexible.Large"    -> GridUtils.GridType.Flexible.Large
    "Flexible.Largest"  -> GridUtils.GridType.Flexible.Largest
    "Fixed.Bar_1"       -> GridUtils.GridType.Fixed.Bar_1
    "Fixed.Bar_2"       -> GridUtils.GridType.Fixed.Bar_2
    "Fixed.Bar_4"       -> GridUtils.GridType.Fixed.Bar_4
    "Fixed.Bar_8"       -> GridUtils.GridType.Fixed.Bar_8
    "Fixed._1_2"        -> GridUtils.GridType.Fixed._1_2
    "Fixed._1_4"        -> GridUtils.GridType.Fixed._1_4
    "Fixed._1_8"        -> GridUtils.GridType.Fixed._1_8
    "Fixed._1_16"       -> GridUtils.GridType.Fixed._1_16
    "Fixed._1_32"       -> GridUtils.GridType.Fixed._1_32
    else -> {
        println("WorkspaceSync: Unknown GridType key '$key', falling back to Flexible.Medium")
        GridUtils.GridType.Flexible.Medium
    }
}
