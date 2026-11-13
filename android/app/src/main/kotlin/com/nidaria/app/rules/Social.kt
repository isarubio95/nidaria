// Amigos, visitas y regalos (como shared/social.js). Las reglas de verdad
// las aplica el servidor; aquí están las que la app necesita para no
// mandar algo que se sabe que no vale.

package com.nidaria.app.rules

// Se regala comida y cuidados, de lo que uno tiene.
val GIFT_KINDS = listOf("comida", "cuidado")

fun canGift(itemId: String): Boolean = getItem(itemId)?.kind in GIFT_KINDS

// Código de amigo: 8 caracteres del alfabeto de Crockford, «K7QM-4XPA».
const val FRIEND_CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
const val FRIEND_CODE_LENGTH = 8

// Lo que escribe el jugador, sin guiones ni espacios y en mayúsculas. Las
// letras que se confunden con cifras cuentan como esas cifras. null si no
// tiene la forma de un código.
fun normalizeFriendCode(input: String): String? {
    val clean = input.uppercase().replace(Regex("[\\s-]"), "").replace('O', '0').replace(Regex("[IL]"), "1")
    if (clean.length != FRIEND_CODE_LENGTH) return null
    return if (clean.all { it in FRIEND_CODE_ALPHABET }) clean else null
}
