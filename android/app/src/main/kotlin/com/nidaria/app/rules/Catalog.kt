// Catálogo de la tienda. Los efectos se suman a las necesidades (0 a 100).

package com.nidaria.app.rules

class Category(val id: String, val name: String)

val CATEGORIES = listOf(
    Category("comida", "Comida"), Category("cuidado", "Cuidado"), Category("juguete", "Juguetes"), Category("ropa", "Ropa"),
    Category("casa", "Casa"),
)

val SLOTS = listOf(Category("cabeza", "Cabeza"), Category("cara", "Cara"), Category("cuello", "Cuello"))

// Sitios de la habitación que se pueden decorar. La habitación es del
// jugador, no de la criatura. `base` es lo que hay si no se ha puesto
// nada (null: el sitio queda vacío).
class Spot(val id: String, val name: String, val base: String?)

val SPOTS = listOf(
    Spot("pared", "Pared", "Tablas azules"),
    Spot("alfombra", "Alfombra", "Alfombra marinera"),
    Spot("rincon", "Rincón", "Planta"),
    Spot("ventana", "Ventana", null),
)

class Item(
    val id: String,
    val name: String,
    val price: Int,
    val kind: String,
    val blurb: String,
    val hunger: Int = 0,
    val fun_: Int = 0,
    val health: Int = 0,
    val energy: Int = 0,
    val sweet: Boolean = false,
    val cures: Boolean = false,
    val bath: Boolean = false,
    val slot: String? = null,
    val reusable: Boolean = false,
    val spot: String? = null,
)

private fun food(id: String, name: String, price: Int, hunger: Int, blurb: String, fun_: Int = 0, health: Int = 0, sweet: Boolean = false) =
    Item(id, name, price, "comida", blurb, hunger = hunger, fun_ = fun_, health = health, sweet = sweet)

private fun toy(id: String, name: String, price: Int, fun_: Int, energy: Int, blurb: String) =
    Item(id, name, price, "juguete", blurb, fun_ = fun_, energy = energy, reusable = true)

private fun wear(id: String, name: String, price: Int, slot: String, blurb: String) =
    Item(id, name, price, "ropa", blurb, slot = slot, reusable = true)

private fun decor(id: String, name: String, price: Int, spot: String, blurb: String) =
    Item(id, name, price, "casa", blurb, spot = spot, reusable = true)

val ITEMS = listOf(
    food("manzana", "Manzana", 4, 14, "Crujiente y sana.", health = 1),
    food("zanahoria", "Zanahoria", 4, 12, "Buena para todo.", health = 2),
    food("pan", "Pan", 6, 20, "Recién hecho."),
    food("arroz", "Bol de arroz", 10, 30, "Llena de verdad."),
    food("sopa", "Sopa", 14, 26, "Calentita; sienta bien.", health = 4),
    food("pescado", "Pescado", 16, 34, "A la plancha.", health = 1),
    food("galleta", "Galleta", 5, 7, "Un capricho pequeño.", fun_ = 6, sweet = true),
    food("helado", "Helado", 12, 8, "Dulce y frío.", fun_ = 16, sweet = true),
    food("tarta", "Tarta", 18, 18, "Para celebrar.", fun_ = 14, sweet = true),
    food("festin", "Festín", 40, 60, "De todo un poco.", fun_ = 10, health = 5),

    Item("medicina", "Medicina", 30, "cuidado", "Cura cuando está enferma.", cures = true, health = 15),
    Item("vitaminas", "Vitaminas", 22, "cuidado", "Sube la salud.", health = 12),
    Item("jabon", "Jabón de burbujas", 10, "cuidado", "Baño completo y divertido.", bath = true, fun_ = 6),

    toy("pelota", "Pelota", 60, 16, 6, "Bota mucho."),
    toy("peonza", "Peonza", 90, 20, 5, "Gira sin parar."),
    toy("peluche", "Peluche", 110, 14, 0, "Para abrazar; no cansa."),
    toy("cometa", "Cometa", 140, 28, 9, "Vuela alto."),

    wear("lazo", "Lazo", 70, "cabeza", "Un lazo grande."),
    wear("gorro", "Gorro de lana", 80, "cabeza", "Con pompón."),
    wear("gorra", "Gorra", 100, "cabeza", "Con visera."),
    wear("paja", "Sombrero de paja", 120, "cabeza", "Para el sol."),
    wear("flor", "Flor", 90, "cabeza", "Una margarita."),
    wear("corona", "Corona", 400, "cabeza", "Para criaturas con clase."),
    wear("gafas", "Gafas redondas", 110, "cara", "De pasta fina."),
    wear("gafasol", "Gafas de sol", 150, "cara", "Muy oscuras."),
    wear("bigote", "Bigote", 95, "cara", "De quita y pon."),
    wear("panuelo", "Pañuelo", 60, "cuello", "Atado al cuello."),
    wear("pajarita", "Pajarita", 75, "cuello", "Elegante."),
    wear("bufanda", "Bufanda", 85, "cuello", "Larga y a rayas."),
    wear("cascabel", "Cascabel", 100, "cuello", "Suena al andar."),

    decor("papel-rayas", "Rayas de menta", 180, "pared", "Rayas finas, frescas y tranquilas."),
    decor("papel-vichy", "Cuadros", 240, "pared", "Como un mantel de merienda."),
    decor("papel-flores", "Florecitas", 300, "pared", "Margaritas por todas partes."),
    decor("papel-olas", "Olas", 360, "pared", "Para soñar con el mar."),
    decor("papel-estrellas", "Noche de estrellas", 450, "pared", "Lila, con estrellas de oro."),

    decor("alfombra-menta", "Alfombra menta", 180, "alfombra", "Redonda y suave."),
    decor("alfombra-flor", "Alfombra flor", 300, "alfombra", "Con pétalos alrededor."),
    decor("alfombra-arcoiris", "Alfombra arcoíris", 380, "alfombra", "Un color en cada vuelta."),
    decor("alfombra-nube", "Alfombra nube", 480, "alfombra", "Mullida como una nube."),

    decor("cactus", "Cactus", 200, "ventana", "No hay que regarlo casi nunca."),
    decor("girasol", "Girasol", 280, "ventana", "Siempre mira hacia la luz."),
    decor("lampara", "Lámpara de pie", 420, "ventana", "Luz cálida para leer."),
    decor("pecera", "Pecera", 650, "ventana", "Con un pez que saluda."),

    decor("cojin", "Cojín grande", 160, "rincon", "Para tumbarse a descansar."),
    decor("mesita", "Mesita con flores", 300, "rincon", "Un jarrón con tulipanes."),
    decor("baul", "Baúl de juguetes", 450, "rincon", "Siempre se queda algo fuera."),
    decor("camita", "Camita", 600, "rincon", "Un nido blandito con su manta."),
)

private val BY_ID = ITEMS.associateBy { it.id }

fun getItem(id: String): Item? = BY_ID[id]

fun itemsOfKind(kind: String): List<Item> = ITEMS.filter { it.kind == kind }

const val TOY_COOLDOWN_MS = 20.0 * 60 * 1000
const val BATH_COOLDOWN_MS = 5.0 * 60 * 1000
const val MAX_STACK = 99
