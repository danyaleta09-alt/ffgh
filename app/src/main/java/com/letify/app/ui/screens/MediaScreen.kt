package com.letify.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.components.SettingsHeader
import com.letify.app.ui.components.noFeedbackClick
import com.letify.app.ui.components.screenHPad
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.state.MediaItem
import com.letify.app.ui.theme.Letify

/**
 * Pinterest-style media gallery. Two-column masonry of photos / videos
 * the user captured from the in-app camera. FAB (+) opens the camera.
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
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize().background(Letify.colors.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 6.dp, bottom = 100.dp),
        ) {
            SettingsHeader(title = "Медиа", onBack = onBack)

            if (items.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
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
                // Two-column masonry (Pinterest-style), alternating into
                // the shorter column by aspect ratio weight.
                val left = mutableListOf<MediaItem>()
                val right = mutableListOf<MediaItem>()
                var leftH = 0f
                var rightH = 0f
                items.forEach { item ->
                    val h = 1f / item.aspectRatio.coerceAtLeast(0.3f)
                    if (leftH <= rightH) {
                        left += item
                        leftH += h
                    } else {
                        right += item
                        rightH += h
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .screenHPad()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MasonryColumn(Modifier.weight(1f), left)
                    MasonryColumn(Modifier.weight(1f), right)
                }
            }
        }

        // FAB — bottom-right, above the home indicator area
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
private fun MasonryColumn(modifier: Modifier, items: List<MediaItem>) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            MediaCard(item)
        }
    }
}

@Composable
private fun MediaCard(item: MediaItem) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(item.aspectRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(Letify.colors.container)
            .noFeedbackClick(onClick = { /* future: open detail */ }),
    ) {
        AsyncImage(
            model = Uri.parse(item.uri),
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
