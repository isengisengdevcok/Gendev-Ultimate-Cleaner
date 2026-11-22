package com.example.engine

import android.content.Context
import android.os.StatFs
import com.example.data.CleaningRepository
import com.example.model.AppCacheEntry
import com.example.model.CacheExplosionApp
import com.example.model.FormatUtils
import com.example.model.LogType
import com.example.model.RootStatus
import com.example.model.StorageDeltaItem
import com.example.model.StorageDeltaReport
import com.example.model.StorageForecastReport
import com.example.model.StorageHistoryPoint
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class StorageForecasterEngine(
    private val context: Context,
    private val rootManager: RootManager,
    private val repository: CleaningRepository,
    private val nonRootEngine: NonRootEngine
) {

    suspend fun recordAndAnalyzeForecast(
        appCaches: List<AppCacheEntry>
    ): StorageForecastReport = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val stat = StatFs(context.filesDir.absolutePath)
        val totalBytes = stat.totalBytes
        val availBytes = stat.availableBytes
        val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)

        repository.appendStorageHistoryPoint(
            StorageHistoryPoint(
                timestampMillis = now,
                usedBytes = usedBytes,
                availableBytes = availBytes
            )
        )
        val history = repository.getStorageHistoryPoints()

        // Calculate weighted daily growth velocity from recorded points
        var dailyGrowthBytes = 0L
        if (history.size >= 2) {
            val first = history.first()
            val last = history.last()
            val elapsedDays = ((last.timestampMillis - first.timestampMillis).toDouble() / 86_400_000.0)
                .coerceAtLeast(1.0 / 24.0) // minimum 1 hour window to avoid division spikes
            val rawDelta = last.usedBytes - first.usedBytes
            dailyGrowthBytes = (rawDelta / elapsedDays).toLong()
        } else {
            // Single-point fallback: estimate pressure from current accumulated cache load
            val cacheSum = appCaches.sumOf { it.totalCacheBytes }
            dailyGrowthBytes = if (cacheSum > 0L) cacheSum / 3L else 0L
        }

        val estimatedDays = if (dailyGrowthBytes > 10L * 1024L * 1024L && availBytes > 0L) {
            (availBytes.toDouble() / dailyGrowthBytes.toDouble()).roundToInt().coerceIn(1, 999)
        } else {
            null
        }

        val headline = when {
            estimatedDays != null -> "Memori internal diperkirakan penuh dalam ±$estimatedDays hari"
            dailyGrowthBytes < -5L * 1024L * 1024L -> "Tren penyimpanan membaik (ruang bertambah bersih)"
            else -> "Pertumbuhan penyimpanan relatif stabil"
        }

        val detail = buildString {
            append("Ruang tersisa: ${FormatUtils.formatBytes(availBytes)} dari ${FormatUtils.formatBytes(totalBytes)}. ")
            if (dailyGrowthBytes > 0L) {
                append("Laju pertambahan: +${FormatUtils.formatBytes(dailyGrowthBytes)}/hari berdasarkan ${history.size} titik pantau.")
            } else {
                append("Dipantau melalui ${history.size} titik riwayat penyimpanan lokal.")
            }
        }

        // Cache-Explosion Radar
        val (prevTs, prevMap) = repository.getPreviousAppCacheSnapshot()
        val elapsedAppDays = if (prevTs > 0L) {
            ((now - prevTs).toDouble() / 86_400_000.0).coerceAtLeast(1.0 / 24.0)
        } else {
            1.0
        }

        val explosionApps = appCaches
            .mapNotNull { entry ->
                val prevBytes = prevMap[entry.packageName] ?: 0L
                val deltaPerDay = if (prevTs > 0L) {
                    ((entry.totalCacheBytes - prevBytes) / elapsedAppDays).toLong()
                } else {
                    0L
                }
                val isExploding = entry.totalCacheBytes >= 60L * 1024L * 1024L || deltaPerDay >= 20L * 1024L * 1024L
                if (!isExploding && appCaches.size > 5) return@mapNotNull null
                val severity = when {
                    entry.totalCacheBytes >= 250L * 1024L * 1024L || deltaPerDay >= 80L * 1024L * 1024L -> "Kritis"
                    entry.totalCacheBytes >= 100L * 1024L * 1024L || deltaPerDay >= 30L * 1024L * 1024L -> "Tinggi"
                    else -> "Normal"
                }
                CacheExplosionApp(
                    packageName = entry.packageName,
                    appLabel = entry.appLabel,
                    currentCacheBytes = entry.totalCacheBytes,
                    deltaBytesPerDay = deltaPerDay,
                    severityLabel = severity
                )
            }
            .sortedByDescending { it.currentCacheBytes + it.deltaBytesPerDay.coerceAtLeast(0L) }
            .take(8)

        if (appCaches.isNotEmpty()) {
            repository.saveAppCacheSnapshot(
                now,
                appCaches.associate { it.packageName to it.totalCacheBytes }
            )
        }

        StorageForecastReport(
            dailyGrowthBytes = dailyGrowthBytes,
            estimatedDaysUntilFull = estimatedDays,
            forecastHeadline = headline,
            forecastDetail = detail,
            historyPoints = history,
            explosionApps = explosionApps,
            analyzedAtMillis = now
        )
    }

    suspend fun saveStorageBaseline(
        useRoot: Boolean,
        safUris: List<String>,
        customExclusions: List<String>
    ): StorageDeltaReport = withContext(Dispatchers.IO) {
        val snapshotMap = collectDirectorySizeMap(useRoot, safUris, customExclusions)
        val now = System.currentTimeMillis()
        repository.saveBaselineDiffSnapshot(now, snapshotMap)
        rootManager.appendLog(LogType.INFO, "AI Delta Diff: patokan ukuran (${snapshotMap.size} direktori) disimpan.")
        StorageDeltaReport(
            baselineTimestampMillis = now,
            comparedTimestampMillis = now,
            totalDeltaBytes = 0L,
            items = emptyList(),
            statusMessage = "Titik patokan berhasil disimpan (${snapshotMap.size} folder terindeks). Bandingkan nanti untuk melacak perubahan."
        )
    }

    suspend fun compareWithBaseline(
        useRoot: Boolean,
        safUris: List<String>,
        customExclusions: List<String>
    ): StorageDeltaReport = withContext(Dispatchers.IO) {
        val (baseTs, baseMap) = repository.getBaselineDiffSnapshot()
        if (baseTs == 0L || baseMap.isEmpty()) {
            return@withContext saveStorageBaseline(useRoot, safUris, customExclusions)
        }
        val now = System.currentTimeMillis()
        val currentMap = collectDirectorySizeMap(useRoot, safUris, customExclusions)
        val allPaths = (baseMap.keys + currentMap.keys).distinct()
        val deltas = allPaths.mapNotNull { path ->
            val prev = baseMap[path] ?: 0L
            val curr = currentMap[path] ?: 0L
            val diff = curr - prev
            if (kotlin.math.abs(diff) < 128 * 1024L && curr == prev) return@mapNotNull null
            StorageDeltaItem(
                path = path,
                label = path.removePrefix("/sdcard/").removePrefix("/data/").ifBlank { path },
                previousBytes = prev,
                currentBytes = curr,
                deltaBytes = diff
            )
        }.sortedByDescending { kotlin.math.abs(it.deltaBytes) }

        val netDelta = deltas.sumOf { it.deltaBytes }
        val msg = if (deltas.isEmpty()) {
            "Tidak ada perubahan signifikan (>= 128 KB) sejak patokan terakhir."
        } else {
            val growers = deltas.count { it.deltaBytes > 0 }
            "Terdeteksi $growers folder bertambah ukuran (total delta ${if (netDelta >= 0) "+" else ""}${FormatUtils.formatBytes(kotlin.math.abs(netDelta))})."
        }

        StorageDeltaReport(
            baselineTimestampMillis = baseTs,
            comparedTimestampMillis = now,
            totalDeltaBytes = netDelta,
            items = deltas.take(25),
            statusMessage = msg
        )
    }

    private suspend fun collectDirectorySizeMap(
        useRoot: Boolean,
        safUris: List<String>,
        customExclusions: List<String>
    ): Map<String, Long> {
        val result = linkedMapOf<String, Long>()
        if (useRoot && rootManager.checkRoot() == RootStatus.GRANTED) {
            val cmd = "for d in /sdcard/* /sdcard/Android/data/* /data/anr /data/tombstones /data/local/tmp; do " +
                "[ -e \"\$d\" ] && du -sk \"\$d\" 2>/dev/null; done"
            val res = rootManager.execute(
                command = cmd,
                userApproved = true,
                timeoutMs = 30_000L,
                logCommand = true,
                logStderrAsError = false
            )
            res.stdout.forEach { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) {
                    val kb = parts[0].toLongOrNull() ?: return@forEach
                    val path = parts[1].trim()
                    if (path.startsWith("/")) {
                        result[path] = kb * 1024L
                    }
                }
            }
        } else {
            // Non-Root: use SAF granted folders + app cache dir
            val ownDir = context.cacheDir
            val ownBytes = ownDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            result["Cache Internal App"] = ownBytes

            val safItems = nonRootEngine.inspectSafFolders(safUris, customExclusions)
            safItems.forEach { item ->
                result["SAF: ${item.displayName}"] = item.totalBytes
            }

            // Fallback stat on /data if no SAF granted yet
            if (safItems.isEmpty()) {
                val stat = StatFs(File(context.filesDir.absolutePath).absolutePath)
                result["Internal Used (/data)"] = (stat.totalBytes - stat.availableBytes).coerceAtLeast(0L)
            }
        }
        return result
    }
}
