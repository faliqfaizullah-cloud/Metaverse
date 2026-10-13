package com.metaverse.gallery.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

enum class Hap { TICK, CLICK, HEAVY, CONFIRM, REJECT }

@Composable
fun rememberHaptics(): (Hap) -> Unit {
    val view = LocalView.current
    return remember(view) { { type -> view.buzz(type) } }
}

fun View.buzz(type: Hap) {
    val c = when (type) {
        Hap.TICK -> HapticFeedbackConstants.CLOCK_TICK
        Hap.CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
        Hap.HEAVY -> HapticFeedbackConstants.LONG_PRESS
        Hap.CONFIRM ->
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS
        Hap.REJECT ->
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
            else HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(c)
}
