package com.example.model

enum class AiLabTab(val label: String) {
    FORECAST("Prediktor"),
    DELTA_DIFF("Delta Diff"),
    CONTEXT_VISUAL("Triage Cerdas"),
    CRASH_ANALYZER("Crash & ANR"),
    MEDIA_RECLAIMER("Kompresi Media"),
    PRE_LAUNCH("Pre-Launch")
}

// 1. Storage Growth Forecaster & Cache-Explosion Radar
data class StorageHistoryPoint(
    val timestampMillis: Long,
    val usedBytes: Long,
    val availableBytes: Long
)

data class CacheExplosionApp(
    val packageName: String,
    val appLabel: String,
    val currentCacheBytes: Long,
    val deltaBytesPerDay: Long,
    val severityLabel: String
)

data class StorageForecastReport(
    val dailyGrowthBytes: Long = 0L,
    val estimatedDaysUntilFull: Int? = null,
    val forecastHeadline: String = "Belum ada cukup riwayat untuk memprediksi laju memori.",
    val forecastDetail: String = "Tekan Rekam & Analisis untuk mulai melacak pertumbuhan cache dan ruang penyimpanan.",
    val historyPoints: List<StorageHistoryPoint> = emptyList(),
    val explosionApps: List<CacheExplosionApp> = emptyList(),
    val analyzedAtMillis: Long = 0L
)

// 2. Storage Delta Timeline ("Time-Travel Storage Diff")
data class StorageDeltaItem(
    val path: String,
    val label: String,
    val previousBytes: Long,
    val currentBytes: Long,
    val deltaBytes: Long
)

data class StorageDeltaReport(
    val baselineTimestampMillis: Long = 0L,
    val comparedTimestampMillis: Long = 0L,
    val totalDeltaBytes: Long = 0L,
    val items: List<StorageDeltaItem> = emptyList(),
    val statusMessage: String = "Simpan titik patokan terlebih dahulu untuk melacak perubahan memori."
)

// 3. Smart Context & Visual Similarity Triage
enum class ContextRiskLevel(val label: String) {
    PROTECTED("Lindungi"),
    REVIEW("Tinjau"),
    SAFE_TO_CLEAN("Aman Hapus")
}

data class ContextRiskItem(
    val path: String,
    val uriString: String? = null,
    val fileName: String,
    val sizeBytes: Long,
    val ageDays: Int,
    val riskLevel: ContextRiskLevel,
    val reason: String,
    val isSelected: Boolean = false
)

data class VisualPhotoCandidate(
    val path: String,
    val uriString: String? = null,
    val fileName: String,
    val sizeBytes: Long,
    val dHash: Long,
    val sharpnessScore: Double,
    val isBlurry: Boolean,
    val isScreenshot: Boolean,
    val isSelected: Boolean = false
)

data class VisualSimilarityGroup(
    val groupId: String,
    val reason: String,
    val candidates: List<VisualPhotoCandidate>
)

// 4. Silent Crash & ANR Culprit Analyzer
data class CrashCulpritEntry(
    val packageName: String,
    val sourceType: String, // "Tombstone", "ANR", "Dropbox", "ExitInfo"
    val occurrenceCount: Int,
    val totalLogBytes: Long,
    val sampleReason: String,
    val filePaths: List<String> = emptyList()
)

data class CrashAnalyzerReport(
    val culprits: List<CrashCulpritEntry> = emptyList(),
    val totalCrashLogBytes: Long = 0L,
    val totalLogFiles: Int = 0,
    val isRootDetailAvailable: Boolean = false,
    val summaryMessage: String = "Tekan Pindai Crash & ANR untuk mendiagnosa proses penyebab perlambatan."
)

// 5. On-Device Media Reclaimer
data class MediaReclaimCandidate(
    val id: String,
    val path: String?,
    val uriString: String?,
    val fileName: String,
    val isVideo: Boolean,
    val originalBytes: Long,
    val compressedBytes: Long? = null,
    val statusNote: String = "Siap dikompresi secara lokal",
    val isCompleted: Boolean = false,
    val isSelected: Boolean = true
)

// 6. Precision Pre-Launch Conditioner
enum class PreLaunchProfile(val title: String, val description: String) {
    GAMING("Mode Gaming", "Prioritaskan RAM bebas maksimal, trim cache aplikasi idle, dan sinkronisasi I/O."),
    CAMERA("Mode Kamera & Video", "Bebaskan buffer memori untuk perekaman stabil serta pantau suhu baterai."),
    HEAVY_APP("Multitasking Berat", "Pangkas proses latar belakang tidak aktif tanpa menutup layanan sistem inti.")
}

data class LaunchableAppEntry(
    val packageName: String,
    val appLabel: String
)

data class PreLaunchReport(
    val profile: PreLaunchProfile = PreLaunchProfile.GAMING,
    val targetAppLabel: String? = null,
    val targetPackageName: String? = null,
    val ramBeforeAvailableBytes: Long = 0L,
    val ramAfterAvailableBytes: Long = 0L,
    val batteryTempCelsius: Float = 0f,
    val trimmedProcessesCount: Int = 0,
    val actionsSummary: List<String> = emptyList(),
    val readyToLaunch: Boolean = false
)

// Non-Root SAF Folder representation
data class SafFolderItem(
    val uriString: String,
    val displayName: String,
    val fileCount: Int = 0,
    val junkBytes: Long = 0L,
    val totalBytes: Long = 0L
)
