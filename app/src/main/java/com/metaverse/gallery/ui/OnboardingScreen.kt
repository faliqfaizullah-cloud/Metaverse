package com.metaverse.gallery.ui

import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.metaverse.gallery.ui.theme.*
import com.metaverse.gallery.util.Hap
import com.metaverse.gallery.util.rememberHaptics
import kotlinx.coroutines.delay

private val headlines = listOf(
    "A place for\nthings you love.",
    "Every day,\na little stamp.",
    "See it, hear it,\nkeep it."
)

@Composable
fun OnboardingScreen(photos: List<Uri>, onEnter: () -> Unit) {
    val haptic = rememberHaptics()
    val pager = rememberPagerState { headlines.size }

    LaunchedEffect(pager.currentPage) { haptic(Hap.TICK) }

    // floating idle motion for the collage
    val inf = rememberInfiniteTransition(label = "float")
    val bob by inf.animateFloat(
        -5f, 5f,
        infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob"
    )

    // entrance pops
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(150); shown = true }
    val pop by animateFloatAsState(
        if (shown) 1f else 0.6f, spring(dampingRatio = 0.55f, stiffness = 260f), label = "pop"
    )
    var heart by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(900); heart = true; haptic(Hap.CLICK) }
    val heartScale by animateFloatAsState(
        if (heart) 1f else 0f, spring(dampingRatio = 0.35f, stiffness = 300f), label = "heart"
    )

    Box(Modifier.fillMaxSize().background(Ink).systemBarsPadding()) {
        Text(
            "come, take a look—\nthese little things\nare yours to keep.",
            fontFamily = Hand, fontSize = 18.sp, lineHeight = 21.sp, color = Color(0xFF555555),
            modifier = Modifier.padding(start = 28.dp, top = 62.dp)
        )

        // ---- collage ----
        Box(Modifier.padding(top = 150.dp).fillMaxWidth().height(330.dp).scale(pop)) {
            Tile(photos.getOrNull(0), 76, 13, bob, 0)
            Tile(photos.getOrNull(1), 207, 0, -bob, 1, wave = true)
            Tile(photos.getOrNull(2), 43, 130, -bob, 2)
            Tile(photos.getOrNull(3), 174, 108, bob, 3)
            Tile(photos.getOrNull(4), 104, 189, bob, 4, wave = true)

            // tool pill
            Row(
                Modifier.offset(115.dp, (83 + bob / 2).dp).size(130.dp, 40.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xE61C1C1C)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PhotoCamera, null, tint = Color(0xFF666666), modifier = Modifier.size(20.dp))
                Icon(Icons.Filled.Mic, null, tint = Color(0xFF666666), modifier = Modifier.size(20.dp))
                Icon(Icons.Filled.VideoLibrary, null, tint = Color(0xFF666666), modifier = Modifier.size(20.dp))
            }
            // heart bubble
            Row(
                Modifier.offset(161.dp, 150.dp).scale(heartScale).clip(RoundedCornerShape(10.dp))
                    .background(Ember).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("1", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // quote
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 28.dp, top = 160.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                "a moment here,\na memory there,\nand suddenly,\na whole life on display.",
                fontFamily = Hand, fontSize = 18.sp, lineHeight = 21.sp, color = Color(0xFF666666),
                textAlign = TextAlign.End
            )
            Row {
                Text("Shakespeare", color = Color(0xFF666666), fontSize = 14.sp, textDecoration = TextDecoration.LineThrough)
                Text(" ~ You", color = Color.White, fontSize = 14.sp)
            }
        }

        // bottom block
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 28.dp).padding(bottom = 28.dp)) {
            Text("YOUR PERSONAL MUSEUM", fontFamily = Mono, fontSize = 13.sp, color = Muted, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            HorizontalPager(pager, Modifier.fillMaxWidth().height(110.dp)) { page ->
                Text(
                    headlines[page], color = Color(0xFFF2F2F2), fontSize = 44.sp,
                    fontWeight = FontWeight.Medium, lineHeight = 48.sp, letterSpacing = (-1.5).sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(headlines.size) { i ->
                    val w by animateDpAsState(if (i == pager.currentPage) 24.dp else 7.dp, label = "dot")
                    Box(Modifier.height(7.dp).width(w).clip(CircleShape).background(if (i == pager.currentPage) Color.White else Color(0xFF666666)))
                }
            }
            Spacer(Modifier.height(22.dp))
            Box(
                Modifier.fillMaxWidth().height(64.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFF4F4F4), Color(0xFFE2E2E2))))
                    .clickable { haptic(Hap.HEAVY); onEnter() },
                contentAlignment = Alignment.Center
            ) {
                Text("Enter the museum", color = Color(0xFF111111), fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private val tilt = listOf(0f, 0f, 0f, 0f, 0f)

@Composable
private fun Tile(uri: Uri?, x: Int, y: Int, bob: Float, i: Int, wave: Boolean = false) {
    Box(
        Modifier.offset(x.dp, (y + bob).dp).size(110.dp).rotate(tilt[i])
            .clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0xFF2B2B2B), RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2A3A5A), Color(0xFF5A2A3A))))
    ) {
        if (uri != null) {
            AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Filled.Image, null, tint = Color(0x55FFFFFF), modifier = Modifier.align(Alignment.Center).size(32.dp))
        }
        if (wave) Waveform(
            Modifier.align(Alignment.Center).size(70.dp, 28.dp), seed = i * 7 + 3,
            progress = 1f, playing = true, activeColor = Ember
        )
    }
}
