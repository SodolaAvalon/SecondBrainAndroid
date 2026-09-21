package com.lifeos.secondbrain.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cheap glass fallback used everywhere by default. It intentionally avoids per-card backdrop blur:
 * translucent material, a directional highlight edge and a restrained shadow provide depth without
 * turning a long list into dozens of live blur passes.
 */
@Composable
fun Modifier.softGlass(
    radius: Dp = 28.dp,
    emphasized: Boolean = false,
    shadow: Dp = if (emphasized) 22.dp else 12.dp
): Modifier {
    val shape: Shape = RoundedCornerShape(radius)
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminanceCompat() < 0.35f
    val top = if (dark) Color.White.copy(alpha = if (emphasized) 0.13f else 0.085f)
    else Color.White.copy(alpha = if (emphasized) 0.84f else 0.68f)
    val bottom = if (dark) colors.surface.copy(alpha = if (emphasized) 0.88f else 0.74f)
    else colors.surface.copy(alpha = if (emphasized) 0.82f else 0.66f)
    val border = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.30f else 0.88f),
            colors.primary.copy(alpha = if (emphasized) 0.18f else 0.07f),
            Color.White.copy(alpha = if (dark) 0.08f else 0.42f)
        )
    )
    return this
        .shadow(
            elevation = shadow,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.22f else 0.07f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.28f else 0.09f)
        )
        .background(Brush.verticalGradient(listOf(top, bottom)), shape)
        .border(BorderStroke(0.8.dp, border), shape)
}

/** Quiet spring press feedback. Motion can be disabled from the app's Reduce Motion setting. */
fun Modifier.springClickable(
    enabled: Boolean = true,
    reduceMotion: Boolean = false,
    onClick: () -> Unit
): Modifier = composed {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val target = if (pressed && enabled && !reduceMotion) 0.975f else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) snap()
        else spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow),
        label = "quiet-press"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        enabled = enabled,
        interactionSource = interactions,
        indication = null,
        onClick = onClick
    )
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    emphasized: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.softGlass(radius = radius, emphasized = emphasized).padding(contentPadding),
        content = content
    )
}

private fun Color.luminanceCompat(): Float {
    fun channel(value: Float): Float = if (value <= 0.03928f) value / 12.92f else
        Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
