package com.example.engine

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.Settings
import com.example.model.AppCacheEntry
import com.example.model.FormatUtils
import com.example.model.JunkCategoryScanResult
import com.example.model.JunkCategoryType
import com.example.model.LargeFileEntry
import com.example.model.LogType
import com.example.model.OldApkEntry
import com.example.model.SafFolderItem
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SafFileEntry(
    val uri: Uri,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
)

class NonRootEngine(
    private val context: Context,
    private val rootManager: RootManager,
    private val secureDeleteEngine: SecureDeleteEngine? = null
) {

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun buildUsageAccessIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getSafFolderDisplayName(uriString: String): String {
        return try {
            val treeUri = Uri.parse(uriString)
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            docId.substringAfter(':').ifBlank { docId }
        } catch (_: Exception) {
            uriString
        }
    }

    suspend fun scanSafTreeFiles(
        treeUriString: String,
        customExclusions: List<String>,
        maxFiles: Int = 1500
    ): List<SafFileEntry> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SafFileEntry>()
        val treeUri = try {
            Uri.parse(treeUriString)
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val queue = ArrayDeque<Pair<String, String>>()
        queue.add(rootDocId to "")

        while (queue.isNotEmpty() && results.size < maxFiles) {
            val (docId, relDir) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )
            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext() && results.size < maxFiles) {
                        val childId = if (idIdx >= 0) cursor.getString(idIdx) ?: continue else continue
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                        val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx).coerceAtLeast(0L) else 0L
                        val mod = if (modIdx >= 0) cursor.getLong(modIdx).coerceAtLeast(0L) else 0L

                        if (isProtectedNameOrPath(name, relDir, customExclusions)) continue

                        val relPath = if (relDir.isEmpty()) name else "$relDir/$name"
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(childId to relPath)
                        } else {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            results.add(
                                SafFileEntry(
                                    uri = docUri,
                                    displayName = name,
                                    relativePath = relPath,
                                    mimeType = mime,
                                    sizeBytes = size,
                                    lastModifiedMillis = mod
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        results
    }

    suspend fun inspectSafFolders(
        uriStrings: List<String>,
        customExclusions: List<String>
    ): List<SafFolderItem> = withContext(Dispatchers.IO) {
        uriStrings.map { uriStr ->
            val files = scanSafTreeFiles(uriStr, customExclusions)
            val junkBytes = files.filter { isJunkCandidate(it) }.sumOf { it.sizeBytes }
            val totalBytes = files.sumOf { it.sizeBytes }
            SafFolderItem(
                uriString = uriStr,
                displayName = getSafFolderDisplayName(uriStr),
                fileCount = files.size,
                junkBytes = junkBytes,
                totalBytes = totalBytes
            )
        }
    }

    suspend fun scanNonRootJunk(
        currentCategories: List<JunkCategoryScanResult>,
        grantedSafUris: List<String>,
        customExclusions: List<String>,
        onProgress: ((Float, String) -> Unit)? = null
    ): List<JunkCategoryScanResult> = withContext(Dispatchers.IO) {
        onProgress?.invoke(0.15f, "Memindai cache & staging internal (Non-Root)...")
        val ownCacheFiles = collectLocalFiles(
            listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir)
        )
        val ownCount = ownCacheFiles.size
        val ownBytes = ownCacheFiles.sumOf { it.length() }

        onProgress?.invoke(0.45f, "Memindai folder SAF terotorisasi...")
        val safFiles = mutableListOf<SafFileEntry>()
        grantedSafUris.forEach { uri ->
            safFiles.addAll(scanSafTreeFiles(uri, customExclusions))
        }

        val tempFiles = safFiles.filter { isTempFile(it.displayName) }
        val thumbFiles = safFiles.filter { isThumbFile(it) }
        val trashFiles = safFiles.filter { isTrashFile(it) }

        onProgress?.invoke(1.0f, "Pemindaian Non-Root selesai")
        currentCategories.map { cat ->
            when (cat.type) {
                JunkCategoryType.APP_CACHE -> cat.copy(
                    fileCount = ownCount,
                    sizeBytes = ownBytes,
                    sampleFiles = ownCacheFiles.take(3).map { it.absolutePath },
                    statusNote = "Non-Root: membersihkan cache internal & eksternal aplikasi ini"
                )
                JunkCategoryType.TEMP_FILES -> cat.copy(
                    fileCount = tempFiles.size,
                    sizeBytes = tempFiles.sumOf { it.sizeBytes },
                    sampleFiles = tempFiles.take(3).map { it.relativePath },
                    statusNote = if (grantedSafUris.isEmpty()) "Tambahkan izin folder SAF di Pengaturan/Cleaner" else "SAF: ${grantedSafUris.size} folder"
                )
                JunkCategoryType.THUMBNAIL_CACHE -> cat.copy(
                    fileCount = thumbFiles.size,
                    sizeBytes = thumbFiles.sumOf { it.sizeBytes },
                    sampleFiles = thumbFiles.take(3).map { it.relativePath },
                    statusNote = if (grantedSafUris.isEmpty()) "Memerlukan Root atau izin folder SAF" else "SAF aktif"
                )
                JunkCategoryType.TRASH_FOLDERS -> cat.copy(
                    fileCount = trashFiles.size,
                    sizeBytes = trashFiles.sumOf { it.sizeBytes },
                    sampleFiles = trashFiles.take(3).map { it.relativePath },
                    statusNote = if (grantedSafUris.isEmpty()) "Memerlukan Root atau izin folder SAF" else "SAF aktif"
                )
                else -> cat.copy(
                    fileCount = 0,
                    sizeBytes = 0L,
                    sampleFiles = emptyList(),
                    statusNote = "Direktori sistem terlindungi Android (memerlukan Root su -c)"
                )
            }
        }
    }

    suspend fun cleanNonRootJunk(
        categories: List<JunkCategoryScanResult>,
        grantedSafUris: List<String>,
        customExclusions: List<String>,
        secureShred: Boolean = true,
        onProgress: ((Float, String) -> Unit)? = null
    ): CleanSummaryResult = withContext(Dispatchers.IO) {
        var totalFreed = 0L
        var totalFiles = 0
        var errors = 0
        val selected = categories.filter { it.isSelected }
        val steps = selected.size.coerceAtLeast(1)

        val safFiles = mutableListOf<SafFileEntry>()
        if (grantedSafUris.isNotEmpty()) {
            grantedSafUris.forEach { uri ->
                safFiles.addAll(scanSafTreeFiles(uri, customExclusions))
            }
        }

        selected.forEachIndexed { idx, cat ->
            onProgress?.invoke(idx.toFloat() / steps.toFloat(), "Membersihkan ${cat.title} (Non-Root)...")
            when (cat.type) {
                JunkCategoryType.APP_CACHE -> {
                    val dirs = listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir)
                    val files = collectLocalFiles(dirs)
                    files.forEach { file ->
                        val len = file.length()
                        val deleted = if (secureShred && secureDeleteEngine != null) {
                            secureDeleteEngine.secureShredLocalFile(file)
                        } else {
                            file.delete()
                        }
                        if (deleted) {
                            totalFreed += len
                            totalFiles++
                        }
                    }
                    rootManager.appendLog(LogType.CLEANED, "Non-Root [Cache App]: $totalFiles berkas (${FormatUtils.formatBytes(totalFreed)})")
                }
                JunkCategoryType.TEMP_FILES,
                JunkCategoryType.THUMBNAIL_CACHE,
                JunkCategoryType.TRASH_FOLDERS -> {
                    val targets = when (cat.type) {
                        JunkCategoryType.TEMP_FILES -> safFiles.filter { isTempFile(it.displayName) }
                        JunkCategoryType.THUMBNAIL_CACHE -> safFiles.filter { isThumbFile(it) }
                        else -> safFiles.filter { isTrashFile(it) }
                    }
                    var catFreed = 0L
                    var catDeleted = 0
                    targets.forEach { item ->
                        try {
                            val deleted = if (secureShred && secureDeleteEngine != null) {
                                secureDeleteEngine.secureDeleteSafUri(item.uri)
                            } else {
                                DocumentsContract.deleteDocument(context.contentResolver, item.uri)
                            }
                            if (deleted) {
                                catFreed += item.sizeBytes
                                catDeleted++
                            }
                        } catch (_: Exception) {
                            errors++
                        }
                    }
                    totalFreed += catFreed
                    totalFiles += catDeleted
                    if (catDeleted > 0) {
                        rootManager.appendLog(
                            LogType.CLEANED,
                            "Non-Root SAF [${cat.title}]: $catDeleted berkas (${FormatUtils.formatBytes(catFreed)})"
                        )
                    }
                }
                else -> Unit
            }
            onProgress?.invoke((idx + 1).toFloat() / steps.toFloat(), "Selesai ${cat.title}")
        }

        val msg = "Non-Root selesai: $totalFiles berkas dibersihkan (${FormatUtils.formatBytes(totalFreed)})"
        rootManager.appendLog(LogType.INFO, msg)
        CleanSummaryResult(totalFreed, totalFiles, errors, msg)
    }

    suspend fun scanPerAppCachesViaUsageStats(): List<AppCacheEntry> = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) return@withContext emptyList()
        val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            ?: return@withContext emptyList()
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolved = pm.queryIntentActivities(launchIntent, 0)
        val packages = resolved.map { it.activityInfo.packageName }.distinct()
        val userHandle = Process.myUserHandle()
        val entries = mutableListOf<AppCacheEntry>()

        packages.forEach { pkg ->
            try {
                val stats = storageStatsManager.queryStatsForPackage(
                    StorageManager.UUID_DEFAULT,
                    pkg,
                    userHandle
                )
                val cacheBytes = stats.cacheBytes.coerceAtLeast(0L)
                if (cacheBytes > 64 * 1024L) {
                    val label = try {
                        val ai = pm.getApplicationInfo(pkg, 0)
                        pm.getApplicationLabel(ai).toString()
                    } catch (_: Exception) {
                        pkg
                    }
                    entries.add(
                        AppCacheEntry(
                            packageName = pkg,
                            appLabel = label,
                            internalCacheBytes = cacheBytes,
                            externalCacheBytes = 0L,
                            totalCacheBytes = cacheBytes,
                            isSelected = false
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
        entries.sortedByDescending { it.totalCacheBytes }
    }

    suspend fun scanSafLargeFilesAndApks(
        grantedSafUris: List<String>,
        customExclusions: List<String>
    ): Pair<List<LargeFileEntry>, List<OldApkEntry>> = withContext(Dispatchers.IO) {
        val largeFiles = mutableListOf<LargeFileEntry>()
        val apks = mutableListOf<OldApkEntry>()
        val pm = context.packageManager
        val thresholdBytes = 50L * 1024L * 1024L

        grantedSafUris.forEach { treeUri ->
            val files = scanSafTreeFiles(treeUri, customExclusions)
            files.forEach { file ->
                if (file.sizeBytes >= thresholdBytes) {
                    largeFiles.add(
                        LargeFileEntry(
                            path = file.uri.toString(),
                            fileName = "${file.displayName} (${file.relativePath})",
                            sizeBytes = file.sizeBytes,
                            isSelected = false
                        )
                    )
                }
                if (file.displayName.lowercase().endsWith(".apk")) {
                    val installed = isPackageInstalled(pm, file.displayName)
                    apks.add(
                        OldApkEntry(
                            path = file.uri.toString(),
                            fileName = file.displayName,
                            packageName = null,
                            versionName = "SAF Document",
                            sizeBytes = file.sizeBytes,
                            isInstalled = installed,
                            isSelected = false
                        )
                    )
                }
            }
        }
        Pair(largeFiles.sortedByDescending { it.sizeBytes }, apks.sortedByDescending { it.sizeBytes })
    }

    suspend fun deleteSafUris(uriStrings: List<String>, secureShred: Boolean = true): Pair<Long, Int> = withContext(Dispatchers.IO) {
        var freed = 0L
        var count = 0
        uriStrings.forEach { raw ->
            try {
                val uri = Uri.parse(raw)
                val size = queryDocumentSize(uri)
                val deleted = if (secureShred && secureDeleteEngine != null) {
                    secureDeleteEngine.secureDeleteSafUri(uri)
                } else {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                }
                if (deleted) {
                    freed += size
                    count++
                }
            } catch (_: Exception) {
            }
        }
        Pair(freed, count)
    }

    private fun queryDocumentSize(docUri: Uri): Long {
        return try {
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0).coerceAtLeast(0L) else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun collectLocalFiles(roots: List<File>): List<File> {
        val out = mutableListOf<File>()
        roots.forEach { root ->
            if (root.exists()) {
                root.walkTopDown().filter { it.isFile }.forEach { out.add(it) }
            }
        }
        return out
    }

    private fun isPackageInstalled(pm: PackageManager, name: String): Boolean {
        return try {
            val base = name.substringBeforeLast(".apk")
            pm.getPackageInfo(base, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isProtectedNameOrPath(name: String, relDir: String, customExclusions: List<String>): Boolean {
        val combined = "$relDir/$name".lowercase()
        if (combined.contains("kitsunebackup")) return true
        if (combined.contains("lineageos-18.1-arm64-a-vanilla.img.xz")) return true
        if (combined.contains("ultimatecleaner.log")) return true
        if (name.endsWith(".db", ignoreCase = true) ||
            name.endsWith(".db-wal", ignoreCase = true) ||
            name.endsWith(".db-shm", ignoreCase = true) ||
            name.contains("msgstore", ignoreCase = true)
        ) return true
        return customExclusions.any { it.isNotBlank() && combined.contains(it.trim().lowercase()) }
    }

    private fun isJunkCandidate(entry: SafFileEntry): Boolean {
        return isTempFile(entry.displayName) || isThumbFile(entry) || isTrashFile(entry)
    }

    private fun isTempFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".tmp") || lower.endsWith(".temp") ||
            lower.endsWith(".bak") || lower.endsWith(".log") || lower.endsWith(".dmp")
    }

    private fun isThumbFile(entry: SafFileEntry): Boolean {
        val lower = entry.relativePath.lowercase()
        return lower.contains(".thumbnails") || lower.contains("thumbdata")
    }

    private fun isTrashFile(entry: SafFileEntry): Boolean {
        val lower = entry.relativePath.lowercase()
        return lower.contains(".trash") || lower.contains(".recyclebin") || lower.contains(".opporecycle")
    }
}
