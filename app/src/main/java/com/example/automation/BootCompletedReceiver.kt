package com.example.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer.get(appContext)
                val now = System.currentTimeMillis()
                val formatted = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(now))
                val config = container.repository.getAutomationOnce()

                val statusText = if (config.scheduledEnabled || config.screenOffEnabled || config.batterySaverEnabled || config.lowStorageGuardEnabled) {
                    "Boot aktif ($formatted) — Otomatisasi dipulihkan"
                } else {
                    "Boot terdeteksi ($formatted) — Semua otomatisasi NONAKTIF"
                }
                container.repository.recordBootEvent(now, statusText)
                container.automationManager.syncAutomationState(config)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
