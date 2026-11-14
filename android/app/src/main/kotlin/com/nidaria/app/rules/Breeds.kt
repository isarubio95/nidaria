// GENERADO por android/tools/breeds.mjs a partir de shared/breeds.js. No editar a mano.
// Especies y razas: lo que necesitan las reglas (nombres, capas y ojos).

package com.nidaria.app.rules

class SpeciesInfo(val name: String, val plural: String, val blurb: String)

// `second`: el color de la primera zona pintada distinta del fondo.
class CoatInfo(val name: String, val base: String, val second: String?)

class BreedInfo(val id: String, val species: String, val name: String, val coats: List<CoatInfo>, val eyes: Int)

val SPECIES = listOf("perro", "gato", "monstruo")

val SPECIES_INFO = mapOf(
    "perro" to SpeciesInfo("Perro", "Perros", "Leales y con ganas de jugar. El huevo esconde la raza hasta que nace."),
    "gato" to SpeciesInfo("Gato", "Gatos", "Curiosos y muy suyos. El huevo esconde la raza hasta que nace."),
    "monstruo" to SpeciesInfo("Monstruo", "Monstruos", "Criaturas de tres familias que cambian muchísimo al crecer."),
)

val BREEDS = listOf(
    BreedInfo("labrador", "perro", "Labrador", listOf(CoatInfo("Amarillo", "#e6c48c", "#efdcb8"), CoatInfo("Chocolate", "#6a3f2c", null), CoatInfo("Negro", "#2a2523", null)), 2),
    BreedInfo("golden", "perro", "Golden retriever", listOf(CoatInfo("Dorado", "#d99a50", "#e7b672"), CoatInfo("Crema", "#e8c58f", "#efdcb8")), 1),
    BreedInfo("pastorAleman", "perro", "Pastor alemán", listOf(CoatInfo("Negro y fuego", "#b0612e", "#2a2523"), CoatInfo("Negro", "#2a2523", null), CoatInfo("Blanco", "#f5f0e8", null)), 1),
    BreedInfo("bulldogFrances", "perro", "Bulldog francés", listOf(CoatInfo("Leonado", "#d6a466", "#3b3634"), CoatInfo("Atigrado", "#4b3628", "#2a2523"), CoatInfo("Crema", "#efdcb8", null), CoatInfo("Azul", "#6e7783", "#f5f0e8")), 1),
    BreedInfo("bulldogIngles", "perro", "Bulldog inglés", listOf(CoatInfo("Blanco y rojo", "#f5f0e8", "#b85f2c"), CoatInfo("Leonado", "#d6a466", "#f5f0e8"), CoatInfo("Atigrado", "#4b3628", "#2a2523")), 1),
    BreedInfo("beagle", "perro", "Beagle", listOf(CoatInfo("Tricolor", "#c48446", "#2a2523"), CoatInfo("Limón", "#e2b46e", "#f5f0e8")), 1),
    BreedInfo("caniche", "perro", "Caniche", listOf(CoatInfo("Blanco", "#f5f0e8", null), CoatInfo("Albaricoque", "#e2a26a", null), CoatInfo("Negro", "#3b3634", null), CoatInfo("Chocolate", "#6a3f2c", null)), 1),
    BreedInfo("chihuahua", "perro", "Chihuahua", listOf(CoatInfo("Leonado", "#d6a466", "#efdcb8"), CoatInfo("Negro y fuego", "#2a2523", "#b0612e"), CoatInfo("Crema", "#efdcb8", null), CoatInfo("Chocolate", "#6a3f2c", "#efdcb8")), 1),
    BreedInfo("yorkshire", "perro", "Yorkshire terrier", listOf(CoatInfo("Azul y fuego", "#c98d4e", "#5f6873")), 1),
    BreedInfo("teckel", "perro", "Teckel", listOf(CoatInfo("Rojo", "#b85f2c", null), CoatInfo("Negro y fuego", "#2a2523", "#b0612e"), CoatInfo("Arlequín", "#8f939b", "#b0612e")), 1),
    BreedInfo("husky", "perro", "Husky siberiano", listOf(CoatInfo("Gris", "#6e7783", "#f5f0e8"), CoatInfo("Negro", "#3b3634", "#f5f0e8"), CoatInfo("Rojo", "#b85f2c", "#f5f0e8")), 4),
    BreedInfo("malamute", "perro", "Malamute", listOf(CoatInfo("Gris lobo", "#6f6a66", "#f5f0e8"), CoatInfo("Sable", "#7a5236", "#f5f0e8")), 1),
    BreedInfo("samoyedo", "perro", "Samoyedo", listOf(CoatInfo("Blanco", "#f6f3ee", null), CoatInfo("Bizcocho", "#efe1c6", null)), 1),
    BreedInfo("shiba", "perro", "Shiba inu", listOf(CoatInfo("Rojo", "#d9823f", "#efdcb8"), CoatInfo("Sésamo", "#9e6a3e", "#5a3d2b"), CoatInfo("Negro y fuego", "#2a2523", "#efdcb8")), 1),
    BreedInfo("akita", "perro", "Akita", listOf(CoatInfo("Rojo", "#c96f35", "#f5f0e8"), CoatInfo("Blanco", "#f5f0e8", null), CoatInfo("Atigrado", "#4b3628", "#2a2523")), 1),
    BreedInfo("chowChow", "perro", "Chow chow", listOf(CoatInfo("Rojo", "#c47437", null), CoatInfo("Crema", "#ecd3a4", null), CoatInfo("Negro", "#3b3634", null)), 1),
    BreedInfo("pomerania", "perro", "Pomerania", listOf(CoatInfo("Naranja", "#de8a3f", "#efb070"), CoatInfo("Crema", "#efd9b0", null), CoatInfo("Negro", "#3b3634", null)), 1),
    BreedInfo("corgi", "perro", "Corgi", listOf(CoatInfo("Rojo", "#d9823f", "#f5f0e8"), CoatInfo("Tricolor", "#2a2523", "#d9823f")), 1),
    BreedInfo("borderCollie", "perro", "Border collie", listOf(CoatInfo("Blanco y negro", "#2a2523", "#f5f0e8"), CoatInfo("Rojo y blanco", "#7d4b33", "#f5f0e8"), CoatInfo("Mirlo", "#8d939b", "#f5f0e8")), 3),
    BreedInfo("pastorAustraliano", "perro", "Pastor australiano", listOf(CoatInfo("Azul mirlo", "#9aa0a8", "#b0612e"), CoatInfo("Rojo mirlo", "#c99a7c", "#f5f0e8"), CoatInfo("Negro tricolor", "#2a2523", "#b0612e")), 3),
    BreedInfo("boxer", "perro", "Bóxer", listOf(CoatInfo("Leonado", "#c98444", "#3b3634"), CoatInfo("Atigrado", "#8a5a32", "#2a2523")), 1),
    BreedInfo("rottweiler", "perro", "Rottweiler", listOf(CoatInfo("Negro y fuego", "#2a2523", "#b0612e")), 1),
    BreedInfo("doberman", "perro", "Dóberman", listOf(CoatInfo("Negro y fuego", "#2a2523", "#b0612e"), CoatInfo("Marrón y fuego", "#6a3f2c", "#b0612e")), 1),
    BreedInfo("granDanes", "perro", "Gran danés", listOf(CoatInfo("Arlequín", "#f5f0e8", "#2a2523"), CoatInfo("Leonado", "#d6a466", "#3b3634"), CoatInfo("Azul", "#6e7783", null)), 1),
    BreedInfo("dalmata", "perro", "Dálmata", listOf(CoatInfo("Negro", "#f5f0e8", "#2a2523"), CoatInfo("Hígado", "#f5f0e8", "#7d4b33")), 2),
    BreedInfo("carlino", "perro", "Carlino", listOf(CoatInfo("Leonado", "#d6a466", "#3b3634"), CoatInfo("Negro", "#2a2523", null)), 1),
    BreedInfo("shihTzu", "perro", "Shih tzu", listOf(CoatInfo("Oro y blanco", "#f5f0e8", "#d9a35e"), CoatInfo("Negro y blanco", "#f5f0e8", "#3b3634")), 1),
    BreedInfo("maltes", "perro", "Bichón maltés", listOf(CoatInfo("Blanco", "#f7f4ee", null)), 1),
    BreedInfo("bichonFrise", "perro", "Bichón frisé", listOf(CoatInfo("Blanco", "#f7f4ee", null)), 1),
    BreedInfo("cocker", "perro", "Cocker spaniel", listOf(CoatInfo("Dorado", "#d08a42", null), CoatInfo("Negro", "#2a2523", null), CoatInfo("Azul ruano", "#8e9298", "#2a2523")), 1),
    BreedInfo("cavalier", "perro", "Cavalier King Charles", listOf(CoatInfo("Blenheim", "#f5f0e8", "#c46a35"), CoatInfo("Tricolor", "#f5f0e8", "#2a2523"), CoatInfo("Rubí", "#b85a2c", null)), 1),
    BreedInfo("schnauzer", "perro", "Schnauzer", listOf(CoatInfo("Sal y pimienta", "#7f8186", "#c8c8c6"), CoatInfo("Negro", "#3b3634", null), CoatInfo("Negro y plata", "#3b3634", "#b9bcc2")), 1),
    BreedInfo("westie", "perro", "West highland terrier", listOf(CoatInfo("Blanco", "#f6f3ec", null)), 1),
    BreedInfo("jackRussell", "perro", "Jack Russell terrier", listOf(CoatInfo("Blanco y fuego", "#f5f0e8", "#c48446"), CoatInfo("Tricolor", "#f5f0e8", "#2a2523")), 1),
    BreedInfo("bullTerrier", "perro", "Bull terrier", listOf(CoatInfo("Blanco", "#f5f0e8", "#4b3628"), CoatInfo("Atigrado", "#4b3628", "#f5f0e8")), 1),
    BreedInfo("staffordshire", "perro", "American staffordshire", listOf(CoatInfo("Azul", "#6c737d", "#f5f0e8"), CoatInfo("Leonado", "#d6a466", "#f5f0e8"), CoatInfo("Atigrado", "#4b3628", "#2a2523")), 2),
    BreedInfo("basset", "perro", "Basset hound", listOf(CoatInfo("Tricolor", "#c48446", "#2a2523"), CoatInfo("Limón y blanco", "#dfb06e", "#f5f0e8")), 1),
    BreedInfo("sharPei", "perro", "Shar pei", listOf(CoatInfo("Leonado", "#c99a62", null), CoatInfo("Crema", "#e8cfa4", null), CoatInfo("Negro", "#3b3634", null)), 1),
    BreedInfo("sanBernardo", "perro", "San Bernardo", listOf(CoatInfo("Rojo y blanco", "#b8622e", "#5a3220")), 1),
    BreedInfo("bernes", "perro", "Boyero de Berna", listOf(CoatInfo("Tricolor", "#2a2523", "#b0612e")), 1),
    BreedInfo("terranova", "perro", "Terranova", listOf(CoatInfo("Negro", "#3b3634", null), CoatInfo("Marrón", "#6a3f2c", null)), 1),
    BreedInfo("galgo", "perro", "Galgo", listOf(CoatInfo("Atigrado", "#8f6a48", "#3d2b20"), CoatInfo("Leonado", "#d6a466", null), CoatInfo("Negro", "#3b3634", "#f5f0e8"), CoatInfo("Blanco", "#f5f0e8", null)), 1),
    BreedInfo("podenco", "perro", "Podenco", listOf(CoatInfo("Canela y blanco", "#c9773c", "#f5f0e8"), CoatInfo("Canela", "#c9773c", null)), 1),
    BreedInfo("weimaraner", "perro", "Braco de Weimar", listOf(CoatInfo("Gris plata", "#9b9a96", null)), 2),
    BreedInfo("setter", "perro", "Setter irlandés", listOf(CoatInfo("Caoba", "#9c3f1f", null)), 1),
    BreedInfo("pinscher", "perro", "Pinscher miniatura", listOf(CoatInfo("Negro y fuego", "#2a2523", "#b0612e"), CoatInfo("Rojo", "#a8482a", null)), 1),
    BreedInfo("europeo", "gato", "Común europeo", listOf(CoatInfo("Atigrado pardo", "#9a7550", "#4a3526"), CoatInfo("Naranja", "#d9823f", "#a8541f"), CoatInfo("Esmoquin", "#2a2523", "#f5f0e8"), CoatInfo("Calicó", "#f5f0e8", null), CoatInfo("Carey", "#2a2523", null), CoatInfo("Negro", "#2a2523", null), CoatInfo("Gris", "#8a8d93", "#55585e")), 3),
    BreedInfo("siames", "gato", "Siamés", listOf(CoatInfo("Seal point", "#efe1cb", "#4a362c"), CoatInfo("Blue point", "#eceae6", "#6c7480"), CoatInfo("Chocolate point", "#f2e6d4", "#8a5a3e")), 1),
    BreedInfo("persa", "gato", "Persa", listOf(CoatInfo("Blanco", "#f5f0e8", null), CoatInfo("Crema", "#efdcb8", null), CoatInfo("Gris", "#9a9ea6", null), CoatInfo("Chinchilla", "#e9e6e0", "#b5b2ad")), 2),
    BreedInfo("himalayo", "gato", "Himalayo", listOf(CoatInfo("Seal point", "#efe3d0", "#4a362c"), CoatInfo("Flame point", "#f2e6d6", "#d9823f")), 1),
    BreedInfo("exotico", "gato", "Exótico de pelo corto", listOf(CoatInfo("Crema", "#e8c896", "#c99a5e"), CoatInfo("Gris", "#8e939b", null)), 1),
    BreedInfo("maineCoon", "gato", "Maine coon", listOf(CoatInfo("Atigrado pardo", "#8a6440", "#3a2a1e"), CoatInfo("Plata", "#b8b6b2", "#3d3b3a"), CoatInfo("Rojo", "#d9823f", "#a8541f"), CoatInfo("Negro", "#2a2523", null)), 3),
    BreedInfo("bosqueNoruega", "gato", "Bosque de Noruega", listOf(CoatInfo("Atigrado y blanco", "#8d7b67", "#3a3027"), CoatInfo("Blanco", "#f5f0e8", null)), 2),
    BreedInfo("siberiano", "gato", "Siberiano", listOf(CoatInfo("Atigrado dorado", "#b48a55", "#4d3826"), CoatInfo("Neva masquerade", "#ece4d6", "#5d544c")), 3),
    BreedInfo("ragdoll", "gato", "Ragdoll", listOf(CoatInfo("Seal mitted", "#efe3d0", "#4a362c"), CoatInfo("Blue bicolor", "#f2efea", "#77808c")), 1),
    BreedInfo("birmano", "gato", "Sagrado de Birmania", listOf(CoatInfo("Seal point", "#ecdcc0", "#4a362c")), 1),
    BreedInfo("scottishFold", "gato", "Scottish fold", listOf(CoatInfo("Gris azulado", "#8f97a2", null), CoatInfo("Atigrado plata", "#b9b7b3", "#4a4846"), CoatInfo("Crema", "#e9c89a", "#cf9f66")), 2),
    BreedInfo("britanico", "gato", "Británico de pelo corto", listOf(CoatInfo("Azul", "#7f8996", null), CoatInfo("Lila", "#a99a9c", null), CoatInfo("Dorado", "#d9b47a", "#8a6a3e")), 1),
    BreedInfo("azulRuso", "gato", "Azul ruso", listOf(CoatInfo("Azul", "#7c8794", null)), 1),
    BreedInfo("bombay", "gato", "Bombay", listOf(CoatInfo("Negro", "#262220", null)), 2),
    BreedInfo("abisinio", "gato", "Abisinio", listOf(CoatInfo("Rojizo", "#b8743e", "#8a5530"), CoatInfo("Sorrel", "#cf8e5a", "#f2dcbc")), 2),
    BreedInfo("somali", "gato", "Somalí", listOf(CoatInfo("Rojizo", "#b8743e", "#8a5530")), 2),
    BreedInfo("bengali", "gato", "Bengalí", listOf(CoatInfo("Leopardo", "#d39b56", "#4a2f1d"), CoatInfo("Nieve", "#ece4d6", "#8a7a68")), 2),
    BreedInfo("savannah", "gato", "Savannah", listOf(CoatInfo("Moteado", "#d4a964", "#2f2219")), 2),
    BreedInfo("oriental", "gato", "Oriental", listOf(CoatInfo("Ébano", "#2c2826", null), CoatInfo("Habana", "#6a4433", null), CoatInfo("Blanco", "#f5f0e8", null)), 2),
    BreedInfo("burmes", "gato", "Burmés", listOf(CoatInfo("Marta", "#5d4030", null), CoatInfo("Champán", "#c39e78", null)), 1),
    BreedInfo("devonRex", "gato", "Devon rex", listOf(CoatInfo("Crema", "#e2c9a4", null), CoatInfo("Gris", "#8f939a", null), CoatInfo("Calicó", "#f5f0e8", null)), 2),
    BreedInfo("cornishRex", "gato", "Cornish rex", listOf(CoatInfo("Negro", "#2c2826", null), CoatInfo("Blanco", "#f5f0e8", null)), 2),
    BreedInfo("esfinge", "gato", "Esfinge", listOf(CoatInfo("Rosado", "#e3b4a2", null), CoatInfo("Manchado", "#e3b4a2", null), CoatInfo("Gris", "#a99c9a", null)), 3),
    BreedInfo("angoraTurco", "gato", "Angora turco", listOf(CoatInfo("Blanco", "#f8f5f0", null)), 3),
    BreedInfo("vanTurco", "gato", "Van turco", listOf(CoatInfo("Rojo y blanco", "#f6f2ec", "#d9823f"), CoatInfo("Crema y blanco", "#f6f2ec", "#e2bd88")), 3),
    BreedInfo("munchkin", "gato", "Munchkin", listOf(CoatInfo("Atigrado", "#a9825a", "#4f3a28"), CoatInfo("Blanco y negro", "#f5f0e8", null)), 2),
    BreedInfo("manx", "gato", "Manx", listOf(CoatInfo("Atigrado", "#977452", "#3f2e22"), CoatInfo("Negro", "#2a2523", null)), 2),
    BreedInfo("bobtailJapones", "gato", "Bobtail japonés", listOf(CoatInfo("Mi-ke", "#f5f0e8", null), CoatInfo("Blanco", "#f5f0e8", null)), 3),
)

private val BREED_MAP = BREEDS.associateBy { it.id }

fun getBreed(id: String?): BreedInfo? = id?.let { BREED_MAP[it] }

fun breedsOf(species: String): List<BreedInfo> = BREEDS.filter { it.species == species }
