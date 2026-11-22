package com.example.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.AppCacheEntry
import com.example.model.DuplicateGroup
import com.example.model.FormatUtils
import com.example.model.LargeFileEntry
import com.example.model.LogType
import com.example.model.OldApkEntry
import com.example.model.OrphanedResidueItem
import com.example.model.RootStatus
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class AdvancedScannerEngine(
    private val context: Context,
    private val rootManager: RootManager,
    private val secureDeleteEngine: SecureDeleteEngine? = null
) {
    private val selfPackage = context.packageName

    suspend fun scanPerAppCache(customExclusions: List<String>): List<AppCacheEntry> {
        if (rootManager.checkRoot() != RootStatus.GRANTED) return emptyList()
        rootManager.appendLog(LogType.INFO, "Memindai peringkat cache per aplikasi...")

        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val cmd = "for d in /data/user/0/*/cache /data/user/0/*/code_cache /sdcard/Android/data/*/cache; do " +
            "[ -d \"\$d\" ] && du -sk \"\$d\" 2>/dev/null; done"
        val res = rootManager.execute(
            command = cmd,
            userApproved = true,
            timeoutMs = 30_000L,
            logCommand = true,
            logStderrAsError = false
        )

        val internalMap = mutableMapOf<String, Long>()
        val externalMap = mutableMapOf<String, Long>()

        res.stdout.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                val kb = parts[0].toLongOrNull() ?: return@forEach
                val path = parts[1].trim()
                val bytes = kb * 1024L
                if (bytes <= 0L) return@forEach

                when {
                    path.startsWith("/data/user/0/") -> {
                        val pkg = path.removePrefix("/data/user/0/").substringBefore("/")
                        if (pkg != selfPackage && !isPackageExcluded(pkg, customExclusions)) {
                            internalMap[pkg] = (internalMap[pkg] ?: 0L) + bytes
                        }
                    }
                    path.startsWith("/sdcard/Android/data/") -> {
                        val pkg = path.removePrefix("/sdcard/Android/data/").substringBefore("/")
                        if (pkg != selfPackage && !isPackageExcluded(pkg, customExclusions)) {
                            externalMap[pkg] = (externalMap[pkg] ?: 0L) + bytes
                        }
                    }
                }
            }
        }

        val pm = context.packageManager
        val allPackages = (internalMap.keys + externalMap.keys).distinct()
        val entries = allPackages.mapNotNull { pkg ->
            val intBytes = internalMap[pkg] ?: 0L
            val extBytes = externalMap[pkg] ?: 0L
            val total = intBytes + extBytes
            if (total < 1024L) return@mapNotNull null

            val label = try {
                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg
            }

            AppCacheEntry(
                packageName = pkg,
                appLabel = label,
                internalCacheBytes = intBytes,
                externalCacheBytes = extBytes,
                totalCacheBytes = total
            )
        }.sortedByDescending { it.totalCacheBytes }

        rootManager.appendLog(LogType.FOUND, "Terdeteksi ${entries.size} aplikasi dengan cache aktif.")
        return entries
    }

    suspend fun cleanSelectedAppCaches(
        entries: List<AppCacheEntry>,
        customExclusions: List<String>
    ): CleanSummaryResult {
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            return CleanSummaryResult(0L, 0, 1, "Otorisasi root tidak aktif")
        }
        val selected = entries.filter { it.isSelected }
        if (selected.isEmpty()) return CleanSummaryResult(0L, 0, 0, "Tidak ada aplikasi dipilih")

        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        var freedBytes = 0L
        var filesDeleted = 0
        var errors = 0

        for (app in selected) {
            currentCoroutineContext().ensureActive()
            val pkg = app.packageName
            if (pkg == selfPackage || !pkg.matches(Regex("^[a-zA-Z0-9_.]+$"))) continue

            val safePkg = PathValidator.sanitizeShellPath(pkg)
            val targets = listOf(
                "/data/user/0/$safePkg/cache",
                "/data/user/0/$safePkg/code_cache",
                "/data/data/$safePkg/cache",
                "/sdcard/Android/data/$safePkg/cache"
            )
            val cmd = targets.joinToString("; ") { dir ->
                "if [ -d '$dir' ]; then find '$dir' -mindepth 1 -type f $exclusions -exec rm -f {} + 2>&1; fi"
            }
            val delRes = rootManager.execute(
                command = cmd,
                userApproved = true,
                timeoutMs = 20_000L,
                logCommand = true,
                logStderrAsError = true
            )
            if (!delRes.success && delRes.stderr.isNotEmpty()) errors++
            freedBytes += app.totalCacheBytes
            filesDeleted++
            rootManager.appendLog(
                LogType.CLEANED,
                "Cache ${app.appLabel} (${app.packageName}) dibersihkan (${FormatUtils.formatBytes(app.totalCacheBytes)})"
            )
        }

        val msg = "Cache ${selected.size} aplikasi dibersihkan (${FormatUtils.formatBytes(freedBytes)})"
        return CleanSummaryResult(freedBytes, filesDeleted, errors, msg)
    }

    suspend fun scanOrphanedResidue(customExclusions: List<String>): List<OrphanedResidueItem> {
        if (rootManager.checkRoot() != RootStatus.GRANTED) return emptyList()
        rootManager.appendLog(LogType.INFO, "Memindai residu direktori aplikasi yang telah dihapus...")

        val installedRes = rootManager.execute(
            command = "pm list packages 2>/dev/null",
            userApproved = true,
            timeoutMs = 15_000L,
            logCommand = true,
            logStderrAsError = false
        )
        val installedPackages = installedRes.stdout
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("package:")) trimmed.removePrefix("package:").trim() else null
            }
            .toSet()

        if (installedPackages.isEmpty()) {
            rootManager.appendLog(LogType.ERROR, "Gagal membaca daftar paket terinstal (pm list packages).")
            return emptyList()
        }

        val dirCmd = "for base in /sdcard/Android/data /sdcard/Android/obb; do " +
            "if [ -d \"\$base\" ]; then for d in \"\$base\"/*; do [ -d \"\$d\" ] && du -sk \"\$d\" 2>/dev/null; done; fi; done"
        val dirRes = rootManager.execute(
            command = dirCmd,
            userApproved = true,
            timeoutMs = 25_000L,
            logCommand = true,
            logStderrAsError = false
        )

        val orphans = mutableListOf<OrphanedResidueItem>()
        dirRes.stdout.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                val kb = parts[0].toLongOrNull() ?: return@forEach
                val path = parts[1].trim()
                val folderName = File(path).name
                if (folderName.contains(".") &&
                    !installedPackages.contains(folderName) &&
                    folderName != selfPackage &&
                    !folderName.startsWith("com.android.") &&
                    !folderName.startsWith("com.google.android.") &&
                    PathValidator.isSafeDirectoryTarget(path, customExclusions)
                ) {
                    val countRes = rootManager.execute(
                        command = "find '${PathValidator.sanitizeShellPath(path)}' -type f 2>/dev/null | wc -l",
                        userApproved = true,
                        timeoutMs = 5_000L,
                        logCommand = false,
                        logStderrAsError = false
                    )
                    val fileCount = countRes.stdout.firstOrNull()?.trim()?.toIntOrNull() ?: 0
                    orphans.add(
                        OrphanedResidueItem(
                            packageName = folderName,
                            path = path,
                            sizeBytes = kb * 1024L,
                            fileCount = fileCount
                        )
                    )
                }
            }
        }

        val sorted = orphans.sortedByDescending { it.sizeBytes }
        rootManager.appendLog(LogType.FOUND, "Terdeteksi ${sorted.size} direktori residu aplikasi tidak terinstal.")
        return sorted
    }

    suspend fun cleanOrphanedResidue(
        items: List<OrphanedResidueItem>,
        customExclusions: List<String>,
        secureShred: Boolean = true
    ): CleanSummaryResult {
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            return CleanSummaryResult(0L, 0, 1, "Otorisasi root tidak aktif")
        }
        val selected = items.filter { it.isSelected }
        if (selected.isEmpty()) return CleanSummaryResult(0L, 0, 0, "Tidak ada residu dipilih")

        var totalFreed = 0L
        var totalFiles = 0
        var errors = 0
        for (item in selected) {
            currentCoroutineContext().ensureActive()
            if (!PathValidator.isSafeDirectoryTarget(item.path, customExclusions)) continue
            val safePath = PathValidator.sanitizeShellPath(item.path)
            val cmd = if (secureShred && secureDeleteEngine != null) {
                "${secureDeleteEngine.buildRootDeleteDirectoryFilesCmd(safePath, PathValidator.buildFindExclusionFlags(customExclusions, true), true)}; rm -rf '$safePath' 2>&1; sync"
            } else {
                "rm -rf '$safePath' 2>&1"
            }
            val res = rootManager.execute(
                command = cmd,
                userApproved = true,
                timeoutMs = 35_000L,
                logCommand = true,
                logStderrAsError = true
            )
            if (res.success) {
                totalFreed += item.sizeBytes
                totalFiles += item.fileCount.coerceAtLeast(1)
                rootManager.appendLog(
                    LogType.CLEANED,
                    "Residu dihapus: ${item.path} (${FormatUtils.formatBytes(item.sizeBytes)})"
                )
            } else {
                errors++
            }
        }
        return CleanSummaryResult(
            totalFreed,
            totalFiles,
            errors,
            "Residu dibersihkan: ${FormatUtils.formatBytes(totalFreed)}"
        )
    }

    suspend fun scanLargeFiles(
        minSizeMb: Int = 50,
        customExclusions: List<String>
    ): List<LargeFileEntry> {
        if (rootManager.checkRoot() != RootStatus.GRANTED) return emptyList()
        rootManager.appendLog(LogType.INFO, "Memindai berkas berukuran >${minSizeMb} MB pada /sdcard...")

        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val cmd = "find /sdcard -mindepth 1 -type f -size +${minSizeMb}M ! -path '/sdcard/Android/obb/*' $exclusions -exec du -sk {} + 2>/dev/null"
        val res = rootManager.execute(
            command = cmd,
            userApproved = true,
            timeoutMs = 30_000L,
            logCommand = true,
            logStderrAsError = false
        )

        val list = mutableListOf<LargeFileEntry>()
        res.stdout.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                val kb = parts[0].toLongOrNull() ?: return@forEach
                val path = parts[1].trim()
                if (PathValidator.isSafeFileDeletionTarget(path, selfPackage, customExclusions)) {
                    list.add(
                        LargeFileEntry(
                            path = path,
                            fileName = File(path).name,
                            sizeBytes = kb * 1024L
                        )
                    )
                }
            }
        }
        val sorted = list.sortedByDescending { it.sizeBytes }
        rootManager.appendLog(LogType.FOUND, "Terdeteksi ${sorted.size} berkas besar (>${minSizeMb} MB).")
        return sorted
    }

    suspend fun scanDuplicateFiles(customExclusions: List<String>): List<DuplicateGroup> {
        if (rootManager.checkRoot() != RootStatus.GRANTED) return emptyList()
        rootManager.appendLog(LogType.INFO, "Memindai berkas duplikat (verifikasi ukuran & SHA-256)...")

        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        // Scan files >= 100 KB on /sdcard (excluding Android/data & Android/obb) for fast, practical duplicate detection
        val sizeCmd = "find /sdcard -mindepth 1 -type f -size +100k ! -path '/sdcard/Android/*' $exclusions -exec stat -c '%s|%n' {} + 2>/dev/null"
        val sizeRes = rootManager.execute(
            command = sizeCmd,
            userApproved = true,
            timeoutMs = 35_000L,
            logCommand = true,
            logStderrAsError = false
        )

        val bySize = mutableMapOf<Long, MutableList<String>>()
        sizeRes.stdout.forEach { line ->
            val idx = line.indexOf('|')
            if (idx > 0) {
                val bytes = line.substring(0, idx).trim().toLongOrNull() ?: return@forEach
                val path = line.substring(idx + 1).trim()
                if (bytes > 0L && PathValidator.isSafeFileDeletionTarget(path, selfPackage, customExclusions)) {
                    bySize.getOrPut(bytes) { mutableListOf() }.add(path)
                }
            }
        }

        val candidates = bySize.filter { it.value.size >= 2 }
        if (candidates.isEmpty()) {
            rootManager.appendLog(LogType.INFO, "Tidak ada kandidat berkas duplikat ditemukan.")
            return emptyList()
        }

        val groups = mutableListOf<DuplicateGroup>()
        for ((sizeBytes, paths) in candidates.entries.take(60)) {
            currentCoroutineContext().ensureActive()
            val quoted = paths.joinToString(" ") { "'${PathValidator.sanitizeShellPath(it)}'" }
            val hashRes = rootManager.execute(
                command = "sha256sum $quoted 2>/dev/null || md5sum $quoted 2>/dev/null",
                userApproved = true,
                timeoutMs = 20_000L,
                logCommand = false,
                logStderrAsError = false
            )
            val byHash = mutableMapOf<String, MutableList<String>>()
            hashRes.stdout.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) {
                    val hash = parts[0].trim()
                    val path = parts[1].trim()
                    byHash.getOrPut(hash) { mutableListOf() }.add(path)
                }
            }
            byHash.forEach { (hash, dupPaths) ->
                if (dupPaths.size >= 2) {
                    groups.add(
                        DuplicateGroup(
                            hash = hash,
                            sizeBytes = sizeBytes,
                            filePaths = dupPaths.sorted()
                        )
                    )
                }
            }
        }

        val sorted = groups.sortedByDescending { it.sizeBytes * (it.filePaths.size - 1) }
        rootManager.appendLog(LogType.FOUND, "Teridentifikasi ${sorted.size} grup duplikat terverifikasi hash.")
        return sorted
    }

    suspend fun scanOldApks(customExclusions: List<String>): List<OldApkEntry> = withContext(Dispatchers.IO) {
        if (rootManager.checkRoot() != RootStatus.GRANTED) return@withContext emptyList()
        rootManager.appendLog(LogType.INFO, "Memindai paket instalasi APK sisa pada penyimpanan...")

        val exclusions = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val cmd = "find /sdcard -mindepth 1 -type f -iname '*.apk' ! -path '/sdcard/Android/*' $exclusions -exec du -sk {} + 2>/dev/null"
        val res = rootManager.execute(
            command = cmd,
            userApproved = true,
            timeoutMs = 25_000L,
            logCommand = true,
            logStderrAsError = false
        )

        val pm = context.packageManager
        val installedSet = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0).map { it.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        val entries = mutableListOf<OldApkEntry>()
        res.stdout.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                val kb = parts[0].toLongOrNull() ?: return@forEach
                val path = parts[1].trim()
                if (!PathValidator.isSafeFileDeletionTarget(path, selfPackage, customExclusions)) return@forEach

                val archiveInfo = try {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(path, 0)
                } catch (_: Exception) {
                    null
                }
                val pkgName = archiveInfo?.packageName
                val verName = archiveInfo?.versionName
                val installed = pkgName != null && installedSet.contains(pkgName)
                val exactBytes = File(path).let { if (it.exists() && it.canRead()) it.length() else kb * 1024L }

                entries.add(
                    OldApkEntry(
                        path = path,
                        fileName = File(path).name,
                        packageName = pkgName,
                        versionName = verName,
                        sizeBytes = exactBytes,
                        isInstalled = installed
                    )
                )
            }
        }
        val sorted = entries.sortedByDescending { it.sizeBytes }
        rootManager.appendLog(LogType.FOUND, "Terdeteksi ${sorted.size} berkas installer APK.")
        sorted
    }

    suspend fun deleteValidatedPaths(
        paths: List<String>,
        expectedBytes: Long,
        customExclusions: List<String>,
        operationLabel: String,
        secureShred: Boolean = true
    ): CleanSummaryResult {
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            return CleanSummaryResult(0L, 0, 1, "Otorisasi root tidak aktif")
        }
        val safeTargets = paths.filter {
            PathValidator.isSafeFileDeletionTarget(it, selfPackage, customExclusions)
        }
        if (safeTargets.isEmpty()) {
            return CleanSummaryResult(0L, 0, 0, "Tidak ada target valid untuk dihapus")
        }

        val (deletedCount, errors) = if (secureDeleteEngine != null) {
            secureDeleteEngine.secureDeleteRootFileList(safeTargets, customExclusions, secureShred)
        } else {
            var del = 0
            var err = 0
            for (chunk in safeTargets.chunked(25)) {
                currentCoroutineContext().ensureActive()
                val quoted = chunk.joinToString(" ") { "'${PathValidator.sanitizeShellPath(it)}'" }
                val res = rootManager.execute(
                    command = "rm -f $quoted 2>&1",
                    userApproved = true,
                    timeoutMs = 25_000L,
                    logCommand = true,
                    logStderrAsError = true
                )
                if (res.success) del += chunk.size else err++
            }
            del to err
        }
        val msg = "$operationLabel: $deletedCount berkas dihapus (${FormatUtils.formatBytes(expectedBytes)})"
        rootManager.appendLog(LogType.CLEANED, msg)
        return CleanSummaryResult(expectedBytes, deletedCount, errors, msg)
    }

    private fun isPackageExcluded(pkg: String, customExclusions: List<String>): Boolean {
        return customExclusions.any { ex -> ex.isNotBlank() && pkg.contains(ex.trim(), ignoreCase = true) }
    }
}
