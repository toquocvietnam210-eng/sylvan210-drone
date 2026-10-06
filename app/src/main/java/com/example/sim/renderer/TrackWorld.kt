package com.example.sim.renderer

import com.example.sim.physics.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RacingGate(
    val id: Int,
    val position: Vector3,
    val width: Float = 3.5f,
    val height: Float = 3.5f,
    val yawDeg: Float = 0f,
    val isFinishLine: Boolean = false
) {
    var isPassed: Boolean = false

    /**
     * Test whether drone trajectory from lastPos to currPos crossed the gate plane within the aperture.
     */
    fun checkPass(lastPos: Vector3, currPos: Vector3): Boolean {
        if (isPassed) return false

        val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
        // Normal to gate plane
        val nx = -sin(yawRad)
        val nz = cos(yawRad)

        // Vector from gate center to positions
        val d1x = lastPos.x - position.x
        val d1z = lastPos.z - position.z

        val d2x = currPos.x - position.x
        val d2z = currPos.z - position.z

        val dot1 = d1x * nx + d1z * nz
        val dot2 = d2x * nx + d2z * nz

        // Plane crossing condition: dot products have opposite signs (or crossed from negative to positive)
        if (dot1 < 0f && dot2 >= 0f) {
            // Check if within gate bounds at intersection
            val t = -dot1 / (dot2 - dot1)
            val ix = lastPos.x + (currPos.x - lastPos.x) * t
            val iy = lastPos.y + (currPos.y - lastPos.y) * t
            val iz = lastPos.z + (currPos.z - lastPos.z) * t

            // Transverse vector (gate right)
            val rx = cos(yawRad)
            val rz = sin(yawRad)

            val relX = (ix - position.x) * rx + (iz - position.z) * rz
            val relY = iy - position.y

            if (abs(relX) <= width * 0.55f && relY >= -0.2f && relY <= height * 1.1f) {
                isPassed = true
                return true
            }
        }
        return false
    }
}

data class Obstacle(
    val position: Vector3,
    val scale: Vector3,
    val color: FloatArray, // RGBA
    val name: String = "Building"
)

object TrackWorld {
    fun createGates(): List<RacingGate> {
        return listOf(
            RacingGate(id = 1, position = Vector3(0f, 1.5f, 18f), width = 3.5f, height = 3.5f, yawDeg = 0f),
            RacingGate(id = 2, position = Vector3(15f, 4.0f, 45f), width = 3.8f, height = 3.8f, yawDeg = 25f),
            RacingGate(id = 3, position = Vector3(32f, 6.5f, 25f), width = 4.0f, height = 4.0f, yawDeg = 110f),
            RacingGate(id = 4, position = Vector3(20f, 3.0f, -10f), width = 3.5f, height = 3.5f, yawDeg = 190f),
            RacingGate(id = 5, position = Vector3(-12f, 5.0f, 0f), width = 3.8f, height = 3.8f, yawDeg = 260f),
            RacingGate(id = 6, position = Vector3(-5f, 1.8f, 10f), width = 3.6f, height = 3.6f, yawDeg = 330f, isFinishLine = true)
        )
    }

    fun createObstacles(): List<Obstacle> {
        return listOf(
            // Central Launch Pad
            Obstacle(Vector3(0f, 0.05f, 0f), Vector3(6f, 0.1f, 6f), floatArrayOf(0.18f, 0.22f, 0.28f, 1f), "LaunchPad"),

            // Pylons and Obstacle Towers for dives and power loops
            Obstacle(Vector3(25f, 6f, 35f), Vector3(3f, 12f, 3f), floatArrayOf(0.25f, 0.32f, 0.42f, 1f), "Tower1"),
            Obstacle(Vector3(-20f, 8f, 25f), Vector3(4f, 16f, 4f), floatArrayOf(0.20f, 0.26f, 0.36f, 1f), "Tower2"),
            Obstacle(Vector3(10f, 4f, -25f), Vector3(5f, 8f, 5f), floatArrayOf(0.30f, 0.28f, 0.26f, 1f), "Hangar1"),
            Obstacle(Vector3(-30f, 5f, -15f), Vector3(6f, 10f, 8f), floatArrayOf(0.22f, 0.30f, 0.38f, 1f), "Building"),

            // Slalom markers
            Obstacle(Vector3(8f, 2.5f, 28f), Vector3(0.5f, 5f, 0.5f), floatArrayOf(1.0f, 0.5f, 0.0f, 1f), "Slalom1"),
            Obstacle(Vector3(22f, 3.5f, 15f), Vector3(0.5f, 7f, 0.5f), floatArrayOf(0.0f, 0.8f, 1.0f, 1f), "Slalom2"),
            Obstacle(Vector3(-10f, 2.5f, -12f), Vector3(0.5f, 5f, 0.5f), floatArrayOf(1.0f, 0.2f, 0.5f, 1f), "Slalom3")
        )
    }
}
