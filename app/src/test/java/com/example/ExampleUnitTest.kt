package com.example

import com.example.sim.physics.DroneConfig
import com.example.sim.physics.DronePhysics
import com.example.sim.physics.FlightMode
import com.example.sim.physics.Quaternion
import com.example.sim.physics.RateProfile
import com.example.sim.physics.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testVector3Operations() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)
        val sum = v1 + v2
        assertEquals(5f, sum.x, 0.001f)
        assertEquals(7f, sum.y, 0.001f)
        assertEquals(9f, sum.z, 0.001f)

        val dot = v1.dot(v2)
        assertEquals(32f, dot, 0.001f)
    }

    @Test
    fun testQuaternionRotation() {
        // Rotate Vector3.FORWARD by 90 degrees around Y axis
        val q = Quaternion.fromEuler(0f, 0f, Math.PI.toFloat() / 2f)
        val v = q.rotate(Vector3(0f, 0f, 1f))
        // Should point toward positive X
        assertEquals(1f, v.x, 0.01f)
        assertEquals(0f, v.y, 0.01f)
        assertEquals(0f, v.z, 0.01f)
    }

    @Test
    fun testBetaflightRateCalculation() {
        val rate = RateProfile(rcRate = 1.0f, superRate = 0.70f, rcExpo = 0.15f)
        // Center stick should produce 0 rate
        assertEquals(0f, rate.calculateRate(0f), 0.001f)

        // Max stick deflection (1.0) should give expected high rate (~666 deg/s)
        val maxRate = rate.calculateRate(1.0f)
        assertTrue("Max rate should be between 500 and 1000 deg/s", maxRate in 500f..1000f)

        // Negative deflection should be symmetric
        val negRate = rate.calculateRate(-1.0f)
        assertEquals(-maxRate, negRate, 0.01f)
    }

    @Test
    fun testDronePhysicsThrustAndHover() {
        val physics = DronePhysics(DroneConfig.freestyle5Inch())
        physics.reset()
        physics.isArmed = true

        // Simulate 0.5s of flight with high throttle (1.0)
        for (i in 0 until 50) {
            physics.update(
                dt = 0.01f,
                throttleInput = 1.0f,
                yawInput = 0f,
                pitchInput = 0f,
                rollInput = 0f
            )
        }

        // Drone should gain altitude and positive vertical velocity
        assertTrue("Drone should gain altitude on full throttle", physics.position.y > 0.15f)
        assertTrue("Vertical velocity should be positive", physics.velocity.y > 0f)
        assertFalse("Drone should not be crashed", physics.isCrashed)
    }

    @Test
    fun testAngleModeStabilization() {
        val config = DroneConfig.freestyle5Inch().copy(flightMode = FlightMode.ANGLE)
        val physics = DronePhysics(config)
        physics.reset()
        physics.isArmed = true

        // Pitch forward input
        physics.update(
            dt = 0.02f,
            throttleInput = 0.5f,
            yawInput = 0f,
            pitchInput = 0.5f,
            rollInput = 0f
        )

        // Should tilt pitch forward
        val (pitch, _, _) = physics.orientation.toEulerAngles()
        // Pitch angle should be positive
        assertTrue("Pitch angle should change with input", pitch != 0f)
    }
}
