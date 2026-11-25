package com.owledit.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.owledit.app.ui.i18n.tr
import com.owledit.app.ui.theme.*
import com.owledit.core.model.DiffEntry
import com.owledit.core.model.EntryInspection
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatNumber(value: Long): String = NumberFormat.getInstance(Locale.US).format(value)
fun formatNumber(value: Int): String = formatNumber(value.toLong())

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}

fun formatTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun EmptySessionCard(
    rootGranted: Boolean,
    selectedPackage: String,
    onOpenRoot: () -> Unit,
    onOpenLocalXml: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = OwlSoulGold,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = tr("Chưa mở dữ liệu Save nào", "No Save Session Loaded"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = tr(
                    "Hãy mở save Otherworld Legends trực tiếp qua Root hoặc chọn file .v2.playerprefs.xml từ bộ nhớ máy để chỉnh sửa.",
                    "Open Otherworld Legends save directly via Root or pick a .v2.playerprefs.xml file from device storage to start editing.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = rootGranted,
                onClick = onOpenRoot,
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Mở Save trực tiếp ($selectedPackage)", "Open Save via Root ($selectedPackage)"))
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenLocalXml,
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Mở file XML từ bộ nhớ (Offline / Test)", "Open Local XML File (Offline / Test)"))
            }
        }
    }
}

@Composable
fun DiffPreviewDialog(
    diffs: List<DiffEntry>,
    isRootMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val warningCount = diffs.count { !it.warning.isNullOrBlank() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.84f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Xem trước thay đổi (Diff)", "Preview Changes (Diff)"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = tr(
                                "${diffs.size} khóa đã thay đổi • Tự kiểm chứng AES-CBC & XML trước khi ghi",
                                "${diffs.size} modified keys • Auto-verifies AES-CBC & XML before writing",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = tr("Đóng", "Close"))
                    }
                }

                if (warningCount > 0) {
                    Surface(
                        color = OwlSoulGoldContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = OwlSoulGold)
                            Text(
                                text = tr(
                                    "Có $warningCount mục vượt ngưỡng khuyến nghị Safe-Max. Bạn vẫn có thể lưu nếu muốn.",
                                    "$warningCount item(s) exceed recommended Safe-Max caps. You may still save if desired.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFE4B5),
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(diffs, key = { it.rawKey }) { d ->
                        val (badgeBg, badgeFg) = when (d.changeType) {
                            "ADDED" -> OwlEmeraldContainer to OwlEmerald
                            "REMOVED" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
                            else -> OwlSapphireContainer to OwlSapphire
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = d.displayKey,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    StatusPill(d.changeType, badgeBg, badgeFg)
                                }
                                Text(
                                    text = tr("Cũ:  ${d.oldPreview}", "Old: ${d.oldPreview}"),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = tr("Mới: ${d.newPreview}", "New: ${d.newPreview}"),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = OwlEmerald,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!d.warning.isNullOrBlank()) {
                                    Text(
                                        text = "⚠ ${d.warning}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OwlSoulGold,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    ) {
                        Text(tr("Hủy", "Cancel"))
                    }
                    Button(
                        modifier = Modifier.weight(1.4f),
                        onClick = onConfirm,
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isRootMode) tr("Ghi An Toàn (Atomic)", "Write Safely (Atomic)")
                            else tr("Xuất File XML", "Export XML File")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EntryInspectorDialog(
    entry: EntryInspection,
    onDismiss: () -> Unit,
    onSaveRaw: (key: String, value: String, encrypt: Boolean) -> Unit,
    onPatchNrbf: (containerKey: String, entryKey: String, entryValue: String) -> Unit,
    onSetAllNrbf: (containerKey: String, uniformValue: String) -> Unit,
    onBeautifyJson: (String, (String) -> Unit) -> Unit,
    onMinifyJson: (String, (String) -> Unit) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.displayKey,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = tr(
                                "Raw: ${entry.rawKey.take(24)} • Loại: ${entry.valueKind} • ${if (entry.isEncrypted) "AES-256-CBC" else "Plain XML"}",
                                "Raw: ${entry.rawKey.take(24)} • Kind: ${entry.valueKind} • ${if (entry.isEncrypted) "AES-256-CBC" else "Plain XML"}",
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = tr("Đóng", "Close"))
                    }
                }

                if (entry.nrbf != null) {
                    NrbfDictInspectorBody(
                        containerKey = entry.displayKey.ifBlank { entry.rawKey },
                        nrbf = entry.nrbf,
                        onPatch = onPatchNrbf,
                        onSetAll = onSetAllNrbf,
                    )
                } else {
                    RawTextInspectorBody(
                        entry = entry,
                        onSave = onSaveRaw,
                        onBeautifyJson = onBeautifyJson,
                        onMinifyJson = onMinifyJson,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NrbfDictInspectorBody(
    containerKey: String,
    nrbf: com.owledit.core.model.NrbfDict,
    onPatch: (containerKey: String, entryKey: String, entryValue: String) -> Unit,
    onSetAll: (containerKey: String, uniformValue: String) -> Unit,
) {
    var search by remember(containerKey) { mutableStateOf("") }
    var newKey by remember(containerKey) { mutableStateOf("") }
    var newVal by remember(containerKey) { mutableStateOf("1") }
    var batchVal by remember(containerKey) { mutableStateOf("1") }

    val filtered = remember(nrbf.entries, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) nrbf.entries
        else nrbf.entries.filter { it.key.lowercase().contains(q) || it.value.lowercase().contains(q) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(
            text = tr(
                "NRBF ${nrbf.kind.kind} (${nrbf.entries.size} mục)",
                "NRBF ${nrbf.kind.kind} (${nrbf.entries.size} entries)",
            ),
            containerColor = OwlSapphireContainer,
            contentColor = OwlSapphire,
        )
        if (!nrbf.kind.keyEnum.isNullOrBlank()) {
            StatusPill(
                text = nrbf.kind.keyEnum,
                containerColor = OwlSoulGoldContainer,
                contentColor = OwlSoulGold,
            )
        }
    }

    // Add / Update single key row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = newKey,
            onValueChange = { newKey = it },
            label = { Text(tr("Khóa mới / sửa", "Add / edit key")) },
            singleLine = true,
            modifier = Modifier.weight(1.3f),
        )
        OutlinedTextField(
            value = newVal,
            onValueChange = { newVal = it },
            label = { Text(tr("Giá trị", "Value")) },
            singleLine = true,
            modifier = Modifier.weight(0.8f),
        )
        FilledTonalButton(
            onClick = {
                if (newKey.isNotBlank()) {
                    onPatch(containerKey, newKey.trim(), newVal.trim())
                    newKey = ""
                }
            },
        ) {
            Text(tr("Ghi", "Set"))
        }
    }

    // Batch Set-All bar
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text(tr("Lọc ${nrbf.entries.size} mục...", "Filter ${nrbf.entries.size} entries...")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.weight(1.2f),
        )
        OutlinedTextField(
            value = batchVal,
            onValueChange = { batchVal = it },
            label = { Text("Set All") },
            singleLine = true,
            modifier = Modifier.weight(0.6f),
        )
        Button(
            onClick = {
                if (batchVal.isNotBlank()) onSetAll(containerKey, batchVal.trim())
            },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Text(tr("Đặt hết", "Apply All"))
        }
    }

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(filtered, key = { it.key }) { item ->
            var draft by remember(item.key, item.value) { mutableStateOf(item.value) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.key,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.2f),
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                    )
                    FilledTonalIconButton(
                        enabled = draft != item.value,
                        onClick = { onPatch(containerKey, item.key, draft.trim()) },
                    ) {
                        Icon(Icons.Default.Check, contentDescription = tr("Lưu mục", "Save entry"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RawTextInspectorBody(
    entry: EntryInspection,
    onSave: (key: String, value: String, encrypt: Boolean) -> Unit,
    onBeautifyJson: (String, (String) -> Unit) -> Unit,
    onMinifyJson: (String, (String) -> Unit) -> Unit,
) {
    var draft by remember(entry.rawKey, entry.rawPlaintext) {
        mutableStateOf(entry.jsonPretty ?: entry.rawPlaintext)
    }

    val isJson = entry.valueKind == "json" || draft.trimStart().startsWith("{") || draft.trimStart().startsWith("[")
    if (isJson) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { onBeautifyJson(draft) { draft = it } }) {
                Text("Beautify JSON")
            }
            OutlinedButton(onClick = { onMinifyJson(draft) { draft = it } }) {
                Text("Minify JSON")
            }
        }
    }

    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .background(OwlBg, RoundedCornerShape(8.dp)),
        textStyle = LocalTextStyle.current.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        ),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = tr("${draft.length} ký tự", "${draft.length} chars"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
        )
        Button(
            onClick = {
                onSave(entry.rawKey, draft, entry.isEncrypted)
            },
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("Áp dụng vào phiên", "Apply to Session"))
        }
    }
}

@Composable
fun AboutAndNotesCard(
    versionName: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, OwlSoulGold.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = OwlSoulGold,
                    )
                    Text(
                        text = tr("Ghi Chú & Thông Tin Ứng Dụng", "Notes & About"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StatusPill(
                    text = "v$versionName",
                    containerColor = OwlSoulGoldContainer,
                    contentColor = OwlSoulGold,
                )
            }

            // Author Credit Banner
            Surface(
                color = OwlRubyContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, OwlRuby.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = OwlSoulGold,
                        modifier = Modifier.size(22.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr("Được tạo bởi Nguyễn Phan Đức Mạnh", "Created by Nguyễn Phan Đức Mạnh"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = OwlSoulGold,
                        )
                        Text(
                            text = tr(
                                "OWLVNEditor • Trình chỉnh sửa Save Otherworld Legends lõi Rust",
                                "OWLVNEditor • Rust-powered Otherworld Legends Save Editor",
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Usage & Safety Notes
            Text(
                text = tr("Ghi chú sử dụng an toàn:", "Usage & Safety Notes:"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val notes = listOf(
                tr(
                    "• Yêu cầu thiết bị Android đã Root (Magisk / KernelSU / APatch) hoặc giả lập PC đã bật Root (LDPlayer, MuMu, BlueStacks, Nox, MEmu).",
                    "• Requires a rooted Android device (Magisk / KernelSU / APatch) or a rooted PC emulator (LDPlayer, MuMu, BlueStacks, Nox, MEmu).",
                ),
                tr(
                    "• Luôn tạo bản Sao lưu (.owldb) trước khi chỉnh sửa để bảo toàn dữ liệu gốc 100%.",
                    "• Always create a Backup (.owldb) before editing so your original save is 100% protected.",
                ),
                tr(
                    "• Ưu tiên dùng gói Preset 'Full Safe Bundle' hoặc nút 'Chuẩn Hóa Về Mức An Toàn' để giữ chỉ số đúng giới hạn game và đồng bộ Cloud Save ổn định.",
                    "• Prefer the 'Full Safe Bundle' preset or 'Normalize To Safe Caps' to stay within natural game caps for smooth Cloud Save syncing.",
                ),
                tr(
                    "• Với 236+ Thành tựu: chọn 'Mở Hết (Tự nhận)' để vào game tự bấm 'Nhận Tất Cả' lấy thưởng và điểm thành tích tự nhiên.",
                    "• For 236+ Achievements: choose 'Unlock (Claimable)' so you can tap 'Claim All' in-game to collect rewards & points naturally.",
                ),
                tr(
                    "• App tự động dừng game (Force-Stop), tạo ảnh chụp Auto-Rollback và kiểm chứng SHA-256 trước mỗi lần ghi save.",
                    "• Automatically force-stops the game, snapshots Auto-Rollback, and verifies SHA-256 on every atomic write.",
                ),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                notes.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun AboutAndNotesDialog(
    versionName: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = OwlSoulGold)
                Text(
                    text = tr("Ghi Chú & Giới Thiệu", "Notes & About"),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = OwlRubyContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, OwlRuby.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "OWLVNEditor v$versionName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = tr("Được tạo bởi Nguyễn Phan Đức Mạnh", "Created by Nguyễn Phan Đức Mạnh"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = OwlSoulGold,
                        )
                    }
                }
                Text(
                    text = tr(
                        "• Yêu cầu Android đã Root hoặc giả lập PC bật Root (LDPlayer, MuMu, BlueStacks, Nox, MEmu).\n" +
                            "• Nên bấm Sao lưu (.owldb) trước khi chỉnh sửa save.\n" +
                            "• Dùng gói Full Safe Bundle / Chuẩn Hóa An Toàn để đồng bộ Cloud Save ổn định, tránh vượt trần game.\n" +
                            "• Mở Thành tựu ở chế độ Tự nhận để vào game bấm Nhận Tất Cả.\n" +
                            "• Hỗ trợ chuyển nhanh Tiếng Việt / English bằng nút VI / EN trên thanh tiêu đề.",
                        "• Requires rooted Android or rooted PC emulator (LDPlayer, MuMu, BlueStacks, Nox, MEmu).\n" +
                            "• Create an .owldb backup before modifying your save.\n" +
                            "• Use Full Safe Bundle / Normalize Safe Caps for smooth Cloud Save syncing.\n" +
                            "• Use Claimable Achievement mode to tap Claim All in-game.\n" +
                            "• Switch instantly between Vietnamese & English via the VI / EN button in the top bar.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(tr("Đóng", "Close"))
            }
        },
    )
}
