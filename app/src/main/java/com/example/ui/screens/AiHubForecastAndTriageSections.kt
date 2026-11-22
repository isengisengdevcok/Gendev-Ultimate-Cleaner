package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ContextRiskItem
import com.example.model.ContextRiskLevel
import com.example.model.FormatUtils
import com.example.model.StorageDeltaReport
import com.example.model.StorageForecastReport
import com.example.model.StorageHistoryPoint
import com.example.model.VisualSimilarityGroup
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.PremiumCard
import com.example.ui.components.SelectAllControlRow
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintContainer
import com.example.ui.theme.MintGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ForecastRadarSection(
    report: StorageForecastReport,
    isRootActive: Boolean,
    isBusy: Boolean,
    onAnalyzeForecast: () -> Unit,
    onCleanExplosionApp: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoGraph, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Prediktor Memori & Radar Cache",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = report.forecastHeadline,
                style = MaterialTheme.typography.titleSmall,
                color = if ((report.estimatedDaysUntilFull ?: 99) <= 14) CoralRed else MintGreen,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = report.forecastDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (report.historyPoints.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                StorageSparklineChart(points = report.historyPoints)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAnalyzeForecast,
                enabled = !isBusy,
                contentPadding = ActionButtonPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("ai_forecast_analyze_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rekam & Analisis Tren", maxLines = 1, softWrap = false)
            }
        }

        PremiumCard(cornerRadius = 22.dp) {
            Text(
                text = "Radar Lonjakan Cache Aplikasi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isRootActive) {
                    "Memantau cache per-aplikasi yang tumbuh paling agresif."
                } else {
                    "Mode Non-Root membaca ukuran cache via Usage Access (jika diizinkan)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (report.explosionApps.isEmpty()) {
                Text(
                    text = "Belum ada lonjakan cache terdeteksi. Jalankan analisis secara berkala untuk melihat laju pertumbuhan per aplikasi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                report.explosionApps.forEachIndexed { idx, app ->
                    if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${app.severityLabel} • Cache ${FormatUtils.formatBytes(app.currentCacheBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (app.severityLabel.contains("Tinggi")) AmberWarn else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (app.deltaBytesPerDay > 0L) {
                                    Text(
                                        text = "+${FormatUtils.formatBytes(app.deltaBytesPerDay)}/hari",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CoralRed
                                    )
                                }
                            }
                            if (isRootActive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { onCleanExplosionApp(app.packageName) },
                                    enabled = !isBusy,
                                    contentPadding = ActionButtonPadding,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Bersihkan", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSparklineChart(points: List<StorageHistoryPoint>) {
    val lineColor = CyanAccent
    val minUsed = points.minOf { it.usedBytes }.toFloat()
    val maxUsed = points.maxOf { it.usedBytes }.toFloat()
    val range = (maxUsed - minUsed).coerceAtLeast(1024f)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Kurva Pemakaian (${points.size} titik rekaman)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
            ) {
                val path = Path()
                points.forEachIndexed { index, point ->
                    val x = if (points.size == 1) size.width / 2f else (index.toFloat() / (points.size - 1)) * size.width
                    val norm = ((point.usedBytes - minUsed) / range).coerceIn(0f, 1f)
                    val y = size.height - (norm * (size.height - 16f) + 8f)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
                }
                drawPath(path = path, color = lineColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun DeltaTimelineSection(
    report: StorageDeltaReport,
    isBusy: Boolean,
    onSaveBaseline: () -> Unit,
    onCompareBaseline: () -> Unit
) {
    val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Timeline Delta Penyimpanan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = report.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (report.baselineTimestampMillis > 0L) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Patokan tersimpan: ${fmt.format(Date(report.baselineTimestampMillis))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyanAccent
                )
            }
            if (report.comparedTimestampMillis > 0L) {
                val sign = if (report.totalDeltaBytes >= 0) "+" else "-"
                val absDelta = kotlin.math.abs(report.totalDeltaBytes)
                Text(
                    text = "Selisih total (${fmt.format(Date(report.comparedTimestampMillis))}): $sign${FormatUtils.formatBytes(absDelta)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (report.totalDeltaBytes > 0) CoralRed else MintGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveBaseline,
                    enabled = !isBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_delta_save_baseline"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Simpan Patokan", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onCompareBaseline,
                    enabled = !isBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_delta_compare"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Bandingkan", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (report.items.isNotEmpty()) {
            PremiumCard(cornerRadius = 22.dp) {
                Text(
                    text = "Perubahan Ukuran per Direktori",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                report.items.forEachIndexed { idx, item ->
                    if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                    val isIncrease = item.deltaBytes > 0L
                    val absVal = kotlin.math.abs(item.deltaBytes)
                    val deltaText = if (item.deltaBytes == 0L) {
                        "Tetap"
                    } else if (isIncrease) {
                        "+${FormatUtils.formatBytes(absVal)}"
                    } else {
                        "-${FormatUtils.formatBytes(absVal)}"
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Patokan: ${FormatUtils.formatBytes(item.previousBytes)} → Kini: ${FormatUtils.formatBytes(item.currentBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = deltaText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    item.deltaBytes > 0L -> CoralRed
                                    item.deltaBytes < 0L -> MintGreen
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContextAndVisualTriageSection(
    contextItems: List<ContextRiskItem>,
    visualGroups: List<VisualSimilarityGroup>,
    isRootActive: Boolean,
    isBusy: Boolean,
    onScanContextRisk: () -> Unit,
    onToggleContextItem: (String) -> Unit,
    onToggleAllContextItems: (Boolean) -> Unit,
    onDeleteContextItems: () -> Unit,
    onScanVisualRoot: () -> Unit,
    onPickVisualPhotos: () -> Unit,
    onToggleVisualCandidate: (String) -> Unit,
    onToggleAllVisualCandidates: (Boolean) -> Unit,
    onDeleteVisualCandidates: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Part A: Context Risk Triage
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Triage Risiko Konteks Unduhan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Menilai berkas di folder Download/SAF ke tingkat Lindungi, Tinjau, atau Aman Hapus berdasarkan pola dokumen penting, arsip cadangan, & usia berkas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onScanContextRisk,
                    enabled = !isBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_context_scan_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Pindai Konteks", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                val selectedCount = contextItems.count { it.isSelected }
                FilledTonalButton(
                    onClick = onDeleteContextItems,
                    enabled = !isBusy && selectedCount > 0,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_context_clean_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hapus ($selectedCount)", maxLines = 1, softWrap = false)
                }
            }

            if (contextItems.isNotEmpty()) {
                val toggleableItems = contextItems.filter { it.riskLevel != ContextRiskLevel.PROTECTED }
                val selectedToggleable = toggleableItems.count { it.isSelected }
                if (toggleableItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SelectAllControlRow(
                        allSelected = selectedToggleable == toggleableItems.size,
                        selectedCount = selectedToggleable,
                        totalCount = toggleableItems.size,
                        enabled = !isBusy,
                        label = "Pilih Semua Berkas Aman",
                        testTag = "ai_context_select_all_checkbox",
                        onToggleSelectAll = onToggleAllContextItems
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                contextItems.take(30).forEach { item ->
                    val key = item.uriString ?: item.path
                    val (badgeBg, badgeFg) = when (item.riskLevel) {
                        ContextRiskLevel.PROTECTED -> CoralContainer to CoralRed
                        ContextRiskLevel.REVIEW -> AmberContainer to AmberWarn
                        ContextRiskLevel.SAFE_TO_CLEAN -> MintContainer to MintGreen
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = item.riskLevel != ContextRiskLevel.PROTECTED) {
                                onToggleContextItem(key)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.riskLevel == ContextRiskLevel.PROTECTED) {
                                Icon(
                                    Icons.Rounded.Shield,
                                    contentDescription = "Dilindungi",
                                    tint = CoralRed,
                                    modifier = Modifier.padding(12.dp).size(20.dp)
                                )
                            } else {
                                Checkbox(
                                    checked = item.isSelected,
                                    onCheckedChange = { onToggleContextItem(key) }
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${FormatUtils.formatBytes(item.sizeBytes)} • ${item.ageDays} hari • ${item.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = badgeBg) {
                                Text(
                                    text = item.riskLevel.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeFg,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Part B: Visual Similarity & Blur Detector (dHash)
        PremiumCard(cornerRadius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Detektor Kemiripan Foto & Blur (dHash)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Menemukan foto nyaris identik, tangkapan layar, atau bidikan buram di perangkat secara offline tanpa izin penyimpanan luas.",
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
                        onClick = onScanVisualRoot,
                        enabled = !isBusy,
                        contentPadding = ActionButtonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ai_visual_scan_root"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Pindai Galeri", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(
                    onClick = onPickVisualPhotos,
                    enabled = !isBusy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_visual_pick_photos"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Pilih Foto", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }

            val selectedVisual = visualGroups.sumOf { g -> g.candidates.count { it.isSelected } }
            val selectableVisualTotal = visualGroups.sumOf { g ->
                if (g.groupId.startsWith("sim_") && g.candidates.size > 1) g.candidates.size - 1 else g.candidates.size
            }
            if (visualGroups.isNotEmpty()) {
                if (selectableVisualTotal > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    SelectAllControlRow(
                        allSelected = selectedVisual >= selectableVisualTotal,
                        selectedCount = selectedVisual,
                        totalCount = selectableVisualTotal,
                        enabled = !isBusy,
                        label = "Pilih Semua Salinan/Buram",
                        testTag = "ai_visual_select_all_checkbox",
                        onToggleSelectAll = onToggleAllVisualCandidates
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                visualGroups.forEach { group ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = AmberWarn, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(group.reason, style = MaterialTheme.typography.labelLarge, color = AmberWarn, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            group.candidates.forEach { candidate ->
                                val key = candidate.uriString ?: candidate.path
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleVisualCandidate(key) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = candidate.isSelected,
                                        onCheckedChange = { onToggleVisualCandidate(key) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val blurTag = if (candidate.isBlurry) " • Terindikasi Buram" else ""
                                        Text(
                                            text = "${FormatUtils.formatBytes(candidate.sizeBytes)} • Ketajaman ${candidate.sharpnessScore.toInt()}$blurTag",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDeleteVisualCandidates,
                    enabled = !isBusy && selectedVisual > 0,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("ai_visual_delete_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Hapus $selectedVisual Foto Terpilih", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
