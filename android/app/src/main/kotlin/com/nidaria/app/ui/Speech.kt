// Lo que dice la criatura. Frases cortas, en primera persona.

package com.nidaria.app.ui

import com.nidaria.app.data.WeatherNow
import com.nidaria.app.rules.Creature
import com.nidaria.app.rules.LOW
import java.time.LocalTime
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

// Lo que comenta de la hora del jugador y del tiempo que hace fuera.
private val HOUR_LINES = mapOf(
    "manana" to listOf("¡Buenos días!", "Qué bien se ha dormido."),
    "noche" to listOf("Qué tarde es…", "Ya es de noche.", "¿Tú no duermes?"),
    "sueno" to listOf("¿Nos vamos a dormir?", "Es hora de dormir, ¿no?"),
)
private val WEATHER_LINES = mapOf(
    "despejado" to listOf("Hace un día precioso.", "Mira qué sol."),
    "estrellas" to listOf("Hay muchas estrellas.", "Qué noche tan clara."),
    "nubes" to listOf("Hay nubes con formas.", "Esa nube parece un pez."),
    "cubierto" to listOf("Qué día más gris."),
    "niebla" to listOf("No se ve nada por la ventana."),
    "lluvia" to listOf("Está lloviendo.", "Me gusta el ruido de la lluvia."),
    "tormenta" to listOf("¿Has oído ese trueno?", "Menos mal que estamos en casa."),
    "nieve" to listOf("¡Está nevando!", "Quiero tocar la nieve."),
    "calor" to listOf("Qué calor hace hoy."),
    "frio" to listOf("Brrr. Fuera hace frío."),
)

private fun <T> pick(list: List<T>): T = list[Random.nextInt(list.size)]

private fun contextLines(creature: Creature, weather: WeatherNow?, hour: Int = LocalTime.now().hour): List<String> {
    val lines = mutableListOf<String>()
    val night = hour >= 23 || hour < 6
    if (night) lines += HOUR_LINES.getValue(if (creature.energy < 90) "sueno" else "noche")
    else if (hour in 7..10) lines += HOUR_LINES.getValue("manana")
    if (weather != null) {
        val day = hour in 8..19
        val kind = if (weather.kind == "despejado" && !day) "estrellas" else weather.kind
        lines += WEATHER_LINES[kind].orEmpty()
        if (weather.temp >= 30) lines += WEATHER_LINES.getValue("calor")
        if (weather.temp <= 3) lines += WEATHER_LINES.getValue("frio")
    }
    return lines
}

fun line(kind: String?): String = pick(LINES[kind] ?: LINES.getValue("calm"))

// Frase espontánea: primero lo que más le falta, luego su carácter.
fun idleLine(creature: Creature, mood: String, weather: WeatherNow? = null): String {
    if (creature.sick) return pick(LINES.getValue("sick"))
    val worst = listOf("hunger", "energy", "hygiene", "fun").minBy { creature.need(it) }
    if (creature.need(worst) < LOW) return pick(LINES.getValue(worst))
    val context = contextLines(creature, weather)
    if (context.isNotEmpty() && Random.nextFloat() < 0.35f) return pick(context)
    if (Random.nextFloat() < 0.5f) {
        val lines = TRAIT_LINES[pick(creature.genes.traits)]
        if (lines != null) return pick(lines)
    }
    return pick(LINES.getValue(if (mood == "feliz") "happy" else "calm"))
}
