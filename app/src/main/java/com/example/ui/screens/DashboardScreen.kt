package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppSettings
import com.example.model.CleaningHistoryItem
import com.example.model.FormatUtils
import com.example.model.LastCleanSummary
import com.example.model.RootStatus
import com.example.model.SystemSnapshot
import com.example.ui.CleanerUiState
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.AnimatedUsageRing
import com.example.ui.components.MetricProgressRow
import com.example.ui.components.PremiumCard
import com.example.ui.components.RootStatusPill
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    rootStatus: RootStatus,
    suVersion: String,
    isRootActive: Boolean,
    snapshot: SystemSnapshot,
    uiState: CleanerUiState,
    settings: AppSettings,
    totalFreedBytes: Long,
    lastCleanSummary: LastCleanSummary,
    history: List<CleaningHistoryItem>,
    onVerifyRoot: () -> Unit,
    onCleanNow: () -> Unit,
    onDeepClean: () -> Unit,
    onCancelOperation: () -> Unit,
    onNavigateToLegacy: () -> Unit,
    onNavigateToAutomation: () -> Unit,
    onNavigateToAiHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val busy = uiState.isScanning || uiState.isCleaning
    val animatedOpProgress by animateFloatAsState(
        targetValue = uiState.operationProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 240),
        label = "dashboard_op_progress"
    )
    val opPercent = (animatedOpProgress * 100).toInt().coerceIn(0, 100)

    val storageTotal = snapshot.internalStorage.totalBytes.coerceAtLeast(1L)
    val storageUsed = snapshot.internalStorage.usedBytes
    val storageProgress = (storageUsed.toFloat() / storageTotal.toFloat()).coerceIn(0f, 1f)

    val ramTotal = snapshot.ramInfo.totalBytes.coerceAtLeast(1L)
    val ramUsed = snapshot.ramInfo.usedBytes
    val ramProgress = (ramUsed.toFloat() / ramTotal.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Branding & Root Status Card
            item {
                PremiumCard(cornerRadius = 24.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_cleaner_logo),
                                contentDescription = "Ultimate Cleaner Logo",
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ULTIMATE CLEANER",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "ROOT EDITION • by Gendev • $suVersion",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onVerifyRoot,
                            modifier = Modifier.testTag("recheck_root_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Periksa ulang status root",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RootStatusPill(rootStatus = rootStatus)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "${settings.operationMode.label} • ${if (isRootActive) "Root Aktif" else "Non-Root Aktif"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isRootActive) MintGreen else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = snapshot.deviceName.ifBlank { "Android" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (settings.secureShredEnabled || settings.zeroTraceHistoryEnabled) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                val badge = buildString {
                                    if (settings.secureShredEnabled) append("Anti-Recovery")
                                    if (settings.secureShredEnabled && settings.zeroTraceHistoryEnabled) append(" • ")
                                    if (settings.zeroTraceHistoryEnabled) append("Tanpa Riwayat")
                                }
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MintGreen,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${snapshot.androidVersion} • ${snapshot.modelCode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hero Storage Ring & Action Buttons
            item {
                PremiumCard(cornerRadius = 24.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnimatedUsageRing(
                            progress = if (busy) animatedOpProgress else storageProgress,
                            isScanning = busy,
                            animationsEnabled = settings.animationsEnabled,
                            centerPrimary = if (busy) "$opPercent%" else "${(storageProgress * 100).toInt()}%",
                            centerSecondary = if (busy) {
                                if (uiState.isCleaning) "Membersihkan..." else "Memindai..."
                            } else {
                                "Storage Terpakai"
                            },
                            modifier = Modifier.size(176.dp)
                        )

                        AnimatedVisibility(
                            visible = busy,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp, bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.statusBanner ?: "Memproses perintah su -c...",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MintGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$opPercent%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MintGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { animatedOpProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(CircleShape),
                                    color = MintGreen,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // RAM & Storage Metrics
                        MetricProgressRow(
                            label = "RAM (${FormatUtils.formatBytes(snapshot.ramInfo.availableBytes)} tersedia)",
                            valueText = "${FormatUtils.formatBytes(ramUsed)} / ${FormatUtils.formatBytes(snapshot.ramInfo.totalBytes)}",
                            progress = ramProgress,
                            accentColor = CyanAccent,
                            animationsEnabled = settings.animationsEnabled
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        MetricProgressRow(
                            label = "Storage (${FormatUtils.formatBytes(snapshot.internalStorage.availableBytes)} tersedia)",
                            valueText = "${FormatUtils.formatBytes(storageUsed)} / ${FormatUtils.formatBytes(snapshot.internalStorage.totalBytes)}",
                            progress = storageProgress,
                            accentColor = MaterialTheme.colorScheme.primary,
                            animationsEnabled = settings.animationsEnabled
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (busy) {
                            Button(
                                onClick = onCancelOperation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("cancel_operation_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CoralRed,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("HENTIKAN PROSES ROOT", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onCleanNow,
                                    contentPadding = ActionButtonPadding,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("clean_now_button"),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CleaningServices,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "CLEAN NOW",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                FilledTonalButton(
                                    onClick = onDeepClean,
                                    contentPadding = ActionButtonPadding,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .testTag("deep_clean_button"),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "DEEP CLEAN",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Stats & Quick Shortcuts Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PremiumCard(
                        modifier = Modifier.weight(1f),
                        cornerRadius = 20.dp
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MintGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Total Dibersihkan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FormatUtils.formatBytes(totalFreedBytes),
                            style = MaterialTheme.typography.titleLarge,
                            color = MintGreen,
                            fontWeight = FontWeight.Bold
                        )
                        if (lastCleanSummary.timestampMillis > 0L) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Terakhir: +${FormatUtils.formatBytes(lastCleanSummary.freedBytes)} (${lastCleanSummary.mode})",
                                style = MaterialTheme.typography.labelSmall,
                                color = CyanAccent,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    PremiumCard(
                        modifier = Modifier.weight(1f),
                        cornerRadius = 20.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SdStorage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Memory,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Shared (/sdcard)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${FormatUtils.formatBytes(snapshot.externalStorage.availableBytes)} bebas",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Quick Actions for Legacy Cleaner & Automation
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToLegacy,
                        contentPadding = ActionButtonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("quick_legacy_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Legacy Cleaner", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onNavigateToAutomation,
                        contentPadding = ActionButtonPadding,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("quick_automation_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.AutoMode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Otomatisasi", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // AI Smart Hub Hero Shortcut Card
            item {
                PremiumCard(
                    cornerRadius = 22.dp,
                    modifier = Modifier
                        .clickable { onNavigateToAiHub() }
                        .testTag("dashboard_ai_hub_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI Smart Hub (6 Fitur Baru)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Prediktor Memori, Delta Diff, Triage dHash, Crash ANR, Kompresi Media & Pre-Launch",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onNavigateToAiHub,
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Buka", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            // Recent Cleaning History Card
            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Riwayat Pembersihan Terakhir",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (history.isEmpty()) {
                        Text(
                            text = if (settings.zeroTraceHistoryEnabled) {
                                "Mode Tanpa Riwayat (Zero-Trace) aktif — daftar riwayat & log sesi tidak disimpan. Angka pembersihan terakhir tetap diperbarui di atas."
                            } else {
                                "Belum terdapat catatan pembersihan. Jalankan CLEAN NOW atau DEEP CLEAN untuk memulai diagnostik dan pembersihan."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val formatter = SimpleDateFormat("dd MMM HH:mm", Locale.US)
                        history.take(5).forEachIndexed { idx, item ->
                            if (idx > 0) Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.mode,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${formatter.format(Date(item.timestampMillis))} • ${item.filesDeleted} file",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = FormatUtils.formatBytes(item.freedBytes),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MintGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
