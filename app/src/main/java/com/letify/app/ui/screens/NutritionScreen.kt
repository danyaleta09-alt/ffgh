package com.letify.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.components.ScreenHeader
import com.letify.app.ui.components.ScreenScaffold
import com.letify.app.ui.components.SectionTitle
import com.letify.app.ui.components.SegItem
import com.letify.app.ui.components.SegmentedTabs
import com.letify.app.ui.components.StackedRing
import com.letify.app.ui.components.WCard
import com.letify.app.ui.components.noFeedbackClick
import com.letify.app.ui.components.screenHPad
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.theme.Letify
import com.letify.app.ui.theme.LetifyColors
import kotlin.math.roundToInt

@Composable
fun NutritionScreen(onAddMeal: () -> Unit = {}, onWaterHistory: () -> Unit = {}) {
    var tab by remember { mutableStateOf("water") }

    ScreenScaffold(
        pinnedHeader = {
            ScreenHeader(
                title = "питание",
                trailingIcon = "add-bold",
                trailingAccent = true,
                onTrailingClick = onAddMeal,
            )
        }
    ) {
        Box(Modifier.screenHPad()) {
            SegmentedTabs(
                items = listOf(
                    SegItem("water", "Вода", "bottle-bold-duotone"),
                    SegItem("food", "Еда", "apple-bold-duotone"),
                ),
                selected = tab,
                onSelect = { tab = it },
            )
        }
        Box(Modifier.height(12.dp))
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(260)) { it / 12 })
                    .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { -it / 24 })
            },
            label = "nutrition_pane"
        ) { current ->
            if (current == "water") WaterPane(onHistory = onWaterHistory) else FoodPane(onAddMeal = onAddMeal)
        }
    }
}

// ── Water pane ──────────────────────────────────────────────────────────────

@Composable
private fun WaterPane(onHistory: () -> Unit = {}) {
    val state = LocalAppState.current
    // Default amount — reset back here after each successful add.
    val defaultAmountMl = 250
    var amountMl by remember { mutableIntStateOf(defaultAmountMl) }

    Column {
        // Filling glass hero
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                WaterGlass(
                    progress = (state.waterMl.toFloat() / state.waterTarget).coerceIn(0f, 1.15f),
                    modifier = Modifier.size(width = 130.dp, height = 170.dp),
                )
                Box(Modifier.height(10.dp))
                Text(
                    "${state.waterMl}",
                    color = Letify.colors.text,
                    style = Letify.typography.displayLarge,
                )
                Text(
                    "из ${state.waterTarget} мл",
                    color = Letify.colors.muted,
                    style = Letify.typography.bodySmall,
                )
            }
        }

        SectionTitle("Добавить")

        // Amount slider + add button
        Column(
            Modifier.screenHPad(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WaterAmountSlider(
                valueMl = amountMl,
                onValueChange = { amountMl = it },
                minMl = 50,
                maxMl = 1000,
                stepMl = 50,
            )
            NoFeedbackButton(
                onClick = {
                    state.addWater(amountMl, labelFor(amountMl), iconFor(amountMl))
                    // Reset slider so the user can't spam the same amount.
                    amountMl = defaultAmountMl
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(LetifyColors.Water, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Добавить · $amountMl мл",
                        color = Color.White,
                        style = Letify.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        SectionTitle("История")
        WCard(
            modifier = Modifier.screenHPad().noFeedbackClick(onClick = onHistory),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(LetifyColors.Water.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    SolarIcon(name = "calendar-bold-duotone", tint = LetifyColors.Water, size = 20.dp)
                }
                Box(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Вся история", color = Letify.colors.text, style = Letify.typography.titleSmall)
                    Text(
                        "Записи по дням и статистика",
                        color = Letify.colors.muted,
                        style = Letify.typography.bodySmall,
                    )
                }
                SolarIcon(name = "alt-arrow-right-outline", tint = Letify.colors.muted, size = 18.dp)
            }
        }
    }
}

private fun labelFor(ml: Int): String = when {
    ml <= 150 -> "Глоток"
    ml <= 300 -> "Стакан воды"
    ml <= 550 -> "Бутылка"
    else -> "Большая бутылка"
}

private fun iconFor(ml: Int): String = when {
    ml <= 150 -> "waterdrop-outline"
    ml <= 300 -> "cup-paper-bold-duotone"
    else -> "bottle-bold-duotone"
}

/**
 * Theme-aware 3D tumbler. Empty glass is a soft translucent shell (no black
 * mass in light theme). Water has depth gradient + surface ellipse + shine.
 */
@Composable
private fun WaterGlass(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val sanitized = progress.coerceIn(0f, 1.2f)
    val anim = remember { Animatable(sanitized) }
    LaunchedEffect(sanitized) {
        anim.animateTo(sanitized, animationSpec = tween(durationMillis = 650))
    }
    val fill = anim.value.coerceIn(0f, 1f)
    val water = LetifyColors.Water
    val isDark = Letify.colors.isDark

    // Empty-glass shell — never pure black. Light theme gets a cool translucent
    // grey-blue; dark theme a soft elevated surface.
    val shellTop = if (isDark) Color(0xFF2C3340) else Color(0xFFD8E2F0)
    val shellMid = if (isDark) Color(0xFF222833) else Color(0xFFC5D3E6)
    val shellBot = if (isDark) Color(0xFF1A1F28) else Color(0xFFB4C6DC)
    val sideShade = if (isDark) Color.Black.copy(alpha = 0.22f) else Color(0xFF6B849E).copy(alpha = 0.18f)
    val shine = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.55f)
    val rimColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.7f)

    Canvas(modifier) {
        val w = size.width
        val h = size.height

        val topPad = w * 0.11f
        val botPad = w * 0.23f
        val topY = h * 0.07f
        val botY = h * 0.93f
        val rimH = h * 0.05f

        val glassPath = Path().apply {
            moveTo(topPad, topY + rimH)
            quadraticBezierTo(topPad, topY, topPad + rimH, topY)
            lineTo(w - topPad - rimH, topY)
            quadraticBezierTo(w - topPad, topY, w - topPad, topY + rimH)
            lineTo(w - botPad, botY - botPad * 0.55f)
            quadraticBezierTo(w - botPad, botY, w / 2f, botY)
            quadraticBezierTo(botPad, botY, botPad, botY - botPad * 0.55f)
            lineTo(topPad, topY + rimH)
            close()
        }

        // Shell body
        drawPath(
            path = glassPath,
            brush = Brush.verticalGradient(
                colors = listOf(shellTop, shellMid, shellBot),
                startY = topY,
                endY = botY,
            ),
        )

        // Cylindrical side shading
        clipPath(glassPath) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0f to sideShade,
                        0.16f to Color.Transparent,
                        0.84f to Color.Transparent,
                        1f to sideShade,
                    ),
                ),
            )
        }

        // Water
        if (fill > 0.001f) {
            val waterTop = botY - (botY - topY - rimH) * fill
            val surfRy = w * 0.05f
            val t = ((waterTop - topY) / (botY - topY)).coerceIn(0f, 1f)
            val leftAt = topPad + (botPad - topPad) * t
            val rightAt = w - leftAt

            clipPath(glassPath) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            water.copy(alpha = 0.75f),
                            water,
                            Color(0xFF2B7FD4),
                        ),
                        startY = waterTop,
                        endY = botY,
                    ),
                    topLeft = Offset(0f, waterTop),
                    size = Size(w, botY - waterTop + 2f),
                )
                // Surface ellipse
                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.30f else 0.45f),
                            water.copy(alpha = 0.85f),
                        ),
                    ),
                    topLeft = Offset(leftAt, waterTop - surfRy),
                    size = Size(rightAt - leftAt, surfRy * 2f),
                )
            }
        }

        // Specular highlight strip
        clipPath(glassPath) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.10f to Color.Transparent,
                        0.18f to shine,
                        0.24f to shine.copy(alpha = shine.alpha * 0.35f),
                        0.32f to Color.Transparent,
                    ),
                ),
            )
        }

        // Top rim
        drawOval(
            color = rimColor,
            topLeft = Offset(topPad, topY),
            size = Size(w - topPad * 2f, rimH * 1.15f),
        )
        drawOval(
            color = rimColor.copy(alpha = rimColor.alpha * 0.5f),
            topLeft = Offset(topPad + 2f, topY + rimH * 0.3f),
            size = Size(w - topPad * 2f - 4f, rimH * 0.75f),
            style = Stroke(width = 1.5f),
        )
    }
}

/**
 * Telegram-style amount slider.
 *
 *  - fill capsule fully contains the white knob (right edge = knobCenter + endR)
 *  - speech bubble is a single continuous path (no tail seam/gap)
 *  - bubble tilts slightly while dragging ("живая")
 *  - number animates smoothly; committed value snaps to [stepMl]
 */
@Composable
private fun WaterAmountSlider(
    valueMl: Int,
    onValueChange: (Int) -> Unit,
    minMl: Int = 50,
    maxMl: Int = 1000,
    stepMl: Int = 50,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    var fraction by remember {
        mutableFloatStateOf(
            ((valueMl - minMl).toFloat() / (maxMl - minMl).toFloat()).coerceIn(0f, 1f),
        )
    }

    // Tilt of the bubble while dragging (degrees). Springs back to 0 on release.
    val tilt = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }

    val displayAnim = remember { Animatable(valueMl.toFloat()) }
    LaunchedEffect(valueMl) {
        displayAnim.animateTo(valueMl.toFloat(), animationSpec = tween(durationMillis = 100))
    }
    val displayMl = displayAnim.value.roundToInt()

    LaunchedEffect(valueMl) {
        val expected = ((valueMl - minMl).toFloat() / (maxMl - minMl).toFloat()).coerceIn(0f, 1f)
        if (kotlin.math.abs(expected - fraction) > 0.02f) {
            fraction = expected
        }
    }

    // Settle tilt when not dragging
    LaunchedEffect(dragging) {
        if (!dragging) {
            tilt.animateTo(0f, animationSpec = tween(durationMillis = 220))
        }
    }

    fun snapMl(raw: Float): Int {
        val stepped = ((raw - minMl) / stepMl.toFloat()).roundToInt() * stepMl + minMl
        return stepped.coerceIn(minMl, maxMl)
    }

    fun applyFraction(f: Float, fromDrag: Boolean = true) {
        val prev = fraction
        val clamped = f.coerceIn(0f, 1f)
        fraction = clamped
        onValueChange(snapMl(minMl + clamped * (maxMl - minMl)))
        if (fromDrag) {
            // Tilt toward the direction of movement, clamped to ±12°
            val delta = (clamped - prev) * 180f
            val target = (tilt.value * 0.6f + delta).coerceIn(-12f, 12f)
            // Snap tilt immediately during drag for responsiveness
            // (animateTo would lag behind the finger).
        }
    }

    val trackH = 28.dp
    val knobSize = 20.dp
    val endRpx = with(density) { (trackH / 2).toPx() }
    val knobRpx = with(density) { (knobSize / 2).toPx() }

    // Keep last drag delta for tilt via a mutable state updated in gesture
    var lastDelta by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lastDelta, dragging) {
        if (dragging) {
            val target = (lastDelta * 140f).coerceIn(-12f, 12f)
            tilt.snapTo(target)
        }
    }

    Column(modifier.fillMaxWidth()) {
        // Speech bubble — single path with integrated tail (no seam)
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            if (trackWidthPx > 0f) {
                val travel = (trackWidthPx - endRpx * 2f).coerceAtLeast(1f)
                val knobCenterPx = endRpx + fraction * travel
                val bubbleWpx = with(density) { 76.dp.toPx() }
                val bubbleHpx = with(density) { 32.dp.toPx() }
                val tailHpx = with(density) { 8.dp.toPx() }
                val bubbleLeft = (knobCenterPx - bubbleWpx / 2f)
                    .coerceIn(0f, (trackWidthPx - bubbleWpx).coerceAtLeast(0f))
                val tiltDeg = tilt.value

                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .graphicsLayer {
                            rotationZ = tiltDeg
                            // Pivot around the tail tip so the bubble "leans"
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                (knobCenterPx / trackWidthPx).coerceIn(0f, 1f),
                                1f,
                            )
                        },
                ) {
                    val r = 16.dp.toPx()
                    val left = bubbleLeft
                    val right = bubbleLeft + bubbleWpx
                    val top = 2.dp.toPx()
                    val bottom = top + bubbleHpx
                    val tipX = knobCenterPx.coerceIn(left + r + 4.dp.toPx(), right - r - 4.dp.toPx())
                    val tailHalf = 7.dp.toPx()

                    // ONE continuous path: rounded body with the tail replacing
                    // a section of the bottom edge — eliminates the AA gap.
                    val path = Path().apply {
                        // Start just after the tail on the bottom edge, go clockwise
                        moveTo(tipX + tailHalf, bottom)
                        // bottom-right corner
                        lineTo(right - r, bottom)
                        quadraticBezierTo(right, bottom, right, bottom - r)
                        // right side → top-right
                        lineTo(right, top + r)
                        quadraticBezierTo(right, top, right - r, top)
                        // top edge → top-left
                        lineTo(left + r, top)
                        quadraticBezierTo(left, top, left, top + r)
                        // left side → bottom-left
                        lineTo(left, bottom - r)
                        quadraticBezierTo(left, bottom, left + r, bottom)
                        // bottom edge to tail
                        lineTo(tipX - tailHalf, bottom)
                        // tail tip
                        lineTo(tipX, bottom + tailHpx)
                        // back to start
                        close()
                    }
                    drawPath(path, color = LetifyColors.Water)
                }

                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                bubbleLeft.roundToInt(),
                                with(density) { 6.dp.roundToPx() },
                            )
                        }
                        .width(with(density) { bubbleWpx.toDp() })
                        .height(with(density) { (bubbleHpx - 2.dp.toPx()).toDp() })
                        .graphicsLayer {
                            rotationZ = tiltDeg
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$displayMl мл",
                        color = Color.White,
                        style = Letify.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(trackH)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(minMl, maxMl, stepMl) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            if (trackWidthPx > 0f) {
                                val travel = (trackWidthPx - endRpx * 2f).coerceAtLeast(1f)
                                val f = ((offset.x - endRpx) / travel).coerceIn(0f, 1f)
                                lastDelta = 0f
                                fraction = f
                                onValueChange(snapMl(minMl + f * (maxMl - minMl)))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (trackWidthPx > 0f) {
                                val travel = (trackWidthPx - endRpx * 2f).coerceAtLeast(1f)
                                val prev = fraction
                                val f = ((change.position.x - endRpx) / travel).coerceIn(0f, 1f)
                                lastDelta = f - prev
                                fraction = f
                                onValueChange(snapMl(minMl + f * (maxMl - minMl)))
                            }
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // Track
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackH)
                    .background(Letify.colors.container, RoundedCornerShape(999.dp)),
            )

            // Fill fully contains the knob: right edge = knobCenter + endR
            // (end-cap of the pill), same geometry as Telegram stars slider.
            val fillWidthPx = if (trackWidthPx > 0f) {
                val travel = (trackWidthPx - endRpx * 2f).coerceAtLeast(1f)
                val knobCenter = endRpx + fraction * travel
                (knobCenter + endRpx).coerceIn(endRpx * 2f, trackWidthPx)
            } else 0f
            if (fillWidthPx > 0f) {
                Box(
                    Modifier
                        .width(with(density) { fillWidthPx.toDp() })
                        .height(trackH)
                        .background(LetifyColors.Water, RoundedCornerShape(999.dp)),
                )
            }

            // Knob — fully inside the fill
            Box(
                Modifier
                    .offset {
                        val travel = (trackWidthPx - endRpx * 2f).coerceAtLeast(1f)
                        val knobCenter = endRpx + fraction * travel
                        IntOffset((knobCenter - knobRpx).roundToInt(), 0)
                    }
                    .size(knobSize)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

// ── Food pane ───────────────────────────────────────────────────────────────

@Composable
private fun FoodPane(onAddMeal: () -> Unit = {}) {
    val state = LocalAppState.current
    Column {
        Box(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                val pLen = (state.protein.toFloat() / state.proteinTarget).coerceIn(0f, 1f) * 0.33f
                val fLen = (state.fat.toFloat() / state.fatTarget).coerceIn(0f, 1f) * 0.33f
                val cLen = (state.carb.toFloat() / state.carbTarget).coerceIn(0f, 1f) * 0.33f
                StackedRing(
                    segments = listOf(
                        LetifyColors.Protein to pLen,
                        LetifyColors.Fat to fLen,
                        LetifyColors.Carb to cLen,
                    ),
                    size = 200.dp,
                    strokeWidth = 14.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.kcal.toString(), color = Letify.colors.text, style = Letify.typography.displayLarge)
                    Text(
                        "из ${state.kcalTarget} ккал",
                        color = Letify.colors.muted,
                        style = Letify.typography.bodySmall,
                    )
                }
            }
        }
        Box(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().screenHPad(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MacroRow("Белки", state.protein, state.proteinTarget, "г", LetifyColors.Protein)
            MacroRow("Жиры", state.fat, state.fatTarget, "г", LetifyColors.Fat)
            MacroRow("Углеводы", state.carb, state.carbTarget, "г", LetifyColors.Carb)
        }
        SectionTitle("Сегодня")
        WCard(modifier = Modifier.screenHPad(), contentPadding = PaddingValues(8.dp)) {
            if (state.meals.isEmpty()) {
                EmptyHint("Пока нет приёмов пищи — добавь первый через «+» вверху")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.meals.forEach { meal ->
                        MealRow(
                            meal.title,
                            meal.icon,
                            meal.color,
                            meal.kcal,
                            meal.description ?: "",
                            onAdd = onAddMeal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Letify.colors.muted,
            style = Letify.typography.bodySmall,
        )
    }
}

@Composable
private fun MacroRow(label: String, value: Int, target: Int, unit: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(999.dp)))
        Box(Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value.toString(), color = Letify.colors.text, style = Letify.typography.titleMedium)
                Text(" / $target $unit", color = Letify.colors.muted, style = Letify.typography.bodySmall)
            }
            Text(label, color = Letify.colors.muted, style = Letify.typography.bodySmall)
        }
    }
}

@Composable
private fun MealRow(
    title: String,
    icon: String,
    color: Color,
    kcal: Int?,
    description: String,
    onAdd: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(color.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SolarIcon(name = icon, tint = color, size = 22.dp)
        }
        Box(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Letify.colors.text, style = Letify.typography.titleSmall)
            Text(
                if (kcal != null) "$description · $kcal ккал" else description,
                color = Letify.colors.muted,
                style = Letify.typography.bodySmall,
            )
        }
        NoFeedbackButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
            SolarIcon(name = "add-circle-bold-duotone", tint = Letify.colors.accent, size = 22.dp)
        }
    }
}
