// Criaturas de modelo (.glb hecho fuera, con su esqueleto) en vez de
// esculpidas, como scene/models.js de la web. De momento es una prueba
// detrás de un interruptor de desarrollo: apagado, todo sigue esculpido.
//
// La línea de prueba es la de las pelusas: la cría y la joven son
// `mascota` y las adultas, `brasa`. Las demás familias salen esculpidas.

package com.nidaria.app.scene

import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.getForm

// `hue`: tono (0-360) del pelo del modelo, para moverlo según el ADN.
// `arms`: "swing" (de pie, los brazos se balancean) o "legs".
// `ears = false`: lo que va en los huesos de las orejas es rígido (cuernos).
class ModelSpec(val id: String, val file: String, val hue: Double, val arms: String, val ears: Boolean, val height: Double)

object Models {
    // Lo pone la app al arrancar (preferencias) y se cambia en Perfil >
    // Pruebas, con las herramientas de desarrollo del servidor.
    @Volatile var enabled = false

    private class Model(val file: String, val hue: Double, val arms: String, val ears: Boolean)

    private val MODELS = mapOf(
        "mascota" to Model("mascota.glb", 22.0, "swing", true),
        "brasa" to Model("brasa.glb", 355.0, "swing", false),
    )

    // Modelo y altura (en la habitación) de cada etapa.
    private val LINES = mapOf(
        "pelusa" to mapOf("cria" to ("mascota" to 0.72), "joven" to ("mascota" to 0.92), "adulto" to ("brasa" to 1.25)),
    )

    // El modelo de esta forma, o null si va esculpida.
    fun modelFor(formId: String, genes: Genes, on: Boolean = enabled): ModelSpec? {
        if (!on) return null
        val form = getForm(formId)
        val (id, height) = LINES[form.family ?: genes.family]?.get(form.stage) ?: return null
        val model = MODELS.getValue(id)
        return ModelSpec(id, model.file, model.hue, model.arms, model.ears, height)
    }
}
