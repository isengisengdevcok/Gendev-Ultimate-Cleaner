package com.example.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager as AndroidBatteryManager
import com.example.model.BatteryStatusInfo
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BatteryManager(
    private val context: Context,
    private val rootManager: RootManager
) {
    suspend fun readBatteryStatus(): BatteryStatusInfo = withContext(Dispatchers.IO) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(AndroidBatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(AndroidBatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else 0

        var tempTenths = intent?.getIntExtra(AndroidBatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        if (tempTenths <= 0) {
            val sysTemp = rootManager.execute(
                command = "cat /sys/class/power_supply/battery/temp 2>/dev/null",
                userApproved = true,
                timeoutMs = 3000L,
                logCommand = false,
                logStderrAsError = false
            )
            tempTenths = sysTemp.stdout.firstOrNull()?.trim()?.toIntOrNull() ?: 0
        }

        val voltage = intent?.getIntExtra(AndroidBatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val statusCode = intent?.getIntExtra(AndroidBatteryManager.EXTRA_STATUS, -1) ?: -1
        val healthCode = intent?.getIntExtra(AndroidBatteryManager.EXTRA_HEALTH, -1) ?: -1

        val statusLabel = when (statusCode) {
            AndroidBatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            AndroidBatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            AndroidBatteryManager.BATTERY_STATUS_FULL -> "Full"
            AndroidBatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }

        val healthLabel = when (healthCode) {
            AndroidBatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            AndroidBatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            AndroidBatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            AndroidBatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            AndroidBatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Good"
        }

        BatteryStatusInfo(
            levelPercent = percent.coerceIn(0, 100),
            temperatureCelsius = tempTenths / 10f,
            voltageMv = voltage,
            healthLabel = healthLabel,
            statusLabel = statusLabel,
            isCharging = statusCode == AndroidBatteryManager.BATTERY_STATUS_CHARGING ||
                statusCode == AndroidBatteryManager.BATTERY_STATUS_FULL
        )
    }
}
