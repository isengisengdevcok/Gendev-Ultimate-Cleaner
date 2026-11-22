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
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.FormatUtils
import com.example.model.WhatsAppCategoryType
import com.example.ui.CleanerUiState
import com.example.ui.components.ActionButtonPadding
import com.example.ui.components.PremiumCard
import com.example.ui.components.SelectAllControlRow
import com.example.ui.theme.CoralRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MintContainer
import com.example.ui.theme.MintGreen

fun LazyListScope.whatsAppRootSectionItems(
    uiState: CleanerUiState,
    animatedOpProgress: Float,
    opPercent: Int,
    onScanWhatsApp: () -> Unit,
    onToggleWhatsAppGroup: (WhatsAppCategoryType) -> Unit,
    onToggleAllWhatsApp: (Boolean) -> Unit,
    onRequestCleanWhatsApp: () -> Unit
) {
    val busy = uiState.isWhatsAppBusy
    val groups = uiState.whatsAppGroups
    val selected = groups.filter { it.isSelected }
    val selectedFiles = selected.sumOf { it.fileCount }
    val selectedBytes = selected.sumOf { it.sizeBytes }
    val allSelected = groups.isNotEmpty() && selected.size == groups.size

    item {
        PremiumCard(cornerRadius = 22.dp) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MintContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        tint = MintGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Akun WhatsApp Terkunci Aman: shared_prefs, databases, Backups, msgstore*.crypt*, me, key, rc2, secret, & pw tidak akan dihapus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MintGreen
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
                        text = uiState.statusBanner ?: "Memindai WhatsApp via Root...",
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
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { animatedOpProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Terpilih: ${selected.size} kategori • $selectedFiles berkas (${FormatUtils.formatBytes(selectedBytes)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onScanWhatsApp,
                    enabled = !busy,
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("scan_whatsapp_root_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pindai WA", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = onRequestCleanWhatsApp,
                    enabled = !busy && selected.isNotEmpty(),
                    contentPadding = ActionButtonPadding,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("clean_whatsapp_root_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralRed,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Musnahkan", maxLines = 1, softWrap = false)
                }
            }
        }
    }

    item {
        SelectAllControlRow(
            allSelected = allSelected,
            selectedCount = selected.size,
            totalCount = groups.size,
            enabled = !busy,
            label = "Pilih Semua Kategori WhatsApp",
            testTag = "whatsapp_select_all_checkbox",
            onToggleSelectAll = onToggleAllWhatsApp
        )
    }

    items(groups, key = { it.type.name }) { group ->
        PremiumCard(
            cornerRadius = 18.dp,
            modifier = Modifier.clickable(enabled = !busy) { onToggleWhatsAppGroup(group.type) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = group.isSelected,
                    onCheckedChange = { if (!busy) onToggleWhatsAppGroup(group.type) },
                    modifier = Modifier.testTag("whatsapp_checkbox_${group.type.name}")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (group.isScanned) FormatUtils.formatBytes(group.sizeBytes) else "Belum dipindai",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (group.isScanned) MintGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = group.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (group.isScanned) {
                        Text(
                            text = "${group.fileCount} berkas terdeteksi (Root)",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanAccent
                        )
                    }
                    if (group.sampleFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        group.sampleFiles.take(2).forEach { sample ->
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
