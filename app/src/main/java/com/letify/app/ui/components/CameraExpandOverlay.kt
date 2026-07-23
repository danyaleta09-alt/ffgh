package com.letify.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.max
import kotlin.math.min

/**
 * "Container transform" reveal: morphs a small button (bounds captured via
 * `Modifier.onGloballyPositioned { … boundsInRoot() }`) into a full-screen
 * camera surface, and back again on close.
 *
 * Fixes vs previous implementation:
 *  - **Uniform scale** (no scaleX ≠ scaleY). Non-uniform scale turned the
 *    RoundedCornerShape into a distorted ellipse and produced the visible
 *    "lost rounding / clipping" artefacts on intermediate frames.
 *  - **Centre-based transform**. The geometric centre of the expanding
 *    circle travels in a straight line from the button centre → screen
 *    centre; TransformOrigin(0.5, 0.5) keeps the math simple and stable.
 *  - **Black fill** instead of `Letify.colors.text` (near-white). The white
 *    fill was the source of the bright flash against the dark UI; the
 *    camera itself is already black, so the morph now starts dark and the
 *    preview simply fades in.
 *  - Corner radius is calculated in unscaled space so that *after* the
 *    uniform scale it is exactly `startDiam / 2` at p = 0 (perfect circle
 *    covering the source button) and 0 at p = 1 (full-bleed rect).
 *
 * Content (CameraCaptureScreen) is composed the whole time but its alpha
 * only rises once the shape is large enough to show a usable preview.
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

        // Treat the source as a circle whose diameter covers the real button
        // (works for both the round Media FAB and the squat Profile tile).
        val startDiam = max(start.width, start.height).coerceAtLeast(1f)
        val startCx = start.left + start.width / 2f
        val startCy = start.top + start.height / 2f

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress().coerceIn(0f, 1f)
                    val minScreen = min(size.width, size.height).coerceAtLeast(1f)

                    // Uniform scale: at p=0 the visible side equals startDiam,
                    // at p=1 the layer is full-screen.
                    val startScale = startDiam / minScreen
                    val s = lerp(startScale, 1f, p)
                    scaleX = s
                    scaleY = s

                    // Centre travels from button centre → screen centre.
                    val cx = lerp(startCx, size.width / 2f, p)
                    val cy = lerp(startCy, size.height / 2f, p)
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    translationX = cx - size.width / 2f
                    translationY = cy - size.height / 2f

                    // Unscaled corner radius: after ×s it becomes startDiam/2
                    // at p=0 (perfect circle) and 0 at p=1.
                    val cornerUnscaled = lerp(minScreen / 2f, 0f, p)
                    clip = true
                    shape = RoundedCornerShape(with(density) { cornerUnscaled.toDp() })
                },
        ) {
            // Black matches CameraCaptureScreen root and eliminates the
            // white flash that previously lit up the whole UI.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = progress().coerceIn(0f, 1f)
                            // Slightly earlier reveal so the preview is already
                            // readable while the shape is still expanding.
                            alpha = ((p - 0.18f) / 0.45f).coerceIn(0f, 1f)
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
