package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppSettings
import com.example.model.FormatUtils
import com.example.model.SystemSnapshot
import com.example.ui.CleanerUiState
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.MetricProgressRow
import com.example.ui.components.PremiumCard
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintGreen

@Composable
fun PerformanceScreen(
    snapshot: SystemSnapshot,
    uiState: CleanerUiState,
    settings: AppSettings,
    onEnsureLoaded: () -> Unit,
    onSyncStorage: () -> Unit,
    onTrimStorage: () -> Unit,
    onClearAppCache: () -> Unit,
    onOptimizeArt: () -> Unit,
    onRefreshSystemInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onEnsureLoaded()
    }
    val busy = uiState.isPerformanceBusy
    val ramTotal = snapshot.ramInfo.totalBytes.coerceAtLeast(1L)
    val ramProgress = (snapshot.ramInfo.usedBytes.toFloat() / ramTotal.toFloat()).coerceIn(0f, 1f)
    val storageTotal = snapshot.internalStorage.totalBytes.coerceAtLeast(1L)
    val storageProgress = (snapshot.internalStorage.usedBytes.toFloat() / storageTotal.toFloat()).coerceIn(0f, 1f)
    val maxFolderBytes = (snapshot.storageBreakdown.maxOfOrNull { it.sizeBytes } ?: 1L).coerceAtLeast(1L)

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
            // Action Tools Card
            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Text(
                        text = "Diagnostik Performa & Optimasi Sistem",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Eksekusi pemeliharaan partisi dan kompilasi ART sesuai standar Android tanpa memodifikasi governor, voltase, maupun proteksi termal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (busy) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onSyncStorage,
                            enabled = !busy,
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("sync_storage_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Storage", maxLines = 1, softWrap = false)
                        }
                        FilledTonalButton(
                            onClick = onTrimStorage,
                            enabled = !busy,
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("trim_storage_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trim Storage", maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onClearAppCache,
                            enabled = !busy,
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("perf_clear_cache_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Hapus Cache", maxLines = 1, softWrap = false)
                        }
                        FilledTonalButton(
                            onClick = onOptimizeArt,
                            enabled = !busy,
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("optimize_art_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Optimasi ART", maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRefreshSystemInfo,
                        enabled = !busy,
                        contentPadding = ActionButtonPadding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("refresh_system_info_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Muat Ulang Info Sistem", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Storage Tree Breakdown
            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Distribusi Penyimpanan (Storage Breakdown)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (snapshot.storageBreakdown.isEmpty()) {
                        Text(
                            text = "Data distribusi direktori memerlukan otorisasi root aktif.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        snapshot.storageBreakdown.take(8).forEachIndexed { idx, folder ->
                            if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                            MetricProgressRow(
                                label = folder.label,
                                valueText = FormatUtils.formatBytes(folder.sizeBytes),
                                progress = (folder.sizeBytes.toFloat() / maxFolderBytes.toFloat()).coerceIn(0.02f, 1f),
                                accentColor = if (idx == 0) MaterialTheme.colorScheme.primary else CyanAccent,
                                animationsEnabled = settings.animationsEnabled
                            )
                        }
                    }
                }
            }

            // RAM & Android Memory Card
            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Memory, contentDescription = null, tint = CyanAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Memori RAM & Kernel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    MetricProgressRow(
                        label = "Terpakai terhadap Total",
                        valueText = "${FormatUtils.formatBytes(snapshot.ramInfo.usedBytes)} / ${FormatUtils.formatBytes(snapshot.ramInfo.totalBytes)}",
                        progress = ramProgress,
                        accentColor = CyanAccent,
                        animationsEnabled = settings.animationsEnabled
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoRow("Tersedia", FormatUtils.formatBytes(snapshot.ramInfo.availableBytes))
                    InfoRow("Buffers / Cached", "${FormatUtils.formatBytes(snapshot.ramInfo.buffersBytes)} / ${FormatUtils.formatBytes(snapshot.ramInfo.cachedBytes)}")
                    InfoRow("Swap (ZRAM)", "${FormatUtils.formatBytes(snapshot.ramInfo.swapFreeBytes)} bebas dari ${FormatUtils.formatBytes(snapshot.ramInfo.swapTotalBytes)}")
                    InfoRow("Status Memori Android", if (snapshot.ramInfo.isLowMemory) "Low Memory" else "Stabil")
                }
            }

            // CPU Card (full width for effortless reading)
            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DeveloperBoard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Prosesor (CPU)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoRow("Hardware", snapshot.cpuInfo.hardware)
                    InfoRow("Arsitektur", snapshot.cpuInfo.abi)
                    InfoRow("Jumlah Core", "${snapshot.cpuInfo.coreCount} Core")
                    if (snapshot.cpuInfo.maxFreqMhz > 0) {
                        InfoRow("Frekuensi Maks", "${snapshot.cpuInfo.maxFreqMhz} MHz")
                    }
                    if (snapshot.cpuInfo.currentFreqMhzList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = snapshot.cpuInfo.currentFreqMhzList.joinToString(" • ") { "${it} MHz" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Battery Card (full width for effortless reading)
            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = MintGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Baterai", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoRow("Kapasitas", "${snapshot.batteryInfo.levelPercent}%")
                    InfoRow("Temperatur", "${snapshot.batteryInfo.temperatureCelsius} °C")
                    InfoRow("Status Pengisian", snapshot.batteryInfo.statusLabel)
                    InfoRow("Kesehatan Baterai", snapshot.batteryInfo.healthLabel)
                    if (snapshot.batteryInfo.voltageMv > 0) {
                        InfoRow("Tegangan", "${snapshot.batteryInfo.voltageMv} mV")
                    }
                }
            }

            // Storage Card
            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SdStorage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Partisi Penyimpanan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = if (snapshot.fstrimSupported) "FSTRIM Tersedia" else "FSTRIM Tidak Tersedia",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (snapshot.fstrimSupported) MintGreen else AmberWarn
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    MetricProgressRow(
                        label = snapshot.internalStorage.name,
                        valueText = "${FormatUtils.formatBytes(snapshot.internalStorage.availableBytes)} tersedia",
                        progress = storageProgress,
                        animationsEnabled = settings.animationsEnabled
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("Kapasitas Internal", FormatUtils.formatBytes(snapshot.internalStorage.totalBytes))
                    InfoRow("Shared (/sdcard) Tersedia", FormatUtils.formatBytes(snapshot.externalStorage.availableBytes))
                }
            }

            // Running Processes Section (Read-only)
            item {
                Text(
                    text = "Proses Sistem Aktif (Read-Only • Top RSS)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (snapshot.topProcesses.isEmpty()) {
                item {
                    PremiumCard(cornerRadius = 18.dp) {
                        Text(
                            text = "Pemantauan proses aktif memerlukan akses root atau tekan REFRESH SYSTEM INFO.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(snapshot.topProcesses, key = { "${it.pid}_${it.name}" }) { proc ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = proc.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "PID ${proc.pid} • ${proc.user}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = FormatUtils.formatBytes(proc.rssKb * 1024L),
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
