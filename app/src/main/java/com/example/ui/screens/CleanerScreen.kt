package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FormatUtils
import com.example.model.JunkCategoryType
import com.example.ui.CleanerUiState
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.PremiumCard
import com.example.ui.components.SelectAllControlRow
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MintGreen

private enum class CleanerSubTab(val label: String) {
    SMART_JUNK("Smart Junk"),
    PER_APP("Per-App"),
    RESIDUE_APK("Residu & APK"),
    LARGE_DUP("File Besar & Duplikat")
}

@Composable
fun CleanerScreen(
    uiState: CleanerUiState,
    onScan: (Boolean) -> Unit,
    onToggleCategory: (JunkCategoryType) -> Unit,
    onToggleAllCategories: (Boolean) -> Unit,
    onCleanSelected: () -> Unit,
    onRunDryRunPreview: () -> Unit,
    onDismissDryRunPreview: () -> Unit,
    onScanPerAppCache: () -> Unit,
    onToggleAppCache: (String) -> Unit,
    onToggleAllAppCaches: (Boolean) -> Unit,
    onCleanAppCache: () -> Unit,
    onScanResidueAndApks: () -> Unit,
    onToggleOrphan: (String) -> Unit,
    onToggleOldApk: (String) -> Unit,
    onToggleAllResidueAndApks: (Boolean) -> Unit,
    onCleanResidueAndApks: () -> Unit,
    onScanLargeAndDuplicates: () -> Unit,
    onToggleLargeFile: (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onToggleAllLargeAndDuplicates: (Boolean) -> Unit,
    onDeleteLargeAndDuplicates: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(CleanerSubTab.SMART_JUNK) }
    val busy = uiState.isScanning || uiState.isCleaning || uiState.isAdvancedBusy
    val animatedOpProgress by animateFloatAsState(
        targetValue = uiState.operationProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 240),
        label = "cleaner_op_progress"
    )
    val opPercent = (animatedOpProgress * 100).toInt().coerceIn(0, 100)
    val selectedCategories = uiState.junkCategories.filter { it.isSelected }
    val selectedBytes = selectedCategories.sumOf { it.sizeBytes }
    val selectedFiles = selectedCategories.sumOf { it.fileCount }
    val allJunkSelected = uiState.junkCategories.isNotEmpty() && selectedCategories.size == uiState.junkCategories.size

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CleanerSubTab.entries.forEach { tab ->
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("cleaner_tab_${tab.name}")
                        )
                    }
                }
            }

            when (activeTab) {
                CleanerSubTab.SMART_JUNK -> {
                    item {
                        PremiumCard(cornerRadius = 22.dp) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Smart Junk & Deep Clean Engine",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Estimasi ruang dapat dibebaskan: ${FormatUtils.formatBytes(selectedBytes)} ($selectedFiles berkas)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MintGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Deep Clean Engine Mode",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Cakup /data/system/dropbox, code_cache, dan berkas .dmp",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = uiState.isDeepMode,
                                        onCheckedChange = { if (!busy) onScan(it) },
                                        modifier = Modifier.testTag("deep_mode_switch")
                                    )
                                }
                            }

                            if (busy) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.statusBanner ?: "Memproses perintah su -c...",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$opPercent%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { animatedOpProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (busy) {
                                Button(
                                    onClick = onCancel,
                                    contentPadding = ActionButtonPadding,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("cleaner_cancel_button"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Hentikan Proses", maxLines = 1)
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { onScan(uiState.isDeepMode) },
                                            contentPadding = ActionButtonPadding,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("scan_junk_button"),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Pindai Sampah", maxLines = 1, softWrap = false)
                                        }
                                        Button(
                                            onClick = onCleanSelected,
                                            contentPadding = ActionButtonPadding,
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("clean_selected_junk_button"),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Bersihkan", maxLines = 1, softWrap = false)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = onRunDryRunPreview,
                                        contentPadding = ActionButtonPadding,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("dry_run_preview_button"),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pratinjau Dry-Run (Tanpa Hapus)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Proteksi aktif: Basis data (*.db*), shared_prefs, keystore, KitsuneBackup, dan LineageOS-18.1-arm64-a-Vanilla.img.xz dikecualikan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    item {
                        SelectAllControlRow(
                            allSelected = allJunkSelected,
                            selectedCount = selectedCategories.size,
                            totalCount = uiState.junkCategories.size,
                            enabled = !busy,
                            label = "Pilih Semua Kategori",
                            testTag = "junk_select_all_checkbox",
                            onToggleSelectAll = onToggleAllCategories
                        )
                    }

                    items(uiState.junkCategories, key = { it.type.name }) { item ->
                        PremiumCard(
                            cornerRadius = 20.dp,
                            modifier = Modifier.clickable(enabled = !busy) { onToggleCategory(item.type) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.isSelected,
                                    onCheckedChange = { if (!busy) onToggleCategory(item.type) },
                                    modifier = Modifier.testTag("junk_checkbox_${item.type.name}")
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = FormatUtils.formatBytes(item.sizeBytes),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (item.sizeBytes > 0L) MintGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${item.fileCount} berkas" + (item.statusNote?.let { " • $it" } ?: ""),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (item.statusNote?.contains("SELinux") == true) AmberWarn else MaterialTheme.colorScheme.primary
                                    )
                                    if (item.sampleFiles.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        item.sampleFiles.take(2).forEach { sample ->
                                            Text(
                                                text = sample,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                CleanerSubTab.PER_APP -> {
                    perAppCacheTabContent(
                        uiState = uiState,
                        onScan = onScanPerAppCache,
                        onToggle = onToggleAppCache,
                        onToggleAll = onToggleAllAppCaches,
                        onCleanSelected = onCleanAppCache
                    )
                }

                CleanerSubTab.RESIDUE_APK -> {
                    residueAndApkTabContent(
                        uiState = uiState,
                        onScan = onScanResidueAndApks,
                        onToggleOrphan = onToggleOrphan,
                        onToggleApk = onToggleOldApk,
                        onToggleAll = onToggleAllResidueAndApks,
                        onCleanSelected = onCleanResidueAndApks
                    )
                }

                CleanerSubTab.LARGE_DUP -> {
                    largeAndDuplicateTabContent(
                        uiState = uiState,
                        onScan = onScanLargeAndDuplicates,
                        onToggleLarge = onToggleLargeFile,
                        onToggleDuplicate = onToggleDuplicate,
                        onToggleAll = onToggleAllLargeAndDuplicates,
                        onDeleteSelected = onDeleteLargeAndDuplicates
                    )
                }
            }
        }
    }

    // Dry-Run Preview Dialog
    uiState.dryRunPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = onDismissDryRunPreview,
            title = { Text(preview.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${preview.totalFileCount} berkas target (${FormatUtils.formatBytes(preview.totalBytes)}). Mode Dry-Run tidak menghapus berkas apa pun.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
                    )
                    if (preview.filePaths.isEmpty()) {
                        Text(
                            text = "Tidak ada berkas sampah terdeteksi pada kategori terpilih.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(preview.filePaths) { line ->
                                    Text(
                                        text = line,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onDismissDryRunPreview()
                    if (preview.totalFileCount > 0) onCleanSelected()
                }) {
                    Text(if (preview.totalFileCount > 0) "BERSIHKAN SEKARANG" else "TUTUP")
                }
            },
            dismissButton = {
                if (preview.totalFileCount > 0) {
                    TextButton(onClick = onDismissDryRunPreview) {
                        Text("TUTUP")
                    }
                }
            }
        )
    }
}
