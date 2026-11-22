package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FormatUtils
import com.example.ui.CleanerUiState
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.PremiumCard
import com.example.ui.components.SelectAllControlRow
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintGreen

fun LazyListScope.perAppCacheTabContent(
    uiState: CleanerUiState,
    onScan: () -> Unit,
    onToggle: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onCleanSelected: () -> Unit
) {
    val selected = uiState.appCaches.filter { it.isSelected }
    val selectedBytes = selected.sumOf { it.totalCacheBytes }
    val allSelected = uiState.appCaches.isNotEmpty() && selected.size == uiState.appCaches.size

    item {
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Peringkat Cache Per Aplikasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Terpilih: ${selected.size} aplikasi (${FormatUtils.formatBytes(selectedBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
                    )
                }
            }
            if (uiState.isAdvancedBusy) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onScan,
                    enabled = !uiState.isAdvancedBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("scan_app_cache_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pindai Cache", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = onCleanSelected,
                    enabled = !uiState.isAdvancedBusy && selected.isNotEmpty(),
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("clean_app_cache_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bersihkan", maxLines = 1, softWrap = false)
                }
            }
        }
    }

    if (uiState.appCaches.isEmpty()) {
        item {
            EmptyScanStateCard("Tekan PINDAI untuk mengurutkan aplikasi berdasarkan ukuran cache terbesar.")
        }
    } else {
        item {
            SelectAllControlRow(
                allSelected = allSelected,
                selectedCount = selected.size,
                totalCount = uiState.appCaches.size,
                enabled = !uiState.isAdvancedBusy,
                label = "Pilih Semua Aplikasi",
                testTag = "per_app_select_all_checkbox",
                onToggleSelectAll = onToggleAll
            )
        }
        items(uiState.appCaches, key = { it.packageName }) { app ->
            PremiumCard(
                cornerRadius = 18.dp,
                modifier = Modifier.clickable(enabled = !uiState.isAdvancedBusy) { onToggle(app.packageName) }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = app.isSelected,
                        onCheckedChange = { if (!uiState.isAdvancedBusy) onToggle(app.packageName) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = app.appLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = FormatUtils.formatBytes(app.totalCacheBytes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MintGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Internal: ${FormatUtils.formatBytes(app.internalCacheBytes)} • Android/data: ${FormatUtils.formatBytes(app.externalCacheBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanAccent
                        )
                    }
                }
            }
        }
    }
}

fun LazyListScope.residueAndApkTabContent(
    uiState: CleanerUiState,
    onScan: () -> Unit,
    onToggleOrphan: (String) -> Unit,
    onToggleApk: (String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onCleanSelected: () -> Unit
) {
    val selectedOrphans = uiState.orphanedResidues.filter { it.isSelected }
    val selectedApks = uiState.oldApks.filter { it.isSelected }
    val totalBytes = selectedOrphans.sumOf { it.sizeBytes } + selectedApks.sumOf { it.sizeBytes }
    val selectedCount = selectedOrphans.size + selectedApks.size
    val totalItems = uiState.orphanedResidues.size + uiState.oldApks.size
    val hasSelection = selectedCount > 0
    val allSelected = totalItems > 0 && selectedCount == totalItems

    item {
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderDelete, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Residu Aplikasi & Installer APK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Terpilih: ${selectedOrphans.size + selectedApks.size} item (${FormatUtils.formatBytes(totalBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
                    )
                }
            }
            if (uiState.isAdvancedBusy) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onScan,
                    enabled = !uiState.isAdvancedBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("scan_residue_apk_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pindai Residu", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = onCleanSelected,
                    enabled = !uiState.isAdvancedBusy && hasSelection,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("clean_residue_apk_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus Pilihan", maxLines = 1, softWrap = false)
                }
            }
        }
    }

    if (uiState.orphanedResidues.isEmpty() && uiState.oldApks.isEmpty()) {
        item {
            EmptyScanStateCard("Tekan PINDAI untuk mendeteksi direktori sisa aplikasi terhapus dan berkas .apk lama.")
        }
    } else {
        item {
            SelectAllControlRow(
                allSelected = allSelected,
                selectedCount = selectedCount,
                totalCount = totalItems,
                enabled = !uiState.isAdvancedBusy,
                label = "Pilih Semua Residu & APK",
                testTag = "residue_select_all_checkbox",
                onToggleSelectAll = onToggleAll
            )
        }
        if (uiState.orphanedResidues.isNotEmpty()) {
            item {
                Text(
                    text = "Residu Aplikasi Tidak Terinstal (${uiState.orphanedResidues.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(uiState.orphanedResidues, key = { it.path }) { item ->
                PremiumCard(
                    cornerRadius = 18.dp,
                    modifier = Modifier.clickable(enabled = !uiState.isAdvancedBusy) { onToggleOrphan(item.path) }
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.isSelected, onCheckedChange = { onToggleOrphan(item.path) })
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.packageName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(FormatUtils.formatBytes(item.sizeBytes), style = MaterialTheme.typography.titleSmall, color = MintGreen, fontWeight = FontWeight.Bold)
                            }
                            Text(item.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${item.fileCount} berkas", style = MaterialTheme.typography.bodySmall, color = CyanAccent)
                        }
                    }
                }
            }
        }

        if (uiState.oldApks.isNotEmpty()) {
            item {
                Text(
                    text = "Berkas Installer APK (${uiState.oldApks.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(uiState.oldApks, key = { it.path }) { apk ->
                PremiumCard(
                    cornerRadius = 18.dp,
                    modifier = Modifier.clickable(enabled = !uiState.isAdvancedBusy) { onToggleApk(apk.path) }
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = apk.isSelected, onCheckedChange = { onToggleApk(apk.path) })
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Android, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(apk.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(FormatUtils.formatBytes(apk.sizeBytes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "${apk.packageName ?: "Paket tidak dikenal"} ${apk.versionName?.let { "v$it" } ?: ""} • ${if (apk.isInstalled) "Sudah terinstal" else "Belum terinstal"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (apk.isInstalled) MintGreen else CyanAccent
                            )
                            Text(apk.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.largeAndDuplicateTabContent(
    uiState: CleanerUiState,
    onScan: () -> Unit,
    onToggleLarge: (String) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onDeleteSelected: () -> Unit
) {
    val selectedLarge = uiState.largeFiles.filter { it.isSelected }
    val dupDeleteCount = uiState.duplicateGroups.sumOf { it.selectedForDeletion.size }
    val dupDeleteBytes = uiState.duplicateGroups.sumOf { it.sizeBytes * it.selectedForDeletion.size }
    val totalCount = selectedLarge.size + dupDeleteCount
    val totalSelectable = uiState.largeFiles.size + uiState.duplicateGroups.sumOf { (it.filePaths.size - 1).coerceAtLeast(0) }
    val allSelected = totalSelectable > 0 && totalCount >= totalSelectable
    val totalBytes = selectedLarge.sumOf { it.sizeBytes } + dupDeleteBytes

    item {
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FileCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Berkas Besar (>50 MB) & Duplikat SHA-256", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Terpilih: $totalCount berkas (${FormatUtils.formatBytes(totalBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
                    )
                }
            }
            if (uiState.isAdvancedBusy) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onScan,
                    enabled = !uiState.isAdvancedBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("scan_large_dup_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pindai Berkas", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = onDeleteSelected,
                    enabled = !uiState.isAdvancedBusy && totalCount > 0,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("delete_large_dup_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hapus Pilihan", maxLines = 1, softWrap = false)
                }
            }
        }
    }

    if (uiState.largeFiles.isEmpty() && uiState.duplicateGroups.isEmpty()) {
        item {
            EmptyScanStateCard("Tekan PINDAI untuk menganalisis berkas berukuran >50 MB dan duplikat terverifikasi SHA-256.")
        }
    } else {
        item {
            SelectAllControlRow(
                allSelected = allSelected,
                selectedCount = totalCount,
                totalCount = totalSelectable,
                enabled = !uiState.isAdvancedBusy,
                label = "Pilih Semua Berkas & Salinan",
                testTag = "large_dup_select_all_checkbox",
                onToggleSelectAll = onToggleAll
            )
        }
        if (uiState.duplicateGroups.isNotEmpty()) {
            item {
                Text(
                    text = "Grup Duplikat Terverifikasi (${uiState.duplicateGroups.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(uiState.duplicateGroups, key = { it.hash }) { group ->
                PremiumCard(cornerRadius = 18.dp) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${group.filePaths.size} salinan identik",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${FormatUtils.formatBytes(group.sizeBytes)} / berkas",
                            style = MaterialTheme.typography.titleSmall,
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "SHA-256: ${group.hash.take(16)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    group.filePaths.forEach { filePath ->
                        val checked = group.selectedForDeletion.contains(filePath)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !uiState.isAdvancedBusy) { onToggleDuplicate(group.hash, filePath) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleDuplicate(group.hash, filePath) }
                            )
                            Text(
                                text = filePath,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (checked) CoralRed else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (uiState.largeFiles.isNotEmpty()) {
            item {
                Text(
                    text = "Berkas Besar >50 MB (${uiState.largeFiles.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(uiState.largeFiles, key = { it.path }) { file ->
                PremiumCard(
                    cornerRadius = 18.dp,
                    modifier = Modifier.clickable(enabled = !uiState.isAdvancedBusy) { onToggleLarge(file.path) }
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = file.isSelected, onCheckedChange = { onToggleLarge(file.path) })
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(file.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(FormatUtils.formatBytes(file.sizeBytes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Text(file.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyScanStateCard(message: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
