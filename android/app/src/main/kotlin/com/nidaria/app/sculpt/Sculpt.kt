// La escultura tal como la usan los modelos (como Sculpt en sdf.js de la
// web): colores en sRGB o en lineal, utilidades para pegarse a la
// superficie y el paso a geometría, que se esculpe en segundo plano.

package com.nidaria.app.sculpt

import com.nidaria.app.gl.Geometry

private val WHITE = Col.hex("#ffffff")

class Surface(val point: V3, val normal: V3)

class Sculpt(ground: Double? = null, groundK: Double = 0.03) : Field(ground, groundK) {
    // `k` es cuánto se funde con lo anterior; `colorK`, lo ancho del paso
    // de su color al de al lado; `bone`, el hueso que la mueve.
    fun add(form: Shape, color: Col = WHITE, k: Double = 0.04, bone: Int = 0, colorK: Double? = null, split: Split? = null): Sculpt {
        addShape(form, color.toArray(), k, bone, colorK, split)
        return this
    }

    fun add(form: Shape, color: String, k: Double = 0.04, bone: Int = 0, colorK: Double? = null, split: Split? = null): Sculpt =
        add(form, Col.hex(color), k, bone, colorK, split)

    fun sub(form: Shape, k: Double = 0.02): Sculpt {
        subShape(form, k)
        return this
    }

    // Pinta sin cambiar la forma: lo que cae dentro de `form` toma el color.
    fun paint(form: Shape, color: Col, soft: Double = 0.008, amount: Double = 1.0): Sculpt {
        paintShape(form, color.toArray(), soft, amount)
        return this
    }

    fun paint(form: Shape, color: String, soft: Double = 0.008, amount: Double = 1.0): Sculpt = paint(form, Col.hex(color), soft, amount)

    fun gradient(x: Double, y: Double, z: Double, h: Double, out: V3): V3 {
        out.set(
            sdf(x + h, y, z) - sdf(x - h, y, z),
            sdf(x, y + h, z) - sdf(x, y - h, z),
            sdf(x, y, z + h) - sdf(x, y, z - h),
        )
        val l = out.length()
        if (l > 1e-12) out.divideScalar(l) else out.set(0.0, 1.0, 0.0)
        return out
    }

    // Lleva un punto a la superficie siguiendo el gradiente.
    fun project(point: V3, steps: Int = 4): Surface {
        val p = point.clone()
        val n = V3()
        val h = 0.002
        for (i in 0 until steps) {
            val dd = sdf(p.x, p.y, p.z)
            gradient(p.x, p.y, p.z, h, n)
            p.addScaledVector(n, -dd)
        }
        gradient(p.x, p.y, p.z, h, n)
        return Surface(p, n)
    }

    // Convierte la escultura en geometría. Puede llegar vacía (`ready`
    // falso) y rellenarse sola al terminar de esculpirse.
    fun build(cell: Double = 0.02, bones: Int = 0, ao: Double = 1.0, aoReach: Double = 0.18): Geometry =
        Sculptor.geometryFor(toJob(MeshOptions(cell, bones, ao, aoReach)))
}

// Rejilla que se adapta a la superficie, para pintar encima (la boca, los
// coloretes, los ojos cerrados): PlaneGeometry de Three.js con cada
// vértice llevado a la escultura.
fun surfacePatch(sculpt: Sculpt, center: V3, normal: V3, up: V3, width: Double, height: Double, segments: Int = 10, lift: Double = 0.0025): Geometry {
    val n = normal.clone().normalize()
    val right = V3().crossVectors(up, n).normalize()
    val top = V3().crossVectors(n, right).normalize()
    val grid1 = segments + 1
    val segW = width / segments
    val segH = height / segments
    val positions = FloatArray(grid1 * grid1 * 3)
    val uvs = FloatArray(grid1 * grid1 * 2)
    for (iy in 0..segments) {
        val y = iy * segH - height / 2
        for (ix in 0..segments) {
            val x = ix * segW - width / 2
            val i = iy * grid1 + ix
            positions[i * 3] = x.toFloat()
            positions[i * 3 + 1] = (-y).toFloat()
            uvs[i * 2] = (ix.toDouble() / segments).toFloat()
            uvs[i * 2 + 1] = (1 - iy.toDouble() / segments).toFloat()
        }
    }
    val indices = IntArray(segments * segments * 6)
    var k = 0
    for (iy in 0 until segments) {
        for (ix in 0 until segments) {
            val a = ix + grid1 * iy
            val b = ix + grid1 * (iy + 1)
            val c = (ix + 1) + grid1 * (iy + 1)
            val dd = (ix + 1) + grid1 * iy
            indices[k++] = a; indices[k++] = b; indices[k++] = dd
            indices[k++] = b; indices[k++] = c; indices[k++] = dd
        }
    }
    val p = V3()
    val g = V3()
    for (i in 0 until grid1 * grid1) {
        p.copy(center).addScaledVector(right, positions[i * 3].toDouble()).addScaledVector(top, positions[i * 3 + 1].toDouble()).addScaledVector(n, 0.02)
        for (it in 0 until 4) {
            val dd = sculpt.sdf(p.x, p.y, p.z)
            sculpt.gradient(p.x, p.y, p.z, 0.002, g)
            p.addScaledVector(g, -dd)
        }
        sculpt.gradient(p.x, p.y, p.z, 0.002, g)
        p.addScaledVector(g, lift)
        positions[i * 3] = p.x.toFloat()
        positions[i * 3 + 1] = p.y.toFloat()
        positions[i * 3 + 2] = p.z.toFloat()
    }
    return Geometry(positions, FloatArray(positions.size), uvs, indices).computeVertexNormals()
}
