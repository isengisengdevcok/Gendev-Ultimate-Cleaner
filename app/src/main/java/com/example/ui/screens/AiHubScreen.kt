package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AiLabTab
import com.example.model.AppSettings
import com.example.model.RootStatus
import com.example.ui.AiHubUiState
import com.example.ui.components.PremiumCard
import com.example.ui.theme.MintGreen

@Composable
fun AiHubScreen(
    uiState: AiHubUiState,
    settings: AppSettings,
    rootStatus: RootStatus,
    isRootActive: Boolean,
    onShowMessage: (String) -> Unit,
    onAnalyzeForecast: () -> Unit,
    onCleanExplosionApp: (String) -> Unit,
    onSaveBaseline: () -> Unit,
    onCompareBaseline: () -> Unit,
    onScanContextRisk: () -> Unit,
    onToggleContextItem: (String) -> Unit,
    onToggleAllContextItems: (Boolean) -> Unit,
    onDeleteContextItems: () -> Unit,
    onScanVisualRoot: () -> Unit,
    onScanVisualUris: (List<Uri>) -> Unit,
    onToggleVisualCandidate: (String) -> Unit,
    onToggleAllVisualCandidates: (Boolean) -> Unit,
    onDeleteVisualCandidates: () -> Unit,
    onAnalyzeCrash: () -> Unit,
    onClearCrashLogs: () -> Unit,
    onScanMediaRoot: () -> Unit,
    onLoadMediaUris: (List<Uri>) -> Unit,
    onToggleMediaCandidate: (String) -> Unit,
    onToggleAllMediaCandidates: (Boolean) -> Unit,
    onCompressSelectedMedia: () -> Unit,
    onSelectPreLaunchProfile: (com.example.model.PreLaunchProfile) -> Unit,
    onSelectTargetApp: (com.example.model.LaunchableAppEntry) -> Unit,
    onRunPreLaunch: () -> Unit,
    onLaunchTargetApp: () -> Unit,
    onClearToast: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(AiLabTab.FORECAST) }

    val visualPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(25)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onScanVisualUris(uris)
        }
    }

    val mediaReclaimPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(15)
    ) { uris ->
        if (uris.isNotEmpty()) {
            onLoadMediaUris(uris)
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            onShowMessage(msg)
            onClearToast()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "AI Smart Hub (On-Device)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val modeInfo = "${settings.operationMode.label} • ${if (isRootActive) "Root Aktif" else "Non-Root Aktif"}"
                                Text(
                                    text = modeInfo,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isRootActive) MintGreen else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (rootStatus == RootStatus.GRANTED) "SU" else "SAF",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (uiState.isBusy) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val pct = (uiState.progress * 100).toInt().coerceIn(0, 100)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.busyMessage ?: "Memproses analisis lokal...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MintGreen,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MintGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { uiState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = MintGreen,
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(AiLabTab.entries) { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = {
                                Text(
                                    text = tab.label,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.testTag("ai_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            item {
                when (selectedTab) {
                    AiLabTab.FORECAST -> ForecastRadarSection(
                        report = uiState.forecastReport,
                        isRootActive = isRootActive,
                        isBusy = uiState.isBusy,
                        onAnalyzeForecast = onAnalyzeForecast,
                        onCleanExplosionApp = onCleanExplosionApp
                    )
                    AiLabTab.DELTA_DIFF -> DeltaTimelineSection(
                        report = uiState.deltaReport,
                        isBusy = uiState.isBusy,
                        onSaveBaseline = onSaveBaseline,
                        onCompareBaseline = onCompareBaseline
                    )
                    AiLabTab.CONTEXT_VISUAL -> ContextAndVisualTriageSection(
                        contextItems = uiState.contextItems,
                        visualGroups = uiState.visualGroups,
                        isRootActive = isRootActive,
                        isBusy = uiState.isBusy,
                        onScanContextRisk = onScanContextRisk,
                        onToggleContextItem = onToggleContextItem,
                        onToggleAllContextItems = onToggleAllContextItems,
                        onDeleteContextItems = onDeleteContextItems,
                        onScanVisualRoot = onScanVisualRoot,
                        onPickVisualPhotos = {
                            visualPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onToggleVisualCandidate = onToggleVisualCandidate,
                        onToggleAllVisualCandidates = onToggleAllVisualCandidates,
                        onDeleteVisualCandidates = onDeleteVisualCandidates
                    )
                    AiLabTab.CRASH_ANALYZER -> CrashAnalyzerSection(
                        report = uiState.crashReport,
                        isBusy = uiState.isBusy,
                        onAnalyzeCrash = onAnalyzeCrash,
                        onClearCrashLogs = onClearCrashLogs
                    )
                    AiLabTab.MEDIA_RECLAIMER -> MediaReclaimerSection(
                        candidates = uiState.mediaCandidates,
                        isRootActive = isRootActive,
                        isBusy = uiState.isBusy,
                        onScanMediaRoot = onScanMediaRoot,
                        onPickMediaFromGallery = {
                            mediaReclaimPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onToggleCandidate = onToggleMediaCandidate,
                        onToggleAllCandidates = onToggleAllMediaCandidates,
                        onCompressSelected = onCompressSelectedMedia
                    )
                    AiLabTab.PRE_LAUNCH -> PreLaunchSection(
                        selectedProfile = uiState.selectedProfile,
                        launchableApps = uiState.launchableApps,
                        selectedTargetApp = uiState.selectedTargetApp,
                        report = uiState.preLaunchReport,
                        isBusy = uiState.isBusy,
                        onSelectProfile = onSelectPreLaunchProfile,
                        onSelectTargetApp = onSelectTargetApp,
                        onRunConditioner = onRunPreLaunch,
                        onLaunchTargetApp = onLaunchTargetApp
                    )
                }
            }
        }
    }
}
