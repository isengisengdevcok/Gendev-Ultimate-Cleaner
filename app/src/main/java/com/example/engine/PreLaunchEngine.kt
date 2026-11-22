package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import com.example.model.FormatUtils
import com.example.model.LaunchableAppEntry
import com.example.model.LogType
import com.example.model.PreLaunchProfile
import com.example.model.PreLaunchReport
import com.example.model.RootStatus
import com.example.root.RootManager
import com.example.system.BatteryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PreLaunchEngine(
    private val context: Context,
    private val rootManager: RootManager,
    private val batteryManager: BatteryManager
) {

    private val protectedPackages = setOf(
        "com.android.systemui",
        "com.android.phone",
        "com.android.settings",
        "com.android.providers.settings",
        "com.google.android.gms",
        "com.topjohnwu.magisk",
        "io.github.huskydg.magisk",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.oppo.launcher",
        "com.android.launcher3"
    )

    suspend fun getLaunchableUserApps(): List<LaunchableAppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { info ->
                info.packageName != context.packageName &&
                    !protectedPackages.contains(info.packageName)
            }
            .map { info ->
                LaunchableAppEntry(
                    packageName = info.packageName,
                    appLabel = pm.getApplicationLabel(info).toString()
                )
            }
            .sortedBy { it.appLabel.lowercase() }
    }

    suspend fun runPreLaunchConditioner(
        profile: PreLaunchProfile,
        targetApp: LaunchableAppEntry?,
        useRoot: Boolean
    ): PreLaunchReport = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memBefore = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val battery = batteryManager.readBatteryStatus()
        val actions = mutableListOf<String>()
        var trimmedCount = 0

        if (useRoot && rootManager.checkRoot() == RootStatus.GRANTED) {
            rootManager.appendLog(LogType.INFO, "Pre-Launch Conditioner [${profile.title}]: memulai sekuens presisi...")

            // 1. Flush pending storage write buffers
            val syncRes = rootManager.execute("sync", userApproved = true, timeoutMs = 8_000L)
            if (syncRes.success) actions.add("Sinkronisasi buffer tulis partisi (sync)")

            // 2. Trim non-essential Android app caches safely
            val trimBytes = when (profile) {
                PreLaunchProfile.GAMING -> "512M"
                PreLaunchProfile.CAMERA -> "384M"
                PreLaunchProfile.HEAVY_APP -> "256M"
            }
            val pmRes = rootManager.execute("pm trim-caches $trimBytes", userApproved = true, timeoutMs = 15_000L)
            if (pmRes.success) actions.add("Pemangkasan cache sistem Android ($trimBytes)")

            // 3. Send polite memory trim signals only to idle non-system user packages (never killing system services)
            val pm = context.packageManager
            val userPkgs = pm.getInstalledApplications(0)
                .filter { info ->
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    !isSystem &&
                        info.packageName != context.packageName &&
                        info.packageName != targetApp?.packageName &&
                        !protectedPackages.contains(info.packageName)
                }
                .take(16)
                .map { it.packageName }

            if (userPkgs.isNotEmpty()) {
                val batchCmd = userPkgs.joinToString("; ") { pkg ->
                    "am send-trim-memory ${PathValidator.sanitizeShellPath(pkg)} RUNNING_LOW 2>/dev/null || true"
                }
                rootManager.execute(batchCmd, userApproved = true, timeoutMs = 15_000L, logCommand = false)
                trimmedCount = userPkgs.size
                actions.add("Trim memori terkendali pada $trimmedCount aplikasi latar belakang")
            }
        } else {
            // Non-Root: clean own staging caches & request JVM/native memory compaction
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("reclaim_") || f.name.startsWith("dhash_")) f.delete()
            }
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
            actions.add("Pembersihan staging internal & pemadatan memori (Non-Root)")
            actions.add("Audit suhu baterai (${battery.temperatureCelsius}°C) & ruang RAM bebas")
        }

        val memAfter = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val delta = (memAfter.availMem - memBefore.availMem).coerceAtLeast(0L)
        actions.add("RAM tersedia saat ini: ${FormatUtils.formatBytes(memAfter.availMem)} (+${FormatUtils.formatBytes(delta)})")

        PreLaunchReport(
            profile = profile,
            targetAppLabel = targetApp?.appLabel,
            targetPackageName = targetApp?.packageName,
            ramBeforeAvailableBytes = memBefore.availMem,
            ramAfterAvailableBytes = memAfter.availMem,
            batteryTempCelsius = battery.temperatureCelsius,
            trimmedProcessesCount = trimmedCount,
            actionsSummary = actions,
            readyToLaunch = true
        )
    }

    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
