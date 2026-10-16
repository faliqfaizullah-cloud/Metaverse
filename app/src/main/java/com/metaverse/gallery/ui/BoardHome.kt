package com.metaverse.gallery.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.metaverse.gallery.data.MediaItem
import com.metaverse.gallery.ui.theme.Cell
import com.metaverse.gallery.ui.theme.Ember
import com.metaverse.gallery.ui.theme.Hand
import com.metaverse.gallery.util.Hap
import com.metaverse.gallery.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

val BoardBg = Color(0xFFDADBDC)
private val Ink1 = Color(0xFF111111)
private val Soft = Color(0xFF8C8D90)
private val Yellow = Color(0xFFE9EA3B)
private val Amber = Color(0xFFFBBC04)
private val Lilac = Color(0xFFD9CCFF)
private val Olive = Color(0xFF4C5546)
private val BoardGreen = Color(0xFF22B573)
private val BoardRed = Color(0xFFE5483F)
private const val TAU = 6.2831855f

private val DayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private fun MediaItem.timeText(): String =
    Instant.ofEpochMilli(takenMillis).atZone(ZoneId.systemDefault()).format(TimeFmt)

// ───────────────────────────── atmosphere ─────────────────────────────

/** Phone tilt as (-1..1, -1..1), low-pass filtered. Drives parallax and the glossy sheen. */
@Composable
fun rememberTilt(): State<Offset> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var fx = 0f
        var fy = 0f
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
                val y = ((e.values[1] / 9.81f - 0.8f) * 2f).coerceIn(-1f, 1f)
                fx += (x - fx) * 0.07f
                fy += (y - fy) * 0.07f
                state.value = Offset(fx, fy)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sm != null && sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm?.unregisterListener(l) }
    }
    return state
}

/**
 * Slow drifting pools of coloured light behind everything. Pastel on the light board,
 * ember / blue glow on the dark views; crossfades between the two.
 */
@Composable
fun Atmosphere(light: Boolean) {
    val inf = rememberInfiniteTransition(label = "atmosphere")
    val p = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(32000, easing = LinearEasing), RepeatMode.Restart), label = "p")
    val k by animateFloatAsState(if (light) 1f else 0f, tween(700), label = "k")
    Box(
        Modifier.fillMaxSize().drawBehind {
            val a = p.value * TAU
            val w = size.width
            val h = size.height
            fun blob(c: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), center = Offset(cx, cy), radius = r), r, Offset(cx, cy))
            }
            if (k > 0.01f) {
                blob(Yellow.copy(alpha = 0.34f * k), w * (0.18f + 0.14f * cos(a)), h * (0.16f + 0.08f * sin(a)), w * 0.75f)
                blob(Lilac.copy(alpha = 0.55f * k), w * (0.88f + 0.10f * sin(a)), h * (0.46f + 0.10f * cos(a)), w * 0.85f)
                blob(Color(0xFFAEE0FF).copy(alpha = 0.40f * k), w * (0.30f + 0.12f * sin(a + 2f)), h * (0.86f + 0.06f * cos(a)), w * 0.8f)
            }
            if (k < 0.99f) {
                val d = 1f - k
                blob(Color(0xFFFF3D00).copy(alpha = 0.16f * d), w * (0.2f + 0.15f * cos(a)), h * (0.2f + 0.08f * sin(a)), w * 0.8f)
                blob(Color(0xFF3B5BFF).copy(alpha = 0.18f * d), w * (0.85f + 0.1f * sin(a)), h * (0.62f + 0.1f * cos(a)), w * 0.9f)
            }
        }
    )
}

// ───────────────────────────── board ─────────────────────────────

/** Where a floating card sits (x, y, width in screen widths), its shape, drift and depth layer. */
private class Slot(
    val x: Float, val y: Float, val w: Float, val ar: Float,
    val rot: Float, val k: Int, val ph: Float, val depth: Float
) {
    /** −1 (left edge) … +1 (right edge): the side a card flies in from. */
    val out: Float get() = (x + w / 2f - 0.5f) * 2f
}

private val Slots = listOf(
    Slot(0.30f, 0.00f, 0.34f, 1.20f, 0f, 1, 0.00f, 0.6f),    // 0 yellow date card
    Slot(-0.02f, 0.06f, 0.30f, 0.80f, 0f, 2, 0.15f, 1.0f),   // 1 photo + label
    Slot(0.32f, 0.22f, 0.36f, 2.20f, 0f, 1, 0.30f, 0.8f),    // 2 link card
    Slot(0.66f, 0.10f, 0.40f, 0.85f, -4f, 1, 0.45f, 0.5f),   // 3 stats card
    Slot(0.02f, 0.46f, 0.28f, 0.80f, -8f, 2, 0.60f, 1.25f),  // 4 latest-time card
    Slot(0.29f, 0.38f, 0.38f, 0.78f, 8f, 1, 0.75f, 1.5f),    // 5 hero photo stack
    Slot(0.69f, 0.60f, 0.28f, 0.90f, 0f, 2, 0.90f, 0.9f),    // 6 photo + tag
    Slot(0.02f, 0.84f, 0.30f, 1.00f, 10f, 1, 0.20f, 1.1f),   // 7 photo + lilac chip
    Slot(0.30f, 0.92f, 0.38f, 1.20f, 0f, 2, 0.55f, 0.8f),    // 8 person card
    Slot(0.64f, 0.94f, 0.40f, 1.15f, 0f, 1, 0.80f, 0.6f)     // 9 amber camera card
)
private const val PER_CLUSTER = 9
private const val CLUSTER_H = 1.34f

private class Stats(val photos: Int, val videos: Int, val favs: Int, val latest: MediaItem?)

/** Shared motion inputs: idle drift clock, scroll momentum, phone tilt and scroll position. */
private class Fx(
    val t: State<Float>,
    val vel: State<Float>,
    val tilt: State<Offset>,
    val scrollPx: () -> Float,
    val clusterIdx: Int = 0,
    val clusterPx: Float = 1f
) {
    fun at(i: Int, px: Float) = Fx(t, vel, tilt, scrollPx, i, px)
}

class BoardActions(
    val open: (MediaItem) -> Unit,
    val favorite: (MediaItem) -> Unit,
    val archive: (MediaItem) -> Unit,
    val delete: (MediaItem) -> Unit,
    val camera: () -> Unit
)

/**
 * The home "canvas": photos and info cards float on a dotted board in layers of depth.
 * Layers drift, parallax against scrolling, lean with scroll momentum, shift with phone tilt
 * and catch a moving sheen. Tap = open, long-press = favourite, swipe right = archive,
 * swipe left = delete (on photo cards).
 */
@Composable
fun BoardHome(
    items: List<MediaItem>,
    favs: Set<String>,
    title: String,
    topPad: Dp,
    bottomPad: Dp,
    scrollTick: Int,
    actions: BoardActions
) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val stats = remember(items, favs) {
        Stats(
            photos = items.count { !it.isVideo }, videos = items.count { it.isVideo },
            favs = items.count { it.uri.toString() in favs }, latest = items.firstOrNull()
        )
    }
    val inf = rememberInfiniteTransition(label = "board")
    val t = inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "t"
    )
    val tilt = rememberTilt()
    val vel = remember { mutableFloatStateOf(0f) }
    val clusterCount = (items.size + PER_CLUSTER - 1) / PER_CLUSTER

    LaunchedEffect(scrollTick) { if (scrollTick > 0) listState.animateScrollToItem(0) }
    LaunchedEffect(title) { listState.scrollToItem(0) }

    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            val step = 13.dp.toPx()
            val r = 0.8.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(Color(0x1C000000), r, Offset(x, y)); x += step
                }
                y += step
            }
        }
    ) {
        val base = maxWidth
        val clusterPx = with(density) { (base * CLUSTER_H).toPx() }
        val scrollPx = { listState.firstVisibleItemIndex * clusterPx + listState.firstVisibleItemScrollOffset }

        // scroll momentum: smoothed per-frame scroll speed, decays to rest
        LaunchedEffect(clusterPx) {
            var last = scrollPx()
            while (true) {
                withFrameNanos {
                    val cur = scrollPx()
                    val d = cur - last
                    last = cur
                    vel.floatValue = vel.floatValue * 0.88f + (d / 38f).coerceIn(-1.5f, 1.5f) * 0.12f
                }
            }
        }
        val fx = remember(clusterPx) { Fx(t, vel, tilt, scrollPx) }

        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = topPad + 38.dp, bottom = bottomPad)
        ) {
            items(clusterCount, key = { it }) { c ->
                val chunk = List(PER_CLUSTER) { items[(c * PER_CLUSTER + it) % items.size] }
                Cluster(chunk, c, stats, base, fx.at(c, clusterPx), favs, actions)
            }
        }
        // corner captions, like the reference board
        Row(
            Modifier.padding(top = topPad + 6.dp, start = 18.dp, end = 18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (title.isEmpty()) {
                Text("Meta", color = Ink1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("verse", color = Soft, fontSize = 16.sp)
            } else {
                Text(title, color = Ink1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            Text("${items.size} memories", color = Soft, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Cluster(
    chunk: List<MediaItem>, index: Int, stats: Stats, base: Dp,
    fx: Fx, favs: Set<String>, a: BoardActions
) {
    fun m(i: Int) = chunk[i]
    fun fav(i: Int) = m(i).uri.toString() in favs
    val o = index * 10
    val first = m(0)

    Box(Modifier.fillMaxWidth().height(base * CLUSTER_H)) {
        // 0 — yellow date card
        Floaty(Slots[0], o + 0, base, fx, null, bg = Yellow, onClick = { a.open(first) }) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(first.bucket.ifBlank { "Museum" }, color = Color(0x99000000), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(first.date.format(DayFmt), color = Ink1, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        // 1 — photo with white label
        Floaty(Slots[1], o + 1, base, fx, m(1), onClick = { a.open(m(1)) }, onLong = { a.favorite(m(1)) },
            onArchive = { a.archive(m(1)) }, onDelete = { a.delete(m(1)) }) {
            Pic(m(1))
            FavMark(fav(1))
            Text(
                m(1).bucket.ifBlank { "Photo" }, color = Ink1, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)).background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        // 2 — link card
        Floaty(Slots[2], o + 2, base, fx, null, corner = 12.dp, onClick = { a.open(m(1)) }) {
            Row(Modifier.fillMaxSize().padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Pic(m(1), Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(7.dp)))
                Spacer(Modifier.width(7.dp))
                Column {
                    Text("${stats.photos} photos and ${stats.videos} videos in your museum", color = Ink1, fontSize = 8.sp,
                        fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 9.5.sp)
                    Text("metaverse.gallery", color = Soft, fontSize = 6.sp, maxLines = 1)
                }
            }
        }
        // 3 — stats card (photo tinted olive)
        Floaty(Slots[3], o + 3, base, fx, m(2), corner = 10.dp, bg = Olive, onClick = { a.open(m(2)) }, onLong = { a.favorite(m(2)) },
            onArchive = { a.archive(m(2)) }, onDelete = { a.delete(m(2)) }) {
            Pic(m(2))
            Box(Modifier.matchParentSize().background(Olive.copy(alpha = 0.66f)))
            Text("Your\nMuseum", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(9.dp))
            Text("${stats.photos}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 10.dp))
            Column(Modifier.align(Alignment.BottomEnd).padding(10.dp), horizontalAlignment = Alignment.End) {
                Text("Videos", color = Color(0xAAFFFFFF), fontSize = 7.sp)
                Text("${stats.videos}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("Favourites", color = Color(0xAAFFFFFF), fontSize = 7.sp)
                Text("${stats.favs}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        // 4 — latest capture card (the ON dot breathes)
        Floaty(Slots[4], o + 4, base, fx, null, corner = 18.dp, onClick = { stats.latest?.let(a.open) }) {
            Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
                    Text("ON", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier.align(Alignment.TopEnd).size(5.dp).graphicsLayer {
                            alpha = 0.35f + 0.65f * (0.5f + 0.5f * sin(fx.t.value * TAU * 7f))
                        }.clip(CircleShape).background(BoardGreen)
                    )
                }
                Column {
                    Text("Latest capture", color = Color(0xFFB4B4B6), fontFamily = Hand, fontSize = 11.sp, maxLines = 1)
                    Text(stats.latest?.timeText() ?: "--:--", color = Ink1, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        // 5 — hero stack (layers separate a little with tilt)
        Floaty(Slots[5], o + 5, base, fx, m(3), plain = true, gloss = false, onClick = { a.open(m(3)) }, onLong = { a.favorite(m(3)) },
            onArchive = { a.archive(m(3)) }, onDelete = { a.delete(m(3)) }) {
            Pic(m(5), Modifier.align(Alignment.TopEnd).fillMaxWidth(0.9f).fillMaxHeight(0.92f)
                .graphicsLayer { rotationZ = 7f; translationX = fx.tilt.value.x * 7.dp.toPx() }
                .shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)))
            Pic(m(4), Modifier.align(Alignment.BottomStart).fillMaxWidth(0.9f).fillMaxHeight(0.92f)
                .graphicsLayer { rotationZ = -5f; translationX = fx.tilt.value.x * 3.dp.toPx() }
                .shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)))
            Pic(m(3), Modifier.align(Alignment.Center).fillMaxWidth(0.92f).fillMaxHeight(0.96f)
                .graphicsLayer { translationX = -fx.tilt.value.x * 4.dp.toPx() }
                .shadow(14.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)))
            FavMark(fav(3))
        }
        // 6 — photo card with amber tag
        Floaty(Slots[6], o + 6, base, fx, m(6), corner = 16.dp, onClick = { a.open(m(6)) }, onLong = { a.favorite(m(6)) },
            onArchive = { a.archive(m(6)) }, onDelete = { a.delete(m(6)) }) {
            Pic(m(6), Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(12.dp)))
            FavMark(fav(6))
            Text(
                m(6).timeText(), color = Ink1, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(7.dp))
                    .background(Amber).padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
        // 7 — photo with lilac chip
        Floaty(Slots[7], o + 7, base, fx, m(7), corner = 12.dp, onClick = { a.open(m(7)) }, onLong = { a.favorite(m(7)) },
            onArchive = { a.archive(m(7)) }, onDelete = { a.delete(m(7)) }) {
            Pic(m(7), Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(9.dp)))
            FavMark(fav(7))
            Text(
                "${stats.videos} videos", color = Ink1, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(7.dp))
                    .background(Lilac).padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
        // 8 — person-style card
        Floaty(Slots[8], o + 8, base, fx, m(8), corner = 18.dp, onClick = { a.open(m(8)) }, onLong = { a.favorite(m(8)) },
            onArchive = { a.archive(m(8)) }, onDelete = { a.delete(m(8)) }) {
            Pic(m(8), Modifier.align(Alignment.TopEnd).fillMaxWidth(0.5f).fillMaxHeight(0.82f)
                .padding(top = 6.dp, end = 6.dp).clip(RoundedCornerShape(12.dp)))
            Row(Modifier.align(Alignment.TopStart).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Pic(m(8), Modifier.size(20.dp).clip(CircleShape))
            }
            Text(m(8).bucket.ifBlank { "Sally" }, color = Ink1, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 6.dp))
            Box(
                Modifier.align(Alignment.Center).width(56.dp).height(20.dp)
                    .shadow(8.dp, CircleShape).clip(CircleShape).background(Color.White)
                    .drawBehind {
                        val step = 3.dp.toPx()
                        var x = step * 2
                        while (x < size.width - step * 2) {
                            var y = step * 1.5f
                            while (y < size.height - step) {
                                drawCircle(Color(0x33000000), 0.7.dp.toPx(), Offset(x, y)); y += step
                            }
                            x += step
                        }
                    }
            )
        }
        // 9 — amber camera card (a ring ripples out of the camera button)
        Floaty(Slots[9], o + 9, base, fx, null, bg = Amber, corner = 14.dp, onClick = { a.camera() }) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("✦", color = Ink1, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("${stats.photos + stats.videos} items", color = Color(0x99000000), fontSize = 7.sp, maxLines = 1)
                Text(first.bucket.ifBlank { "Camera" }, color = Ink1, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(9.dp).size(24.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(24.dp).graphicsLayer {
                        val p = (fx.t.value * 4f) % 1f
                        scaleX = 1f + p * 0.9f; scaleY = scaleX
                        alpha = (1f - p) * 0.55f
                    }.clip(CircleShape).background(Color(0xFFEFDDA0))
                )
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFEFDDA0)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PhotoCamera, null, tint = Ink1, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

/**
 * One floating card. It flies in along an eased path from its side of the board, then lives in
 * three motion layers: idle drift, depth (scroll parallax + phone-tilt shift + momentum lean),
 * and a light sheen that slides across with the tilt. Press lifts it; photo cards swipe.
 */
@Composable
private fun Floaty(
    slot: Slot, index: Int, base: Dp, fx: Fx, media: MediaItem?,
    bg: Color = Color.White, corner: Dp = 14.dp, plain: Boolean = false, gloss: Boolean = true,
    onClick: () -> Unit, onLong: () -> Unit = {},
    onArchive: () -> Unit = {}, onDelete: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index % 10) * 75L)
        enter.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
    }

    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1.06f else 1f, spring(0.85f, 380f), label = "lift")
    val elev by animateDpAsState(if (pressed) 28.dp else 14.dp, tween(220), label = "elev")
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableIntStateOf(0) }
    val thr by rememberUpdatedState(max(widthPx * 0.34f, 64f))
    val archiveCb by rememberUpdatedState(onArchive)
    val deleteCb by rememberUpdatedState(onDelete)
    val shape = RoundedCornerShape(corner)
    val d = slot.depth

    Box(
        Modifier
            .offset(base * slot.x, base * slot.y)
            .width(base * slot.w)
            .aspectRatio(slot.ar)
            .graphicsLayer {
                val a = fx.t.value * TAU * slot.k + slot.ph * TAU
                val bob = sin(a)
                val sway = cos(a)
                val inv = 1f - enter.value
                val tl = fx.tilt.value
                val v = fx.vel.value
                val rel = (fx.scrollPx() - fx.clusterIdx * fx.clusterPx) / fx.clusterPx
                translationX = offset.value + sway * 3.dp.toPx() + tl.x * d * 14.dp.toPx() +
                    inv * slot.out * 90.dp.toPx()
                translationY = bob * 7.dp.toPx() + tl.y * d * 9.dp.toPx() -
                    rel * (d - 1f) * 0.22f * fx.clusterPx + inv * 70.dp.toPx()
                rotationZ = slot.rot + bob * 1.4f + inv * slot.out * 12f
                rotationY = tl.x * 5f * d
                rotationX = -tl.y * 4f * d - v * 8f * d
                cameraDistance = 14f * density
                val s = (0.82f + 0.18f * enter.value) * press * (1f - abs(v) * 0.02f)
                scaleX = s; scaleY = s
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .then(
                if (plain) Modifier
                else Modifier.shadow(elev, shape, ambientColor = Color(0x33000000), spotColor = Color(0x66000000))
                    .clip(shape).background(bg)
            )
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(index) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { haptic(Hap.CLICK); onClick() },
                    onLongPress = { haptic(Hap.CONFIRM); onLong() }
                )
            }
            .then(
                if (media != null) Modifier.pointerInput(media.uri) {
                    detectHorizontalDragGestures(
                        onDragStart = { armed = 0 },
                        onDragEnd = {
                            scope.launch {
                                val v = offset.value
                                when {
                                    v > thr -> { haptic(Hap.HEAVY); archiveCb() }
                                    v < -thr -> { haptic(Hap.HEAVY); deleteCb() }
                                }
                                armed = 0
                                offset.animateTo(0f, spring(0.8f, 380f))
                            }
                        },
                        onDragCancel = { scope.launch { armed = 0; offset.animateTo(0f, spring(0.8f, 380f)) } },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + dx) }
                            val a = when { offset.value + dx > thr -> 1; offset.value + dx < -thr -> -1; else -> 0 }
                            if (a != armed) { armed = a; if (a != 0) haptic(Hap.TICK) }
                        }
                    )
                } else Modifier
            )
    ) {
        content()
        // sheen: a soft diagonal highlight that slides with phone tilt
        if (gloss && media != null) Box(
            Modifier.matchParentSize().drawBehind {
                val cx = size.width * (0.5f + fx.tilt.value.x * 0.9f)
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color(0x30FFFFFF), Color.Transparent),
                        start = Offset(cx - size.width * 0.55f, 0f),
                        end = Offset(cx + size.width * 0.25f, size.height)
                    )
                )
            }
        )
        val o = offset.value
        if (o != 0f) Box(
            Modifier.matchParentSize().clip(shape)
                .background((if (o > 0) BoardGreen else BoardRed).copy(alpha = (abs(o) / thr).coerceIn(0f, 1f) * 0.85f)),
            contentAlignment = if (o > 0) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Icon(
                if (o > 0) Icons.Filled.Archive else Icons.Filled.Delete, null, tint = Color.White,
                modifier = Modifier.padding(horizontal = 10.dp).size(22.dp)
            )
        }
    }
}

@Composable
private fun Pic(item: MediaItem, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.background(Cell)) {
        AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (item.isVideo) Box(
            Modifier.align(Alignment.Center).size(26.dp).clip(CircleShape).background(Color(0x66000000)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun BoxScope.FavMark(on: Boolean) {
    if (on) Icon(
        Icons.Filled.Favorite, null, tint = Ember,
        modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(14.dp)
    )
}
