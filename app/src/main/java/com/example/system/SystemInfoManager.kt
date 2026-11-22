package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.engine.StorageAnalyzer
import com.example.model.CpuInfo
import com.example.model.ProcessInfo
import com.example.model.RamInfo
import com.example.model.RootStatus
import com.example.model.SystemSnapshot
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SystemInfoManager(
    private val context: Context,
    private val rootManager: RootManager,
    private val storageAnalyzer: StorageAnalyzer,
    private val batteryManager: BatteryManager
) {
    suspend fun collectSnapshot(
        includeProcesses: Boolean = true,
        includeStorageBreakdown: Boolean = true
    ): SystemSnapshot {
        val ram = readRamInfo()
        val internal = storageAnalyzer.getInternalStorageInfo()
        val external = storageAnalyzer.getExternalStorageInfo()
        val cpu = readCpuInfo()
        val battery = batteryManager.readBatteryStatus()
        val processes = if (includeProcesses) readRunningProcesses() else emptyList()
        val breakdown = if (includeStorageBreakdown) storageAnalyzer.getStorageTreeBreakdown() else emptyList()
        val fstrim = storageAnalyzer.isFstrimSupported()

        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        val deviceTitle = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }

        return SystemSnapshot(
            deviceName = deviceTitle.ifBlank { "Android Device" },
            modelCode = "${Build.DEVICE} (${Build.HARDWARE})",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            sdkInt = Build.VERSION.SDK_INT,
            ramInfo = ram,
            internalStorage = internal,
            externalStorage = external,
            cpuInfo = cpu,
            batteryInfo = battery,
            topProcesses = processes,
            storageBreakdown = breakdown,
            fstrimSupported = fstrim,
            lastUpdatedMillis = System.currentTimeMillis()
        )
    }

    suspend fun readRamInfo(): RamInfo = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        var totalKb = memInfo.totalMem / 1024L
        var availKb = memInfo.availMem / 1024L
        var buffersKb = 0L
        var cachedKb = 0L
        var swapTotalKb = 0L
        var swapFreeKb = 0L

        try {
            val procMem = File("/proc/meminfo")
            val lines = if (procMem.canRead()) {
                procMem.readLines()
            } else if (rootManager.rootStatus.value == RootStatus.GRANTED) {
                rootManager.execute(
                    command = "cat /proc/meminfo",
                    userApproved = true,
                    timeoutMs = 3000L,
                    logCommand = false,
                    logStderrAsError = false
                ).stdout
            } else {
                emptyList()
            }

            lines.forEach { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val valueKb = parts[1].toLongOrNull() ?: return@forEach
                    when (parts[0]) {
                        "MemTotal:" -> totalKb = valueKb
                        "MemAvailable:" -> availKb = valueKb
                        "Buffers:" -> buffersKb = valueKb
                        "Cached:" -> cachedKb = valueKb
                        "SwapTotal:" -> swapTotalKb = valueKb
                        "SwapFree:" -> swapFreeKb = valueKb
                    }
                }
            }
        } catch (_: Exception) {
        }

        val totalBytes = totalKb * 1024L
        val availBytes = availKb * 1024L
        val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)

        RamInfo(
            totalBytes = totalBytes,
            availableBytes = availBytes,
            usedBytes = usedBytes,
            buffersBytes = buffersKb * 1024L,
            cachedBytes = cachedKb * 1024L,
            swapTotalBytes = swapTotalKb * 1024L,
            swapFreeBytes = swapFreeKb * 1024L,
            isLowMemory = memInfo.lowMemory
        )
    }

    suspend fun readCpuInfo(): CpuInfo = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val freqs = mutableListOf<Int>()
        var maxFreqMhz = 0

        for (i in 0 until coreCount) {
            val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            val curKhz = try {
                if (curFile.canRead()) curFile.readText().trim().toIntOrNull() ?: 0 else 0
            } catch (_: Exception) {
                0
            }
            val maxKhz = try {
                if (maxFile.canRead()) maxFile.readText().trim().toIntOrNull() ?: 0 else 0
            } catch (_: Exception) {
                0
            }
            if (curKhz > 0) freqs.add(curKhz / 1000)
            if (maxKhz / 1000 > maxFreqMhz) maxFreqMhz = maxKhz / 1000
        }

        if (freqs.isEmpty() && rootManager.rootStatus.value == RootStatus.GRANTED) {
            val rootFreqs = rootManager.execute(
                command = "cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq 2>/dev/null",
                userApproved = true,
                timeoutMs = 3000L,
                logCommand = false,
                logStderrAsError = false
            )
            rootFreqs.stdout.forEach { line ->
                val mhz = (line.trim().toIntOrNull() ?: 0) / 1000
                if (mhz > 0) freqs.add(mhz)
            }
        }

        var hardware = Build.HARDWARE.ifBlank { Build.BOARD }
        try {
            val cpuInfoFile = File("/proc/cpuinfo")
            if (cpuInfoFile.canRead()) {
                cpuInfoFile.readLines().firstOrNull { it.startsWith("Hardware", ignoreCase = true) }?.let {
                    val value = it.substringAfter(":").trim()
                    if (value.isNotEmpty()) hardware = value
                }
            }
        } catch (_: Exception) {
        }

        CpuInfo(
            hardware = hardware,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
            coreCount = coreCount,
            currentFreqMhzList = freqs,
            maxFreqMhz = maxFreqMhz
        )
    }

    suspend fun readRunningProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        if (rootManager.rootStatus.value != RootStatus.GRANTED) {
            return@withContext emptyList()
        }
        val res = rootManager.execute(
            command = "ps -A -o PID,USER,RSS,NAME 2>/dev/null || ps -A 2>/dev/null",
            userApproved = true,
            timeoutMs = 5000L,
            logCommand = false,
            logStderrAsError = false
        )
        if (!res.success) return@withContext emptyList()

        val parsed = mutableListOf<ProcessInfo>()
        res.stdout.drop(1).forEach { line ->
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size >= 4) {
                val pid = cols[0].toIntOrNull()
                val rss = cols[2].toLongOrNull()
                if (pid != null && rss != null) {
                    parsed.add(
                        ProcessInfo(
                            pid = pid,
                            user = cols[1],
                            rssKb = rss,
                            name = cols.last()
                        )
                    )
                }
            }
        }
        parsed.sortedByDescending { it.rssKb }.take(20)
    }
}
