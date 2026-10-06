package com.example.sim.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sim.physics.DronePhysics
import com.example.sim.renderer.RacingGate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Betaflight OSD High-Contrast Green
val OsdGreen = Color(0xFF33FF33)
val OsdShadow = Color(0xDD000000)
val OsdRed = Color(0xFFFF3333)
val OsdYellow = Color(0xFFFFD700)
val OsdCyan = Color(0xFF00E5FF)

@Composable
fun OSDOverlay(
    physics: DronePhysics,
    currentGate: RacingGate?,
    totalGates: Int,
    isThirdPerson: Boolean,
    modifier: Modifier = Modifier
) {
    val (pitchRad, rollRad, yawRad) = physics.orientation.toEulerAngles()
    val pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat()
    val rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat()
    val yawDeg = ((Math.toDegrees(yawRad.toDouble()).toFloat() % 360f) + 360f) % 360f

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Center Artificial Horizon Ladder & Crosshairs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // Aircraft Center Crosshair [- - + - -]
            val crosshairSpan = 40f
            // Left wing
            drawLine(
                color = OsdGreen,
                start = Offset(center.x - crosshairSpan - 20f, center.y),
                end = Offset(center.x - 20f, center.y),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = OsdGreen,
                start = Offset(center.x - 20f, center.y),
                end = Offset(center.x - 20f, center.y + 12f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )

            // Right wing
            drawLine(
                color = OsdGreen,
                start = Offset(center.x + 20f, center.y),
                end = Offset(center.x + crosshairSpan + 20f, center.y),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = OsdGreen,
                start = Offset(center.x + 20f, center.y),
                end = Offset(center.x + 20f, center.y + 12f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )

            // Center pip
            drawCircle(
                color = OsdGreen,
                radius = 3.5f,
                center = center
            )

            // Rotating Artificial Horizon Bar
            // Roll rotates in opposite direction to show the true horizon
            rotate(degrees = -rollDeg, pivot = center) {
                // Shift horizon line by pitch: 1 degree approx 5 pixels
                val pitchPixelShift = (pitchDeg * 4.5f).coerceIn(-size.height * 0.4f, size.height * 0.4f)
                val horizonY = center.y + pitchPixelShift

                // Horizon level line
                drawLine(
                    color = Color(0x8833FF33),
                    start = Offset(center.x - 70f, horizonY),
                    end = Offset(center.x + 70f, horizonY),
                    strokeWidth = 2.5f
                )

                // +15 deg pitch line
                val pitch15Y = horizonY - (15f * 4.5f)
                drawLine(
                    color = Color(0x5533FF33),
                    start = Offset(center.x - 40f, pitch15Y),
                    end = Offset(center.x + 40f, pitch15Y),
                    strokeWidth = 1.8f
                )

                // -15 deg pitch line
                val pitchNeg15Y = horizonY + (15f * 4.5f)
                drawLine(
                    color = Color(0x5533FF33),
                    start = Offset(center.x - 40f, pitchNeg15Y),
                    end = Offset(center.x + 40f, pitchNeg15Y),
                    strokeWidth = 1.8f
                )
            }
        }

        // 2. Top Bar Telemetry
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left Top: Battery & Current
            Column {
                val cellAvg = physics.batteryVoltage / physics.config.cellCount
                val batColor = if (cellAvg < 3.55f) OsdRed else if (cellAvg < 3.75f) OsdYellow else OsdGreen

                Text(
                    text = "${String.format("%.1f", physics.batteryVoltage)}V  ${physics.config.cellCount}S",
                    color = batColor,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${String.format("%.1f", physics.currentAmps)}A   ${physics.mahConsumed.toInt()}mAh",
                    color = OsdGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Center Top: Flight Mode & Status
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Arm state badge
                    val armText = if (physics.isArmed) "ARMED" else "DISARMED"
                    val armColor = if (physics.isArmed) OsdGreen else OsdRed

                    Box(
                        modifier = Modifier
                            .background(
                                color = armColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = armText,
                            color = armColor,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Flight Mode badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = OsdCyan.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = physics.config.flightMode.name,
                            color = OsdCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isThirdPerson) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "[3RD PERSON]",
                            color = OsdYellow,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Flight Timer
                val mins = (physics.flightTimeSeconds / 60).toInt()
                val secs = (physics.flightTimeSeconds % 60).toInt()
                val tenths = ((physics.flightTimeSeconds * 10) % 10).toInt()
                Text(
                    text = "TIME ${String.format("%02d:%02d.%d", mins, secs, tenths)}",
                    color = OsdGreen,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }

            // Right Top: Radio & Video link
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "LQ: 99% -42dBm",
                    color = OsdGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "CAM: ${physics.config.cameraTiltDeg.toInt()}° TILT",
                    color = OsdGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 3. Middle Left: Altitude & Throttle
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            Text(
                text = "ALT: ${String.format("%.1f", physics.position.y)} M",
                color = OsdGreen,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            val vertSpeed = physics.velocity.y
            val sign = if (vertSpeed >= 0) "+" else ""
            Text(
                text = "V-SPD: $sign${String.format("%.1f", vertSpeed)} M/S",
                color = if (vertSpeed < -3.0f) OsdYellow else OsdGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(12.dp))
            val throttlePercent = (physics.actualThrottle * 100f).toInt()
            Text(
                text = "THR: $throttlePercent%",
                color = OsdGreen,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // 4. Middle Right: Speed & Heading
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp),
            horizontalAlignment = Alignment.End
        ) {
            val speedKmh = (physics.velocity.length() * 3.6f).toInt()
            Text(
                text = "SPD: $speedKmh KM/H",
                color = OsdGreen,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "MAX: ${physics.maxSpeedKmh.toInt()} KM/H",
                color = OsdGreen.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "HDG: ${yawDeg.toInt()}°",
                color = OsdGreen,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // 5. Gate / Race Checkpoint tracker
        currentGate?.let { gate ->
            val dist = (gate.position - physics.position).length()
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 54.dp)
                    .background(Color(0x99000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "CỔNG ${gate.id} / $totalGates   KHOẢNG CÁCH: ${dist.toInt()} M",
                    color = if (gate.isFinishLine) OsdYellow else OsdCyan,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 6. Crash warning notification banner
        if (physics.isCrashed) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(blinkAlpha)
                    .background(Color(0xEE880000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠ DRONE ĐÃ VA CHẠM / RƠI!",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = physics.crashReason,
                        color = OsdYellow,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nhấn 'ĐẶT LẠI' hoặc 'LẬT TURTLE' để tiếp tục bay",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
