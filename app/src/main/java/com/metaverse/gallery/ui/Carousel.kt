package com.metaverse.gallery.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.metaverse.gallery.data.MediaItem
import com.metaverse.gallery.ui.theme.Cell
import com.metaverse.gallery.ui.theme.Hand
import com.metaverse.gallery.ui.theme.Ink
import com.metaverse.gallery.ui.theme.Muted
import com.metaverse.gallery.util.Hap
import com.metaverse.gallery.util.rememberHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.lerp as lerpOffset

private val CardShape = RoundedCornerShape(28.dp)

/**
 * The "museum" carousel: a vertical 3D stack of photo cards.
 *  - drag / fling to scroll (snaps to a card, haptic tick per card)
 *  - tap the centre card to open the full-screen viewer, tap a strip to jump to it
 *  - pinch in (or the grid button) to collapse into a collage, pinch out to return
 *  - in the viewer: swipe between photos, pinch to zoom, drag to pan, double-tap to zoom
 */
@Composable
fun MuseumCarousel(
    title: String,
    list: List<MediaItem>,
    startIndex: Int = 0,
    onClose: () -> Unit
) {
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val maxIndex = (list.size - 1).coerceAtLeast(0)
    val pos = remember { Animatable(startIndex.coerceIn(0, maxIndex).toFloat()) }
    var current by remember { mutableIntStateOf(startIndex.coerceIn(0, maxIndex)) }
    var overview by remember { mutableStateOf(false) }
    var viewerOpen by remember { mutableStateOf(false) }
    var viewerStart by remember { mutableIntStateOf(0) }
    var viewerPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(list.size) {
        if (pos.value > maxIndex) pos.snapTo(maxIndex.toFloat())
    }
    LaunchedEffect(Unit) {
        snapshotFlow { pos.value.roundToInt() }
            .distinctUntilChanged()
            .drop(1)
            .collect { current = it.coerceIn(0, maxIndex); haptic(Hap.TICK) }
    }

    BackHandler {
        when {
            viewerOpen -> closeViewer(scope, pos, viewerPage) { viewerOpen = false }
            overview -> overview = false
            else -> onClose()
        }
    }

    Box(Modifier.fillMaxSize().background(Ink).systemBarsPadding()) {
        if (list.isEmpty()) {
            Text(
                "No photos here yet", color = Muted, fontFamily = Hand, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            BoxWithConstraints(
                Modifier.fillMaxSize().padding(top = 64.dp, bottom = 28.dp)
                    .pinchToggle(
                        onPinchIn = { if (!overview) { overview = true; haptic(Hap.CONFIRM) } },
                        onPinchOut = { if (overview) { overview = false; haptic(Hap.CONFIRM) } }
                    )
            ) {
                val cardW = maxWidth - 40.dp
                val cardH = minOf(cardW * 1.3333f, maxHeight * 0.70f)

                AnimatedVisibility(
                    visible = !overview,
                    enter = fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 1.18f),
                    exit = fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 1.18f)
                ) {
                    CardStack(
                        list = list, pos = pos, cardW = cardW, cardH = cardH,
                        onOpen = { i ->
                            haptic(Hap.HEAVY)
                            viewerStart = i; viewerPage = i; viewerOpen = true
                        },
                        onSelect = { i ->
                            haptic(Hap.CLICK)
                            scope.launch { pos.animateTo(i.toFloat(), spring(0.8f, 220f)) }
                        }
                    )
                }

                AnimatedVisibility(
                    visible = overview,
                    enter = fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.8f),
                    exit = fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.8f)
                ) {
                    Collage(list) { i ->
                        haptic(Hap.CLICK)
                        scope.launch { pos.snapTo(i.toFloat()) }
                        overview = false
                    }
                }
            }
        }

        // top bar
        Box(
            Modifier.fillMaxWidth().height(64.dp)
                .background(Brush.verticalGradient(listOf(Ink, Color.Transparent)))
        ) {
            RoundBtn(Icons.Filled.Close, Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
                haptic(Hap.CLICK); onClose()
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (list.isNotEmpty()) Text(
                    if (overview) "${list.size} moments" else "${current + 1} / ${list.size}",
                    color = Muted, fontSize = 12.sp
                )
            }
            RoundBtn(
                if (overview) Icons.Filled.ViewCarousel else Icons.Filled.GridView,
                Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
            ) { haptic(Hap.CONFIRM); overview = !overview }
        }

        if (list.isNotEmpty() && !overview && !viewerOpen) {
            Text(
                "scroll  ·  tap to open  ·  pinch to zoom out",
                color = Color(0xFF555555), fontFamily = Hand, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
            )
        }

        // full-screen viewer
        AnimatedVisibility(
            visible = viewerOpen && list.isNotEmpty(),
            enter = fadeIn(tween(220)) + scaleIn(tween(300), initialScale = 0.82f),
            exit = fadeOut(tween(180)) + scaleOut(tween(240), targetScale = 0.82f)
        ) {
            ImageViewer(
                list = list, start = viewerStart,
                onPage = { viewerPage = it },
                onClose = { closeViewer(scope, pos, viewerPage) { viewerOpen = false } }
            )
        }
    }
}

private fun closeViewer(
    scope: kotlinx.coroutines.CoroutineScope,
    pos: Animatable<Float, AnimationVector1D>,
    page: Int,
    hide: () -> Unit
) {
    hide()
    scope.launch { pos.animateTo(page.toFloat(), spring(0.85f, 200f)) }
}

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(42.dp).clip(CircleShape).background(Color(0xCC1A1A1A)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

/** Observes two-finger pinches without consuming them, so scrolling keeps working. */
private fun Modifier.pinchToggle(onPinchIn: () -> Unit, onPinchOut: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var total = 1f
            var fired = false
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (!fired && ev.changes.size >= 2 && ev.changes.all { it.pressed }) {
                    total *= ev.calculateZoom()
                    if (total < 0.78f) { fired = true; onPinchIn() }
                    else if (total > 1.25f) { fired = true; onPinchOut() }
                }
            } while (ev.changes.any { it.pressed })
        }
    }

/** Top edge (relative to the stage centre) of the card at offset [d] from the focused card. */
private fun cardTop(d: Float, h: Float, strip: Float): Float = when {
    d >= 1f -> h / 2f + (d - 1f) * strip
    d >= 0f -> lerp(-h / 2f, h / 2f, d)
    d >= -1f -> lerp(-h / 2f - strip, -h / 2f, d + 1f)
    else -> -h / 2f - strip + (d + 1f) * strip
}

@Composable
private fun CardStack(
    list: List<MediaItem>,
    pos: Animatable<Float, AnimationVector1D>,
    cardW: androidx.compose.ui.unit.Dp,
    cardH: androidx.compose.ui.unit.Dp,
    onOpen: (Int) -> Unit,
    onSelect: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val cardHpx = with(LocalDensity.current) { cardH.toPx() }
    val strip = cardHpx * 0.10f
    val pxPerItem = cardHpx
    val maxIndex = list.size - 1

    val lo by remember { derivedStateOf { floor(pos.value - 7f).toInt().coerceAtLeast(0) } }
    val hi by remember { derivedStateOf { ceil(pos.value + 7f).toInt().coerceAtMost(maxIndex) } }
    val fmt = remember { DateTimeFormatter.ofPattern("MMM d") }

    Box(
        Modifier.fillMaxSize().draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { dy ->
                scope.launch {
                    pos.snapTo((pos.value - dy / pxPerItem).coerceIn(-0.25f, maxIndex + 0.25f))
                }
            },
            onDragStarted = { pos.stop() },
            onDragStopped = { v ->
                val target = (pos.value - v / pxPerItem * 0.22f).roundToInt().coerceIn(0, maxIndex)
                scope.launch { pos.animateTo(target.toFloat(), spring(0.78f, 170f)) }
            }
        )
    ) {
        for (i in lo..hi) {
            key(list[i].uri.toString()) {
                val item = list[i]
                Box(
                    Modifier.align(Alignment.Center).size(cardW, cardH)
                        .zIndex(i.toFloat())
                        .graphicsLayer {
                            val d = i - pos.value
                            val ad = abs(d)
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            translationY = cardTop(d, cardHpx, strip) + cardHpx / 2f
                            val s = 1f - 0.04f * min(ad, 4f)
                            scaleX = s
                            scaleY = s
                            rotationX = 30f * min(ad, 1f)
                            cameraDistance = 16f * density
                            alpha = 1f - (ad - 5f).coerceIn(0f, 1f)
                        }
                        .clip(CardShape).background(Cell)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (abs(i - pos.value) < 0.5f) onOpen(i) else onSelect(i) }
                ) {
                    AsyncImage(
                        item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxWidth().height(54.dp)
                            .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                    )
                    Text(
                        item.date.format(fmt).lowercase(),
                        color = Color.White, fontFamily = Hand, fontSize = 20.sp,
                        modifier = Modifier.padding(start = 18.dp, top = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Collage(list: List<MediaItem>, onPick: (Int) -> Unit) {
    LazyVerticalGrid(
        GridCells.Fixed(3),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(list, key = { _, m -> m.uri.toString() }) { i, m ->
            val rot = (((i * 37) % 11) - 5) * 1.3f
            val sc = 1.06f + ((i * 13) % 5) * 0.035f
            AsyncImage(
                m.uri, null,
                Modifier.aspectRatio(0.75f)
                    .graphicsLayer { rotationZ = rot; scaleX = sc; scaleY = sc }
                    .clip(RoundedCornerShape(10.dp)).background(Cell)
                    .clickable { onPick(i) },
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ImageViewer(list: List<MediaItem>, start: Int, onPage: (Int) -> Unit, onClose: () -> Unit) {
    val haptic = rememberHaptics()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, list.size - 1)) { list.size }
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect {
            onPage(it)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().drop(1).collect { haptic(Hap.TICK) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            key = { list[it].uri.toString() },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(
                uri = list[page].uri,
                onZoomChanged = { zoomed = it },
                onTap = { chrome = !chrome }
            )
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(
                Modifier.fillMaxWidth().statusBarsPadding().height(64.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            ) {
                RoundBtn(Icons.Filled.Close, Modifier.align(Alignment.CenterStart).padding(start = 16.dp)) {
                    haptic(Hap.CLICK); onClose()
                }
                Text(
                    "${pager.currentPage + 1} / ${list.size}", color = Color.White, fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(uri: Uri, onZoomChanged: (Boolean) -> Unit, onTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptics()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffset(o: Offset, s: Float): Offset {
        val mx = size.width * (s - 1f) / 2f
        val my = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        scope.launch {
                            val from = scale
                            val fromOff = offset
                            val to = if (scale > 1.05f) 1f else 2.6f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val toOff = if (to == 1f) Offset.Zero
                            else clampOffset((center - tap) * (to - 1f), to)
                            haptic(Hap.CLICK)
                            onZoomChanged(to > 1f)
                            animate(0f, 1f, animationSpec = tween(280)) { f, _ ->
                                scale = lerp(from, to, f)
                                offset = lerpOffset(fromOff, toOff, f)
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        val canceled = ev.changes.any { it.isConsumed }
                        if (!canceled) {
                            val multi = ev.changes.count { it.pressed } > 1
                            if (multi || scale > 1.02f) {
                                val zoom = ev.calculateZoom()
                                val pan = ev.calculatePan()
                                val centroid = ev.calculateCentroid(useCurrent = false)
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val q = centroid - center
                                val ns = (scale * zoom).coerceIn(1f, 6f)
                                val k = ns / scale
                                val newOff = q - (q - offset) * k + pan
                                val wasZoomed = scale > 1.02f
                                scale = ns
                                offset = clampOffset(newOff, ns)
                                val nowZoomed = ns > 1.02f
                                if (nowZoomed != wasZoomed) {
                                    onZoomChanged(nowZoomed)
                                    haptic(Hap.CLICK)
                                }
                                ev.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (!canceled && ev.changes.any { it.pressed })
                    if (scale <= 1.02f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomChanged(false)
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

/** Full-screen photo viewer (swipe, pinch-zoom, pan, double-tap) usable from any screen. */
@Composable
fun PhotoViewer(list: List<MediaItem>, start: Int, onClose: () -> Unit) {
    if (list.isEmpty()) return
    BackHandler { onClose() }
    ImageViewer(list = list, start = start.coerceIn(0, list.size - 1), onPage = {}, onClose = onClose)
}
