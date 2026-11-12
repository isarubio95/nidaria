// Lo que dice la criatura. Frases cortas, en primera persona.

package com.nidaria.app.ui

import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.LOW
import kotlin.random.Random

private val LINES = mapOf(
    "hunger" to listOf("Tengo hambre.", "¿Hay algo de comer?", "Me suenan las tripas."),
    "energy" to listOf("Qué sueño…", "Se me cierran los ojos.", "Quiero dormir."),
    "hygiene" to listOf("Necesito un baño.", "Me pica todo.", "Huelo raro."),
    "fun" to listOf("Me aburro.", "¿Jugamos a algo?", "No hay nada que hacer."),
    "sick" to listOf("No me encuentro bien.", "Me duele la tripa.", "Tengo frío."),
    "happy" to listOf("¡Qué bien estoy!", "Hoy es un buen día.", "Me gusta estar aquí."),
    "calm" to listOf("Hola.", "¿Qué hacemos?", "Mmm."),
    "favorita" to listOf("¡Mi favorita!", "¡Qué rico!"),
    "asco" to listOf("Puaj.", "Esto no me gusta."),
    "come" to listOf("Ñam.", "Gracias.", "Qué bueno."),
    "caricia" to listOf("Ji, ji.", "Más.", "Qué gustito."),
    "bano" to listOf("¡Burbujas!", "Qué limpita estoy."),
    "cura" to listOf("Ya estoy mejor.", "Gracias."),
    "duerme" to listOf("Buenas noches."),
    "despierta" to listOf("¡Buenos días!"),
    "grunona" to listOf("Cinco minutos más…"),
    "juega" to listOf("¡Otra vez!", "¡Yupi!"),
    "gana" to listOf("¡Hemos ganado!", "¡Qué bien jugamos!"),
    "pierde" to listOf("Otra vez será.", "Casi."),
    "nace" to listOf("¡Hola!"),
)

private val TRAIT_LINES = mapOf(
    "gloton" to listOf("¿Y la merienda?", "Pienso en tarta."),
    "dormilon" to listOf("Echaría una siesta."),
    "travieso" to listOf("Tengo una idea…", "¿Y si lo tiramos?"),
    "mimoso" to listOf("¿Me haces mimos?"),
    "presumido" to listOf("¿Estoy guapa?"),
    "fuerte" to listOf("¡Mira qué fuerte soy!"),
    "timido" to listOf("…hola."),
    "curioso" to listOf("¿Qué es eso?", "¿Y eso para qué sirve?"),
)

private fun <T> pick(list: List<T>): T = list[Random.nextInt(list.size)]

fun line(kind: String?): String = pick(LINES[kind] ?: LINES.getValue("calm"))

// Frase espontánea: primero lo que más le falta, luego su carácter.
fun idleLine(creature: Creature, mood: String): String {
    if (creature.sick) return pick(LINES.getValue("sick"))
    val worst = listOf("hunger", "energy", "hygiene", "fun").minBy { creature.need(it) }
    if (creature.need(worst) < LOW) return pick(LINES.getValue(worst))
    if (Random.nextFloat() < 0.5f) {
        val lines = TRAIT_LINES[pick(creature.genes.traits)]
        if (lines != null) return pick(lines)
    }
    return pick(LINES.getValue(if (mood == "feliz") "happy" else "calm"))
}
