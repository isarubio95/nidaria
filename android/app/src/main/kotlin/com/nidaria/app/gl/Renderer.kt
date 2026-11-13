// Dibuja una escena con OpenGL ES 3.0: pasada de sombras, pasada de color
// con antialiasing (MSAA en un búfer aparte) y lectura a imagen para las
// miniaturas. Todo se llama desde el hilo de dibujo.

package com.nidaria.app.gl

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Renderer {
    private class Program(val id: Int) {
        private val locations = HashMap<String, Int>()
        fun loc(name: String): Int = locations.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    }

    private class Target(var fbo: Int = 0, var color: Int = 0, var depth: Int = 0, var width: Int = 0, var height: Int = 0, var samples: Int = 0)

    private var context = 0
    private var lit: Program? = null
    private var basic: Program? = null
    private var shadowProgram: Program? = null
    private var depthProgram: Program? = null

    private var shadowFbo = 0
    private var shadowTexture = 0
    private var shadowSize = 0
    private var envTexture = 0
    private var envMaxLod = 0f
    private val sh = FloatArray(27)
    private var envData: ByteArray? = null
    private var anisotropy = 0f

    private val msaa = Target()
    private val resolve = Target()
    private var msaaOk = true

    private val opaque = ArrayList<Mesh>()
    private val transparent = ArrayList<Mesh>()
    private val casters = ArrayList<Mesh>()
    private val depthOf = HashMap<Mesh, Float>()

    private val normalMatrix = FloatArray(9)
    private val shadowMatrix = Mat4()
    private val lightViewProj = Mat4()
    private val identity = Mat4()
    private val scratch = Vec3()
    private val lightDirs = FloatArray(6)
    private val lightColors = FloatArray(6)

    // Fichero env.bin horneado desde el entorno de Three.js.
    fun setEnvironment(data: ByteArray) {
        envData = data
    }

    // El contexto es nuevo (o se perdió): todo lo subido hay que rehacerlo.
    // El número de contexto es único en todo el proceso, porque hay
    // geometrías compartidas que sobreviven a la vista que las dibujó: si
    // se repitiera, darían por buenos unos búferes que ya no existen.
    fun onContextCreated() {
        context = CONTEXTS.incrementAndGet()
        lit = Program(link(Shaders.VERTEX, Shaders.LIT_FRAGMENT))
        basic = Program(link(Shaders.VERTEX, Shaders.BASIC_FRAGMENT))
        shadowProgram = Program(link(Shaders.VERTEX, Shaders.SHADOW_FRAGMENT))
        depthProgram = Program(link(Shaders.DEPTH_VERTEX, Shaders.DEPTH_FRAGMENT))
        shadowFbo = 0
        shadowTexture = 0
        shadowSize = 0
        envTexture = 0
        msaa.fbo = 0
        resolve.fbo = 0
        msaaOk = true
        GpuTrash.geometries.clear()
        val extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        anisotropy = 0f
        if (extensions.contains("GL_EXT_texture_filter_anisotropic")) {
            val max = FloatArray(1)
            GLES30.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, max, 0)
            anisotropy = max[0]
        }
        uploadEnvironment()
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw IllegalStateException("El sombreador no compila: $log")
        }
        return shader
    }

    private fun link(vertex: String, fragment: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragment)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) throw IllegalStateException("El programa no enlaza: ${GLES30.glGetProgramInfoLog(program)}")
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    private fun uploadEnvironment() {
        val data = envData ?: return
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.position(4)
        val base = buffer.int
        val levels = buffer.int
        for (i in 0 until 27) sh[i] = buffer.float
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        envTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, envTexture)
        for (level in 0 until levels) {
            val size = base shr level
            for (face in 0 until 6) {
                val floats = FloatArray(size * size * 3)
                for (i in floats.indices) floats[i] = buffer.float
                val native = ByteBuffer.allocateDirect(floats.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                native.put(floats).position(0)
                GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, level, GLES30.GL_RGB16F,
                    size, size, 0, GLES30.GL_RGB, GLES30.GL_FLOAT, native,
                )
            }
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAX_LEVEL, levels - 1)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        envMaxLod = (levels - 1).toFloat()
    }

    private fun ensureShadowMap(size: Int) {
        if (shadowFbo != 0 && shadowSize == size) return
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        shadowTexture = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTexture)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, size, size, 0,
            GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        GLES30.glGenFramebuffers(1, ids, 0)
        shadowFbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowTexture, 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        shadowSize = size
    }

    // Búfer de color y profundidad; con `samples` > 0 es multimuestra.
    private fun ensureTarget(target: Target, width: Int, height: Int, samples: Int): Boolean {
        if (target.fbo != 0 && target.width == width && target.height == height && target.samples == samples) return true
        val ids = IntArray(1)
        if (target.fbo != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(target.fbo), 0)
            GLES30.glDeleteRenderbuffers(2, intArrayOf(target.color, target.depth), 0)
        }
        GLES30.glGenFramebuffers(1, ids, 0)
        target.fbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.fbo)
        GLES30.glGenRenderbuffers(1, ids, 0)
        target.color = ids[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, target.color)
        if (samples > 0) GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_RGBA8, width, height)
        else GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_RGBA8, width, height)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, target.color)
        GLES30.glGenRenderbuffers(1, ids, 0)
        target.depth = ids[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, target.depth)
        if (samples > 0) GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_DEPTH_COMPONENT24, width, height)
        else GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, width, height)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, target.depth)
        target.width = width
        target.height = height
        target.samples = samples
        val ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        if (!ok) Log.w(TAG, "Búfer incompleto (${width}x$height, $samples muestras)")
        return ok
    }

    private fun upload(geometry: Geometry): GpuGeometry {
        geometry.gpu?.let { if (it.context == context) return it }
        val ids = IntArray(1)
        GLES30.glGenVertexArrays(1, ids, 0)
        val vao = ids[0]
        GLES30.glBindVertexArray(vao)
        val buffers = IntArray(4)
        GLES30.glGenBuffers(4, buffers, 0)
        fun attribute(index: Int, data: FloatArray, size: Int) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[index])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, FloatBuffer.wrap(data), GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(index)
            GLES30.glVertexAttribPointer(index, size, GLES30.GL_FLOAT, false, 0, 0)
        }
        attribute(0, geometry.positions, 3)
        attribute(1, geometry.normals, 3)
        geometry.uvs?.let { attribute(2, it, 2) }
        geometry.indices?.let {
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[3])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, it.size * 4, java.nio.IntBuffer.wrap(it), GLES30.GL_STATIC_DRAW)
        }
        GLES30.glBindVertexArray(0)
        val gpu = GpuGeometry(vao, buffers, context)
        geometry.gpu = gpu
        return gpu
    }

    private fun bind(texture: Texture): Int {
        if (texture.glContext == context && texture.glVersion == texture.version && texture.glId != 0) return texture.glId
        val pixels = texture.pixels ?: return 0
        if (texture.glContext != context || texture.glId == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            texture.glId = ids[0]
            texture.glContext = context
        }
        // Three.js sube las imágenes con la fila de abajo primero (flipY).
        val row = texture.width * 4
        val flipped = ByteBuffer.allocateDirect(pixels.size)
        for (y in texture.height - 1 downTo 0) flipped.put(pixels, y * row, row)
        flipped.position(0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture.glId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_SRGB8_ALPHA8, texture.width, texture.height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, flipped,
        )
        val wrap = if (texture.repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        if (texture.mipmaps) {
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            if (anisotropy > 1f) GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, anisotropy)
        } else {
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        }
        texture.glVersion = texture.version
        return texture.glId
    }

    private fun emptyTrash() {
        for (gpu in GpuTrash.geometries) {
            if (gpu.context != context) continue
            GLES30.glDeleteVertexArrays(1, intArrayOf(gpu.vao), 0)
            GLES30.glDeleteBuffers(gpu.buffers.size, gpu.buffers, 0)
        }
        GpuTrash.geometries.clear()
    }

    private fun collect(node: Object3D, camera: PerspectiveCamera) {
        if (!node.visible) return
        if (node is Mesh && node.material.visible) {
            if (node.billboard) {
                // Mismo giro que la cámara, conservando sitio y tamaño.
                val e = node.matrixWorld.e
                scratch.set(e[12], e[13], e[14])
                node.matrixWorld.compose(scratch, camera.quaternion, node.scale)
            }
            if (node.castShadow) casters.add(node)
            if (node.material.transparent) {
                val e = node.matrixWorld.e
                depthOf[node] = scratch.set(e[12], e[13], e[14]).applyMat4(camera.viewProjection).z
                transparent.add(node)
            } else {
                opaque.add(node)
            }
        }
        for (child in node.children) collect(child, camera)
    }

    private fun mirrored(m: Mat4): Boolean {
        val e = m.e
        val det = e[0] * (e[5] * e[10] - e[6] * e[9]) - e[4] * (e[1] * e[10] - e[2] * e[9]) + e[8] * (e[1] * e[6] - e[2] * e[5])
        return det < 0f
    }

    private fun drawGeometry(mesh: Mesh) {
        val gpu = upload(mesh.geometry)
        GLES30.glBindVertexArray(gpu.vao)
        val indices = mesh.geometry.indices
        if (indices != null) GLES30.glDrawElements(GLES30.GL_TRIANGLES, indices.size, GLES30.GL_UNSIGNED_INT, 0)
        else GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, mesh.geometry.vertexCount)
    }

    private fun shadowPass(light: DirectionalLight) {
        ensureShadowMap(light.shadowMapSize)
        val view = Mat4().lookAt(light.position, light.target, Vec3(0f, 1f, 0f))
        view.e[12] = light.position.x
        view.e[13] = light.position.y
        view.e[14] = light.position.z
        view.invert()
        val projection = Mat4().orthographic(light.shadowLeft, light.shadowRight, light.shadowTop, light.shadowBottom, light.shadowNear, light.shadowFar)
        lightViewProj.multiplyMatrices(projection, view)
        // De coordenadas de recorte (-1 a 1) a coordenadas de textura (0 a 1).
        val bias = Mat4()
        bias.e[0] = 0.5f; bias.e[5] = 0.5f; bias.e[10] = 0.5f
        bias.e[12] = 0.5f; bias.e[13] = 0.5f; bias.e[14] = 0.5f
        shadowMatrix.multiplyMatrices(bias, lightViewProj)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glViewport(0, 0, shadowSize, shadowSize)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        val program = depthProgram!!
        GLES30.glUseProgram(program.id)
        GLES30.glUniformMatrix4fv(program.loc("uViewProj"), 1, false, lightViewProj.e, 0)
        for (mesh in casters) {
            // Como Three.js: las mallas de una cara proyectan con la cara de atrás.
            if (mesh.material.doubleSide) {
                GLES30.glDisable(GLES30.GL_CULL_FACE)
            } else {
                GLES30.glEnable(GLES30.GL_CULL_FACE)
                GLES30.glCullFace(GLES30.GL_FRONT)
            }
            GLES30.glFrontFace(if (mirrored(mesh.matrixWorld)) GLES30.GL_CW else GLES30.GL_CCW)
            GLES30.glUniformMatrix4fv(program.loc("uModel"), 1, false, mesh.matrixWorld.e, 0)
            drawGeometry(mesh)
        }
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glFrontFace(GLES30.GL_CCW)
    }

    private fun programFor(material: Material): Program = when (material.kind) {
        MaterialKind.Standard -> lit!!
        MaterialKind.Basic -> basic!!
        MaterialKind.Shadow -> shadowProgram!!
    }

    private fun setupFrame(program: Program, scene: Scene, camera: PerspectiveCamera, shadowLight: DirectionalLight?) {
        GLES30.glUseProgram(program.id)
        GLES30.glUniformMatrix4fv(program.loc("uViewProj"), 1, false, camera.viewProjection.e, 0)
        GLES30.glUniformMatrix4fv(program.loc("uShadowMatrix"), 1, false, (if (shadowLight != null) shadowMatrix else identity).e, 0)
        GLES30.glUniform1f(program.loc("uNormalBias"), shadowLight?.shadowNormalBias ?: 0f)
        GLES30.glUniform1f(program.loc("uShadowBias"), shadowLight?.shadowBias ?: 0f)
        GLES30.glUniform1f(program.loc("uShadowTexel"), if (shadowSize > 0) 1f / shadowSize else 0f)
        GLES30.glUniform1i(program.loc("uShadowMap"), 1)
        GLES30.glUniform1i(program.loc("uMap"), 0)
        if (program === lit) {
            GLES30.glUniform3f(program.loc("uCameraPos"), camera.position.x, camera.position.y, camera.position.z)
            val hemi = scene.hemisphere
            val hi = hemi?.intensity ?: 0f
            GLES30.glUniform3f(program.loc("uHemiSky"), (hemi?.sky?.r ?: 0f) * hi, (hemi?.sky?.g ?: 0f) * hi, (hemi?.sky?.b ?: 0f) * hi)
            GLES30.glUniform3f(program.loc("uHemiGround"), (hemi?.ground?.r ?: 0f) * hi, (hemi?.ground?.g ?: 0f) * hi, (hemi?.ground?.b ?: 0f) * hi)
            lightDirs.fill(0f)
            lightColors.fill(0f)
            // La luz con sombra va la primera: es la que el sombreador atenúa.
            val ordered = scene.lights.sortedByDescending { it === shadowLight }
            for ((i, light) in ordered.take(2).withIndex()) {
                scratch.copy(light.position).sub(light.target).normalize()
                lightDirs[i * 3] = scratch.x; lightDirs[i * 3 + 1] = scratch.y; lightDirs[i * 3 + 2] = scratch.z
                lightColors[i * 3] = light.color.r * light.intensity
                lightColors[i * 3 + 1] = light.color.g * light.intensity
                lightColors[i * 3 + 2] = light.color.b * light.intensity
            }
            GLES30.glUniform3fv(program.loc("uLightDir"), 2, lightDirs, 0)
            GLES30.glUniform3fv(program.loc("uLightColor"), 2, lightColors, 0)
            GLES30.glUniform1f(program.loc("uEnvIntensity"), if (envTexture != 0) scene.environmentIntensity else 0f)
            GLES30.glUniform1f(program.loc("uEnvMaxLod"), envMaxLod)
            GLES30.glUniform3fv(program.loc("uSH"), 9, sh, 0)
            GLES30.glUniform1i(program.loc("uEnvMap"), 2)
        }
    }

    private fun drawMesh(mesh: Mesh, hasShadows: Boolean) {
        val material = mesh.material
        val program = programFor(material)
        GLES30.glUseProgram(program.id)
        GLES30.glUniformMatrix4fv(program.loc("uModel"), 1, false, mesh.matrixWorld.e, 0)
        mesh.matrixWorld.normalMatrix(normalMatrix)
        GLES30.glUniformMatrix3fv(program.loc("uNormalMatrix"), 1, false, normalMatrix, 0)
        GLES30.glUniform2f(program.loc("uUvScale"), material.uvScaleX, material.uvScaleY)
        GLES30.glUniform3f(program.loc("uColor"), material.color.r, material.color.g, material.color.b)
        GLES30.glUniform1f(program.loc("uOpacity"), if (material.transparent) material.opacity else 1f)
        val receives = hasShadows && (mesh.receiveShadow || material.kind == MaterialKind.Shadow)
        GLES30.glUniform1f(program.loc("uReceiveShadow"), if (receives) 1f else 0f)
        when (material.kind) {
            MaterialKind.Standard -> {
                GLES30.glUniform1f(program.loc("uRoughness"), material.roughness)
                GLES30.glUniform1f(program.loc("uMetalness"), material.metalness)
                GLES30.glUniform3f(
                    program.loc("uSheenColor"),
                    material.sheenColor.r * material.sheen, material.sheenColor.g * material.sheen, material.sheenColor.b * material.sheen,
                )
                GLES30.glUniform1f(program.loc("uSheenRoughness"), material.sheenRoughness)
                GLES30.glUniform1f(program.loc("uClearcoat"), material.clearcoat)
                GLES30.glUniform1f(program.loc("uClearcoatRoughness"), material.clearcoatRoughness)
            }
            MaterialKind.Basic -> {
                val id = material.map?.let { bind(it) } ?: 0
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
                GLES30.glUniform1f(program.loc("uUseMap"), if (id != 0) 1f else 0f)
            }
            MaterialKind.Shadow -> Unit
        }
        if (material.doubleSide) GLES30.glDisable(GLES30.GL_CULL_FACE) else GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glFrontFace(if (mirrored(mesh.matrixWorld)) GLES30.GL_CW else GLES30.GL_CCW)
        if (material.transparent) {
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        } else {
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        GLES30.glDepthMask(material.depthWrite)
        drawGeometry(mesh)
    }

    private fun drawScene(scene: Scene, camera: PerspectiveCamera, fbo: Int, width: Int, height: Int, clearAlpha: Float) {
        scene.updateMatrixWorld()
        camera.updateView()
        opaque.clear()
        transparent.clear()
        casters.clear()
        depthOf.clear()
        collect(scene, camera)
        transparent.sortByDescending { depthOf[it] ?: 0f }

        val shadowLight = scene.lights.firstOrNull { it.castShadow }
        if (shadowLight != null) shadowPass(shadowLight)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(true)
        val bg = scene.background
        if (bg != null) {
            GLES30.glClearColor(Color.linearToSrgb(bg.r), Color.linearToSrgb(bg.g), Color.linearToSrgb(bg.b), 1f)
        } else {
            GLES30.glClearColor(0f, 0f, 0f, clearAlpha)
        }
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (shadowLight != null) shadowTexture else 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, envTexture)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        for (program in arrayOf(lit!!, basic!!, shadowProgram!!)) setupFrame(program, scene, camera, shadowLight)

        for (mesh in opaque) drawMesh(mesh, shadowLight != null)
        for (mesh in transparent) drawMesh(mesh, shadowLight != null)

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
        GLES30.glFrontFace(GLES30.GL_CCW)
    }

    // Dibuja en el búfer de pantalla, pasando por el búfer multimuestra.
    fun render(scene: Scene, camera: PerspectiveCamera, width: Int, height: Int) {
        emptyTrash()
        if (msaaOk && !ensureTarget(msaa, width, height, 4)) msaaOk = false
        if (msaaOk) {
            drawScene(scene, camera, msaa.fbo, width, height, 1f)
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, msaa.fbo)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
            GLES30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        } else {
            drawScene(scene, camera, 0, width, height, 1f)
        }
    }

    // Dibuja sobre fondo transparente y devuelve los píxeles RGBA
    // premultiplicados, fila superior primero. Se dibuja al doble de tamaño
    // y se reduce: el borde sale suave sin depender del MSAA.
    fun renderToPixels(scene: Scene, camera: PerspectiveCamera, size: Int): ByteArray {
        emptyTrash()
        val big = size * 2
        ensureTarget(resolve, big, big, 0)
        drawScene(scene, camera, resolve.fbo, big, big, 0f)
        val raw = ByteBuffer.allocateDirect(big * big * 4)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, resolve.fbo)
        GLES30.glReadPixels(0, 0, big, big, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, raw)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        val src = ByteArray(big * big * 4)
        raw.position(0)
        raw.get(src)
        val out = ByteArray(size * size * 4)
        for (y in 0 until size) {
            // glReadPixels empieza por abajo.
            val sy = (size - 1 - y) * 2
            for (x in 0 until size) {
                val sx = x * 2
                for (c in 0 until 4) {
                    val a = src[(sy * big + sx) * 4 + c].toInt() and 255
                    val b = src[(sy * big + sx + 1) * 4 + c].toInt() and 255
                    val d = src[((sy + 1) * big + sx) * 4 + c].toInt() and 255
                    val e = src[((sy + 1) * big + sx + 1) * 4 + c].toInt() and 255
                    out[(y * size + x) * 4 + c] = ((a + b + d + e + 2) / 4).toByte()
                }
            }
        }
        return out
    }

    // Foto de la escena a `width`×`height` píxeles, para la tarjeta de
    // compartir: RGBA, fila superior primero. Usa búferes propios (con
    // multimuestra si se puede) que se borran al acabar, para no tocar los
    // de la pantalla.
    fun renderPhoto(scene: Scene, camera: PerspectiveCamera, width: Int, height: Int): ByteArray {
        emptyTrash()
        val multi = Target()
        val flat = Target()
        try {
            ensureTarget(flat, width, height, 0)
            if (ensureTarget(multi, width, height, 4)) {
                drawScene(scene, camera, multi.fbo, width, height, 1f)
                GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, multi.fbo)
                GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, flat.fbo)
                GLES30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
            } else {
                drawScene(scene, camera, flat.fbo, width, height, 1f)
            }
            val raw = ByteBuffer.allocateDirect(width * height * 4)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, flat.fbo)
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, raw)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            // glReadPixels empieza por abajo.
            val row = width * 4
            val out = ByteArray(row * height)
            for (y in 0 until height) {
                raw.position((height - 1 - y) * row)
                raw.get(out, y * row, row)
            }
            return out
        } finally {
            release(multi)
            release(flat)
        }
    }

    private fun release(target: Target) {
        if (target.fbo == 0) return
        GLES30.glDeleteFramebuffers(1, intArrayOf(target.fbo), 0)
        GLES30.glDeleteRenderbuffers(2, intArrayOf(target.color, target.depth), 0)
        target.fbo = 0
    }

    companion object {
        private val CONTEXTS = java.util.concurrent.atomic.AtomicInteger(0)
        private const val TAG = "NidariaGL"
        private const val GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
        private const val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF
    }
}
