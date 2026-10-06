package com.example.sim.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class DronePhysics(
    var config: DroneConfig = DroneConfig.freestyle5Inch()
) {
    // 6-DOF Rigid body state
    val position = Vector3(0f, 0.15f, 0f)
    val velocity = Vector3(0f, 0f, 0f)
    var orientation = Quaternion.IDENTITY.copy()
    val angularVelocity = Vector3(0f, 0f, 0f) // rad/s in body frame (x=pitch, y=yaw, z=roll)

    // Motor and system state
    var isArmed: Boolean = false
    var isCrashed: Boolean = false
    var crashReason: String = ""
    var actualThrottle: Float = 0f // 0.0 to 1.0 (motor RPM response)

    // Telemetry stats
    var batteryVoltage: Float = 25.2f // 6S LiPo: 25.2V max
    var currentAmps: Float = 0f
    var mahConsumed: Float = 0f
    var flightTimeSeconds: Float = 0f
    var maxSpeedKmh: Float = 0f
    var maxAltitudeM: Float = 0f

    // Propeller visual rotation angles (radians)
    val propRotations = FloatArray(4) { 0f }

    private val gravity = 9.81f
    private val frameRadius = 0.15f // 15cm radius 5" quad

    fun reset(startPos: Vector3 = Vector3(0f, 0.15f, 0f), startYawDeg: Float = 0f) {
        position.set(startPos)
        velocity.set(0f, 0f, 0f)
        val yawRad = Math.toRadians(startYawDeg.toDouble()).toFloat()
        orientation = Quaternion.fromEuler(0f, 0f, yawRad)
        angularVelocity.set(0f, 0f, 0f)
        actualThrottle = 0f
        isCrashed = false
        crashReason = ""
        flightTimeSeconds = 0f
        currentAmps = 0f
        mahConsumed = 0f
        batteryVoltage = config.cellCount * 4.2f
    }

    fun turtleModeFlip() {
        if (!isArmed && position.y <= 0.25f) {
            // Re-orient right side up
            val (_, _, currentYaw) = orientation.toEulerAngles()
            orientation = Quaternion.fromEuler(0f, 0f, currentYaw)
            position.y = 0.20f
            velocity.set(0f, 0.5f, 0f)
            angularVelocity.set(0f, 0f, 0f)
            isCrashed = false
            crashReason = ""
        }
    }

    /**
     * Step the 6-DOF simulation by dt seconds.
     * @param throttleInput 0.0 to 1.0
     * @param yawInput -1.0 to 1.0 (left / right)
     * @param pitchInput -1.0 to 1.0 (back / forward)
     * @param rollInput -1.0 to 1.0 (left / right)
     */
    fun update(
        dt: Float,
        throttleInput: Float,
        yawInput: Float,
        pitchInput: Float,
        rollInput: Float
    ) {
        val clampedDt = dt.coerceIn(0.001f, 0.05f)

        if (isArmed && !isCrashed) {
            flightTimeSeconds += clampedDt
        }

        // 1. Motor response (low-pass filter on throttle)
        val targetThrottle = if (isArmed && !isCrashed) {
            val idleThrot = config.idleThrottlePercent / 100f
            max(idleThrot, throttleInput.coerceIn(0f, 1f))
        } else {
            0f
        }

        val tau = (config.motorResponseTimeMs / 1000f).coerceAtLeast(0.01f)
        val alpha = (clampedDt / tau).coerceIn(0f, 1f)
        actualThrottle += (targetThrottle - actualThrottle) * alpha

        // Battery voltage simulation & current draw
        if (isArmed) {
            val idleCurrent = 1.2f
            val maxCurrent = 75.0f * (config.thrustToWeightRatio / 4.8f)
            currentAmps = idleCurrent + (maxCurrent - idleCurrent) * (actualThrottle * actualThrottle)
            val mahUsedThisStep = (currentAmps * 1000f) * (clampedDt / 3600f)
            mahConsumed += mahUsedThisStep

            // Cell discharge curve: 4.2V down to 3.5V nominal, with voltage sag
            val dischargeFraction = (mahConsumed / config.batteryCapacityMah).coerceIn(0f, 1f)
            val nominalPerCell = 4.2f - (dischargeFraction * 0.7f)
            val sagPerCell = (currentAmps / 80f) * 0.35f
            batteryVoltage = config.cellCount * (nominalPerCell - sagPerCell).coerceAtLeast(3.2f)
        } else {
            currentAmps = 0f
        }

        // Spin propellers visually (props spin opposite in pairs)
        val propSpeed = if (isArmed) (actualThrottle * 150f + 30f) else 0f
        propRotations[0] += propSpeed * clampedDt
        propRotations[1] -= propSpeed * clampedDt
        propRotations[2] -= propSpeed * clampedDt
        propRotations[3] += propSpeed * clampedDt

        // 2. Rotational Dynamics & Betaflight PID / Rate response
        if (isArmed && !isCrashed) {
            when (config.flightMode) {
                FlightMode.ACRO -> {
                    // Acro mode: stick inputs directly command angular rates (deg/s converted to rad/s)
                    val targetRollRateDeg = config.rollRate.calculateRate(rollInput)
                    val targetPitchRateDeg = config.pitchRate.calculateRate(pitchInput)
                    val targetYawRateDeg = config.yawRate.calculateRate(yawInput)

                    val targetRollRateRad = Math.toRadians(targetRollRateDeg.toDouble()).toFloat()
                    val targetPitchRateRad = Math.toRadians(targetPitchRateDeg.toDouble()).toFloat()
                    val targetYawRateRad = Math.toRadians(targetYawRateDeg.toDouble()).toFloat()

                    // PID rate tracking: high bandwidth motor response
                    val pGain = 38.0f // P gain response
                    angularVelocity.z += (targetRollRateRad - angularVelocity.z) * pGain * clampedDt
                    angularVelocity.x += (targetPitchRateRad - angularVelocity.x) * pGain * clampedDt
                    angularVelocity.y += (targetYawRateRad - angularVelocity.y) * (pGain * 0.8f) * clampedDt
                }
                FlightMode.ANGLE -> {
                    // Angle mode: stick inputs dictate target lean angles
                    val (currentPitch, currentRoll, _) = orientation.toEulerAngles()

                    val maxTiltRad = Math.toRadians(config.angleModeMaxTiltDeg.toDouble()).toFloat()
                    val targetPitchAngle = pitchInput * maxTiltRad
                    val targetRollAngle = rollInput * maxTiltRad

                    // Angle error to target rate
                    val anglePGain = 9.0f
                    val targetPitchRate = (targetPitchAngle - currentPitch) * anglePGain
                    val targetRollRate = (targetRollAngle - currentRoll) * anglePGain
                    val targetYawRate = Math.toRadians(config.yawRate.calculateRate(yawInput).toDouble()).toFloat()

                    val rateGain = 32.0f
                    angularVelocity.x += (targetPitchRate - angularVelocity.x) * rateGain * clampedDt
                    angularVelocity.z += (targetRollRate - angularVelocity.z) * rateGain * clampedDt
                    angularVelocity.y += (targetYawRate - angularVelocity.y) * rateGain * clampedDt
                }
                FlightMode.HORIZON -> {
                    val defMag = sqrt(pitchInput * pitchInput + rollInput * rollInput).coerceIn(0f, 1f)
                    if (defMag > 0.8f) {
                        // Blend to Acro
                        val targetRollRate = Math.toRadians(config.rollRate.calculateRate(rollInput).toDouble()).toFloat()
                        val targetPitchRate = Math.toRadians(config.pitchRate.calculateRate(pitchInput).toDouble()).toFloat()
                        val targetYawRate = Math.toRadians(config.yawRate.calculateRate(yawInput).toDouble()).toFloat()
                        angularVelocity.z += (targetRollRate - angularVelocity.z) * 35f * clampedDt
                        angularVelocity.x += (targetPitchRate - angularVelocity.x) * 35f * clampedDt
                        angularVelocity.y += (targetYawRate - angularVelocity.y) * 35f * clampedDt
                    } else {
                        // Angle leveling
                        val (curPitch, curRoll, _) = orientation.toEulerAngles()
                        val maxTiltRad = Math.toRadians(config.angleModeMaxTiltDeg.toDouble()).toFloat()
                        val targetPitchRate = (pitchInput * maxTiltRad - curPitch) * 8.0f
                        val targetRollRate = (rollInput * maxTiltRad - curRoll) * 8.0f
                        val targetYawRate = Math.toRadians(config.yawRate.calculateRate(yawInput).toDouble()).toFloat()
                        angularVelocity.x += (targetPitchRate - angularVelocity.x) * 30f * clampedDt
                        angularVelocity.z += (targetRollRate - angularVelocity.z) * 30f * clampedDt
                        angularVelocity.y += (targetYawRate - angularVelocity.y) * 30f * clampedDt
                    }
                }
            }
        } else {
            // Disarmed or crashed: angular velocities damp out quickly
            angularVelocity.x *= (1f - 5f * clampedDt)
            angularVelocity.y *= (1f - 5f * clampedDt)
            angularVelocity.z *= (1f - 5f * clampedDt)
        }

        // Apply angular damping
        angularVelocity.x *= (1f - config.angularDamping * 0.05f * clampedDt)
        angularVelocity.y *= (1f - config.angularDamping * 0.05f * clampedDt)
        angularVelocity.z *= (1f - config.angularDamping * 0.05f * clampedDt)

        // Integrate orientation quaternion
        orientation.integrateAngularVelocity(angularVelocity, clampedDt)

        // 3. Linear Forces and Acceleration
        // Body up vector in world space:
        val bodyUp = orientation.rotate(Vector3.UP)

        // Thrust vector
        val thrustMagnitude = actualThrottle * config.maxThrustNewtons
        val thrustForce = bodyUp * thrustMagnitude

        // Gravity vector
        val gravityForce = Vector3(0f, -config.massKg * gravity, 0f)

        // Ground effect cushion near ground surface (< 0.6m)
        val groundEffect = if (position.y < 0.6f && position.y > 0.05f && actualThrottle > 0.1f) {
            val factor = ((0.6f - position.y) / 0.6f).coerceIn(0f, 1f)
            Vector3(0f, thrustMagnitude * config.groundEffectStrength * factor, 0f)
        } else {
            Vector3.ZERO
        }

        // Aerodynamic Drag: quadratic in speed + linear damping
        val speed = velocity.length()
        val dragForce = if (speed > 0.001f) {
            val dragMag = 0.5f * 1.225f * config.airDragCoeff * speed * speed + 0.15f * speed
            velocity.normalized() * (-dragMag)
        } else {
            Vector3.ZERO
        }

        // Total force
        val totalForce = thrustForce + gravityForce + groundEffect + dragForce

        // a = F / m
        val acceleration = totalForce / config.massKg

        // Integrate velocity: v = v + a * dt
        velocity.x += acceleration.x * clampedDt
        velocity.y += acceleration.y * clampedDt
        velocity.z += acceleration.z * clampedDt

        // Integrate position: p = p + v * dt
        position.x += velocity.x * clampedDt
        position.y += velocity.y * clampedDt
        position.z += velocity.z * clampedDt

        // 4. Ground Collision & Landing physics
        if (position.y <= frameRadius) {
            val impactSpeed = velocity.length()
            val verticalSpeed = velocity.y

            // Check if quad is upright
            val isUpright = bodyUp.y > 0.70f // tilted less than ~45 deg

            if (verticalSpeed < -4.0f || (verticalSpeed < -2.0f && !isUpright)) {
                // Hard crash!
                if (!isCrashed && isArmed) {
                    isCrashed = true
                    crashReason = if (!isUpright) "LẬT DRONE TIẾP ĐẤT GÓC NGUY HIỂM!" else "TIẾP ĐẤT QUÁ MẠNH! TỐC ĐỘ: ${(impactSpeed * 3.6f).toInt()} KM/H"
                    isArmed = false
                }
                // Bounce with high damping
                velocity.y = -velocity.y * 0.20f
                velocity.x *= 0.5f
                velocity.z *= 0.5f
                angularVelocity.set(
                    (Math.random().toFloat() - 0.5f) * 10f,
                    (Math.random().toFloat() - 0.5f) * 10f,
                    (Math.random().toFloat() - 0.5f) * 10f
                )
            } else {
                // Gentle landing / resting on ground
                position.y = frameRadius
                if (velocity.y < 0f) velocity.y = 0f

                // Surface ground friction
                val friction = 8.0f * clampedDt
                velocity.x *= (1f - friction).coerceAtLeast(0f)
                velocity.z *= (1f - friction).coerceAtLeast(0f)

                // If disarmed, damp rotation to rest flat
                if (!isArmed) {
                    angularVelocity.set(0f, 0f, 0f)
                    val (_, _, yaw) = orientation.toEulerAngles()
                    orientation = Quaternion.fromEuler(0f, 0f, yaw)
                }
            }
        }

        // Update records
        val speedKmh = velocity.length() * 3.6f
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        if (position.y > maxAltitudeM) maxAltitudeM = position.y
    }

    /**
     * Get the FPV Camera view transformation parameters:
     * Eye position, LookAt target, Up vector.
     * Takes camera tilt angle into account!
     */
    fun getFPVCameraVectors(): Triple<Vector3, Vector3, Vector3> {
        val eye = position.copy()

        // Rotate camera forward vector with camera uptilt
        // In drone body frame, forward is (0, 0, 1), up is (0, 1, 0)
        // Tilting up by cameraTiltDeg means rotating around body X axis
        val tiltRad = Math.toRadians(config.cameraTiltDeg.toDouble()).toFloat()
        val cosT = cos(tiltRad)
        val sinT = sin(tiltRad)

        // Body camera forward vector: pointing forward and up
        val bodyCamForward = Vector3(0f, sinT, cosT)
        // Body camera up vector: perpendicular to camera forward
        val bodyCamUp = Vector3(0f, cosT, -sinT)

        // Transform into world space via drone quaternion
        val worldCamForward = orientation.rotate(bodyCamForward)
        val worldCamUp = orientation.rotate(bodyCamUp)

        val target = eye + worldCamForward

        return Triple(eye, target, worldCamUp)
    }

    /**
     * Get Third-person Chase Camera view vectors (to inspect drone and flight dynamics).
     */
    fun getChaseCameraVectors(): Triple<Vector3, Vector3, Vector3> {
        val bodyBack = Vector3(0f, 0.45f, -1.5f) // 1.5m behind, 0.45m above
        val worldBack = orientation.rotate(bodyBack)
        val eye = position + worldBack
        val target = position + orientation.rotate(Vector3(0f, 0.1f, 0.5f))
        val up = Vector3.UP
        return Triple(eye, target, up)
    }
}
