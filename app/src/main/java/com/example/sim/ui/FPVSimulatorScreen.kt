package com.example.sim.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.sim.physics.DroneConfig
import com.example.sim.physics.DronePhysics
import com.example.sim.physics.FlightMode
import com.example.sim.renderer.FPVGLRenderer
import com.example.sim.renderer.RacingGate
import kotlinx.coroutines.isActive

@Composable
fun FPVSimulatorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Physics Engine instance
    val physics = remember { DronePhysics(DroneConfig.freestyle5Inch()) }

    // Renderer instance
    val renderer = remember { FPVGLRenderer(context, physics) }

    // Controls state
    var throttleInput by remember { mutableFloatStateOf(0f) }
    var yawInput by remember { mutableFloatStateOf(0f) }
    var pitchInput by remember { mutableFloatStateOf(0f) }
    var rollInput by remember { mutableFloatStateOf(0f) }

    var frictionThrottle by remember { mutableStateOf(true) }
    var isThirdPersonCam by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Lap / Gate tracker state
    var currentGateIndex by remember { mutableIntStateOf(0) }
    var gatesClearedCount by remember { mutableIntStateOf(0) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    // Telemetry tick counter to trigger recomposition of OSD
    var telemetryTick by remember { mutableIntStateOf(0) }

    // Vibrator helper
    fun triggerHaptic(durationMs: Long, isSevere: Boolean = false) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vib = vm?.defaultVibrator
                vib?.vibrate(
                    VibrationEffect.createOneShot(
                        durationMs,
                        if (isSevere) VibrationEffect.DEFAULT_AMPLITUDE else 120
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib?.vibrate(
                        VibrationEffect.createOneShot(
                            durationMs,
                            if (isSevere) VibrationEffect.DEFAULT_AMPLITUDE else 120
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vib?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    // Gate crossing callback
    DisposableEffect(Unit) {
        renderer.onGatePassed = { gate ->
            gatesClearedCount++
            triggerHaptic(80)
            if (gate.isFinishLine) {
                bannerMessage = "HOÀN THÀNH VÒNG ĐUA! THỜI GIAN: ${String.format("%.1f", physics.flightTimeSeconds)}s"
                renderer.resetGates()
                currentGateIndex = 0
            } else {
                bannerMessage = "ĐÃ QUA CỔNG ${gate.id} / ${renderer.gates.size}"
                currentGateIndex = (currentGateIndex + 1).coerceAtMost(renderer.gates.size - 1)
            }
        }
        onDispose {
            renderer.onGatePassed = null
        }
    }

    // High frequency physics loop (fixed 100Hz = 10ms step for simulation stability)
    LaunchedEffect(Unit) {
        var lastTimeNano = System.nanoTime()
        val fixedDt = 0.010f // 10ms = 100Hz
        var lastCrashState = false

        while (isActive) {
            val now = System.nanoTime()
            val elapsedSec = ((now - lastTimeNano) / 1_000_000_000f).coerceIn(0.005f, 0.05f)
            lastTimeNano = now

            // Step physics
            physics.update(
                dt = fixedDt,
                throttleInput = throttleInput,
                yawInput = yawInput,
                pitchInput = pitchInput,
                rollInput = rollInput
            )

            // Crash detection haptic
            if (physics.isCrashed && !lastCrashState) {
                triggerHaptic(300, isSevere = true)
            }
            lastCrashState = physics.isCrashed

            // Refresh OSD at ~30Hz
            telemetryTick++

            kotlinx.coroutines.delay(10) // 100Hz step
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. OpenGL 3D Viewport
        AndroidView(
            modifier = Modifier.fillMaxSize().testTag("gl_surface_view"),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            update = { glView ->
                renderer.isThirdPersonCam = isThirdPersonCam
            }
        )

        // 2. Betaflight OSD Heads-Up Display
        // Reads from telemetryTick to update smoothly
        if (telemetryTick >= 0) {
            val currentGate = renderer.gates.getOrNull(currentGateIndex)
            OSDOverlay(
                physics = physics,
                currentGate = currentGate,
                totalGates = renderer.gates.size,
                isThirdPerson = isThirdPersonCam
            )
        }

        // 3. Top Quick Control Action Bar (Floating HUD Pills)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 42.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action: ARM / DISARM Switch
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (physics.isArmed) Color(0xCC059669) else Color(0xCCDC2626)
                    )
                    .clickable {
                        physics.isArmed = !physics.isArmed
                        triggerHaptic(50)
                        if (physics.isArmed && physics.isCrashed) {
                            physics.reset()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("arm_switch_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "ARM Switch",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (physics.isArmed) "ARMED (KHÓA GA)" else "DISARM (CHẠM ĐỂ MỞ)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Center Actions: Mode Toggle & View Switch
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ACRO / ANGLE quick toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC1E293B))
                        .clickable {
                            val nextMode = if (physics.config.flightMode == FlightMode.ACRO) FlightMode.ANGLE else FlightMode.ACRO
                            physics.config = physics.config.copy(flightMode = nextMode)
                            triggerHaptic(30)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("flight_mode_toggle_button")
                ) {
                    Text(
                        text = "CHẾ ĐỘ: ${physics.config.flightMode.name}",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Camera FPV vs 3rd Person Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC1E293B))
                        .clickable {
                            isThirdPersonCam = !isThirdPersonCam
                            renderer.isThirdPersonCam = isThirdPersonCam
                            triggerHaptic(30)
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("camera_view_toggle_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Camera View",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isThirdPersonCam) "3RD PERSON" else "FPV CAM",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Right Actions: RESET, TURTLE & SETTINGS
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Turtle flip if crashed
                if (physics.isCrashed || physics.position.y <= 0.25f) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xCCF59E0B))
                            .clickable {
                                physics.turtleModeFlip()
                                triggerHaptic(80)
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("turtle_mode_button")
                    ) {
                        Text(
                            text = "LẬT TURTLE",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Reset Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xCC334155))
                        .clickable {
                            physics.reset()
                            renderer.resetGates()
                            currentGateIndex = 0
                            gatesClearedCount = 0
                            bannerMessage = null
                            triggerHaptic(50)
                        }
                        .padding(8.dp)
                        .testTag("reset_drone_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Đặt lại Drone",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Settings Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xCC334155))
                        .clickable { showSettings = true }
                        .padding(8.dp)
                        .testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Cài đặt Rates & FPV",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 4. Gate / Lap Announcement Banner
        bannerMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xDD0F172A))
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = msg,
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // 5. Dual Virtual RC Thumbsticks (Mode 2)
        // Placed at lower left and lower right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // LEFT STICK: Throttle (Vertical) + Yaw (Horizontal)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "THROTTLE / YAW (Mode 2)",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                VirtualStick(
                    isThrottleYaw = true,
                    frictionThrottle = frictionThrottle,
                    stickSize = 148.dp,
                    testTag = "left_stick_throttle_yaw",
                    onValuesChanged = { hYaw, vThrottle ->
                        yawInput = hYaw
                        throttleInput = vThrottle
                    }
                )
            }

            // RIGHT STICK: Pitch (Vertical) + Roll (Horizontal)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PITCH / ROLL",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                VirtualStick(
                    isThrottleYaw = false,
                    stickSize = 148.dp,
                    testTag = "right_stick_pitch_roll",
                    onValuesChanged = { hRoll, vPitch ->
                        rollInput = hRoll
                        pitchInput = vPitch
                    }
                )
            }
        }

        // 6. Settings Dialog
        if (showSettings) {
            SettingsDialog(
                config = physics.config,
                frictionThrottle = frictionThrottle,
                onFrictionThrottleChange = { frictionThrottle = it },
                onConfigUpdated = { newConfig ->
                    physics.config = newConfig
                },
                onDismiss = { showSettings = false }
            )
        }
    }
}
