// Tarjeta para compartir: una foto de la criatura en su habitación con su
// nombre, su forma y su ADN. La misma que hace la web (public/js/ui/card.js),
// con las mismas medidas y colores.

package com.nidaria.app.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.nidaria.app.R
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.FAMILY_INFO
import com.nidaria.app.rules.TRAITS
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.Normalizer

object Card {
    // Formato vertical 4:5: el que mejor se ve en redes y en el móvil.
    const val WIDTH = 1080
    const val HEIGHT = 1350
    private const val PAD = 48f
    const val PHOTO_WIDTH = WIDTH - 2 * 48
    const val PHOTO_HEIGHT = 860

    private val CREAM = Color.parseColor("#fff8ec")
    private val PAPER = Color.parseColor("#fffdf8")
    private val INK = Color.parseColor("#5b3a29")
    private val INK_SOFT = Color.parseColor("#84644f")
    private val INK_FAINT = Color.parseColor("#b89f8c")
    private val LINE = Color.parseColor("#eadfce")
    private val MINT_WASH = Color.parseColor("#dff5e8")
    private val SUN_WASH = Color.parseColor("#fff1cc")

    // Reduce el cuerpo de letra hasta que el texto quepa en `max` píxeles.
    private fun fit(paint: Paint, text: String, size: Float, max: Float) {
        var px = size
        paint.textSize = px
        while (px > 20f && paint.measureText(text) > max) {
            px -= 2f
            paint.textSize = px
        }
    }

    // Ficha redonda con texto, como las del juego. Devuelve dónde acaba.
    private fun chip(canvas: Canvas, paint: Paint, x: Float, y: Float, text: String, fill: Int): Float {
        paint.textSize = 30f
        val w = paint.measureText(text) + 44f
        val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
        canvas.drawRoundRect(RectF(x, y, x + w, y + 56f), 28f, 28f, back)
        paint.color = INK
        val metrics = paint.fontMetrics
        canvas.drawText(text, x + 22f, y + 29f - (metrics.ascent + metrics.descent) / 2f, paint)
        return x + w
    }

    // `photo` mide PHOTO_WIDTH × PHOTO_HEIGHT. `host` es dónde se juega,
    // sin «https://».
    fun make(context: Context, photo: Bitmap, creature: Creature, host: String): Bitmap {
        val black = ResourcesCompat.getFont(context, R.font.zen_maru_gothic_black) ?: Typeface.DEFAULT_BOLD
        val bold = ResourcesCompat.getFont(context, R.font.zen_maru_gothic_bold) ?: Typeface.DEFAULT_BOLD
        val card = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(card)
        canvas.drawColor(CREAM)

        // La foto, con las esquinas redondas y un canto de papel.
        val box = RectF(PAD, PAD, WIDTH - PAD, PAD + PHOTO_HEIGHT)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(box, 40f, 40f, Path.Direction.CW) })
        canvas.drawBitmap(photo, PAD, PAD, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = PAPER
        }
        canvas.drawRoundRect(RectF(PAD + 3f, PAD + 3f, WIDTH - PAD - 3f, PAD + PHOTO_HEIGHT - 3f), 37f, 37f, edge)

        val left = PAD + 24f
        val inner = WIDTH - left * 2f
        var y = PAD + PHOTO_HEIGHT + 100f
        val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

        text.typeface = black
        text.color = INK
        fit(text, creature.name, 84f, inner)
        canvas.drawText(creature.name, left, y, text)

        y += 58f
        val sub = if (creature.stage == "huevo") "Huevo" else "${creature.formName} · ${creature.stageName}"
        text.typeface = bold
        text.color = INK_SOFT
        fit(text, sub, 40f, inner)
        canvas.drawText(sub, left, y, text)

        // Familia y carácter.
        y += 34f
        text.typeface = black
        val family = FAMILY_INFO[creature.genes.family]?.name ?: ""
        var x = chip(canvas, text, left, y, "Familia $family", MINT_WASH)
        for (trait in creature.genes.traits) {
            val name = TRAITS[trait]?.name ?: continue
            text.textSize = 30f
            if (x + 16f + text.measureText(name) + 44f > left + inner) break
            x = chip(canvas, text, x + 16f, y, name, SUN_WASH)
        }

        // Pie: ADN a un lado y el nombre del juego al otro, sobre una línea.
        val footY = HEIGHT - PAD - 12f
        canvas.drawRect(left, footY - 70f, left + inner, footY - 67f, Paint().apply { color = LINE })
        text.textAlign = Paint.Align.LEFT
        text.typeface = black
        text.color = INK_FAINT
        text.textSize = 28f
        canvas.drawText("ADN", left, footY, text)
        val labelW = text.measureText("ADN ")
        text.color = INK
        text.textSize = 38f
        canvas.drawText(creature.code, left + labelW + 4f, footY, text)

        text.textAlign = Paint.Align.RIGHT
        text.textSize = 46f
        canvas.drawText("Nidaria", left + inner, footY - 2f, text)
        val brandW = text.measureText("Nidaria")
        text.typeface = bold
        text.color = INK_SOFT
        text.textSize = 26f
        canvas.drawText(host, left + inner - brandW - 20f, footY - 4f, text)
        return card
    }

    fun png(card: Bitmap): ByteArray = ByteArrayOutputStream().also { card.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    // «nidaria-mota.png», sin tildes ni signos.
    fun fileName(name: String): String {
        val plain = Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val slug = plain.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase()
        return "nidaria-${slug.ifEmpty { "criatura" }}.png"
    }

    // Lo que acompaña a la imagen al compartirla, como en la web.
    fun shareText(creature: Creature, origin: String): String {
        val what = if (creature.stage == "huevo") "todavía es un huevo" else "es ${creature.formName}"
        return "${creature.name} $what. Su ADN es ${creature.code}. Cría la tuya en $origin"
    }

    // Abre el menú de compartir del teléfono con la tarjeta. El fichero va
    // en la caché de la app y se presta con un FileProvider.
    @Throws(IOException::class)
    fun share(context: Context, png: ByteArray, fileName: String, title: String, text: String) {
        val folder = File(context.cacheDir, "tarjetas").apply { mkdirs() }
        // Solo se guarda la última: las anteriores sobran.
        folder.listFiles()?.forEach { it.delete() }
        val file = File(folder, fileName)
        file.writeBytes(png)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .putExtra(Intent.EXTRA_TITLE, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Con el ClipData, el menú enseña la imagen y puede leerla.
        send.clipData = ClipData.newRawUri(title, uri)
        val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    // Guarda la tarjeta en las fotos del teléfono, en Imágenes/Nidaria.
    // Hasta Android 9 hace falta el permiso de escritura (lo pide quien
    // llama); desde Android 10, no.
    @Throws(IOException::class)
    fun saveToGallery(context: Context, png: ByteArray, fileName: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Nidaria")
            values.put(MediaStore.Images.Media.IS_PENDING, 1)
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Nidaria")
            folder.mkdirs()
            var file = File(folder, fileName)
            var copy = 1
            while (file.exists()) file = File(folder, fileName.replace(".png", "-${copy++}.png"))
            @Suppress("DEPRECATION")
            values.put(MediaStore.Images.Media.DATA, file.absolutePath)
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("No se puede crear la imagen")
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("No se puede escribir la imagen")
            stream.use { it.write(png) }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (error: IOException) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
