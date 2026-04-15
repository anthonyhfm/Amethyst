package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.Icon
import com.composeunstyled.UnstyledButton
import com.composeunstyled.theme.Theme
import dev.anthonyhfm.amethyst.ui.theme.*
import kotlinx.coroutines.launch

private val LocalCarouselPagerState = compositionLocalOf<PagerState> {
    error("CarouselPagerState not provided. Use Carousel as a parent.")
}

private val LocalCarouselPageCount = compositionLocalOf<Int> {
    error("CarouselPageCount not provided. Use Carousel as a parent.")
}

@Composable
fun Carousel(
    pageCount: Int,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    content: @Composable () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    CompositionLocalProvider(
        LocalCarouselPagerState provides pagerState,
        LocalCarouselPageCount provides pageCount,
    ) {
        Box(modifier = modifier) {
            content()
        }
    }
}

@Composable
fun CarouselContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable (page: Int) -> Unit,
) {
    val pagerState = LocalCarouselPagerState.current

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
    ) { page ->
        content(page)
    }
}

@Composable
fun CarouselItem(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
    }
}

@Composable
fun CarouselPrevious(
    modifier: Modifier = Modifier,
) {
    val pagerState = LocalCarouselPagerState.current
    val scope = rememberCoroutineScope()
    val canScrollBack = pagerState.currentPage > 0

    CarouselNavButton(
        onClick = {
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        },
        enabled = canScrollBack,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.ChevronLeft,
            contentDescription = "Previous",
            modifier = Modifier.size(16.dp),
            tint = Theme[colors][foreground],
        )
    }
}

@Composable
fun CarouselNext(
    modifier: Modifier = Modifier,
) {
    val pagerState = LocalCarouselPagerState.current
    val pageCount = LocalCarouselPageCount.current
    val scope = rememberCoroutineScope()
    val canScrollForward = pagerState.currentPage < pageCount - 1

    CarouselNavButton(
        onClick = {
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        },
        enabled = canScrollForward,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Next",
            modifier = Modifier.size(16.dp),
            tint = Theme[colors][foreground],
        )
    }
}

@Composable
private fun CarouselNavButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = FullShape

    val bg = if (hovered) Theme[colors][accent] else Theme[colors][background]
    val borderColor = Theme[colors][border]

    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        interactionSource = interactionSource,
        indication = null,
        contentPadding = PaddingValues(8.dp),
        modifier = modifier
            .size(32.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(bg),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
