package com.metaverse.gallery.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Scalloped postage-stamp edge (a squircle whose radius alternates) for calendar thumbnails. */
class StampShape(private val teeth: Int = 20, private val depth: Float = 0.06f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = teeth * 2
        val e = 2.0 / 4.5 // superellipse exponent -> rounded square
        for (i in 0 until steps) {
            val a = i.toDouble() / steps * Math.PI * 2
            val c = cos(a)
            val s = sin(a)
            val r = if (i % 2 == 0) 1f else 1f - depth
            val x = cx + (sign(c) * abs(c).pow(e)).toFloat() * cx * r
            val y = cy + (sign(s) * abs(s).pow(e)).toFloat() * cy * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
