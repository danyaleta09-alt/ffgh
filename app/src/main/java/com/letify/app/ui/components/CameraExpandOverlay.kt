package com.letify.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.letify.app.ui.theme.Letify
import kotlin.math.min

/**
 * "Container transform" reveal: morphs a small circular button (its bounds
 * captured via `Modifier.onGloballyPositioned { … boundsInRoot() }` at the
 * call site) into a full-screen surface, and back again on the way out.
 *
 * The trick is a single fixed-size (screen-sized) layer that we visually
 * shrink down to the button's rect with `graphicsLayer { scaleX/Y, translationX/Y }`
 * pinned at `TransformOrigin(0f, 0f)` — so at progress = 0 it exactly overlaps
 * the button (same position + size, fully round corners), and at progress = 1
 * it's the whole screen with square corners. The corner radius is defined in
 * the layer's own *unscaled* space so that, once multiplied by the shrink
 * factor, it renders as a perfect circle at progress = 0.
 *
 * [content] (the camera screen) is composed at all times but only fades in
 * once the shape is big enough to actually show anything meaningful — a tiny
 * squashed camera preview at progress ≈ 0 would just look like a broken pixel.
 */
@Composable
fun CameraExpandOverlay(
    progress: () -> Float,
    origin: Rect?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().zIndex(50f)) {
        val density = LocalDensity.current
        val screenWpx = with(density) { maxWidth.toPx() }
        val screenHpx = with(density) { maxHeight.toPx() }
        val fallback = fallbackOrigin(density, screenWpx, screenHpx)
        val start = origin ?: fallback

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress().coerceIn(0f, 1f)
                    val startW = start.width.coerceAtLeast(1f)
                    val startH = start.height.coerceAtLeast(1f)
                    scaleX = lerp(startW / size.width, 1f, p)
                    scaleY = lerp(startH / size.height, 1f, p)
                    translationX = lerp(start.left, 0f, p)
                    translationY = lerp(start.top, 0f, p)
                    transformOrigin = TransformOrigin(0f, 0f)
                    // Corner radius lives in the layer's own unscaled space —
                    // once multiplied by scaleX above it lands on `startW / 2`
                    // at p = 0 (a perfect circle matching the round button)
                    // and shrinks toward 0 (a flat full-bleed rect) at p = 1.
                    val cornerPx = lerp(min(screenWpx, screenHpx) / 2f, 0f, p)
                    clip = true
                    shape = RoundedCornerShape(with(density) { cornerPx.toDp() })
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Letify.colors.text),
            ) {
                // Reveal the real camera content only once the shape is
                // roughly a third of the way open — avoids showing a
                // squished, unreadable preview while it's still tiny. Read
                // inside graphicsLayer so this only triggers a redraw each
                // frame, not a recomposition of the camera preview below.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = progress().coerceIn(0f, 1f)
                            alpha = ((p - 0.32f) / 0.5f).coerceIn(0f, 1f)
                        },
                ) {
                    content()
                }
            }
        }
    }
}

private fun fallbackOrigin(density: Density, screenWpx: Float, screenHpx: Float): Rect {
    val sizePx = with(density) { 56.dp.toPx() }
    val bottomInsetPx = with(density) { 96.dp.toPx() }
    val cx = screenWpx / 2f
    val top = screenHpx - bottomInsetPx
    return Rect(
        left = cx - sizePx / 2f,
        top = top,
        right = cx + sizePx / 2f,
        bottom = top + sizePx,
    )
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
