package com.example.automation

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.AppContainer
import com.example.model.FormatUtils
import com.example.model.JunkCategoryType
import com.example.model.LogType
import com.example.model.RootStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class QuickCleanTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Quick Clean"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = "Cache Root"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "Memproses..."
        }
        tile.updateTile()

        tileScope.launch {
            val container = AppContainer.get(applicationContext)
            val rootManager = container.rootManager
            val engine = container.cleanerEngine
            val repository = container.repository

            rootManager.appendLog(LogType.INFO, "Quick Settings Tile: memulai pembersihan cache...")
            if (rootManager.checkRoot(forceRecheck = false) != RootStatus.GRANTED) {
                rootManager.appendLog(LogType.ERROR, "Quick Settings Tile: otorisasi root tidak aktif.")
                resetTile("Root Ditolak")
                return@launch
            }

            val settings = repository.getSettingsOnce()
            val cacheTargets = engine.getSmartJunkDefinitions().filter {
                it.type == JunkCategoryType.APP_CACHE ||
                    it.type == JunkCategoryType.ANDROID_DATA_CACHE ||
                    it.type == JunkCategoryType.THUMBNAIL_CACHE
            }.map { it.copy(isSelected = true) }

            val summary = engine.cleanSmartJunk(
                categories = cacheTargets,
                customExclusions = settings.customExclusions,
                userApproved = true,
                deepMode = false
            )

            repository.recordCleaningRun(
                mode = "Quick Settings Tile",
                freedBytes = summary.totalFreedBytes,
                filesDeleted = summary.totalFilesDeleted,
                details = summary.summaryMessage,
                success = summary.errorsCount == 0
            )

            resetTile(FormatUtils.formatBytes(summary.totalFreedBytes))
        }
    }

    private fun resetTile(subtitleText: String) {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = subtitleText
            }
            updateTile()
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }
}
