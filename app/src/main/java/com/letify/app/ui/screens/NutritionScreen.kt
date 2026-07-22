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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
 * Soft rounded glass — continuous U-curve silhouette (no sharp corners),
 * flat fill, no outline / wave / highlight.
 */
@Composable
private fun WaterGlass(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val sanitized = progress.coerceIn(0f, 1.2f)
    val anim = remember { Animatable(sanitized) }
    LaunchedEffect(sanitized) {
        anim.animateTo(sanitized, animationSpec = tween(durationMillis = 600))
    }
    val fill = anim.value.coerceIn(0f, 1f)
    val water = LetifyColors.Water
    val empty = water.copy(alpha = 0.12f)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Fully rounded tumbler: top is a soft capsule lip, bottom is a deep
        // continuous curve — no hard corners.
        val topInset = w * 0.14f
        val bottomInset = w * 0.20f
        val topY = h * 0.05f
        val bottomY = h * 0.95f
        val topR = (w - topInset * 2f) * 0.18f
        val bottomR = (w - bottomInset * 2f) * 0.42f

        val glassPath = Path().apply {
            // Top-left rounded corner → top edge → top-right
            moveTo(topInset, topY + topR)
            quadraticBezierTo(topInset, topY, topInset + topR, topY)
            lineTo(w - topInset - topR, topY)
            quadraticBezierTo(w - topInset, topY, w - topInset, topY + topR)
            // Right side down into deep bottom curve
            lineTo(w - bottomInset, bottomY - bottomR)
            quadraticBezierTo(w - bottomInset, bottomY, w / 2f, bottomY)
            quadraticBezierTo(bottomInset, bottomY, bottomInset, bottomY - bottomR)
            // Left side up
            lineTo(topInset, topY + topR)
            close()
        }

        drawPath(path = glassPath, color = empty)

        if (fill > 0.001f) {
            val waterTop = bottomY - (bottomY - topY) * fill
            clipPath(glassPath) {
                drawRect(
                    color = water,
                    topLeft = Offset(0f, waterTop),
                    size = Size(w, bottomY - waterTop + 1f),
                )
            }
        }
    }
}

/**
 * Telegram-style amount slider:
 *  - thick fully-rounded track
 *  - filled pill grows from the left
 *  - white knob sits *inside* the track
 *  - bubble follows the knob
 *
 * Visual fraction tracks the finger continuously (smooth). Displayed /
 * committed value snaps to [stepMl] so adds stay on clean 50 мл steps.
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

    // Continuous visual position (0..1) — follows the finger pixel-perfect.
    var fraction by remember {
        mutableFloatStateOf(
            ((valueMl - minMl).toFloat() / (maxMl - minMl).toFloat()).coerceIn(0f, 1f),
        )
    }

    // When parent resets value (after add), glide the knob back.
    LaunchedEffect(valueMl) {
        val expected = ((valueMl - minMl).toFloat() / (maxMl - minMl).toFloat()).coerceIn(0f, 1f)
        if (kotlin.math.abs(expected - fraction) > 0.002f) {
            fraction = expected
        }
    }

    fun snapMl(raw: Float): Int {
        val stepped = ((raw - minMl) / stepMl).roundToInt() * stepMl + minMl
        return stepped.coerceIn(minMl, maxMl)
    }

    fun applyFraction(f: Float) {
        val clamped = f.coerceIn(0f, 1f)
        fraction = clamped
        val raw = minMl + clamped * (maxMl - minMl)
        onValueChange(snapMl(raw))
    }

    val trackH = 28.dp
    val knobSize = 22.dp
    // Horizontal inset so the knob stays fully inside the rounded track ends.
    val knobInsetPx = with(density) { ((trackH - knobSize) / 2f).toPx() }

    Column(modifier.fillMaxWidth()) {
        // Value bubble — speech-bubble style, sits above the knob
        Box(Modifier.fillMaxWidth().height(40.dp)) {
            if (trackWidthPx > 0f) {
                val travel = (trackWidthPx - knobInsetPx * 2f).coerceAtLeast(1f)
                val knobCenterX = with(density) {
                    (knobInsetPx + fraction * travel).toDp()
                }
                // Centre the bubble over the knob, clamp to track bounds.
                val bubbleW = 64.dp
                val maxX = with(density) { trackWidthPx.toDp() }
                val bubbleX = (knobCenterX - bubbleW / 2)
                    .coerceIn(0.dp, (maxX - bubbleW).coerceAtLeast(0.dp))
                Box(
                    Modifier
                        .offset { IntOffset(bubbleX.roundToPx(), 0) }
                        .background(LetifyColors.Water, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
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
                            if (trackWidthPx > 0f) {
                                val travel = (trackWidthPx - knobInsetPx * 2f).coerceAtLeast(1f)
                                applyFraction((offset.x - knobInsetPx) / travel)
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            if (trackWidthPx > 0f) {
                                val travel = (trackWidthPx - knobInsetPx * 2f).coerceAtLeast(1f)
                                applyFraction((change.position.x - knobInsetPx) / travel)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // Full track — thick rounded pill (Telegram style)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackH)
                    .background(
                        Letify.colors.container,
                        RoundedCornerShape(999.dp),
                    ),
            )
            // Filled portion — same height, rounded, grows from the left.
            // Minimum width keeps the left cap + knob visible at the low end.
            val fillFraction = fraction.coerceIn(0f, 1f)
            val minFillFrac = if (trackWidthPx > 0f) {
                (knobInsetPx * 2f + with(density) { knobSize.toPx() }) / trackWidthPx
            } else 0.08f
            Box(
                Modifier
                    .fillMaxWidth(fillFraction.coerceAtLeast(minFillFrac * 0.5f).coerceIn(0.02f, 1f))
                    .height(trackH)
                    .background(LetifyColors.Water, RoundedCornerShape(999.dp)),
            )
            // White knob sitting inside the track
            Box(
                Modifier
                    .offset {
                        val travel = (trackWidthPx - knobInsetPx * 2f).coerceAtLeast(1f)
                        val x = knobInsetPx + fraction * travel - with(density) { (knobSize / 2).toPx() }
                        IntOffset(x.roundToInt(), 0)
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
