package com.letify.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.letify.app.ui.components.NoFeedbackButton
import com.letify.app.ui.icons.SolarIcon
import com.letify.app.ui.state.LocalAppState
import com.letify.app.ui.theme.Letify
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

private const val TAG = "CameraCapture"

/**
 * In-app camera.
 *  - rounded preview
 *  - tap shutter → photo → brief flash → return to gallery
 *  - long-press shutter → video until release
 *  - flip button switches front / back
 */
@Composable
fun CameraCaptureScreen(
    onBack: () -> Unit,
    onCaptured: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = LocalAppState.current
    val onCapturedState = rememberUpdatedState(onCaptured)

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

    // Lens: 0 = back, 1 = front
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isRecording by remember { mutableStateOf(false) }
    var flashAlpha by remember { mutableStateOf(0f) }
    var captureBusy by remember { mutableStateOf(false) }
    var lastSavedThumb by remember { mutableStateOf<String?>(null) }
    var showSavedBadge by remember { mutableStateOf(false) }

    val flashAnim by animateFloatAsState(
        targetValue = flashAlpha,
        animationSpec = tween(120),
        label = "flash",
    )

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

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var bound by remember { mutableStateOf(false) }

    // Bind / rebind when permission, lens, or previewView changes — once.
    DisposableEffect(hasCamera, lensFacing, previewView, lifecycleOwner) {
        val view = previewView
        if (!hasCamera || view == null) {
            onDispose { }
        } else {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val future = ProcessCameraProvider.getInstance(context)
            var provider: ProcessCameraProvider? = null
            future.addListener({
                try {
                    provider = future.get()
                    val preview = Preview.Builder().build().also { p ->
                        p.setSurfaceProvider(view.surfaceProvider)
                    }
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    provider?.unbindAll()
                    provider?.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        imageCapture,
                        videoCapture,
                    )
                    bound = true
                } catch (e: Exception) {
                    Log.e(TAG, "bind failed", e)
                    bound = false
                }
            }, mainExecutor)

            onDispose {
                try {
                    provider?.unbindAll()
                } catch (_: Exception) {
                }
                bound = false
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
        if (captureBusy || isRecording || !bound) return
        captureBusy = true
        // Shutter flash
        flashAlpha = 0.85f

        val file = File(mediaDir(), "IMG_${stamp()}.jpg")
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            opts,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    state.addMedia(
                        path = file.absolutePath,
                        isVideo = false,
                        aspectRatio = 3f / 4f,
                    )
                    lastSavedThumb = file.absolutePath
                    showSavedBadge = true
                    // Return to gallery after a short confirmation beat.
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        flashAlpha = 0f
                        captureBusy = false
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        onCapturedState.value()
                    }, 450)
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
        if (isRecording || captureBusy || !bound) return
        val file = File(mediaDir(), "VID_${stamp()}.mp4")
        val opts = FileOutputOptions.Builder(file).build()
        try {
            val pending = videoCapture.output
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
                                showSavedBadge = true
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    onCapturedState.value()
                                }, 350)
                            } else {
                                Log.e(TAG, "video error ${event.error}")
                            }
                        }
                    }
                }
            activeRecording = pending
        } catch (e: Exception) {
            Log.e(TAG, "start video failed", e)
        }
    }

    fun stopVideo() {
        activeRecording?.stop()
        activeRecording = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasCamera) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp, bottom = 120.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            // PERFORMANCE is smoother for live preview.
                            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        }.also { previewView = it }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Shutter flash overlay
                if (flashAnim > 0.01f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = flashAnim)),
                    )
                }

                AnimatedVisibility(
                    visible = isRecording,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
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
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape),
                        )
                        Text(
                            "REC",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showSavedBadge,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "Сохранено",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Нужен доступ к камере",
                        color = Color.White,
                        style = Letify.typography.titleMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    NoFeedbackButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.RECORD_AUDIO,
                                ),
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

        // Top bar: close + flip
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoFeedbackButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SolarIcon(name = "alt-arrow-left-outline", tint = Color.White, size = 22.dp)
                }
            }

            NoFeedbackButton(
                onClick = {
                    if (!isRecording && !captureBusy) {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    }
                },
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    SolarIcon(name = "restart-bold", tint = Color.White, size = 22.dp)
                }
            }
        }

        // Shutter
        val shutterScale by animateFloatAsState(
            targetValue = if (isRecording) 0.88f else 1f,
            animationSpec = tween(120),
            label = "shutter",
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .size(84.dp)
                .scale(shutterScale)
                .pointerInput(bound) {
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
                    .size(84.dp)
                    .background(
                        if (isRecording) Color.Red.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.22f),
                        CircleShape,
                    ),
            )
            Box(
                Modifier
                    .size(if (isRecording) 36.dp else 66.dp)
                    .background(
                        if (isRecording) Color.Red else Color.White,
                        if (isRecording) RoundedCornerShape(10.dp) else CircleShape,
                    ),
            )
        }

        Text(
            if (isRecording) "Отпусти, чтобы остановить"
            else "Тап — фото · Удерживай — видео",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        )
    }
}
