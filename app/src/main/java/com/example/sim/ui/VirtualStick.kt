package com.example.sim.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High precision virtual RC joystick for FPV flight control.
 * @param isThrottleYaw if true: Left stick (vertical is 0..1 throttle, horizontal is -1..1 yaw)
 *                      if false: Right stick (vertical is -1..1 pitch, horizontal is -1..1 roll)
 * @param frictionThrottle if true: throttle stick maintains its vertical position when finger is lifted
 */
@Composable
fun VirtualStick(
    isThrottleYaw: Boolean,
    frictionThrottle: Boolean = true,
    stickSize: Dp = 150.dp,
    label: String = "",
    testTag: String = "virtual_stick",
    onValuesChanged: (horizontal: Float, vertical: Float) -> Unit
) {
    // Stick thumb position normalized [-1..1]
    var normX by remember { mutableFloatStateOf(0f) }
    var normY by remember { mutableFloatStateOf(if (isThrottleYaw) -1f else 0f) } // -1 is zero throttle at bottom

    var isTouching by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(stickSize)
            .clip(CircleShape)
            .background(Color(0x770D1526))
            .testTag(testTag)
            .pointerInput(isThrottleYaw, frictionThrottle) {
                val radius = (stickSize.toPx() / 2f) * 0.75f

                detectDragGestures(
                    onDragStart = { offset ->
                        isTouching = true
                        val center = Offset(stickSize.toPx() / 2f, stickSize.toPx() / 2f)
                        val dx = offset.x - center.x
                        val dy = offset.y - center.y
                        val dist = sqrt(dx * dx + dy * dy)
                        val clampedDist = min(dist, radius)
                        val angle = atan2(dy, dx)

                        normX = (clampedDist * cos(angle) / radius).coerceIn(-1f, 1f)
                        // In screen coords, negative dy is UP (forward/throttle up)
                        val rawY = -(clampedDist * sin(angle) / radius).coerceIn(-1f, 1f)
                        normY = rawY

                        val vertVal = if (isThrottleYaw) {
                            // Map normY [-1..1] to throttle [0..1]
                            ((normY + 1f) / 2f).coerceIn(0f, 1f)
                        } else {
                            // Pitch: forward (up on screen) is positive pitch input
                            normY
                        }
                        onValuesChanged(normX, vertVal)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val maxPx = radius
                        val newPxX = (normX * maxPx) + dragAmount.x
                        val newPxY = (-normY * maxPx) + dragAmount.y

                        val dist = sqrt(newPxX * newPxX + newPxY * newPxY)
                        val clampedDist = min(dist, maxPx)
                        val angle = atan2(newPxY, newPxX)

                        normX = (clampedDist * cos(angle) / maxPx).coerceIn(-1f, 1f)
                        normY = -(clampedDist * sin(angle) / maxPx).coerceIn(-1f, 1f)

                        val vertVal = if (isThrottleYaw) {
                            ((normY + 1f) / 2f).coerceIn(0f, 1f)
                        } else {
                            normY
                        }
                        onValuesChanged(normX, vertVal)
                    },
                    onDragEnd = {
                        isTouching = false
                        // Horizontal (Yaw or Roll) always spring-centers to 0
                        normX = 0f
                        if (isThrottleYaw) {
                            if (!frictionThrottle) {
                                // Spring to bottom (zero throttle)
                                normY = -1f
                            }
                            // If frictionThrottle == true, throttle retains its position!
                        } else {
                            // Pitch spring-centers to 0
                            normY = 0f
                        }

                        val vertVal = if (isThrottleYaw) {
                            ((normY + 1f) / 2f).coerceIn(0f, 1f)
                        } else {
                            normY
                        }
                        onValuesChanged(normX, vertVal)
                    },
                    onDragCancel = {
                        isTouching = false
                        normX = 0f
                        if (!isThrottleYaw || !frictionThrottle) {
                            normY = if (isThrottleYaw) -1f else 0f
                        }
                        val vertVal = if (isThrottleYaw) {
                            ((normY + 1f) / 2f).coerceIn(0f, 1f)
                        } else {
                            normY
                        }
                        onValuesChanged(normX, vertVal)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(stickSize)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.width / 2f
            val travelRadius = outerRadius * 0.65f
            val knobRadius = outerRadius * 0.28f

            // 1. Gimbal Outer Bezel
            drawCircle(
                color = Color(0xFF1E293B),
                radius = outerRadius - 2f,
                style = Stroke(width = 3f)
            )

            // Inner graduation circle
            drawCircle(
                color = Color(0x44475569),
                radius = travelRadius,
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            )

            // Crosshair lines
            drawLine(
                color = Color(0x4464748B),
                start = Offset(center.x - travelRadius, center.y),
                end = Offset(center.x + travelRadius, center.y),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color(0x4464748B),
                start = Offset(center.x, center.y - travelRadius),
                end = Offset(center.x, center.y + travelRadius),
                strokeWidth = 1.5f
            )

            // 2. Thumbstick Knob Position
            val knobCenter = Offset(
                center.x + normX * travelRadius,
                center.y - normY * travelRadius
            )

            // Knob Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (isTouching) Color(0xAA00E5FF) else Color(0x4400E5FF),
                        Color.Transparent
                    ),
                    center = knobCenter,
                    radius = knobRadius * 1.5f
                ),
                radius = knobRadius * 1.5f,
                center = knobCenter
            )

            // Knob metallic body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF334155),
                        Color(0xFF0F172A)
                    ),
                    center = knobCenter,
                    radius = knobRadius
                ),
                radius = knobRadius,
                center = knobCenter
            )

            // Knob rim border
            drawCircle(
                color = if (isTouching) Color(0xFF00E5FF) else Color(0xFF38BDF8),
                radius = knobRadius,
                center = knobCenter,
                style = Stroke(width = 2.5f)
            )

            // Grip knurling dots
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = knobRadius * 0.25f,
                center = knobCenter
            )
        }
    }
}
