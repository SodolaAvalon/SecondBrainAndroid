package com.lifeos.secondbrain.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Signed travel distances for a depth change, in fractions of the container width.
 *
 * Kept as plain numbers, separate from the Compose transitions that consume them, because
 * `EnterTransition` exposes only its target alpha — the direction it travels is not observable from
 * the outside, so this is the part that can actually be asserted in a test.
 *
 * @param overlayFrom signed offset the overlay travels to/from: `+1` when it arrives from the
 *   trailing edge, `-1` when it retreats back to it. The sign flips on return, which is the whole
 *   point — leaving a screen must not replay entering it.
 * @param shellFrom signed offset the shell sits at while an overlay covers it. Constant in both
 *   directions: the shell is displaced once and restored, never sent further away.
 */
internal data class DepthMotion(
    val overlayFrom: Float,
    val shellFrom: Float
) {
    companion object {
        /** Shallow on purpose, and always towards the leading edge: depth, not a second page. */
        const val SHELL_PARALLAX = -0.2f

        fun of(forward: Boolean): DepthMotion = DepthMotion(
            overlayFrom = if (forward) 1f else -1f,
            shellFrom = SHELL_PARALLAX
        )
    }
}

/**
 * Shared motion vocabulary.
 *
 * Every helper takes `reduceMotion` from the app's settings and degrades to `snap()`, because an
 * animation setting that only covers press feedback is worse than no setting at all — motion
 * sensitivity applies most to large travelling transitions, not just to small scale bounces.
 */
object Motion {
    /** Decelerating curve for things arriving on screen: fast start, gentle settle, no overshoot. */
    val Settle: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Slightly stronger deceleration for large surfaces (screens, sheets) travelling further. */
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    const val PAGE_MS = 320
    const val ENTER_MS = 260

    /** Crisp and controlled — used for content that moves a short distance. */
    fun <T> settle(reduceMotion: Boolean, durationMs: Int = ENTER_MS): FiniteAnimationSpec<T> =
        if (reduceMotion) snap() else tween(durationMs, easing = Settle)

    fun pageAlpha(reduceMotion: Boolean): AnimationSpec<Float> =
        if (reduceMotion) snap() else tween(PAGE_MS, easing = Settle)

    /** Gently under-damped: a little life on arrival without looking bouncy or toy-like. */
    fun <T> arrive(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) snap()
        else spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * One-shot entrance: content fades in while sliding up a short distance, then stays put.
 *
 * `key` ties the animation to a stable identity (a file id, a tab) so scrolling a lazy list cannot
 * replay it — an entrance that restarts on every recomposition reads as a glitch, not as polish.
 */
@Composable
fun Modifier.enterMotion(
    key: Any?,
    reduceMotion: Boolean,
    slideFromDp: Float = 14f,
    delayMs: Int = 0
): Modifier {
    if (reduceMotion) return this
    var arrived by remember(key) { mutableStateOf(false) }
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = tween(durationMillis = Motion.ENTER_MS, easing = Motion.Settle),
        label = "enter"
    )
    LaunchedEffect(key) {
        arrived = false
        if (delayMs > 0) delay(delayMs.toLong())
        arrived = true
    }
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * slideFromDp * density
    }
}
/**
 * Staggered sibling entrance. The offset is derived from the item's index within its own section, so
 * each section cascades independently and reopening a tab replays a short, legible cascade.
 */
@Composable
fun Modifier.sectionEnter(
    key: Any?,
    index: Int,
    reduceMotion: Boolean,
    maxStaggerMs: Int = 240,
    stepMs: Int = 45
): Modifier {
    if (reduceMotion) return this
    return enterMotion(
        key = key,
        reduceMotion = false,
        delayMs = (index * stepMs).coerceAtMost(maxStaggerMs)
    )
}
