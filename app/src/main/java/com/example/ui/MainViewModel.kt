package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.model.AppCacheEntry
import com.example.model.AppSettings
import com.example.model.AutomationConfig
import com.example.model.CleaningHistoryItem
import com.example.model.DryRunPreview
import com.example.model.DuplicateGroup
import com.example.model.FormatUtils
import com.example.model.JunkCategoryScanResult
import com.example.model.JunkCategoryType
import com.example.model.LargeFileEntry
import com.example.model.LastCleanSummary
import com.example.model.LegacyCategoryItem
import com.example.model.LegacyCategoryType
import com.example.model.LogType
import com.example.model.OldApkEntry
import com.example.model.OperationMode
import com.example.model.OrphanedResidueItem
import com.example.model.RootStatus
import com.example.model.SystemSnapshot
import com.example.model.TerminalLogEntry
import com.example.model.WhatsAppCategoryType
import com.example.model.WhatsAppTargetGroup
import android.content.Intent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingCleanConfirmation(
    val title: String,
    val description: String,
    val deepMode: Boolean,
    val fromSmartCleanerScreen: Boolean
)

data class CleanerUiState(
    val isScanning: Boolean = false,
    val isCleaning: Boolean = false,
    val isDeepMode: Boolean = false,
    val operationProgress: Float = 0f,
    val statusBanner: String? = null,
    val junkCategories: List<JunkCategoryScanResult> = emptyList(),
    val legacyCategories: List<LegacyCategoryItem> = emptyList(),
    val isLegacyScanning: Boolean = false,
    val isLegacyCleaning: Boolean = false,
    val whatsAppGroups: List<WhatsAppTargetGroup> = emptyList(),
    val isWhatsAppBusy: Boolean = false,
    val isPerformanceBusy: Boolean = false,
    val pendingConfirmation: PendingCleanConfirmation? = null,
    val dryRunPreview: DryRunPreview? = null,
    val appCaches: List<AppCacheEntry> = emptyList(),
    val orphanedResidues: List<OrphanedResidueItem> = emptyList(),
    val oldApks: List<OldApkEntry> = emptyList(),
    val largeFiles: List<LargeFileEntry> = emptyList(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val isAdvancedBusy: Boolean = false,
    val lastOperationMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.get(application)
    private val rootManager = container.rootManager
    private val repository = container.repository
    private val cleanerEngine = container.cleanerEngine
    private val nonRootEngine = container.nonRootEngine
    private val advancedScanner = container.advancedScannerEngine
    private val whatsAppCleanerEngine = container.whatsAppCleanerEngine
    private val secureDeleteEngine = container.secureDeleteEngine
    private val storageAnalyzer = container.storageAnalyzer
    private val systemInfoManager = container.systemInfoManager
    private val automationManager = container.automationManager

    val rootStatus: StateFlow<RootStatus> = rootManager.rootStatus
    val suVersion: StateFlow<String> = rootManager.suVersion
    val logs: StateFlow<List<TerminalLogEntry>> = rootManager.logs
    val isLogPaused: StateFlow<Boolean> = rootManager.isPaused
    val isRootExecuting: StateFlow<Boolean> = rootManager.isExecuting

    val periodicWorkStatus: StateFlow<String> = automationManager.periodicWorkStatusFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "Tidak Aktif"
    )

    val settings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    val totalFreedBytes: StateFlow<Long> = repository.totalFreedBytesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0L
    )

    val lastCleanSummary: StateFlow<LastCleanSummary> = repository.lastCleanFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LastCleanSummary()
    )

    val history: StateFlow<List<CleaningHistoryItem>> = repository.historyFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val automationConfig: StateFlow<AutomationConfig> = repository.automationFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AutomationConfig()
    )

    private val _systemSnapshot = MutableStateFlow(SystemSnapshot())
    val systemSnapshot: StateFlow<SystemSnapshot> = _systemSnapshot.asStateFlow()

    private val _uiState = MutableStateFlow(
        CleanerUiState(
            junkCategories = cleanerEngine.getSmartJunkDefinitions(),
            legacyCategories = cleanerEngine.getLegacyCategories(),
            whatsAppGroups = whatsAppCleanerEngine.getDefaultGroups()
        )
    )
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    private var activeOperationJob: Job? = null
    private var performanceDetailsLoadedOnce = false

    init {
        // Fast cold-start: load local device/RAM/Storage/Battery immediately without blocking on shell du/ps!
        viewModelScope.launch {
            _systemSnapshot.value = systemInfoManager.collectSnapshot(
                includeProcesses = false,
                includeStorageBreakdown = false
            )
        }
        // Run root check asynchronously so UI renders instantaneously
        viewModelScope.launch {
            rootManager.appendLog(LogType.INFO, "Ultimate Cleaner Root Edition by Gendev siap.")
            rootManager.checkRoot(forceRecheck = false)
        }
    }

    private suspend fun resolveUseRoot(): Boolean {
        return when (repository.getSettingsOnce().operationMode) {
            OperationMode.ROOT -> rootManager.checkRoot(forceRecheck = false) == RootStatus.GRANTED
            OperationMode.NON_ROOT -> false
            OperationMode.AUTO -> rootManager.checkRoot(forceRecheck = false) == RootStatus.GRANTED
        }
    }

    fun isRootActiveNow(): Boolean {
        return when (settings.value.operationMode) {
            OperationMode.ROOT -> rootStatus.value == RootStatus.GRANTED
            OperationMode.NON_ROOT -> false
            OperationMode.AUTO -> rootStatus.value == RootStatus.GRANTED
        }
    }

    private suspend fun finalizeZeroTraceIfNeeded(s: AppSettings) {
        if (s.zeroTraceHistoryEnabled) {
            secureDeleteEngine.wipeSystemGhostCaches(true)
            repository.clearHistoryLogsOnly()
            rootManager.clearLogsQuietly()
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean = automationManager.isIgnoringBatteryOptimizations()
    fun buildBatteryOptimizationIntent(): Intent = automationManager.buildBatteryOptimizationIntent()
    fun hasUsageStatsAccess(): Boolean = nonRootEngine.hasUsageStatsPermission()
    fun buildUsageAccessIntent(): Intent = nonRootEngine.buildUsageAccessIntent()

    fun verifyRoot(forceRecheck: Boolean = true) {
        viewModelScope.launch {
            rootManager.checkRoot(forceRecheck = forceRecheck)
            _systemSnapshot.value = systemInfoManager.collectSnapshot(
                includeProcesses = false,
                includeStorageBreakdown = false
            )
        }
    }

    fun ensurePerformanceDetailsLoaded() {
        if (!performanceDetailsLoadedOnce) {
            performanceDetailsLoadedOnce = true
            refreshSystemInfo(includeProcesses = true, includeBreakdown = true)
        }
    }

    fun refreshSystemInfo(includeProcesses: Boolean = true, includeBreakdown: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformanceBusy = true) }
            try {
                val snapshot = systemInfoManager.collectSnapshot(
                    includeProcesses = includeProcesses,
                    includeStorageBreakdown = includeBreakdown
                )
                _systemSnapshot.value = snapshot
                if (includeProcesses && includeBreakdown) {
                    performanceDetailsLoadedOnce = true
                }
            } finally {
                _uiState.update { it.copy(isPerformanceBusy = false) }
            }
        }
    }

    fun requestDashboardClean(deepMode: Boolean) {
        val currentSettings = settings.value
        if (currentSettings.cleaningConfirmation) {
            _uiState.update {
                it.copy(
                    pendingConfirmation = PendingCleanConfirmation(
                        title = if (deepMode) "Otorisasi Deep Clean" else "Otorisasi Clean Now",
                        description = if (deepMode) {
                            "Deep Clean akan memindai dan membersihkan seluruh direktori cache aplikasi, Android/data, sistem aman, log, berkas temporer, thumbnail, dan residu WebView melalui su -c."
                        } else {
                            "Clean Now akan memindai dan membersihkan cache aplikasi, Android/data, berkas temporer, thumbnail, dan folder sampah melalui su -c."
                        },
                        deepMode = deepMode,
                        fromSmartCleanerScreen = false
                    )
                )
            }
        } else {
            executeDashboardClean(deepMode = deepMode, userApproved = true)
        }
    }

    fun requestSelectedJunkClean() {
        val currentSettings = settings.value
        val selectedCount = _uiState.value.junkCategories.count { it.isSelected }
        if (selectedCount == 0) {
            _uiState.update { it.copy(lastOperationMessage = "Pilih minimal satu kategori pembersihan.") }
            return
        }
        if (currentSettings.cleaningConfirmation) {
            _uiState.update {
                it.copy(
                    pendingConfirmation = PendingCleanConfirmation(
                        title = "Eksekusi Pembersihan ($selectedCount Kategori)",
                        description = "Menghapus berkas cache dan sampah pada kategori terpilih melalui su -c. Basis data aplikasi dan berkas terproteksi dikecualikan secara ketat.",
                        deepMode = _uiState.value.isDeepMode,
                        fromSmartCleanerScreen = true
                    )
                )
            }
        } else {
            cleanSelectedJunk(userApproved = true)
        }
    }

    fun dismissCleanConfirmation() {
        _uiState.update { it.copy(pendingConfirmation = null) }
    }

    fun confirmPendingClean() {
        val pending = _uiState.value.pendingConfirmation ?: return
        _uiState.update { it.copy(pendingConfirmation = null) }
        if (pending.fromSmartCleanerScreen) {
            cleanSelectedJunk(userApproved = true)
        } else {
            executeDashboardClean(deepMode = pending.deepMode, userApproved = true)
        }
    }

    fun runDryRunPreview() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    operationProgress = 0.2f,
                    statusBanner = "Menyusun daftar pratinjau Dry-Run..."
                )
            }
            try {
                val preview = cleanerEngine.previewSmartJunkDryRun(
                    categories = _uiState.value.junkCategories,
                    customExclusions = settings.value.customExclusions,
                    deepMode = _uiState.value.isDeepMode
                )
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        operationProgress = 1f,
                        statusBanner = null,
                        dryRunPreview = preview
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isScanning = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    fun dismissDryRunPreview() {
        _uiState.update { it.copy(dryRunPreview = null) }
    }

    private fun executeDashboardClean(deepMode: Boolean, userApproved: Boolean) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    isDeepMode = deepMode,
                    operationProgress = 0.02f,
                    statusBanner = if (deepMode) "Analisis Deep Scan aktif..." else "Memindai direktori sistem..."
                )
            }
            try {
                val s = repository.getSettingsOnce()
                val customExclusions = s.customExclusions
                val useRoot = resolveUseRoot()
                if (s.operationMode == OperationMode.ROOT && !useRoot) {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            isCleaning = false,
                            operationProgress = 0f,
                            statusBanner = null,
                            lastOperationMessage = "Mode Root dipilih, namun akses su -c belum diberikan. Beralih ke Auto/Non-Root atau berikan izin root."
                        )
                    }
                    return@launch
                }

                // Phase 1: Scan (0% -> 45%)
                val scanned = if (useRoot) {
                    cleanerEngine.scanSmartJunk(
                        customExclusions = customExclusions,
                        deepMode = deepMode,
                        onProgress = { fraction, label ->
                            _uiState.update {
                                it.copy(
                                    operationProgress = (fraction * 0.45f).coerceIn(0.02f, 0.45f),
                                    statusBanner = label
                                )
                            }
                        }
                    )
                } else {
                    nonRootEngine.scanNonRootJunk(
                        currentCategories = _uiState.value.junkCategories,
                        grantedSafUris = s.grantedSafUris,
                        customExclusions = customExclusions,
                        onProgress = { fraction, label ->
                            _uiState.update {
                                it.copy(
                                    operationProgress = (fraction * 0.45f).coerceIn(0.02f, 0.45f),
                                    statusBanner = label
                                )
                            }
                        }
                    )
                }

                _uiState.update {
                    it.copy(
                        isScanning = false,
                        isCleaning = true,
                        junkCategories = scanned,
                        operationProgress = 0.48f,
                        statusBanner = if (useRoot) {
                            if (deepMode) "Mengeksekusi Deep Clean..." else "Membersihkan cache & sampah..."
                        } else {
                            "Membersihkan cache internal & SAF (Non-Root)..."
                        }
                    )
                }

                // Phase 2: Clean (48% -> 95%)
                val summary = if (useRoot) {
                    cleanerEngine.cleanSmartJunk(
                        categories = scanned,
                        customExclusions = customExclusions,
                        userApproved = userApproved,
                        deepMode = deepMode,
                        secureShred = s.secureShredEnabled,
                        onProgress = { fraction, label ->
                            _uiState.update {
                                it.copy(
                                    operationProgress = (0.48f + fraction * 0.47f).coerceIn(0.48f, 0.95f),
                                    statusBanner = label
                                )
                            }
                        }
                    )
                } else {
                    nonRootEngine.cleanNonRootJunk(
                        categories = scanned,
                        grantedSafUris = s.grantedSafUris,
                        customExclusions = customExclusions,
                        secureShred = s.secureShredEnabled,
                        onProgress = { fraction, label ->
                            _uiState.update {
                                it.copy(
                                    operationProgress = (0.48f + fraction * 0.47f).coerceIn(0.48f, 0.95f),
                                    statusBanner = label
                                )
                            }
                        }
                    )
                }

                val modeLabel = when {
                    useRoot && deepMode -> "Deep Clean"
                    useRoot -> "Clean Now"
                    else -> "Non-Root Clean"
                }
                repository.recordCleaningRun(
                    mode = modeLabel,
                    freedBytes = summary.totalFreedBytes,
                    filesDeleted = summary.totalFilesDeleted,
                    details = summary.summaryMessage,
                    success = summary.errorsCount == 0
                )
                finalizeZeroTraceIfNeeded(s)

                _uiState.update {
                    it.copy(
                        operationProgress = 1f,
                        statusBanner = "Memperbarui statistik sistem..."
                    )
                }
                _systemSnapshot.value = systemInfoManager.collectSnapshot(
                    includeProcesses = false,
                    includeStorageBreakdown = false
                )

                _uiState.update {
                    it.copy(
                        isCleaning = false,
                        operationProgress = 0f,
                        junkCategories = scanned.map { cat ->
                            if (cat.isSelected) cat.copy(fileCount = 0, sizeBytes = 0L, sampleFiles = emptyList(), statusNote = "Dibersihkan") else cat
                        },
                        statusBanner = null,
                        lastOperationMessage = summary.summaryMessage
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        isCleaning = false,
                        operationProgress = 0f,
                        statusBanner = null,
                        lastOperationMessage = "Operasi dihentikan: ${e.message ?: "Dibatalkan"}"
                    )
                }
            }
        }
    }

    fun scanSmartJunk(deepMode: Boolean = _uiState.value.isDeepMode) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    isDeepMode = deepMode,
                    operationProgress = 0.03f,
                    statusBanner = "Memindai kategori sampah..."
                )
            }
            try {
                val s = repository.getSettingsOnce()
                val useRoot = resolveUseRoot()
                val results = if (useRoot) {
                    cleanerEngine.scanSmartJunk(
                        customExclusions = s.customExclusions,
                        deepMode = deepMode,
                        onProgress = { fraction, label ->
                            _uiState.update {
                                it.copy(
                                    operationProgress = fraction.coerceIn(0.03f, 1f),
                                    statusBanner = label
                                )
                            }
                        }
                    )
                } else {
                    nonRootEngine.scanNonRootJunk(
                        currentCategories = _uiState.value.junkCategories,
                        grantedSafUris = s.grantedSafUris,
                        customExclusions = s.customExclusions,
                        onProgress = { fraction, label ->
                            _uiState.update {
                                it.copy(
                                    operationProgress = fraction.coerceIn(0.03f, 1f),
                                    statusBanner = label
                                )
                            }
                        }
                    )
                }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        operationProgress = 0f,
                        junkCategories = results,
                        statusBanner = null,
                        lastOperationMessage = if (useRoot) "Pemindaian Root selesai." else "Pemindaian Non-Root selesai."
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        operationProgress = 0f,
                        statusBanner = null,
                        lastOperationMessage = "Pemindaian dihentikan."
                    )
                }
            }
        }
    }

    private fun cleanSelectedJunk(userApproved: Boolean) {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCleaning = true,
                    operationProgress = 0.05f,
                    statusBanner = "Membersihkan kategori terpilih..."
                )
            }
            try {
                val s = repository.getSettingsOnce()
                val customExclusions = s.customExclusions
                val useRoot = resolveUseRoot()
                val summary = if (useRoot) {
                    cleanerEngine.cleanSmartJunk(
                        categories = _uiState.value.junkCategories,
                        customExclusions = customExclusions,
                        userApproved = userApproved,
                        deepMode = _uiState.value.isDeepMode,
                        secureShred = s.secureShredEnabled,
                        onProgress = { fraction, label ->
                            _uiState.update {
                                it.copy(
                                    operationProgress = (fraction * 0.9f).coerceIn(0.05f, 0.92f),
                                    statusBanner = label
                                )
                            }
                        }
                    )
                } else {
                    nonRootEngine.cleanNonRootJunk(
                        categories = _uiState.value.junkCategories,
                        grantedSafUris = s.grantedSafUris,
                        customExclusions = customExclusions,
                        secureShred = s.secureShredEnabled,
                        onProgress = { fraction, label ->
                            _uiState.update {
                                it.copy(
                                    operationProgress = (fraction * 0.9f).coerceIn(0.05f, 0.92f),
                                    statusBanner = label
                                )
                            }
                        }
                    )
                }
                repository.recordCleaningRun(
                    mode = if (!useRoot) "Non-Root Cleaner" else if (_uiState.value.isDeepMode) "Deep Clean (Selektif)" else "Smart Cleaner",
                    freedBytes = summary.totalFreedBytes,
                    filesDeleted = summary.totalFilesDeleted,
                    details = summary.summaryMessage,
                    success = summary.errorsCount == 0
                )
                finalizeZeroTraceIfNeeded(s)
                val updated = if (useRoot) {
                    cleanerEngine.scanSmartJunk(
                        customExclusions = customExclusions,
                        deepMode = _uiState.value.isDeepMode,
                        onProgress = { _, _ -> _uiState.update { it.copy(operationProgress = 0.96f) } }
                    )
                } else {
                    nonRootEngine.scanNonRootJunk(
                        currentCategories = _uiState.value.junkCategories,
                        grantedSafUris = s.grantedSafUris,
                        customExclusions = customExclusions
                    )
                }
                _systemSnapshot.value = systemInfoManager.collectSnapshot(
                    includeProcesses = false,
                    includeStorageBreakdown = false
                )
                _uiState.update {
                    it.copy(
                        isCleaning = false,
                        operationProgress = 0f,
                        junkCategories = updated,
                        statusBanner = null,
                        lastOperationMessage = summary.summaryMessage
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isCleaning = false,
                        operationProgress = 0f,
                        statusBanner = null,
                        lastOperationMessage = "Pembersihan dihentikan."
                    )
                }
            }
        }
    }

    fun toggleJunkCategory(type: JunkCategoryType) {
        _uiState.update { state ->
            state.copy(
                junkCategories = state.junkCategories.map {
                    if (it.type == type) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun toggleAllJunkCategories(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(junkCategories = state.junkCategories.map { it.copy(isSelected = selectAll) })
        }
    }

    // Per-App Cache Inspector (Root or Non-Root StorageStatsManager)
    fun scanPerAppCaches() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(isAdvancedBusy = true, operationProgress = 0.25f, statusBanner = "Mengindeks cache per aplikasi...") }
            try {
                val useRoot = resolveUseRoot()
                val list = if (useRoot) {
                    advancedScanner.scanPerAppCache(settings.value.customExclusions)
                } else {
                    nonRootEngine.scanPerAppCachesViaUsageStats()
                }
                val msg = if (!useRoot && list.isEmpty() && !nonRootEngine.hasUsageStatsPermission()) {
                    "Mode Non-Root: berikan izin Akses Penggunaan di Pengaturan untuk melihat cache per aplikasi."
                } else {
                    "${list.size} aplikasi terindeks."
                }
                _uiState.update {
                    it.copy(
                        isAdvancedBusy = false,
                        operationProgress = 0f,
                        statusBanner = null,
                        appCaches = list,
                        lastOperationMessage = msg
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isAdvancedBusy = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    fun toggleAppCacheSelection(packageName: String) {
        _uiState.update { state ->
            state.copy(
                appCaches = state.appCaches.map {
                    if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun toggleAllAppCaches(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(appCaches = state.appCaches.map { it.copy(isSelected = selectAll) })
        }
    }

    fun cleanSelectedAppCaches() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(isAdvancedBusy = true, operationProgress = 0.4f, statusBanner = "Membersihkan cache aplikasi terpilih...") }
            try {
                val s = repository.getSettingsOnce()
                val res = advancedScanner.cleanSelectedAppCaches(_uiState.value.appCaches, s.customExclusions)
                repository.recordCleaningRun("Per-App Cache", res.totalFreedBytes, res.totalFilesDeleted, res.summaryMessage, res.errorsCount == 0)
                finalizeZeroTraceIfNeeded(s)
                val updated = advancedScanner.scanPerAppCache(s.customExclusions)
                _uiState.update {
                    it.copy(
                        isAdvancedBusy = false,
                        operationProgress = 0f,
                        statusBanner = null,
                        appCaches = updated,
                        lastOperationMessage = res.summaryMessage
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isAdvancedBusy = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    // Orphaned App Residue & Old APK Cleanup
    fun scanResidueAndApks() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(isAdvancedBusy = true, operationProgress = 0.2f, statusBanner = "Memindai residu aplikasi...") }
            try {
                val excl = settings.value.customExclusions
                val orphans = advancedScanner.scanOrphanedResidue(excl)
                _uiState.update { it.copy(operationProgress = 0.65f, statusBanner = "Memindai berkas installer APK...") }
                val apks = advancedScanner.scanOldApks(excl)
                _uiState.update {
                    it.copy(
                        isAdvancedBusy = false,
                        operationProgress = 0f,
                        statusBanner = null,
                        orphanedResidues = orphans,
                        oldApks = apks,
                        lastOperationMessage = "${orphans.size} residu & ${apks.size} berkas APK terdeteksi."
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isAdvancedBusy = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    fun toggleOrphanResidue(path: String) {
        _uiState.update { state ->
            state.copy(
                orphanedResidues = state.orphanedResidues.map {
                    if (it.path == path) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun toggleOldApk(path: String) {
        _uiState.update { state ->
            state.copy(
                oldApks = state.oldApks.map {
                    if (it.path == path) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun toggleAllResidueAndApks(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(
                orphanedResidues = state.orphanedResidues.map { it.copy(isSelected = selectAll) },
                oldApks = state.oldApks.map { it.copy(isSelected = selectAll) }
            )
        }
    }

    fun cleanSelectedResidueAndApks() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(isAdvancedBusy = true, operationProgress = 0.35f, statusBanner = "Menghapus residu & installer APK terpilih...") }
            try {
                val s = repository.getSettingsOnce()
                val excl = s.customExclusions
                val orphanRes = advancedScanner.cleanOrphanedResidue(
                    items = _uiState.value.orphanedResidues,
                    customExclusions = excl,
                    secureShred = s.secureShredEnabled
                )
                _uiState.update { it.copy(operationProgress = 0.7f) }
                val selectedApks = _uiState.value.oldApks.filter { it.isSelected }
                val apkRes = if (selectedApks.isNotEmpty()) {
                    advancedScanner.deleteValidatedPaths(
                        paths = selectedApks.map { it.path },
                        expectedBytes = selectedApks.sumOf { it.sizeBytes },
                        customExclusions = excl,
                        operationLabel = "Installer APK",
                        secureShred = s.secureShredEnabled
                    )
                } else {
                    com.example.engine.CleanSummaryResult(0L, 0, 0, "")
                }
                val totalFreed = orphanRes.totalFreedBytes + apkRes.totalFreedBytes
                val totalFiles = orphanRes.totalFilesDeleted + apkRes.totalFilesDeleted
                val msg = "Pembersihan residu & APK selesai (${FormatUtils.formatBytes(totalFreed)})"
                if (totalFiles > 0 || totalFreed > 0L) {
                    repository.recordCleaningRun("Residue & APK", totalFreed, totalFiles, msg, true)
                    finalizeZeroTraceIfNeeded(s)
                }
                val orphans = advancedScanner.scanOrphanedResidue(excl)
                val apks = advancedScanner.scanOldApks(excl)
                _uiState.update {
                    it.copy(
                        isAdvancedBusy = false,
                        operationProgress = 0f,
                        statusBanner = null,
                        orphanedResidues = orphans,
                        oldApks = apks,
                        lastOperationMessage = msg
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isAdvancedBusy = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    // Large Files & SHA-256 Duplicate Finder (Root or Non-Root SAF)
    fun scanLargeAndDuplicates() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(isAdvancedBusy = true, operationProgress = 0.25f, statusBanner = "Memindai berkas berukuran besar...") }
            try {
                val s = repository.getSettingsOnce()
                val excl = s.customExclusions
                val useRoot = resolveUseRoot()
                if (useRoot) {
                    val large = advancedScanner.scanLargeFiles(minSizeMb = 50, customExclusions = excl)
                    _uiState.update { it.copy(operationProgress = 0.65f, statusBanner = "Memverifikasi duplikat SHA-256...") }
                    val dups = advancedScanner.scanDuplicateFiles(excl)
                    _uiState.update {
                        it.copy(
                            isAdvancedBusy = false,
                            operationProgress = 0f,
                            statusBanner = null,
                            largeFiles = large,
                            duplicateGroups = dups,
                            lastOperationMessage = "${large.size} berkas besar & ${dups.size} grup duplikat terdeteksi."
                        )
                    }
                } else {
                    val (large, apks) = nonRootEngine.scanSafLargeFilesAndApks(s.grantedSafUris, excl)
                    _uiState.update {
                        it.copy(
                            isAdvancedBusy = false,
                            operationProgress = 0f,
                            statusBanner = null,
                            largeFiles = large,
                            oldApks = apks,
                            duplicateGroups = emptyList(),
                            lastOperationMessage = if (s.grantedSafUris.isEmpty()) {
                                "Mode Non-Root: tambahkan izin folder SAF di Pengaturan untuk memindai berkas besar."
                            } else {
                                "Non-Root SAF: ${large.size} berkas besar & ${apks.size} APK ditemukan."
                            }
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isAdvancedBusy = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    fun toggleLargeFile(path: String) {
        _uiState.update { state ->
            state.copy(
                largeFiles = state.largeFiles.map {
                    if (it.path == path) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun toggleDuplicateFile(hash: String, path: String) {
        _uiState.update { state ->
            state.copy(
                duplicateGroups = state.duplicateGroups.map { group ->
                    if (group.hash == hash) {
                        val updated = group.selectedForDeletion.toMutableSet()
                        if (updated.contains(path)) updated.remove(path) else updated.add(path)
                        group.copy(selectedForDeletion = updated)
                    } else {
                        group
                    }
                }
            )
        }
    }

    fun toggleAllLargeAndDuplicates(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(
                largeFiles = state.largeFiles.map { it.copy(isSelected = selectAll) },
                duplicateGroups = state.duplicateGroups.map { group ->
                    val updatedSet = if (selectAll && group.filePaths.size > 1) {
                        group.filePaths.drop(1).toSet()
                    } else {
                        emptySet()
                    }
                    group.copy(selectedForDeletion = updatedSet)
                }
            )
        }
    }

    fun deleteSelectedLargeAndDuplicates() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update { it.copy(isAdvancedBusy = true, operationProgress = 0.45f, statusBanner = "Menghapus berkas besar & duplikat terpilih...") }
            try {
                val s = repository.getSettingsOnce()
                val excl = s.customExclusions
                val selectedLarge = _uiState.value.largeFiles.filter { it.isSelected }
                val selectedDupPaths = _uiState.value.duplicateGroups.flatMap { group ->
                    group.selectedForDeletion.map { path -> path to group.sizeBytes }
                }
                val allPaths = (selectedLarge.map { it.path } + selectedDupPaths.map { it.first }).distinct()
                val expectedBytes = selectedLarge.sumOf { it.sizeBytes } + selectedDupPaths.sumOf { it.second }

                val safTargets = allPaths.filter { it.startsWith("content://") }
                val rootTargets = allPaths.filterNot { it.startsWith("content://") }

                var totalFreed = 0L
                var totalDeleted = 0
                var totalErr = 0

                if (safTargets.isNotEmpty()) {
                    val (f, c) = nonRootEngine.deleteSafUris(safTargets, secureShred = s.secureShredEnabled)
                    totalFreed += f
                    totalDeleted += c
                }
                if (rootTargets.isNotEmpty()) {
                    val res = advancedScanner.deleteValidatedPaths(
                        paths = rootTargets,
                        expectedBytes = expectedBytes,
                        customExclusions = excl,
                        operationLabel = "Large & Duplicate",
                        secureShred = s.secureShredEnabled
                    )
                    totalFreed += res.totalFreedBytes
                    totalDeleted += res.totalFilesDeleted
                    totalErr += res.errorsCount
                }

                val summaryMsg = "$totalDeleted berkas dihapus (${FormatUtils.formatBytes(totalFreed)})"
                if (totalDeleted > 0 || totalFreed > 0L) {
                    repository.recordCleaningRun("Large & Duplicate", totalFreed, totalDeleted, summaryMsg, totalErr == 0)
                    finalizeZeroTraceIfNeeded(s)
                }

                if (resolveUseRoot()) {
                    val large = advancedScanner.scanLargeFiles(minSizeMb = 50, customExclusions = excl)
                    val dups = advancedScanner.scanDuplicateFiles(excl)
                    _uiState.update {
                        it.copy(
                            isAdvancedBusy = false,
                            operationProgress = 0f,
                            statusBanner = null,
                            largeFiles = large,
                            duplicateGroups = dups,
                            lastOperationMessage = summaryMsg
                        )
                    }
                } else {
                    val (large, _) = nonRootEngine.scanSafLargeFilesAndApks(s.grantedSafUris, excl)
                    _uiState.update {
                        it.copy(
                            isAdvancedBusy = false,
                            operationProgress = 0f,
                            statusBanner = null,
                            largeFiles = large,
                            lastOperationMessage = summaryMsg
                        )
                    }
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isAdvancedBusy = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    fun cancelActiveRootOperation() {
        activeOperationJob?.cancel()
        rootManager.cancelActiveProcess()
        _uiState.update {
            it.copy(
                isScanning = false,
                isCleaning = false,
                isLegacyScanning = false,
                isLegacyCleaning = false,
                isWhatsAppBusy = false,
                isAdvancedBusy = false,
                operationProgress = 0f,
                statusBanner = null,
                lastOperationMessage = "Operasi root dihentikan."
            )
        }
    }

    // Legacy Cleaner — NEVER auto-run, confirmed via standard dialog button
    fun toggleLegacyCategory(type: LegacyCategoryType) {
        _uiState.update { state ->
            state.copy(
                legacyCategories = state.legacyCategories.map {
                    if (it.type == type) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun toggleAllLegacyCategories(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(legacyCategories = state.legacyCategories.map { it.copy(isSelected = selectAll) })
        }
    }

    fun scanLegacyCategories() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLegacyScanning = true,
                    operationProgress = 0.03f,
                    statusBanner = "Menghitung ukuran kategori Legacy..."
                )
            }
            try {
                val scanned = cleanerEngine.scanLegacyCategories(
                    currentItems = _uiState.value.legacyCategories,
                    customExclusions = settings.value.customExclusions,
                    onProgress = { fraction, label ->
                        _uiState.update {
                            it.copy(
                                operationProgress = fraction.coerceIn(0.03f, 1f),
                                statusBanner = label
                            )
                        }
                    }
                )
                _uiState.update {
                    it.copy(
                        isLegacyScanning = false,
                        operationProgress = 0f,
                        legacyCategories = scanned,
                        statusBanner = null,
                        lastOperationMessage = "Analisis kategori Legacy selesai."
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLegacyScanning = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    fun executeLegacyClean() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLegacyCleaning = true,
                    operationProgress = 0.05f,
                    statusBanner = "Menghapus permanen kategori terpilih..."
                )
            }
            try {
                val s = repository.getSettingsOnce()
                val summary = cleanerEngine.cleanLegacyCategories(
                    selectedItems = _uiState.value.legacyCategories,
                    customExclusions = s.customExclusions,
                    userConfirmed = true,
                    secureShred = s.secureShredEnabled,
                    onProgress = { fraction, label ->
                        _uiState.update {
                            it.copy(
                                operationProgress = (fraction * 0.9f).coerceIn(0.05f, 0.9f),
                                statusBanner = label
                            )
                        }
                    }
                )
                repository.recordCleaningRun(
                    mode = "Legacy Cleaner",
                    freedBytes = summary.totalFreedBytes,
                    filesDeleted = summary.totalFilesDeleted,
                    details = summary.summaryMessage,
                    success = summary.errorsCount == 0
                )
                finalizeZeroTraceIfNeeded(s)
                val rescanned = cleanerEngine.scanLegacyCategories(
                    currentItems = _uiState.value.legacyCategories.map { it.copy(isSelected = false) },
                    customExclusions = s.customExclusions
                )
                _systemSnapshot.value = systemInfoManager.collectSnapshot(
                    includeProcesses = false,
                    includeStorageBreakdown = false
                )
                _uiState.update {
                    it.copy(
                        isLegacyCleaning = false,
                        operationProgress = 0f,
                        legacyCategories = rescanned,
                        statusBanner = null,
                        lastOperationMessage = summary.summaryMessage
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isLegacyCleaning = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    // WhatsApp Root Deep Cleaner (Account & DB strictly protected)
    fun toggleWhatsAppGroup(type: WhatsAppCategoryType) {
        _uiState.update { state ->
            state.copy(
                whatsAppGroups = state.whatsAppGroups.map {
                    if (it.type == type) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun toggleAllWhatsAppGroups(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(whatsAppGroups = state.whatsAppGroups.map { it.copy(isSelected = selectAll) })
        }
    }

    fun scanWhatsAppDeepRoot() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isWhatsAppBusy = true,
                    operationProgress = 0.04f,
                    statusBanner = "Memindai seluruh berkas WhatsApp (Root, Akun Aman)..."
                )
            }
            try {
                val s = repository.getSettingsOnce()
                val scanned = whatsAppCleanerEngine.scanAllWhatsAppRoot(
                    currentGroups = _uiState.value.whatsAppGroups,
                    customExclusions = s.customExclusions,
                    onProgress = { fraction, label ->
                        _uiState.update {
                            it.copy(
                                operationProgress = fraction.coerceIn(0.04f, 1f),
                                statusBanner = label
                            )
                        }
                    }
                )
                val totalFiles = scanned.sumOf { it.fileCount }
                val totalBytes = scanned.sumOf { it.sizeBytes }
                _uiState.update {
                    it.copy(
                        isWhatsAppBusy = false,
                        operationProgress = 0f,
                        whatsAppGroups = scanned,
                        statusBanner = null,
                        lastOperationMessage = "WhatsApp Scan selesai: $totalFiles berkas (${FormatUtils.formatBytes(totalBytes)}), akun tetap terkunci aman."
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isWhatsAppBusy = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    fun executeWhatsAppDeepClean() {
        activeOperationJob?.cancel()
        activeOperationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isWhatsAppBusy = true,
                    operationProgress = 0.05f,
                    statusBanner = "Menghancurkan permanen data media WhatsApp..."
                )
            }
            try {
                val s = repository.getSettingsOnce()
                val summary = whatsAppCleanerEngine.cleanWhatsAppPermanent(
                    groups = _uiState.value.whatsAppGroups,
                    customExclusions = s.customExclusions,
                    secureShred = s.secureShredEnabled,
                    onProgress = { fraction, label ->
                        _uiState.update {
                            it.copy(
                                operationProgress = (fraction * 0.9f).coerceIn(0.05f, 0.9f),
                                statusBanner = label
                            )
                        }
                    }
                )
                repository.recordCleaningRun(
                    mode = "WhatsApp Permanen",
                    freedBytes = summary.totalFreedBytes,
                    filesDeleted = summary.totalFilesDeleted,
                    details = summary.summaryMessage,
                    success = summary.errorsCount == 0
                )
                finalizeZeroTraceIfNeeded(s)
                val rescanned = whatsAppCleanerEngine.scanAllWhatsAppRoot(
                    currentGroups = _uiState.value.whatsAppGroups,
                    customExclusions = s.customExclusions
                )
                _systemSnapshot.value = systemInfoManager.collectSnapshot(
                    includeProcesses = false,
                    includeStorageBreakdown = false
                )
                _uiState.update {
                    it.copy(
                        isWhatsAppBusy = false,
                        operationProgress = 0f,
                        whatsAppGroups = rescanned,
                        statusBanner = null,
                        lastOperationMessage = summary.summaryMessage
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isWhatsAppBusy = false, operationProgress = 0f, statusBanner = null) }
            }
        }
    }

    // Performance Actions
    fun syncStorage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformanceBusy = true) }
            val ok = storageAnalyzer.syncStorage()
            _uiState.update {
                it.copy(
                    isPerformanceBusy = false,
                    lastOperationMessage = if (ok) "Sinkronisasi filesystem selesai." else "Sinkronisasi gagal: periksa otorisasi root."
                )
            }
            refreshSystemInfo(includeProcesses = true, includeBreakdown = true)
        }
    }

    fun trimStorage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformanceBusy = true) }
            val (_, msg) = storageAnalyzer.trimStorage()
            _uiState.update {
                it.copy(
                    isPerformanceBusy = false,
                    lastOperationMessage = msg
                )
            }
            refreshSystemInfo(includeProcesses = true, includeBreakdown = true)
        }
    }

    fun runArtDexoptOptimization() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformanceBusy = true) }
            val (_, msg) = storageAnalyzer.runArtDexoptOptimization()
            _uiState.update {
                it.copy(
                    isPerformanceBusy = false,
                    lastOperationMessage = msg
                )
            }
            refreshSystemInfo(includeProcesses = true, includeBreakdown = false)
        }
    }

    fun clearAppCacheFromPerformance() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPerformanceBusy = true) }
            val s = repository.getSettingsOnce()
            val customExclusions = s.customExclusions
            val cacheTargets = cleanerEngine.getSmartJunkDefinitions().filter {
                it.type == JunkCategoryType.APP_CACHE || it.type == JunkCategoryType.ANDROID_DATA_CACHE
            }.map { it.copy(isSelected = true) }
            val summary = if (resolveUseRoot()) {
                cleanerEngine.cleanSmartJunk(
                    categories = cacheTargets,
                    customExclusions = customExclusions,
                    userApproved = true,
                    deepMode = false,
                    secureShred = s.secureShredEnabled
                )
            } else {
                nonRootEngine.cleanNonRootJunk(
                    categories = cacheTargets,
                    grantedSafUris = s.grantedSafUris,
                    customExclusions = customExclusions,
                    secureShred = s.secureShredEnabled
                )
            }
            repository.recordCleaningRun(
                mode = "Clear App Cache",
                freedBytes = summary.totalFreedBytes,
                filesDeleted = summary.totalFilesDeleted,
                details = summary.summaryMessage,
                success = summary.errorsCount == 0
            )
            finalizeZeroTraceIfNeeded(s)
            _uiState.update {
                it.copy(
                    isPerformanceBusy = false,
                    lastOperationMessage = summary.summaryMessage
                )
            }
            refreshSystemInfo(includeProcesses = true, includeBreakdown = true)
        }
    }

    fun clearLogs() {
        viewModelScope.launch { rootManager.clearLogs() }
    }

    fun toggleLogPaused() {
        rootManager.setLogPaused(!isLogPaused.value)
    }

    fun getExportLogContent(): String = rootManager.getExportableLogText()

    fun setScheduledAutomation(enabled: Boolean, intervalHours: Int = automationConfig.value.intervalHours) {
        viewModelScope.launch {
            automationManager.setScheduledCleaning(enabled, intervalHours)
            rootManager.appendLog(
                LogType.INFO,
                "Pembersihan Terjadwal: ${if (enabled) "AKTIF (${intervalHours}j)" else "NONAKTIF"}"
            )
        }
    }

    fun setScreenOffAutomation(enabled: Boolean) {
        viewModelScope.launch {
            automationManager.setScreenOffCleaning(enabled)
            rootManager.appendLog(
                LogType.INFO,
                "Pembersihan Layar Mati: ${if (enabled) "AKTIF" else "NONAKTIF"}"
            )
        }
    }

    fun setBatterySaverAutomation(enabled: Boolean) {
        viewModelScope.launch {
            automationManager.setBatterySaverCleaning(enabled)
            rootManager.appendLog(
                LogType.INFO,
                "Pembersihan Penghemat Baterai: ${if (enabled) "AKTIF" else "NONAKTIF"}"
            )
        }
    }

    fun setLowStorageGuard(enabled: Boolean, thresholdGb: Int = automationConfig.value.lowStorageThresholdGb) {
        viewModelScope.launch {
            automationManager.setLowStorageGuard(enabled, thresholdGb)
            rootManager.appendLog(
                LogType.INFO,
                "Penjaga Memori Rendah (<${thresholdGb}GB): ${if (enabled) "AKTIF" else "NONAKTIF"}"
            )
        }
    }

    fun setAutomationCooldown(minutes: Int) {
        viewModelScope.launch {
            automationManager.setCooldownMinutes(minutes)
            rootManager.appendLog(LogType.INFO, "Jeda otomatisasi diatur ke $minutes menit.")
        }
    }

    fun triggerManualAutomationTest() {
        automationManager.enqueueImmediateAutoClean("Uji-Manual")
        viewModelScope.launch {
            rootManager.appendLog(LogType.INFO, "Antrean tugas WorkManager (Uji Manual) dikirim.")
        }
        _uiState.update { it.copy(lastOperationMessage = "Tugas otomatisasi dikirim ke antrean WorkManager.") }
    }

    fun setOperationMode(mode: OperationMode) {
        viewModelScope.launch {
            repository.updateOperationMode(mode)
            rootManager.appendLog(LogType.INFO, "Mode eksekusi diubah ke: ${mode.label}")
            _uiState.update { it.copy(lastOperationMessage = "Mode eksekusi: ${mode.label}") }
        }
    }

    fun addSafTreeUri(uriString: String) {
        viewModelScope.launch {
            repository.addSafUri(uriString)
            _uiState.update { it.copy(lastOperationMessage = "Folder SAF berhasil ditambahkan untuk mode Non-Root.") }
        }
    }

    fun removeSafTreeUri(uriString: String) {
        viewModelScope.launch {
            repository.removeSafUri(uriString)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.updateDarkTheme(enabled) }
    }

    fun setPurpleAccent(enabled: Boolean) {
        viewModelScope.launch { repository.updatePurpleAccent(enabled) }
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.updateAnimationsEnabled(enabled) }
    }

    fun setAutoScrollLog(enabled: Boolean) {
        viewModelScope.launch { repository.updateAutoScrollLog(enabled) }
    }

    fun setCleaningConfirmation(enabled: Boolean) {
        viewModelScope.launch { repository.updateCleaningConfirmation(enabled) }
    }

    fun setSecureShredEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSecureShredEnabled(enabled)
            _uiState.update {
                it.copy(
                    lastOperationMessage = if (enabled) {
                        "Hapus Permanen Anti-Recovery AKTIF (timpa blok & MediaStore)."
                    } else {
                        "Hapus standar aktif (penimpaan blok dinonaktifkan)."
                    }
                )
            }
        }
    }

    fun setZeroTraceHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateZeroTraceHistoryEnabled(enabled)
            if (enabled) {
                rootManager.clearLogsQuietly()
            }
            _uiState.update {
                it.copy(
                    lastOperationMessage = if (enabled) {
                        "Mode Tanpa Riwayat (Zero-Trace) AKTIF: daftar riwayat & log otomatis bersih."
                    } else {
                        "Mode Tanpa Riwayat dinonaktifkan."
                    }
                )
            }
        }
    }

    fun wipeHistoryAndLogsNow() {
        viewModelScope.launch {
            secureDeleteEngine.wipeSystemGhostCaches(true)
            repository.clearHistoryLogsOnly()
            rootManager.clearLogsQuietly()
            _uiState.update { it.copy(lastOperationMessage = "Seluruh catatan riwayat, cache thumbnail, & log sesi dihapus bersih.") }
        }
    }

    fun addExclusion(pattern: String) {
        viewModelScope.launch { repository.addExclusion(pattern) }
    }

    fun removeExclusion(pattern: String) {
        viewModelScope.launch { repository.removeExclusion(pattern) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _uiState.update { it.copy(lastOperationMessage = "Riwayat pembersihan telah direset.") }
        }
    }

    fun clearOperationMessage() {
        _uiState.update { it.copy(lastOperationMessage = null) }
    }
}
