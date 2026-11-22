package com.example.automation

import android.content.Context
import android.os.StatFs
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.AppContainer
import com.example.model.FormatUtils
import com.example.model.JunkCategoryType
import com.example.model.LogType
import com.example.model.OperationMode
import com.example.model.RootStatus

class AutoCleanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TRIGGER_SOURCE = "trigger_source"
    }

    override suspend fun doWork(): Result {
        val trigger = inputData.getString(KEY_TRIGGER_SOURCE) ?: "Terjadwal"
        val container = AppContainer.get(applicationContext)
        val rootManager = container.rootManager
        val repository = container.repository
        val engine = container.cleanerEngine
        val nonRootEngine = container.nonRootEngine

        val settings = repository.getSettingsOnce()
        val autoConfig = repository.getAutomationOnce()

        rootManager.appendLog(LogType.INFO, "Otomatisasi ($trigger): memulai pembersihan cache...")

        val rootGranted = if (settings.operationMode != OperationMode.NON_ROOT) {
            rootManager.checkRoot(forceRecheck = false) == RootStatus.GRANTED
        } else {
            false
        }

        val useRoot = when (settings.operationMode) {
            OperationMode.ROOT -> rootGranted
            OperationMode.NON_ROOT -> false
            OperationMode.AUTO -> rootGranted
        }

        if (settings.operationMode == OperationMode.ROOT && !rootGranted) {
            val errMsg = "Mode Root dipilih, namun otorisasi su -c belum aktif."
            rootManager.appendLog(LogType.ERROR, "Otomatisasi ($trigger): $errMsg")
            repository.recordAutoCleanResult(System.currentTimeMillis(), trigger, errMsg)
            return Result.failure()
        }

        // Strictly clean safe cache categories only — NEVER Legacy Cleaner!
        val safeCacheTargets = engine.getSmartJunkDefinitions().filter {
            it.type == JunkCategoryType.APP_CACHE ||
                it.type == JunkCategoryType.ANDROID_DATA_CACHE ||
                it.type == JunkCategoryType.THUMBNAIL_CACHE ||
                it.type == JunkCategoryType.TEMP_FILES
        }.map { it.copy(isSelected = true) }

        val summary = if (useRoot) {
            engine.cleanSmartJunk(
                categories = safeCacheTargets,
                customExclusions = settings.customExclusions,
                userApproved = true,
                deepMode = false,
                secureShred = settings.secureShredEnabled
            )
        } else {
            nonRootEngine.cleanNonRootJunk(
                categories = safeCacheTargets,
                grantedSafUris = settings.grantedSafUris,
                customExclusions = settings.customExclusions,
                secureShred = settings.secureShredEnabled
            )
        }

        // Low-Storage Guard telemetry log
        if (autoConfig.lowStorageGuardEnabled) {
            val stat = StatFs(applicationContext.filesDir.absolutePath)
            val availBytes = stat.availableBytes
            val thresholdBytes = autoConfig.lowStorageThresholdGb * 1024L * 1024L * 1024L
            if (availBytes < thresholdBytes) {
                rootManager.appendLog(
                    LogType.INFO,
                    "Low-Storage Guard: ruang tersisa ${FormatUtils.formatBytes(availBytes)} (< ${autoConfig.lowStorageThresholdGb} GB)."
                )
            }
        }

        val now = System.currentTimeMillis()
        val modeTag = if (useRoot) "Auto-Root ($trigger)" else "Auto-NonRoot ($trigger)"
        repository.recordAutoCleanResult(now, trigger, summary.summaryMessage)
        repository.recordCleaningRun(
            mode = modeTag,
            freedBytes = summary.totalFreedBytes,
            filesDeleted = summary.totalFilesDeleted,
            details = summary.summaryMessage,
            success = summary.errorsCount == 0
        )

        if (settings.zeroTraceHistoryEnabled) {
            container.secureDeleteEngine.wipeSystemGhostCaches(true)
            repository.clearHistoryLogsOnly()
            rootManager.clearLogsQuietly()
        }

        return Result.success()
    }
}
