// El zoom de toda la pantalla, como en la web (public/js/zoom.js): la
// interfaz se encoge (App le baja la densidad) y la habitación se aleja con
// su cámara, las dos con el mismo factor. Se cambia pellizcando (lo recoge
// MainActivity), no se enseña en ningún sitio y se recuerda en el teléfono.
// El tamaño de siempre es el máximo: solo se puede alejar.

package com.nidaria.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlin.math.roundToInt

class Zoom(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nidaria", Context.MODE_PRIVATE)

    // Estado de Compose: al cambiar, la interfaz se vuelve a medir.
    var level by mutableFloatStateOf(prefs.getFloat(KEY, DEFAULT).coerceIn(MIN, MAX))
        private set

    // Para la escena, que no es de Compose.
    var onChange: ((Float) -> Unit)? = null

    fun set(value: Float) {
        val next = (value.coerceIn(MIN, MAX) * 1000f).roundToInt() / 1000f
        if (next == level) return
        level = next
        onChange?.invoke(next)
    }

    fun save() = prefs.edit { putFloat(KEY, level) }

    companion object {
        const val MIN = 0.8f
        const val MAX = 1f
        const val DEFAULT = 0.95f
        private const val KEY = "zoom"
    }
}
