package com.metaverse.gallery.ui

import android.app.Activity
import android.app.RecoverableSecurityException
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.shadow
import kotlin.math.sin
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.metaverse.gallery.MuseumViewModel
import com.metaverse.gallery.data.MediaItem
import com.metaverse.gallery.ui.theme.Cell
import com.metaverse.gallery.ui.theme.Ember
import com.metaverse.gallery.ui.theme.Hand
import com.metaverse.gallery.ui.theme.Mono
import com.metaverse.gallery.util.Hap
import com.metaverse.gallery.util.rememberHaptics
import kotlinx.coroutines.launch

private val HomeBg = Color(0xFF080808)

/**
 * Home: a masonry wall of everything in the museum, a floating glass nav pill
 * (home / search / import / favourites / profile) and a glossy bottom bar
 * (carousel · camera · more).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: MuseumViewModel) {
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val items by vm.items.collectAsState()
    val extras by vm.extras.collectAsState()
    val favs by vm.favorites.collectAsState()
    val query by vm.query.collectAsState()
    val archived by vm.archived.collectAsState()
    val ctx = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(0) }       // 0 home, 1 search, 3 favourites
    var cols by rememberSaveable { mutableIntStateOf(4) }
    var sheet by remember { mutableStateOf(false) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    var showCarousel by rememberSaveable { mutableStateOf(false) }
    var viewerAt by rememberSaveable { mutableIntStateOf(-1) }
    var snack by remember { mutableStateOf<Snack?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    LaunchedEffect(snack) { if (snack != null) { delay(3400); snack = null } }

    val all = remember(items, extras) { extras + items }
    val shown = remember(all, favs, query, tab, archived) {
        var l = all.filter { (it.uri.toString() in archived) == (tab == 5) }
        if (tab == 3) l = l.filter { it.uri.toString() in favs }
        if (tab == 1 && query.isNotBlank()) {
            val q = query.trim()
            l = l.filter {
                it.name.contains(q, true) || it.bucket.contains(q, true) || it.date.toString().contains(q)
            }
        }
        l
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { vm.addExtra(uri); haptic(Hap.CONFIRM) }
    }
    val gridState = rememberLazyStaggeredGridState()

    // ---- delete (asks the system for permission; photos are removed from the device) ----
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val p = pendingDelete
            if (Build.VERSION.SDK_INT == 29 && p != null) {
                try { ctx.contentResolver.delete(p.uri, null, null) } catch (_: Exception) {}
            }
            haptic(Hap.CONFIRM)
            snack = Snack("Deleted")
        }
        pendingDelete = null
    }
    fun deleteItem(item: MediaItem) {
        haptic(Hap.HEAVY)
        if (extras.any { it.uri == item.uri }) {
            vm.removeExtra(item.uri); snack = Snack("Removed"); return
        }
        pendingDelete = item
        val cr = ctx.contentResolver
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val pi = MediaStore.createDeleteRequest(cr, listOf(item.uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            } else {
                cr.delete(item.uri, null, null)
                snack = Snack("Deleted")
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                deleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } else {
                snack = Snack("Couldn't delete")
            }
        }
    }
    fun archiveItem(item: MediaItem) {
        val un = item.uri.toString() in archived
        vm.setArchived(item.uri, !un)
        haptic(Hap.CONFIRM)
        snack = Snack(if (un) "Restored" else "Archived", "UNDO") { vm.setArchived(item.uri, un) }
    }

    BackHandler(enabled = sheet || tab != 0) {
        if (sheet) sheet = false else { tab = 0; vm.query.value = "" }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val searching = tab == 1

    val bgColor by animateColorAsState(if (tab == 0) BoardBg else HomeBg, tween(400), label = "bg")
    val navInf = rememberInfiniteTransition(label = "navfloat")
    val navBob = navInf.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "nb")

    Box(Modifier.fillMaxSize().background(bgColor)) {
        // ---------- wall ----------
        if (shown.isEmpty()) {
            Text(
                when {
                    tab == 5 -> "nothing archived —\nswipe a photo right"
                    tab == 3 -> "no favourites yet —\nlong-press a photo ♥"
                    tab == 1 && query.isNotBlank() -> "nothing found"
                    else -> "your museum is empty —\ntake a photo with +"
                },
                color = if (tab == 0) Color(0xFF77777A) else Color(0xFF666666), fontFamily = Hand, fontSize = 22.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (tab == 0 && shown.isNotEmpty()) {
            BoardHome(
                items = shown, favs = favs,
                topPad = statusTop, bottomPad = 250.dp,
                actions = BoardActions(
                    open = { viewerAt = shown.indexOf(it) },
                    favorite = { vm.toggleFavorite(it.uri) },
                    archive = { archiveItem(it) },
                    delete = { deleteItem(it) },
                    camera = { haptic(Hap.HEAVY); showCamera = true }
                )
            )
        } else LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(cols),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                top = statusTop + 14.dp + if (searching || tab == 5) 64.dp else 0.dp,
                bottom = 230.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalItemSpacing = 10.dp
        ) {
            items(shown, key = { it.uri.toString() + it.isVideo }) { item ->
                SwipeTile(
                    item = item,
                    fav = item.uri.toString() in favs,
                    archiveMode = tab == 5,
                    onClick = { haptic(Hap.CLICK); viewerAt = shown.indexOf(item) },
                    onLong = {
                        val now = vm.toggleFavorite(item.uri)
                        haptic(if (now) Hap.CONFIRM else Hap.HEAVY)
                    },
                    onArchive = { archiveItem(item) },
                    onDelete = { deleteItem(item) }
                )
            }
        }

        // ---------- search field ----------
        AnimatedVisibility(
            searching,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }
        ) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Row(
                Modifier.padding(top = statusTop + 8.dp).padding(horizontal = 18.dp).fillMaxWidth().height(50.dp)
                    .clip(CircleShape).background(Color(0xE61E1E1E)).border(1.dp, Color(0x22FFFFFF), CircleShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search your museum…", color = Color(0xFF8A8A8A), fontSize = 16.sp)
                    BasicTextField(
                        value = query, onValueChange = { vm.query.value = it }, singleLine = true,
                        cursorBrush = SolidColor(Ember),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    )
                }
                if (query.isNotEmpty()) Icon(
                    Icons.Filled.Close, null, tint = Color(0xFFAAAAAA),
                    modifier = Modifier.size(22.dp).clip(CircleShape).clickable { haptic(Hap.TICK); vm.query.value = "" }
                )
            }
        }

        // ---------- archive header ----------
        AnimatedVisibility(
            tab == 5,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it }, exit = fadeOut() + slideOutVertically { -it }
        ) {
            Row(
                Modifier.padding(top = statusTop + 8.dp).padding(horizontal = 18.dp).fillMaxWidth().height(50.dp)
                    .clip(CircleShape).background(Color(0xE61E1E1E)).border(1.dp, Color(0x22FFFFFF), CircleShape)
                    .padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Archive, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("ARCHIVE", color = Color.White, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("  ·  ${shown.size}", color = Color(0xFF8A8A8A), fontFamily = Mono, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.Close, null, tint = Color.White,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { haptic(Hap.CLICK); tab = 0 }.padding(7.dp)
                )
            }
        }

        // ---------- bottom stack ----------
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(
                sheet,
                enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.9f),
                modifier = Modifier.align(Alignment.End).padding(end = 22.dp, bottom = 12.dp)
            ) {
                Column(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xF01E1E1E))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SheetRow(Icons.Filled.PhotoLibrary, "Import") {
                        sheet = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    }
                    SheetRow(Icons.Filled.PhotoCamera, "Camera") { sheet = false; showCamera = true }
                    SheetRow(Icons.Filled.CalendarMonth, "Museum calendar") { sheet = false; showCalendar = true }
                    SheetRow(Icons.Filled.Inventory2, "Archive (${archived.size})") { sheet = false; tab = 5; vm.query.value = "" }
                    SheetRow(Icons.Filled.GridView, "Grid: $cols columns") {
                        cols = when (cols) { 4 -> 3; 3 -> 2; else -> 4 }
                    }
                }
            }

            AnimatedVisibility(
                snack != null,
                enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }
            ) {
                val sn = snack
                if (sn != null) Row(
                    Modifier.padding(bottom = 12.dp).clip(CircleShape).background(Color(0xF0262626))
                        .border(1.dp, Color(0x22FFFFFF), CircleShape)
                        .padding(start = 20.dp, end = if (sn.action != null) 8.dp else 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(sn.text, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                    if (sn.action != null) Text(
                        sn.label, color = Color(0xFFFFD60A), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clip(CircleShape).clickable { haptic(Hap.CLICK); sn.action.invoke(); snack = null }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
            Box(Modifier.graphicsLayer { translationY = sin(navBob.value * 6.2831855f) * 3.dp.toPx() }) {
            NavPill(
                tab = tab,
                avatar = all.firstOrNull { !it.isVideo }?.uri,
                onHome = {
                    if (tab == 0) scope.launch { gridState.animateScrollToItem(0) }
                    tab = 0; vm.query.value = ""; keyboard?.hide()
                },
                onSearch = { tab = if (tab == 1) 0 else 1; if (tab == 0) vm.query.value = "" },
                onAdd = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onHeart = { tab = if (tab == 3) 0 else 3; vm.query.value = ""; keyboard?.hide() },
                onProfile = { showCalendar = true },
                haptic = haptic
            )
            }
            Spacer(Modifier.height(14.dp))
            BottomBar(
                stackA = all.getOrNull(0)?.uri, stackB = all.getOrNull(1)?.uri,
                sheetOpen = sheet,
                onStack = { haptic(Hap.CONFIRM); showCarousel = true },
                onCamera = { haptic(Hap.HEAVY); showCamera = true },
                onMore = { haptic(Hap.CLICK); sheet = !sheet }
            )
        }

        // ---------- overlays ----------
        AnimatedVisibility(showCalendar, enter = fadeIn() + scaleIn(initialScale = 0.96f), exit = fadeOut() + scaleOut(targetScale = 0.96f)) {
            MuseumScreen(vm, onExit = { showCalendar = false })
        }
        AnimatedVisibility(
            showCarousel,
            enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)
        ) {
            MuseumCarousel("Your Museum", shown, 0) { showCarousel = false }
        }
        AnimatedVisibility(
            showCamera,
            enter = fadeIn() + slideInVertically { it / 6 }, exit = fadeOut() + slideOutVertically { it / 6 }
        ) {
            CameraScreen(
                lastPhoto = all.firstOrNull { !it.isVideo }?.uri,
                onClose = { showCamera = false },
                onOpenGallery = { showCamera = false; showCarousel = true }
            )
        }
        AnimatedVisibility(
            viewerAt >= 0 && shown.isNotEmpty(),
            enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut() + scaleOut(targetScale = 0.85f)
        ) {
            PhotoViewer(
                list = shown, start = viewerAt.coerceAtLeast(0),
                favs = favs, archiveMode = tab == 5,
                onFavorite = { vm.toggleFavorite(it.uri) },
                onArchive = { archiveItem(it) },
                onDelete = { deleteItem(it) },
                onClose = { viewerAt = -1 }
            )
        }
    }
}

private data class Snack(val text: String, val label: String = "", val action: (() -> Unit)? = null)

private val ArchiveGreen = Color(0xFF22B573)
private val DeleteRed = Color(0xFFE5483F)

/** A wall tile: tap = open, long-press = favourite, swipe right = archive, swipe left = delete. */
@Composable
private fun SwipeTile(
    item: MediaItem, fav: Boolean, archiveMode: Boolean,
    onClick: () -> Unit, onLong: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit
) {
    val haptic = rememberHaptics()
    val scope = rememberCoroutineScope()
    val ratio = if (item.width > 0 && item.height > 0)
        (item.width.toFloat() / item.height).coerceIn(0.55f, 1.7f) else 0.78f
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableStateOf(false) }
    val thr by rememberUpdatedState(max(widthPx * 0.34f, 64f))
    val archiveCb by rememberUpdatedState(onArchive)
    val deleteCb by rememberUpdatedState(onDelete)
    val s by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.6f, 500f), label = "tile")
    val iconScale by animateFloatAsState(if (armed != 0) 1.25f else 0.9f, spring(0.4f, 500f), label = "icon")
    val o = offset.value

    Box(
        Modifier.fillMaxWidth().aspectRatio(ratio).scale(s).clip(RoundedCornerShape(5.dp))
            .onSizeChanged { widthPx = it.width.toFloat() }
    ) {
        if (o != 0f) Box(
            Modifier.matchParentSize().background(if (o > 0) ArchiveGreen else DeleteRed),
            contentAlignment = if (o > 0) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Icon(
                if (o > 0) (if (archiveMode) Icons.Filled.Unarchive else Icons.Filled.Archive) else Icons.Filled.Delete,
                null, tint = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp).size(22.dp).scale(iconScale)
            )
        }
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { translationX = offset.value }
                .background(Cell)
                .pointerInput(item.uri) {
                    detectTapGestures(
                        onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                        onTap = { onClick() },
                        onLongPress = { onLong() }
                    )
                }
                .pointerInput(item.uri) {
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
                }
        ) {
            AsyncImage(item.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (item.isVideo) Box(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(10.dp)).background(Color(0x40FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) { Text("VIDEO", color = Color(0xCCFFFFFF), fontFamily = Mono, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
            if (fav) Icon(
                Icons.Filled.Favorite, null, tint = Ember,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(16.dp)
            )
        }
    }
}

@Composable
private fun NavPill(
    tab: Int, avatar: Uri?,
    onHome: () -> Unit, onSearch: () -> Unit, onAdd: () -> Unit, onHeart: () -> Unit, onProfile: () -> Unit,
    haptic: (Hap) -> Unit
) {
    Row(
        Modifier.height(66.dp).shadow(20.dp, CircleShape).clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color(0xE63A3A3A), Color(0xE61C1C1C))))
            .border(1.dp, Color(0x26FFFFFF), CircleShape).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(Icons.Outlined.Home, Icons.Outlined.Home, tab == 0) { haptic(Hap.CLICK); onHome() }
        NavItem(Icons.Outlined.Search, Icons.Outlined.Search, tab == 1) { haptic(Hap.CLICK); onSearch() }
        NavItem(Icons.Outlined.Add, Icons.Outlined.Add, false) { haptic(Hap.HEAVY); onAdd() }
        NavItem(Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite, tab == 3) { haptic(Hap.CLICK); onHeart() }
        Box(
            Modifier.size(52.dp).clip(CircleShape).clickable { haptic(Hap.CONFIRM); onProfile() },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Cell).border(2.dp, Color(0xFFE8A33D), CircleShape)) {
                if (avatar != null) AsyncImage(avatar, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, selectedIcon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "navbg")
    val tint by animateColorAsState(if (selected) Color(0xFF111111) else Color(0xFFD0D0D0), label = "navtint")
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.86f else 1f, spring(0.5f, 500f), label = "navscale")
    Box(
        Modifier.size(54.dp).scale(s).clip(CircleShape).background(bg)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(if (selected) selectedIcon else icon, null, tint = tint, modifier = Modifier.size(26.dp)) }
}

@Composable
private fun BottomBar(
    stackA: Uri?, stackB: Uri?, sheetOpen: Boolean,
    onStack: () -> Unit, onCamera: () -> Unit, onMore: () -> Unit
) {
    val rot by animateFloatAsState(if (sheetOpen) 180f else 0f, spring(0.7f, 300f), label = "chev")
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(72.dp)
            .shadow(22.dp, RoundedCornerShape(40.dp))
            .clip(RoundedCornerShape(40.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF4B4B4B), Color(0xFF1F1F1F))))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(40.dp))
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // stacked polaroids
        Box(Modifier.size(54.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onStack)) {
            StackCard(stackB, Modifier.align(Alignment.Center).offset(x = (-4).dp, y = (-2).dp).rotate(-14f))
            StackCard(stackA, Modifier.align(Alignment.Center).offset(x = 4.dp, y = 2.dp).rotate(9f))
        }
        // camera pill
        val press = remember { MutableInteractionSource() }
        val pressed by press.collectIsPressedAsState()
        val s by animateFloatAsState(if (pressed) 0.92f else 1f, spring(0.5f, 500f), label = "plus")
        Box(
            Modifier.width(102.dp).height(52.dp).scale(s).clip(RoundedCornerShape(26.dp))
                .background(Color(0x33FFFFFF)).border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(26.dp))
                .clickable(interactionSource = press, indication = null, onClick = onCamera),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        // more
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(0x40FFFFFF)).clickable(onClick = onMore),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.KeyboardArrowUp, null, tint = Color.White, modifier = Modifier.size(30.dp).rotate(rot)) }
    }
}

@Composable
private fun StackCard(uri: Uri?, modifier: Modifier) {
    Box(
        modifier.size(40.dp, 46.dp).clip(RoundedCornerShape(4.dp)).background(Color.White).padding(2.dp)
            .clip(RoundedCornerShape(2.dp)).background(Cell)
    ) {
        if (uri != null) AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}
