package dev.anthonyhfm.amethyst.ui.modifier

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import dev.anthonyhfm.amethyst.settings.data.GeneralSettings
import kotlinx.coroutines.delay

@Composable
fun rememberReducedMotion(): Boolean {
    val reducedMotion by GeneralSettings.reducedMotion.flow.collectAsState()
    return reducedMotion
}

@Composable
fun hoverTweenSpec(durationMillis: Int = 100): FiniteAnimationSpec<Dp> =
    if (rememberReducedMotion()) snap() else tween(durationMillis)

@Composable
fun hoverTweenSpecFloat(durationMillis: Int = 100): FiniteAnimationSpec<Float> =
    if (rememberReducedMotion()) snap() else tween(durationMillis)

@Composable
fun hoverRevealEnterTransition(): EnterTransition {
    if (rememberReducedMotion()) return EnterTransition.None
    return fadeIn() + scaleIn()
}

@Composable
fun hoverRevealExitTransition(): ExitTransition {
    if (rememberReducedMotion()) return ExitTransition.None
    return fadeOut() + scaleOut()
}

@Composable
fun rememberDelayedHoverAsState(
    interactionSource: MutableInteractionSource,
): State<Boolean> {
    val isHovered by interactionSource.collectIsHoveredAsState()
    val hoverTimeMs by GeneralSettings.hoverTime.flow.collectAsState()
    val delayedHover = remember { mutableStateOf(false) }

    LaunchedEffect(isHovered, hoverTimeMs) {
        if (!isHovered) {
            delayedHover.value = false
            return@LaunchedEffect
        }
        if (hoverTimeMs <= 0) {
            delayedHover.value = true
            return@LaunchedEffect
        }
        delay(hoverTimeMs.toLong())
        delayedHover.value = isHovered
    }

    return delayedHover
}
