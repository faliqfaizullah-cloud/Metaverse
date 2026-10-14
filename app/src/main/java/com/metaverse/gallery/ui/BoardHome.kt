package com.metaverse.gallery.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import com.metaverse.gallery.ui.theme.Mono
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

private val DayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val TimeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private fun MediaItem.timeText(): String =
    Instant.ofEpochMilli(takenMillis).atZone(ZoneId.systemDefault()).format(TimeFmt)

/** Where a floating card sits (x, y, width in units of screen width), its shape and how it drifts. */
private class Slot(
    val x: Float, val y: Float, val w: Float, val ar: Float,
    val rot: Float, val k: Int, val ph: Float
)

private val Slots = listOf(
    Slot(0.30f, 0.00f, 0.34f, 1.20f, 0f, 1, 0.00f),    // 0 yellow date card
    Slot(-0.02f, 0.06f, 0.30f, 0.80f, 0f, 2, 0.15f),   // 1 photo + label
    Slot(0.32f, 0.22f, 0.36f, 2.20f, 0f, 1, 0.30f),    // 2 link card
    Slot(0.66f, 0.10f, 0.40f, 0.85f, -4f, 1, 0.45f),   // 3 stats card
    Slot(0.02f, 0.46f, 0.28f, 0.80f, -8f, 2, 0.60f),   // 4 latest-time card
    Slot(0.29f, 0.38f, 0.38f, 0.78f, 8f, 1, 0.75f),    // 5 hero photo stack
    Slot(0.69f, 0.60f, 0.28f, 0.90f, 0f, 2, 0.90f),    // 6 photo + tag
    Slot(0.02f, 0.84f, 0.30f, 1.00f, 10f, 1, 0.20f),   // 7 photo + lilac chip
    Slot(0.30f, 0.92f, 0.38f, 1.20f, 0f, 2, 0.55f),    // 8 person card
    Slot(0.64f, 0.94f, 0.40f, 1.15f, 0f, 1, 0.80f)     // 9 amber camera card
)
private const val PER_CLUSTER = 9
private const val CLUSTER_H = 1.34f

private class Stats(val photos: Int, val videos: Int, val favs: Int, val latest: MediaItem?)

class BoardActions(
    val open: (MediaItem) -> Unit,
    val favorite: (MediaItem) -> Unit,
    val archive: (MediaItem) -> Unit,
    val delete: (MediaItem) -> Unit,
    val camera: () -> Unit
)

/**
 * Home "canvas": photos and little info cards float on a dotted board, tilted and drifting.
 * Tap = open, long-press = favourite, swipe right = archive, swipe left = delete (on photo cards).
 */
@Composable
fun BoardHome(
    items: List<MediaItem>,
    favs: Set<String>,
    topPad: Dp,
    bottomPad: Dp,
    actions: BoardActions
) {
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
    val clusterCount = (items.size + PER_CLUSTER - 1) / PER_CLUSTER

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
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topPad + 38.dp, bottom = bottomPad)
        ) {
            items(clusterCount, key = { it }) { c ->
                val chunk = List(PER_CLUSTER) { items[(c * PER_CLUSTER + it) % items.size] }
                Cluster(chunk, c, stats, base, t, favs, actions)
            }
        }
        // corner captions, like the reference board
        Row(
            Modifier.padding(top = topPad + 6.dp, start = 18.dp, end = 18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Meta", color = Ink1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("verse", color = Soft, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text("${items.size} memories", color = Soft, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Cluster(
    chunk: List<MediaItem>, index: Int, stats: Stats, base: Dp,
    t: State<Float>, favs: Set<String>, a: BoardActions
) {
    fun m(i: Int) = chunk[i]
    fun fav(i: Int) = m(i).uri.toString() in favs
    val o = index * 10
    val first = m(0)

    Box(Modifier.fillMaxWidth().height(base * CLUSTER_H)) {
        // 0 — yellow date card
        Floaty(Slots[0], o + 0, base, t, null, bg = Yellow, onClick = { a.open(first) }) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Text(first.bucket.ifBlank { "Museum" }, color = Color(0x99000000), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(first.date.format(DayFmt), color = Ink1, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        // 1 — photo with white label
        Floaty(Slots[1], o + 1, base, t, m(1), onClick = { a.open(m(1)) }, onLong = { a.favorite(m(1)) },
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
        Floaty(Slots[2], o + 2, base, t, null, corner = 12.dp, onClick = { a.open(m(1)) }) {
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
        Floaty(Slots[3], o + 3, base, t, m(2), corner = 10.dp, bg = Olive, onClick = { a.open(m(2)) }, onLong = { a.favorite(m(2)) },
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
        // 4 — latest capture card
        Floaty(Slots[4], o + 4, base, t, null, corner = 18.dp, onClick = { stats.latest?.let(a.open) }) {
            Column(Modifier.fillMaxSize().padding(11.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
                    Text("ON", color = Color.White, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Latest capture", color = Color(0xFFB4B4B6), fontFamily = Hand, fontSize = 11.sp, maxLines = 1)
                    Text(stats.latest?.timeText() ?: "--:--", color = Ink1, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        // 5 — hero stack
        Floaty(Slots[5], o + 5, base, t, m(3), plain = true, onClick = { a.open(m(3)) }, onLong = { a.favorite(m(3)) },
            onArchive = { a.archive(m(3)) }, onDelete = { a.delete(m(3)) }) {
            Pic(m(5), Modifier.align(Alignment.TopEnd).fillMaxWidth(0.9f).fillMaxHeight(0.92f)
                .graphicsLayer { rotationZ = 7f }.shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)))
            Pic(m(4), Modifier.align(Alignment.BottomStart).fillMaxWidth(0.9f).fillMaxHeight(0.92f)
                .graphicsLayer { rotationZ = -5f }.shadow(6.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)))
            Pic(m(3), Modifier.align(Alignment.Center).fillMaxWidth(0.92f).fillMaxHeight(0.96f)
                .shadow(14.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)))
            FavMark(fav(3))
        }
        // 6 — photo card with amber tag
        Floaty(Slots[6], o + 6, base, t, m(6), corner = 16.dp, onClick = { a.open(m(6)) }, onLong = { a.favorite(m(6)) },
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
        Floaty(Slots[7], o + 7, base, t, m(7), corner = 12.dp, onClick = { a.open(m(7)) }, onLong = { a.favorite(m(7)) },
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
        Floaty(Slots[8], o + 8, base, t, m(8), corner = 18.dp, onClick = { a.open(m(8)) }, onLong = { a.favorite(m(8)) },
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
        // 9 — amber camera card
        Floaty(Slots[9], o + 9, base, t, null, bg = Amber, corner = 14.dp, onClick = { a.camera() }) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text("✦", color = Ink1, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("${stats.photos + stats.videos} items", color = Color(0x99000000), fontSize = 7.sp, maxLines = 1)
                Text(first.bucket.ifBlank { "Camera" }, color = Ink1, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(9.dp).size(24.dp).clip(CircleShape).background(Color(0xFFEFDDA0)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.PhotoCamera, null, tint = Ink1, modifier = Modifier.size(13.dp)) }
        }
    }
}

/**
 * One floating card: pops in with a spring, drifts and tilts forever, lifts when pressed,
 * and (if it carries a photo) can be swiped right to archive / left to delete.
 */
@Composable
private fun Floaty(
    slot: Slot, index: Int, base: Dp, t: State<Float>, media: MediaItem?,
    bg: Color = Color.White, corner: Dp = 14.dp, plain: Boolean = false,
    onClick: () -> Unit, onLong: () -> Unit = {},
    onArchive: () -> Unit = {}, onDelete: () -> Unit = {},
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay((index % 10) * 60L); enter.animateTo(1f, spring(0.6f, 200f)) }

    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1.07f else 1f, spring(0.55f, 420f), label = "lift")
    val elev by animateDpAsState(if (pressed) 30.dp else 14.dp, label = "elev")
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableIntStateOf(0) }
    val thr by rememberUpdatedState(max(widthPx * 0.34f, 64f))
    val archiveCb by rememberUpdatedState(onArchive)
    val deleteCb by rememberUpdatedState(onDelete)
    val shape = RoundedCornerShape(corner)

    Box(
        Modifier
            .offset(base * slot.x, base * slot.y)
            .width(base * slot.w)
            .aspectRatio(slot.ar)
            .graphicsLayer {
                val tt = t.value * 6.2831855f
                val bob = sin(tt * slot.k + slot.ph * 6.2831855f)
                val sway = cos(tt * slot.k + slot.ph * 6.2831855f)
                val e = enter.value
                translationY = bob * 7.dp.toPx() + (1f - e) * 70.dp.toPx()
                translationX = offset.value + sway * 3.dp.toPx()
                rotationZ = slot.rot + bob * 1.4f + (1f - e) * -10f
                val s = (0.55f + 0.45f * e) * press
                scaleX = s; scaleY = s
                alpha = e.coerceIn(0f, 1f)
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
                                offset.animateTo(0f, spring(0.7f, 380f))
                            }
                        },
                        onDragCancel = { scope.launch { armed = 0; offset.animateTo(0f, spring(0.7f, 380f)) } },
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
