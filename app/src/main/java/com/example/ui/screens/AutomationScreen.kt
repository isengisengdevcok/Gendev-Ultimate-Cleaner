package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.AutomationConfig
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.PremiumCard
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationScreen(
    config: AutomationConfig,
    periodicWorkStatus: String,
    isRootActive: Boolean,
    isIgnoringBatteryOpt: Boolean,
    onToggleScheduled: (Boolean, Int) -> Unit,
    onToggleScreenOff: (Boolean) -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit,
    onToggleLowStorageGuard: (Boolean, Int) -> Unit,
    onSetCooldownMinutes: (Int) -> Unit,
    onTriggerManualTest: () -> Unit,
    onOpenBatteryOptimization: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingPermissionAction?.invoke()
        pendingPermissionAction = null
    }

    fun requestMonitorToggle(enable: Boolean, action: (Boolean) -> Unit) {
        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingPermissionAction = { action(true) }
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        action(enable)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Otomatisasi Tugas Latar Belakang",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MintGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Otomatisasi mendukung mode ${if (isRootActive) "Root (su -c)" else "Non-Root (Cache + SAF)"}. Semua pemicu NONAKTIF secara default dan tidak pernah menjalankan Legacy Cleaner.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Status WorkManager", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(periodicWorkStatus, style = MaterialTheme.typography.titleSmall, color = CyanAccent, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onTriggerManualTest,
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("auto_manual_test_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Uji Sekarang", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            item {
                PremiumCard(cornerRadius = 20.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Scheduled Cache Cleaning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Penjadwalan WorkManager setiap ${config.intervalHours} jam",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = config.scheduledEnabled,
                            onCheckedChange = { onToggleScheduled(it, config.intervalHours) },
                            modifier = Modifier.testTag("auto_scheduled_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 12, 24).forEach { hrs ->
                            FilterChip(
                                selected = config.intervalHours == hrs,
                                onClick = { onToggleScheduled(config.scheduledEnabled, hrs) },
                                label = { Text("${hrs} Jam") },
                                modifier = Modifier.testTag("auto_interval_${hrs}h")
                            )
                        }
                    }
                }
            }

            item {
                PremiumCard(cornerRadius = 20.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ScreenLockPortrait, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Screen-off Cache Cleaning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Eksekusi otomatis saat layar mati (menunggu jeda cooldown)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = config.screenOffEnabled,
                            onCheckedChange = { enabled ->
                                requestMonitorToggle(enabled, onToggleScreenOff)
                            },
                            modifier = Modifier.testTag("auto_screen_off_switch")
                        )
                    }
                }
            }

            item {
                PremiumCard(cornerRadius = 20.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.BatterySaver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Battery Saver Cleaning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Eksekusi pembersihan cache saat mode Penghemat Baterai aktif",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = config.batterySaverEnabled,
                            onCheckedChange = { enabled ->
                                requestMonitorToggle(enabled, onToggleBatterySaver)
                            },
                            modifier = Modifier.testTag("auto_battery_saver_switch")
                        )
                    }
                }
            }

            item {
                PremiumCard(cornerRadius = 20.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SdStorage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Low-Storage Guard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Pemicu otomatis saat ruang bebas < ${config.lowStorageThresholdGb} GB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = config.lowStorageGuardEnabled,
                            onCheckedChange = { enabled ->
                                requestMonitorToggle(enabled) { onToggleLowStorageGuard(it, config.lowStorageThresholdGb) }
                            },
                            modifier = Modifier.testTag("auto_low_storage_switch")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { gb ->
                            FilterChip(
                                selected = config.lowStorageThresholdGb == gb,
                                onClick = { onToggleLowStorageGuard(config.lowStorageGuardEnabled, gb) },
                                label = { Text("< $gb GB") }
                            )
                        }
                    }
                }
            }

            item {
                PremiumCard(cornerRadius = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Jeda Antar Pemicu (Cooldown)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Mencegah pembersihan berulang terlalu sering (${config.cooldownMinutes} menit)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 60).forEach { mins ->
                            FilterChip(
                                selected = config.cooldownMinutes == mins,
                                onClick = { onSetCooldownMinutes(mins) },
                                label = { Text("${mins} Menit") },
                                modifier = Modifier.testTag("auto_cooldown_${mins}m")
                            )
                        }
                    }
                }
            }

            item {
                PremiumCard(cornerRadius = 20.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.BatteryAlert,
                                contentDescription = null,
                                tint = if (isIgnoringBatteryOpt) MintGreen else AmberWarn
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Pengecualian Optimasi Baterai", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isIgnoringBatteryOpt) {
                                        "Diizinkan (layanan latar belakang stabil di ColorOS/Android)"
                                    } else {
                                        "Disarankan agar jadwal tidak dihentikan paksa oleh sistem"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isIgnoringBatteryOpt) MintGreen else AmberWarn
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = onOpenBatteryOptimization,
                            contentPadding = ActionButtonPadding,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Atur", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            item {
                PremiumCard(cornerRadius = 20.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, tint = MintGreen)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Status Boot & Eksekusi Terakhir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = config.bootStatusMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (config.lastAutoCleanTimestamp > 0L) {
                        val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Terakhir (${config.lastAutoCleanTrigger}): ${fmt.format(Date(config.lastAutoCleanTimestamp))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanAccent
                        )
                        if (config.lastAutoCleanSummary.isNotBlank()) {
                            Text(
                                text = config.lastAutoCleanSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
