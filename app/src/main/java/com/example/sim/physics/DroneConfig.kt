package com.example.sim.physics

import kotlin.math.abs
import kotlin.math.pow

enum class FlightMode(val label: String, val description: String) {
    ACRO("ACRO", "Điều khiển góc quay tự do (Rate Mode) - cho Freestyle & Racing"),
    ANGLE("ANGLE", "Tự động cân bằng thăng bằng (Self-level) - dễ bay cho người mới"),
    HORIZON("HORIZON", "Cân bằng ở góc nhỏ, lộn nhào ở gạt cần tối đa")
}

data class RateProfile(
    val rcRate: Float = 1.0f,
    val superRate: Float = 0.70f,
    val rcExpo: Float = 0.15f
) {
    /**
     * Betaflight standard rate calculation.
     * deflection in range [-1.0 .. 1.0]
     * returns angular rate in degrees per second.
     */
    fun calculateRate(deflection: Float): Float {
        val sign = if (deflection < 0) -1f else 1f
        val absDef = abs(deflection).coerceIn(0f, 1f)

        // Apply Betaflight cubic expo
        val expDef = absDef * (1f - rcExpo) + absDef.pow(3) * rcExpo

        // Base rate
        val baseRate = 200f * rcRate * expDef

        // Super rate denominator curve
        val superFactor = 1f - (absDef * superRate.coerceIn(0f, 0.99f))
        val rateDegPerSec = if (superFactor > 0.01f) {
            baseRate / superFactor
        } else {
            baseRate / 0.01f
        }

        return sign * rateDegPerSec
    }

    /**
     * Maximum rate achievable at full stick deflection (1.0).
     */
    val maxRateDegPerSec: Float
        get() = calculateRate(1.0f)
}

data class DroneConfig(
    // Physical specs
    var massKg: Float = 0.65f,             // 650g typical 5" quad with 6S LiPo
    var thrustToWeightRatio: Float = 4.8f, // TWR: 4.8 to 1 (aggressive freestyle)
    var motorResponseTimeMs: Float = 40f,  // Motor spool-up latency (ms)
    var airDragCoeff: Float = 0.18f,       // Parasitic drag coefficient
    var angularDamping: Float = 6.0f,      // Rotational air drag damping
    var groundEffectStrength: Float = 0.25f, // Cushion lift near ground

    // FPV Camera setup
    var cameraTiltDeg: Float = 25f,        // Camera uptilt angle (0 to 50 deg)
    var cameraFovDeg: Float = 105f,        // Wide angle FPV lens FOV

    // Flight mode & control
    var flightMode: FlightMode = FlightMode.ACRO,
    var angleModeMaxTiltDeg: Float = 50f,  // Max pitch/roll angle in ANGLE mode
    var throttleExpo: Float = 0.2f,        // Throttle curve expo
    var throttleMid: Float = 0.35f,        // Hover throttle point
    var idleThrottlePercent: Float = 5f,   // Air mode idle motor spin

    // Betaflight Rates
    var rollRate: RateProfile = RateProfile(rcRate = 1.0f, superRate = 0.70f, rcExpo = 0.15f),
    var pitchRate: RateProfile = RateProfile(rcRate = 1.0f, superRate = 0.70f, rcExpo = 0.15f),
    var yawRate: RateProfile = RateProfile(rcRate = 0.95f, superRate = 0.65f, rcExpo = 0.10f),

    // Battery simulation
    var cellCount: Int = 6,                // 6S LiPo (25.2V max, 22.2V nominal, 21.0V cutoff)
    var batteryCapacityMah: Float = 1300f
) {
    val maxThrustNewtons: Float
        get() = massKg * 9.81f * thrustToWeightRatio

    companion object {
        fun freestyle5Inch(): DroneConfig = DroneConfig(
            massKg = 0.65f,
            thrustToWeightRatio = 5.2f,
            motorResponseTimeMs = 35f,
            cameraTiltDeg = 28f,
            flightMode = FlightMode.ACRO,
            rollRate = RateProfile(rcRate = 1.0f, superRate = 0.72f, rcExpo = 0.18f),
            pitchRate = RateProfile(rcRate = 1.0f, superRate = 0.72f, rcExpo = 0.18f),
            yawRate = RateProfile(rcRate = 0.95f, superRate = 0.65f, rcExpo = 0.12f)
        )

        fun beginnerTrainer(): DroneConfig = DroneConfig(
            massKg = 0.55f,
            thrustToWeightRatio = 3.2f,
            motorResponseTimeMs = 60f,
            cameraTiltDeg = 15f,
            flightMode = FlightMode.ANGLE,
            angleModeMaxTiltDeg = 40f,
            rollRate = RateProfile(rcRate = 0.8f, superRate = 0.50f, rcExpo = 0.25f),
            pitchRate = RateProfile(rcRate = 0.8f, superRate = 0.50f, rcExpo = 0.25f),
            yawRate = RateProfile(rcRate = 0.8f, superRate = 0.50f, rcExpo = 0.20f)
        )

        fun cinewhoop3Inch(): DroneConfig = DroneConfig(
            massKg = 0.40f,
            thrustToWeightRatio = 3.6f,
            motorResponseTimeMs = 50f,
            cameraTiltDeg = 12f,
            flightMode = FlightMode.ANGLE,
            angleModeMaxTiltDeg = 45f,
            rollRate = RateProfile(rcRate = 0.85f, superRate = 0.55f, rcExpo = 0.30f),
            pitchRate = RateProfile(rcRate = 0.85f, superRate = 0.55f, rcExpo = 0.30f),
            yawRate = RateProfile(rcRate = 0.85f, superRate = 0.55f, rcExpo = 0.25f)
        )

        fun racingBeast(): DroneConfig = DroneConfig(
            massKg = 0.60f,
            thrustToWeightRatio = 6.2f,
            motorResponseTimeMs = 28f,
            cameraTiltDeg = 45f,
            flightMode = FlightMode.ACRO,
            rollRate = RateProfile(rcRate = 1.15f, superRate = 0.78f, rcExpo = 0.10f),
            pitchRate = RateProfile(rcRate = 1.15f, superRate = 0.78f, rcExpo = 0.10f),
            yawRate = RateProfile(rcRate = 1.05f, superRate = 0.70f, rcExpo = 0.10f)
        )
    }
}
