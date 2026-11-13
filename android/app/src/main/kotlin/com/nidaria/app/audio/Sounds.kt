// Los efectos de sonido, hechos con código: cada receta suma tonos y
// ruido filtrado. No hay ficheros de audio en la app.
//
// Es una copia de public/js/audio/sounds.js, la versión de la web. Si se
// cambia una receta allí, hay que cambiarla aquí y volver a generar
// golden.json (tools/golden.mjs): SoundsParityTest compara las muestras.
//
// Las cuentas se hacen en Double y se guardan en Float, como el
// Float32Array de la web, para que salgan las mismas muestras.

package com.nidaria.app.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Frecuencia de una nota MIDI (60 = do central, 69 = la 440).
private fun note(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

// Math.round de JavaScript: las mitades suben.
private fun jsRound(value: Double): Int = floor(value + 0.5).toInt()

private class Mulberry32(seed: Int) {
    private var a = seed

    fun next(): Double {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toDouble() / 4294967296.0
    }
}

private fun seedOf(name: String): Int {
    var hash = 2166136261L.toInt()
    for (char in name) hash = (hash xor char.code) * 16777619
    return hash
}

// Formas de onda como armónicos (múltiplo, volumen). Solo senos sumados.
private val WAVES = mapOf(
    "sine" to listOf(1.0 to 1.0),
    "soft" to listOf(1.0 to 1.0, 2.0 to 0.3, 3.0 to 0.12),
    "tri" to listOf(1.0 to 1.0, 3.0 to -1.0 / 9.0, 5.0 to 1.0 / 25.0),
)

private sealed class Part {
    abstract val at: Double
    abstract val dur: Double
}

private class TonePart(
    override val at: Double, override val dur: Double, val f: Double, val to: Double, val glide: Double, val wave: String,
    val gain: Double, val attack: Double, val curve: Double, val vib: Double, val vibRate: Double, val vibDecay: Double,
) : Part()

private class NoisePart(
    override val at: Double, override val dur: Double, val gain: Double, val lp: Double, val lpTo: Double,
    val hp: Double, val attack: Double, val curve: Double,
) : Part()

// Una receta llama a estas funciones; primero se apuntan las piezas y
// luego se suman en el búfer, que ya sabe cuánto ha de durar.
private class Recorder(val rand: () -> Double) {
    val parts = ArrayList<Part>()

    fun tone(
        dur: Double, f: Double, at: Double = 0.0, to: Double = f, glide: Double = dur, wave: String = "sine", gain: Double = 0.3,
        attack: Double = 0.005, curve: Double = 2.0, vib: Double = 0.0, vibRate: Double = 6.0, vibDecay: Double = 0.0,
    ) {
        parts.add(TonePart(at, dur, f, to, glide, wave, gain, attack, curve, vib, vibRate, vibDecay))
    }

    // Campanita: un tono con parciales que se apagan antes cuanto más agudos.
    fun bell(f: Double, at: Double = 0.0, dur: Double = 0.8, gain: Double = 0.15) {
        val partials = listOf(Triple(1.0, 1.0, 1.0), Triple(2.0, 0.32, 1.8), Triple(3.0, 0.12, 2.6), Triple(4.2, 0.05, 3.4))
        for ((ratio, amp, speed) in partials) {
            parts.add(TonePart(at, dur / speed, f * ratio, f * ratio, dur, "sine", gain * amp, 0.002, 3.0, 0.0, 0.0, 0.0))
        }
    }

    fun noise(
        dur: Double, at: Double = 0.0, gain: Double = 0.2, lp: Double = 4000.0, lpTo: Double = lp, hp: Double = 0.0,
        attack: Double = 0.002, curve: Double = 2.0,
    ) {
        parts.add(NoisePart(at, dur, gain, lp, lpTo, hp, attack, curve))
    }
}

private fun envelope(t: Double, dur: Double, attack: Double, curve: Double): Double =
    (if (t < attack) t / attack else 1.0) * max(0.0, 1.0 - t / dur).pow(curve)

private fun renderTone(out: FloatArray, sr: Int, part: TonePart) {
    val start = jsRound(part.at * sr)
    val count = min(out.size - start, jsRound(part.dur * sr))
    val harmonics = WAVES[part.wave] ?: WAVES.getValue("sine")
    val norm = harmonics.sumOf { abs(it.second) }
    val ratio = part.to / part.f
    var phase = 0.0
    for (i in 0 until count) {
        val t = i.toDouble() / sr
        val progress = min(1.0, t / part.glide)
        var freq = part.f * ratio.pow(progress)
        if (part.vib != 0.0) freq *= 1 + part.vib * exp(-part.vibDecay * t) * sin(2 * PI * part.vibRate * t)
        phase += (2 * PI * freq) / sr
        var sample = 0.0
        for ((multiple, amp) in harmonics) {
            if (freq * multiple < sr * 0.45) sample += amp * sin(phase * multiple)
        }
        val index = start + i
        out[index] = (out[index] + (sample / norm) * part.gain * envelope(t, part.dur, part.attack, part.curve)).toFloat()
    }
}

private fun renderNoise(out: FloatArray, sr: Int, part: NoisePart, rand: () -> Double) {
    val start = jsRound(part.at * sr)
    val count = min(out.size - start, jsRound(part.dur * sr))
    var low = 0.0
    var floor = 0.0
    val hpCoef = if (part.hp != 0.0) 1 - exp((-2 * PI * part.hp) / sr) else 0.0
    for (i in 0 until count) {
        val t = i.toDouble() / sr
        val cutoff = part.lp * (part.lpTo / part.lp).pow(t / part.dur)
        val lpCoef = 1 - exp((-2 * PI * min(cutoff, sr * 0.45)) / sr)
        low += lpCoef * (rand() * 2 - 1 - low)
        var sample = low
        if (hpCoef != 0.0) {
            floor += hpCoef * (sample - floor)
            sample -= floor
        }
        val index = start + i
        out[index] = (out[index] + sample * part.gain * 1.8 * envelope(t, part.dur, part.attack, part.curve)).toFloat()
    }
}

// Volumen de todos los sonidos (ver la web).
private const val LEVEL = 1.6

// Ayuda para escribir las recetas con el tipo que les toca.
private fun r(block: Recorder.() -> Unit) = block

private val RECIPES: Map<String, Recorder.() -> Unit> = linkedMapOf(
    // Tocar a la criatura.
    "toque" to r { tone(dur = 0.11, f = note(74), to = note(81), glide = 0.06, wave = "soft", gain = 0.3, curve = 1.6) },
    // Acariciarla.
    "caricia" to r {
        tone(dur = 0.16, f = note(81), gain = 0.14, curve = 2.2, attack = 0.01)
        tone(at = 0.07, dur = 0.22, f = note(86), gain = 0.12, curve = 2.4, attack = 0.01)
    },
    // Cada mordisco al comer.
    "bocado" to r {
        noise(dur = 0.07, gain = 0.26, lp = 1800.0, lpTo = 600.0)
        tone(dur = 0.08, f = 200.0, to = 110.0, gain = 0.32)
    },
    // Su comida favorita.
    "encanta" to r {
        listOf(84, 88, 91, 96).forEachIndexed { i, midi -> bell(at = i * 0.075, dur = 0.55, f = note(midi), gain = 0.15) }
    },
    // La comida que no soporta.
    "puaj" to r {
        tone(dur = 0.28, f = note(64), to = 250.0, wave = "soft", gain = 0.24, curve = 0.8, vib = 0.04, vibRate = 9.0, attack = 0.02)
        tone(at = 0.26, dur = 0.34, f = note(60), to = 175.0, wave = "soft", gain = 0.24, curve = 1.2, vib = 0.05, vibRate = 9.0, attack = 0.02)
    },
    // El baño: pompas que suben y un poco de agua.
    "burbujas" to r {
        val times = List(13) { rand() * 1.2 }.sorted()
        for (at in times) {
            val f = 350 + rand() * 500
            val dur = 0.06 + rand() * 0.05
            tone(at = at, dur = dur, f = f, to = f * 2.4, gain = 0.1 + rand() * 0.09, attack = 0.003, curve = 1.5)
        }
        noise(dur = 1.35, gain = 0.03, lp = 3000.0, hp = 800.0, attack = 0.2, curve = 1.0)
    },
    // Una sola pompa (el juego de las burbujas).
    "pompa" to r { tone(dur = 0.08, f = 420 + rand() * 60, to = 1100.0, gain = 0.22, attack = 0.003, curve = 1.4) },
    // Medicina o vitaminas.
    "cura" to r {
        bell(dur = 0.9, f = note(88), gain = 0.19)
        bell(at = 0.14, dur = 1.1, f = note(95), gain = 0.16)
    },
    // Un juguete que bota.
    "juguete" to r { tone(dur = 0.5, f = 180.0, to = 420.0, glide = 0.08, wave = "soft", gain = 0.26, curve = 1.5, vib = 0.18, vibRate = 14.0, vibDecay = 6.0) },
    // Un truco: escalera rápida de notas.
    "truco" to r {
        listOf(79, 81, 84, 86, 88, 91, 93, 96).forEachIndexed { i, midi -> bell(at = i * 0.045, dur = 0.35, f = note(midi), gain = 0.1) }
    },
    // Monedas: recoger, comprar, premios.
    "monedas" to r {
        tone(dur = 0.08, f = note(83), wave = "soft", gain = 0.18, curve = 0.6)
        tone(at = 0.075, dur = 0.38, f = note(88), wave = "soft", gain = 0.18, curve = 2.0)
        bell(at = 0.075, dur = 0.5, f = note(88), gain = 0.07)
    },
    "dormir" to r {
        listOf(79, 76, 72).forEachIndexed { i, midi -> tone(at = i * 0.2, dur = 0.45, f = note(midi), wave = "soft", gain = 0.15, curve = 1.6, attack = 0.02) }
    },
    "despertar" to r {
        listOf(72, 76, 79, 84).forEachIndexed { i, midi -> tone(at = i * 0.09, dur = 0.3, f = note(midi), wave = "soft", gain = 0.14, curve = 1.8, attack = 0.01) }
    },
    // El huevo se resquebraja.
    "crac" to r {
        noise(dur = 0.035, gain = 0.32, lp = 6000.0, hp = 1500.0, attack = 0.001, curve = 3.0)
        tone(dur = 0.03, f = 1400.0, to = 900.0, gain = 0.07)
    },
    // Nace o termina de evolucionar.
    "nace" to r {
        listOf(72, 76, 79, 84, 88).forEachIndexed { i, midi -> bell(at = i * 0.085, dur = 0.7, f = note(midi), gain = 0.14) }
        listOf(84, 88, 91).forEach { midi -> tone(at = 0.42, dur = 0.95, f = note(midi), wave = "soft", gain = 0.06, attack = 0.03, curve = 1.4) }
        listOf(96, 100, 103).forEachIndexed { i, midi -> bell(at = 0.5 + i * 0.05, dur = 0.5, f = note(midi), gain = 0.05) }
    },
    // Mientras evoluciona: sube y se corta justo al cambiar de forma.
    "evoluciona" to r {
        tone(dur = 1.3, f = 180.0, to = 1100.0, gain = 0.15, attack = 0.4, curve = 0.4, vib = 0.02, vibRate = 7.0)
        tone(dur = 1.3, f = 270.0, to = 1650.0, gain = 0.07, attack = 0.5, curve = 0.4)
        noise(dur = 1.3, gain = 0.025, lp = 500.0, lpTo = 5000.0, attack = 0.8, curve = 0.3)
    },
    // Sube el vínculo.
    "vinculo" to r {
        bell(dur = 0.8, f = note(79), gain = 0.16)
        bell(at = 0.12, dur = 0.8, f = note(86), gain = 0.14)
        bell(at = 0.24, dur = 1.1, f = note(91), gain = 0.13)
    },
    // Despedida.
    "adios" to r {
        listOf(76, 72, 69).forEachIndexed { i, midi -> bell(at = i * 0.32, dur = 1.2, f = note(midi), gain = 0.11) }
    },
    // Minijuegos.
    "punto" to r { tone(dur = 0.07, f = note(84), to = note(91), glide = 0.04, wave = "soft", gain = 0.16, curve = 1.5) },
    "fallo" to r { tone(dur = 0.18, f = 220.0, to = 150.0, wave = "tri", gain = 0.26, curve = 1.5) },
    "salto" to r { tone(dur = 0.14, f = 330.0, to = 660.0, glide = 0.1, gain = 0.18, curve = 1.3) },
    "carta" to r { noise(dur = 0.06, gain = 0.2, lp = 5000.0, lpTo = 2000.0, hp = 900.0, attack = 0.01) },
    "gana" to r {
        listOf(72, 76, 79).forEachIndexed { i, midi -> tone(at = i * 0.1, dur = 0.16, f = note(midi), wave = "soft", gain = 0.16, curve = 1.0) }
        listOf(84, 88, 91).forEach { midi -> tone(at = 0.3, dur = 0.85, f = note(midi), wave = "soft", gain = 0.08, attack = 0.01, curve = 1.6) }
        bell(at = 0.3, dur = 1.0, f = note(96), gain = 0.09)
    },
    "pierde" to r {
        listOf(67, 66, 65).forEachIndexed { i, midi -> tone(at = i * 0.26, dur = 0.3, f = note(midi), wave = "soft", gain = 0.15, curve = 1.2, vib = 0.015, attack = 0.02) }
        tone(at = 0.78, dur = 0.6, f = note(64), wave = "soft", gain = 0.15, curve = 1.2, vib = 0.03, vibRate = 6.0, attack = 0.02)
    },
    // Darle calor al huevo.
    "calor" to r {
        tone(dur = 0.09, f = 240.0, to = 340.0, gain = 0.17, curve = 1.5)
        noise(dur = 0.06, gain = 0.04, lp = 1200.0)
    },
    // Las cuatro luces de Eco, de grave a aguda.
    *listOf(72, 76, 79, 84).mapIndexed { index, midi ->
        "nota$index" to r {
            tone(dur = 0.42, f = note(midi), wave = "soft", gain = 0.2, attack = 0.006, curve = 1.6)
            bell(dur = 0.42, f = note(midi), gain = 0.05)
        }
    }.toTypedArray(),
    // Hacer la foto de la tarjeta.
    "foto" to r {
        noise(dur = 0.025, gain = 0.28, lp = 7000.0, hp = 2000.0)
        noise(at = 0.07, dur = 0.04, gain = 0.22, lp = 5000.0, hp = 1200.0)
    },
)

val SOUND_NAMES: List<String> = RECIPES.keys.toList()

// Muestras del sonido, mono y entre -1 y 1, o null si no existe.
fun renderSound(name: String, sampleRate: Int = 44100): FloatArray? {
    val recipe = RECIPES[name] ?: return null
    val random = Mulberry32(seedOf(name))
    val rand: () -> Double = random::next
    val recorder = Recorder(rand)
    recorder.recipe()
    val seconds = recorder.parts.maxOf { it.at + it.dur } + 0.01
    val out = FloatArray(ceil(seconds * sampleRate).toInt())
    for (part in recorder.parts) {
        when (part) {
            is TonePart -> renderTone(out, sampleRate, part)
            is NoisePart -> renderNoise(out, sampleRate, part, rand)
        }
    }
    // Por si las piezas se suman de más: nunca pasar de 0,9.
    var peak = 0.0
    for (sample in out) peak = max(peak, abs(sample.toDouble()) * LEVEL)
    val scale = if (peak > 0.9) (0.9 / peak) * LEVEL else LEVEL
    for (i in out.indices) out[i] = (out[i] * scale).toFloat()
    return out
}
