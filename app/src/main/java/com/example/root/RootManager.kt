package com.example.root

import android.util.Log
import com.example.model.LogType
import com.example.model.RootStatus
import com.example.model.TerminalLogEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class RootCommandResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
    val success: Boolean,
    val errorMessage: String? = null
)

class RootManager {

    companion object {
        const val LOG_FILE_PATH = "/sdcard/UltimateCleaner.log"
        private const val TAG = "RootManager"
        private const val MAX_MEMORY_LOG_LINES = 800
        private const val MAX_LOG_FILE_BYTES = 2L * 1024L * 1024L // 2 MB auto-rotate cap
        private const val RETAIN_LOG_LINES_ON_ROTATE = 500
    }

    private val _rootStatus = MutableStateFlow(RootStatus.CHECKING)
    val rootStatus: StateFlow<RootStatus> = _rootStatus.asStateFlow()

    private val _suVersion = MutableStateFlow("Uninitialized")
    val suVersion: StateFlow<String> = _suVersion.asStateFlow()

    private val _logs = MutableStateFlow<List<TerminalLogEntry>>(emptyList())
    val logs: StateFlow<List<TerminalLogEntry>> = _logs.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val logIdCounter = AtomicLong(1L)
    private val writesSinceRotateCheck = AtomicInteger(0)
    private val activeProcess = AtomicReference<Process?>(null)
    private val rootCheckMutex = Mutex()
    private val fileLogMutex = Mutex()
    private val pausedBuffer = mutableListOf<TerminalLogEntry>()
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var rootVerifiedOnce = false

    fun setLogPaused(paused: Boolean) {
        _isPaused.value = paused
        if (!paused && pausedBuffer.isNotEmpty()) {
            val drained = synchronized(pausedBuffer) {
                val copy = pausedBuffer.toList()
                pausedBuffer.clear()
                copy
            }
            _logs.update { current -> (current + drained).takeLast(MAX_MEMORY_LOG_LINES) }
        }
    }

    suspend fun appendLog(type: LogType, message: String, persistToFile: Boolean = true) {
        val timestamp = synchronized(timeFormatter) { timeFormatter.format(Date()) }
        val entry = TerminalLogEntry(
            id = logIdCounter.getAndIncrement(),
            timestamp = timestamp,
            type = type,
            message = message
        )
        if (_isPaused.value) {
            synchronized(pausedBuffer) {
                pausedBuffer.add(entry)
                if (pausedBuffer.size > MAX_MEMORY_LOG_LINES) {
                    pausedBuffer.removeAt(0)
                }
            }
        } else {
            _logs.update { current -> (current + entry).takeLast(MAX_MEMORY_LOG_LINES) }
        }

        if (persistToFile) {
            writeLineToSdcardLog("[$timestamp] [${type.name}] $message")
        }
    }

    suspend fun clearLogs() {
        clearLogsQuietly()
        appendLog(LogType.INFO, "Log sesi dan berkas terminal dibersihkan.", persistToFile = true)
    }

    suspend fun clearLogsQuietly() {
        synchronized(pausedBuffer) { pausedBuffer.clear() }
        _logs.value = emptyList()
        withContext(Dispatchers.IO) {
            fileLogMutex.withLock {
                try {
                    val directFile = File(LOG_FILE_PATH)
                    if (directFile.exists() && directFile.canWrite()) {
                        directFile.writeText("")
                    } else if (_rootStatus.value == RootStatus.GRANTED) {
                        runRawSuCommand(": > '$LOG_FILE_PATH'", timeoutMs = 5000L)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to truncate $LOG_FILE_PATH: ${e.message}")
                }
            }
        }
    }

    fun getExportableLogText(): String {
        val current = _logs.value
        val buffered = synchronized(pausedBuffer) { pausedBuffer.toList() }
        return (current + buffered)
            .distinctBy { it.id }
            .joinToString("\n") { "[${it.timestamp}] [${it.type.name}] ${it.message}" }
    }

    suspend fun checkRoot(forceRecheck: Boolean = false): RootStatus = rootCheckMutex.withLock {
        if (!forceRecheck && rootVerifiedOnce && _rootStatus.value == RootStatus.GRANTED) {
            return@withLock RootStatus.GRANTED
        }

        _rootStatus.value = RootStatus.CHECKING
        appendLog(LogType.CMD, "su -c id", persistToFile = false)

        val result = withContext(Dispatchers.IO) {
            runRawSuCommand("id", timeoutMs = 10_000L)
        }

        val combined = result.stdout.joinToString(" ")
        return@withLock when {
            result.exitCode == 0 && combined.contains("uid=0") -> {
                rootVerifiedOnce = true
                _rootStatus.value = RootStatus.GRANTED
                appendLog(LogType.INFO, "ROOT GRANTED ($combined)")
                val versionRes = withContext(Dispatchers.IO) {
                    runRawSuCommand("su -v 2>/dev/null || magisk -v 2>/dev/null || echo Superuser", timeoutMs = 4000L)
                }
                val ver = versionRes.stdout.firstOrNull()?.trim().orEmpty()
                if (ver.isNotEmpty()) {
                    _suVersion.value = ver
                }
                rotateLogFileIfNeeded()
                RootStatus.GRANTED
            }
            result.errorMessage?.contains("No such file", ignoreCase = true) == true ||
                result.errorMessage?.contains("error=2", ignoreCase = true) == true ||
                result.errorMessage?.contains("not found", ignoreCase = true) == true -> {
                rootVerifiedOnce = false
                _rootStatus.value = RootStatus.NOT_AVAILABLE
                appendLog(LogType.ERROR, "Biner superuser (su) tidak terdeteksi pada sistem.")
                RootStatus.NOT_AVAILABLE
            }
            else -> {
                rootVerifiedOnce = false
                _rootStatus.value = RootStatus.DENIED
                val errDetail = result.stderr.firstOrNull() ?: result.errorMessage ?: "Otorisasi root ditolak (exit=${result.exitCode})"
                appendLog(LogType.ERROR, "ROOT DENIED: $errDetail")
                RootStatus.DENIED
            }
        }
    }

    suspend fun execute(
        command: String,
        userApproved: Boolean = true,
        timeoutMs: Long = 45_000L,
        logCommand: Boolean = true,
        streamOutput: Boolean = false,
        logStderrAsError: Boolean = true
    ): RootCommandResult {
        if (!userApproved) {
            val msg = "Eksekusi ditolak: konfirmasi pengguna diperlukan."
            appendLog(LogType.ERROR, msg)
            return RootCommandResult(-1, emptyList(), listOf(msg), false, msg)
        }

        if (_rootStatus.value != RootStatus.GRANTED) {
            val status = checkRoot(forceRecheck = false)
            if (status != RootStatus.GRANTED) {
                val msg = "Eksekusi dibatalkan: status root = $status"
                appendLog(LogType.ERROR, msg)
                return RootCommandResult(-1, emptyList(), listOf(msg), false, msg)
            }
        }

        if (logCommand) {
            appendLog(LogType.CMD, "su -c $command")
        }

        _isExecuting.value = true
        return try {
            withContext(Dispatchers.IO) {
                val stdoutLines = mutableListOf<String>()
                val stderrLines = mutableListOf<String>()

                var process: Process? = null
                try {
                    val pb = ProcessBuilder("su", "-c", command)
                    process = pb.start()
                    activeProcess.set(process)

                    val completed = withTimeoutOrNull(timeoutMs) {
                        coroutineScope {
                            val stdoutJob = async(Dispatchers.IO) {
                                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                                    var line: String?
                                    while (reader.readLine().also { line = it } != null) {
                                        ensureActive()
                                        val cleanLine = line ?: continue
                                        stdoutLines.add(cleanLine)
                                        if (streamOutput && cleanLine.isNotBlank()) {
                                            appendLog(LogType.FOUND, cleanLine)
                                        }
                                    }
                                }
                            }
                            val stderrJob = async(Dispatchers.IO) {
                                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                                    var line: String?
                                    while (reader.readLine().also { line = it } != null) {
                                        ensureActive()
                                        val cleanLine = line ?: continue
                                        stderrLines.add(cleanLine)
                                        if (logStderrAsError && cleanLine.isNotBlank()) {
                                            appendLog(LogType.ERROR, cleanLine)
                                        }
                                    }
                                }
                            }
                            stdoutJob.await()
                            stderrJob.await()
                            process.waitFor()
                        }
                    }

                    if (completed == null) {
                        process.destroyForcibly()
                        val msg = "Batas waktu eksekusi terlampaui (${timeoutMs}ms)."
                        appendLog(LogType.ERROR, msg)
                        RootCommandResult(-1, stdoutLines, stderrLines + msg, false, msg)
                    } else {
                        RootCommandResult(
                            exitCode = completed,
                            stdout = stdoutLines,
                            stderr = stderrLines,
                            success = completed == 0,
                            errorMessage = if (completed != 0) stderrLines.firstOrNull() else null
                        )
                    }
                } catch (e: CancellationException) {
                    process?.destroyForcibly()
                    appendLog(LogType.INFO, "Operasi shell superuser dihentikan oleh pengguna.")
                    throw e
                } catch (e: Exception) {
                    val msg = e.message ?: "Kegagalan inisialisasi proses su."
                    appendLog(LogType.ERROR, msg)
                    RootCommandResult(-1, stdoutLines, stderrLines + msg, false, msg)
                } finally {
                    activeProcess.compareAndSet(process, null)
                }
            }
        } finally {
            _isExecuting.value = false
        }
    }

    fun cancelActiveProcess() {
        val process = activeProcess.getAndSet(null)
        if (process != null) {
            try {
                process.destroyForcibly()
            } catch (_: Exception) {
            }
        }
        _isExecuting.value = false
    }

    private fun runRawSuCommand(command: String, timeoutMs: Long): RootCommandResult {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", command).start()
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readLines()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readLines()
            val finished = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            } else {
                process.waitFor()
                true
            }
            if (!finished) {
                process.destroyForcibly()
                RootCommandResult(-1, stdout, stderr, false, "Timeout")
            } else {
                val exit = process.exitValue()
                RootCommandResult(exit, stdout, stderr, exit == 0, stderr.firstOrNull())
            }
        } catch (e: Exception) {
            RootCommandResult(-1, emptyList(), listOf(e.message ?: "su error"), false, e.message)
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun rotateLogFileIfNeeded() {
        withContext(Dispatchers.IO) {
            fileLogMutex.withLock {
                try {
                    val file = File(LOG_FILE_PATH)
                    if (file.exists() && file.canRead() && file.canWrite()) {
                        if (file.length() > MAX_LOG_FILE_BYTES) {
                            val tailLines = file.readLines().takeLast(RETAIN_LOG_LINES_ON_ROTATE)
                            file.writeText(tailLines.joinToString("\n") + "\n")
                        }
                        return@withLock
                    }
                    if (_rootStatus.value == RootStatus.GRANTED) {
                        val rotateCmd = "if [ -f '$LOG_FILE_PATH' ]; then sz=\$(wc -c < '$LOG_FILE_PATH' 2>/dev/null || echo 0); " +
                            "if [ \"\$sz\" -gt $MAX_LOG_FILE_BYTES ]; then tail -n $RETAIN_LOG_LINES_ON_ROTATE '$LOG_FILE_PATH' > '$LOG_FILE_PATH.rot' && mv '$LOG_FILE_PATH.rot' '$LOG_FILE_PATH'; fi; fi"
                        runRawSuCommand(rotateCmd, timeoutMs = 4000L)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun writeLineToSdcardLog(line: String) {
        withContext(Dispatchers.IO) {
            fileLogMutex.withLock {
                try {
                    val file = File(LOG_FILE_PATH)
                    val parent = file.parentFile
                    if ((parent != null && parent.exists() && file.canWrite()) || (parent?.canWrite() == true)) {
                        file.appendText(line + "\n")
                        if (writesSinceRotateCheck.incrementAndGet() >= 100) {
                            writesSinceRotateCheck.set(0)
                            if (file.length() > MAX_LOG_FILE_BYTES) {
                                val tailLines = file.readLines().takeLast(RETAIN_LOG_LINES_ON_ROTATE)
                                file.writeText(tailLines.joinToString("\n") + "\n")
                            }
                        }
                        return@withLock
                    }
                    if (_rootStatus.value == RootStatus.GRANTED) {
                        val escaped = line.replace("'", "'\"'\"'")
                        runRawSuCommand("printf '%s\\n' '$escaped' >> '$LOG_FILE_PATH'", timeoutMs = 3000L)
                        if (writesSinceRotateCheck.incrementAndGet() >= 100) {
                            writesSinceRotateCheck.set(0)
                            val rotateCmd = "if [ -f '$LOG_FILE_PATH' ]; then sz=\$(wc -c < '$LOG_FILE_PATH' 2>/dev/null || echo 0); " +
                                "if [ \"\$sz\" -gt $MAX_LOG_FILE_BYTES ]; then tail -n $RETAIN_LOG_LINES_ON_ROTATE '$LOG_FILE_PATH' > '$LOG_FILE_PATH.rot' && mv '$LOG_FILE_PATH.rot' '$LOG_FILE_PATH'; fi; fi"
                            runRawSuCommand(rotateCmd, timeoutMs = 4000L)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Log file write skipped: ${e.message}")
                }
            }
        }
    }
}
