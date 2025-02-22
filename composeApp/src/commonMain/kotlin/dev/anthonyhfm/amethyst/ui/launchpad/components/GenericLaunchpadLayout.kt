package dev.anthonyhfm.amethyst.ui.launchpad.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun GenericLaunchpadLayout(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            for (row in 0 until 10) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    for (col in 0 until 10) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1 / 1f)
                                .background(if ((row + col) % 2 == 0) Color.Black else Color.White)
                        )
                    }
                }
            }
        }
    }
}
