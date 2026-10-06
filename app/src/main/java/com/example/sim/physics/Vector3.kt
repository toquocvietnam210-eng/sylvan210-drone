package com.example.sim.physics

import kotlin.math.sqrt

data class Vector3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vector3 = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vector3 = Vector3(x / scalar, y / scalar, z / scalar)
    operator fun unaryMinus(): Vector3 = Vector3(-x, -y, -z)

    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun lengthSquared(): Float = x * x + y * y + z * z

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 0.00001f) Vector3(x / len, y / len, z / len) else Vector3(0f, 0f, 0f)
    }

    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 = Vector3(
        x = y * other.z - z * other.y,
        y = z * other.x - x * other.z,
        z = x * other.y - y * other.x
    )

    fun set(newX: Float, newY: Float, newZ: Float) {
        x = newX
        y = newY
        z = newZ
    }

    fun set(other: Vector3) {
        x = other.x
        y = other.y
        z = other.z
    }

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val UP = Vector3(0f, 1f, 0f)
        val FORWARD = Vector3(0f, 0f, 1f)
        val RIGHT = Vector3(1f, 0f, 0f)
    }
}
