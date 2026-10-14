package com.metaverse.gallery.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.metaverse.gallery.MuseumViewModel
import com.metaverse.gallery.data.MediaItem
import com.metaverse.gallery.ui.theme.Cell
import com.metaverse.gallery.util.Hap
import com.metaverse.gallery.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

private val DocBlue = Color(0xFF55B8F5)
private val DocDark = Color(0xFF121212)
private val DocInk = Color(0xFF0E0E0E)
private val DocGray = Color(0xFF8E8E93)

private fun compact(n: Int): String =
    if (n >= 1000) String.format(Locale.US, "%.1fK", n / 1000f) else n.toString()

/**
 * "Doc" — your collection. A light sheet with tilted floating cards and a "Most viewed"
 * card that blurs away at its bottom edge, sitting on a dark upload bar. Drag the sheet up
 * (or tap the bar) to reveal the dark "Uploading" view; "+" imports photos from the picker.
 */
@Composable
fun MuseumScreen(
    vm: MuseumViewModel,
    onExit: () -> Unit = {},
    onOpen: (MediaItem) -> Unit = {}
) {
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val items by vm.items.collectAsState()
    val extras by vm.extras.collectAsState()
    val archived by vm.archived.collectAsState()
    val views by vm.views.collectAsState()

    val all = remember(items, extras, archived) {
        (extras + items).filter { it.uri.toString() !in archived }
    }
    var searching by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    val list = remember(all, q) {
        val s = q.trim()
        if (s.isEmpty()) all else all.filter {
            it.name.contains(s, true) || it.bucket.contains(s, true) || it.date.toString().contains(s)
        }
    }

    // ---- import / "uploading" ----
    var upTotal by remember { mutableIntStateOf(0) }
    var upDone by remember { mutableIntStateOf(0) }
    val uploading = upTotal > 0
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            upDone = 0; upTotal = uris.size
            for (u in uris) { delay(160); vm.addExtra(u); upDone++; haptic(Hap.TICK) }
            haptic(Hap.CONFIRM); delay(1400); upTotal = 0
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

    val title = if (uploading) "Uploading" else "Synced"
    val sub = if (uploading) "$upDone of $upTotal" else "${all.size} of ${all.size}"

    // ---- sheet progress: 0 = light collection, 1 = dark uploading ----
    val prog = remember { Animatable(0f) }
    fun settle(to: Float) { scope.launch { prog.animateTo(to, spring(0.82f, 260f)) }; haptic(Hap.CLICK) }
    val expanded by remember { derivedStateOf { prog.value > 0.5f } }
    BackHandler { if (expanded) settle(0f) else if (searching) { searching = false; q = "" } else onExit() }

    val inf = rememberInfiniteTransition(label = "doc")
    val t = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart), label = "t")

    BoxWithConstraints(Modifier.fillMaxSize().background(DocDark)) {
        val density = LocalDensity.current
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val barH = 104.dp + navBottom
        val panelH = maxHeight - barH
        val panelPx = with(density) { panelH.toPx() }
        val peekPx = with(density) { 118.dp.toPx() }
        val range = (panelPx - peekPx).coerceAtLeast(1f)
        val w = maxWidth

        Box(
            Modifier.fillMaxSize().draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dy ->
                    scope.launch { prog.snapTo((prog.value - dy / range).coerceIn(0f, 1f)) }
                },
                onDragStopped = { v ->
                    val to = when {
                        v < -700f -> 1f
                        v > 700f -> 0f
                        prog.value > 0.5f -> 1f
                        else -> 0f
                    }
                    scope.launch { prog.animateTo(to, spring(0.82f, 260f), -v / range) }
                    haptic(Hap.CLICK)
                }
            )
        ) {
            // ================= dark "Uploading" view =================
            Column(
                Modifier.fillMaxSize().padding(top = 148.dp, start = 28.dp)
                    .graphicsLayer { alpha = prog.value; translationY = (1f - prog.value) * 40.dp.toPx() }
            ) {
                Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                Spacer(Modifier.height(6.dp))
                val shownN = if (uploading) upDone else all.size
                val totalN = if (uploading) upTotal else all.size
                Text("$shownN of $totalN images", color = DocGray, fontSize = 15.sp)
            }
            if (all.isNotEmpty()) Box(
                Modifier.align(Alignment.Center).offset(y = 24.dp).fillMaxWidth().height(280.dp)
                    .graphicsLayer { alpha = prog.value; translationY = (1f - prog.value) * 90.dp.toPx() }
            ) {
                fun at(i: Int) = all[i % all.size]
                DarkPhoto(at(2), 150, 210, -4f, Modifier.align(Alignment.CenterStart).offset(x = (-34).dp), t, 0f)
                DarkPhoto(at(1), 184, 226, -3f, Modifier.align(Alignment.Center).offset(x = 12.dp), t, 0.33f)
                DarkPhoto(at(0), 150, 220, 4f, Modifier.align(Alignment.CenterEnd).offset(x = 34.dp), t, 0.66f)
            }

            // ================= bottom bar (collapsed) =================
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(barH)
                    .padding(top = 18.dp, bottom = navBottom, start = 24.dp, end = 24.dp)
                    .graphicsLayer { alpha = 1f - prog.value }
                    .pointerInput(expanded) { detectTapGestures { if (!expanded) settle(1f) } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(58.dp, 52.dp)) {
                    if (all.size > 1) MiniThumb(all[1], Modifier.align(Alignment.CenterStart).offset(x = 2.dp, y = (-2).dp), -9f)
                    if (all.isNotEmpty()) MiniThumb(all[0], Modifier.align(Alignment.CenterEnd).offset(x = (-2).dp, y = 2.dp), 7f)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(sub, color = DocGray, fontSize = 14.sp)
                }
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF2A2A2A))
                        .clickable(remember { MutableInteractionSource() }, null) { if (!expanded) { haptic(Hap.HEAVY); pick() } },
                    contentAlignment = Alignment.Center
                ) {
                    if (uploading) CircularProgressIndicator(
                        progress = { upDone / upTotal.coerceAtLeast(1).toFloat() },
                        color = Color.White, trackColor = Color(0x33FFFFFF), strokeWidth = 2.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }

            // ================= bottom pill (expanded) =================
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = navBottom + 22.dp, start = 28.dp, end = 28.dp)
                    .fillMaxWidth().height(56.dp)
                    .graphicsLayer { alpha = prog.value }
                    .clip(CircleShape).background(Color(0xFF262626))
                    .pointerInput(expanded) { detectTapGestures { if (expanded) { haptic(Hap.HEAVY); pick() } } },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (uploading) "Uploading" else "Import", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            // ================= drag handle (rides the sheet's bottom edge) =================
            Box(
                Modifier.fillMaxWidth()
                    .offset { IntOffset(0, (panelPx + 10.dp.toPx() - (panelPx - peekPx) * prog.value).roundToInt()) },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(Modifier.size(40.dp, 4.dp).clip(CircleShape).background(Color(0xFF4A4A4A)))
            }

            // ================= light "Your collection" sheet =================
            Column(
                Modifier.fillMaxWidth().height(panelH)
                    .graphicsLayer { translationY = -(panelPx - peekPx) * prog.value }
                    .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                    .background(Color.White)
                    .drawBehind {
                        val step = 19.dp.toPx()
                        val r = 0.8.dp.toPx()
                        var y = step / 2
                        while (y < size.height) {
                            var x = step / 2
                            while (x < size.width) { drawCircle(Color(0x16000000), r, Offset(x, y)); x += step }
                            y += step
                        }
                    }
                    .pointerInput(expanded) { detectTapGestures { if (expanded) settle(0f) } }
            ) {
                Row(
                    Modifier.padding(top = statusTop + 14.dp, start = 24.dp, end = 24.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RoundIcon(Icons.Filled.ArrowBack) { haptic(Hap.CLICK); if (searching) { searching = false; q = "" } else onExit() }
                    RoundIcon(if (searching) Icons.Filled.Close else Icons.Filled.Search) {
                        haptic(Hap.CLICK); searching = !searching; if (!searching) q = ""
                    }
                }
                Spacer(Modifier.height(26.dp))
                Column(Modifier.padding(start = 28.dp, end = 28.dp)) {
                    if (searching) {
                        Box {
                            if (q.isEmpty()) Text("Search…", color = Color(0xFFC4C4C8), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                            BasicTextField(
                                value = q, onValueChange = { q = it }, singleLine = true,
                                cursorBrush = SolidColor(DocBlue),
                                textStyle = TextStyle(color = DocInk, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text("Your collection", color = DocInk, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${list.size} ${if (list.size == 1) "image" else "images"}", color = DocGray, fontSize = 15.sp)
                }

                // ---- tilted floating cards ----
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (list.isEmpty()) {
                        Text("nothing here yet — tap +", color = Color(0xFFB0B0B5), fontSize = 16.sp)
                    } else LazyRow(
                        Modifier.fillMaxWidth().height(320.dp),
                        contentPadding = PaddingValues(end = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy((-14).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(list.take(80), key = { _, it -> it.uri.toString() + it.isVideo }) { i, item ->
                            DocCard(item, i, t) { haptic(Hap.CLICK); onOpen(item) }
                        }
                    }
                }

                // ---- most viewed ----
                val top = remember(list, views) { list.maxByOrNull { views[it.uri.toString()] ?: 0 } }
                val totalViews = remember(views) { views.values.sum() }
                Row(Modifier.padding(horizontal = 28.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Most viewed", color = DocInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Text(compact(totalViews), color = Color(0xFFB4B4B8), fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.padding(horizontal = 28.dp).fillMaxWidth().height(178.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(Color(0xFFF1F1F1))
                        .pointerInput(top) { detectTapGestures { top?.let { haptic(Hap.CLICK); onOpen(it) } } }
                ) {
                    if (top != null) BlurFadeImage(top.uri, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(DocBlue)
            .clickable(press, null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
}

private val CardDims = listOf(Triple(156, 208, -3f), Triple(196, 268, -4f), Triple(170, 262, 3f))

/** A tilted, floating, shadowed card in the collection row. */
@Composable
private fun DocCard(item: MediaItem, index: Int, t: State<Float>, onClick: () -> Unit) {
    val (cw, ch, rot) = CardDims[index % CardDims.size]
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay((index.coerceAtMost(5)) * 70L); enter.animateTo(1f, spring(0.62f, 200f)) }
    var pressed by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (pressed) 1.06f else 1f, spring(0.55f, 420f), label = "lift")
    val ph = index * 0.37f
    Box(
        Modifier
            .offset(x = if (index == 0) (-26).dp else 0.dp)
            .size(cw.dp, ch.dp)
            .graphicsLayer {
                val a = t.value * 6.2831855f + ph * 6.2831855f
                val bob = sin(a)
                val e = enter.value
                translationY = bob * 6.dp.toPx() + (1f - e) * 60.dp.toPx()
                rotationZ = rot + bob * 1.1f + (1f - e) * 8f
                val s = (0.7f + 0.3f * e) * lift
                scaleX = s; scaleY = s
                alpha = e.coerceIn(0f, 1f)
            }
            .shadow(if (pressed) 28.dp else 18.dp, RoundedCornerShape(3.dp), ambientColor = Color(0x22000000), spotColor = Color(0x55000000))
            .clip(RoundedCornerShape(3.dp)).background(Cell)
            .pointerInput(item.uri) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() }
                )
            }
    ) {
        AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun DarkPhoto(item: MediaItem, w: Int, h: Int, rot: Float, modifier: Modifier, t: State<Float>, ph: Float) {
    Box(
        modifier.size(w.dp, h.dp)
            .graphicsLayer {
                val bob = sin(t.value * 6.2831855f + ph * 6.2831855f)
                translationY = bob * 6.dp.toPx()
                rotationZ = rot + bob * 1.2f
            }
            .clip(RoundedCornerShape(3.dp)).background(Cell)
    ) {
        AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun MiniThumb(item: MediaItem, modifier: Modifier, rot: Float) {
    Box(
        modifier.size(38.dp, 46.dp).graphicsLayer { rotationZ = rot }
            .clip(RoundedCornerShape(3.dp)).background(Cell)
    ) {
        AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/**
 * The photo, sharp at the top and dissolving into a heavy blur toward the bottom edge —
 * the soft colour-smear under the "Most viewed" card.
 */
@Composable
private fun BlurFadeImage(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    Box(modifier) {
        AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent, 0.34f to Color.Transparent, 0.82f to Color.Black
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            if (Build.VERSION.SDK_INT >= 31) {
                AsyncImage(uri, null, Modifier.fillMaxSize().blur(28.dp), contentScale = ContentScale.Crop)
            } else {
                // older phones: a tiny image stretched up reads as a blur
                AsyncImage(
                    ImageRequest.Builder(ctx).data(uri).size(24).build(), null,
                    Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium
                )
            }
        }
    }
}
