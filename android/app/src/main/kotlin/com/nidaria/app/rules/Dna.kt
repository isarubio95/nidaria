// ADN de una criatura: familia, colores, proporciones y carácter.

package com.nidaria.app.rules

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlinx.serialization.Serializable

// Familias de monstruo y de mascota (ver shared/dna.js). Perros y gatos
// son una familia cada uno; su aspecto sale de la raza.
val MONSTER_FAMILIES = listOf("pelusa", "hoja", "gota")
val PET_FAMILIES = listOf("perro", "gato")
val FAMILIES = MONSTER_FAMILIES + PET_FAMILIES

class FamilyInfo(val name: String, val blurb: String, val hues: List<IntArray>, val species: String)

val FAMILY_INFO = mapOf(
    "pelusa" to FamilyInfo("Pelusa", "Zorros de pelo encendido y mucho genio.", listOf(intArrayOf(4, 40), intArrayOf(196, 212)), "monstruo"),
    "hoja" to FamilyInfo("Hoja", "Pequeños dinosaurios con hojas por cresta.", listOf(intArrayOf(78, 160)), "monstruo"),
    "gota" to FamilyInfo("Gota", "Anfibios lisos, con aletas, de agua.", listOf(intArrayOf(186, 228), intArrayOf(232, 248)), "monstruo"),
    "perro" to FamilyInfo("Perro", "Leal y con ganas de jugar.", emptyList(), "perro"),
    "gato" to FamilyInfo("Gato", "Curioso y muy suyo.", emptyList(), "gato"),
)

fun isPet(family: String?): Boolean = family in PET_FAMILIES

// Especie de una familia: perro, gato o monstruo.
fun speciesOf(family: String?): String = FAMILY_INFO[family]?.species ?: "monstruo"


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
    // Familia (o raza, en las mascotas) del progenitor si el huevo
    // heredero cambió de familia (o de raza).
    val origin: String? = null,
    // Solo perros y gatos: raza, capa y color de ojos (índices).
    val breed: String? = null,
    val coat: Int? = null,
    val eyes: Int? = null,
)

fun Genes.breedInfo(): BreedInfo? = getBreed(breed)

// La raza en las mascotas; la familia en los monstruos.
fun Genes.kindName(): String = breedInfo()?.name ?: FAMILY_INFO[family]?.name ?: ""

// Lo que se sabe de un huevo antes de elegirlo: solo sus colores.
@Serializable
data class EggLook(
    val index: Int = 0,
    val species: String = "monstruo",
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

// Color en hexadecimal a tono, saturación y luz, como hexToHsl en dna.js.
fun hexToHsl(hex: String): IntArray {
    val n = hex.substring(1).toInt(16)
    val r = ((n shr 16) and 255) / 255.0
    val g = ((n shr 8) and 255) / 255.0
    val b = (n and 255) / 255.0
    val hi = maxOf(r, g, b)
    val lo = minOf(r, g, b)
    val l = (hi + lo) / 2
    var h = 0.0
    var s = 0.0
    if (hi != lo) {
        val d = hi - lo
        s = if (l > 0.5) d / (2 - hi - lo) else d / (hi + lo)
        h = when (hi) {
            r -> (g - b) / d + (if (g < b) 6 else 0)
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        }
        h *= 60
    }
    return intArrayOf(jsRound(h) % 360, jsRound(s * 100), jsRound(l * 100))
}

// Los colores de una mascota (para el huevo y la tarjeta) salen de su capa.
private fun Genes.withCoat(breed: BreedInfo, coat: Int, eyes: Int): Genes {
    val info = breed.coats[coat]
    val hsl = hexToHsl(info.base)
    val hue2 = info.second?.let { hexToHsl(it)[0] } ?: hsl[0]
    return copy(breed = breed.id, coat = coat, eyes = eyes, hue = hsl[0], sat = hsl[1], light = hsl[2], hue2 = hue2)
}

// `family` puede faltar: entonces sale una familia de monstruo al azar.
fun createGenes(seed: Long, family: String? = null): Genes {
    val rng = Rng(seed)
    val fam = family ?: rng.pick(MONSTER_FAMILIES)
    val pet = isPet(fam)
    val hue = if (pet) 0 else pickHue(rng, fam)
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
    val genes = Genes(
        v = 1, seed = seed and 0xffffffffL, family = fam, hue = hue, sat = sat, light = light, hue2 = hue2,
        pattern = pattern, patternAmt = patternAmt, build = build, eyeStyle = eyeStyle,
        traits = listOf(first, second), favFood = fav, hateFood = hate,
    )
    if (!pet) return genes
    val breed = rng.pick(breedsOf(fam))
    val coat = rng.int(0, breed.coats.size - 1)
    val eyes = rng.int(0, breed.eyes - 1)
    return genes.withCoat(breed, coat, eyes)
}
