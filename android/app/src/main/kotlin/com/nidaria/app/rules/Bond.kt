// Vínculo: la confianza entre la criatura y quien la cuida. Sube con los
// cuidados (eso lo decide el servidor) y baja con cada descuido.
// Desbloquea frases, trucos y más vida adulta. Es la regla de
// shared/bond.js; la app no suma vínculo, solo lo simula y lo enseña.

package com.nidaria.app.rules

import kotlin.math.max

const val BOND_MAX = 100.0

// Lo que resta cada descuido (una necesidad que se pone crítica o caer
// rendida de cansancio).
const val BOND_MISTAKE = 3.0

// Vida adulta extra con el vínculo al máximo. Crece en proporción.
const val BOND_LIFE_MS = 4.0 * 24 * 60 * 60 * 1000

// Por debajo de este vínculo, a una criatura tímida le cuesta el doble.
const val SHY_UNTIL = 50.0

// `reached` es el aviso al llegar al nivel; {name} es la criatura.
class BondLevel(val min: Double, val name: String, val unlocks: String, val reached: String)

val BOND_LEVELS = listOf(
    BondLevel(0.0, "Os estáis conociendo", "", ""),
    BondLevel(20.0, "Te tiene cariño", "Te dice cosas bonitas.", "{name} ya te tiene cariño."),
    BondLevel(
        50.0, "Confía en ti", "Si la tocas, a veces te enseña un truco.",
        "{name} ya confía en ti. Tócala y a veces te enseñará un truco.",
    ),
    BondLevel(80.0, "Inseparables", "Sabe un truco nuevo y vivirá más tiempo.", "{name} y tú ya sois inseparables."),
)

// Lo que dice según el vínculo, a partir del nivel 1. {player} es quien
// la cuida.
val BOND_LINES = mapOf(
    1 to listOf("Me alegra verte.", "Me gusta que estés aquí."),
    2 to listOf("¡{player}! Te estaba esperando.", "Contigo estoy a gusto."),
    3 to listOf("Eres mi persona favorita, {player}.", "No te vayas, ¿vale?"),
)

val TRICK_LINES = listOf("¡Mira lo que sé hacer!", "¡Ta-chán!")

// Trucos que sabe a cada nivel: el giro desde el 2, los saltitos en el 3.
val TRICKS = mapOf(2 to listOf("giro"), 3 to listOf("giro", "saltitos"))

// Probabilidad de hacer un truco al tocarla, y espera mínima entre dos.
const val TRICK_CHANCE = 0.4
const val TRICK_GAP_MS = 20L * 1000

fun bondLevel(bond: Double): Int {
    var level = 0
    for ((index, entry) in BOND_LEVELS.withIndex()) if (bond >= entry.min) level = index
    return level
}

// Resta vínculo por descuidos. Lo usa la simulación.
fun loseBond(creature: Creature, mistakes: Double) {
    if (mistakes <= 0) return
    creature.bond = max(0.0, creature.bond - BOND_MISTAKE * mistakes)
}

// Fin real de la vida adulta: el previsto al hacerse adulta, más lo que
// le da el vínculo de ahora. Si el vínculo baja, el final se adelanta.
fun lifeEnd(creature: Creature, scale: Double = 1.0): Double? {
    val ends = creature.lifeEndsAt
    if (ends == null || ends == 0.0) return null
    return ends + (BOND_LIFE_MS * (creature.bond / BOND_MAX)) / scale
}
