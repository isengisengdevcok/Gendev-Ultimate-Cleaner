package com.example.engine

import com.example.model.DryRunPreview
import com.example.model.FormatUtils
import com.example.model.JunkCategoryScanResult
import com.example.model.JunkCategoryType
import com.example.model.LegacyCategoryItem
import com.example.model.LegacyCategoryType
import com.example.model.LogType
import com.example.model.RootStatus
import com.example.root.RootManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

data class CleanSummaryResult(
    val totalFreedBytes: Long,
    val totalFilesDeleted: Int,
    val errorsCount: Int,
    val summaryMessage: String
)

object PathValidator {

    val MANDATORY_PROTECTED_EXACT = setOf(
        "/sdcard/Download/KitsuneBackup",
        "/storage/emulated/0/Download/KitsuneBackup",
        "/sdcard/UltimateCleaner.log",
        "/storage/emulated/0/UltimateCleaner.log"
    )

    val MANDATORY_PROTECTED_SUBSTRINGS = listOf(
        "KitsuneBackup",
        "LineageOS-18.1-arm64-a-Vanilla.img.xz",
        "UltimateCleaner.log",
        "/data/adb",
        "/data/system",
        "/data/misc",
        "/system/",
        "/vendor/",
        "/product/"
    )

    val FORBIDDEN_EXACT_ROOTS = setOf(
        "/",
        "/data",
        "/data/data",
        "/data/user",
        "/data/user/0",
        "/sdcard",
        "/storage",
        "/storage/emulated",
        "/storage/emulated/0",
        "/system",
        "/vendor",
        "/product",
        "/apex",
        "/data/adb"
    )

    fun sanitizeShellPath(path: String): String {
        return path.replace("'", "'\"'\"'")
    }

    fun isSafeDirectoryTarget(path: String, customExclusions: List<String>): Boolean {
        val normalized = path.trim().trimEnd('/')
        if (normalized.isEmpty()) return false
        if (normalized in FORBIDDEN_EXACT_ROOTS) return false
        if (normalized.contains("*") || normalized.contains(";") || normalized.contains("|") || normalized.contains("&")) {
            return false
        }
        if (MANDATORY_PROTECTED_EXACT.any { normalized.equals(it, ignoreCase = true) }) {
            return false
        }
        if (MANDATORY_PROTECTED_SUBSTRINGS.any { normalized.contains(it, ignoreCase = true) }) {
            return false
        }
        if (customExclusions.any { ex -> ex.isNotBlank() && normalized.contains(ex.trim(), ignoreCase = true) }) {
            return false
        }
        return normalized.startsWith("/sdcard/") ||
            normalized.startsWith("/storage/emulated/0/") ||
            normalized.startsWith("/data/data/") ||
            normalized.startsWith("/data/user/0/") ||
            normalized == "/data/local/tmp" ||
            normalized == "/data/anr" ||
            normalized == "/data/tombstones" ||
            normalized == "/data/cache" ||
            normalized == "/cache"
    }

    fun isSafeFileDeletionTarget(path: String, selfPackageName: String, customExclusions: List<String>): Boolean {
        val normalized = path.trim()
        if (normalized.isEmpty()) return false
        if (normalized.contains(";") || normalized.contains("|") || normalized.contains("&") || normalized.contains("`")) {
            return false
        }
        if (normalized in FORBIDDEN_EXACT_ROOTS) return false
        if (normalized.contains(selfPackageName)) return false
        if (MANDATORY_PROTECTED_EXACT.any { normalized.equals(it, ignoreCase = true) }) return false
        if (MANDATORY_PROTECTED_SUBSTRINGS.any { normalized.contains(it, ignoreCase = true) }) return false
        val name = File(normalized).name
        if (name.endsWith(".db", ignoreCase = true) ||
            name.endsWith(".db-wal", ignoreCase = true) ||
            name.endsWith(".db-shm", ignoreCase = true) ||
            name.endsWith(".db-journal", ignoreCase = true) ||
            name.endsWith(".crypt12", ignoreCase = true) ||
            name.endsWith(".crypt14", ignoreCase = true) ||
            name.endsWith(".crypt15", ignoreCase = true)
        ) {
            return false
        }
        if (customExclusions.any { ex -> ex.isNotBlank() && normalized.contains(ex.trim(), ignoreCase = true) }) {
            return false
        }
        return normalized.startsWith("/sdcard/") ||
            normalized.startsWith("/storage/emulated/0/") ||
            normalized.startsWith("/data/data/") ||
            normalized.startsWith("/data/user/0/")
    }

    fun buildFindExclusionFlags(customExclusions: List<String>, includeDatabaseProtection: Boolean = true): String {
        val flags = mutableListOf<String>()
        flags.add("! -path '*/KitsuneBackup*'")
        flags.add("! -name 'LineageOS-18.1-arm64-a-Vanilla.img.xz'")
        flags.add("! -path '*LineageOS-18.1-arm64-a-Vanilla.img.xz*'")
        flags.add("! -name 'UltimateCleaner.log'")
        flags.add("! -path '*/data/adb*'")

        if (includeDatabaseProtection) {
            flags.add("! -name '*.db'")
            flags.add("! -name '*.db-wal'")
            flags.add("! -name '*.db-shm'")
            flags.add("! -name '*.db-journal'")
            flags.add("! -name '*.crypt*'")
            flags.add("! -path '*/databases/*'")
            flags.add("! -path '*/Databases/*'")
            flags.add("! -path '*/Backups/*'")
            flags.add("! -path '*/shared_prefs/*'")
            flags.add("! -path '*/keystore/*'")
        }

        customExclusions.forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty() && !trimmed.contains("'")) {
                if (trimmed.startsWith("/")) {
                    flags.add("! -path '${sanitizeShellPath(trimmed)}*'")
                } else {
                    flags.add("! -path '*${sanitizeShellPath(trimmed)}*'")
                    flags.add("! -name '*${sanitizeShellPath(trimmed)}*'")
                }
            }
        }
        return flags.joinToString(" ")
    }
}

class CleanerEngine(
    private val rootManager: RootManager,
    private val selfPackageName: String,
    private val secureDeleteEngine: SecureDeleteEngine? = null
) {

    fun getSmartJunkDefinitions(): List<JunkCategoryScanResult> {
        return listOf(
            JunkCategoryScanResult(
                type = JunkCategoryType.APP_CACHE,
                title = "Cache Aplikasi (/data)",
                description = "Direktori cache dan code_cache internal aplikasi",
                targetPaths = listOf("/data/data", "/data/user/0")
            ),
            JunkCategoryScanResult(
                type = JunkCategoryType.ANDROID_DATA_CACHE,
                title = "Cache Android/data",
                description = "Direktori cache eksternal aplikasi pada /sdcard/Android/data",
                targetPaths = listOf("/sdcard/Android/data")
            ),
            JunkCategoryScanResult(
                type = JunkCategoryType.SYSTEM_CACHE,
                title = "Cache Sistem Terverifikasi",
                description = "Berkas temporer sistem pada /data/local/tmp, anr, tombstones, dan cache",
                targetPaths = listOf("/data/local/tmp", "/data/anr", "/data/tombstones", "/data/cache", "/cache")
            ),
            JunkCategoryScanResult(
                type = JunkCategoryType.TEMP_FILES,
                title = "Berkas Temporer & Log",
                description = "Berkas *.tmp, *.temp, *.bak, *.log pada penyimpanan bersama",
                targetPaths = listOf("/sdcard")
            ),
            JunkCategoryScanResult(
                type = JunkCategoryType.THUMBNAIL_CACHE,
                title = "Cache Thumbnail Media",
                description = "Indeks pratinjau gambar dan video pada direktori .thumbnails",
                targetPaths = listOf(
                    "/sdcard/DCIM/.thumbnails",
                    "/sdcard/Pictures/.thumbnails",
                    "/sdcard/Movies/.thumbnails"
                )
            ),
            JunkCategoryScanResult(
                type = JunkCategoryType.TRASH_FOLDERS,
                title = "Direktori Sampah Sistem",
                description = "Berkas tersembunyi pada .Trash, .RecycleBin, dan .opporecycle",
                targetPaths = listOf(
                    "/sdcard/.Trash",
                    "/sdcard/.Trash-1000",
                    "/sdcard/DCIM/.Trash",
                    "/sdcard/Pictures/.Trash",
                    "/sdcard/Android/.Trash"
                )
            ),
            JunkCategoryScanResult(
                type = JunkCategoryType.RESIDUAL_TEMP,
                title = "Residu WebView & OBB",
                description = "Cache WebView internal serta berkas unduhan OBB .tmp tidak lengkap",
                targetPaths = listOf("/data/data", "/sdcard/Android/obb")
            )
        )
    }

    fun getLegacyCategories(): List<LegacyCategoryItem> {
        return listOf(
            LegacyCategoryItem(
                type = LegacyCategoryType.DCIM,
                title = "DCIM",
                paths = listOf("/sdcard/DCIM"),
                protectedNotes = "Direktori induk DCIM dipertahankan"
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.PICTURES,
                title = "Pictures",
                paths = listOf("/sdcard/Pictures"),
                protectedNotes = "Direktori induk Pictures dipertahankan"
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.MOVIES,
                title = "Movies",
                paths = listOf("/sdcard/Movies"),
                protectedNotes = "Direktori induk Movies dipertahankan"
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.MUSIC,
                title = "Music",
                paths = listOf("/sdcard/Music"),
                protectedNotes = "Direktori induk Music dipertahankan"
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.PODCASTS,
                title = "Podcasts",
                paths = listOf("/sdcard/Podcasts")
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.RINGTONES,
                title = "Ringtones",
                paths = listOf("/sdcard/Ringtones")
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.NOTIFICATIONS,
                title = "Notifications",
                paths = listOf("/sdcard/Notifications")
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.ALARMS,
                title = "Alarms",
                paths = listOf("/sdcard/Alarms")
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.WHATSAPP_MEDIA,
                title = "WhatsApp Media",
                paths = listOf(
                    "/sdcard/WhatsApp/Media",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media"
                ),
                protectedNotes = "Basis data (*.db*, *.crypt*) & Backups WhatsApp dilindungi"
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.WHATSAPP_BUSINESS_MEDIA,
                title = "WhatsApp Business Media",
                paths = listOf(
                    "/sdcard/WhatsApp Business/Media",
                    "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media"
                ),
                protectedNotes = "Basis data & konfigurasi WhatsApp Business dilindungi"
            ),
            LegacyCategoryItem(
                type = LegacyCategoryType.DOWNLOAD,
                title = "Download",
                paths = listOf("/sdcard/Download"),
                protectedNotes = "KitsuneBackup & LineageOS-18.1-arm64-a-Vanilla.img.xz dilindungi"
            )
        )
    }

    suspend fun previewSmartJunkDryRun(
        categories: List<JunkCategoryScanResult>,
        customExclusions: List<String>,
        deepMode: Boolean
    ): DryRunPreview {
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            return DryRunPreview("Dry-Run Preview", emptyList(), 0, 0L)
        }
        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val previewPaths = mutableListOf<String>()
        var totalBytes = 0L
        var totalCount = 0

        for (cat in categories.filter { it.isSelected }) {
            currentCoroutineContext().ensureActive()
            val scanCmd = buildScanCommandForCategory(cat.type, exclusions, deepMode)
            val res = rootManager.execute(
                command = scanCmd,
                userApproved = true,
                timeoutMs = 25_000L,
                logCommand = false,
                logStderrAsError = false
            )
            res.stdout.forEach { line ->
                parseDuLine(line)?.let { (kb, path) ->
                    if (!isSelfOrProtected(path, customExclusions)) {
                        totalCount++
                        totalBytes += kb * 1024L
                        if (previewPaths.size < 200) {
                            previewPaths.add("${FormatUtils.formatBytes(kb * 1024L)}  $path")
                        }
                    }
                }
            }
        }
        rootManager.appendLog(
            LogType.INFO,
            "Dry-Run Preview: $totalCount target teridentifikasi (${FormatUtils.formatBytes(totalBytes)})."
        )
        return DryRunPreview(
            title = if (deepMode) "Pratinjau Deep Clean (Dry-Run)" else "Pratinjau Smart Junk (Dry-Run)",
            filePaths = previewPaths,
            totalFileCount = totalCount,
            totalBytes = totalBytes
        )
    }

    suspend fun scanSmartJunk(
        customExclusions: List<String>,
        deepMode: Boolean = false,
        onProgress: ((Float, String) -> Unit)? = null
    ): List<JunkCategoryScanResult> {
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            rootManager.appendLog(LogType.ERROR, "Pemindaian dibatalkan: otorisasi root tidak aktif.")
            return getSmartJunkDefinitions().map { it.copy(statusNote = "Root tidak terhubung") }
        }

        rootManager.appendLog(
            LogType.INFO,
            if (deepMode) "Memulai pemindaian menyeluruh (Deep Mode)..." else "Memulai pemindaian Smart Junk..."
        )

        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val baseList = getSmartJunkDefinitions()
        val results = mutableListOf<JunkCategoryScanResult>()
        val totalSteps = baseList.size.coerceAtLeast(1)

        baseList.forEachIndexed { index, category ->
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(index.toFloat() / totalSteps.toFloat(), "Memindai ${category.title}...")
            val scanCmd = buildScanCommandForCategory(category.type, exclusions, deepMode)
            val cmdRes = rootManager.execute(
                command = scanCmd,
                userApproved = true,
                timeoutMs = 30_000L,
                logCommand = true,
                streamOutput = false,
                logStderrAsError = false
            )

            var totalBytes = 0L
            var count = 0
            val samples = mutableListOf<String>()
            cmdRes.stdout.forEach { line ->
                parseDuLine(line)?.let { (kb, path) ->
                    if (!isSelfOrProtected(path, customExclusions)) {
                        count++
                        totalBytes += kb * 1024L
                        if (samples.size < 6) samples.add(path)
                    }
                }
            }

            val permErrors = cmdRes.stderr.count {
                it.contains("Permission denied", ignoreCase = true) ||
                    it.contains("avc: denied", ignoreCase = true)
            }
            val note = when {
                permErrors > 0 && count == 0 -> "Dibatasi kebijakan SELinux ($permErrors direktori)"
                permErrors > 0 -> "$permErrors direktori dibatasi SELinux"
                count == 0 -> "Optimal (0 berkas)"
                else -> null
            }

            if (count > 0) {
                rootManager.appendLog(
                    LogType.FOUND,
                    "${category.title}: $count berkas (${FormatUtils.formatBytes(totalBytes)})"
                )
            }
            if (permErrors > 0) {
                rootManager.appendLog(LogType.ERROR, "${category.title}: $permErrors direktori permission denied")
            }

            results.add(
                category.copy(
                    fileCount = count,
                    sizeBytes = totalBytes,
                    sampleFiles = samples,
                    statusNote = note
                )
            )
            onProgress?.invoke((index + 1).toFloat() / totalSteps.toFloat(), "Selesai memindai ${category.title}")
        }
        return results
    }

    suspend fun cleanSmartJunk(
        categories: List<JunkCategoryScanResult>,
        customExclusions: List<String>,
        userApproved: Boolean,
        deepMode: Boolean = false,
        secureShred: Boolean = true,
        onProgress: ((Float, String) -> Unit)? = null
    ): CleanSummaryResult {
        if (!userApproved) {
            rootManager.appendLog(LogType.ERROR, "Pembersihan ditolak: persetujuan pengguna diperlukan.")
            return CleanSummaryResult(0L, 0, 1, "Konfirmasi pengguna diperlukan")
        }
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            return CleanSummaryResult(0L, 0, 1, "Otorisasi root tidak aktif")
        }

        val selected = categories.filter { it.isSelected }
        if (selected.isEmpty()) {
            return CleanSummaryResult(0L, 0, 0, "Tidak ada kategori dipilih")
        }

        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val availBefore = secureDeleteEngine?.getAvailableDataBytes() ?: 0L
        var totalFreed = 0L
        var totalFiles = 0
        var totalErrors = 0
        val totalSteps = selected.size.coerceAtLeast(1)

        selected.forEachIndexed { index, cat ->
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(index.toFloat() / totalSteps.toFloat(), "Membersihkan ${cat.title}...")
            rootManager.appendLog(LogType.INFO, "Mengeksekusi pembersihan: ${cat.title}")

            val preScanCmd = buildScanCommandForCategory(cat.type, exclusions, deepMode)
            val preRes = rootManager.execute(
                command = preScanCmd,
                userApproved = true,
                timeoutMs = 30_000L,
                logCommand = false,
                logStderrAsError = false
            )
            var preBytes = 0L
            var preCount = 0
            val foundPaths = mutableListOf<String>()
            preRes.stdout.forEach { line ->
                parseDuLine(line)?.let { (kb, path) ->
                    if (!isSelfOrProtected(path, customExclusions)) {
                        preCount++
                        preBytes += kb * 1024L
                        if (foundPaths.size < 200) foundPaths.add(path)
                    }
                }
            }

            if (preCount == 0 && cat.fileCount == 0) {
                onProgress?.invoke((index + 1).toFloat() / totalSteps.toFloat(), "${cat.title} sudah bersih")
                return@forEachIndexed
            }

            val deleteCmd = buildDeleteCommandForCategory(cat.type, exclusions, deepMode, secureShred)
            val delRes = rootManager.execute(
                command = deleteCmd,
                userApproved = true,
                timeoutMs = 60_000L,
                logCommand = true,
                streamOutput = false,
                logStderrAsError = true
            )

            val postRes = rootManager.execute(
                command = preScanCmd,
                userApproved = true,
                timeoutMs = 25_000L,
                logCommand = false,
                logStderrAsError = false
            )
            var postBytes = 0L
            var postCount = 0
            postRes.stdout.forEach { line ->
                parseDuLine(line)?.let { (kb, path) ->
                    if (!isSelfOrProtected(path, customExclusions)) {
                        postCount++
                        postBytes += kb * 1024L
                    }
                }
            }

            val effectivePreBytes = if (preBytes > 0L) preBytes else cat.sizeBytes
            val effectivePreCount = if (preCount > 0) preCount else cat.fileCount
            val actuallyDeleted = (effectivePreCount - postCount).coerceAtLeast(0)
            val actuallyFreed = if (actuallyDeleted > 0 && effectivePreBytes > postBytes) {
                (effectivePreBytes - postBytes).coerceAtLeast(0L)
            } else if (actuallyDeleted > 0 && cat.sizeBytes > 0L) {
                cat.sizeBytes
            } else {
                0L
            }
            totalFreed += actuallyFreed
            totalFiles += actuallyDeleted
            if (!delRes.success && delRes.stderr.isNotEmpty()) {
                totalErrors += delRes.stderr.size
            }

            if (foundPaths.isNotEmpty()) {
                secureDeleteEngine?.purgeMediaStoreTraces(foundPaths)
            }

            rootManager.appendLog(
                LogType.CLEANED,
                "${cat.title}: $actuallyDeleted berkas dibersihkan (${FormatUtils.formatBytes(actuallyFreed)})"
            )
            onProgress?.invoke((index + 1).toFloat() / totalSteps.toFloat(), "Selesai membersihkan ${cat.title}")
        }

        val availAfter = secureDeleteEngine?.getAvailableDataBytes() ?: 0L
        val statGain = (availAfter - availBefore).coerceAtLeast(0L)
        if (totalFiles > 0 && statGain > totalFreed) {
            totalFreed = statGain
        }

        val summary = "Ruang dibebaskan: ${FormatUtils.formatBytes(totalFreed)} ($totalFiles berkas)"
        rootManager.appendLog(LogType.CLEANED, summary)
        return CleanSummaryResult(totalFreed, totalFiles, totalErrors, summary)
    }

    suspend fun scanLegacyCategories(
        currentItems: List<LegacyCategoryItem>,
        customExclusions: List<String>,
        onProgress: ((Float, String) -> Unit)? = null
    ): List<LegacyCategoryItem> {
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            return currentItems
        }
        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val updated = mutableListOf<LegacyCategoryItem>()
        val totalSteps = currentItems.size.coerceAtLeast(1)

        currentItems.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(index.toFloat() / totalSteps.toFloat(), "Menganalisis ${item.title}...")
            var count = 0
            var bytes = 0L

            for (rawPath in item.paths) {
                if (!PathValidator.isSafeDirectoryTarget(rawPath, customExclusions)) continue
                val safePath = PathValidator.sanitizeShellPath(rawPath)
                val cmd = "if [ -d '$safePath' ]; then find '$safePath' -mindepth 1 -type f $exclusions -exec du -sk {} + 2>/dev/null; fi"
                val res = rootManager.execute(
                    command = cmd,
                    userApproved = true,
                    timeoutMs = 30_000L,
                    logCommand = true,
                    logStderrAsError = false
                )
                res.stdout.forEach { line ->
                    parseDuLine(line)?.let { (kb, filePath) ->
                        if (!isSelfOrProtected(filePath, customExclusions)) {
                            count++
                            bytes += kb * 1024L
                        }
                    }
                }
            }

            rootManager.appendLog(
                LogType.FOUND,
                "Legacy [${item.title}]: $count berkas (${FormatUtils.formatBytes(bytes)})"
            )
            updated.add(
                item.copy(
                    fileCount = count,
                    sizeBytes = bytes,
                    isScanned = true
                )
            )
            onProgress?.invoke((index + 1).toFloat() / totalSteps.toFloat(), "Selesai menganalisis ${item.title}")
        }
        return updated
    }

    suspend fun cleanLegacyCategories(
        selectedItems: List<LegacyCategoryItem>,
        customExclusions: List<String>,
        userConfirmed: Boolean,
        secureShred: Boolean = true,
        onProgress: ((Float, String) -> Unit)? = null
    ): CleanSummaryResult {
        if (!userConfirmed) {
            val msg = "Otorisasi dibatalkan: konfirmasi pengguna diperlukan."
            rootManager.appendLog(LogType.ERROR, msg)
            return CleanSummaryResult(0L, 0, 1, msg)
        }
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            return CleanSummaryResult(0L, 0, 1, "Otorisasi root tidak aktif")
        }

        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val availBefore = secureDeleteEngine?.getAvailableDataBytes() ?: 0L
        var totalFreed = 0L
        var totalFiles = 0
        var totalErrors = 0
        val targets = selectedItems.filter { it.isSelected }
        val totalSteps = targets.size.coerceAtLeast(1)

        targets.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(index.toFloat() / totalSteps.toFloat(), "Menghapus ${item.title}...")
            val deletedPathsForMediaStore = mutableListOf<String>()

            for (rawPath in item.paths) {
                if (!PathValidator.isSafeDirectoryTarget(rawPath, customExclusions)) {
                    rootManager.appendLog(LogType.ERROR, "Direktori terproteksi dilewati: $rawPath")
                    continue
                }
                val safePath = PathValidator.sanitizeShellPath(rawPath)
                val scanCmd = "if [ -d '$safePath' ]; then find '$safePath' -mindepth 1 -type f $exclusions -exec du -sk {} + 2>/dev/null; fi"
                val preRes = rootManager.execute(
                    command = scanCmd,
                    userApproved = true,
                    timeoutMs = 30_000L,
                    logCommand = false,
                    logStderrAsError = false
                )
                var preBytes = 0L
                var preCount = 0
                preRes.stdout.forEach { line ->
                    parseDuLine(line)?.let { (kb, p) ->
                        if (!isSelfOrProtected(p, customExclusions)) {
                            preCount++
                            preBytes += kb * 1024L
                            if (deletedPathsForMediaStore.size < 250) deletedPathsForMediaStore.add(p)
                        }
                    }
                }
                if (preCount == 0 && item.fileCount == 0) continue

                val delCmd = if (secureShred && secureDeleteEngine != null) {
                    secureDeleteEngine.buildRootDeleteDirectoryFilesCmd(safePath, exclusions, secureShred = true)
                } else {
                    "if [ -d '$safePath' ]; then find '$safePath' -mindepth 1 -type f $exclusions -exec rm -f {} + 2>&1; find '$safePath' -mindepth 1 -type d -empty $exclusions -exec rmdir {} + 2>/dev/null || true; sync; fi"
                }
                val delRes = rootManager.execute(
                    command = delCmd,
                    userApproved = true,
                    timeoutMs = 90_000L,
                    logCommand = true,
                    logStderrAsError = true
                )

                val postRes = rootManager.execute(
                    command = scanCmd,
                    userApproved = true,
                    timeoutMs = 25_000L,
                    logCommand = false,
                    logStderrAsError = false
                )
                var postBytes = 0L
                var postCount = 0
                postRes.stdout.forEach { line ->
                    parseDuLine(line)?.let { (kb, p) ->
                        if (!isSelfOrProtected(p, customExclusions)) {
                            postCount++
                            postBytes += kb * 1024L
                        }
                    }
                }

                val effectivePreBytes = if (preBytes > 0L) preBytes else item.sizeBytes
                val effectivePreCount = if (preCount > 0) preCount else item.fileCount
                val deleted = (effectivePreCount - postCount).coerceAtLeast(0)
                val freed = if (deleted > 0 && effectivePreBytes > postBytes) {
                    (effectivePreBytes - postBytes).coerceAtLeast(0L)
                } else if (deleted > 0 && item.sizeBytes > 0L) {
                    item.sizeBytes
                } else {
                    0L
                }
                totalFreed += freed
                totalFiles += deleted
                if (!delRes.success && delRes.stderr.isNotEmpty()) {
                    totalErrors += delRes.stderr.size
                }

                rootManager.appendLog(
                    LogType.CLEANED,
                    "Legacy [${item.title}]: $deleted berkas dihapus (${FormatUtils.formatBytes(freed)})"
                )
            }
            if (deletedPathsForMediaStore.isNotEmpty()) {
                secureDeleteEngine?.purgeMediaStoreTraces(deletedPathsForMediaStore)
            }
            onProgress?.invoke((index + 1).toFloat() / totalSteps.toFloat(), "Selesai menghapus ${item.title}")
        }

        val availAfter = secureDeleteEngine?.getAvailableDataBytes() ?: 0L
        val statGain = (availAfter - availBefore).coerceAtLeast(0L)
        if (totalFiles > 0 && statGain > totalFreed) {
            totalFreed = statGain
        }

        val summary = "Legacy Cleaner selesai: $totalFiles berkas (${FormatUtils.formatBytes(totalFreed)})"
        rootManager.appendLog(LogType.CLEANED, summary)
        return CleanSummaryResult(totalFreed, totalFiles, totalErrors, summary)
    }

    internal fun buildScanCommandForCategory(
        type: JunkCategoryType,
        exclusions: String,
        deepMode: Boolean
    ): String {
        val selfExcl = "! -path '*/$selfPackageName/*'"
        return when (type) {
            JunkCategoryType.APP_CACHE -> {
                val extraDeep = if (deepMode) " /data/user/0/*/code_cache" else ""
                "for d in /data/data/*/cache /data/data/*/code_cache /data/user/0/*/cache$extraDeep; do " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f $selfExcl $exclusions -exec du -sk {} + 2>/dev/null; done"
            }
            JunkCategoryType.ANDROID_DATA_CACHE -> {
                "for d in /sdcard/Android/data/*/cache /sdcard/Android/data/*/code_cache; do " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f $selfExcl $exclusions -exec du -sk {} + 2>/dev/null; done"
            }
            JunkCategoryType.SYSTEM_CACHE -> {
                val dirs = if (deepMode) {
                    "/data/local/tmp /data/anr /data/tombstones /data/cache /cache /data/system/dropbox"
                } else {
                    "/data/local/tmp /data/anr /data/tombstones /data/cache /cache"
                }
                "for d in $dirs; do [ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f $exclusions -exec du -sk {} + 2>/dev/null; done"
            }
            JunkCategoryType.TEMP_FILES -> {
                val nameFilter = if (deepMode) {
                    "\\( -iname '*.tmp' -o -iname '*.temp' -o -iname '*.bak' -o -iname '*.log' -o -iname '*.dmp' \\)"
                } else {
                    "\\( -iname '*.tmp' -o -iname '*.temp' -o -iname '*.bak' -o -iname '*.log' \\)"
                }
                "find /sdcard -mindepth 1 -type f $nameFilter $exclusions -exec du -sk {} + 2>/dev/null"
            }
            JunkCategoryType.THUMBNAIL_CACHE -> {
                "for d in /sdcard/DCIM/.thumbnails /sdcard/Pictures/.thumbnails /sdcard/Movies/.thumbnails; do " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f $exclusions -exec du -sk {} + 2>/dev/null; done"
            }
            JunkCategoryType.TRASH_FOLDERS -> {
                "find /sdcard -mindepth 1 -maxdepth 4 -type d \\( -name '.Trash*' -o -name '.RecycleBin*' -o -name '.opporecycle*' \\) $exclusions -exec find {} -mindepth 1 -type f $exclusions -exec du -sk {} + \\; 2>/dev/null"
            }
            JunkCategoryType.RESIDUAL_TEMP -> {
                "for d in /data/data/*/app_webview/Default/Cache /data/data/*/app_webview/Service\\ Worker/CacheStorage /sdcard/Android/obb; do " +
                    "if [ -d \"\$d\" ]; then " +
                    "case \"\$d\" in */obb) find \"\$d\" -mindepth 1 -type f \\( -iname '*.tmp' -o -iname '*.temp' \\) $exclusions -exec du -sk {} + 2>/dev/null ;; " +
                    "*) find \"\$d\" -mindepth 1 -type f $selfExcl $exclusions -exec du -sk {} + 2>/dev/null ;; esac; fi; done"
            }
        }
    }

    private fun buildDeleteCommandForCategory(
        type: JunkCategoryType,
        exclusions: String,
        deepMode: Boolean,
        secureShred: Boolean = false
    ): String {
        val selfExcl = "! -path '*/$selfPackageName/*'"
        fun wipeFilesAction(extraFilter: String = ""): String {
            return if (secureShred && secureDeleteEngine != null) {
                "$extraFilter $exclusions ${SecureDeleteEngine.SHELL_WIPE_EXEC}"
            } else {
                "$extraFilter $exclusions -exec rm -f {} + 2>&1"
            }
        }

        return when (type) {
            JunkCategoryType.APP_CACHE -> {
                val extraDeep = if (deepMode) " /data/user/0/*/code_cache" else ""
                "for d in /data/data/*/cache /data/data/*/code_cache /data/user/0/*/cache$extraDeep; do " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f ${wipeFilesAction(selfExcl)}; " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type d -empty $selfExcl $exclusions -exec rmdir {} + 2>/dev/null || true; done; sync"
            }
            JunkCategoryType.ANDROID_DATA_CACHE -> {
                "for d in /sdcard/Android/data/*/cache /sdcard/Android/data/*/code_cache; do " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f ${wipeFilesAction(selfExcl)}; " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type d -empty $selfExcl $exclusions -exec rmdir {} + 2>/dev/null || true; done; sync"
            }
            JunkCategoryType.SYSTEM_CACHE -> {
                val dirs = if (deepMode) {
                    "/data/local/tmp /data/anr /data/tombstones /data/cache /cache /data/system/dropbox"
                } else {
                    "/data/local/tmp /data/anr /data/tombstones /data/cache /cache"
                }
                "for d in $dirs; do [ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f ${wipeFilesAction()} || true; done; sync"
            }
            JunkCategoryType.TEMP_FILES -> {
                val nameFilter = if (deepMode) {
                    "\\( -iname '*.tmp' -o -iname '*.temp' -o -iname '*.bak' -o -iname '*.log' -o -iname '*.dmp' \\)"
                } else {
                    "\\( -iname '*.tmp' -o -iname '*.temp' -o -iname '*.bak' -o -iname '*.log' \\)"
                }
                "find /sdcard -mindepth 1 -type f ${wipeFilesAction(nameFilter)}; sync"
            }
            JunkCategoryType.THUMBNAIL_CACHE -> {
                "for d in /sdcard/DCIM/.thumbnails /sdcard/Pictures/.thumbnails /sdcard/Movies/.thumbnails; do " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f ${wipeFilesAction()}; done; sync"
            }
            JunkCategoryType.TRASH_FOLDERS -> {
                val innerExec = if (secureShred && secureDeleteEngine != null) {
                    SecureDeleteEngine.SHELL_WIPE_EXEC
                } else {
                    "-exec rm -f {} + 2>&1"
                }
                "find /sdcard -mindepth 1 -maxdepth 4 -type d \\( -name '.Trash*' -o -name '.RecycleBin*' -o -name '.opporecycle*' \\) $exclusions -exec find {} -mindepth 1 -type f $exclusions $innerExec \\; ; sync"
            }
            JunkCategoryType.RESIDUAL_TEMP -> {
                "for d in /data/data/*/app_webview/Default/Cache /data/data/*/app_webview/Service\\ Worker/CacheStorage /sdcard/Android/obb; do " +
                    "if [ -d \"\$d\" ]; then " +
                    "case \"\$d\" in */obb) find \"\$d\" -mindepth 1 -type f ${wipeFilesAction("\\( -iname '*.tmp' -o -iname '*.temp' \\)")} ;; " +
                    "*) find \"\$d\" -mindepth 1 -type f ${wipeFilesAction(selfExcl)} ;; esac; fi; done; sync"
            }
        }
    }

    internal fun parseDuLine(line: String): Pair<Long, String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(Regex("\\s+"), limit = 2)
        if (parts.size < 2) return null
        val kb = parts[0].toLongOrNull() ?: return null
        val path = parts[1].trim()
        if (!path.startsWith("/")) return null
        return Pair(kb, path)
    }

    internal fun isSelfOrProtected(path: String, customExclusions: List<String>): Boolean {
        return !PathValidator.isSafeFileDeletionTarget(path, selfPackageName, customExclusions)
    }
}
