// Iconos de trazo de Nidaria. Los mismos dibujos que la web, en un
// lienzo de 24 × 24.

package com.nidaria.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private const val HEART = "M12 20s-7.5-4.7-7.5-10.2A4.2 4.2 0 0 1 12 7.4a4.2 4.2 0 0 1 7.5 2.4C19.5 15.3 12 20 12 20z"

enum class Ic(private vararg val data: String) {
    Bag("M5 8.5h14l-1 11.5H6L5 8.5z", "M9 8.5V7a3 3 0 0 1 6 0v1.5"),
    Hanger("M10 5.5a2 2 0 1 1 2.6 1.9c-.4.2-.6.5-.6.9V9.5", "M12 9.5l8 5.6a1.5 1.5 0 0 1-.9 2.7H4.9a1.5 1.5 0 0 1-.9-2.7l8-5.6z"),
    Tree(circle(12f, 5.5f, 2.5f), circle(6f, 18.5f, 2.5f), circle(18f, 18.5f, 2.5f), "M12 8v4M6 16v-4h12v4"),
    Food("M3.5 11.5h17a8.5 8.5 0 0 1-17 0z", "M9 8c0-1.6 1.2-1.6 1.2-3.2M14 8c0-1.6 1.2-1.6 1.2-3.2"),
    Moon("M20 14.2A8 8 0 1 1 9.8 4a6.4 6.4 0 0 0 10.2 10.2z"),
    Sun(circle(12f, 12f, 4f), "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6L7 7M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4"),
    Drop("M12 3.5s6.2 6.3 6.2 10.6a6.2 6.2 0 0 1-12.4 0C5.8 9.8 12 3.5 12 3.5z", "M9.3 14.5a2.8 2.8 0 0 0 2.2 2.6"),
    Ball(circle(12f, 12f, 8.5f), "M6 6c3.2 2.8 3.2 9.2 0 12M18 6c-3.2 2.8-3.2 9.2 0 12"),
    Heart(HEART),
    Heal(HEART, "M12 10.2v4.6M9.7 12.5h4.6"),
    X("M6.5 6.5l11 11M17.5 6.5l-11 11"),
    Back("M14.5 5.5L8 12l6.5 6.5"),
    Bolt("M13 3L5.5 13.5H11l-1 7.5 7.5-10.5H12L13 3z"),
    Clock(circle(12f, 12f, 8.5f), "M12 7.5V12l3 2"),
    Check("M5 12.5l4.5 4.5L19 7.5"),
    Exit("M10 4.5H6.5a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2H10M15 8l4 4-4 4M19 12H9.5"),
    Pencil("M4.5 19.5l3.8-.9L19 7.9l-2.9-2.9L5.4 15.7l-.9 3.8z"),
    Play("M8.5 5.5v13L19 12 8.5 5.5z"),
    Target(circle(12f, 12f, 8.5f), circle(12f, 12f, 3.5f)),
    Flame("M12.5 2.5c.6 3.4 5.5 5.9 5.5 11.2A6 6 0 0 1 6 13.7c0-2.6 1.4-4.4 2.8-5.6.1 1.7.9 2.9 2.1 3.4-.4-3 .5-6 1.6-9z"),
    Star("M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.900l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9L12 3.5z"),
    ;

    val paths: List<Path> by lazy { data.map { PathParser().parsePathString(it).toPath() } }
}

private val STROKE = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun Icon(icon: Ic, size: Dp = 20.dp, color: Color = Nd.ink, filled: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            for (path in icon.paths) drawPath(path, color, style = if (filled) Fill else STROKE)
        }
    }
}
