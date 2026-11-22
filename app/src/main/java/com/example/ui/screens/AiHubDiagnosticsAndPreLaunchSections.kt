package com.example.ui.screens

import androidx.compose.foundation.clickable
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.example.model.CrashAnalyzerReport
import com.example.model.FormatUtils
import com.example.model.LaunchableAppEntry
import com.example.model.MediaReclaimCandidate
import com.example.model.PreLaunchProfile
import com.example.model.PreLaunchReport
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.PremiumCard
import com.example.ui.components.SelectAllControlRow
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintGreen

@Composable
fun CrashAnalyzerSection(
    report: CrashAnalyzerReport,
    isBusy: Boolean,
    onAnalyzeCrash: () -> Unit,
    onClearCrashLogs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BugReport, contentDescription = null, tint = CoralRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Analis Pelaku Crash & ANR",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = report.summaryMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (report.totalLogFiles > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${report.totalLogFiles} berkas log (${FormatUtils.formatBytes(report.totalCrashLogBytes)})",
                    style = MaterialTheme.typography.labelLarge,
                    color = AmberWarn,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onAnalyzeCrash,
                    enabled = !isBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_crash_analyze_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Pindai Crash", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                if (report.isRootDetailAvailable && report.totalLogFiles > 0) {
                    FilledTonalButton(
                        onClick = onClearCrashLogs,
                        enabled = !isBusy,
                        contentPadding = ActionButtonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ai_crash_clear_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hapus Log", maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        if (report.culprits.isNotEmpty()) {
            PremiumCard(cornerRadius = 22.dp) {
                Text(
                    text = "Daftar Proses Terindikasi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                report.culprits.forEachIndexed { idx, entry ->
                    if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.packageName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${entry.occurrenceCount}x • ${entry.sourceType}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CoralRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.sampleReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.totalLogBytes > 0L) {
                                Text(
                                    text = "Ukuran berkas: ${FormatUtils.formatBytes(entry.totalLogBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaReclaimerSection(
    candidates: List<MediaReclaimCandidate>,
    isRootActive: Boolean,
    isBusy: Boolean,
    onScanMediaRoot: () -> Unit,
    onPickMediaFromGallery: () -> Unit,
    onToggleCandidate: (String) -> Unit,
    onToggleAllCandidates: (Boolean) -> Unit,
    onCompressSelected: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhotoSizeSelectLarge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Penghemat Ruang Media Lokal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Mengoptimasi ukuran foto beresolusi besar (JPEG 82%) & video (MediaCodec H.264) langsung di perangkat tanpa menghapus kenangan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isRootActive) {
                    Button(
                        onClick = onScanMediaRoot,
                        enabled = !isBusy,
                        contentPadding = ActionButtonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ai_media_scan_root"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Pindai DCIM", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(
                    onClick = onPickMediaFromGallery,
                    enabled = !isBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_media_pick_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Pilih Media", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }

            val pendingCandidates = candidates.filter { !it.isCompleted }
            val selectedCount = pendingCandidates.count { it.isSelected }
            if (candidates.isNotEmpty()) {
                if (pendingCandidates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SelectAllControlRow(
                        allSelected = selectedCount == pendingCandidates.size,
                        selectedCount = selectedCount,
                        totalCount = pendingCandidates.size,
                        enabled = !isBusy,
                        label = "Pilih Semua Media",
                        testTag = "ai_media_select_all_checkbox",
                        onToggleSelectAll = onToggleAllCandidates
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onCompressSelected,
                    enabled = !isBusy && selectedCount > 0,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("ai_media_compress_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kompresi $selectedCount Media Terpilih", maxLines = 1, softWrap = false)
                }

                Spacer(modifier = Modifier.height(10.dp))
                candidates.forEachIndexed { idx, item ->
                    if (idx > 0) Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !item.isCompleted) { onToggleCandidate(item.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isSelected,
                                enabled = !item.isCompleted,
                                onCheckedChange = { onToggleCandidate(item.id) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${if (item.isVideo) "[Video]" else "[Foto]"} ${item.fileName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val resultInfo = if (item.compressedBytes != null) {
                                    "Asal ${FormatUtils.formatBytes(item.originalBytes)} → Baru ${FormatUtils.formatBytes(item.compressedBytes)}"
                                } else {
                                    "Ukuran ${FormatUtils.formatBytes(item.originalBytes)} • ${item.statusNote}"
                                }
                                Text(
                                    text = resultInfo,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.isCompleted) MintGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreLaunchSection(
    selectedProfile: PreLaunchProfile,
    launchableApps: List<LaunchableAppEntry>,
    selectedTargetApp: LaunchableAppEntry?,
    report: PreLaunchReport?,
    isBusy: Boolean,
    onSelectProfile: (PreLaunchProfile) -> Unit,
    onSelectTargetApp: (LaunchableAppEntry) -> Unit,
    onRunConditioner: () -> Unit,
    onLaunchTargetApp: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RocketLaunch, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pre-Launch Conditioner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = selectedProfile.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreLaunchProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = selectedProfile == profile,
                        onClick = { onSelectProfile(profile) },
                        label = { Text(profile.title, maxLines = 1, softWrap = false) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = launchableApps.isNotEmpty()) { showAppPicker = true }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aplikasi Target Peluncuran",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedTargetApp?.appLabel ?: "Tidak ada aplikasi terdeteksi",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Ubah",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRunConditioner,
                    enabled = !isBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_prelaunch_prepare_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Siapkan Sistem", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onLaunchTargetApp,
                    enabled = selectedTargetApp != null,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_prelaunch_launch_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Buka Aplikasi", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (report != null) {
            PremiumCard(cornerRadius = 22.dp) {
                Text(
                    text = "Laporan Kesiapan (${report.profile.title})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MintGreen
                )
                Spacer(modifier = Modifier.height(8.dp))
                val ramGain = (report.ramAfterAvailableBytes - report.ramBeforeAvailableBytes).coerceAtLeast(0L)
                Text(
                    text = "RAM Tersedia: ${FormatUtils.formatBytes(report.ramBeforeAvailableBytes)} → ${FormatUtils.formatBytes(report.ramAfterAvailableBytes)} (+${FormatUtils.formatBytes(ramGain)})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Suhu Baterai: ${String.format(Locale.US, "%.1f", report.batteryTempCelsius)} °C • Proses di-trim: ${report.trimmedProcessesCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                report.actionsSummary.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showAppPicker) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false },
            title = { Text("Pilih Aplikasi Target") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(launchableApps, key = { it.packageName }) { app ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (app.packageName == selectedTargetApp?.packageName) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable {
                                    onSelectTargetApp(app)
                                    showAppPicker = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(app.appLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false }) { Text("TUTUP") }
            }
        )
    }
}
