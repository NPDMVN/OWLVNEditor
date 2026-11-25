package com.owledit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owledit.app.BuildConfig
import com.owledit.app.ui.MainUiState
import com.owledit.app.ui.SaveSource
import com.owledit.app.ui.components.*
import com.owledit.app.ui.i18n.LocalIsEnglish
import com.owledit.app.ui.i18n.localizedLabel
import com.owledit.app.ui.i18n.tr
import com.owledit.app.ui.theme.*

@Composable
fun HomeScreen(
    state: MainUiState,
    onRefreshEnv: () -> Unit,
    onSelectPackage: (String) -> Unit,
    onOpenSaveRoot: (String) -> Unit,
    onOpenLocalXml: () -> Unit,
    onQuickBackup: () -> Unit,
    onLaunchGame: () -> Unit,
    onRequestSave: () -> Unit,
    onGoVisualEditor: () -> Unit,
) {
    val isEn = LocalIsEnglish.current
    var showCustomPkgDialog by remember { mutableStateOf(false) }

    val diagnosticText = remember(state.env, isEn) {
        when {
            !state.env.rootGranted -> tr(
                isEn,
                "Chưa có quyền Root. Bạn vẫn có thể dùng chế độ 'Mở file XML (Offline)' để chỉnh sửa thử hoặc cấp quyền Root cho OWLVNEditor trong Magisk/KernelSU/Cài đặt giả lập.",
                "Root permission not granted. You can still use 'Open XML File (Offline)' or grant Root access to OWLVNEditor in Magisk / KernelSU / Emulator settings.",
            )
            state.env.installedPackages.isEmpty() -> tr(
                isEn,
                "Root OK (${state.env.suVariant.localizedLabel(false)}). Chưa phát hiện gói Otherworld Legends đã cài — bạn có thể chọn thủ công tên gói hoặc dùng chế độ Mở file XML.",
                "Root OK (${state.env.suVariant.localizedLabel(true)}). No installed Otherworld Legends package detected — you can pick a custom package name or open a local XML file.",
            )
            else -> tr(
                isEn,
                "Sẵn sàng (${state.env.suVariant.localizedLabel(false)} • ${state.env.emulatorType.localizedLabel(false)}). Tìm thấy ${state.env.installedPackages.size} gói game.",
                "Ready (${state.env.suVariant.localizedLabel(true)} • ${state.env.emulatorType.localizedLabel(true)}). Found ${state.env.installedPackages.size} game package(s).",
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Environment & Root Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (state.env.rootGranted) OwlEmerald.copy(alpha = 0.55f) else OwlSoulGold.copy(alpha = 0.55f),
                ),
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
                        ) {
                            Icon(
                                imageVector = if (state.env.rootGranted) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
                                contentDescription = null,
                                tint = if (state.env.rootGranted) OwlEmerald else OwlSoulGold,
                            )
                            Text(
                                text = if (state.env.rootGranted) {
                                    tr("Root & Rust Helper Sẵn Sàng", "Root & Rust Helper Ready")
                                } else {
                                    tr("Chưa Cấp Quyền Root", "Root Access Not Granted")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        IconButton(onClick = onRefreshEnv) {
                            Icon(Icons.Default.Refresh, contentDescription = tr("Quét lại", "Refresh"))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusPill(
                            text = state.env.suVariant.localizedLabel(isEn),
                            containerColor = if (state.env.rootGranted) OwlEmeraldContainer else OwlSoulGoldContainer,
                            contentColor = if (state.env.rootGranted) OwlEmerald else OwlSoulGold,
                        )
                        StatusPill(
                            text = state.env.emulatorType.localizedLabel(isEn),
                            containerColor = OwlSapphireContainer,
                            contentColor = OwlSapphire,
                        )
                    }

                    Text(
                        text = "${state.env.androidVersion} • ABI: ${state.env.abi} • SELinux: ${state.env.selinuxMode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Core: ${state.env.coreBanner}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.env.helperReady) {
                        Text(
                            text = "Helper: ${state.env.helperBanner}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = OwlEmerald,
                        )
                    }
                    Text(
                        text = diagnosticText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // 2. Installed Game Packages & Target Selector
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SectionTitle(
                                title = tr("Gói Game Otherworld Legends", "Otherworld Legends Package"),
                                subtitle = tr("Đang chọn: ${state.settings.selectedPackage}", "Selected: ${state.settings.selectedPackage}"),
                            )
                        }
                        TextButton(onClick = { showCustomPkgDialog = true }) {
                            Text(tr("Chọn gói khác", "Change Pkg"))
                        }
                    }

                    if (state.env.installedPackages.isNotEmpty()) {
                        state.env.installedPackages.forEach { pkg ->
                            val selected = pkg.packageName == state.settings.selectedPackage
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) OwlRubyContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) OwlRuby else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPackage(pkg.packageName) },
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
                                            text = "${pkg.appLabel} (${pkg.versionName})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = pkg.packageName,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    StatusPill(
                                        text = if (pkg.saveExists) "Save ${formatBytes(pkg.saveSizeBytes)}" else tr("Chưa có save", "No save yet"),
                                        containerColor = if (pkg.saveExists) OwlEmeraldContainer else OwlSurface,
                                        contentColor = if (pkg.saveExists) OwlEmerald else OwlTextSecondary,
                                    )
                                }
                            }
                        }
                    }

                    // Primary Open Action Buttons
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.env.rootGranted,
                        onClick = { onOpenSaveRoot(state.settings.selectedPackage) },
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Mở Save Trực Tiếp Bằng Root", "Open Save Directly via Root"))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onOpenLocalXml,
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Mở File XML", "Open XML"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            enabled = state.env.rootGranted || state.hasActiveSession,
                            onClick = onQuickBackup,
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Sao Lưu Nhanh", "Quick Backup"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // 3. Active Save Session Summary Card
        val dash = state.dashboard
        if (dash != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, OwlSapphire.copy(alpha = 0.55f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("Phiên Save Đang Mở", "Active Save Session"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                val srcText = when (val s = state.saveSource) {
                                    is SaveSource.RootPackage -> "Root: ${s.packageName}"
                                    is SaveSource.LocalXmlFile -> tr("File cục bộ: ${s.displayName}", "Local file: ${s.displayName}")
                                    SaveSource.None -> ""
                                }
                                Text(
                                    text = tr(
                                        "$srcText • Giải mã trong ${state.lastOpenMs} ms",
                                        "$srcText • Decrypted in ${state.lastOpenMs} ms",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OwlSapphire,
                                )
                            }
                            if (dash.modifiedCount > 0) {
                                StatusPill(
                                    text = tr("${dash.modifiedCount} thay đổi", "${dash.modifiedCount} changes"),
                                    containerColor = OwlRubyContainer,
                                    contentColor = OwlRuby,
                                )
                            }
                        }

                        // Account Info Summary
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = tr(
                                        "UID tài khoản: ${dash.account.uid ?: "Không có (Offline)"}",
                                        "Account UID: ${dash.account.uid ?: "None (Offline)"}",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (!dash.account.email.isNullOrBlank()) {
                                    Text(
                                        text = "Email / SDK: ${dash.account.email}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!dash.account.cloudSaveId.isNullOrBlank()) {
                                    Text(
                                        text = "CloudSaveId: ${dash.account.cloudSaveId}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        // Quick Stats Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatBox(tr("Tổng khóa", "Total Keys"), formatNumber(dash.totalEntries), Modifier.weight(1f))
                            StatBox(tr("AES giải mã", "AES Keys"), formatNumber(dash.encryptedEntries), Modifier.weight(1f))
                            StatBox("NRBF Dict", formatNumber(dash.nrbfContainers), Modifier.weight(1f))
                            StatBox(
                                tr("Tướng mở", "Heroes"),
                                "${dash.heroes.count { it.unlocked }}/${dash.heroes.size}",
                                Modifier.weight(1f),
                            )
                        }

                        // Quick Currency Snapshot
                        val topCurrencies = dash.currencies.take(3)
                        if (topCurrencies.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                topCurrencies.forEach { c ->
                                    val cLabel = if (isEn) {
                                        state.catalog.currencies[c.key]?.en?.ifBlank { c.label } ?: c.label
                                    } else {
                                        c.label.substringBefore(" (")
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = OwlBg,
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text(
                                                text = cLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = formatNumber(c.value),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = OwlSoulGold,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = onGoVisualEditor,
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(tr("Chỉnh Sửa Ngay", "Quick Edit"))
                            }
                            if (dash.modifiedCount > 0) {
                                FilledTonalButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = onRequestSave,
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Lưu (${dash.modifiedCount})", "Save (${dash.modifiedCount})"))
                                }
                            } else if (state.env.rootGranted) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = onLaunchGame,
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(tr("Vào Game", "Launch Game"))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Notes & Author Credit Card
        item {
            AboutAndNotesCard(versionName = BuildConfig.VERSION_NAME)
        }
    }

    if (showCustomPkgDialog) {
        var customPkg by remember { mutableStateOf(state.settings.selectedPackage) }
        AlertDialog(
            onDismissRequest = { showCustomPkgDialog = false },
            title = { Text(tr("Chọn Gói Otherworld Legends", "Select Otherworld Legends Package")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customPkg,
                        onValueChange = { customPkg = it },
                        label = { Text("Package Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = tr(
                            "Các phiên bản hỗ trợ sẵn (${state.profile.packages.size} gói):",
                            "Supported regional builds (${state.profile.packages.size} packages):",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(state.profile.packages) { p ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    customPkg = p
                                    onSelectPackage(p)
                                    showCustomPkgDialog = false
                                },
                            ) {
                                Text(
                                    text = p,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (customPkg.isNotBlank()) onSelectPackage(customPkg.trim())
                    showCustomPkgDialog = false
                }) {
                    Text(tr("Chọn", "Select"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPkgDialog = false }) { Text(tr("Đóng", "Close")) }
            },
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
