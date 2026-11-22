package com.example.engine

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.model.LogType
import com.example.model.RootStatus
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

class SecureDeleteEngine(
    private val context: Context,
    private val rootManager: RootManager,
    private val selfPackageName: String
) {

    companion object {
        // Inline POSIX/mksh shell function for explicit path lists
        const val SHELL_SHRED_FN =
            "uc_wipe() { " +
                "_f=\"\$1\"; " +
                "[ -f \"\$_f\" ] || return 0; " +
                "_sz=\$(stat -c '%s' \"\$_f\" 2>/dev/null || echo 0); " +
                "if [ \"\$_sz\" -gt 0 ] 2>/dev/null; then " +
                "if [ \"\$_sz\" -le 8388608 ]; then " +
                "_b=\$(( (_sz + 65535) / 65536 )); " +
                "dd if=/dev/zero of=\"\$_f\" bs=65536 count=\"\$_b\" conv=notrunc 2>/dev/null; " +
                "else " +
                "dd if=/dev/zero of=\"\$_f\" bs=65536 count=32 conv=notrunc 2>/dev/null; " +
                "_tail=\$(( (_sz / 65536) - 16 )); " +
                "[ \"\$_tail\" -gt 32 ] && dd if=/dev/zero of=\"\$_f\" bs=65536 seek=\"\$_tail\" count=16 conv=notrunc 2>/dev/null; " +
                "fi; " +
                "fi; " +
                ": > \"\$_f\" 2>/dev/null; " +
                "case \"\$_f\" in */*) _d=\"\${_f%/*}\" ;; *) _d=\".\" ;; esac; " +
                "_t=\"\$_d/.ucw_\$\$_tmp\"; " +
                "mv -f \"\$_f\" \"\$_t\" 2>/dev/null && rm -f \"\$_t\" 2>/dev/null || rm -f \"\$_f\" 2>/dev/null; " +
                "}; "

        // POSIX/mksh-safe batched find -exec action (zero single quotes inside body)
        const val SHELL_WIPE_EXEC =
            "-exec sh -c 'for _f in \"\$@\"; do " +
                "[ -f \"\$_f\" ] || continue; " +
                "_sz=\$(stat -c \"%s\" \"\$_f\" 2>/dev/null || echo 0); " +
                "if [ \"\$_sz\" -gt 0 ] 2>/dev/null; then " +
                "if [ \"\$_sz\" -le 8388608 ]; then " +
                "_b=\$(( (_sz + 65535) / 65536 )); " +
                "dd if=/dev/zero of=\"\$_f\" bs=65536 count=\"\$_b\" conv=notrunc 2>/dev/null; " +
                "else " +
                "dd if=/dev/zero of=\"\$_f\" bs=65536 count=32 conv=notrunc 2>/dev/null; " +
                "_tail=\$(( (_sz / 65536) - 16 )); " +
                "[ \"\$_tail\" -gt 32 ] && dd if=/dev/zero of=\"\$_f\" bs=65536 seek=\"\$_tail\" count=16 conv=notrunc 2>/dev/null; " +
                "fi; " +
                "fi; " +
                ": > \"\$_f\" 2>/dev/null; " +
                "case \"\$_f\" in */*) _d=\"\${_f%/*}\" ;; *) _d=\".\" ;; esac; " +
                "_t=\"\$_d/.ucw_\$\$_tmp\"; " +
                "mv -f \"\$_f\" \"\$_t\" 2>/dev/null && rm -f \"\$_t\" 2>/dev/null || rm -f \"\$_f\" 2>/dev/null; " +
                "done' _ {} + 2>&1"
    }

    fun getAvailableDataBytes(): Long {
        return try {
            StatFs(Environment.getDataDirectory().absolutePath).availableBytes
        } catch (_: Exception) {
            0L
        }
    }

    fun buildRootDeleteDirectoryFilesCmd(
        safeDirPath: String,
        exclusions: String,
        secureShred: Boolean
    ): String {
        return if (secureShred) {
            "if [ -d '$safeDirPath' ]; then " +
                "find '$safeDirPath' -mindepth 1 -type f $exclusions $SHELL_WIPE_EXEC; " +
                "find '$safeDirPath' -mindepth 1 -type d -empty $exclusions -exec rmdir {} + 2>/dev/null || true; " +
                "sync; fi"
        } else {
            "if [ -d '$safeDirPath' ]; then " +
                "find '$safeDirPath' -mindepth 1 -type f $exclusions -exec rm -f {} + 2>&1; " +
                "find '$safeDirPath' -mindepth 1 -type d -empty $exclusions -exec rmdir {} + 2>/dev/null || true; fi"
        }
    }

    suspend fun secureDeleteRootFileList(
        paths: List<String>,
        customExclusions: List<String>,
        secureShred: Boolean
    ): Pair<Int, Int> {
        if (rootManager.checkRoot() != RootStatus.GRANTED) return 0 to 1
        val safeTargets = paths.filter {
            PathValidator.isSafeFileDeletionTarget(it, selfPackageName, customExclusions)
        }
        if (safeTargets.isEmpty()) return 0 to 0

        var deleted = 0
        var errors = 0
        for (chunk in safeTargets.chunked(20)) {
            val quoted = chunk.joinToString(" ") { "'${PathValidator.sanitizeShellPath(it)}'" }
            val cmd = if (secureShred) {
                "$SHELL_SHRED_FN for _p in $quoted; do uc_wipe \"\$_p\"; done; sync"
            } else {
                "rm -f $quoted 2>&1"
            }
            val res = rootManager.execute(
                command = cmd,
                userApproved = true,
                timeoutMs = 35_000L,
                logCommand = true,
                logStderrAsError = false
            )
            if (res.success) {
                deleted += chunk.size
            } else {
                errors++
            }
        }
        purgeMediaStoreTraces(safeTargets)
        return deleted to errors
    }

    fun secureShredLocalFile(file: File): Boolean {
        return try {
            if (!file.exists()) return false
            val len = file.length()
            if (len > 0L && file.canWrite()) {
                RandomAccessFile(file, "rw").use { raf ->
                    val zeros = ByteArray(minOf(65536, len.toInt().coerceAtLeast(1)))
                    if (len <= 8L * 1024L * 1024L) {
                        var remaining = len
                        while (remaining > 0L) {
                            val toWrite = minOf(zeros.size.toLong(), remaining).toInt()
                            raf.write(zeros, 0, toWrite)
                            remaining -= toWrite
                        }
                    } else {
                        val twoMb = 2L * 1024L * 1024L
                        var remaining = twoMb
                        while (remaining > 0L) {
                            val toWrite = minOf(zeros.size.toLong(), remaining).toInt()
                            raf.write(zeros, 0, toWrite)
                            remaining -= toWrite
                        }
                        raf.seek((len - 1024L * 1024L).coerceAtLeast(0L))
                        raf.write(zeros, 0, minOf(zeros.size, 65536))
                    }
                    raf.fd.sync()
                    raf.setLength(0L)
                }
            }
            val renamed = File(file.parentFile, ".ucw_${System.nanoTime()}")
            if (file.renameTo(renamed)) {
                renamed.delete()
            } else {
                file.delete()
            }
        } catch (_: Exception) {
            try {
                file.delete()
            } catch (_: Exception) {
                false
            }
        }
    }

    fun secureDeleteSafUri(uri: Uri): Boolean {
        return try {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "wt")?.use { afd ->
                    afd.createOutputStream()?.use { os ->
                        os.write(ByteArray(0))
                        os.flush()
                    }
                }
            } catch (_: Exception) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        os.write(ByteArray(0))
                        os.flush()
                    }
                } catch (_: Exception) {
                }
            }
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun purgeMediaStoreTraces(deletedPaths: List<String>) = withContext(Dispatchers.IO) {
        val sdcardPaths = deletedPaths.filter {
            it.startsWith("/sdcard/") || it.startsWith("/storage/emulated/0/")
        }.map { path ->
            if (path.startsWith("/sdcard/")) "/storage/emulated/0/" + path.removePrefix("/sdcard/") else path
        }
        if (sdcardPaths.isEmpty()) return@withContext

        try {
            val uri = MediaStore.Files.getContentUri("external")
            for (batch in sdcardPaths.chunked(40)) {
                val placeholders = batch.joinToString(",") { "?" }
                @Suppress("DEPRECATION")
                val where = "${MediaStore.MediaColumns.DATA} IN ($placeholders)"
                try {
                    context.contentResolver.delete(uri, where, batch.toTypedArray())
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }

        try {
            val dirs = sdcardPaths.mapNotNull { File(it).parent }.distinct().take(20)
            if (dirs.isNotEmpty()) {
                MediaScannerConnection.scanFile(context, dirs.toTypedArray(), null, null)
            }
        } catch (_: Exception) {
        }
    }

    suspend fun wipeSystemGhostCaches(zeroTraceHistory: Boolean) {
        if (rootManager.checkRoot() == RootStatus.GRANTED) {
            val cmd = "for d in /sdcard/DCIM/.thumbnails /sdcard/Pictures/.thumbnails /sdcard/Movies/.thumbnails /sdcard/.Trash /sdcard/.Trash-1000; do " +
                "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f -exec rm -f {} + 2>/dev/null; done; sync"
            rootManager.execute(cmd, userApproved = true, timeoutMs = 15_000L, logCommand = false, logStderrAsError = false)
        }
        if (zeroTraceHistory) {
            rootManager.appendLog(LogType.INFO, "Zero-Trace aktif: jejak pembersihan akan dibersihkan.")
        }
    }
}
