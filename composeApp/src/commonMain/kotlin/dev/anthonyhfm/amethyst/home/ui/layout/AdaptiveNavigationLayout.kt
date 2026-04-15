package dev.anthonyhfm.amethyst.home.ui.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import dev.anthonyhfm.amethyst.home.ui.components.BottomNavBar
import dev.anthonyhfm.amethyst.home.ui.components.WidescreenNavBar
import dev.anthonyhfm.amethyst.ui.components.primitives.SidebarProvider
import dev.anthonyhfm.amethyst.ui.components.primitives.rememberSidebarState

@Composable
fun AdaptiveNavigationLayout(
    navigator: NavHostController,
    isWidescreen: Boolean,
    content: @Composable () -> Unit,
) {
    if (isWidescreen) {
        val sidebarState = rememberSidebarState(initialOpen = true)

        SidebarProvider(
            state = sidebarState,
            modifier = Modifier.fillMaxSize(),
        ) {
            WidescreenNavBar(navigator)

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
            ) {
                content()
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                BottomNavBar(navigator)
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
            ) {
                content()
            }
        }
    }
}
