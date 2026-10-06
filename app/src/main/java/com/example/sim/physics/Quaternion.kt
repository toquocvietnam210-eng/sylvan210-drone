package com.example.sim.physics

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Quaternion(
    var w: Float = 1f,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    fun normalize(): Quaternion {
        val mag = sqrt(w * w + x * x + y * y + z * z)
        if (mag > 0.00001f) {
            w /= mag
            x /= mag
            y /= mag
            z /= mag
        } else {
            w = 1f
            x = 0f
            y = 0f
            z = 0f
        }
        return this
    }

    operator fun times(q: Quaternion): Quaternion = Quaternion(
        w = w * q.w - x * q.x - y * q.y - z * q.z,
        x = w * q.x + x * q.w + y * q.z - z * q.y,
        y = w * q.y - x * q.z + y * q.w + z * q.x,
        z = w * q.z + x * q.y - y * q.x + z * q.w
    )

    fun rotate(v: Vector3): Vector3 {
        // q * v * q^-1
        val qv = Vector3(x, y, z)
        val uv = qv.cross(v)
        val uuv = qv.cross(uv)
        val uvW = uv * (2.0f * w)
        val uuv2 = uuv * 2.0f
        return v + uvW + uuv2
    }

    /**
     * Integrate angular velocity vector (rad/sec) over dt seconds.
     */
    fun integrateAngularVelocity(omega: Vector3, dt: Float): Quaternion {
        val halfDt = 0.5f * dt
        val dq = Quaternion(
            w = 0f,
            x = omega.x * halfDt,
            y = omega.y * halfDt,
            z = omega.z * halfDt
        )
        val dRot = this * dq
        w += dRot.w
        x += dRot.x
        y += dRot.y
        z += dRot.z
        return normalize()
    }

    /**
     * Get Euler angles in radians (Pitch, Roll, Yaw).
     * Pitch: X axis rotation (rad), Roll: Z axis rotation (rad), Yaw: Y axis rotation (rad)
     */
    fun toEulerAngles(): Triple<Float, Float, Float> {
        val forward = rotate(Vector3(0f, 0f, 1f))
        val right = rotate(Vector3(1f, 0f, 0f))
        val up = rotate(Vector3(0f, 1f, 0f))

        val yaw = atan2(forward.x, forward.z)
        val pitch = asin(-forward.y.coerceIn(-1f, 1f))
        val roll = atan2(right.y, up.y)

        return Triple(pitch, roll, yaw)
    }

    fun toRotationMatrix(matrix: FloatArray, offset: Int = 0) {
        val xx = x * x
        val xy = x * y
        val xz = x * z
        val xw = x * w

        val yy = y * y
        val yz = y * z
        val yw = y * w

        val zz = z * z
        val zw = z * w

        matrix[offset + 0] = 1f - 2f * (yy + zz)
        matrix[offset + 1] = 2f * (xy + zw)
        matrix[offset + 2] = 2f * (xz - yw)
        matrix[offset + 3] = 0f

        matrix[offset + 4] = 2f * (xy - zw)
        matrix[offset + 5] = 1f - 2f * (xx + zz)
        matrix[offset + 6] = 2f * (yz + xw)
        matrix[offset + 7] = 0f

        matrix[offset + 8] = 2f * (xz + yw)
        matrix[offset + 9] = 2f * (yz - xw)
        matrix[offset + 10] = 1f - 2f * (xx + yy)
        matrix[offset + 11] = 0f

        matrix[offset + 12] = 0f
        matrix[offset + 13] = 0f
        matrix[offset + 14] = 0f
        matrix[offset + 15] = 1f
    }

    companion object {
        fun fromAxisAngle(axis: Vector3, angleRad: Float): Quaternion {
            val halfAngle = angleRad * 0.5f
            val s = sin(halfAngle)
            val n = axis.normalized()
            return Quaternion(
                w = cos(halfAngle),
                x = n.x * s,
                y = n.y * s,
                z = n.z * s
            ).normalize()
        }

        fun fromEuler(pitchRad: Float, rollRad: Float, yawRad: Float): Quaternion {
            val qPitch = fromAxisAngle(Vector3(1f, 0f, 0f), pitchRad)
            val qRoll = fromAxisAngle(Vector3(0f, 0f, 1f), rollRad)
            val qYaw = fromAxisAngle(Vector3(0f, 1f, 0f), yawRad)
            return (qYaw * qPitch * qRoll).normalize()
        }

        val IDENTITY = Quaternion(1f, 0f, 0f, 0f)
    }
}
