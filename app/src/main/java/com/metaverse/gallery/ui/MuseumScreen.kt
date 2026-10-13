package com.metaverse.gallery.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.TagFaces
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.metaverse.gallery.MuseumViewModel
import com.metaverse.gallery.data.MediaItem
import com.metaverse.gallery.ui.theme.*
import com.metaverse.gallery.util.Hap
import com.metaverse.gallery.util.rememberHaptics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

@Composable
fun MuseumScreen(vm: MuseumViewModel) {
    val haptic = rememberHaptics()
    val ctx = LocalContext.current
    val items by vm.items.collectAsState()
    val month by vm.month.collectAsState()
    val selected by vm.selectedDay.collectAsState()
    val full by vm.showFullMuseum.collectAsState()
    val query by vm.query.collectAsState()
    val today = remember { LocalDate.now() }

    val visible = remember(items, query) {
        if (query.isBlank()) items
        else items.filter { it.name.contains(query, true) || it.bucket.contains(query, true) }
    }
    val byDate = remember(visible) { visible.groupBy { it.date } }
    val moods: SnapshotStateMap<LocalDate, Int> = remember { mutableStateMapOf() }
    var boardOpen by rememberSaveable { mutableStateOf(true) }
    val pinned = remember { mutableStateListOf<Uri>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { pinned.add(0, uri); haptic(Hap.CONFIRM) }
    }
    var showCamera by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selected != null || full) {
        if (full) vm.showFullMuseum.value = false else vm.selectedDay.value = null
    }

    val boardPhotos: List<Uri> = (pinned + (byDate[today]?.map { it.uri } ?: items.take(6).map { it.uri })).take(6)

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp)
                .then(if (selected != null) Modifier.blur(18.dp) else Modifier)
        ) {
            Header(haptic)
            CalendarBlock(
                month = month, today = today, byDate = byDate, moods = moods,
                onPrev = { haptic(Hap.TICK); vm.shiftMonth(-1) },
                onNext = { haptic(Hap.TICK); vm.shiftMonth(1) },
                onDay = { d, list ->
                    if (list.isNotEmpty()) { haptic(Hap.CLICK); vm.selectedDay.value = d }
                    else {
                        haptic(Hap.TICK)
                        moods[d] = ((moods[d] ?: 0) + 1) % 3
                    }
                }
            )
            FooterRow(today)
            Spacer(Modifier.height(18.dp))
            BoardHeader(
                open = boardOpen,
                onToggle = { haptic(Hap.CLICK); boardOpen = !boardOpen },
                onGrid = { haptic(Hap.CONFIRM); vm.selectedDay.value = null; vm.showFullMuseum.value = true }
            )
            AnimatedVisibility(boardOpen) {
                BoardBody(
                    photos = boardPhotos,
                    onCamera = {
                        haptic(Hap.CLICK)
                        showCamera = true
                    }
                )
            }
        }

        // floating glass search + add
        Row(
            Modifier.align(Alignment.BottomCenter).systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f).height(58.dp).clip(CircleShape)
                    .background(Color(0xCC1A1A1A)).border(1.dp, Color(0x33FFFFFF), CircleShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search Here . . .", color = Color.White, fontSize = 17.sp)
                    BasicTextField(
                        value = query, onValueChange = { vm.query.value = it },
                        singleLine = true, cursorBrush = SolidColor(Ember),
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(Color(0xCC1A1A1A))
                    .border(1.dp, Color(0x55FFFFFF), CircleShape)
                    .clickable {
                        haptic(Hap.HEAVY)
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        }

        // in-app camera
        AnimatedVisibility(
            showCamera,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 6 },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { it / 6 }
        ) {
            CameraScreen(
                lastPhoto = items.firstOrNull { !it.isVideo }?.uri,
                onClose = { showCamera = false },
                onOpenGallery = {
                    showCamera = false
                    vm.selectedDay.value = null
                    vm.showFullMuseum.value = true
                }
            )
        }

        // polaroid pop-up
        AnimatedVisibility(selected != null, enter = fadeIn(), exit = fadeOut()) {
            val day = selected
            if (day != null) PolaroidOverlay(
                day = day, list = byDate[day].orEmpty(),
                onDismiss = { vm.selectedDay.value = null },
                onFull = { haptic(Hap.CONFIRM); vm.showFullMuseum.value = true }
            )
        }

        // full museum carousel (a day, or everything when opened from the board grid button)
        AnimatedVisibility(full, enter = fadeIn(), exit = fadeOut()) {
            val day = selected
            MuseumCarousel(
                title = day?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "Your Museum",
                list = if (day != null) byDate[day].orEmpty() else visible,
                onClose = { vm.showFullMuseum.value = false; vm.selectedDay.value = null }
            )
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF141414))
            .border(1.dp, Color(0xFF3A3A3A), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun Header(haptic: (Hap) -> Unit) {
    val m = remember { LocalDate.now() }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("YOUR MUSEUM", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.5).sp)
            Text(
                "${m.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())} ${m.year}",
                color = Muted, fontSize = 16.sp
            )
        }
        RoundIcon(Icons.Filled.Settings) { haptic(Hap.CLICK) }
        Spacer(Modifier.width(10.dp))
        RoundIcon(Icons.Filled.MoreHoriz) { haptic(Hap.CLICK) }
    }
}

@Composable
private fun CalendarBlock(
    month: java.time.YearMonth,
    today: LocalDate,
    byDate: Map<LocalDate, List<MediaItem>>,
    moods: Map<LocalDate, Int>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDay: (LocalDate, List<MediaItem>) -> Unit
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(26.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.KeyboardDoubleArrowLeft, null, tint = Color(0xFFBBBBBB),
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onPrev))
            Text(
                month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()),
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, modifier = Modifier.width(210.dp)
            )
            Icon(Icons.Filled.KeyboardDoubleArrowRight, null, tint = Color(0xFFBBBBBB),
                modifier = Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onNext))
        }
        Spacer(Modifier.height(18.dp))

        val names = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val todayCol = today.dayOfWeek.value % 7
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            names.forEachIndexed { i, n ->
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xFF1B1B1B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(n, color = if (i == todayCol) Color.White else Color(0xFFAAAAAA), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        val first = month.atDay(1)
        val lead = first.dayOfWeek.value % 7
        val total = month.lengthOfMonth()
        val rows = (lead + total + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(rows) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(7) { c ->
                        val dayNum = r * 7 + c - lead + 1
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            if (dayNum in 1..total) {
                                val d = month.atDay(dayNum)
                                DayCell(d, d == today, byDate[d].orEmpty(), moods[d] ?: autoMood(d, today), onDay)
                            } else {
                                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Color(0xFF151515)))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun autoMood(d: LocalDate, today: LocalDate): Int =
    if (d.isAfter(today)) 0 else when ((d.dayOfMonth * 3 + d.monthValue) % 7) { 1 -> 1; 3 -> 2; else -> 0 }

@Composable
private fun DayCell(d: LocalDate, isToday: Boolean, list: List<MediaItem>, mood: Int, onDay: (LocalDate, List<MediaItem>) -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.5f, 400f), label = "press")

    Box(
        Modifier.fillMaxSize().scale(s).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1B1B1B))
            .clickable(interactionSource = press, indication = null) { onDay(d, list) }
    ) {
        Text(
            "${d.dayOfMonth}",
            color = if (isToday) Color.White else Color(0xFF7A7A7A),
            fontSize = 15.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 5.dp)
        )
        if (list.isEmpty()) {
            when (mood) {
                1 -> Icon(Icons.Outlined.TagFaces, null, tint = Color(0xFF555555), modifier = Modifier.align(Alignment.Center).size(26.dp))
                2 -> Icon(Icons.Outlined.Healing, null, tint = Color(0xFF555555), modifier = Modifier.align(Alignment.Center).size(26.dp))
            }
        }
    }
    if (list.isNotEmpty()) {
        Box(
            Modifier.offset(x = (-6).dp, y = (-10).dp).size(58.dp).scale(s)
                .then(if (isToday) Modifier.background(Ember, StampShape()).padding(3.dp) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                list.first().uri, null,
                Modifier.fillMaxSize().clip(StampShape()).border(1.dp, Color.White, StampShape()),
                contentScale = ContentScale.Crop
            )
            Text(
                caption(list), color = Color.White, fontFamily = Hand, fontSize = 8.sp, lineHeight = 8.sp,
                maxLines = 2, modifier = Modifier.align(Alignment.BottomStart).padding(start = 3.dp, bottom = 1.dp)
            )
        }
    }
}

private fun caption(list: List<MediaItem>): String {
    val b = list.first().bucket
    return if (b.isNotBlank() && b != "Camera") b.lowercase() else "${list.size} moments"
}

@Composable
private fun FooterRow(today: LocalDate) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.LocationOn, null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text("Lagos", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.width(22.dp))
        Icon(Icons.Filled.CalendarMonth, null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(today.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")), color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("TODAY", color = Muted, fontSize = 13.sp)
            Text("A Rainy Afternoon", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BoardHeader(open: Boolean, onToggle: () -> Unit, onGrid: () -> Unit) {
    val rot by animateFloatAsState(if (open) 0f else 180f, spring(), label = "chev")
    Box(Modifier.fillMaxWidth().background(Color(0xFF0F0F0F)).height(64.dp)) {
        Text("Todays Board", color = Color(0xFFAAAAAA), fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 26.dp))
        Box(
            Modifier.align(Alignment.TopCenter).width(180.dp).height(60.dp)
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(Color(0xFF1B1B1B))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.ArrowDropUp, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(40.dp).rotate(rot)) }
        Icon(Icons.Filled.GridView, null, tint = Color(0xFFBBBBBB), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(44.dp).clip(CircleShape).clickable(onClick = onGrid).padding(7.dp))
    }
}

@Composable
private fun BoardBody(photos: List<Uri>, onCamera: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0F0F0F)).padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.width(92.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.take(2).forEach {
                AsyncImage(it, null, Modifier.fillMaxWidth().aspectRatio(0.85f).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            }
            if (photos.isEmpty()) Box(Modifier.fillMaxWidth().aspectRatio(0.85f).background(Color(0xFF1B1B1B)))
        }
        Box(
            Modifier.width(52.dp).height(200.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF151515))
                .drawDashedBorder().clickable(onClick = onCamera),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PhotoCamera, null, tint = Color(0xFF555555), modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayPill(seed = 11, leadingMic = true)
            Text("SEE YOU AGAIN / TYLER THE CREATOR", fontFamily = Mono, fontSize = 11.sp, color = Color.White, maxLines = 1)
            PlayPill(seed = 23)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Headphones, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Museum Of Sound", color = Color(0xFFAAAAAA), fontSize = 16.sp)
            }
            Box(Modifier.fillMaxWidth().height(110.dp).background(Color(0xFF1E1E1E)).padding(10.dp)) {
                Icon(Icons.Filled.AutoFixNormal, null, tint = Color(0xFFAAAAAA), modifier = Modifier.align(Alignment.TopEnd).size(20.dp).clickable { note = "" })
                BasicTextField(
                    note, { note = it }, cursorBrush = SolidColor(Ember),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    modifier = Modifier.fillMaxSize().padding(top = 22.dp)
                )
                if (note.isEmpty()) Text("write a note…", color = Color(0xFF666666), fontFamily = Hand, fontSize = 16.sp, modifier = Modifier.padding(top = 22.dp))
            }
        }
    }
}

private fun Modifier.drawDashedBorder(): Modifier = this.drawBehind {
    drawRoundRect(
        color = Color(0xFF3A3A3A), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 3f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
    )
}

@Composable
private fun PolaroidOverlay(day: LocalDate, list: List<MediaItem>, onDismiss: () -> Unit, onFull: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val sc by animateFloatAsState(if (shown) 1f else 0.5f, spring(0.5f, 300f), label = "sc")
    val tilt by animateFloatAsState(if (shown) -4f else -25f, spring(0.45f, 200f), label = "tilt")

    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(sc)) {
                if (list.size > 1) PolaroidCard(list[1], "", Modifier.offset(26.dp, (-18).dp).rotate(5f))
                PolaroidCard(list.first(), "${day.month.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()).lowercase()} ${day.dayOfMonth} ${caption(list)}", Modifier.rotate(tilt))
            }
            Spacer(Modifier.height(48.dp))
            Text(
                "View Full Museum", color = Color(0xFFDDDDDD), fontSize = 18.sp,
                textDecoration = TextDecoration.Underline, modifier = Modifier.clickable(onClick = onFull).padding(12.dp)
            )
        }
    }
}

@Composable
private fun PolaroidCard(item: MediaItem, caption: String, modifier: Modifier) {
    Column(
        modifier.width(250.dp).background(Polaroid).padding(12.dp)
    ) {
        AsyncImage(
            item.uri, null,
            Modifier.fillMaxWidth().height(250.dp).border(2.dp, Color.White),
            contentScale = ContentScale.Crop
        )
        Text(caption, color = Color.White, fontFamily = Hand, fontSize = 30.sp, lineHeight = 30.sp, modifier = Modifier.padding(top = 10.dp, bottom = 14.dp, start = 4.dp).heightIn(min = 64.dp))
    }
}
