package com.metaverse.gallery.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.MediaActionSound
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.metaverse.gallery.ui.theme.Cell
import com.metaverse.gallery.ui.theme.Mono
import com.metaverse.gallery.util.Hap
import com.metaverse.gallery.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The three looks from the design: mint pill on white, ring on black, yellow "+" pill on black. */
private enum class CamStyle(
    val label: String,
    val lightBar: Boolean,
    val accent: Color,
    val circle: Boolean,
    val plus: Boolean
) {
    MINT("MINT", true, Color(0xFF14E58D), false, false),
    RING("RING", false, Color(0xFFF4F4F4), true, false),
    SUN("SUN", false, Color(0xFFFFD60A), false, true)
}

private val Yellow = Color(0xFFFFD60A)
private val PresetsMm = listOf(24, 35, 48, 70)
private const val BASE_MM = 24f

private suspend fun Context.cameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val f = ProcessCameraProvider.getInstance(this)
    f.addListener({ cont.resume(f.get()) }, ContextCompat.getMainExecutor(this))
}

@Composable
fun CameraScreen(lastPhoto: Uri?, onClose: () -> Unit, onOpenGallery: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val view = LocalView.current
    val config = LocalConfiguration.current
    val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    @Suppress("DEPRECATION")
    val rotation = view.display?.rotation ?: Surface.ROTATION_0
    val barOnLeft = landscape && rotation == Surface.ROTATION_270

    BackHandler { onClose() }

    // ---- permissions ----
    val needed = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.toTypedArray()
    }
    fun granted() = needed.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var allowed by remember { mutableStateOf(granted()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = granted()
    }
    LaunchedEffect(Unit) { if (!allowed) permLauncher.launch(needed) }

    // ---- camera state ----
    var front by remember { mutableStateOf(false) }
    var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var styleIdx by rememberSaveable { mutableIntStateOf(0) }
    var gridOn by rememberSaveable { mutableStateOf(true) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(8f) }
    var error by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var focusAt by remember { mutableStateOf<Offset?>(null) }
    var flashAnim by remember { mutableStateOf(false) }
    var thumb by remember { mutableStateOf(lastPhoto) }
    var thumbPop by remember { mutableIntStateOf(0) }
    var videoMode by rememberSaveable { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recSecs by remember { mutableIntStateOf(0) }
    val recHolder = remember { arrayOfNulls<Recording>(1) }
    val videoCapture = remember {
        VideoCapture.withOutput(
            Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
        )
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val style = CamStyle.entries[styleIdx % CamStyle.entries.size]
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val previewView = remember {
        PreviewView(ctx).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val sound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }

    LaunchedEffect(lastPhoto) { if (lastPhoto != null && thumb == null) thumb = lastPhoto }
    LaunchedEffect(flash) { imageCapture.flashMode = flash }
    LaunchedEffect(flash, videoMode, camera) {
        camera?.cameraControl?.enableTorch(videoMode && flash == ImageCapture.FLASH_MODE_ON)
    }
    LaunchedEffect(toast) { if (toast != null) { delay(1100); toast = null } }
    LaunchedEffect(focusAt) { if (focusAt != null) { delay(900); focusAt = null } }

    LaunchedEffect(allowed, front, rotation, videoMode) {
        if (!allowed) return@LaunchedEffect
        try {
            val provider = ctx.cameraProvider()
            val preview = Preview.Builder().build().also {
                it.targetRotation = rotation
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture.targetRotation = rotation
            provider.unbindAll()
            val sel = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val cam = if (videoMode) provider.bindToLifecycle(owner, sel, preview, videoCapture)
                      else provider.bindToLifecycle(owner, sel, preview, imageCapture)
            camera = cam
            val zs = cam.cameraInfo.zoomState.value
            minZoom = zs?.minZoomRatio ?: 1f
            maxZoom = (zs?.maxZoomRatio ?: 8f).coerceAtMost(10f)
            zoom = 1f.coerceIn(minZoom, maxZoom)
            error = null
        } catch (e: Exception) {
            error = "Camera unavailable"
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            sound.release()
            recHolder[0]?.stop()
            try { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } catch (_: Exception) {}
        }
    }

    fun applyZoom(z: Float) {
        val c = z.coerceIn(minZoom, maxZoom)
        zoom = c
        camera?.cameraControl?.setZoomRatio(c)
    }
    val onPinch by rememberUpdatedState { f: Float -> applyZoom(zoom * f) }

    fun cycleZoom() {
        haptic(Hap.CLICK)
        val cur = zoom * BASE_MM
        val next = PresetsMm.firstOrNull { it > cur + 1.5f } ?: PresetsMm.first()
        val target = (next / BASE_MM).coerceIn(minZoom, maxZoom)
        val from = zoom
        scope.launch { animate(from, target, animationSpec = tween(260)) { v, _ -> applyZoom(v) } }
    }

    fun toggleRecording() {
        val active = recording
        if (active != null) { active.stop(); return }
        haptic(Hap.HEAVY)
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Metaverse_$name.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Metaverse")
        }
        val out = MediaStoreOutputOptions.Builder(ctx.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(cv).build()
        var pending = videoCapture.output.prepareRecording(ctx, out)
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            @Suppress("MissingPermission")
            pending = pending.withAudioEnabled()
        }
        recSecs = 0
        val r = pending.start(ContextCompat.getMainExecutor(ctx)) { ev ->
            when (ev) {
                is VideoRecordEvent.Status ->
                    recSecs = (ev.recordingStats.recordedDurationNanos / 1_000_000_000L).toInt()
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    recHolder[0] = null
                    if (!ev.hasError()) {
                        thumb = ev.outputResults.outputUri
                        thumbPop++
                        haptic(Hap.CONFIRM)
                    } else {
                        haptic(Hap.REJECT)
                        toast = "Couldn't save video"
                    }
                }
                else -> {}
            }
        }
        recording = r
        recHolder[0] = r
    }

    fun capture() {
        haptic(Hap.CLICK)
        sound.play(MediaActionSound.SHUTTER_CLICK)
        flashAnim = true
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Metaverse_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Metaverse")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(
            ctx.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv
        ).build()
        imageCapture.takePicture(opts, ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                r.savedUri?.let { thumb = it; thumbPop++ }
                haptic(Hap.CONFIRM)
            }
            override fun onError(e: ImageCaptureException) {
                haptic(Hap.REJECT)
                toast = "Couldn't save photo"
            }
        })
    }

    val flashAlpha by animateFloatAsState(
        if (flashAnim) 0.85f else 0f,
        tween(if (flashAnim) 40 else 240),
        finishedListener = { flashAnim = false }, label = "flash"
    )

    // ---- pieces ----
    val viewfinder: @Composable (Modifier) -> Unit = { m ->
        Box(m.background(Color.Black)) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures { off ->
                            val c = camera ?: return@detectTapGestures
                            val p = previewView.meteringPointFactory.createPoint(off.x, off.y)
                            c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
                            focusAt = off
                            haptic(Hap.TICK)
                        }
                    }
                    .pointerInput(Unit) { detectTransformGestures { _, _, z, _ -> if (abs(z - 1f) > 0.0005f) onPinch(z) } }
            ) {
                AndroidView({ previewView }, Modifier.fillMaxSize())
            }

            if (gridOn) Canvas(Modifier.fillMaxSize()) {
                val c = Color(0x55FFFFFF)
                for (i in 1..2) {
                    drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1.5f)
                    drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1.5f)
                }
            }

            // focal-length pill
            val mm = (zoom * BASE_MM).roundToInt()
            Box(
                Modifier.align(if (landscape) Alignment.CenterStart else Alignment.BottomStart)
                    .padding(start = 26.dp, bottom = if (landscape) 0.dp else 26.dp)
                    .height(38.dp).clip(RoundedCornerShape(19.dp))
                    .background(if (style == CamStyle.MINT) Color(0xCC1C1C1C) else Color(0x55FFFFFF))
                    .clickable { cycleZoom() }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${mm}mm", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = if (style == CamStyle.MINT) Yellow else Color.White
                )
            }

            // top controls
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassBtn(Icons.Filled.Close) { haptic(Hap.CLICK); onClose() }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassBtn(
                        when (flash) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                            else -> Icons.Filled.FlashOff
                        }
                    ) {
                        haptic(Hap.CLICK)
                        flash = when (flash) {
                            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                            else -> ImageCapture.FLASH_MODE_OFF
                        }
                    }
                    GlassBtn(Icons.Filled.FlipCameraAndroid) { if (recording == null) { haptic(Hap.CONFIRM); front = !front } }
                }
            }

            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 14.dp)
                    .height(38.dp).clip(RoundedCornerShape(19.dp)).background(Color(0x77000000)).padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (recording != null) {
                    Box(Modifier.padding(start = 12.dp).size(9.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                    Text(
                        "%02d:%02d".format(recSecs / 60, recSecs % 60), color = Color.White, fontFamily = Mono,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp)
                    )
                } else {
                    ModeChip("PHOTO", !videoMode) { if (videoMode) { haptic(Hap.CONFIRM); videoMode = false } }
                    ModeChip("VIDEO", videoMode) {
                        if (!videoMode) {
                            haptic(Hap.CONFIRM); videoMode = true
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                }
            }

            toast?.let {
                Text(
                    it, color = Color.White, fontFamily = Mono, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 66.dp)
                        .clip(RoundedCornerShape(12.dp)).background(Color(0x99000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            focusAt?.let { o ->
                Box(
                    Modifier.offset { IntOffset((o.x - 36.dp.toPx()).roundToInt(), (o.y - 36.dp.toPx()).roundToInt()) }
                        .size(72.dp).border(2.dp, Yellow, RoundedCornerShape(18.dp))
                )
            }

            if (flashAlpha > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))

            if (!allowed) PermissionCard(Modifier.align(Alignment.Center)) { permLauncher.launch(needed) }
            else error?.let {
                Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    val thumbUi: @Composable () -> Unit = {
        val pop = remember { Animatable(1f) }
        LaunchedEffect(thumbPop) {
            if (thumbPop > 0) { pop.snapTo(0.6f); pop.animateTo(1f, spring(0.35f, 300f)) }
        }
        Box(
            Modifier.size(60.dp).scale(pop.value).clip(RoundedCornerShape(16.dp)).background(Cell)
                .border(1.dp, if (style.lightBar) Color(0x22000000) else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .clickable { haptic(Hap.CLICK); onOpenGallery() },
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Filled.Image, null, tint = Color(0x66FFFFFF), modifier = Modifier.size(24.dp))
        }
    }
    val shutterUi: @Composable () -> Unit = {
        Shutter(style, vertical = landscape, video = videoMode, recording = recording != null) {
            if (videoMode) toggleRecording() else capture()
        }
    }
    val dotsUi: @Composable () -> Unit = {
        Box(
            Modifier.size(52.dp).clip(CircleShape).pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        haptic(Hap.CONFIRM)
                        styleIdx = (styleIdx + 1) % CamStyle.entries.size
                        toast = CamStyle.entries[styleIdx].label
                    },
                    onLongPress = {
                        haptic(Hap.HEAVY)
                        gridOn = !gridOn
                        toast = if (gridOn) "GRID ON" else "GRID OFF"
                    }
                )
            },
            contentAlignment = Alignment.Center
        ) { Halftone(if (style.lightBar) Color(0xFF222222) else Color(0xFFD8D8D8), Modifier.size(34.dp)) }
    }
    val barColor = if (style.lightBar) Color.White else Color.Black

    Box(Modifier.fillMaxSize().background(barColor)) {
        if (!landscape) {
            Column(Modifier.fillMaxSize()) {
                viewfinder(Modifier.weight(1f).fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().background(barColor).navigationBarsPadding()
                        .height(160.dp).padding(horizontal = 36.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) { thumbUi(); shutterUi(); dotsUi() }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                @Composable
                fun SideBar(thumbFirst: Boolean) {
                    Column(
                        Modifier.fillMaxHeight().width(140.dp).background(barColor)
                            .statusBarsPadding().navigationBarsPadding().padding(vertical = 28.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (thumbFirst) { thumbUi(); shutterUi(); dotsUi() } else { dotsUi(); shutterUi(); thumbUi() }
                    }
                }
                if (barOnLeft) SideBar(thumbFirst = true)
                viewfinder(Modifier.weight(1f).fillMaxHeight())
                if (!barOnLeft) SideBar(thumbFirst = false)
            }
        }
    }
}

@Composable
private fun Shutter(style: CamStyle, vertical: Boolean, video: Boolean, recording: Boolean, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.5f, 500f), label = "shutter")
    val shape = if (style.circle) CircleShape else RoundedCornerShape(50)
    val w: Dp = when {
        style.circle -> 86.dp
        vertical -> 88.dp
        else -> 142.dp
    }
    val h: Dp = when {
        style.circle -> 86.dp
        vertical -> 142.dp
        else -> 82.dp
    }
    Box(
        Modifier.size(w, h).scale(sc).clickable(interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val fill = if (video) Color(0xFFFF3B30) else style.accent
        val inset by androidx.compose.animation.core.animateDpAsState(
            if (recording) 26.dp else 10.dp, spring(0.6f, 400f), label = "inset"
        )
        Box(Modifier.fillMaxSize().border(4.dp, style.accent, shape))
        Box(
            Modifier.fillMaxSize().padding(inset).clip(if (recording) RoundedCornerShape(10.dp) else shape)
                .background(Brush.verticalGradient(listOf(fill, fill.copy(alpha = 0.88f)))),
            contentAlignment = Alignment.Center
        ) {
            if (style.plus && !video) Canvas(Modifier.size(26.dp)) {
                val c = Color(0xFF111111)
                drawLine(c, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 5f, StrokeCap.Round)
                drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 5f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun Halftone(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val n = 6
        val step = size.width / n
        val mid = (n - 1) / 2f
        val maxD = hypot(mid, mid)
        for (r in 0 until n) for (c in 0 until n) {
            val d = hypot(r - mid, c - mid) / maxD
            drawCircle(color, step * 0.46f * (1f - 0.62f * d), Offset((c + 0.5f) * step, (r + 0.5f) * step))
        }
    }
}

@Composable
private fun GlassBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun PermissionCard(modifier: Modifier, onAllow: () -> Unit) {
    Column(
        modifier.padding(32.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xCC111111)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Allow camera access to take photos", color = Color.White, fontSize = 16.sp)
        Box(
            Modifier.clip(CircleShape).background(Color.White).clickable(onClick = onAllow)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) { Text("Allow camera", color = Color.Black, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp,
            color = if (selected) Color.Black else Color(0xFFDDDDDD)
        )
    }
}
