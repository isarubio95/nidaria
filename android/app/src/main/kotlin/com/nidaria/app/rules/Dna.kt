// ADN de una criatura: familia, colores, proporciones y carácter.

package com.nidaria.app.rules

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlinx.serialization.Serializable

val FAMILIES = listOf("pelusa", "hoja", "gota")

class FamilyInfo(val name: String, val blurb: String, val hues: List<IntArray>)

val FAMILY_INFO = mapOf(
    "pelusa" to FamilyInfo("Pelusa", "Blandas y de pelo esponjoso.", listOf(intArrayOf(8, 48), intArrayOf(318, 352))),
    "hoja" to FamilyInfo("Hoja", "Brotan con hojas y tallos.", listOf(intArrayOf(72, 158))),
    "gota" to FamilyInfo("Gota", "Lisas y de agua.", listOf(intArrayOf(172, 232), intArrayOf(246, 286))),
)

class TraitInfo(val name: String, val blurb: String)

val TRAITS = linkedMapOf(
    "gloton" to TraitInfo("Glotona", "Le entra hambre antes."),
    "dormilon" to TraitInfo("Dormilona", "Se cansa antes y duerme profundo."),
    "travieso" to TraitInfo("Traviesa", "Se aburre enseguida."),
    "mimoso" to TraitInfo("Mimosa", "Las caricias le alegran el doble."),
    "presumido" to TraitInfo("Presumida", "Odia estar sucia."),
    "fuerte" to TraitInfo("Fuerte", "Aguanta mejor cuando algo le falta."),
    "timido" to TraitInfo("Tímida", "Tarda en coger confianza."),
    "curioso" to TraitInfo("Curiosa", "Jugar le divierte más."),
)

private val TRAIT_IDS = TRAITS.keys.toList()
private val PATTERNS = listOf("liso", "motas", "rayas", "barriga", "calcetines")
private val FOODS = listOf("manzana", "zanahoria", "pan", "arroz", "sopa", "pescado", "tarta", "helado", "galleta")

@Serializable
data class Build(
    val round: Double = 0.5,
    val head: Double = 0.5,
    val ears: Double = 0.5,
    val eyes: Double = 0.5,
    val eyeGap: Double = 0.5,
    val tail: Double = 0.5,
)

@Serializable
data class Genes(
    val v: Int = 1,
    val seed: Long = 0,
    val family: String = "pelusa",
    val hue: Int = 0,
    val sat: Int = 60,
    val light: Int = 68,
    val hue2: Int = 0,
    val pattern: String = "liso",
    val patternAmt: Double = 0.5,
    val build: Build = Build(),
    val eyeStyle: Int = 0,
    val traits: List<String> = emptyList(),
    val favFood: String = "",
    val hateFood: String = "",
    // Familia del progenitor si el huevo heredero cambió de familia.
    val origin: String? = null,
)

// Lo que se sabe de un huevo antes de elegirlo: solo sus colores.
@Serializable
data class EggLook(
    val index: Int = 0,
    val heir: Boolean = false,
    val family: String = "pelusa",
    val hue: Int = 0,
    val sat: Int = 60,
    val light: Int = 68,
    val hue2: Int = 0,
)

fun Genes.eggLook() = EggLook(family = family, hue = hue, sat = sat, light = light, hue2 = hue2)

private fun jsRound(x: Double): Int = floor(x + 0.5).toInt()

// Como `+x.toFixed(2)` en JavaScript.
private fun fixed2(x: Double): Double = BigDecimal(x).setScale(2, RoundingMode.HALF_UP).toDouble()

private fun pickHue(rng: Rng, family: String): Int {
    val band = rng.pick(FAMILY_INFO.getValue(family).hues)
    return jsRound(rng.range(band[0].toDouble(), band[1].toDouble()))
}

fun createGenes(seed: Long, family: String? = null): Genes {
    val rng = Rng(seed)
    val fam = family ?: rng.pick(FAMILIES)
    val hue = pickHue(rng, fam)
    val first = rng.pick(TRAIT_IDS)
    var second = rng.pick(TRAIT_IDS)
    while (second == first) second = rng.pick(TRAIT_IDS)
    val fav = rng.pick(FOODS)
    var hate = rng.pick(FOODS)
    while (hate == fav) hate = rng.pick(FOODS)
    val sat = jsRound(rng.range(52.0, 74.0))
    val light = jsRound(rng.range(62.0, 74.0))
    val hue2 = ((hue + rng.pick(listOf(-150, -40, -24, 24, 40, 150, 180)) + 360) % 360)
    val pattern = rng.pick(PATTERNS)
    val patternAmt = fixed2(rng.range(0.25, 1.0))
    val build = Build(
        round = fixed2(rng.range(0.0, 1.0)),
        head = fixed2(rng.range(0.0, 1.0)),
        ears = fixed2(rng.range(0.0, 1.0)),
        eyes = fixed2(rng.range(0.0, 1.0)),
        eyeGap = fixed2(rng.range(0.0, 1.0)),
        tail = fixed2(rng.range(0.0, 1.0)),
    )
    val eyeStyle = rng.int(0, 2)
    return Genes(
        v = 1, seed = seed and 0xffffffffL, family = fam, hue = hue, sat = sat, light = light, hue2 = hue2,
        pattern = pattern, patternAmt = patternAmt, build = build, eyeStyle = eyeStyle,
        traits = listOf(first, second), favFood = fav, hateFood = hate,
    )
}
