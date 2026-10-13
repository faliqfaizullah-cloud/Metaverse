package com.metaverse.gallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metaverse.gallery.ui.theme.Cell
import com.metaverse.gallery.ui.theme.CellStroke
import com.metaverse.gallery.ui.theme.Ember
import com.metaverse.gallery.ui.theme.Mono
import com.metaverse.gallery.util.Hap
import com.metaverse.gallery.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/** Bar waveform; bars dance while [playing] and fill with [activeColor] up to [progress]. */
@Composable
fun Waveform(
    modifier: Modifier,
    seed: Int,
    progress: Float,
    playing: Boolean,
    activeColor: Color = Color.White,
    idleColor: Color = Color(0xFF9A9A9A)
) {
    val bars = remember(seed) {
        val r = Random(seed)
        List(44) { 0.22f + r.nextFloat() * 0.78f }
    }
    val t by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f,
        targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Canvas(modifier) {
        val n = bars.size
        val step = size.width / n
        bars.forEachIndexed { i, h ->
            val wob = if (playing) 0.72f + 0.28f * sin(t + i * 0.55f) else 1f
            val bh = size.height * h * wob
            val x = i * step + step / 2
            drawLine(
                color = if (i / n.toFloat() < progress) activeColor else idleColor,
                start = Offset(x, (size.height - bh) / 2),
                end = Offset(x, (size.height + bh) / 2),
                strokeWidth = step * 0.42f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Playable animation pill. Tap the button to play/pause: the waveform animates, progress fills,
 * and haptics fire. (Visual playback; plug MediaPlayer/ExoPlayer into [onToggle] for real audio.)
 */
@Composable
fun PlayPill(
    seed: Int,
    modifier: Modifier = Modifier,
    leadingMic: Boolean = false,
    totalSeconds: Int = 154,
    label: String = "2:34 MIN"
) {
    val haptic = rememberHaptics()
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playing) {
        while (playing) {
            delay(100)
            progress += 0.1f / totalSeconds
            if (progress >= 1f) { progress = 0f; playing = false; haptic(Hap.CONFIRM) }
        }
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(25.dp)).background(Cell)
        ) {
            Waveform(
                Modifier.align(Alignment.CenterStart).padding(start = 14.dp, end = 14.dp).fillMaxWidth().height(24.dp),
                seed, progress, playing
            )
            Text(
                label, fontFamily = Mono, fontSize = 6.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 12.dp)
            )
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Cell)
                .clickable {
                    playing = !playing
                    haptic(if (playing) Hap.CLICK else Hap.TICK)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    leadingMic -> Icons.Filled.Mic
                    playing -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = null,
                tint = if (leadingMic) Color(0xFF9A9A9A) else Ember,
                modifier = Modifier.size(if (leadingMic) 18.dp else 24.dp)
            )
        }
    }
}
