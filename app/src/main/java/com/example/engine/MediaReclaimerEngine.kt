package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.FormatUtils
import com.example.model.LogType
import com.example.model.MediaReclaimCandidate
import com.example.model.RootStatus
import com.example.root.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

class MediaReclaimerEngine(
    private val context: Context,
    private val rootManager: RootManager
) {

    suspend fun scanMediaCandidatesFromRoot(
        customExclusions: List<String>
    ): List<MediaReclaimCandidate> = withContext(Dispatchers.IO) {
        if (rootManager.checkRoot() != RootStatus.GRANTED) return@withContext emptyList()
        val excl = PathValidator.buildFindExclusionFlags(customExclusions, includeDatabaseProtection = true)
        val cmd = "for d in /sdcard/DCIM /sdcard/Movies /sdcard/Pictures /sdcard/Download /sdcard/WhatsApp/Media; do " +
            "[ -d \"\$d\" ] && find \"\$d\" -maxdepth 3 -type f \\( " +
            "\\( \\( -iname '*.mp4' -o -iname '*.mkv' -o -iname '*.mov' \\) -size +15M \\) -o " +
            "\\( \\( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' \\) -size +2500k \\) " +
            "\\) $excl -exec stat -c '%s|%n' {} + 2>/dev/null; done"
        val res = rootManager.execute(
            command = cmd,
            userApproved = true,
            timeoutMs = 25_000L,
            logCommand = true,
            logStderrAsError = false
        )
        res.stdout.take(80).mapNotNull { line ->
            val parts = line.split("|", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val size = parts[0].toLongOrNull() ?: return@mapNotNull null
            val path = parts[1].trim()
            if (!PathValidator.isSafeFileDeletionTarget(path, context.packageName, customExclusions)) return@mapNotNull null
            val fileName = File(path).name
            val isVideo = fileName.lowercase().let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".mov") }
            MediaReclaimCandidate(
                id = path,
                path = path,
                uriString = null,
                fileName = fileName,
                isVideo = isVideo,
                originalBytes = size,
                statusNote = if (isVideo) "Video >15 MB (siap optimasi bitrate)" else "Foto >2.5 MB (siap optimasi resolusi/JPEG)"
            )
        }.sortedByDescending { it.originalBytes }
    }

    suspend fun buildCandidatesFromUris(uris: List<Uri>): List<MediaReclaimCandidate> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            val meta = queryUriMeta(uri)
            val isVideo = meta.third.startsWith("video/") ||
                meta.second.lowercase().let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".mov") }
            MediaReclaimCandidate(
                id = uri.toString(),
                path = null,
                uriString = uri.toString(),
                fileName = meta.second,
                isVideo = isVideo,
                originalBytes = meta.first,
                statusNote = if (isVideo) "Video terpilih (${FormatUtils.formatBytes(meta.first)})" else "Foto terpilih (${FormatUtils.formatBytes(meta.first)})"
            )
        }
    }

    suspend fun compressCandidate(
        candidate: MediaReclaimCandidate,
        customExclusions: List<String>
    ): MediaReclaimCandidate = withContext(Dispatchers.IO) {
        val stagingIn = File(context.cacheDir, "reclaim_in_${abs(candidate.id.hashCode())}")
        val stagingOut = File(context.cacheDir, "reclaim_out_${abs(candidate.id.hashCode())}.${if (candidate.isVideo) "mp4" else "jpg"}")
        try {
            val loaded = when {
                candidate.uriString != null -> copyUriToFile(Uri.parse(candidate.uriString), stagingIn)
                candidate.path != null -> {
                    val direct = File(candidate.path)
                    if (direct.canRead()) {
                        direct.copyTo(stagingIn, overwrite = true)
                        true
                    } else if (rootManager.checkRoot() == RootStatus.GRANTED) {
                        val safe = PathValidator.sanitizeShellPath(candidate.path)
                        val safeTmp = PathValidator.sanitizeShellPath(stagingIn.absolutePath)
                        rootManager.execute("cp '$safe' '$safeTmp' && chmod 644 '$safeTmp'", userApproved = true, timeoutMs = 20_000L, logCommand = false).success
                    } else {
                        false
                    }
                }
                else -> false
            }

            if (!loaded || !stagingIn.exists() || stagingIn.length() == 0L) {
                return@withContext candidate.copy(statusNote = "Gagal membaca berkas sumber", isCompleted = false)
            }

            val success = if (candidate.isVideo) {
                transcodeVideoFile(stagingIn, stagingOut)
            } else {
                compressImageFile(stagingIn, stagingOut)
            }

            if (!success || !stagingOut.exists() || stagingOut.length() == 0L) {
                return@withContext candidate.copy(statusNote = "Media sudah optimal atau codec tidak didukung", isCompleted = false)
            }

            val newSize = stagingOut.length()
            if (newSize >= (candidate.originalBytes * 0.92).toLong()) {
                return@withContext candidate.copy(
                    compressedBytes = newSize,
                    statusNote = "Ukuran sudah efisien (tidak diganti)",
                    isCompleted = false
                )
            }

            // Write back safely:
            val replaced = if (candidate.path != null && PathValidator.isSafeFileDeletionTarget(candidate.path, context.packageName, customExclusions)) {
                val targetFile = File(candidate.path)
                if (targetFile.canWrite()) {
                    stagingOut.copyTo(targetFile, overwrite = true)
                    true
                } else if (rootManager.checkRoot() == RootStatus.GRANTED) {
                    val safeSrc = PathValidator.sanitizeShellPath(stagingOut.absolutePath)
                    val safeDst = PathValidator.sanitizeShellPath(candidate.path)
                    rootManager.execute(
                        "cp '$safeSrc' '$safeDst' && chmod 660 '$safeDst'",
                        userApproved = true,
                        timeoutMs = 25_000L,
                        logCommand = true
                    ).success
                } else {
                    false
                }
            } else if (candidate.uriString != null) {
                // In Non-Root Photo Picker (read-only URI), save compressed copy to app's Movies/Pictures export or writable SAF URI
                try {
                    context.contentResolver.openOutputStream(Uri.parse(candidate.uriString), "wt")?.use { out ->
                        stagingOut.inputStream().use { input -> input.copyTo(out) }
                    } != null
                } catch (_: Exception) {
                    // Save optimized copy into external app media folder so user gets usable file even on read-only picker URIs
                    val exportDir = File(context.getExternalFilesDir(null), "ReclaimedMedia").apply { mkdirs() }
                    val outFile = File(exportDir, "opt_${candidate.fileName}")
                    stagingOut.copyTo(outFile, overwrite = true)
                    return@withContext candidate.copy(
                        compressedBytes = newSize,
                        isCompleted = true,
                        statusNote = "Tersimpan di ${outFile.name} (Hemat ${FormatUtils.formatBytes(candidate.originalBytes - newSize)})"
                    )
                }
            } else {
                false
            }

            val saved = (candidate.originalBytes - newSize).coerceAtLeast(0L)
            if (replaced) {
                rootManager.appendLog(LogType.CLEANED, "Media Reclaimer: ${candidate.fileName} hemat ${FormatUtils.formatBytes(saved)}")
                candidate.copy(
                    compressedBytes = newSize,
                    isCompleted = true,
                    statusNote = "Terkompresi! Hemat ${FormatUtils.formatBytes(saved)}"
                )
            } else {
                candidate.copy(statusNote = "Tidak memiliki izin tulis ke lokasi asal", isCompleted = false)
            }
        } catch (e: Exception) {
            candidate.copy(statusNote = "Gagal kompresi: ${e.message ?: "error"}", isCompleted = false)
        } finally {
            stagingIn.delete()
            stagingOut.delete()
        }
    }

    private fun compressImageFile(input: File, output: File): Boolean {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(input.absolutePath, bounds)
            val maxDim = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxDim / sample > 1920 * 2) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeFile(
                input.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return false

            val scaleDim = max(decoded.width, decoded.height)
            val finalBmp = if (scaleDim > 1920) {
                val ratio = 1920f / scaleDim.toFloat()
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                ).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }

            FileOutputStream(output).use { out ->
                finalBmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            finalBmp.recycle()
            output.exists() && output.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    // Lightweight hardware MediaCodec remux/re-encode pass to reduce oversized video bitrates safely
    private fun transcodeVideoFile(input: File, output: File): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(input.absolutePath)
            var videoTrack = -1
            var audioTrack = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") && videoTrack == -1) {
                    videoTrack = i
                    videoFormat = fmt
                } else if (mime.startsWith("audio/") && audioTrack == -1) {
                    audioTrack = i
                    audioFormat = fmt
                }
            }
            if (videoTrack == -1 || videoFormat == null) return false

            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val inMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val targetBitrate = 1_800_000 // ~1.8 Mbps clean mobile H.264 target

            val outFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            decoder = MediaCodec.createDecoderByType(inMime).apply {
                configure(videoFormat, null, null, 0)
                start()
            }
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            extractor.selectTrack(videoTrack)
            val info = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var muxVideoTrack = -1
            var muxAudioTrack = -1
            var muxStarted = false
            var inputDone = false
            var decodeDone = false
            var encodeDone = false

            val startMs = System.currentTimeMillis()
            while (!encodeDone && (System.currentTimeMillis() - startMs) < 90_000L) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                if (!decodeDone) {
                    val outIdx = decoder.dequeueOutputBuffer(info, 5_000)
                    if (outIdx >= 0) {
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val decBuf = decoder.getOutputBuffer(outIdx)
                        val encInIdx = encoder.dequeueInputBuffer(10_000)
                        if (encInIdx >= 0) {
                            val encInBuf = encoder.getInputBuffer(encInIdx)!!
                            encInBuf.clear()
                            var copied = 0
                            if (decBuf != null && info.size > 0) {
                                decBuf.position(info.offset)
                                decBuf.limit(info.offset + minOf(info.size, encInBuf.remaining()))
                                encInBuf.put(decBuf)
                                copied = decBuf.limit() - info.offset
                            }
                            encoder.queueInputBuffer(
                                encInIdx,
                                0,
                                copied,
                                info.presentationTimeUs,
                                if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            )
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (eos) decodeDone = true
                    }
                }
                val encOutIdx = encoder.dequeueOutputBuffer(encInfo, 5_000)
                when {
                    encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxStarted) {
                            muxVideoTrack = muxer.addTrack(encoder.outputFormat)
                            if (audioTrack != -1 && audioFormat != null) {
                                muxAudioTrack = muxer.addTrack(audioFormat)
                            }
                            muxer.start()
                            muxStarted = true
                        }
                    }
                    encOutIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(encOutIdx)
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && encInfo.size > 0 && muxStarted && outBuf != null) {
                            outBuf.position(encInfo.offset)
                            outBuf.limit(encInfo.offset + encInfo.size)
                            muxer.writeSampleData(muxVideoTrack, outBuf, encInfo)
                        }
                        encoder.releaseOutputBuffer(encOutIdx, false)
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encodeDone = true
                        }
                    }
                }
            }

            // Copy audio track cleanly without re-encoding
            if (muxStarted && audioTrack != -1 && muxAudioTrack != -1) {
                extractor.unselectTrack(videoTrack)
                extractor.selectTrack(audioTrack)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                val audioBuf = ByteBuffer.allocate(256 * 1024)
                val audioInfo = MediaCodec.BufferInfo()
                while (true) {
                    val sampleSize = extractor.readSampleData(audioBuf, 0)
                    if (sampleSize < 0) break
                    audioInfo.offset = 0
                    audioInfo.size = sampleSize
                    audioInfo.presentationTimeUs = extractor.sampleTime
                    audioInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxAudioTrack, audioBuf, audioInfo)
                    extractor.advance()
                }
            }
            muxStarted && output.exists() && output.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun copyUriToFile(uri: Uri, dest: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { out -> input.copyTo(out) }
            } != null
        } catch (_: Exception) {
            false
        }
    }

    private fun queryUriMeta(uri: Uri): Triple<Long, String, String> {
        val mime = context.contentResolver.getType(uri) ?: ""
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    Triple(
                        c.getLong(0).coerceAtLeast(0L),
                        c.getString(1) ?: uri.lastPathSegment ?: "media",
                        mime
                    )
                } else {
                    Triple(0L, uri.lastPathSegment ?: "media", mime)
                }
            } ?: Triple(0L, uri.lastPathSegment ?: "media", mime)
        } catch (_: Exception) {
            Triple(0L, uri.lastPathSegment ?: "media", mime)
        }
    }
}
