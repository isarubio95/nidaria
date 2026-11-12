package com.nidaria.app

import com.nidaria.app.gl.Animator
import com.nidaria.app.gl.Eases
import com.nidaria.app.gl.Euler
import com.nidaria.app.gl.Geometry
import com.nidaria.app.gl.Mat4
import com.nidaria.app.gl.Object3D
import com.nidaria.app.gl.PerspectiveCamera
import com.nidaria.app.gl.Pos
import com.nidaria.app.gl.Quat
import com.nidaria.app.gl.Shape
import com.nidaria.app.gl.Vec3
import com.nidaria.app.gl.capsuleGeometry
import com.nidaria.app.gl.cylinderGeometry
import com.nidaria.app.gl.extrudeGeometry
import com.nidaria.app.gl.rayHitsUnitSphere
import com.nidaria.app.gl.shapeGeometry
import com.nidaria.app.gl.sphereGeometry
import com.nidaria.app.gl.to
import com.nidaria.app.gl.torusGeometry
import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val pi = PI.toFloat()

    @Test
    fun matrixInverseIsExact() {
        val node = Object3D()
        node.position.set(1.5f, -2f, 0.7f)
        node.rotation.set(0.4f, -1.1f, 2.3f)
        node.scale.set(1.2f, 0.8f, 2f)
        node.updateMatrixWorld()
        val product = Mat4().multiplyMatrices(node.matrixWorld, Mat4().copy(node.matrixWorld).invert())
        for (i in 0 until 16) assertEquals(if (i % 5 == 0) 1f else 0f, product.e[i], 1e-5f)
    }

    @Test
    fun eulerAndQuaternionStayInSync() {
        for (order in Euler.Order.entries) {
            val node = Object3D()
            node.rotation.order = order
            node.rotation.set(0.3f, -0.8f, 1.2f)
            val q = Quat().copy(node.quaternion)
            // Volver a poner el mismo cuaternión no debe cambiar los ángulos.
            node.quaternion.copy(q)
            assertEquals(0.3f, node.rotation.x, 1e-5f)
            assertEquals(-0.8f, node.rotation.y, 1e-5f)
            assertEquals(1.2f, node.rotation.z, 1e-5f)
        }
    }

    @Test
    fun unitVectorsRotation() {
        val to = Vec3(0.3f, 0.8f, -0.5f).normalize()
        val q = Quat().setFromUnitVectors(Vec3.Z, to)
        val moved = Vec3.Z.applyQuat(q)
        assertEquals(to.x, moved.x, 1e-5f)
        assertEquals(to.y, moved.y, 1e-5f)
        assertEquals(to.z, moved.z, 1e-5f)
    }

    @Test
    fun cameraProjectsAndUnprojects() {
        val camera = PerspectiveCamera(30f, 0.5f, 0.1f, 80f)
        camera.position.set(0f, 1.5f, 6f)
        camera.lookAt(0f, 0.7f, 0f)
        camera.updateProjectionMatrix()
        camera.updateView()
        val center = camera.project(Vec3(0f, 0.7f, 0f))
        assertEquals(0f, center.x, 1e-4f)
        assertEquals(0f, center.y, 1e-4f)
        val back = camera.unproject(camera.project(Vec3(0.4f, 0.2f, -1f)))
        assertEquals(0.4f, back.x, 1e-3f)
        assertEquals(0.2f, back.y, 1e-3f)
        assertEquals(-1f, back.z, 1e-3f)
    }

    private fun volume(g: Geometry): Float {
        var total = 0f
        val idx = g.indices
        val count = idx?.size ?: g.vertexCount
        var i = 0
        while (i < count) {
            val a = (idx?.get(i) ?: i) * 3
            val b = (idx?.get(i + 1) ?: (i + 1)) * 3
            val c = (idx?.get(i + 2) ?: (i + 2)) * 3
            val p = g.positions
            total += (
                p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1]) -
                    p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c]) +
                    p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])
                ) / 6f
            i += 3
        }
        return total
    }

    // Con las caras mirando hacia fuera, el volumen con signo es positivo.
    @Test
    fun solidsFaceOutwards() {
        assertEquals(4f / 3f * pi, volume(sphereGeometry(1f, 40, 28)), 0.05f)
        assertEquals(pi * 0.25f * 2f, volume(cylinderGeometry(0.5f, 0.5f, 2f, 48)), 0.02f)
        assertTrue(volume(torusGeometry(1f, 0.2f, 12, 36)) > 0.7f)
        val capsule = volume(capsuleGeometry(0.2f, 0.3f, 8, 20))
        assertEquals(pi * 0.04f * 0.3f + 4f / 3f * pi * 0.008f, capsule, 0.004f)
    }

    private fun leaf(length: Float, width: Float): Shape = Shape().moveTo(0f, 0f)
        .bezierCurveTo(width * 0.75f, length * 0.2f, width * 0.55f, length * 0.8f, 0f, length)
        .bezierCurveTo(-width * 0.55f, length * 0.8f, -width * 0.75f, length * 0.2f, 0f, 0f)

    private fun area(g: Geometry): Float {
        var total = 0f
        val idx = g.indices!!
        var i = 0
        while (i < idx.size) {
            val a = idx[i] * 3
            val b = idx[i + 1] * 3
            val c = idx[i + 2] * 3
            val p = g.positions
            total += ((p[b] - p[a]) * (p[c + 1] - p[a + 1]) - (p[b + 1] - p[a + 1]) * (p[c] - p[a])) / 2f
            i += 3
        }
        return total
    }

    @Test
    fun shapesTriangulateCompletely() {
        // Cuadrado, estrella cóncava y arco con hueco en forma de U.
        val square = Shape().moveTo(0f, 0f).lineTo(2f, 0f).lineTo(2f, 2f).lineTo(0f, 2f).closePath()
        assertEquals(4f, area(shapeGeometry(square)), 1e-4f)

        val star = Shape()
        for (i in 0 until 8) {
            val r = if (i % 2 == 1) 0.17f else 0.5f
            val a = (i / 8f) * 2f * pi + pi / 2f
            if (i == 0) star.moveTo(kotlin.math.cos(a) * r, kotlin.math.sin(a) * r) else star.lineTo(kotlin.math.cos(a) * r, kotlin.math.sin(a) * r)
        }
        star.closePath()
        val starArea = 8 * 0.5f * 0.5f * 0.17f * kotlin.math.sin(pi / 4f)
        assertEquals(starArea, area(shapeGeometry(star)), 1e-3f)

        val arch = Shape().moveTo(-0.5f, 0f).lineTo(-0.5f, 0.6f).absarc(0f, 0.6f, 0.5f, pi, 0f, true).lineTo(0.5f, 0f).closePath()
        assertEquals(0.6f + pi * 0.125f, area(shapeGeometry(arch, 20)), 2e-3f)

        val leafArea = area(shapeGeometry(leaf(1f, 0.5f), 14))
        assertTrue(leafArea > 0.25f && leafArea < 0.45f)
    }

    @Test
    fun extrusionIsClosedAndOutward() {
        val plain = extrudeGeometry(leaf(1f, 0.5f), depth = 0.1f, curveSegments = 14)
        val base = area(shapeGeometry(leaf(1f, 0.5f), 14))
        assertEquals(base * 0.1f, volume(plain), 1e-3f)
        val bevel = extrudeGeometry(leaf(1f, 0.5f), 0.05f, true, 0.025f, 0.025f, 3, 14)
        assertTrue(volume(bevel) > base * 0.05f)
        val b = bevel.bounds()
        assertEquals(-0.025f, b[2], 1e-5f)
        assertEquals(0.075f, b[5], 1e-5f)
        // Las normales son unitarias.
        var i = 0
        while (i < bevel.normals.size) {
            val l = bevel.normals[i] * bevel.normals[i] + bevel.normals[i + 1] * bevel.normals[i + 1] + bevel.normals[i + 2] * bevel.normals[i + 2]
            assertTrue(abs(l - 1f) < 1e-3f || l == 0f)
            i += 3
        }
    }

    @Test
    fun rayHitsEllipsoid() {
        val node = Object3D()
        node.position.set(0f, 1f, 0f)
        node.scale.set(0.5f, 1f, 0.5f)
        node.updateMatrixWorld()
        val hit = rayHitsUnitSphere(Vec3(0f, 1f, 5f), Vec3(0f, 0f, -1f), node.matrixWorld)
        assertEquals(4.5f, hit, 1e-4f)
        assertTrue(rayHitsUnitSphere(Vec3(2f, 1f, 5f), Vec3(0f, 0f, -1f), node.matrixWorld) < 0f)
    }

    @Test
    fun timelinePositionsFollowGsap() {
        val anim = Animator()
        val a = Vec3()
        val b = Vec3()
        val calls = ArrayList<Float>()
        var finished = false
        anim.timeline(onComplete = { finished = true })
            .to(a.to(x = 1f), 0.2f, Eases.none)
            .to(b.to(x = 1f), 0.6f, Eases.none, Pos.withPrev())
            .to(a.to(y = 1f), 0.2f, Eases.none, Pos.afterPrev(-0.2f))
            .call { calls.add(anim.time) }
            .to(a.to(z = 2f), 0.1f, Eases.none, Pos.at(1f))
        // b empieza con a; el tercero empieza en 0.4 y acaba en 0.6.
        anim.update(0.1f)
        assertEquals(0.5f, a.x, 1e-4f)
        assertEquals(1f / 6f, b.x, 1e-4f)
        assertEquals(0f, a.y, 1e-4f)
        anim.update(0.4f)
        assertEquals(1f, a.x, 1e-4f)
        assertEquals(0.5f, a.y, 1e-4f)
        assertTrue(calls.isEmpty())
        anim.update(0.15f)
        assertEquals(1, calls.size)
        assertEquals(1f, b.x, 1e-4f)
        assertTrue(!finished)
        anim.update(0.5f)
        assertEquals(2f, a.z, 1e-4f)
        assertTrue(finished)
    }

    @Test
    fun yoyoEndsWhereItStarted() {
        val anim = Animator()
        val v = Vec3(0f, 0f, 0f)
        anim.to(v.to(y = 1f), 0.1f, Eases.power2Out, repeat = 1, yoyo = true)
        anim.update(0.1f)
        assertEquals(1f, v.y, 1e-3f)
        anim.update(0.2f)
        assertEquals(0f, v.y, 1e-4f)
    }

    @Test
    fun easesHitTheirEnds() {
        for (ease in listOf(Eases.power1Out, Eases.power2InOut, Eases.power3In, Eases.sineOut, Eases.backOut(2f), Eases.elasticOut(1f, 0.5f), Eases.bounceOut)) {
            assertEquals(0f, ease(0f), 2e-2f)
            assertEquals(1f, ease(1f), 1e-4f)
        }
        // elastic.out(1, 0.5) de GSAP en p = 0.25
        assertEquals(1f + Math.pow(2.0, -2.5).toFloat() * kotlin.math.sin((0.25f - 0.125f) * 2f * pi / 0.5f), Eases.elasticOut(1f, 0.5f)(0.25f), 1e-5f)
    }
}
