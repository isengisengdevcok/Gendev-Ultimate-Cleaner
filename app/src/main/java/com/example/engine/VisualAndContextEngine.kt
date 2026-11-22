package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.ContextRiskItem
import com.example.model.ContextRiskLevel
import com.example.model.FormatUtils
import com.example.model.LogType
import com.example.model.RootStatus
import com.example.model.VisualPhotoCandidate
import com.example.model.VisualSimilarityGroup
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

class VisualAndContextEngine(
    private val context: Context,
    private val rootManager: RootManager,
    private val nonRootEngine: NonRootEngine,
    private val secureDeleteEngine: SecureDeleteEngine? = null
) {

    suspend fun scanDownloadContextRisk(
        useRoot: Boolean,
        safUris: List<String>,
        customExclusions: List<String>
    ): List<ContextRiskItem> = withContext(Dispatchers.IO) {
        val nowSec = System.currentTimeMillis() / 1000L
        val items = mutableListOf<ContextRiskItem>()

        if (useRoot && rootManager.checkRoot() == RootStatus.GRANTED) {
            val excl = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
            val cmd = "if [ -d '/sdcard/Download' ]; then find '/sdcard/Download' -maxdepth 3 -type f $excl -exec stat -c '%s|%Y|%n' {} + 2>/dev/null; fi"
            val res = rootManager.execute(
                command = cmd,
                userApproved = true,
                timeoutMs = 25_000L,
                logCommand = true,
                logStderrAsError = false
            )
            res.stdout.take(250).forEach { line ->
                val parts = line.split("|", limit = 3)
                if (parts.size == 3) {
                    val size = parts[0].toLongOrNull() ?: 0L
                    val mtime = parts[1].toLongOrNull() ?: nowSec
                    val path = parts[2].trim()
                    if (PathValidator.isSafeFileDeletionTarget(path, context.packageName, customExclusions)) {
                        val ageDays = ((nowSec - mtime) / 86400L).toInt().coerceAtLeast(0)
                        val name = File(path).name
                        val (risk, reason) = classifyContextRisk(name, path, ageDays, size)
                        items.add(
                            ContextRiskItem(
                                path = path,
                                fileName = name,
                                sizeBytes = size,
                                ageDays = ageDays,
                                riskLevel = risk,
                                reason = reason,
                                isSelected = risk == ContextRiskLevel.SAFE_TO_CLEAN
                            )
                        )
                    }
                }
            }
        } else {
            val nowMs = System.currentTimeMillis()
            safUris.forEach { treeUri ->
                val safFiles = nonRootEngine.scanSafTreeFiles(treeUri, customExclusions, maxFiles = 200)
                safFiles.forEach { f ->
                    val ageDays = ((nowMs - f.lastModifiedMillis) / 86_400_000L).toInt().coerceAtLeast(0)
                    val (risk, reason) = classifyContextRisk(f.displayName, f.relativePath, ageDays, f.sizeBytes)
                    items.add(
                        ContextRiskItem(
                            path = f.relativePath,
                            uriString = f.uri.toString(),
                            fileName = f.displayName,
                            sizeBytes = f.sizeBytes,
                            ageDays = ageDays,
                            riskLevel = risk,
                            reason = reason,
                            isSelected = risk == ContextRiskLevel.SAFE_TO_CLEAN
                        )
                    )
                }
            }
        }

        items.sortedWith(compareBy<ContextRiskItem> { it.riskLevel.ordinal }.reversed().thenByDescending { it.sizeBytes })
    }

    suspend fun deleteContextItems(
        items: List<ContextRiskItem>,
        customExclusions: List<String>,
        secureShred: Boolean = true
    ): CleanSummaryResult = withContext(Dispatchers.IO) {
        val selected = items.filter { it.isSelected && it.riskLevel != ContextRiskLevel.PROTECTED }
        if (selected.isEmpty()) {
            return@withContext CleanSummaryResult(0L, 0, 0, "Tidak ada berkas aman terpilih.")
        }

        var freed = 0L
        var deleted = 0
        var errors = 0

        val safItems = selected.filter { it.uriString != null }
        if (safItems.isNotEmpty()) {
            val (f, c) = nonRootEngine.deleteSafUris(safItems.mapNotNull { it.uriString }, secureShred = secureShred)
            freed += f
            deleted += c
        }

        val rootItems = selected.filter { it.uriString == null }
        if (rootItems.isNotEmpty() && rootManager.checkRoot() == RootStatus.GRANTED) {
            if (secureDeleteEngine != null) {
                val paths = rootItems.map { it.path }
                val (del, err) = secureDeleteEngine.secureDeleteRootFileList(paths, customExclusions, secureShred)
                deleted += del
                errors += err
                if (del > 0) freed += rootItems.sumOf { it.sizeBytes }
            } else {
                rootItems.forEach { item ->
                    if (PathValidator.isSafeFileDeletionTarget(item.path, context.packageName, customExclusions)) {
                        val safe = PathValidator.sanitizeShellPath(item.path)
                        val res = rootManager.execute(
                            command = "rm -f '$safe'",
                            userApproved = true,
                            timeoutMs = 10_000L,
                            logCommand = true
                        )
                        if (res.success) {
                            freed += item.sizeBytes
                            deleted++
                        } else {
                            errors++
                        }
                    }
                }
            }
        }

        val msg = "Triage Konteks: $deleted berkas dihapus (${FormatUtils.formatBytes(freed)})"
        rootManager.appendLog(LogType.CLEANED, msg)
        CleanSummaryResult(freed, deleted, errors, msg)
    }

    suspend fun scanVisualSimilarityFromRoot(
        customExclusions: List<String>
    ): List<VisualSimilarityGroup> = withContext(Dispatchers.IO) {
        if (rootManager.checkRoot() != RootStatus.GRANTED) return@withContext emptyList()
        val excl = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val cmd = "for d in /sdcard/DCIM/Camera /sdcard/Pictures/Screenshots /sdcard/WhatsApp/Media/WhatsApp\\ Images; do " +
            "[ -d \"\$d\" ] && find \"\$d\" -maxdepth 2 -type f \\( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' \\) $excl -exec stat -c '%s|%n' {} + 2>/dev/null; done"
        val res = rootManager.execute(
            command = cmd,
            userApproved = true,
            timeoutMs = 25_000L,
            logCommand = true,
            logStderrAsError = false
        )
        val candidates = mutableListOf<VisualPhotoCandidate>()
        res.stdout.take(120).forEach { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size == 2) {
                val size = parts[0].toLongOrNull() ?: 0L
                val path = parts[1].trim()
                val f = File(path)
                if (f.canRead()) {
                    analyzeBitmapFromFile(f, size)?.let { candidates.add(it) }
                } else {
                    // Fallback: copy tiny stream via cat su -c for root-only paths if needed
                    analyzeBitmapViaRoot(path, size)?.let { candidates.add(it) }
                }
            }
        }
        groupVisualCandidates(candidates)
    }

    suspend fun scanVisualSimilarityFromUris(
        uris: List<Uri>
    ): List<VisualSimilarityGroup> = withContext(Dispatchers.IO) {
        val candidates = uris.take(100).mapNotNull { uri ->
            analyzeBitmapFromUri(uri)
        }
        groupVisualCandidates(candidates)
    }

    suspend fun deleteVisualCandidates(
        groups: List<VisualSimilarityGroup>,
        customExclusions: List<String>,
        secureShred: Boolean = true
    ): CleanSummaryResult = withContext(Dispatchers.IO) {
        val selected = groups.flatMap { it.candidates }.filter { it.isSelected }.distinctBy { it.uriString ?: it.path }
        if (selected.isEmpty()) {
            return@withContext CleanSummaryResult(0L, 0, 0, "Tidak ada foto terpilih.")
        }
        var freed = 0L
        var deleted = 0
        var errors = 0

        val safOnes = selected.filter { it.uriString != null }
        if (safOnes.isNotEmpty()) {
            safOnes.forEach { item ->
                try {
                    val uri = Uri.parse(item.uriString)
                    val ok = if (secureShred && secureDeleteEngine != null) {
                        secureDeleteEngine.secureDeleteSafUri(uri)
                    } else {
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    }
                    if (ok) {
                        freed += item.sizeBytes
                        deleted++
                    } else {
                        errors++
                    }
                } catch (_: Exception) {
                    errors++
                }
            }
        }

        val rootOnes = selected.filter { it.uriString == null }
        if (rootOnes.isNotEmpty() && rootManager.checkRoot() == RootStatus.GRANTED) {
            if (secureDeleteEngine != null) {
                val (del, err) = secureDeleteEngine.secureDeleteRootFileList(
                    paths = rootOnes.map { it.path },
                    customExclusions = customExclusions,
                    secureShred = secureShred
                )
                deleted += del
                errors += err
                if (del > 0) freed += rootOnes.sumOf { it.sizeBytes }
            } else {
                rootOnes.forEach { item ->
                    if (PathValidator.isSafeFileDeletionTarget(item.path, context.packageName, customExclusions)) {
                        val safe = PathValidator.sanitizeShellPath(item.path)
                        val res = rootManager.execute("rm -f '$safe'", userApproved = true, timeoutMs = 10_000L)
                        if (res.success) {
                            freed += item.sizeBytes
                            deleted++
                        } else {
                            errors++
                        }
                    }
                }
            }
        }

        val msg = "Triage Visual: $deleted foto dibersihkan (${FormatUtils.formatBytes(freed)})"
        rootManager.appendLog(LogType.CLEANED, msg)
        CleanSummaryResult(freed, deleted, errors, msg)
    }

    private fun groupVisualCandidates(candidates: List<VisualPhotoCandidate>): List<VisualSimilarityGroup> {
        if (candidates.isEmpty()) return emptyList()
        val groups = mutableListOf<VisualSimilarityGroup>()
        val used = mutableSetOf<String>()

        // 1. Visual similarity grouping via 64-bit dHash Hamming distance <= 8
        for (i in candidates.indices) {
            val anchor = candidates[i]
            val key = anchor.uriString ?: anchor.path
            if (used.contains(key)) continue
            val cluster = mutableListOf(anchor)
            for (j in (i + 1) until candidates.size) {
                val other = candidates[j]
                val otherKey = other.uriString ?: other.path
                if (used.contains(otherKey)) continue
                if (hammingDistance(anchor.dHash, other.dHash) <= 8) {
                    cluster.add(other)
                }
            }
            if (cluster.size >= 2) {
                // Keep the sharpest image unselected; select duplicates automatically
                val sorted = cluster.sortedByDescending { it.sharpnessScore }
                val marked = sorted.mapIndexed { idx, c ->
                    val k = c.uriString ?: c.path
                    used.add(k)
                    c.copy(isSelected = idx > 0)
                }
                groups.add(
                    VisualSimilarityGroup(
                        groupId = "sim_$i",
                        reason = "${marked.size} foto mirip secara visual (dHash >= 87% identik)",
                        candidates = marked
                    )
                )
            }
        }

        // 2. Blurry or screenshot candidates not already grouped
        val blurryOrShots = candidates.filter { c ->
            val k = c.uriString ?: c.path
            !used.contains(k) && (c.isBlurry || c.isScreenshot)
        }
        if (blurryOrShots.isNotEmpty()) {
            groups.add(
                VisualSimilarityGroup(
                    groupId = "blur_shot",
                    reason = "Terdeteksi ${blurryOrShots.size} foto buram / screenshot untuk ditinjau",
                    candidates = blurryOrShots.map { it.copy(isSelected = false) }
                )
            )
        }

        return groups
    }

    internal fun classifyContextRisk(
        fileName: String,
        path: String,
        ageDays: Int,
        sizeBytes: Long
    ): Pair<ContextRiskLevel, String> {
        val lower = fileName.lowercase()
        val pathLower = path.lowercase()
        val importantKeywords = listOf(
            "ktp", "sim", "npwp", "kk", "ijazah", "sertifikat", "skripsi", "kontrak",
            "invoice", "faktur", "tagihan", "pajak", "slip", "kwitansi", "struk",
            "backup", "kitsune", "lineage", "passport", "dokumen", "laporan"
        )
        if (importantKeywords.any { lower.contains(it) || pathLower.contains(it) }) {
            return ContextRiskLevel.PROTECTED to "Mengandung kata kunci dokumen penting / cadangan"
        }
        if (lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".xlsx") || lower.endsWith(".pptx")) {
            return if (ageDays > 180) {
                ContextRiskLevel.REVIEW to "Dokumen kerja/PDF berusia $ageDays hari"
            } else {
                ContextRiskLevel.PROTECTED to "Dokumen aktif (PDF/Office)"
            }
        }
        if (lower.endsWith(".apk")) {
            return if (ageDays >= 7) {
                ContextRiskLevel.SAFE_TO_CLEAN to "Berkas installer APK lama ($ageDays hari)"
            } else {
                ContextRiskLevel.REVIEW to "Installer APK baru ($ageDays hari)"
            }
        }
        if (lower.endsWith(".tmp") || lower.endsWith(".temp") || lower.endsWith(".log") || lower.endsWith(".bak") || lower.contains("(1)") || lower.contains("(2)")) {
            return ContextRiskLevel.SAFE_TO_CLEAN to "Berkas sementara / unduhan duplikat"
        }
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) {
            return if (ageDays > 60 && sizeBytes > 20 * 1024 * 1024L) {
                ContextRiskLevel.REVIEW to "Arsip besar lama ($ageDays hari)"
            } else {
                ContextRiskLevel.REVIEW to "Arsip terkompresi"
            }
        }
        return if (ageDays > 90 && (lower.startsWith("screenshot") || lower.startsWith("img_") || lower.startsWith("wa"))) {
            ContextRiskLevel.SAFE_TO_CLEAN to "Media/tangkapan layar lama ($ageDays hari)"
        } else {
            ContextRiskLevel.REVIEW to "Berkas umum (usia $ageDays hari)"
        }
    }

    private fun analyzeBitmapFromFile(file: File, sizeBytes: Long): VisualPhotoCandidate? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val sample = computeInSampleSize(opts.outWidth, opts.outHeight, 160)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            val dHash = computeDHash(bmp)
            val sharpness = computeLaplacianSharpness(bmp)
            bmp.recycle()
            val name = file.name
            val isShot = name.contains("screenshot", ignoreCase = true) || file.absolutePath.contains("Screenshots", ignoreCase = true)
            VisualPhotoCandidate(
                path = file.absolutePath,
                fileName = name,
                sizeBytes = sizeBytes,
                dHash = dHash,
                sharpnessScore = sharpness,
                isBlurry = sharpness < 38.0 && !isShot,
                isScreenshot = isShot
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun analyzeBitmapViaRoot(path: String, sizeBytes: Long): VisualPhotoCandidate? {
        return try {
            val tmp = File(context.cacheDir, "dhash_tmp_${abs(path.hashCode())}.img")
            val safePath = PathValidator.sanitizeShellPath(path)
            val safeTmp = PathValidator.sanitizeShellPath(tmp.absolutePath)
            val res = rootManager.execute(
                command = "cp '$safePath' '$safeTmp' && chmod 644 '$safeTmp'",
                userApproved = true,
                timeoutMs = 5_000L,
                logCommand = false,
                logStderrAsError = false
            )
            val candidate = if (res.success && tmp.exists()) {
                analyzeBitmapFromFile(tmp, sizeBytes)?.copy(path = path, fileName = File(path).name)
            } else null
            tmp.delete()
            candidate
        } catch (_: Exception) {
            null
        }
    }

    private fun analyzeBitmapFromUri(uri: Uri): VisualPhotoCandidate? {
        return try {
            val size = context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) to (c.getString(1) ?: uri.lastPathSegment ?: "foto.jpg") else 0L to "foto.jpg"
            } ?: (0L to (uri.lastPathSegment ?: "foto.jpg"))

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val sample = computeInSampleSize(opts.outWidth, opts.outHeight, 160)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val dHash = computeDHash(bmp)
            val sharpness = computeLaplacianSharpness(bmp)
            bmp.recycle()
            val name = size.second
            val isShot = name.contains("screenshot", ignoreCase = true)
            VisualPhotoCandidate(
                path = name,
                uriString = uri.toString(),
                fileName = name,
                sizeBytes = size.first,
                dHash = dHash,
                sharpnessScore = sharpness,
                isBlurry = sharpness < 38.0 && !isShot,
                isScreenshot = isShot
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, targetMax: Int): Int {
        var inSampleSize = 1
        while (width / inSampleSize > targetMax * 2 || height / inSampleSize > targetMax * 2) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    // 9x8 grayscale difference hash -> 64-bit perceptual signature
    internal fun computeDHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var hash = 0L
        var bitIndex = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(scaled.getPixel(x, y))
                val right = luminance(scaled.getPixel(x + 1, y))
                if (left > right) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }
        if (scaled != bitmap) scaled.recycle()
        return hash
    }

    private fun computeLaplacianSharpness(bitmap: Bitmap): Double {
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
        val gray = Array(64) { y -> IntArray(64) { x -> luminance(scaled.getPixel(x, y)) } }
        if (scaled != bitmap) scaled.recycle()

        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until 63) {
            for (x in 1 until 63) {
                val lap = 4 * gray[y][x] - gray[y - 1][x] - gray[y + 1][x] - gray[y][x - 1] - gray[y][x + 1]
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 100.0
        val mean = sum / count
        return (sumSq / count) - (mean * mean)
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    internal fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
