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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppSettings
import com.example.model.FormatUtils
import com.example.model.LegacyCategoryType
import com.example.model.WhatsAppCategoryType
import com.example.ui.CleanerUiState
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.PremiumCard
import com.example.ui.components.SelectAllControlRow
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralRed
import com.example.ui.theme.MintGreen

private enum class LegacySubMode(val label: String) {
    LEGACY_FOLDERS("Folder Legacy"),
    WHATSAPP_ROOT("WhatsApp Khusus (Root)")
}

@Composable
fun LegacyCleanerScreen(
    uiState: CleanerUiState,
    settings: AppSettings,
    onScanLegacy: () -> Unit,
    onToggleLegacy: (LegacyCategoryType) -> Unit,
    onToggleAllLegacy: (Boolean) -> Unit,
    onExecuteLegacyClean: () -> Unit,
    onScanWhatsApp: () -> Unit,
    onToggleWhatsAppGroup: (WhatsAppCategoryType) -> Unit,
    onToggleAllWhatsApp: (Boolean) -> Unit,
    onExecuteWhatsAppClean: () -> Unit,
    onSetSecureShred: (Boolean) -> Unit,
    onSetZeroTrace: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeMode by rememberSaveable { mutableStateOf(LegacySubMode.LEGACY_FOLDERS) }
    val busy = uiState.isLegacyScanning || uiState.isLegacyCleaning || uiState.isWhatsAppBusy
    val animatedOpProgress by animateFloatAsState(
        targetValue = uiState.operationProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 240),
        label = "legacy_op_progress"
    )
    val opPercent = (animatedOpProgress * 100).toInt().coerceIn(0, 100)

    val selectedItems = uiState.legacyCategories.filter { it.isSelected }
    val selectedFiles = selectedItems.sumOf { it.fileCount }
    val selectedBytes = selectedItems.sumOf { it.sizeBytes }
    val allLegacySelected = uiState.legacyCategories.isNotEmpty() && selectedItems.size == uiState.legacyCategories.size

    val selectedWaGroups = uiState.whatsAppGroups.filter { it.isSelected }
    val selectedWaFiles = selectedWaGroups.sumOf { it.fileCount }
    val selectedWaBytes = selectedWaGroups.sumOf { it.sizeBytes }

    var showLegacyConfirmDialog by remember { mutableStateOf(false) }
    var showWhatsAppConfirmDialog by remember { mutableStateOf(false) }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.FolderCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Legacy & WhatsApp Permanent Shred",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LegacySubMode.entries.forEach { mode ->
                            FilterChip(
                                selected = activeMode == mode,
                                onClick = { if (!busy) activeMode = mode },
                                label = { Text(mode.label, maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("legacy_mode_${mode.name}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = MintGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "Hapus Permanen Anti-Recovery",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Timpa blok nol & hapus indeks Galeri",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = settings.secureShredEnabled,
                                    onCheckedChange = onSetSecureShred,
                                    modifier = Modifier.testTag("legacy_secure_shred_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Tanpa Riwayat (Zero-Trace)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Bersihkan catatan riwayat & log setelah selesai",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = settings.zeroTraceHistoryEnabled,
                                    onCheckedChange = onSetZeroTrace,
                                    modifier = Modifier.testTag("legacy_zero_trace_switch")
                                )
                            }
                        }
                    }
                }
            }

            if (activeMode == LegacySubMode.WHATSAPP_ROOT) {
                whatsAppRootSectionItems(
                    uiState = uiState,
                    animatedOpProgress = animatedOpProgress,
                    opPercent = opPercent,
                    onScanWhatsApp = onScanWhatsApp,
                    onToggleWhatsAppGroup = onToggleWhatsAppGroup,
                    onToggleAllWhatsApp = onToggleAllWhatsApp,
                    onRequestCleanWhatsApp = { showWhatsAppConfirmDialog = true }
                )
            } else {
                item {
                    PremiumCard(cornerRadius = 22.dp) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AmberContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = AmberWarn,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Kebijakan Proteksi: /sdcard/Download/KitsuneBackup, LineageOS-18.1-arm64-a-Vanilla.img.xz, basis data WhatsApp (*.db*, Backups), dan direktori induk kategori dikecualikan.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AmberWarn
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
                                    text = uiState.statusBanner ?: "Memproses direktori Legacy...",
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

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Terpilih: ${selectedItems.size} kategori • $selectedFiles berkas (${FormatUtils.formatBytes(selectedBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onScanLegacy,
                                enabled = !busy,
                                contentPadding = ActionButtonPadding,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("scan_legacy_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pindai Folder", maxLines = 1, softWrap = false)
                            }
                            Button(
                                onClick = { showLegacyConfirmDialog = true },
                                enabled = !busy && selectedItems.isNotEmpty(),
                                contentPadding = ActionButtonPadding,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("clean_legacy_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CoralRed,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Hapus Pilihan", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                item {
                    SelectAllControlRow(
                        allSelected = allLegacySelected,
                        selectedCount = selectedItems.size,
                        totalCount = uiState.legacyCategories.size,
                        enabled = !busy,
                        label = "Pilih Semua Folder Legacy",
                        testTag = "legacy_select_all_checkbox",
                        onToggleSelectAll = onToggleAllLegacy
                    )
                }

                items(uiState.legacyCategories, key = { it.type.name }) { item ->
                    PremiumCard(
                        cornerRadius = 18.dp,
                        modifier = Modifier.clickable(enabled = !busy) { onToggleLegacy(item.type) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isSelected,
                                onCheckedChange = { if (!busy) onToggleLegacy(item.type) },
                                modifier = Modifier.testTag("legacy_checkbox_${item.type.name}")
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
                                        text = if (item.isScanned) FormatUtils.formatBytes(item.sizeBytes) else "Belum dipindai",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (item.isScanned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = item.paths.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.isScanned) {
                                    Text(
                                        text = "${item.fileCount} berkas terindeks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                item.protectedNotes?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AmberWarn
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLegacyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLegacyConfirmDialog = false },
            icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = CoralRed) },
            title = { Text("Konfirmasi Hapus Permanen Legacy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Anda akan menghapus isi dari ${selectedItems.size} kategori (${selectedFiles} berkas, ${FormatUtils.formatBytes(selectedBytes)}):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    selectedItems.forEach { cat ->
                        Text(
                            text = "• ${cat.title}: ${cat.fileCount} berkas (${FormatUtils.formatBytes(cat.sizeBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (settings.secureShredEnabled) {
                        Text(
                            text = "Mode Anti-Recovery aktif: blok data ditimpa nol agar tidak dapat dipulihkan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MintGreen
                        )
                    }
                    Text(
                        text = "Direktori induk, KitsuneBackup, LineageOS-18.1-arm64-a-Vanilla.img.xz, dan basis data WhatsApp tetap dilindungi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarn
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLegacyConfirmDialog = false
                        onExecuteLegacyClean()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                    modifier = Modifier.testTag("legacy_confirm_button")
                ) {
                    Text("KONFIRMASI HAPUS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLegacyConfirmDialog = false }) {
                    Text("BATAL")
                }
            }
        )
    }

    if (showWhatsAppConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWhatsAppConfirmDialog = false },
            icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = CoralRed) },
            title = { Text("Musnahkan Media WhatsApp (Akun Aman)?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${selectedWaGroups.size} kategori WhatsApp ($selectedWaFiles berkas, ${FormatUtils.formatBytes(selectedWaBytes)}) akan dimusnahkan secara permanen:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    selectedWaGroups.forEach { grp ->
                        Text(
                            text = "• ${grp.title}: ${grp.fileCount} berkas (${FormatUtils.formatBytes(grp.sizeBytes)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Akun WhatsApp, kredensial sesi (me, key, shared_prefs), dan database chat (databases, Backups) dikecualikan secara ketat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
                    )
                    if (settings.secureShredEnabled) {
                        Text(
                            text = "Anti-Recovery aktif: file ditimpa nol & indeks Galeri dihapus tanpa bisa di-recovery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberWarn
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWhatsAppConfirmDialog = false
                        onExecuteWhatsAppClean()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                    modifier = Modifier.testTag("whatsapp_confirm_button")
                ) {
                    Text("MUSNAHKAN PERMANEN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhatsAppConfirmDialog = false }) {
                    Text("BATAL")
                }
            }
        )
    }
}
