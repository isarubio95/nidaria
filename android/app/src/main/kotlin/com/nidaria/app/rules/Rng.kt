// Números pseudoaleatorios deterministas, idénticos a los de shared/rng.js:
// la misma semilla da la misma criatura en la web y en la app.

package com.nidaria.app.rules

import kotlin.math.floor

fun hashString(text: String): Long {
    var h = 2166136261L.toInt()
    for (ch in text) {
        h = h xor ch.code
        h *= 16777619
    }
    return h.toLong() and 0xffffffffL
}

fun mix(a: Long, b: Long): Long {
    var h = a.toInt() xor ((b.toInt() + 0x9e3779b9.toInt()) * 0x85ebca6b.toInt())
    h = h xor (h ushr 15)
    h *= 0x2c1b3c6d
    h = h xor (h ushr 12)
    h *= 0x297a2d39
    h = h xor (h ushr 15)
    return h.toLong() and 0xffffffffL
}

class Rng(seed: Long) {
    private var a = seed.toInt()

    fun next(): Double {
        a += 0x6d2b79f5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL) / 4294967296.0
    }

    fun range(min: Double, max: Double): Double = min + (max - min) * next()
    fun int(min: Int, max: Int): Int = floor(min + (max - min + 1) * next()).toInt()
    fun <T> pick(list: List<T>): T = list[floor(next() * list.size).toInt()]
    fun chance(p: Double): Boolean = next() < p
}

// Tirada entre 0 y 1 ligada a una semilla y a un índice, sin estado.
fun roll(seed: Long, index: Long): Double = mix(seed and 0xffffffffL, index and 0xffffffffL) / 4294967296.0

fun clamp(value: Double, min: Double, max: Double): Double = kotlin.math.min(max, kotlin.math.max(min, value))
