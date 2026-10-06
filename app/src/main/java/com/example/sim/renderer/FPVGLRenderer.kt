package com.example.sim.renderer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.sim.physics.DronePhysics
import com.example.sim.physics.Vector3
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class FPVGLRenderer(
    private val context: Context,
    val physics: DronePhysics
) : GLSurfaceView.Renderer {

    var isThirdPersonCam: Boolean = false
    var gates: List<RacingGate> = TrackWorld.createGates()
    var obstacles: List<Obstacle> = TrackWorld.createObstacles()

    var onGatePassed: ((RacingGate) -> Unit)? = null

    // Transformation matrices
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)

    // Shader handles
    private var litProgramId: Int = 0
    private var uMVPMatrixHandle: Int = 0
    private var uModelMatrixHandle: Int = 0
    private var uColorHandle: Int = 0
    private var uLightPosHandle: Int = 0
    private var aPositionHandle: Int = 0
    private var aNormalHandle: Int = 0

    private var lineProgramId: Int = 0
    private var uLineMVPMatrixHandle: Int = 0
    private var uLineColorHandle: Int = 0
    private var aLinePositionHandle: Int = 0

    // Geometry buffers
    private lateinit var cubeBuffer: FloatBuffer
    private var cubeVertexCount: Int = 0

    private lateinit var gridBuffer: FloatBuffer
    private var gridVertexCount: Int = 0

    private val lightPosInWorld = floatArrayOf(20f, 60f, 30f, 1f)

    private var lastDronePos = Vector3(0f, 0.15f, 0f)
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.07f, 0.12f, 1.0f) // Dark atmospheric sky
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        initShaders()
        initGeometry()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)

        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        val fov = physics.config.cameraFovDeg
        Matrix.perspectiveM(projectionMatrix, 0, fov, aspect, 0.1f, 350f)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear color and depth
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Check gate pass triggers
        val currPos = physics.position.copy()
        for (gate in gates) {
            if (gate.checkPass(lastDronePos, currPos)) {
                onGatePassed?.invoke(gate)
            }
        }
        lastDronePos.set(currPos)

        // Setup View Matrix from Camera
        val (eye, target, up) = if (isThirdPersonCam) {
            physics.getChaseCameraVectors()
        } else {
            physics.getFPVCameraVectors()
        }

        Matrix.setLookAtM(
            viewMatrix, 0,
            eye.x, eye.y, eye.z,
            target.x, target.y, target.z,
            up.x, up.y, up.z
        )

        // 1. Draw Ground Grid and Horizon lines
        drawGrid()

        // 2. Draw Obstacles & Buildings
        for (obs in obstacles) {
            drawCube(
                obs.position.x, obs.position.y, obs.position.z,
                obs.scale.x, obs.scale.y, obs.scale.z,
                0f,
                obs.color
            )
        }

        // 3. Draw Racing Gates
        for (gate in gates) {
            drawRacingGate(gate)
        }

        // 4. Draw Drone Model (visible in Third Person mode or in Cockpit view front props)
        if (isThirdPersonCam) {
            drawDroneModel()
        } else {
            // Draw small FPV camera canopy / carbon frame tips at cockpit bottom
            drawCockpitFrame()
        }
    }

    private fun initShaders() {
        // Lit Shader with directional lighting and ambient
        val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            attribute vec4 aPosition;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            varying vec3 vFragPos;

            void main() {
                vFragPos = vec3(uModelMatrix * aPosition);
                vNormal = mat3(uModelMatrix) * aNormal;
                gl_Position = uMVPMatrix * aPosition;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLightPos;
            varying vec3 vNormal;
            varying vec3 vFragPos;

            void main() {
                // Ambient
                vec3 ambient = 0.35 * uColor.rgb;

                // Diffuse
                vec3 norm = normalize(vNormal);
                vec3 lightDir = normalize(uLightPos - vFragPos);
                float diff = max(dot(norm, lightDir), 0.0);
                vec3 diffuse = diff * uColor.rgb * 0.75;

                // Distance fog
                float dist = length(vFragPos);
                float fogFactor = clamp((dist - 30.0) / 180.0, 0.0, 0.65);
                vec3 fogColor = vec3(0.04, 0.07, 0.12);

                vec3 result = mix(ambient + diffuse, fogColor, fogFactor);
                gl_FragColor = vec4(result, uColor.a);
            }
        """.trimIndent()

        val vs = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fs = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        litProgramId = ShaderHelper.createAndLinkProgram(vs, fs)

        uMVPMatrixHandle = GLES20.glGetUniformLocation(litProgramId, "uMVPMatrix")
        uModelMatrixHandle = GLES20.glGetUniformLocation(litProgramId, "uModelMatrix")
        uColorHandle = GLES20.glGetUniformLocation(litProgramId, "uColor")
        uLightPosHandle = GLES20.glGetUniformLocation(litProgramId, "uLightPos")
        aPositionHandle = GLES20.glGetAttribLocation(litProgramId, "aPosition")
        aNormalHandle = GLES20.glGetAttribLocation(litProgramId, "aNormal")

        // Unlit Line Shader for Grid
        val lineVS = """
            uniform mat4 uMVPMatrix;
            attribute vec4 aPosition;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
            }
        """.trimIndent()

        val lineFS = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
                gl_FragColor = uColor;
            }
        """.trimIndent()

        val lvs = ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, lineVS)
        val lfs = ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, lineFS)
        lineProgramId = ShaderHelper.createAndLinkProgram(lvs, lfs)

        uLineMVPMatrixHandle = GLES20.glGetUniformLocation(lineProgramId, "uMVPMatrix")
        uLineColorHandle = GLES20.glGetUniformLocation(lineProgramId, "uColor")
        aLinePositionHandle = GLES20.glGetAttribLocation(lineProgramId, "aPosition")
    }

    private fun initGeometry() {
        val (cBuf, cCount) = MeshPrimitives.createCubeBuffer()
        cubeBuffer = cBuf
        cubeVertexCount = cCount

        val (gBuf, gCount) = MeshPrimitives.createGridBuffer(size = 180f, step = 4f)
        gridBuffer = gBuf
        gridVertexCount = gCount
    }

    private fun drawGrid() {
        GLES20.glUseProgram(lineProgramId)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        GLES20.glUniformMatrix4fv(uLineMVPMatrixHandle, 1, false, mvpMatrix, 0)
        // High-tech neon cyan / slate grid lines
        GLES20.glUniform4f(uLineColorHandle, 0.12f, 0.28f, 0.40f, 0.8f)

        GLES20.glEnableVertexAttribArray(aLinePositionHandle)
        gridBuffer.position(0)
        GLES20.glVertexAttribPointer(aLinePositionHandle, 3, GLES20.GL_FLOAT, false, 0, gridBuffer)

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, gridVertexCount)
        GLES20.glDisableVertexAttribArray(aLinePositionHandle)
    }

    private fun drawCube(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        yawDeg: Float,
        color: FloatArray
    ) {
        GLES20.glUseProgram(litProgramId)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (yawDeg != 0f) {
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform4fv(uColorHandle, 1, color, 0)
        GLES20.glUniform3f(uLightPosHandle, lightPosInWorld[0], lightPosInWorld[1], lightPosInWorld[2])

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glEnableVertexAttribArray(aNormalHandle)

        cubeBuffer.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, cubeBuffer)

        cubeBuffer.position(3)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, cubeBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, cubeVertexCount)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aNormalHandle)
    }

    private fun drawRacingGate(gate: RacingGate) {
        val color = if (gate.isPassed) {
            floatArrayOf(0.1f, 0.9f, 0.3f, 1.0f) // Bright green when cleared
        } else if (gate.isFinishLine) {
            floatArrayOf(1.0f, 0.84f, 0.0f, 1.0f) // Gold finish gate
        } else {
            floatArrayOf(1.0f, 0.40f, 0.0f, 1.0f) // High-visibility Neon Orange
        }

        val yaw = gate.yawDeg
        val w = gate.width
        val h = gate.height
        val t = 0.22f // Beam thickness

        val rad = Math.toRadians(yaw.toDouble())
        val cosY = cos(rad).toFloat()
        val sinY = sin(rad).toFloat()

        // Left post
        val leftOffsetX = -w * 0.5f * cosY
        val leftOffsetZ = -w * 0.5f * sinY
        drawCube(gate.position.x + leftOffsetX, gate.position.y + h * 0.5f, gate.position.z + leftOffsetZ, t, h, t, yaw, color)

        // Right post
        val rightOffsetX = w * 0.5f * cosY
        val rightOffsetZ = w * 0.5f * sinY
        drawCube(gate.position.x + rightOffsetX, gate.position.y + h * 0.5f, gate.position.z + rightOffsetZ, t, h, t, yaw, color)

        // Top bar
        drawCube(gate.position.x, gate.position.y + h, gate.position.z, w + t, t, t, yaw, color)
    }

    private fun drawDroneModel() {
        val droneMat = FloatArray(16)
        physics.orientation.toRotationMatrix(droneMat)

        // Center fuselage
        drawDronePart(droneMat, 0f, 0f, 0f, 0.08f, 0.04f, 0.18f, floatArrayOf(0.15f, 0.16f, 0.18f, 1f))

        // 4 Carbon Fiber Arms (X-configuration)
        val armDist = 0.12f
        drawDronePart(droneMat, armDist, 0f, armDist, 0.02f, 0.015f, 0.12f, floatArrayOf(0.25f, 0.25f, 0.28f, 1f), 45f)
        drawDronePart(droneMat, -armDist, 0f, armDist, 0.02f, 0.015f, 0.12f, floatArrayOf(0.25f, 0.25f, 0.28f, 1f), -45f)
        drawDronePart(droneMat, armDist, 0f, -armDist, 0.02f, 0.015f, 0.12f, floatArrayOf(0.25f, 0.25f, 0.28f, 1f), -45f)
        drawDronePart(droneMat, -armDist, 0f, -armDist, 0.02f, 0.015f, 0.12f, floatArrayOf(0.25f, 0.25f, 0.28f, 1f), 45f)

        // 4 Motors and Spinning Propellers
        val motorOffsets = arrayOf(
            floatArrayOf(armDist * 1.3f, 0.015f, armDist * 1.3f),
            floatArrayOf(-armDist * 1.3f, 0.015f, armDist * 1.3f),
            floatArrayOf(armDist * 1.3f, 0.015f, -armDist * 1.3f),
            floatArrayOf(-armDist * 1.3f, 0.015f, -armDist * 1.3f)
        )

        for (i in 0..3) {
            val off = motorOffsets[i]
            // Motor Bell
            drawDronePart(droneMat, off[0], off[1], off[2], 0.032f, 0.025f, 0.032f, floatArrayOf(0.85f, 0.25f, 0.1f, 1f))

            // Propeller blade
            val propAngleDeg = Math.toDegrees(physics.propRotations[i].toDouble()).toFloat()
            val propColor = if (i < 2) floatArrayOf(0.0f, 0.85f, 1.0f, 0.8f) else floatArrayOf(1.0f, 0.6f, 0.0f, 0.8f)
            drawDronePart(droneMat, off[0], off[1] + 0.015f, off[2], 0.12f, 0.005f, 0.018f, propColor, propAngleDeg)
        }

        // FPV Camera mount (tilted up at front)
        drawDronePart(droneMat, 0f, 0.035f, 0.08f, 0.035f, 0.035f, 0.04f, floatArrayOf(0.95f, 0.1f, 0.1f, 1f))

        // Rear status LED
        val ledColor = if (physics.isArmed) floatArrayOf(0.1f, 1.0f, 0.2f, 1f) else floatArrayOf(1.0f, 0.1f, 0.1f, 1f)
        drawDronePart(droneMat, 0f, 0.02f, -0.09f, 0.04f, 0.015f, 0.015f, ledColor)
    }

    private fun drawCockpitFrame() {
        // In FPV mode, draw slight edges of front prop blades at lower screen corners
        // so the pilot feels true immersion inside the drone frame
        val droneMat = FloatArray(16)
        physics.orientation.toRotationMatrix(droneMat)
        val armDist = 0.14f
        val propColor = floatArrayOf(0.0f, 0.85f, 1.0f, 0.35f)
        drawDronePart(droneMat, armDist * 1.1f, -0.02f, armDist * 1.1f, 0.11f, 0.005f, 0.02f, propColor, Math.toDegrees(physics.propRotations[0].toDouble()).toFloat())
        drawDronePart(droneMat, -armDist * 1.1f, -0.02f, armDist * 1.1f, 0.11f, 0.005f, 0.02f, propColor, Math.toDegrees(physics.propRotations[1].toDouble()).toFloat())
    }

    private fun drawDronePart(
        droneRotMat: FloatArray,
        localX: Float, localY: Float, localZ: Float,
        sx: Float, sy: Float, sz: Float,
        color: FloatArray,
        extraYawDeg: Float = 0f
    ) {
        GLES20.glUseProgram(litProgramId)

        Matrix.setIdentityM(modelMatrix, 0)
        // Drone world position
        Matrix.translateM(modelMatrix, 0, physics.position.x, physics.position.y, physics.position.z)
        // Drone world rotation
        Matrix.multiplyMM(modelMatrix, 0, modelMatrix.clone(), 0, droneRotMat, 0)
        // Drone local component offset
        Matrix.translateM(modelMatrix, 0, localX, localY, localZ)
        if (extraYawDeg != 0f) {
            Matrix.rotateM(modelMatrix, 0, extraYawDeg, 0f, 1f, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)

        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)
        GLES20.glUniform4fv(uColorHandle, 1, color, 0)
        GLES20.glUniform3f(uLightPosHandle, lightPosInWorld[0], lightPosInWorld[1], lightPosInWorld[2])

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glEnableVertexAttribArray(aNormalHandle)

        cubeBuffer.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, cubeBuffer)

        cubeBuffer.position(3)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 6 * 4, cubeBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, cubeVertexCount)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aNormalHandle)
    }

    fun resetGates() {
        gates.forEach { it.isPassed = false }
    }
}
