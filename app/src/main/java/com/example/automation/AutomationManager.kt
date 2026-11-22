package com.example.automation

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.CleaningRepository
import com.example.model.AutomationConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class AutomationManager(
    private val context: Context,
    private val repository: CleaningRepository
) {
    companion object {
        private const val PERIODIC_WORK_NAME = "ultimate_cleaner_periodic_cache"
        private const val IMMEDIATE_WORK_NAME = "ultimate_cleaner_trigger_cache"
    }

    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    val periodicWorkStatusFlow: Flow<String> = workManager
        .getWorkInfosForUniqueWorkFlow(PERIODIC_WORK_NAME)
        .map { infos ->
            val info = infos.firstOrNull()
            when (info?.state) {
                WorkInfo.State.ENQUEUED -> "Terjadwal & Menunggu Siklus Berikutnya"
                WorkInfo.State.RUNNING -> "Sedang Menjalankan Pembersihan..."
                WorkInfo.State.SUCCEEDED -> "Siklus Terakhir Berhasil"
                WorkInfo.State.BLOCKED -> "Tertunda Menunggu Kondisi Baterai"
                WorkInfo.State.FAILED -> "Gagal (Periksa Log/Otorisasi)"
                WorkInfo.State.CANCELLED, null -> "Tidak Aktif"
            }
        }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun buildBatteryOptimizationIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun setScheduledCleaning(enabled: Boolean, intervalHours: Int) {
        repository.updateScheduledAutomation(enabled, intervalHours)
        val config = repository.getAutomationOnce()
        syncAutomationState(config)
    }

    suspend fun setScreenOffCleaning(enabled: Boolean) {
        repository.updateScreenOffAutomation(enabled)
        val config = repository.getAutomationOnce()
        syncAutomationState(config)
    }

    suspend fun setBatterySaverCleaning(enabled: Boolean) {
        repository.updateBatterySaverAutomation(enabled)
        val config = repository.getAutomationOnce()
        syncAutomationState(config)
    }

    suspend fun setLowStorageGuard(enabled: Boolean, thresholdGb: Int) {
        repository.updateLowStorageGuard(enabled, thresholdGb)
        val config = repository.getAutomationOnce()
        syncAutomationState(config)
    }

    suspend fun setCooldownMinutes(minutes: Int) {
        repository.updateAutomationCooldown(minutes)
        val config = repository.getAutomationOnce()
        syncAutomationState(config)
    }

    fun enqueueImmediateAutoClean(triggerSource: String) {
        val request = OneTimeWorkRequestBuilder<AutoCleanWorker>()
            .setInputData(workDataOf(AutoCleanWorker.KEY_TRIGGER_SOURCE to triggerSource))
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun syncAutomationState(config: AutomationConfig) {
        if (config.scheduledEnabled) {
            val hours = config.intervalHours.coerceAtLeast(1).toLong()
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<AutoCleanWorker>(hours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInputData(workDataOf(AutoCleanWorker.KEY_TRIGGER_SOURCE to "Terjadwal-${hours}j"))
                .build()
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        val serviceIntent = Intent(context, AutomationMonitorService::class.java)
        if (config.screenOffEnabled || config.batterySaverEnabled || config.lowStorageGuardEnabled) {
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (_: Exception) {
            }
        } else {
            try {
                context.stopService(serviceIntent)
            } catch (_: Exception) {
            }
        }
    }
}
