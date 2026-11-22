package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppSettings
import com.example.model.OperationMode
import com.example.model.RootStatus
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.PremiumCard
import com.example.ui.components.RootStatusPill
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    rootStatus: RootStatus,
    suVersion: String,
    isRootActive: Boolean,
    hasUsageStatsAccess: Boolean,
    onSetOperationMode: (OperationMode) -> Unit,
    onAddSafUri: (String) -> Unit,
    onRemoveSafUri: (String) -> Unit,
    onOpenUsageAccess: () -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetPurpleAccent: (Boolean) -> Unit,
    onSetAnimations: (Boolean) -> Unit,
    onSetAutoScroll: (Boolean) -> Unit,
    onSetConfirmation: (Boolean) -> Unit,
    onSetSecureShred: (Boolean) -> Unit,
    onSetZeroTrace: (Boolean) -> Unit,
    onWipeHistoryAndLogsNow: () -> Unit,
    onAddExclusion: (String) -> Unit,
    onRemoveExclusion: (String) -> Unit,
    onClearHistory: () -> Unit,
    onVerifyRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var newExclusionText by remember { mutableStateOf("") }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            onAddSafUri(uri.toString())
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
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Status Otorisasi Root", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onVerifyRoot,
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier.testTag("settings_recheck_root_button")
                        ) {
                            Text("Verifikasi", maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    RootStatusPill(rootStatus = rootStatus)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Manajer SU: $suVersion • Status aktif saat ini: ${if (isRootActive) "Root (su -c)" else "Non-Root (SAF & API Android)"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Dual Root & Non-Root Mode Switcher + SAF / Usage Access
            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mode Eksekusi (Root & Non-Root)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val modeDesc = when (settings.operationMode) {
                        OperationMode.AUTO -> "Otomatis memakai Root (su -c) jika tersedia, atau fallback aman ke Non-Root (SAF & API Android)."
                        OperationMode.ROOT -> "Memprioritaskan eksekusi superuser penuh via su -c untuk sistem & cache semua aplikasi."
                        OperationMode.NON_ROOT -> "Menggunakan izin folder SAF, Pemilih Foto Android, & API native tanpa akses root."
                    }
                    Text(
                        text = modeDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OperationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.operationMode == mode,
                                onClick = { onSetOperationMode(mode) },
                                label = { Text(mode.label, maxLines = 1, softWrap = false) },
                                modifier = Modifier.testTag("mode_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { safFolderLauncher.launch(null) },
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_add_saf_folder"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Folder SAF", maxLines = 1, softWrap = false)
                        }
                        FilledTonalButton(
                            onClick = onOpenUsageAccess,
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("settings_usage_access_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.QueryStats, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Usage Stats", maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Izin Usage Access: ${if (hasUsageStatsAccess) "Aktif (Statistik cache terbaca)" else "Belum Aktif (Opsional untuk mode Non-Root)"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasUsageStatsAccess) MintGreen else CyanAccent
                    )

                    if (settings.grantedSafUris.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Folder SAF Terotorisasi (${settings.grantedSafUris.size}):", style = MaterialTheme.typography.labelMedium)
                        settings.grantedSafUris.forEach { uriStr ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uriStr,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { onRemoveSafUri(uriStr) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Hapus izin folder SAF")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Text("Preferensi Antarmuka & Operasi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingToggleRow(
                        title = "Dark Theme",
                        subtitle = "Tema gelap Material You (#100F16)",
                        checked = settings.darkTheme,
                        onCheckedChange = onSetDarkTheme,
                        testTag = "setting_dark_theme"
                    )
                    SettingToggleRow(
                        title = "Purple Accent",
                        subtitle = "Aksen utama Purple #B69CFF",
                        checked = settings.purpleAccent,
                        onCheckedChange = onSetPurpleAccent,
                        testTag = "setting_purple_accent"
                    )
                    SettingToggleRow(
                        title = "Animation Toggle",
                        subtitle = "Animasi transisi dan indikator pemindaian",
                        checked = settings.animationsEnabled,
                        onCheckedChange = onSetAnimations,
                        testTag = "setting_animations"
                    )
                    SettingToggleRow(
                        title = "Auto Scroll Log",
                        subtitle = "Gulir otomatis ke entri konsol terbaru",
                        checked = settings.autoScrollLog,
                        onCheckedChange = onSetAutoScroll,
                        testTag = "setting_autoscroll"
                    )
                    SettingToggleRow(
                        title = "Cleaning Confirmation",
                        subtitle = "Dialog konfirmasi sebelum Clean Now dan Deep Clean",
                        checked = settings.cleaningConfirmation,
                        onCheckedChange = onSetConfirmation,
                        testTag = "setting_confirmation"
                    )
                    SettingToggleRow(
                        title = "Hapus Permanen Anti-Recovery",
                        subtitle = "Timpa blok data nol, rename acak, & bersihkan indeks MediaStore",
                        checked = settings.secureShredEnabled,
                        onCheckedChange = onSetSecureShred,
                        testTag = "setting_secure_shred"
                    )
                    SettingToggleRow(
                        title = "Mode Tanpa Riwayat (Zero-Trace)",
                        subtitle = "Otomatis hapus daftar riwayat, cache thumbnail, & log sesi",
                        checked = settings.zeroTraceHistoryEnabled,
                        onCheckedChange = onSetZeroTrace,
                        testTag = "setting_zero_trace"
                    )
                }
            }

            item {
                PremiumCard(cornerRadius = 22.dp) {
                    Text("Manajer Pengecualian (Exclusion Manager)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Direktori dan berkas sistem berikut dikunci secara permanen:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        "/sdcard/Download/KitsuneBackup",
                        "LineageOS-18.1-arm64-a-Vanilla.img.xz",
                        "/sdcard/UltimateCleaner.log",
                        "Basis Data & Konfigurasi (*.db*, shared_prefs)"
                    ).forEach { locked ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(locked, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newExclusionText,
                            onValueChange = { newExclusionText = it },
                            label = { Text("Tambah path atau pola pengecualian") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exclusion_input")
                        )
                        Button(
                            onClick = {
                                if (newExclusionText.isNotBlank()) {
                                    onAddExclusion(newExclusionText)
                                    newExclusionText = ""
                                }
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("add_exclusion_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Tambah pengecualian")
                        }
                    }

                    if (settings.customExclusions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        settings.customExclusions.forEach { custom ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = custom,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { onRemoveExclusion(custom) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Hapus pengecualian")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                PremiumCard(cornerRadius = 22.dp) {
                    FilledTonalButton(
                        onClick = onWipeHistoryAndLogsNow,
                        contentPadding = ActionButtonPadding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("wipe_traces_now_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bersihkan Jejak Riwayat & Log Sekarang", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showClearHistoryDialog = true },
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("clear_history_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Total", maxLines = 1, softWrap = false)
                        }
                        FilledTonalButton(
                            onClick = { showAboutDialog = true },
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("about_app_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tentang App", maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Ultimate Cleaner Root Edition • Dibuat oleh Gendev",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Reset Riwayat Pembersihan?") },
            text = { Text("Statistik akumulasi ruang yang dibersihkan dan catatan riwayat akan diatur ulang ke nol.") },
            confirmButton = {
                Button(onClick = {
                    showClearHistoryDialog = false
                    onClearHistory()
                }) {
                    Text("RESET")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("BATAL") }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("ULTIMATE CLEANER ROOT EDITION") },
            text = {
                Text(
                    "Rilis 1.1 • Dibuat oleh Gendev\nNative Kotlin & Jetpack Compose\n\n" +
                        "• Dual Mode: Eksekusi Superuser (su -c) & Mode Non-Root (SAF, Photo Picker, Usage Stats)\n" +
                        "• AI Smart Hub (On-Device): Prediktor Memori, Timeline Delta, Triage Konteks & Visual dHash, Analis Crash/ANR, Penghemat Media, & Pre-Launch Conditioner\n" +
                        "• Otomatisasi WorkManager + Low-Storage Guard + PathValidator"
                )
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) { Text("TUTUP") }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
