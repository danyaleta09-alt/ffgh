package com.letify.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

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
 * Water level as a clean vertical capsule — no fake glass rim, no 3D shell.
 * Empty track is a soft wash of the water color; fill rises from the bottom
 * with a gentle curved surface. Works in light and dark themes.
 */
@Composable
private fun WaterGlass(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val sanitized = progress.coerceIn(0f, 1.25f)
    val anim = remember { Animatable(sanitized) }
    LaunchedEffect(sanitized) {
        anim.animateTo(sanitized, animationSpec = tween(durationMillis = 600))
    }
    val fill = anim.value.coerceIn(0f, 1f)
    val water = LetifyColors.Water
    val isDark = Letify.colors.isDark
    val track = if (isDark) water.copy(alpha = 0.12f) else water.copy(alpha = 0.10f)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Tall rounded capsule (stadium)
        val padX = w * 0.22f
        val padY = h * 0.04f
        val left = padX
        val right = w - padX
        val top = padY
        val bot = h - padY
        val radius = (right - left) / 2f

        val vessel = Path().apply {
            addRoundRect(
                RoundRect(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bot,
                    cornerRadius = CornerRadius(radius, radius),
                ),
            )
        }

        // Empty track
        drawPath(vessel, color = track)

        if (fill > 0.001f) {
            val waterTop = bot - (bot - top) * fill
            clipPath(vessel) {
                // Body
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            water.copy(alpha = 0.75f),
                            water,
                            Color(0xFF3A8FE0),
                        ),
                        startY = waterTop,
                        endY = bot,
                    ),
                    topLeft = Offset(left, waterTop),
                    size = Size(right - left, bot - waterTop + 1f),
                )
            }
        }
    }
}

/**
 * Smooth amount slider (Telegram-style).
 *
 * Visual position is a plain Float — updated synchronously on every pointer
 * move, no coroutines during drag (that was the jank source). Soft spring
 * only runs once, on finger-up, to settle on the nearest 50 мл step.
 * Bubble body + overlapping tail (no seam).
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
    var dragging by remember { mutableStateOf(false) }

    fun mlToFrac(ml: Int): Float =
        ((ml - minMl).toFloat() / (maxMl - minMl).toFloat()).coerceIn(0f, 1f)

    fun fracToMl(f: Float): Int {
        val raw = minMl + f.coerceIn(0f, 1f) * (maxMl - minMl)
        val stepped = ((raw - minMl) / stepMl.toFloat()).roundToInt() * stepMl + minMl
        return stepped.coerceIn(minMl, maxMl)
    }

    // Continuous visual 0..1. Mutated directly during drag.
    var fraction by remember { mutableFloatStateOf(mlToFrac(valueMl)) }

    // Soft settle animation (only used on release / external reset).
    val settle = remember { Animatable(mlToFrac(valueMl)) }
    val scope = rememberCoroutineScope()

    // When parent resets amount (after add), glide back.
    LaunchedEffect(valueMl) {
        if (!dragging) {
            val target = mlToFrac(valueMl)
            if (kotlin.math.abs(target - fraction) > 0.001f) {
                settle.snapTo(fraction)
                settle.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 200f))
                fraction = settle.value
            } else {
                fraction = target
            }
        }
    }

    // While settle is running (and not dragging), drive fraction from it.
    val renderFrac = if (dragging) fraction else {
        // Prefer live fraction; settle is applied by LaunchedEffect / onDragEnd.
        fraction
    }

    val trackH = 28.dp
    val knobSize = 20.dp
    val endRpx = with(density) { (trackH / 2).toPx() }
    val knobRpx = with(density) { (knobSize / 2).toPx() }
    val travel = (trackWidthPx - endRpx * 2f).coerceAtLeast(1f)
    val knobCenterPx = if (trackWidthPx > 0f) endRpx + renderFrac * travel else 0f

    Column(modifier.fillMaxWidth()) {
        // Bubble
        Box(Modifier.fillMaxWidth().height(46.dp)) {
            if (trackWidthPx > 0f) {
                val bubbleW = with(density) { 74.dp.toPx() }
                val bubbleH = with(density) { 30.dp.toPx() }
                val tailH = with(density) { 8.dp.toPx() }
                val topPad = with(density) { 4.dp.toPx() }
                val bubbleLeft = (knobCenterPx - bubbleW / 2f)
                    .coerceIn(0f, (trackWidthPx - bubbleW).coerceAtLeast(0f))

                Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                    val r = 15.dp.toPx()
                    val tipX = knobCenterPx.coerceIn(
                        bubbleLeft + r + 4.dp.toPx(),
                        bubbleLeft + bubbleW - r - 4.dp.toPx(),
                    )
                    val half = 7.dp.toPx()
                    val overlap = 2.dp.toPx()
                    val top = topPad
                    val bottom = top + bubbleH

                    drawRoundRect(
                        color = LetifyColors.Water,
                        topLeft = Offset(bubbleLeft, top),
                        size = Size(bubbleW, bubbleH),
                        cornerRadius = CornerRadius(r, r),
                    )
                    val tail = Path().apply {
                        moveTo(tipX - half, bottom - overlap)
                        lineTo(tipX, bottom + tailH)
                        lineTo(tipX + half, bottom - overlap)
                        close()
                    }
                    drawPath(tail, LetifyColors.Water)
                }

                Box(
                    Modifier
                        .offset {
                            IntOffset(bubbleLeft.roundToInt(), topPad.roundToInt())
                        }
                        .width(with(density) { bubbleW.toDp() })
                        .height(with(density) { bubbleH.toDp() }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$valueMl мл",
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
                                val t = (trackWidthPx - endRpx * 2f).coerceAtLeast(1f)
                                val f = ((offset.x - endRpx) / t).coerceIn(0f, 1f)
                                fraction = f
                                onValueChange(fracToMl(f))
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (trackWidthPx > 0f) {
                                val t = (trackWidthPx - endRpx * 2f).coerceAtLeast(1f)
                                val f = ((change.position.x - endRpx) / t).coerceIn(0f, 1f)
                                fraction = f
                                onValueChange(fracToMl(f))
                            }
                        },
                        onDragEnd = {
                            val snapped = fracToMl(fraction)
                            val target = mlToFrac(snapped)
                            onValueChange(snapped)
                            // Soft settle to exact step position
                            scope.launch {
                                settle.snapTo(fraction)
                                settle.animateTo(
                                    target,
                                    spring(dampingRatio = 0.80f, stiffness = 160f),
                                )
                                fraction = settle.value
                                dragging = false
                            }
                        },
                        onDragCancel = {
                            dragging = false
                        },
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
            // Fill — fully covers the knob
            if (trackWidthPx > 0f) {
                val fillW = (knobCenterPx + endRpx).coerceIn(endRpx * 2f, trackWidthPx)
                Box(
                    Modifier
                        .width(with(density) { fillW.toDp() })
                        .height(trackH)
                        .background(LetifyColors.Water, RoundedCornerShape(999.dp)),
                )
            }
            // Knob — sub-pixel via graphicsLayer for smoothness
            if (trackWidthPx > 0f) {
                Box(
                    Modifier
                        .graphicsLayer {
                            translationX = knobCenterPx - knobRpx
                        }
                        .size(knobSize)
                        .background(Color.White, CircleShape),
                )
            }
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
