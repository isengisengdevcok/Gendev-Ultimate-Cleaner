package com.example.model

import java.util.Locale

enum class RootStatus {
    CHECKING,
    GRANTED,
    DENIED,
    NOT_AVAILABLE
}

enum class OperationMode(val label: String, val shortLabel: String) {
    AUTO("Auto (Root / Non-Root)", "Auto"),
    ROOT("Root Mode (su -c)", "Root"),
    NON_ROOT("Non-Root (Native API)", "Non-Root")
}

enum class LogType {
    CMD,
    FOUND,
    CLEANED,
    ERROR,
    INFO
}

data class TerminalLogEntry(
    val id: Long,
    val timestamp: String,
    val type: LogType,
    val message: String
)

enum class JunkCategoryType {
    APP_CACHE,
    ANDROID_DATA_CACHE,
    SYSTEM_CACHE,
    TEMP_FILES,
    THUMBNAIL_CACHE,
    TRASH_FOLDERS,
    RESIDUAL_TEMP
}

data class JunkCategoryScanResult(
    val type: JunkCategoryType,
    val title: String,
    val description: String,
    val targetPaths: List<String>,
    val fileCount: Int = 0,
    val sizeBytes: Long = 0L,
    val sampleFiles: List<String> = emptyList(),
    val isSelected: Boolean = true,
    val statusNote: String? = null
)

enum class LegacyCategoryType {
    DCIM,
    PICTURES,
    MOVIES,
    MUSIC,
    PODCASTS,
    RINGTONES,
    NOTIFICATIONS,
    ALARMS,
    WHATSAPP_MEDIA,
    WHATSAPP_BUSINESS_MEDIA,
    DOWNLOAD
}

data class LegacyCategoryItem(
    val type: LegacyCategoryType,
    val title: String,
    val paths: List<String>,
    val fileCount: Int = 0,
    val sizeBytes: Long = 0L,
    val isSelected: Boolean = false,
    val isScanned: Boolean = false,
    val protectedNotes: String? = null
)

data class AppCacheEntry(
    val packageName: String,
    val appLabel: String,
    val internalCacheBytes: Long,
    val externalCacheBytes: Long,
    val totalCacheBytes: Long,
    val isSelected: Boolean = false
)

data class OrphanedResidueItem(
    val packageName: String,
    val path: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val isSelected: Boolean = false
)

data class LargeFileEntry(
    val path: String,
    val fileName: String,
    val sizeBytes: Long,
    val isSelected: Boolean = false
)

data class DuplicateGroup(
    val hash: String,
    val sizeBytes: Long,
    val filePaths: List<String>,
    val selectedForDeletion: Set<String> = emptySet()
)

data class OldApkEntry(
    val path: String,
    val fileName: String,
    val packageName: String?,
    val versionName: String?,
    val sizeBytes: Long,
    val isInstalled: Boolean,
    val isSelected: Boolean = false
)

data class StorageFolderBreakdown(
    val label: String,
    val path: String,
    val sizeBytes: Long
)

data class DryRunPreview(
    val title: String,
    val filePaths: List<String>,
    val totalFileCount: Int,
    val totalBytes: Long
)

enum class WhatsAppCategoryType(val label: String) {
    MEDIA_IMAGES_VIDEO("Foto, Video & GIF Media"),
    AUDIO_VOICE("Audio & Voice Note"),
    DOCUMENTS("Dokumen Media WhatsApp"),
    STICKERS_VIEWONCE("Stiker & ViewOnce"),
    STATUS_SHARED_TRASH("Status (.Statuses), .Shared & .Trash"),
    ROOT_INTERNAL_CACHE("Root Cache, Logs & WebView (/data)")
}

data class WhatsAppTargetGroup(
    val type: WhatsAppCategoryType,
    val title: String,
    val subtitle: String,
    val paths: List<String>,
    val fileCount: Int = 0,
    val sizeBytes: Long = 0L,
    val sampleFiles: List<String> = emptyList(),
    val isSelected: Boolean = true,
    val isScanned: Boolean = false
)

data class LastCleanSummary(
    val mode: String = "",
    val freedBytes: Long = 0L,
    val filesDeleted: Int = 0,
    val timestampMillis: Long = 0L
)

data class CleaningHistoryItem(
    val id: Long,
    val timestampMillis: Long,
    val mode: String,
    val freedBytes: Long,
    val filesDeleted: Int,
    val details: String,
    val success: Boolean
)

data class AppSettings(
    val darkTheme: Boolean = true,
    val purpleAccent: Boolean = true,
    val animationsEnabled: Boolean = true,
    val autoScrollLog: Boolean = true,
    val cleaningConfirmation: Boolean = true,
    val secureShredEnabled: Boolean = true,
    val zeroTraceHistoryEnabled: Boolean = false,
    val operationMode: OperationMode = OperationMode.AUTO,
    val customExclusions: List<String> = emptyList(),
    val grantedSafUris: List<String> = emptyList()
)

data class AutomationConfig(
    val scheduledEnabled: Boolean = false,
    val intervalHours: Int = 12,
    val screenOffEnabled: Boolean = false,
    val batterySaverEnabled: Boolean = false,
    val lowStorageGuardEnabled: Boolean = false,
    val lowStorageThresholdGb: Int = 2,
    val cooldownMinutes: Int = 30,
    val lastBootTimestamp: Long = 0L,
    val lastAutoCleanTimestamp: Long = 0L,
    val lastAutoCleanSummary: String = "",
    val lastAutoCleanTrigger: String = "",
    val bootStatusMessage: String = "Menunggu inisialisasi event BOOT_COMPLETED"
)

data class RamInfo(
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val buffersBytes: Long = 0L,
    val cachedBytes: Long = 0L,
    val swapTotalBytes: Long = 0L,
    val swapFreeBytes: Long = 0L,
    val isLowMemory: Boolean = false
)

data class StoragePartitionInfo(
    val name: String,
    val mountPath: String,
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long
)

data class CpuInfo(
    val hardware: String = "Unknown",
    val abi: String = "arm64-v8a",
    val coreCount: Int = 1,
    val currentFreqMhzList: List<Int> = emptyList(),
    val maxFreqMhz: Int = 0
)

data class BatteryStatusInfo(
    val levelPercent: Int = 0,
    val temperatureCelsius: Float = 0f,
    val voltageMv: Int = 0,
    val healthLabel: String = "Unknown",
    val statusLabel: String = "Unknown",
    val isCharging: Boolean = false
)

data class ProcessInfo(
    val pid: Int,
    val user: String,
    val rssKb: Long,
    val name: String
)

data class SystemSnapshot(
    val deviceName: String = "",
    val modelCode: String = "",
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val ramInfo: RamInfo = RamInfo(),
    val internalStorage: StoragePartitionInfo = StoragePartitionInfo("Internal", "/data", 0L, 0L, 0L),
    val externalStorage: StoragePartitionInfo = StoragePartitionInfo("Shared (/sdcard)", "/sdcard", 0L, 0L, 0L),
    val cpuInfo: CpuInfo = CpuInfo(),
    val batteryInfo: BatteryStatusInfo = BatteryStatusInfo(),
    val topProcesses: List<ProcessInfo> = emptyList(),
    val storageBreakdown: List<StorageFolderBreakdown> = emptyList(),
    val fstrimSupported: Boolean = false,
    val lastUpdatedMillis: Long = 0L
)

object FormatUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.2f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
