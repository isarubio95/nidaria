// Criaturas de modelo (.glb hecho fuera, con su esqueleto) en vez de
// esculpidas, como scene/models.js de la web. Un interruptor de desarrollo
// las apaga para comparar con las esculpidas.
//
// Cada familia de monstruos tiene un modelo por etapa (cría, joven y
// adulta); las ramas comparten modelo y se distinguen por el ADN. Perros y
// gatos aún salen esculpidos.

package com.nidaria.app.scene

import com.nidaria.app.rules.Genes
import com.nidaria.app.rules.getForm

// `hue`: tono (0-360) del pelo del modelo, para moverlo según el ADN.
// `arms`: "swing" (de pie, los brazos se balancean) o "legs".
// `ears = false`: lo que va en los huesos de las orejas es rígido (cuernos).
// `hueScale`: cuánto sigue el tono al ADN dentro de la banda (1 = del todo).
class ModelSpec(val id: String, val file: String, val hue: Double, val arms: String, val ears: Boolean, val height: Double, val hueScale: Double = 1.0)

object Models {
    // Lo pone la app al arrancar (preferencias) y se cambia en Perfil >
    // Pruebas, con las herramientas de desarrollo del servidor.
    @Volatile var enabled = false

    private class Model(val file: String, val hue: Double, val arms: String, val ears: Boolean, val hueScale: Double = 1.0)

    private val MODELS = mapOf(
        "mascota" to Model("mascota.glb", 22.0, "swing", true),
        "ascua" to Model("ascua.glb", 22.0, "swing", true),
        "brasa" to Model("brasa.glb", 355.0, "swing", false),
        // Hoja: el tono es el de las hojas, no el del pelo castaño.
        "brote" to Model("brote.glb", 100.0, "swing", true, hueScale = 0.3),
        "trepa" to Model("trepa.glb", 100.0, "swing", false, hueScale = 0.3),
        "bosque" to Model("bosque.glb", 100.0, "swing", false, hueScale = 0.3),
        "gotita" to Model("gotita.glb", 192.0, "swing", true, hueScale = 0.5),
        "chapo" to Model("chapo.glb", 192.0, "swing", true, hueScale = 0.5),
        "marea" to Model("marea.glb", 200.0, "swing", true, hueScale = 0.5),
    )

    // Modelo y altura (en la habitación) de cada etapa.
    private val LINES = mapOf(
        "pelusa" to mapOf("cria" to ("mascota" to 0.72), "joven" to ("ascua" to 0.95), "adulto" to ("brasa" to 1.25)),
        "hoja" to mapOf("cria" to ("brote" to 0.72), "joven" to ("trepa" to 0.95), "adulto" to ("bosque" to 1.3)),
        "gota" to mapOf("cria" to ("gotita" to 0.7), "joven" to ("chapo" to 0.95), "adulto" to ("marea" to 1.28)),
    )

    // El modelo de esta forma, o null si va esculpida.
    fun modelFor(formId: String, genes: Genes, on: Boolean = enabled): ModelSpec? {
        if (!on) return null
        val form = getForm(formId)
        val (id, height) = LINES[form.family ?: genes.family]?.get(form.stage) ?: return null
        val model = MODELS.getValue(id)
        return ModelSpec(id, model.file, model.hue, model.arms, model.ears, height, model.hueScale)
    }
}
