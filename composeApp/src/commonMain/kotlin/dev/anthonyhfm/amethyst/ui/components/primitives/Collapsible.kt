package dev.anthonyhfm.amethyst.ui.components.primitives

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.composeunstyled.DisclosureScope
import com.composeunstyled.DisclosureState
import com.composeunstyled.theme.NoIndication
import com.composeunstyled.UnstyledDisclosure
import com.composeunstyled.UnstyledDisclosureHeading
import com.composeunstyled.UnstyledDisclosurePanel
import com.composeunstyled.rememberDisclosureState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Collapsible(
    modifier: Modifier = Modifier,
    state: DisclosureState = rememberDisclosureState(),
    content: @Composable DisclosureScope.() -> Unit,
) {
    UnstyledDisclosure(
        state = state,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun DisclosureScope.CollapsibleTrigger(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    UnstyledDisclosureHeading(
        modifier = modifier,
        indication = NoIndication,
        content = content,
    )
}

@Composable
fun DisclosureScope.CollapsibleContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    UnstyledDisclosurePanel(
        modifier = modifier,
        enter = expandVertically(),
        exit = shrinkVertically(),
        content = content,
    )
}
