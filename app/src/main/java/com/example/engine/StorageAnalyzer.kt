package com.example.engine

import android.os.Environment
import android.os.StatFs
import com.example.model.LogType
import com.example.model.RootStatus
import com.example.model.StorageFolderBreakdown
import com.example.model.StoragePartitionInfo
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StorageAnalyzer(private val rootManager: RootManager) {

    suspend fun getInternalStorageInfo(): StoragePartitionInfo = withContext(Dispatchers.IO) {
        readStatFs("Internal (/data)", Environment.getDataDirectory())
    }

    suspend fun getExternalStorageInfo(): StoragePartitionInfo = withContext(Dispatchers.IO) {
        readStatFs("Shared (/sdcard)", Environment.getExternalStorageDirectory())
    }

    private fun readStatFs(name: String, dir: File): StoragePartitionInfo {
        return try {
            val stat = StatFs(dir.path)
            val total = stat.blockSizeLong * stat.blockCountLong
            val avail = stat.blockSizeLong * stat.availableBlocksLong
            val used = (total - avail).coerceAtLeast(0L)
            StoragePartitionInfo(
                name = name,
                mountPath = dir.absolutePath,
                totalBytes = total,
                usedBytes = used,
                availableBytes = avail
            )
        } catch (_: Exception) {
            StoragePartitionInfo(name, dir.absolutePath, 0L, 0L, 0L)
        }
    }

    suspend fun getStorageTreeBreakdown(): List<StorageFolderBreakdown> {
        if (rootManager.rootStatus.value != RootStatus.GRANTED) {
            return emptyList()
        }
        val cmd = "for p in /sdcard/DCIM /sdcard/Download /sdcard/Pictures /sdcard/Movies /sdcard/Music /sdcard/WhatsApp /sdcard/Android/data /sdcard/Android/obb /sdcard/Android/media /data/app /data/data; do " +
            "[ -d \"\$p\" ] && du -sk \"\$p\" 2>/dev/null; done"
        val res = rootManager.execute(
            command = cmd,
            userApproved = true,
            timeoutMs = 20_000L,
            logCommand = false,
            logStderrAsError = false
        )
        val parsed = mutableListOf<StorageFolderBreakdown>()
        res.stdout.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                val kb = parts[0].toLongOrNull() ?: return@forEach
                val path = parts[1].trim()
                if (kb > 0L) {
                    val label = when (path) {
                        "/sdcard/Android/data" -> "Android/data (Eksternal)"
                        "/sdcard/Android/obb" -> "Android/obb (Game Data)"
                        "/sdcard/Android/media" -> "Android/media (WhatsApp/Media)"
                        "/data/app" -> "Aplikasi Terinstal (/data/app)"
                        "/data/data" -> "Data Aplikasi (/data/data)"
                        else -> path.removePrefix("/sdcard/")
                    }
                    parsed.add(
                        StorageFolderBreakdown(
                            label = label,
                            path = path,
                            sizeBytes = kb * 1024L
                        )
                    )
                }
            }
        }
        return parsed.sortedByDescending { it.sizeBytes }
    }

    suspend fun isFstrimSupported(): Boolean {
        if (rootManager.rootStatus.value != RootStatus.GRANTED) return false
        val res = rootManager.execute(
            command = "command -v fstrim || command -v sm",
            userApproved = true,
            timeoutMs = 4000L,
            logCommand = false,
            logStderrAsError = false
        )
        return res.success && res.stdout.any { it.isNotBlank() }
    }

    suspend fun syncStorage(): Boolean {
        rootManager.appendLog(LogType.INFO, "Menjalankan sinkronisasi buffer filesystem (sync)...")
        val res = rootManager.execute(
            command = "sync",
            userApproved = true,
            timeoutMs = 15_000L
        )
        if (res.success) {
            rootManager.appendLog(LogType.CLEANED, "Sinkronisasi buffer filesystem selesai.")
        }
        return res.success
    }

    suspend fun trimStorage(): Pair<Boolean, String> {
        rootManager.appendLog(LogType.INFO, "Memeriksa dukungan FSTRIM pada partisi perangkat...")
        val hasFstrim = rootManager.execute(
            command = "command -v fstrim",
            userApproved = true,
            timeoutMs = 4000L,
            logCommand = false,
            logStderrAsError = false
        )
        return if (hasFstrim.success && hasFstrim.stdout.any { it.isNotBlank() }) {
            val trimRes = rootManager.execute(
                command = "fstrim -v /data 2>&1; fstrim -v /cache 2>&1 || true",
                userApproved = true,
                timeoutMs = 45_000L,
                streamOutput = true
            )
            val msg = trimRes.stdout.joinToString(" | ").ifBlank { "Optimasi TRIM partisi selesai." }
            rootManager.appendLog(LogType.CLEANED, "TRIM STORAGE: $msg")
            Pair(true, msg)
        } else {
            val smRes = rootManager.execute(
                command = "sm fstrim 2>&1",
                userApproved = true,
                timeoutMs = 45_000L,
                streamOutput = true
            )
            if (smRes.success) {
                val msg = smRes.stdout.joinToString(" | ").ifBlank { "sm fstrim berhasil dieksekusi." }
                rootManager.appendLog(LogType.CLEANED, "TRIM STORAGE (sm fstrim): $msg")
                Pair(true, msg)
            } else {
                val msg = "Perangkat atau kebijakan SELinux tidak mengizinkan eksekusi fstrim."
                rootManager.appendLog(LogType.ERROR, msg)
                Pair(false, msg)
            }
        }
    }

    suspend fun runArtDexoptOptimization(): Pair<Boolean, String> {
        rootManager.appendLog(LogType.INFO, "Memulai optimasi kompilasi ART/Dexopt (bg-dexopt-job)...")
        val res = rootManager.execute(
            command = "cmd package bg-dexopt-job 2>&1",
            userApproved = true,
            timeoutMs = 120_000L,
            streamOutput = true
        )
        return if (res.success) {
            val msg = res.stdout.joinToString(" ").ifBlank { "Optimasi ART/Dexopt selesai dijalankan." }
            rootManager.appendLog(LogType.CLEANED, "ART Optimizer: $msg")
            Pair(true, msg)
        } else {
            val msg = res.stderr.firstOrNull() ?: res.stdout.firstOrNull() ?: "Optimasi ART gagal atau dibatalkan sistem."
            rootManager.appendLog(LogType.ERROR, "ART Optimizer: $msg")
            Pair(false, msg)
        }
    }
}
