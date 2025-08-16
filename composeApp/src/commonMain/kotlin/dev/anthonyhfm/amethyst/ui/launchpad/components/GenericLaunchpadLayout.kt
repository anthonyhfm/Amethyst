package dev.anthonyhfm.amethyst.ui.launchpad.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun GenericLaunchpadLayout(
    modifier: Modifier = Modifier,
    layoutType: LaunchpadLayout = LaunchpadLayout.LAYOUT_10X10,
    buttonContent: @Composable (x: Int, y: Int) -> Unit
) {
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            for (row in (layoutType.rows - 1) downTo 0) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    for (col in 0 until layoutType.cols) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1 / 1f)
                        ) {
                            buttonContent(col + layoutType.offsetX, row + layoutType.offsetY)
                        }
                    }
                }
            }
        }
    }
}
