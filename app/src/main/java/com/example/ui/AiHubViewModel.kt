package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.model.ContextRiskItem
import com.example.model.CrashAnalyzerReport
import com.example.model.FormatUtils
import com.example.model.LaunchableAppEntry
import com.example.model.MediaReclaimCandidate
import com.example.model.OperationMode
import com.example.model.PreLaunchProfile
import com.example.model.PreLaunchReport
import com.example.model.RootStatus
import com.example.model.SafFolderItem
import com.example.model.StorageDeltaReport
import com.example.model.StorageForecastReport
import com.example.model.VisualSimilarityGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiHubUiState(
    val isBusy: Boolean = false,
    val progress: Float = 0f,
    val busyMessage: String? = null,
    val toastMessage: String? = null,
    // 1. Forecaster
    val forecastReport: StorageForecastReport = StorageForecastReport(),
    // 2. Delta Diff
    val deltaReport: StorageDeltaReport = StorageDeltaReport(),
    // 3. Context & Visual
    val contextItems: List<ContextRiskItem> = emptyList(),
    val visualGroups: List<VisualSimilarityGroup> = emptyList(),
    // 4. Crash & ANR
    val crashReport: CrashAnalyzerReport = CrashAnalyzerReport(),
    // 5. Media Reclaimer
    val mediaCandidates: List<MediaReclaimCandidate> = emptyList(),
    // 6. Pre-Launch
    val selectedProfile: PreLaunchProfile = PreLaunchProfile.GAMING,
    val launchableApps: List<LaunchableAppEntry> = emptyList(),
    val selectedTargetApp: LaunchableAppEntry? = null,
    val preLaunchReport: PreLaunchReport? = null,
    // Non-Root SAF & Usage Stats
    val safFolders: List<SafFolderItem> = emptyList(),
    val hasUsageStatsAccess: Boolean = false
)

class AiHubViewModel(application: Application) : AndroidViewModel(application) {

    private val container = AppContainer.get(application)
    private val rootManager = container.rootManager
    private val repository = container.repository
    private val nonRootEngine = container.nonRootEngine
    private val advancedScanner = container.advancedScannerEngine
    private val secureDeleteEngine = container.secureDeleteEngine
    private val forecasterEngine = container.storageForecasterEngine
    private val visualAndContextEngine = container.visualAndContextEngine
    private val crashAnalyzerEngine = container.crashAnalyzerEngine
    private val mediaReclaimerEngine = container.mediaReclaimerEngine
    private val preLaunchEngine = container.preLaunchEngine

    val settings = repository.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.model.AppSettings()
    )

    val rootStatus = rootManager.rootStatus

    private val _uiState = MutableStateFlow(AiHubUiState())
    val uiState: StateFlow<AiHubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = preLaunchEngine.getLaunchableUserApps()
            val hasUsage = nonRootEngine.hasUsageStatsPermission()
            _uiState.update {
                it.copy(
                    launchableApps = apps,
                    selectedTargetApp = apps.firstOrNull(),
                    hasUsageStatsAccess = hasUsage
                )
            }
            refreshSafStatus()
        }
    }

    private suspend fun finalizeZeroTraceIfNeeded(s: com.example.model.AppSettings) {
        if (s.zeroTraceHistoryEnabled) {
            secureDeleteEngine.wipeSystemGhostCaches(true)
            repository.clearHistoryLogsOnly()
            rootManager.clearLogsQuietly()
        }
    }

    private suspend fun isUseRoot(): Boolean {
        val mode = repository.getSettingsOnce().operationMode
        return when (mode) {
            OperationMode.ROOT -> rootManager.checkRoot(forceRecheck = false) == RootStatus.GRANTED
            OperationMode.NON_ROOT -> false
            OperationMode.AUTO -> rootManager.checkRoot(forceRecheck = false) == RootStatus.GRANTED
        }
    }

    fun refreshCapabilities() {
        viewModelScope.launch {
            _uiState.update { it.copy(hasUsageStatsAccess = nonRootEngine.hasUsageStatsPermission()) }
            refreshSafStatus()
        }
    }

    fun buildUsageAccessIntent(): Intent = nonRootEngine.buildUsageAccessIntent()

    fun addSafTreeUri(uriString: String) {
        viewModelScope.launch {
            repository.addSafUri(uriString)
            refreshSafStatus()
            _uiState.update { it.copy(toastMessage = "Folder SAF berhasil diotorisasi untuk mode Non-Root.") }
        }
    }

    fun removeSafTreeUri(uriString: String) {
        viewModelScope.launch {
            repository.removeSafUri(uriString)
            refreshSafStatus()
        }
    }

    private suspend fun refreshSafStatus() {
        val s = repository.getSettingsOnce()
        val folders = nonRootEngine.inspectSafFolders(s.grantedSafUris, s.customExclusions)
        _uiState.update { it.copy(safFolders = folders) }
    }

    // Feature 1: Storage Growth Forecaster & Cache-Explosion Radar
    fun analyzeStorageForecast() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.25f, busyMessage = "Menganalisis tren pertumbuhan memori...") }
            try {
                val useRoot = isUseRoot()
                val s = repository.getSettingsOnce()
                val caches = if (useRoot) {
                    advancedScanner.scanPerAppCache(s.customExclusions)
                } else {
                    nonRootEngine.scanPerAppCachesViaUsageStats()
                }
                _uiState.update { it.copy(progress = 0.75f, busyMessage = "Menghitung laju pertumbuhan & radar cache...") }
                val report = forecasterEngine.recordAndAnalyzeForecast(caches)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progress = 0f,
                        busyMessage = null,
                        forecastReport = report,
                        toastMessage = "Analisis prediktor penyimpanan diperbarui."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isBusy = false, progress = 0f, busyMessage = null, toastMessage = e.message) }
            }
        }
    }

    fun cleanExplosionApp(packageName: String) {
        viewModelScope.launch {
            if (!isUseRoot()) {
                _uiState.update { it.copy(toastMessage = "Pembersihan cache aplikasi lain memerlukan Mode Root (su -c).") }
                return@launch
            }
            _uiState.update { it.copy(isBusy = true, progress = 0.5f, busyMessage = "Membersihkan cache $packageName...") }
            val s = repository.getSettingsOnce()
            val targets = advancedScanner.scanPerAppCache(s.customExclusions).map {
                it.copy(isSelected = it.packageName == packageName)
            }
            val res = advancedScanner.cleanSelectedAppCaches(targets, s.customExclusions)
            repository.recordCleaningRun("Cache Radar", res.totalFreedBytes, res.totalFilesDeleted, res.summaryMessage, res.errorsCount == 0)
            finalizeZeroTraceIfNeeded(s)
            _uiState.update { it.copy(isBusy = false, progress = 0f, busyMessage = null, toastMessage = res.summaryMessage) }
            analyzeStorageForecast()
        }
    }

    // Feature 2: Storage Delta Timeline
    fun saveDiffBaseline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.4f, busyMessage = "Menyimpan patokan indeks direktori...") }
            val useRoot = isUseRoot()
            val s = repository.getSettingsOnce()
            val report = forecasterEngine.saveStorageBaseline(useRoot, s.grantedSafUris, s.customExclusions)
            _uiState.update {
                it.copy(isBusy = false, progress = 0f, busyMessage = null, deltaReport = report, toastMessage = "Titik patokan disimpan.")
            }
        }
    }

    fun compareDiffBaseline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.45f, busyMessage = "Membandingkan perubahan ukuran direktori...") }
            val useRoot = isUseRoot()
            val s = repository.getSettingsOnce()
            val report = forecasterEngine.compareWithBaseline(useRoot, s.grantedSafUris, s.customExclusions)
            _uiState.update {
                it.copy(isBusy = false, progress = 0f, busyMessage = null, deltaReport = report, toastMessage = report.statusMessage)
            }
        }
    }

    // Feature 3: Smart Context & Visual Similarity Triage
    fun scanContextRisk() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.35f, busyMessage = "Menilai konteks risiko dokumen & unduhan...") }
            val useRoot = isUseRoot()
            val s = repository.getSettingsOnce()
            val items = visualAndContextEngine.scanDownloadContextRisk(useRoot, s.grantedSafUris, s.customExclusions)
            val msg = if (items.isEmpty() && !useRoot && s.grantedSafUris.isEmpty()) {
                "Tambahkan folder SAF terlebih dahulu untuk memindai tanpa Root."
            } else {
                "${items.size} berkas dievaluasi dengan skor risiko konteks."
            }
            _uiState.update { it.copy(isBusy = false, progress = 0f, busyMessage = null, contextItems = items, toastMessage = msg) }
        }
    }

    fun toggleContextItem(pathOrUri: String) {
        _uiState.update { state ->
            state.copy(
                contextItems = state.contextItems.map { item ->
                    val key = item.uriString ?: item.path
                    if (key == pathOrUri && item.riskLevel != com.example.model.ContextRiskLevel.PROTECTED) {
                        item.copy(isSelected = !item.isSelected)
                    } else item
                }
            )
        }
    }

    fun toggleAllContextItems(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(
                contextItems = state.contextItems.map { item ->
                    if (item.riskLevel == com.example.model.ContextRiskLevel.PROTECTED) {
                        item.copy(isSelected = false)
                    } else {
                        item.copy(isSelected = selectAll)
                    }
                }
            )
        }
    }

    fun deleteSelectedContextItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.5f, busyMessage = "Membersihkan berkas aman terpilih...") }
            val s = repository.getSettingsOnce()
            val res = visualAndContextEngine.deleteContextItems(
                items = _uiState.value.contextItems,
                customExclusions = s.customExclusions,
                secureShred = s.secureShredEnabled
            )
            if (res.totalFilesDeleted > 0 || res.totalFreedBytes > 0L) {
                repository.recordCleaningRun("Context Triage", res.totalFreedBytes, res.totalFilesDeleted, res.summaryMessage, res.errorsCount == 0)
                finalizeZeroTraceIfNeeded(s)
            }
            val updated = visualAndContextEngine.scanDownloadContextRisk(isUseRoot(), s.grantedSafUris, s.customExclusions)
            _uiState.update { it.copy(isBusy = false, progress = 0f, busyMessage = null, contextItems = updated, toastMessage = res.summaryMessage) }
        }
    }

    fun scanVisualSimilarityRoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.3f, busyMessage = "Menghitung dHash & ketajaman foto (lokal)...") }
            val s = repository.getSettingsOnce()
            val groups = visualAndContextEngine.scanVisualSimilarityFromRoot(s.customExclusions)
            val msg = if (groups.isEmpty()) "Tidak ada foto mirip/buram ditemukan (atau pilih foto via Pemilih Media)." else "${groups.size} grup kemiripan visual terdeteksi."
            _uiState.update { it.copy(isBusy = false, progress = 0f, busyMessage = null, visualGroups = groups, toastMessage = msg) }
        }
    }

    fun scanVisualSimilarityUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.35f, busyMessage = "Menganalisis kemiripan visual ${uris.size} foto...") }
            val groups = visualAndContextEngine.scanVisualSimilarityFromUris(uris)
            val msg = if (groups.isEmpty()) "Semua foto terpilih unik & tajam." else "${groups.size} grup kemiripan/buram ditemukan."
            _uiState.update { it.copy(isBusy = false, progress = 0f, busyMessage = null, visualGroups = groups, toastMessage = msg) }
        }
    }

    fun toggleVisualCandidate(key: String) {
        _uiState.update { state ->
            state.copy(
                visualGroups = state.visualGroups.map { g ->
                    g.copy(
                        candidates = g.candidates.map { c ->
                            if ((c.uriString ?: c.path) == key) c.copy(isSelected = !c.isSelected) else c
                        }
                    )
                }
            )
        }
    }

    fun toggleAllVisualCandidates(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(
                visualGroups = state.visualGroups.map { g ->
                    val isSimilarity = g.groupId.startsWith("sim_") && g.candidates.size > 1
                    g.copy(
                        candidates = g.candidates.mapIndexed { idx, c ->
                            val target = if (!selectAll) false else if (isSimilarity) idx > 0 else true
                            c.copy(isSelected = target)
                        }
                    )
                }
            )
        }
    }

    fun deleteSelectedVisualCandidates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.5f, busyMessage = "Menghapus foto duplikat/buram terpilih...") }
            val s = repository.getSettingsOnce()
            val res = visualAndContextEngine.deleteVisualCandidates(
                groups = _uiState.value.visualGroups,
                customExclusions = s.customExclusions,
                secureShred = s.secureShredEnabled
            )
            if (res.totalFilesDeleted > 0 || res.totalFreedBytes > 0L) {
                repository.recordCleaningRun("Visual Triage", res.totalFreedBytes, res.totalFilesDeleted, res.summaryMessage, res.errorsCount == 0)
                finalizeZeroTraceIfNeeded(s)
            }
            _uiState.update { it.copy(isBusy = false, progress = 0f, busyMessage = null, visualGroups = emptyList(), toastMessage = res.summaryMessage) }
        }
    }

    // Feature 4: Silent Crash & ANR Culprit Analyzer
    fun analyzeCrashCulprits() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.4f, busyMessage = "Membaca header log crash & ANR sistem...") }
            val report = crashAnalyzerEngine.analyzeCrashLogs(isUseRoot())
            _uiState.update { it.copy(isBusy = false, progress = 0f, busyMessage = null, crashReport = report, toastMessage = report.summaryMessage) }
        }
    }

    fun clearCrashLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.5f, busyMessage = "Membersihkan berkas log crash & tombstone...") }
            val s = repository.getSettingsOnce()
            val res = crashAnalyzerEngine.clearCrashLogs(_uiState.value.crashReport.culprits)
            if (res.totalFilesDeleted > 0 || res.totalFreedBytes > 0L) {
                repository.recordCleaningRun("Crash & ANR Clean", res.totalFreedBytes, res.totalFilesDeleted, res.summaryMessage, res.errorsCount == 0)
                finalizeZeroTraceIfNeeded(s)
            }
            val updated = crashAnalyzerEngine.analyzeCrashLogs(isUseRoot())
            _uiState.update { it.copy(isBusy = false, progress = 0f, busyMessage = null, crashReport = updated, toastMessage = res.summaryMessage) }
        }
    }

    // Feature 5: On-Device Media Reclaimer
    fun scanMediaReclaimRoot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.3f, busyMessage = "Memindai foto & video berukuran besar...") }
            val s = repository.getSettingsOnce()
            val list = mediaReclaimerEngine.scanMediaCandidatesFromRoot(s.customExclusions)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    progress = 0f,
                    busyMessage = null,
                    mediaCandidates = list,
                    toastMessage = "${list.size} media kandidat kompresi ditemukan."
                )
            }
        }
    }

    fun loadMediaReclaimUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.3f, busyMessage = "Memuat metadata media terpilih...") }
            val list = mediaReclaimerEngine.buildCandidatesFromUris(uris)
            _uiState.update {
                it.copy(isBusy = false, progress = 0f, busyMessage = null, mediaCandidates = list, toastMessage = "${list.size} media siap dikompresi.")
            }
        }
    }

    fun toggleMediaReclaimCandidate(id: String) {
        _uiState.update { state ->
            state.copy(
                mediaCandidates = state.mediaCandidates.map {
                    if (it.id == id && !it.isCompleted) it.copy(isSelected = !it.isSelected) else it
                }
            )
        }
    }

    fun toggleAllMediaCandidates(selectAll: Boolean) {
        _uiState.update { state ->
            state.copy(
                mediaCandidates = state.mediaCandidates.map {
                    if (!it.isCompleted) it.copy(isSelected = selectAll) else it
                }
            )
        }
    }

    fun compressSelectedMedia() {
        viewModelScope.launch {
            val targets = _uiState.value.mediaCandidates.filter { it.isSelected && !it.isCompleted }
            if (targets.isEmpty()) {
                _uiState.update { it.copy(toastMessage = "Pilih minimal satu media untuk dikompresi.") }
                return@launch
            }
            val s = repository.getSettingsOnce()
            val updatedList = _uiState.value.mediaCandidates.toMutableList()
            var totalSaved = 0L
            var successCount = 0

            targets.forEachIndexed { idx, candidate ->
                _uiState.update {
                    it.copy(
                        isBusy = true,
                        progress = idx.toFloat() / targets.size.coerceAtLeast(1).toFloat(),
                        busyMessage = "Mengompresi ${candidate.fileName}..."
                    )
                }
                val result = mediaReclaimerEngine.compressCandidate(candidate, s.customExclusions)
                val pos = updatedList.indexOfFirst { it.id == candidate.id }
                if (pos >= 0) updatedList[pos] = result
                if (result.isCompleted && result.compressedBytes != null) {
                    totalSaved += (result.originalBytes - result.compressedBytes).coerceAtLeast(0L)
                    successCount++
                }
            }

            if (totalSaved > 0L) {
                repository.recordCleaningRun(
                    mode = "Media Reclaimer",
                    freedBytes = totalSaved,
                    filesDeleted = successCount,
                    details = "$successCount media dioptimasi, hemat ${FormatUtils.formatBytes(totalSaved)}",
                    success = true
                )
                finalizeZeroTraceIfNeeded(s)
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    progress = 0f,
                    busyMessage = null,
                    mediaCandidates = updatedList,
                    toastMessage = "Kompresi selesai: $successCount media hemat ${FormatUtils.formatBytes(totalSaved)}."
                )
            }
        }
    }

    // Feature 6: Precision Pre-Launch Conditioner
    fun selectPreLaunchProfile(profile: PreLaunchProfile) {
        _uiState.update { it.copy(selectedProfile = profile) }
    }

    fun selectTargetApp(app: LaunchableAppEntry) {
        _uiState.update { it.copy(selectedTargetApp = app) }
    }

    fun runPreLaunchConditioner() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, progress = 0.45f, busyMessage = "Menjalankan pengondisian RAM & I/O...") }
            val report = preLaunchEngine.runPreLaunchConditioner(
                profile = _uiState.value.selectedProfile,
                targetApp = _uiState.value.selectedTargetApp,
                useRoot = isUseRoot()
            )
            _uiState.update {
                it.copy(
                    isBusy = false,
                    progress = 0f,
                    busyMessage = null,
                    preLaunchReport = report,
                    toastMessage = "Kondisi sistem siap diluncurkan."
                )
            }
        }
    }

    fun launchSelectedTargetApp() {
        val pkg = _uiState.value.selectedTargetApp?.packageName ?: return
        val ok = preLaunchEngine.launchApp(pkg)
        if (!ok) {
            _uiState.update { it.copy(toastMessage = "Aplikasi tidak dapat diluncurkan.") }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
