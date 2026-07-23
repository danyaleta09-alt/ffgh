package com.letify.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.theme.Letify
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "CameraCapture"
/** Wait for the slide-in overlay animation to finish before binding CameraX. */
private const val OPEN_DELAY_MS = 380L
private const val LENS_FADE_MS = 180

@Composable
fun CameraCaptureScreen(
    onBack: () -> Unit,
    onCaptured: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = LocalAppState.current
    val scope = rememberCoroutineScope()

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasAudio by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasCamera = result[Manifest.permission.CAMERA] == true
        hasAudio = result[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCamera) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    // ── Open sequence: screen first, camera later ──────────────────────
    var readyToBind by remember { mutableStateOf(false) }
    val previewAlpha = remember { Animatable(0f) }
    LaunchedEffect(hasCamera) {
        if (hasCamera) {
            delay(OPEN_DELAY_MS)
            readyToBind = true
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isRecording by remember { mutableStateOf(false) }
    var captureBusy by remember { mutableStateOf(false) }
    var flashAlpha by remember { mutableFloatStateOf(0f) }
    var lastThumb by remember { mutableStateOf<String?>(null) }
    // Exposure compensation index (−range..+range); UI ready, applied on bind.
    var exposureBias by remember { mutableFloatStateOf(0f) }
    // Zoom ratio placeholder for future wide-angle toggle (1f = default).
    var zoomRatio by remember { mutableFloatStateOf(1f) }

    val flashAnim by animateFloatAsState(flashAlpha, tween(100), label = "flash")

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.SD))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var bindGeneration by remember { mutableIntStateOf(0) }

    // Bind only after open delay AND when PreviewView exists.
    DisposableEffect(readyToBind, lensFacing, previewView, lifecycleOwner, bindGeneration) {
        val view = previewView
        if (!readyToBind || view == null || !hasCamera) {
            onDispose { }
        } else {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val future = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            var cancelled = false

            future.addListener({
                if (cancelled) return@addListener
                try {
                    provider = future.get()
                    val preview = Preview.Builder().build().also { p ->
                        p.setSurfaceProvider(view.surfaceProvider)
                    }
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    provider?.unbindAll()
                    val cam = provider?.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture,
                        videoCapture,
                    )
                    boundCamera = cam
                    // Apply pending zoom / exposure if supported
                    cam?.cameraControl?.setZoomRatio(
                        zoomRatio.coerceIn(
                            cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f,
                            cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f,
                        ),
                    )
                    val expState = cam?.cameraInfo?.exposureState
                    if (expState != null && expState.isExposureCompensationSupported) {
                        val range = expState.exposureCompensationRange
                        val idx = (exposureBias * range.upper.coerceAtLeast(1)).toInt()
                            .coerceIn(range.lower, range.upper)
                        cam.cameraControl.setExposureCompensationIndex(idx)
                    }
                    // Fade preview in after bind
                    scope.launch {
                        previewAlpha.snapTo(0f)
                        previewAlpha.animateTo(1f, tween(280))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "bind failed", e)
                }
            }, mainExecutor)

            onDispose {
                cancelled = true
                try { provider?.unbindAll() } catch (_: Exception) {}
                boundCamera = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            cameraExecutor.shutdown()
        }
    }

    fun mediaDir(): File {
        val dir = File(context.filesDir, "media")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    fun takePhoto() {
        if (captureBusy || isRecording || boundCamera == null) return
        captureBusy = true
        flashAlpha = 0.9f
        val file = File(mediaDir(), "IMG_${stamp()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            opts,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    state.addMedia(path = file.absolutePath, isVideo = false, aspectRatio = 3f / 4f)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        flashAlpha = 0f
                        captureBusy = false
                        lastThumb = file.absolutePath
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "photo failed", exception)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        flashAlpha = 0f
                        captureBusy = false
                    }
                }
            },
        )
    }

    fun startVideo() {
        if (isRecording || captureBusy || boundCamera == null) return
        val file = File(mediaDir(), "VID_${stamp()}.mp4")
        val opts = FileOutputOptions.Builder(file).build()
        try {
            activeRecording = videoCapture.output
                .prepareRecording(context, opts)
                .apply { if (hasAudio) withAudioEnabled() }
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> isRecording = true
                        is VideoRecordEvent.Finalize -> {
                            isRecording = false
                            activeRecording = null
                            if (!event.hasError()) {
                                state.addMedia(
                                    path = file.absolutePath,
                                    isVideo = true,
                                    aspectRatio = 3f / 4f,
                                    durationLabel = "видео",
                                )
                                lastThumb = file.absolutePath
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "start video failed", e)
        }
    }

    fun stopVideo() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun flipCamera() {
        if (isRecording || captureBusy) return
        scope.launch {
            // Fade out → switch lens → bind will fade back in
            previewAlpha.animateTo(0f, tween(LENS_FADE_MS))
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            bindGeneration++
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Preview area
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, bottom = 130.dp)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            if (hasCamera) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        }.also { previewView = it }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = previewAlpha.value },
                )
            }

            // Flash
            if (flashAnim > 0.01f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAnim)))
            }

            // REC badge
            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp),
            ) {
                Row(
                    Modifier
                        .background(Color.Red.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).background(Color.White, CircleShape))
                    Text("REC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Loading hint while camera is still binding after open
            if (hasCamera && previewAlpha.value < 0.05f) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Камера…",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (!hasCamera) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Нужен доступ к камере", color = Color.White, style = Letify.typography.titleMedium)
                    Spacer(Modifier.height(14.dp))
                    NoFeedbackButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                            )
                        },
                    ) {
                        Box(
                            Modifier
                                .background(Color.White, RoundedCornerShape(14.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            Text("Разрешить", color = Color.Black, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Top: close only
        NoFeedbackButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 16.dp, top = 12.dp)
                .size(44.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SolarIcon(name = "alt-arrow-left-outline", tint = Color.White, size = 22.dp)
            }
        }

        // Bottom controls row
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 28.dp),
        ) {
            // Last shot thumbnail (bottom-left) — stays on camera after capture
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp),
            ) {
                AnimatedVisibility(
                    visible = lastThumb != null,
                    enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = tween(180)),
                    exit = fadeOut(),
                ) {
                    val path = lastThumb
                    if (path != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(Uri.fromFile(File(path)))
                                .size(160)
                                .crossfade(false)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }

            // Shutter — single solid circle, no outer ring
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .pointerInput(boundCamera) {
                        detectTapGestures(
                            onTap = { takePhoto() },
                            onLongPress = { startVideo() },
                            onPress = {
                                tryAwaitRelease()
                                if (isRecording) stopVideo()
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(if (isRecording) 32.dp else 72.dp)
                        .background(
                            if (isRecording) Color.Red else Color.White,
                            if (isRecording) RoundedCornerShape(8.dp) else CircleShape,
                        ),
                )
            }

            // Flip camera — bottom-right
            NoFeedbackButton(
                onClick = { flipCamera() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(48.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SolarIcon(name = "restart-bold", tint = Color.White, size = 22.dp)
                }
            }
        }

        // Hint
        Text(
            if (isRecording) "Отпусти, чтобы остановить"
            else "Тап — фото · Удерживай — видео",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
}
