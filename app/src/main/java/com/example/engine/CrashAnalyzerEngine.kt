package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.model.CrashAnalyzerReport
import com.example.model.CrashCulpritEntry
import com.example.model.FormatUtils
import com.example.model.LogType
import com.example.model.RootStatus
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CrashAnalyzerEngine(
    private val context: Context,
    private val rootManager: RootManager
) {

    suspend fun analyzeCrashLogs(useRoot: Boolean): CrashAnalyzerReport = withContext(Dispatchers.IO) {
        if (useRoot && rootManager.checkRoot() == RootStatus.GRANTED) {
            analyzeViaRoot()
        } else {
            analyzeViaNonRoot()
        }
    }

    private suspend fun analyzeViaRoot(): CrashAnalyzerReport {
        val cmd = "for f in /data/anr/* /data/tombstones/* /data/system/dropbox/*; do " +
            "[ -f \"\$f\" ] || continue; " +
            "sz=\$(du -sk \"\$f\" 2>/dev/null | cut -f1); " +
            "hdr=\$(head -n 35 \"\$f\" 2>/dev/null | grep -E 'Cmd line:|Process:|Package:|ANR in|Build fingerprint|signal ' | head -n 4 | tr '\\n' ';'); " +
            "echo \"ENTRY|\$f|\$sz|\$hdr\"; " +
            "done"
        val res = rootManager.execute(
            command = cmd,
            userApproved = true,
            timeoutMs = 25_000L,
            logCommand = true,
            logStderrAsError = false
        )

        val entries = mutableListOf<Triple<String, Long, String>>()
        res.stdout.forEach { line ->
            if (!line.startsWith("ENTRY|")) return@forEach
            val parts = line.split("|", limit = 4)
            if (parts.size >= 4) {
                val path = parts[1].trim()
                val kb = parts[2].trim().toLongOrNull() ?: 0L
                val hdr = parts[3].trim()
                entries.add(Triple(path, kb * 1024L, hdr))
            }
        }

        if (entries.isEmpty()) {
            return CrashAnalyzerReport(
                culprits = emptyList(),
                totalCrashLogBytes = 0L,
                totalLogFiles = 0,
                isRootDetailAvailable = true,
                summaryMessage = "Tidak ada log crash/ANR menumpuk di /data/anr, /data/tombstones, atau dropbox."
            )
        }

        val grouped = linkedMapOf<String, MutableList<Triple<String, Long, String>>>()
        entries.forEach { entry ->
            val pkg = extractPackageOrProcess(entry.third, entry.first)
            grouped.getOrPut(pkg) { mutableListOf() }.add(entry)
        }

        val culprits = grouped.map { (pkg, list) ->
            val firstPath = list.first().first
            val sourceType = when {
                firstPath.contains("/anr/") -> "ANR"
                firstPath.contains("/tombstones/") -> "Tombstone"
                else -> "Dropbox"
            }
            val sampleHeader = list.first().third
                .split(";")
                .firstOrNull { it.isNotBlank() }
                ?.take(90)
                ?: "Log kegagalan proses ($sourceType)"

            CrashCulpritEntry(
                packageName = pkg,
                sourceType = sourceType,
                occurrenceCount = list.size,
                totalLogBytes = list.sumOf { it.second },
                sampleReason = sampleHeader,
                filePaths = list.map { it.first }
            )
        }.sortedByDescending { it.totalLogBytes }

        val totalBytes = entries.sumOf { it.second }
        rootManager.appendLog(LogType.FOUND, "Crash Analyzer: ${entries.size} log (${FormatUtils.formatBytes(totalBytes)}) dari ${culprits.size} proses")
        return CrashAnalyzerReport(
            culprits = culprits,
            totalCrashLogBytes = totalBytes,
            totalLogFiles = entries.size,
            isRootDetailAvailable = true,
            summaryMessage = "Terdeteksi ${entries.size} berkas crash/ANR (${FormatUtils.formatBytes(totalBytes)}) dari ${culprits.size} proses."
        )
    }

    private fun analyzeViaNonRoot(): CrashAnalyzerReport {
        val culprits = mutableListOf<CrashCulpritEntry>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val reasons = am.getHistoricalProcessExitReasons(null, 0, 15)
                reasons.groupBy { it.processName }.forEach { (proc, list) ->
                    val latest = list.first()
                    val desc = latest.description ?: "Exit reason code ${latest.reason}"
                    culprits.add(
                        CrashCulpritEntry(
                            packageName = proc,
                            sourceType = "ExitInfo",
                            occurrenceCount = list.size,
                            totalLogBytes = 0L,
                            sampleReason = desc.take(90)
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }
        val msg = if (culprits.isEmpty()) {
            "Mode Non-Root: tidak ada catatan exit proses terbaru. Aktifkan Root untuk memindai /data/anr & /data/tombstones."
        } else {
            "Mode Non-Root: ditemukan ${culprits.size} riwayat penghentian proses melalui Android ExitInfo API."
        }
        return CrashAnalyzerReport(
            culprits = culprits,
            totalCrashLogBytes = 0L,
            totalLogFiles = culprits.sumOf { it.occurrenceCount },
            isRootDetailAvailable = false,
            summaryMessage = msg
        )
    }

    suspend fun clearCrashLogs(culprits: List<CrashCulpritEntry>): CleanSummaryResult =
        withContext(Dispatchers.IO) {
            if (rootManager.checkRoot() != RootStatus.GRANTED) {
                return@withContext CleanSummaryResult(0L, 0, 1, "Pembersihan /data/anr & tombstones memerlukan otorisasi Root.")
            }
            val paths = culprits.flatMap { it.filePaths }.distinct()
            var freed = 0L
            var deleted = 0
            var errors = 0

            paths.forEach { raw ->
                if (raw.startsWith("/data/anr/") || raw.startsWith("/data/tombstones/") || raw.startsWith("/data/system/dropbox/")) {
                    val safe = PathValidator.sanitizeShellPath(raw)
                    val res = rootManager.execute("rm -f '$safe'", userApproved = true, timeoutMs = 8_000L)
                    if (res.success) {
                        deleted++
                    } else {
                        errors++
                    }
                }
            }
            freed = culprits.sumOf { it.totalLogBytes }
            val msg = "Log Crash dibersihkan: $deleted berkas (${FormatUtils.formatBytes(freed)})"
            rootManager.appendLog(LogType.CLEANED, msg)
            CleanSummaryResult(freed, deleted, errors, msg)
        }

    private fun extractPackageOrProcess(header: String, path: String): String {
        val cmdLineMatch = Regex("(?:Cmd line:|Process:|Package:|ANR in)\\s*([A-Za-z0-9._:/-]+)").find(header)
        val candidate = cmdLineMatch?.groupValues?.getOrNull(1)?.trim()
        if (!candidate.isNullOrBlank()) return candidate.substringBefore(" ")
        return path.substringAfterLast("/").substringBefore("_").substringBefore("@").ifBlank { "system_process" }
    }
}
