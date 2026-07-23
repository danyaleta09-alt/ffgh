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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.util.concurrent.Executors

private const val TAG = "CameraCapture"

/**
 * In-app camera with rounded corners.
 *  - tap the shutter → photo
 *  - long-press the shutter → video (until release)
 */
@Composable
fun CameraCaptureScreen(
    onBack: () -> Unit,
    onCaptured: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = LocalAppState.current

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
        if (!hasCamera || !hasAudio) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            )
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD))
            .build()
    }
    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

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
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun takePhoto() {
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
                    onCaptured()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "photo failed", exception)
                }
            },
        )
    }

    fun startVideo() {
        if (isRecording) return
        val file = File(mediaDir(), "VID_${stamp()}.mp4")
        val opts = FileOutputOptions.Builder(file).build()
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
                            onCaptured()
                        } else {
                            Log.e(TAG, "video error ${event.error}")
                        }
                    }
                }
            }
        activeRecording = pending
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
            // Rounded camera preview
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(bottom = 110.dp)
                    .clip(RoundedCornerShape(28.dp)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                        // Bind camera once when the view is created.
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also { p ->
                                p.surfaceProvider = previewView.surfaceProvider
                            }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture,
                                    videoCapture,
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "bind failed", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (isRecording) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "REC",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
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
                    Spacer(Modifier.height(12.dp))
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

        // Close
        NoFeedbackButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, top = 20.dp)
                .size(42.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SolarIcon(name = "alt-arrow-left-outline", tint = Color.White, size = 22.dp)
            }
        }

        // Shutter: tap = photo, long-press = video
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .size(78.dp)
                .pointerInput(hasCamera) {
                    detectTapGestures(
                        onTap = {
                            if (hasCamera && !isRecording) takePhoto()
                        },
                        onLongPress = {
                            if (hasCamera) startVideo()
                        },
                        onPress = {
                            tryAwaitRelease()
                            if (isRecording) stopVideo()
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            // Outer ring
            Box(
                Modifier
                    .size(78.dp)
                    .background(
                        if (isRecording) Color.Red.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.25f),
                        CircleShape,
                    ),
            )
            // Inner button
            Box(
                Modifier
                    .size(if (isRecording) 34.dp else 62.dp)
                    .background(
                        if (isRecording) Color.Red else Color.White,
                        if (isRecording) RoundedCornerShape(8.dp) else CircleShape,
                    ),
            )
        }
    }
}
