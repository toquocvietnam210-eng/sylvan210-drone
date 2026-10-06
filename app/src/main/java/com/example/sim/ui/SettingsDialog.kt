package com.example.sim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.sim.physics.DroneConfig
import com.example.sim.physics.FlightMode
import com.example.sim.physics.RateProfile

@Composable
fun SettingsDialog(
    config: DroneConfig,
    frictionThrottle: Boolean,
    onFrictionThrottleChange: (Boolean) -> Unit,
    onConfigUpdated: (DroneConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Chế độ bay", "Rates (Góc quay)", "Vật lý FPV", "Tay điều khiển")

    var currentConfig by remember { mutableStateOf(config.copy()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("settings_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Dialog Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cài đặt Drone FPV",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "✕",
                        color = Color(0xFF94A3B8),
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(4.dp)
                            .testTag("close_settings_button")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1E293B),
                    contentColor = Color(0xFF00E5FF),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF00E5FF)
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (selectedTab) {
                    // TAB 0: Flight Mode
                    0 -> {
                        Text(
                            text = "Chế độ bay (Flight Mode):",
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlightMode.values().forEach { mode ->
                            val isSelected = currentConfig.flightMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        color = if (isSelected) Color(0xFF1E3A5F) else Color(0xFF1E293B),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        currentConfig = currentConfig.copy(flightMode = mode)
                                        onConfigUpdated(currentConfig)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "● " else "○ ",
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF64748B),
                                    fontSize = 16.sp
                                )
                                Column {
                                    Text(
                                        text = mode.name,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = mode.description,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        if (currentConfig.flightMode == FlightMode.ANGLE) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Góc nghiêng tối đa (Angle Tilt): ${currentConfig.angleModeMaxTiltDeg.toInt()}°",
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp
                            )
                            Slider(
                                value = currentConfig.angleModeMaxTiltDeg,
                                onValueChange = {
                                    currentConfig = currentConfig.copy(angleModeMaxTiltDeg = it)
                                    onConfigUpdated(currentConfig)
                                },
                                valueRange = 25f..65f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00E5FF),
                                    activeTrackColor = Color(0xFF00E5FF)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Góc nghiêng Camera FPV (Uptilt): ${currentConfig.cameraTiltDeg.toInt()}°",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                        Slider(
                            value = currentConfig.cameraTiltDeg,
                            onValueChange = {
                                currentConfig = currentConfig.copy(cameraTiltDeg = it)
                                onConfigUpdated(currentConfig)
                            },
                            valueRange = 0f..50f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF9100),
                                activeTrackColor = Color(0xFFFF9100)
                            )
                        )
                        Text(
                            text = "Góc uptilt cao (30°-45°) phù hợp bay tốc độ cao. Góc thấp (10°-20°) phù hợp người mới.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }

                    // TAB 1: Betaflight Rates
                    1 -> {
                        Text(
                            text = "Cài đặt Betaflight Rates:",
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    val preset = DroneConfig.freestyle5Inch()
                                    currentConfig = currentConfig.copy(
                                        rollRate = preset.rollRate,
                                        pitchRate = preset.pitchRate,
                                        yawRate = preset.yawRate
                                    )
                                    onConfigUpdated(currentConfig)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("5\" Freestyle", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                            Button(
                                onClick = {
                                    val preset = DroneConfig.beginnerTrainer()
                                    currentConfig = currentConfig.copy(
                                        rollRate = preset.rollRate,
                                        pitchRate = preset.pitchRate,
                                        yawRate = preset.yawRate
                                    )
                                    onConfigUpdated(currentConfig)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Người mới", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                            Button(
                                onClick = {
                                    val preset = DroneConfig.racingBeast()
                                    currentConfig = currentConfig.copy(
                                        rollRate = preset.rollRate,
                                        pitchRate = preset.pitchRate,
                                        yawRate = preset.yawRate
                                    )
                                    onConfigUpdated(currentConfig)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Racing Beast", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Roll / Pitch Rate Tuning
                        RateSliderSection(
                            title = "Roll & Pitch Rates (Góc quay Roll/Pitch)",
                            profile = currentConfig.rollRate,
                            onUpdate = { newProfile ->
                                currentConfig = currentConfig.copy(
                                    rollRate = newProfile,
                                    pitchRate = newProfile
                                )
                                onConfigUpdated(currentConfig)
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Yaw Rate Tuning
                        RateSliderSection(
                            title = "Yaw Rate (Góc xoay Yaw)",
                            profile = currentConfig.yawRate,
                            onUpdate = { newProfile ->
                                currentConfig = currentConfig.copy(yawRate = newProfile)
                                onConfigUpdated(currentConfig)
                            }
                        )
                    }

                    // TAB 2: FPV Physics
                    2 -> {
                        Text(
                            text = "Mô phỏng Động lực học & Vật lý Quadcopter:",
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // TWR Slider
                        Text(
                            text = "Tỷ số Lực đẩy / Trọng lượng (TWR): ${String.format("%.1f", currentConfig.thrustToWeightRatio)}:1",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                        Slider(
                            value = currentConfig.thrustToWeightRatio,
                            onValueChange = {
                                currentConfig = currentConfig.copy(thrustToWeightRatio = it)
                                onConfigUpdated(currentConfig)
                            },
                            valueRange = 2.5f..6.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF)
                            )
                        )
                        Text(
                            text = "TWR 4.5:1 - 5.5:1 mang lại lực vọt (Punch-out) đặc trưng của drone 5 inch 6S.",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Drone Mass Slider
                        Text(
                            text = "Trọng lượng Drone (Mass): ${(currentConfig.massKg * 1000).toInt()}g",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                        Slider(
                            value = currentConfig.massKg,
                            onValueChange = {
                                currentConfig = currentConfig.copy(massKg = it)
                                onConfigUpdated(currentConfig)
                            },
                            valueRange = 0.35f..0.85f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF38BDF8)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Motor Spool-Up Latency
                        Text(
                            text = "Độ trễ quay Motor (Spool latency): ${currentConfig.motorResponseTimeMs.toInt()} ms",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp
                        )
                        Slider(
                            value = currentConfig.motorResponseTimeMs,
                            onValueChange = {
                                currentConfig = currentConfig.copy(motorResponseTimeMs = it)
                                onConfigUpdated(currentConfig)
                            },
                            valueRange = 20f..80f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF38BDF8)
                            )
                        )
                    }

                    // TAB 3: Controller / Transmitter
                    3 -> {
                        Text(
                            text = "Thiết lập Tay điều khiển RC (Mode 2):",
                            color = Color(0xFFE2E8F0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ga ma sát (RC Friction Throttle)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Khi nhấc ngón tay, cần ga giữ nguyên vị trí như tay điều khiển RC chuyên nghiệp (Radiomaster, TBS, FrSky).",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = frictionThrottle,
                                onCheckedChange = onFrictionThrottleChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00E5FF),
                                    checkedTrackColor = Color(0xFF0284C7)
                                ),
                                modifier = Modifier.testTag("friction_throttle_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Sơ đồ phím Mode 2 chuẩn quốc tế:",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Cần TRÁI: Ga (Throttle - Lên/Xuống) & Xoay thân (Yaw - Trái/Phải)\n• Cần PHẢI: Chúi/Ngửa (Pitch - Lên/Xuống) & Nghiêng (Roll - Trái/Phải)",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_settings_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text("ÁP DỤNG & TIẾP TỤC BAY", color = Color(0xFF0A0F1D), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RateSliderSection(
    title: String,
    profile: RateProfile,
    onUpdate: (RateProfile) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Tối đa: ${profile.maxRateDegPerSec.toInt()}°/s",
                color = Color(0xFF00E5FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "RC Rate: ${String.format("%.2f", profile.rcRate)}",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
        )
        Slider(
            value = profile.rcRate,
            onValueChange = { onUpdate(profile.copy(rcRate = it)) },
            valueRange = 0.5f..2.2f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF38BDF8)
            )
        )

        Text(
            text = "Super Rate: ${String.format("%.2f", profile.superRate)}",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
        )
        Slider(
            value = profile.superRate,
            onValueChange = { onUpdate(profile.copy(superRate = it)) },
            valueRange = 0.0f..0.88f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF38BDF8)
            )
        )

        Text(
            text = "RC Expo: ${String.format("%.2f", profile.rcExpo)}",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
        )
        Slider(
            value = profile.rcExpo,
            onValueChange = { onUpdate(profile.copy(rcExpo = it)) },
            valueRange = 0.0f..0.50f,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF38BDF8),
                activeTrackColor = Color(0xFF38BDF8)
            )
        )
    }
}
