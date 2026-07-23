package com.letify.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.components.SettingsHeader
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.state.MediaItem
import com.letify.app.ui.theme.Letify

/**
 * Pinterest-style media gallery — LazyVerticalStaggeredGrid for smooth scroll.
 * FAB opens the in-app camera.
 */
@Composable
fun MediaScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
) {
    val state = LocalAppState.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { state.reloadMedia(context.filesDir) }
    val items = state.mediaItems

    // Stable aspect ratios so cells don't jump while Coil loads.
    val ratios = remember(items.size) {
        // Cycle a few pleasing ratios for visual variety when unknown.
        val pool = floatArrayOf(0.75f, 1f, 0.8f, 0.66f, 1.15f)
        items.mapIndexed { i, item ->
            if (item.aspectRatio > 0.2f) item.aspectRatio
            else pool[i % pool.size]
        }
    }

    Box(Modifier.fillMaxSize().background(Letify.colors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 6.dp),
        ) {
            SettingsHeader(title = "Медиа", onBack = onBack)

            if (items.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .background(Letify.colors.container, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            SolarIcon(
                                name = "widget-bold-duotone",
                                tint = Letify.colors.muted,
                                size = 28.dp,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Пока пусто",
                            color = Letify.colors.text,
                            style = Letify.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Нажми + чтобы сделать фото\nили удерживай для видео",
                            color = Letify.colors.muted,
                            style = Letify.typography.bodySmall,
                            lineHeight = 18.sp,
                        )
                    }
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 4.dp,
                        bottom = 110.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                ) {
                    items(
                        items = items,
                        key = { it.id },
                    ) { item ->
                        val ratio = item.aspectRatio.coerceIn(0.45f, 1.6f)
                        MediaCard(item = item, aspectRatio = ratio)
                    }
                }
            }
        }

        // FAB
        NoFeedbackButton(
            onClick = onOpenCamera,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp)
                .size(56.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Letify.colors.text, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SolarIcon(
                    name = "add-bold",
                    tint = Letify.colors.bg,
                    size = 26.dp,
                )
            }
        }
    }
}

@Composable
private fun MediaCard(item: MediaItem, aspectRatio: Float) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(Letify.colors.container),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(Uri.parse(item.uri))
                .crossfade(180)
                .size(600) // decode downscaled — kills scroll jank
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Letify.colors.text.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    item.durationLabel.ifEmpty { "видео" },
                    color = Letify.colors.bg,
                    style = Letify.typography.bodySmall,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
