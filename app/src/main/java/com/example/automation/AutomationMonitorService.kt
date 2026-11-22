package com.example.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.example.AppContainer
import com.example.MainActivity
import com.example.R
import com.example.model.AutomationConfig
import com.example.model.FormatUtils
import com.example.model.LogType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AutomationMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "ultimate_cleaner_automation"
        private const val NOTIFICATION_ID = 4102
        private const val LOW_STORAGE_NOTIF_ID = 4103
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverRegistered = false

    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val container = AppContainer.get(context)
            serviceScope.launch {
                val config = container.repository.getAutomationOnce()
                val now = System.currentTimeMillis()
                val cooldownMs = config.cooldownMinutes.coerceAtLeast(5) * 60 * 1000L
                val cooldownPassed = (now - config.lastAutoCleanTimestamp) >= cooldownMs

                when (action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        if (config.screenOffEnabled && cooldownPassed) {
                            container.rootManager.appendLog(LogType.INFO, "Trigger ACTION_SCREEN_OFF terdeteksi")
                            container.automationManager.enqueueImmediateAutoClean("Screen-Off")
                        }
                        if (config.lowStorageGuardEnabled && cooldownPassed) {
                            checkLowStorageThreshold(context, config, container)
                        }
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                        if (pm.isPowerSaveMode && config.batterySaverEnabled && cooldownPassed) {
                            container.rootManager.appendLog(LogType.INFO, "Trigger Battery Saver aktif terdeteksi")
                            container.automationManager.enqueueImmediateAutoClean("Battery-Saver")
                        }
                    }
                    Intent.ACTION_DEVICE_STORAGE_LOW -> {
                        if (config.lowStorageGuardEnabled && cooldownPassed) {
                            container.rootManager.appendLog(LogType.INFO, "Trigger DEVICE_STORAGE_LOW terdeteksi")
                            container.automationManager.enqueueImmediateAutoClean("Low-Storage")
                        }
                    }
                }
            }
        }
    }

    private suspend fun checkLowStorageThreshold(
        context: Context,
        config: AutomationConfig,
        container: AppContainer
    ) {
        val stat = StatFs(context.filesDir.absolutePath)
        val avail = stat.availableBytes
        val threshold = config.lowStorageThresholdGb * 1024L * 1024L * 1024L
        if (avail < threshold) {
            container.rootManager.appendLog(
                LogType.INFO,
                "Low-Storage Guard aktif: ruang bebas ${FormatUtils.formatBytes(avail)} (< ${config.lowStorageThresholdGb} GB)"
            )
            postLowStorageNotification(avail)
            container.automationManager.enqueueImmediateAutoClean("Low-Storage")
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification("Memantau pemicu otomatisasi aktif")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
        }
        registerReceiver(systemEventReceiver, filter)
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            val config = AppContainer.get(applicationContext).repository.getAutomationOnce()
            if (!config.screenOffEnabled && !config.batterySaverEnabled && !config.lowStorageGuardEnabled) {
                stopSelf()
            } else {
                val activeList = buildList {
                    if (config.screenOffEnabled) add("Screen-Off")
                    if (config.batterySaverEnabled) add("Battery Saver")
                    if (config.lowStorageGuardEnabled) add("Low-Storage <${config.lowStorageThresholdGb}GB")
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(
                    NOTIFICATION_ID,
                    buildNotification("Aktif (${config.cooldownMinutes}m jeda): ${activeList.joinToString(" • ")}")
                )
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try {
                unregisterReceiver(systemEventReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_automation),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_automation_title))
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun postLowStorageNotification(availableBytes: Long) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Penyimpanan Internal Menipis")
            .setContentText("Sisa ruang ${FormatUtils.formatBytes(availableBytes)}. Pembersihan cache otomatis dijalankan.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(LOW_STORAGE_NOTIF_ID, notif)
    }
}
