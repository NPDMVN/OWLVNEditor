package com.owledit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.owledit.app.ui.components.EmptySessionCard
import com.owledit.app.ui.components.StatusPill
import com.owledit.app.ui.i18n.LocalIsEnglish
import com.owledit.app.ui.i18n.tr
import com.owledit.app.ui.theme.*

private data class ExplorerCat(val id: String, val labelVi: String, val labelEn: String)

private val EXPLORER_CATEGORIES = listOf(
    ExplorerCat("all", "Tất cả", "All"),
    ExplorerCat("nrbf", "NRBF Dict", "NRBF Dict"),
    ExplorerCat("json", "JSON", "JSON"),
    ExplorerCat("currency", "Tiền tệ", "Currency"),
    ExplorerCat("hero", "Tướng", "Hero"),
    ExplorerCat("skin", "Skin", "Skin"),
    ExplorerCat("asura", "Tu La", "Asura"),
    ExplorerCat("six_realms", "Lục Đạo", "Six Realms"),
    ExplorerCat("pet", "Thú cưng", "Pet"),
    ExplorerCat("achievement", "Thành tựu", "Achievement"),
    ExplorerCat("account", "Tài khoản", "Account"),
    ExplorerCat("plain", "Plain", "Plain"),
    ExplorerCat("unknown", "Chưa định danh", "Unknown"),
)

@Composable
fun ExplorerScreen(
    state: MainUiState,
    onOpenRoot: () -> Unit,
    onOpenLocalXml: () -> Unit,
    onQueryChange: (search: String, category: String, onlyModified: Boolean, offset: Int) -> Unit,
    onInspectKey: (String) -> Unit,
    onAddRawKey: (key: String, value: String, encrypt: Boolean) -> Unit,
) {
    if (!state.hasActiveSession) {
        EmptySessionCard(
            rootGranted = state.env.rootGranted,
            selectedPackage = state.settings.selectedPackage,
            onOpenRoot = onOpenRoot,
            onOpenLocalXml = onOpenLocalXml,
        )
        return
    }

    val isEn = LocalIsEnglish.current
    var showAddKeyDialog by remember { mutableStateOf(false) }
    val page = state.explorerPage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Search & Add button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.explorerSearch,
                onValueChange = { q ->
                    onQueryChange(q, state.explorerCategory, state.explorerOnlyModified, 0)
                },
                placeholder = { Text(tr("Tìm key, MD5, hoặc giá trị...", "Search key, MD5, or value...")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.explorerSearch.isNotEmpty()) {
                        IconButton(onClick = {
                            onQueryChange("", state.explorerCategory, state.explorerOnlyModified, 0)
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = tr("Xóa tìm kiếm", "Clear search"))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = { showAddKeyDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = tr("Thêm khóa", "Add key"))
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.explorerOnlyModified,
                onClick = {
                    onQueryChange(state.explorerSearch, state.explorerCategory, !state.explorerOnlyModified, 0)
                },
                label = { Text(tr("Đã sửa (${state.modifiedCount})", "Modified (${state.modifiedCount})")) },
                leadingIcon = if (state.explorerOnlyModified) {
                    { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
            )
            EXPLORER_CATEGORIES.forEach { cat ->
                FilterChip(
                    selected = state.explorerCategory == cat.id,
                    onClick = {
                        onQueryChange(state.explorerSearch, cat.id, state.explorerOnlyModified, 0)
                    },
                    label = { Text(if (isEn) cat.labelEn else cat.labelVi) },
                )
            }
        }

        // Paging summary bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val fromIdx = if (page.totalMatched == 0) 0 else page.offset + 1
            val toIdx = (page.offset + page.items.size).coerceAtMost(page.totalMatched)
            Text(
                text = tr(
                    "Hiển thị $fromIdx–$toIdx / ${page.totalMatched} mục",
                    "Showing $fromIdx–$toIdx of ${page.totalMatched} keys",
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    enabled = page.offset > 0,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    onClick = {
                        val prev = (page.offset - page.limit).coerceAtLeast(0)
                        onQueryChange(state.explorerSearch, state.explorerCategory, state.explorerOnlyModified, prev)
                    },
                ) {
                    Text(tr("Trang trước", "Prev"))
                }
                OutlinedButton(
                    enabled = page.offset + page.items.size < page.totalMatched,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    onClick = {
                        val next = page.offset + page.limit
                        onQueryChange(state.explorerSearch, state.explorerCategory, state.explorerOnlyModified, next)
                    },
                ) {
                    Text(tr("Trang sau", "Next"))
                }
            }
        }

        // Paged Entries List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(page.items, key = { it.rawKey }) { item ->
                val (kindBg, kindFg) = when (item.valueKind) {
                    "nrbf" -> OwlSapphireContainer to OwlSapphire
                    "json" -> OwlSoulGoldContainer to OwlSoulGold
                    "number", "bool" -> OwlEmeraldContainer to OwlEmerald
                    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(
                        1.dp,
                        if (item.modified) OwlEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onInspectKey(item.rawKey) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = item.displayKey,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (item.modified) {
                                    StatusPill(tr("Sửa", "Mod"), OwlEmeraldContainer, OwlEmerald)
                                }
                                StatusPill(item.valueKind.uppercase(), kindBg, kindFg)
                            }
                        }
                        Text(
                            text = item.preview.ifBlank { tr("(rỗng)", "(empty)") },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${item.category} • ${if (item.isEncrypted) "AES-CBC" else "Plain"} • ${item.byteLen} chars",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }

    if (showAddKeyDialog) {
        var newKey by remember { mutableStateOf("") }
        var newVal by remember { mutableStateOf("") }
        var encrypt by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            title = { Text(tr("Thêm / Ghi Đè Khóa PlayerPrefs", "Add / Overwrite PlayerPrefs Key")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text(tr("Tên khóa (Plain hoặc MD5)", "Key name (Plain or MD5)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newVal,
                        onValueChange = { newVal = it },
                        label = { Text(tr("Giá trị", "Value")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = encrypt, onCheckedChange = { encrypt = it })
                        Text(tr("Mã hóa bằng CryptoPrefs (MD5 + AES-256-CBC)", "Encrypt via CryptoPrefs (MD5 + AES-256-CBC)"))
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newKey.isNotBlank(),
                    onClick = {
                        onAddRawKey(newKey.trim(), newVal, encrypt)
                        showAddKeyDialog = false
                    },
                ) {
                    Text(tr("Thêm", "Add"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddKeyDialog = false }) { Text(tr("Hủy", "Cancel")) }
            },
        )
    }
}
