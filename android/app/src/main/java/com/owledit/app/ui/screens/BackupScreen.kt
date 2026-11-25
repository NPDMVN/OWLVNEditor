package com.owledit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owledit.app.ui.MainUiState
import com.owledit.app.ui.components.*
import com.owledit.app.ui.i18n.tr
import com.owledit.app.ui.theme.*
import com.owledit.core.model.BackupInspectResult
import com.owledit.core.model.RollbackRecord

@Composable
fun BackupScreen(
    state: MainUiState,
    onCreateBackup: (note: String) -> Unit,
    onRefreshBackups: () -> Unit,
    onImportBackup: () -> Unit,
    onExportBackup: (archivePath: String, suggestedName: String) -> Unit,
    onRestoreBackup: (archivePath: String) -> Unit,
    onDeleteBackup: (archivePath: String) -> Unit,
    onRestoreRollback: (RollbackRecord) -> Unit,
) {
    var noteInput by remember { mutableStateOf("") }
    var confirmRestoreTarget by remember { mutableStateOf<BackupInspectResult?>(null) }
    var confirmDeleteTarget by remember { mutableStateOf<BackupInspectResult?>(null) }
    var confirmRollbackTarget by remember { mutableStateOf<RollbackRecord?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Create Backup Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionTitle(
                        title = tr("Tạo Bản Sao Lưu (.owldb)", "Create Save Backup (.owldb)"),
                        subtitle = tr(
                            "Đóng gói ZIP + manifest.json + kiểm tra toàn vẹn SHA-256 cho ${state.settings.selectedPackage}",
                            "Packages ZIP + manifest.json + SHA-256 integrity hash for ${state.settings.selectedPackage}",
                        ),
                    )
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text(tr("Ghi chú (vd: Trước khi nâng Tu La)", "Note (e.g. Before maxing Asura)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1.2f),
                            enabled = state.env.rootGranted || state.hasActiveSession,
                            onClick = {
                                onCreateBackup(noteInput)
                                noteInput = ""
                            },
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Sao Lưu Ngay", "Backup Now"))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onImportBackup,
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Nhập .owldb", "Import .owldb"))
                        }
                    }
                    Text(
                        text = tr("Thư mục lưu: ${state.backupDirPath}", "Backup directory: ${state.backupDirPath}"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Backups list header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SectionTitle(
                        title = tr("Danh Sách Bản Sao Lưu (${state.backups.size})", "Saved Backups (${state.backups.size})"),
                        subtitle = tr(
                            "Mỗi file đều được kiểm chứng SHA-256 tự động bởi lõi Rust.",
                            "Every archive is automatically SHA-256 verified by Rust Core.",
                        ),
                    )
                }
                IconButton(onClick = onRefreshBackups) {
                    Icon(Icons.Default.Refresh, contentDescription = tr("Làm mới", "Refresh"))
                }
            }
        }

        if (state.backups.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = tr(
                            "Chưa có bản sao lưu .owldb nào. Nhấn 'Sao Lưu Ngay' trước khi chỉnh sửa save!",
                            "No .owldb backups yet. Tap 'Backup Now' before editing your save!",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        } else {
            items(state.backups, key = { it.archivePath }) { bk ->
                val fileName = bk.archivePath.substringAfterLast('/')
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (bk.integrityOk) OwlEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = bk.manifest.note.ifBlank { fileName },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(6.dp))
                            StatusPill(
                                text = if (bk.integrityOk) "SHA-256 OK" else tr("LỖI TOÀN VẸN", "INTEGRITY ERR"),
                                containerColor = if (bk.integrityOk) OwlEmeraldContainer else MaterialTheme.colorScheme.errorContainer,
                                contentColor = if (bk.integrityOk) OwlEmerald else MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            text = "${bk.manifest.packageName} • UID: ${bk.manifest.uid ?: "—"} • ${formatBytes(bk.archiveSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = tr(
                                "Thời gian: ${formatTimestamp(bk.manifest.createdAtEpochMs)} • ${bk.manifest.entries.size} file",
                                "Created: ${formatTimestamp(bk.manifest.createdAtEpochMs)} • ${bk.manifest.entries.size} file(s)",
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                modifier = Modifier.weight(1.2f),
                                enabled = bk.integrityOk && state.env.rootGranted,
                                onClick = { confirmRestoreTarget = bk },
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("Khôi Phục", "Restore"))
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(0.9f),
                                onClick = { onExportBackup(bk.archivePath, fileName) },
                            ) {
                                Text(tr("Xuất", "Export"))
                            }
                            IconButton(onClick = { confirmDeleteTarget = bk }) {
                                Icon(Icons.Default.Delete, contentDescription = tr("Xóa", "Delete"), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Auto-Rollback snapshots section
        item {
            Spacer(Modifier.height(6.dp))
            SectionTitle(
                title = tr(
                    "Ảnh Chụp Tự Động Hoàn Tác — Auto-Rollback (${state.rollbacks.size})",
                    "Auto-Rollback Snapshots (${state.rollbacks.size})",
                ),
                subtitle = tr(
                    "OWLVNEditor tự động lưu bản gốc trước mỗi lần ghi save để bạn hoàn tác tức thì nếu cần.",
                    "OWLVNEditor automatically snapshots your original save before every write so you can roll back instantly.",
                ),
            )
        }

        if (state.rollbacks.isEmpty()) {
            item {
                Text(
                    text = tr("Chưa có bản Auto-Rollback nào.", "No Auto-Rollback snapshots yet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.rollbacks, key = { it.filePath }) { rb ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rb.packageName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${formatTimestamp(rb.createdAtMs)} • ${formatBytes(rb.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(
                            enabled = state.env.rootGranted,
                            onClick = { confirmRollbackTarget = rb },
                        ) {
                            Text(tr("Hoàn Tác", "Rollback"))
                        }
                    }
                }
            }
        }
    }

    confirmRestoreTarget?.let { bk ->
        AlertDialog(
            onDismissRequest = { confirmRestoreTarget = null },
            title = { Text(tr("Xác Nhận Khôi Phục Save", "Confirm Save Restore")) },
            text = {
                Text(
                    tr(
                        "Ghi đè save hiện tại của ${state.settings.selectedPackage} bằng bản sao lưu '${bk.manifest.note}'?\n\nOWLVNEditor sẽ tự dừng game và lưu ảnh chụp Auto-Rollback của save hiện tại trước khi ghi.",
                        "Overwrite current save of ${state.settings.selectedPackage} with backup '${bk.manifest.note}'?\n\nOWLVNEditor will automatically force-stop the game and create an Auto-Rollback snapshot first.",
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    val path = bk.archivePath
                    confirmRestoreTarget = null
                    onRestoreBackup(path)
                }) { Text(tr("Khôi Phục Ngay", "Restore Now")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreTarget = null }) { Text(tr("Hủy", "Cancel")) }
            },
        )
    }

    confirmDeleteTarget?.let { bk ->
        AlertDialog(
            onDismissRequest = { confirmDeleteTarget = null },
            title = { Text(tr("Xóa Bản Sao Lưu?", "Delete Backup?")) },
            text = {
                Text(
                    tr(
                        "Xóa vĩnh viễn file ${bk.archivePath.substringAfterLast('/')}?",
                        "Permanently delete ${bk.archivePath.substringAfterLast('/')}?",
                    )
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val path = bk.archivePath
                        confirmDeleteTarget = null
                        onDeleteBackup(path)
                    },
                ) { Text(tr("Xóa", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTarget = null }) { Text(tr("Hủy", "Cancel")) }
            },
        )
    }

    confirmRollbackTarget?.let { rb ->
        AlertDialog(
            onDismissRequest = { confirmRollbackTarget = null },
            title = { Text(tr("Hoàn Tác Từ Rollback", "Restore Auto-Rollback")) },
            text = {
                Text(
                    tr(
                        "Khôi phục save về trạng thái lúc ${formatTimestamp(rb.createdAtMs)} cho ${rb.packageName}?",
                        "Restore save back to ${formatTimestamp(rb.createdAtMs)} for ${rb.packageName}?",
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmRollbackTarget = null
                    onRestoreRollback(rb)
                }) { Text(tr("Hoàn Tác", "Rollback")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRollbackTarget = null }) { Text(tr("Hủy", "Cancel")) }
            },
        )
    }
}
