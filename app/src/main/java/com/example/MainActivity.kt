package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.AiHubViewModel
import com.example.ui.MainViewModel
import com.example.ui.screens.AiHubScreen
import com.example.ui.screens.AutomationScreen
import com.example.ui.screens.CleanerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LegacyCleanerScreen
import com.example.ui.screens.LogScreen
import com.example.ui.screens.PerformanceScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.UltimateCleanerTheme
import kotlinx.coroutines.launch

object AppRoutes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_CLEANER = "cleaner"
    const val ROUTE_LEGACY = "legacy"
    const val ROUTE_PERFORMANCE = "performance"
    const val ROUTE_LOG = "terminal_log"
    const val ROUTE_AI_HUB = "ai_hub"
    const val ROUTE_AUTOMATION = "automation"
    const val ROUTE_SETTINGS = "settings"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            UltimateCleanerTheme(
                darkTheme = settings.darkTheme,
                usePurpleAccent = settings.purpleAccent
            ) {
                UltimateCleanerApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UltimateCleanerApp(
    viewModel: MainViewModel,
    aiHubViewModel: AiHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoutes.ROUTE_DASHBOARD

    val rootStatus by viewModel.rootStatus.collectAsStateWithLifecycle()
    val suVersion by viewModel.suVersion.collectAsStateWithLifecycle()
    val snapshot by viewModel.systemSnapshot.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val totalFreedBytes by viewModel.totalFreedBytes.collectAsStateWithLifecycle()
    val lastCleanSummary by viewModel.lastCleanSummary.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val isLogPaused by viewModel.isLogPaused.collectAsStateWithLifecycle()
    val automationConfig by viewModel.automationConfig.collectAsStateWithLifecycle()
    val periodicWorkStatus by viewModel.periodicWorkStatus.collectAsStateWithLifecycle()
    val aiHubState by aiHubViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isRootActive = viewModel.isRootActiveNow()

    LaunchedEffect(uiState.lastOperationMessage) {
        val msg = uiState.lastOperationMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearOperationMessage()
        }
    }

    val primaryBottomNav = listOf(
        NavDestinationItem(AppRoutes.ROUTE_DASHBOARD, "Beranda", Icons.Rounded.Dashboard),
        NavDestinationItem(AppRoutes.ROUTE_CLEANER, "Cleaner", Icons.Rounded.CleaningServices),
        NavDestinationItem(AppRoutes.ROUTE_AI_HUB, "AI Hub", Icons.Rounded.AutoAwesome),
        NavDestinationItem(AppRoutes.ROUTE_PERFORMANCE, "Performa", Icons.Rounded.Speed),
        NavDestinationItem(AppRoutes.ROUTE_LOG, "Log", Icons.Rounded.Terminal)
    )

    val allRailDestinations = primaryBottomNav + listOf(
        NavDestinationItem(AppRoutes.ROUTE_LEGACY, "Legacy", Icons.Rounded.FolderSpecial),
        NavDestinationItem(AppRoutes.ROUTE_AUTOMATION, "Otomatis", Icons.Rounded.AutoMode),
        NavDestinationItem(AppRoutes.ROUTE_SETTINGS, "Setelan", Icons.Rounded.Settings)
    )

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val screenTitle = when (currentRoute) {
        AppRoutes.ROUTE_DASHBOARD -> "Beranda"
        AppRoutes.ROUTE_CLEANER -> "System Cleaner"
        AppRoutes.ROUTE_LEGACY -> "Legacy Cleaner"
        AppRoutes.ROUTE_PERFORMANCE -> "Performa & Sistem"
        AppRoutes.ROUTE_LOG -> "Terminal Log"
        AppRoutes.ROUTE_AI_HUB -> "AI Smart Hub"
        AppRoutes.ROUTE_AUTOMATION -> "Otomatisasi"
        AppRoutes.ROUTE_SETTINGS -> "Pengaturan"
        else -> "Ultimate Cleaner"
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = screenTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    actions = {
                        if (!useNavigationRail) {
                            IconButton(
                                onClick = { navigateTo(AppRoutes.ROUTE_LEGACY) },
                                modifier = Modifier.testTag("topbar_legacy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderSpecial,
                                    contentDescription = "Legacy Cleaner",
                                    tint = if (currentRoute == AppRoutes.ROUTE_LEGACY) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            IconButton(
                                onClick = { navigateTo(AppRoutes.ROUTE_AUTOMATION) },
                                modifier = Modifier.testTag("topbar_automation_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoMode,
                                    contentDescription = "Otomatisasi",
                                    tint = if (currentRoute == AppRoutes.ROUTE_AUTOMATION) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            IconButton(
                                onClick = { navigateTo(AppRoutes.ROUTE_SETTINGS) },
                                modifier = Modifier.testTag("topbar_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Pengaturan",
                                    tint = if (currentRoute == AppRoutes.ROUTE_SETTINGS) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        primaryBottomNav.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTo(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                alwaysShowLabel = true,
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        allRailDestinations.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { navigateTo(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.ROUTE_DASHBOARD,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(AppRoutes.ROUTE_DASHBOARD) {
                        DashboardScreen(
                            rootStatus = rootStatus,
                            suVersion = suVersion,
                            isRootActive = isRootActive,
                            snapshot = snapshot,
                            uiState = uiState,
                            settings = settings,
                            totalFreedBytes = totalFreedBytes,
                            lastCleanSummary = lastCleanSummary,
                            history = history,
                            onVerifyRoot = { viewModel.verifyRoot(forceRecheck = true) },
                            onCleanNow = { viewModel.requestDashboardClean(deepMode = false) },
                            onDeepClean = { viewModel.requestDashboardClean(deepMode = true) },
                            onCancelOperation = { viewModel.cancelActiveRootOperation() },
                            onNavigateToLegacy = { navigateTo(AppRoutes.ROUTE_LEGACY) },
                            onNavigateToAutomation = { navigateTo(AppRoutes.ROUTE_AUTOMATION) },
                            onNavigateToAiHub = { navigateTo(AppRoutes.ROUTE_AI_HUB) }
                        )
                    }
                    composable(AppRoutes.ROUTE_CLEANER) {
                        CleanerScreen(
                            uiState = uiState,
                            onScan = { deep -> viewModel.scanSmartJunk(deep) },
                            onToggleCategory = { type -> viewModel.toggleJunkCategory(type) },
                            onToggleAllCategories = { all -> viewModel.toggleAllJunkCategories(all) },
                            onCleanSelected = { viewModel.requestSelectedJunkClean() },
                            onRunDryRunPreview = { viewModel.runDryRunPreview() },
                            onDismissDryRunPreview = { viewModel.dismissDryRunPreview() },
                            onScanPerAppCache = { viewModel.scanPerAppCaches() },
                            onToggleAppCache = { pkg -> viewModel.toggleAppCacheSelection(pkg) },
                            onToggleAllAppCaches = { all -> viewModel.toggleAllAppCaches(all) },
                            onCleanAppCache = { viewModel.cleanSelectedAppCaches() },
                            onScanResidueAndApks = { viewModel.scanResidueAndApks() },
                            onToggleOrphan = { path -> viewModel.toggleOrphanResidue(path) },
                            onToggleOldApk = { path -> viewModel.toggleOldApk(path) },
                            onToggleAllResidueAndApks = { all -> viewModel.toggleAllResidueAndApks(all) },
                            onCleanResidueAndApks = { viewModel.cleanSelectedResidueAndApks() },
                            onScanLargeAndDuplicates = { viewModel.scanLargeAndDuplicates() },
                            onToggleLargeFile = { path -> viewModel.toggleLargeFile(path) },
                            onToggleDuplicate = { hash, path -> viewModel.toggleDuplicateFile(hash, path) },
                            onToggleAllLargeAndDuplicates = { all -> viewModel.toggleAllLargeAndDuplicates(all) },
                            onDeleteLargeAndDuplicates = { viewModel.deleteSelectedLargeAndDuplicates() },
                            onCancel = { viewModel.cancelActiveRootOperation() }
                        )
                    }
                    composable(AppRoutes.ROUTE_AI_HUB) {
                        AiHubScreen(
                            uiState = aiHubState,
                            settings = settings,
                            rootStatus = rootStatus,
                            isRootActive = isRootActive,
                            onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                            onAnalyzeForecast = { aiHubViewModel.analyzeStorageForecast() },
                            onCleanExplosionApp = { pkg -> aiHubViewModel.cleanExplosionApp(pkg) },
                            onSaveBaseline = { aiHubViewModel.saveDiffBaseline() },
                            onCompareBaseline = { aiHubViewModel.compareDiffBaseline() },
                            onScanContextRisk = { aiHubViewModel.scanContextRisk() },
                            onToggleContextItem = { key -> aiHubViewModel.toggleContextItem(key) },
                            onToggleAllContextItems = { all -> aiHubViewModel.toggleAllContextItems(all) },
                            onDeleteContextItems = { aiHubViewModel.deleteSelectedContextItems() },
                            onScanVisualRoot = { aiHubViewModel.scanVisualSimilarityRoot() },
                            onScanVisualUris = { uris -> aiHubViewModel.scanVisualSimilarityUris(uris) },
                            onToggleVisualCandidate = { key -> aiHubViewModel.toggleVisualCandidate(key) },
                            onToggleAllVisualCandidates = { all -> aiHubViewModel.toggleAllVisualCandidates(all) },
                            onDeleteVisualCandidates = { aiHubViewModel.deleteSelectedVisualCandidates() },
                            onAnalyzeCrash = { aiHubViewModel.analyzeCrashCulprits() },
                            onClearCrashLogs = { aiHubViewModel.clearCrashLogs() },
                            onScanMediaRoot = { aiHubViewModel.scanMediaReclaimRoot() },
                            onLoadMediaUris = { uris -> aiHubViewModel.loadMediaReclaimUris(uris) },
                            onToggleMediaCandidate = { id -> aiHubViewModel.toggleMediaReclaimCandidate(id) },
                            onToggleAllMediaCandidates = { all -> aiHubViewModel.toggleAllMediaCandidates(all) },
                            onCompressSelectedMedia = { aiHubViewModel.compressSelectedMedia() },
                            onSelectPreLaunchProfile = { prof -> aiHubViewModel.selectPreLaunchProfile(prof) },
                            onSelectTargetApp = { app -> aiHubViewModel.selectTargetApp(app) },
                            onRunPreLaunch = { aiHubViewModel.runPreLaunchConditioner() },
                            onLaunchTargetApp = { aiHubViewModel.launchSelectedTargetApp() },
                            onClearToast = { aiHubViewModel.clearToast() }
                        )
                    }
                    composable(AppRoutes.ROUTE_LEGACY) {
                        LegacyCleanerScreen(
                            uiState = uiState,
                            settings = settings,
                            onScanLegacy = { viewModel.scanLegacyCategories() },
                            onToggleLegacy = { type -> viewModel.toggleLegacyCategory(type) },
                            onToggleAllLegacy = { all -> viewModel.toggleAllLegacyCategories(all) },
                            onExecuteLegacyClean = { viewModel.executeLegacyClean() },
                            onScanWhatsApp = { viewModel.scanWhatsAppDeepRoot() },
                            onToggleWhatsAppGroup = { type -> viewModel.toggleWhatsAppGroup(type) },
                            onToggleAllWhatsApp = { all -> viewModel.toggleAllWhatsAppGroups(all) },
                            onExecuteWhatsAppClean = { viewModel.executeWhatsAppDeepClean() },
                            onSetSecureShred = { enabled -> viewModel.setSecureShredEnabled(enabled) },
                            onSetZeroTrace = { enabled -> viewModel.setZeroTraceHistoryEnabled(enabled) }
                        )
                    }
                    composable(AppRoutes.ROUTE_PERFORMANCE) {
                        PerformanceScreen(
                            snapshot = snapshot,
                            uiState = uiState,
                            settings = settings,
                            onEnsureLoaded = { viewModel.ensurePerformanceDetailsLoaded() },
                            onSyncStorage = { viewModel.syncStorage() },
                            onTrimStorage = { viewModel.trimStorage() },
                            onClearAppCache = { viewModel.clearAppCacheFromPerformance() },
                            onOptimizeArt = { viewModel.runArtDexoptOptimization() },
                            onRefreshSystemInfo = { viewModel.refreshSystemInfo(includeProcesses = true, includeBreakdown = true) }
                        )
                    }
                    composable(AppRoutes.ROUTE_LOG) {
                        LogScreen(
                            logs = logs,
                            isPaused = isLogPaused,
                            autoScroll = settings.autoScrollLog,
                            onTogglePause = { viewModel.toggleLogPaused() },
                            onToggleAutoScroll = { viewModel.setAutoScrollLog(it) },
                            onClearLog = { viewModel.clearLogs() },
                            getExportText = { viewModel.getExportLogContent() }
                        )
                    }
                    composable(AppRoutes.ROUTE_AUTOMATION) {
                        AutomationScreen(
                            config = automationConfig,
                            periodicWorkStatus = periodicWorkStatus,
                            isRootActive = isRootActive,
                            isIgnoringBatteryOpt = viewModel.isIgnoringBatteryOptimizations(),
                            onToggleScheduled = { enabled, hrs -> viewModel.setScheduledAutomation(enabled, hrs) },
                            onToggleScreenOff = { enabled -> viewModel.setScreenOffAutomation(enabled) },
                            onToggleBatterySaver = { enabled -> viewModel.setBatterySaverAutomation(enabled) },
                            onToggleLowStorageGuard = { enabled, gb -> viewModel.setLowStorageGuard(enabled, gb) },
                            onSetCooldownMinutes = { mins -> viewModel.setAutomationCooldown(mins) },
                            onTriggerManualTest = { viewModel.triggerManualAutomationTest() },
                            onOpenBatteryOptimization = {
                                try {
                                    context.startActivity(viewModel.buildBatteryOptimizationIntent())
                                } catch (_: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("Tidak dapat membuka pengaturan optimasi baterai.") }
                                }
                            }
                        )
                    }
                    composable(AppRoutes.ROUTE_SETTINGS) {
                        SettingsScreen(
                            settings = settings,
                            rootStatus = rootStatus,
                            suVersion = suVersion,
                            isRootActive = isRootActive,
                            hasUsageStatsAccess = viewModel.hasUsageStatsAccess(),
                            onSetOperationMode = { viewModel.setOperationMode(it) },
                            onAddSafUri = { viewModel.addSafTreeUri(it) },
                            onRemoveSafUri = { viewModel.removeSafTreeUri(it) },
                            onOpenUsageAccess = {
                                try {
                                    context.startActivity(aiHubViewModel.buildUsageAccessIntent())
                                } catch (_: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("Tidak dapat membuka pengaturan Akses Penggunaan.") }
                                }
                            },
                            onSetDarkTheme = { viewModel.setDarkTheme(it) },
                            onSetPurpleAccent = { viewModel.setPurpleAccent(it) },
                            onSetAnimations = { viewModel.setAnimationsEnabled(it) },
                            onSetAutoScroll = { viewModel.setAutoScrollLog(it) },
                            onSetConfirmation = { viewModel.setCleaningConfirmation(it) },
                            onSetSecureShred = { viewModel.setSecureShredEnabled(it) },
                            onSetZeroTrace = { viewModel.setZeroTraceHistoryEnabled(it) },
                            onWipeHistoryAndLogsNow = { viewModel.wipeHistoryAndLogsNow() },
                            onAddExclusion = { viewModel.addExclusion(it) },
                            onRemoveExclusion = { viewModel.removeExclusion(it) },
                            onClearHistory = { viewModel.clearHistory() },
                            onVerifyRoot = { viewModel.verifyRoot(forceRecheck = true) }
                        )
                    }
                }
            }
        }
    }

    uiState.pendingConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCleanConfirmation() },
            title = { Text(pending.title) },
            text = { Text(pending.description) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingClean() },
                    modifier = Modifier.testTag("confirm_clean_dialog_button")
                ) {
                    Text("JALANKAN")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCleanConfirmation() }) {
                    Text("BATAL")
                }
            }
        )
    }
}
