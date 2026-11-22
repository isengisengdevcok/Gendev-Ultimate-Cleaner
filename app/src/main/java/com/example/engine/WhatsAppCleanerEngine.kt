package com.example.engine

import com.example.model.FormatUtils
import com.example.model.LogType
import com.example.model.RootStatus
import com.example.model.WhatsAppCategoryType
import com.example.model.WhatsAppTargetGroup
import com.example.root.RootManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

class WhatsAppCleanerEngine(
    private val rootManager: RootManager,
    private val secureDeleteEngine: SecureDeleteEngine
) {

    companion object {
        // Account identity, registration, E2EE keys, and chat databases that MUST NEVER be deleted
        val ACCOUNT_LOCKED_FILE_NAMES = setOf(
            "me", "key", "rc2", "secret", "pw", "backup_token",
            "keystore.xml", "com.whatsapp_preferences_light.xml",
            "com.whatsapp.w4b_preferences_light.xml"
        )

        val ACCOUNT_LOCKED_PATH_SEGMENTS = listOf(
            "/databases/", "/Databases/", "/Backups/", "/shared_prefs/",
            "/keystore/", "/no_backup/", "msgstore", "wa.db", "axolotl.db",
            "chatsettings.db", "stickers.db", "noise_key", "encrypted_backup"
        )

        fun isWhatsAppAccountProtected(path: String): Boolean {
            val normalized = path.trim()
            if (normalized.isEmpty()) return true
            val name = File(normalized).name
            if (ACCOUNT_LOCKED_FILE_NAMES.any { name.equals(it, ignoreCase = true) }) return true
            if (name.endsWith(".db", ignoreCase = true) ||
                name.endsWith(".db-wal", ignoreCase = true) ||
                name.endsWith(".db-shm", ignoreCase = true) ||
                name.endsWith(".db-journal", ignoreCase = true) ||
                name.endsWith(".crypt", ignoreCase = true) ||
                name.contains(".crypt", ignoreCase = true)
            ) {
                return true
            }
            return ACCOUNT_LOCKED_PATH_SEGMENTS.any { normalized.contains(it, ignoreCase = true) }
        }

        fun buildWhatsAppSafeFindFlags(customExclusions: List<String>): String {
            val base = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
            val extra = listOf(
                "! -path '*/no_backup/*'",
                "! -name 'me'",
                "! -name 'key'",
                "! -name 'rc2'",
                "! -name 'secret'",
                "! -name 'pw'",
                "! -name 'backup_token'",
                "! -name '*noise_key*'",
                "! -name '*encrypted_backup*'"
            ).joinToString(" ")
            return "$base $extra"
        }
    }

    fun getDefaultGroups(): List<WhatsAppTargetGroup> {
        return listOf(
            WhatsAppTargetGroup(
                type = WhatsAppCategoryType.MEDIA_IMAGES_VIDEO,
                title = "Foto, Video & GIF WhatsApp",
                subtitle = "WhatsApp Images, Video, Animated Gifs (termasuk folder Sent & Private)",
                paths = listOf(
                    "/sdcard/WhatsApp/Media/WhatsApp Images",
                    "/sdcard/WhatsApp/Media/WhatsApp Video",
                    "/sdcard/WhatsApp/Media/WhatsApp Animated Gifs",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Animated Gifs",
                    "/sdcard/WhatsApp Business/Media/WhatsApp Business Images",
                    "/sdcard/WhatsApp Business/Media/WhatsApp Business Video",
                    "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images",
                    "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Video"
                )
            ),
            WhatsAppTargetGroup(
                type = WhatsAppCategoryType.AUDIO_VOICE,
                title = "Audio & Voice Note WhatsApp",
                subtitle = "WhatsApp Audio dan Voice Notes (pesan suara)",
                paths = listOf(
                    "/sdcard/WhatsApp/Media/WhatsApp Audio",
                    "/sdcard/WhatsApp/Media/WhatsApp Voice Notes",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
                    "/sdcard/WhatsApp Business/Media/WhatsApp Business Audio",
                    "/sdcard/WhatsApp Business/Media/WhatsApp Business Voice Notes",
                    "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio",
                    "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"
                )
            ),
            WhatsAppTargetGroup(
                type = WhatsAppCategoryType.DOCUMENTS,
                title = "Dokumen Media WhatsApp",
                subtitle = "Semua dokumen terunduh di folder WhatsApp Documents",
                paths = listOf(
                    "/sdcard/WhatsApp/Media/WhatsApp Documents",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents",
                    "/sdcard/WhatsApp Business/Media/WhatsApp Business Documents",
                    "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents"
                )
            ),
            WhatsAppTargetGroup(
                type = WhatsAppCategoryType.STICKERS_VIEWONCE,
                title = "Stiker, Profil & ViewOnce (Root)",
                subtitle = "WhatsApp Stickers, Profile Photos, WallPaper, dan cache ViewOnce internal",
                paths = listOf(
                    "/sdcard/WhatsApp/Media/WhatsApp Stickers",
                    "/sdcard/WhatsApp/Media/WhatsApp Profile Photos",
                    "/sdcard/WhatsApp/Media/WallPaper",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Profile Photos",
                    "/data/user/0/com.whatsapp/files/Stickers",
                    "/data/user/0/com.whatsapp/files/ViewOnce",
                    "/data/user/0/com.whatsapp/files/Avatars",
                    "/data/user/0/com.whatsapp/files/gifs",
                    "/data/user/0/com.whatsapp.w4b/files/Stickers",
                    "/data/user/0/com.whatsapp.w4b/files/ViewOnce"
                )
            ),
            WhatsAppTargetGroup(
                type = WhatsAppCategoryType.STATUS_SHARED_TRASH,
                title = "Status, .Shared, .Trash & Thumbs",
                subtitle = "Media sementara WhatsApp (.Statuses, .Shared, .Trash, .Thumbs, .Links)",
                paths = listOf(
                    "/sdcard/WhatsApp/Media/.Statuses",
                    "/sdcard/WhatsApp/.Shared",
                    "/sdcard/WhatsApp/.Trash",
                    "/sdcard/WhatsApp/Thumbs",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/.Shared",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/.Trash",
                    "/sdcard/Android/media/com.whatsapp/WhatsApp/Thumbs",
                    "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses",
                    "/sdcard/Android/media/com.whatsapp.w4b/WhatsApp Business/.Shared"
                )
            ),
            WhatsAppTargetGroup(
                type = WhatsAppCategoryType.ROOT_INTERNAL_CACHE,
                title = "File Cache, Log & WebView Root (/data)",
                subtitle = "Semua file sampah/cache/log internal com.whatsapp* kecuali akun & DB chat",
                paths = listOf(
                    "/data/user/0/com.whatsapp/cache",
                    "/data/user/0/com.whatsapp/code_cache",
                    "/data/user/0/com.whatsapp/app_webview",
                    "/data/user/0/com.whatsapp/files/Logs",
                    "/data/user/0/com.whatsapp/files/decompressed",
                    "/data/user/0/com.whatsapp/files/download",
                    "/data/user/0/com.whatsapp/files/.trash",
                    "/sdcard/Android/data/com.whatsapp/cache",
                    "/data/user/0/com.whatsapp.w4b/cache",
                    "/data/user/0/com.whatsapp.w4b/code_cache",
                    "/data/user/0/com.whatsapp.w4b/files/Logs",
                    "/sdcard/Android/data/com.whatsapp.w4b/cache"
                )
            )
        )
    }

    suspend fun scanAllWhatsAppRoot(
        currentGroups: List<WhatsAppTargetGroup>,
        customExclusions: List<String>,
        onProgress: ((Float, String) -> Unit)? = null
    ): List<WhatsAppTargetGroup> {
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            rootManager.appendLog(LogType.ERROR, "Pemindaian WhatsApp Root dibatalkan: otorisasi root belum aktif.")
            return currentGroups
        }

        val flags = buildWhatsAppSafeFindFlags(customExclusions)
        val total = currentGroups.size.coerceAtLeast(1)
        val updated = mutableListOf<WhatsAppTargetGroup>()

        currentGroups.forEachIndexed { idx, group ->
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(idx.toFloat() / total.toFloat(), "Memindai ${group.title} (Root)...")

            val existingPaths = group.paths.filter { PathValidator.isSafeDirectoryTarget(it, customExclusions) }
            val quotedDirs = existingPaths.joinToString(" ") { "'${PathValidator.sanitizeShellPath(it)}'" }
            val cmd = "for d in $quotedDirs; do " +
                "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f $flags -exec stat -c '%s|%n' {} + 2>/dev/null; done"

            val res = rootManager.execute(
                command = cmd,
                userApproved = true,
                timeoutMs = 30_000L,
                logCommand = true,
                logStderrAsError = false
            )

            var count = 0
            var bytes = 0L
            val samples = mutableListOf<String>()
            res.stdout.forEach { line ->
                val sep = line.indexOf('|')
                if (sep > 0) {
                    val sz = line.substring(0, sep).trim().toLongOrNull() ?: 0L
                    val path = line.substring(sep + 1).trim()
                    if (!isWhatsAppAccountProtected(path) &&
                        PathValidator.isSafeFileDeletionTarget(path, "", customExclusions)
                    ) {
                        count++
                        bytes += sz.coerceAtLeast(0L)
                        if (samples.size < 4) samples.add(path)
                    }
                }
            }

            rootManager.appendLog(
                LogType.FOUND,
                "WhatsApp [${group.title}]: $count berkas (${FormatUtils.formatBytes(bytes)})"
            )
            updated.add(
                group.copy(
                    fileCount = count,
                    sizeBytes = bytes,
                    sampleFiles = samples,
                    isScanned = true
                )
            )
            onProgress?.invoke((idx + 1).toFloat() / total.toFloat(), "Selesai ${group.title}")
        }

        return updated
    }

    suspend fun cleanWhatsAppPermanent(
        groups: List<WhatsAppTargetGroup>,
        customExclusions: List<String>,
        secureShred: Boolean,
        onProgress: ((Float, String) -> Unit)? = null
    ): CleanSummaryResult {
        if (rootManager.checkRoot() != RootStatus.GRANTED) {
            return CleanSummaryResult(0L, 0, 1, "Pembersihan WhatsApp Root memerlukan otorisasi su -c.")
        }

        val selected = groups.filter { it.isSelected }
        if (selected.isEmpty()) {
            return CleanSummaryResult(0L, 0, 0, "Pilih minimal satu kategori WhatsApp.")
        }

        val flags = buildWhatsAppSafeFindFlags(customExclusions)
        val availBefore = secureDeleteEngine.getAvailableDataBytes()
        var totalFreed = 0L
        var totalDeleted = 0
        var totalErrors = 0
        val steps = selected.size.coerceAtLeast(1)

        selected.forEachIndexed { idx, group ->
            currentCoroutineContext().ensureActive()
            onProgress?.invoke(idx.toFloat() / steps.toFloat(), "Menghancurkan permanen ${group.title}...")

            val safeDirs = group.paths.filter { PathValidator.isSafeDirectoryTarget(it, customExclusions) }
            if (safeDirs.isEmpty()) return@forEachIndexed
            val quotedDirs = safeDirs.joinToString(" ") { "'${PathValidator.sanitizeShellPath(it)}'" }

            // Measure exact pre-delete bytes & paths via stat
            val preCmd = "for d in $quotedDirs; do [ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f $flags -exec stat -c '%s|%n' {} + 2>/dev/null; done"
            val preRes = rootManager.execute(preCmd, userApproved = true, timeoutMs = 30_000L, logCommand = false, logStderrAsError = false)
            var preBytes = 0L
            var preCount = 0
            val targetFiles = mutableListOf<String>()
            preRes.stdout.forEach { line ->
                val sep = line.indexOf('|')
                if (sep > 0) {
                    val sz = line.substring(0, sep).trim().toLongOrNull() ?: 0L
                    val path = line.substring(sep + 1).trim()
                    if (!isWhatsAppAccountProtected(path) &&
                        PathValidator.isSafeFileDeletionTarget(path, "", customExclusions)
                    ) {
                        preCount++
                        preBytes += sz
                        targetFiles.add(path)
                    }
                }
            }

            if (preCount == 0) return@forEachIndexed

            // Execute anti-recovery permanent wipe (account & DB files locked via flags)
            val delCmd = if (secureShred) {
                "for d in $quotedDirs; do " +
                    "if [ -d \"\$d\" ]; then " +
                    "find \"\$d\" -mindepth 1 -type f $flags ${SecureDeleteEngine.SHELL_WIPE_EXEC}; " +
                    "find \"\$d\" -mindepth 1 -type d -empty $flags -exec rmdir {} + 2>/dev/null || true; " +
                    "fi; done; sync"
            } else {
                "for d in $quotedDirs; do " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type f $flags -exec rm -f {} + 2>&1; " +
                    "[ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -type d -empty $flags -exec rmdir {} + 2>/dev/null || true; done; sync"
            }

            val delRes = rootManager.execute(
                command = delCmd,
                userApproved = true,
                timeoutMs = 90_000L,
                logCommand = true,
                logStderrAsError = true
            )
            if (!delRes.success && delRes.stderr.isNotEmpty()) {
                totalErrors += delRes.stderr.size
            }

            // Verify post-delete remaining bytes
            val postRes = rootManager.execute(preCmd, userApproved = true, timeoutMs = 25_000L, logCommand = false, logStderrAsError = false)
            var postBytes = 0L
            var postCount = 0
            postRes.stdout.forEach { line ->
                val sep = line.indexOf('|')
                if (sep > 0) {
                    val sz = line.substring(0, sep).trim().toLongOrNull() ?: 0L
                    val path = line.substring(sep + 1).trim()
                    if (!isWhatsAppAccountProtected(path)) {
                        postCount++
                        postBytes += sz
                    }
                }
            }

            val freed = (preBytes - postBytes).coerceAtLeast(0L)
            val deleted = (preCount - postCount).coerceAtLeast(0)
            totalFreed += freed
            totalDeleted += deleted

            // Remove MediaStore / Gallery ghost entries for deleted media
            secureDeleteEngine.purgeMediaStoreTraces(targetFiles)

            rootManager.appendLog(
                LogType.CLEANED,
                "WhatsApp Permanen [${group.title}]: $deleted berkas dimusnahkan (${FormatUtils.formatBytes(freed)}), akun aman."
            )
            onProgress?.invoke((idx + 1).toFloat() / steps.toFloat(), "Selesai ${group.title}")
        }

        val availAfter = secureDeleteEngine.getAvailableDataBytes()
        val statDelta = (availAfter - availBefore).coerceAtLeast(0L)
        val finalFreed = if (totalDeleted > 0 && totalFreed == 0L) statDelta else maxOf(totalFreed, 0L)

        val msg = "WhatsApp Permanen: $totalDeleted berkas dimusnahkan (${FormatUtils.formatBytes(finalFreed)}) • Akun tetap aman."
        rootManager.appendLog(LogType.CLEANED, msg)
        return CleanSummaryResult(finalFreed, totalDeleted, totalErrors, msg)
    }
}
