// Tokens de diseño de Nidaria: los mismos colores, tamaños, radios,
// sombras y tiempos que la web (public/css/tokens.css).

package com.nidaria.app.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nidaria.app.R
import com.nidaria.app.scene.RoomView
import kotlin.math.min

object Nd {
    // La habitación
    val sky = Color(0xFFA9C4D6)
    val cream = Color(0xFFF1EEE8)
    val creamDeep = Color(0xFFE2DDD3)
    val paper = Color(0xFFFCFBF8)

    // Tinta. Cacao, nunca negro.
    val ink = Color(0xFF1F2638)
    val inkSoft = Color(0xFF4D5670)
    val inkFaint = Color(0xFF979EB0)
    val line = Color(0xFFDFDBD2)

    // Caramelos, cada uno con su canto más oscuro.
    val pink = Color(0xFFFF6A3D)
    val pinkEdge = Color(0xFFD2461D)
    val pinkWash = Color(0xFFFFE3D8)
    val mint = Color(0xFF3FC79E)
    val mintEdge = Color(0xFF1F9A76)
    val mintWash = Color(0xFFD6F3E8)
    val sun = Color(0xFFFFC23D)
    val sunEdge = Color(0xFFD48F17)
    val sunWash = Color(0xFFFFF0C6)

    // Una necesidad, un color fijo.
    val hunger = Color(0xFFFF9F43)
    val hungerEdge = Color(0xFFD27A1B)
    val energy = Color(0xFF5B8DEF)
    val energyEdge = Color(0xFF3A69CC)
    val hygiene = mint
    val hygieneEdge = mintEdge
    val funny = Color(0xFF8D6CFF)
    val funnyEdge = Color(0xFF6447DC)
    val health = Color(0xFFFF5C64)
    val healthEdge = Color(0xFFD23A43)
    val track = Color(0xFFE9E5DC)

    val danger = Color(0xFFD0332F)
    val scrim = Color(0x661F2638)

    // Colores extra de los minijuegos
    val skyEdge = Color(0xFF7FA3BD)
    val grey = Color(0xFFB9AEA4)
    val greyEdge = Color(0xFF8F8378)

    val font = FontFamily(
        Font(R.font.zen_maru_gothic_medium, FontWeight.Medium),
        Font(R.font.zen_maru_gothic_bold, FontWeight.Bold),
        Font(R.font.zen_maru_gothic_black, FontWeight.Black),
    )

    // Curva de casi todo lo que se mueve: sale rápido y frena suave.
    val ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    const val QUICK = 150
    const val FAST = 250
    const val MEDIUM = 350
    const val SLOW = 400

    val gutter = 16.dp
    val rSm = 12.dp
    val rMd = 16.dp
    val rLg = 28.dp
    val rPill = 999.dp

    private fun style(size: Int, weight: FontWeight, line: Float, color: Color = ink) = TextStyle(
        fontFamily = font,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.em,
        color = color,
        lineBreak = LineBreak.Paragraph,
        // Como en CSS: cada línea mide justo su interlineado, con la letra
        // centrada, aunque sea más bajo que la altura natural de la fuente.
        // En Compose eso es el modo Tight con recorte en los dos extremos.
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, LineHeightStyle.Mode.Tight),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    val body = style(16, FontWeight.Medium, 1.45f)
    val lead = style(16, FontWeight.Bold, 1.45f, inkSoft)
    val bold = style(16, FontWeight.Bold, 1.45f)
    val strong = style(16, FontWeight.Black, 1.15f)
    val sm = style(14, FontWeight.Bold, 1.25f)
    val smSoft = style(14, FontWeight.Bold, 1.45f, inkSoft)
    val smBlack = style(14, FontWeight.Black, 1.2f)
    val xs = style(12, FontWeight.Bold, 1.15f)
    val xsBlack = style(12, FontWeight.Black, 1.2f)
    val lg = style(20, FontWeight.Black, 1.15f).copy(lineBreak = LineBreak.Heading)
    val xl = style(26, FontWeight.Black, 1.15f).copy(lineBreak = LineBreak.Heading)
    val xxl = style(36, FontWeight.Black, 1f).copy(letterSpacing = (-0.01).em)
}

// Cifras de ancho fijo, para que los números no bailen al cambiar.
val TextStyle.num: TextStyle get() = copy(fontFeatureSettings = "tnum")

val LocalRoom = staticCompositionLocalOf<RoomView> { error("Falta la habitación") }

@Composable
fun T(
    text: String,
    style: TextStyle = Nd.body,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    var merged = style
    if (color != Color.Unspecified) merged = merged.copy(color = color)
    if (align != null) merged = merged.copy(textAlign = align)
    BasicText(text, modifier, merged, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

enum class Elevation(internal val layers: FloatArray) {
    // Por capa: desplazamiento y, difuminado, expansión y opacidad.
    Sm(floatArrayOf(2f, 4f, 0f, 0.10f, 6f, 14f, -6f, 0.22f)),
    Md(floatArrayOf(3f, 6f, 0f, 0.10f, 14f, 28f, -10f, 0.30f)),
    Lg(floatArrayOf(6f, 12f, 0f, 0.10f, 28f, 56f, -16f, 0.36f)),
}

// Sombra marrón, con desplazamiento y difuminado, como las de la web.
fun Modifier.softShadow(elevation: Elevation, radius: Dp, alpha: Float = 1f): Modifier = drawWithCache {
    val paints = ArrayList<Pair<Paint, FloatArray>>()
    val layers = elevation.layers
    var i = 0
    while (i < layers.size) {
        val blur = layers[i + 1].dp.toPx()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Nd.ink.copy(alpha = layers[i + 3]).toArgb()
        // CSS difumina con sigma = radio / 2; Skia, con sigma ≈ radio × 0,577.
        paint.maskFilter = BlurMaskFilter(((blur / 2f) / 0.57735f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        paints.add(paint to floatArrayOf(layers[i].dp.toPx(), layers[i + 2].dp.toPx()))
        i += 4
    }
    val corner = min(radius.toPx(), min(size.width, size.height) / 2f)
    onDrawBehind {
        if (alpha <= 0.01f) return@onDrawBehind
        drawIntoCanvas { canvas ->
            for ((paint, box) in paints) {
                val dy = box[0]
                val spread = box[1]
                val r = (corner + spread).coerceAtLeast(0f)
                val base = paint.alpha
                paint.alpha = (base * alpha).toInt()
                canvas.nativeCanvas.drawRoundRect(-spread, dy - spread, size.width + spread, size.height + dy + spread, r, r, paint)
                paint.alpha = base
            }
        }
    }
}

// Superficie de caramelo: un color con un canto más oscuro abajo, que es
// una sombra interior. Al pulsar, la pieza baja sobre el canto.
fun Modifier.candy(color: Color, edge: Color, edgeHeight: Dp, radius: Dp): Modifier = drawWithCache {
    val corner = min(radius.toPx(), min(size.width, size.height) / 2f)
    val cr = CornerRadius(corner, corner)
    val outline = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), cr)) }
    val lift = edgeHeight.toPx()
    onDrawBehind {
        drawRoundRect(edge, cornerRadius = cr)
        clipPath(outline) {
            translate(0f, -lift) { drawRoundRect(color, cornerRadius = cr) }
        }
    }
}

fun Modifier.surface(color: Color, radius: Dp): Modifier = drawWithCache {
    val corner = min(radius.toPx(), min(size.width, size.height) / 2f)
    onDrawBehind { drawRoundRect(color, cornerRadius = CornerRadius(corner, corner)) }
}

// Borde por dentro, como `box-shadow: inset 0 0 0 2px`.
fun Modifier.innerBorder(color: Color, width: Dp, radius: Dp): Modifier = drawWithCache {
    val w = width.toPx()
    val corner = (min(radius.toPx(), min(size.width, size.height) / 2f) - w / 2f).coerceAtLeast(0f)
    onDrawWithContent {
        drawContent()
        if (color.alpha > 0f) {
            drawRoundRect(
                color, topLeft = Offset(w / 2f, w / 2f), size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(corner, corner), style = Stroke(w),
            )
        }
    }
}
