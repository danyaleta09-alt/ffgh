package com.letify.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * "Container transform" reveal: morphs a small button (its bounds captured
 * via `Modifier.onGloballyPositioned { … boundsInRoot() }` at the call site)
 * into a full-screen surface, and back again on the way out.
 *
 * The trick is a single fixed-size (screen-sized) layer that we visually
 * shrink down to the button's rect with `graphicsLayer { scaleX/Y, translationX/Y }`
 * pinned at `TransformOrigin(0f, 0f)` — so at progress = 0 it exactly overlaps
 * the button (same position + size + corner radius) and at progress = 1 it's
 * the whole screen with square corners.
 *
 * Two things used to go wrong here, both from the same root cause: the
 * button isn't square (a 56dp circular FAB is, but the Profile quick-action
 * tile is a ~110×62dp rounded rect with a flat 22dp radius), so `scaleX` and
 * `scaleY` are DIFFERENT throughout the animation. The old code scaled a
 * single corner-radius value by the layer's transform the same way on both
 * axes — under non-uniform scale that turns a round corner into an ellipse
 * (and, since the radius was also guessed as `min(screenW, screenH) / 2`
 * instead of the button's real radius, the shape didn't match the button at
 * all at progress = 0): that's the "скругления теряются" / "обрезания" bug.
 * The fix is to compute the ACTUAL on-screen radius we want at this progress
 * (`targetPx`, in real pixels) and divide it back by scaleX/scaleY
 * SEPARATELY before handing it to the shape, so after the graphicsLayer's
 * non-uniform scale is applied it lands back on the same round value on both
 * axes — via [AsymmetricRoundRectShape], which (like RoundedCornerShape)
 * still resolves to a hardware-clipped `Outline.Rounded`, just with
 * independent x/y radii, so this costs nothing extra per frame.
 *
 * [content] (the camera screen) is composed at all times but only fades in
 * once the shape is big enough to actually show anything meaningful — a tiny
 * squashed camera preview at progress ≈ 0 would just look like a broken pixel.
 * The placeholder behind it is plain black — matching CameraCaptureScreen's
 * own root background — instead of the theme's (near-white) text colour,
 * which was painting a full white silhouette while it grew and is what
 * actually read as the "белая вспышка" on open.
 */
@Composable
fun CameraExpandOverlay(
    progress: () -> Float,
    origin: Rect?,
    modifier: Modifier = Modifier,
    originCornerRadius: Dp = 28.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().zIndex(50f)) {
        val density = LocalDensity.current
        val screenWpx = with(density) { maxWidth.toPx() }
        val screenHpx = with(density) { maxHeight.toPx() }
        val fallback = fallbackOrigin(density, screenWpx, screenHpx)
        val start = origin ?: fallback
        val startCornerPx = with(density) { originCornerRadius.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress().coerceIn(0f, 1f)
                    val startW = start.width.coerceAtLeast(1f)
                    val startH = start.height.coerceAtLeast(1f)
                    val sX = lerp(startW / size.width, 1f, p).coerceAtLeast(0.0001f)
                    val sY = lerp(startH / size.height, 1f, p).coerceAtLeast(0.0001f)
                    scaleX = sX
                    scaleY = sY
                    translationX = lerp(start.left, 0f, p)
                    translationY = lerp(start.top, 0f, p)
                    transformOrigin = TransformOrigin(0f, 0f)
                    // Real, final on-screen radius we want at this progress —
                    // the button's own radius at p = 0, shrinking to a
                    // square (0) full-bleed screen at p = 1. Independent of
                    // scale; computed in true screen pixels.
                    val targetPx = lerp(startCornerPx, 0f, p)
                    // Divide back by each axis's own scale factor so that,
                    // once this layer's non-uniform scale is applied, BOTH
                    // axes land on the same `targetPx` — a true circle/round
                    // rect on screen instead of an ellipse.
                    clip = true
                    shape = AsymmetricRoundRectShape(targetPx / sX, targetPx / sY)
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
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

/**
 * A rounded rect whose x- and y-axis corner radii are independent — unlike
 * [androidx.compose.foundation.shape.RoundedCornerShape], which always uses
 * the same radius for both axes. Still resolves to `Outline.Rounded` (a
 * hardware-clipped round rect), not `Outline.Generic` (a software path), so
 * it's exactly as cheap to draw per frame.
 */
private class AsymmetricRoundRectShape(private val radiusX: Float, private val radiusY: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rx = radiusX.coerceAtLeast(0f)
        val ry = radiusY.coerceAtLeast(0f)
        return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(rx, ry)))
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
