package com.owledit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owledit.app.BuildConfig
import com.owledit.app.ui.MainUiState
import com.owledit.app.ui.components.AboutAndNotesCard
import com.owledit.app.ui.components.SectionTitle
import com.owledit.app.ui.components.StatusPill
import com.owledit.app.ui.i18n.LocalIsEnglish
import com.owledit.app.ui.i18n.localizedLabel
import com.owledit.app.ui.i18n.tr
import com.owledit.app.ui.theme.*

@Composable
fun ToolsSettingsScreen(
    state: MainUiState,
    onSetLanguage: (String) -> Unit,
    onRunSanitizer: (removeLocalDeviceId: Boolean) -> Unit,
    onInspectSsaid: () -> Unit,
    onSsaidInputChange: (String) -> Unit,
    onRandomizeSsaid: () -> Unit,
    onApplySsaid: () -> Unit,
    onRestoreOriginalSsaid: () -> Unit,
    onSoftReboot: () -> Unit,
    onImportExternalProfile: () -> Unit,
    onResetExternalProfile: () -> Unit,
    onSetAutoForceStop: (Boolean) -> Unit,
    onSetAutoBackup: (Boolean) -> Unit,
    onSetShowDiff: (Boolean) -> Unit,
    onRefreshEnv: () -> Unit,
) {
    val isEn = LocalIsEnglish.current
    var removeDeviceId by remember { mutableStateOf(true) }
    var confirmSanitize by remember { mutableStateOf(false) }
    var confirmSsaidApply by remember { mutableStateOf(false) }
    var confirmSoftReboot by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 0. Language Selector Card (VI / EN)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, OwlSapphire.copy(alpha = 0.6f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = OwlSapphire)
                        SectionTitle(
                            title = tr("Ngôn Ngữ / Language", "Language / Ngôn Ngữ"),
                            subtitle = tr(
                                "Chuyển đổi tức thì giữa Tiếng Việt và Tiếng Anh không cần khởi động lại app.",
                                "Instantly switch between Vietnamese and English without restarting the app.",
                            ),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = !isEn,
                            onClick = { onSetLanguage("vi") },
                            label = {
                                Text(
                                    text = "Tiếng Việt (VI)",
                                    fontWeight = if (!isEn) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            leadingIcon = if (!isEn) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = isEn,
                            onClick = { onSetLanguage("en") },
                            label = {
                                Text(
                                    text = "English (EN)",
                                    fontWeight = if (isEn) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                            leadingIcon = if (isEn) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
            }
        }

        // 1. Account Sanitizer
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionTitle(
                        title = tr("Dọn Tài Khoản & Liên Kết Đám Mây (Account Sanitizer)", "Account & Cloud Session Sanitizer"),
                        subtitle = tr(
                            "Gỡ bỏ an toàn SdkStateCache#2, UsedAccounts#2, emptyLocalCloudSaveId, LastLoginUid, FrequentCheckCache_LoginTag mà vẫn giữ nguyên tiến trình chơi.",
                            "Safely removes SdkStateCache#2, UsedAccounts#2, emptyLocalCloudSaveId, LastLoginUid, FrequentCheckCache_LoginTag while preserving all game progress.",
                        ),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = removeDeviceId, onCheckedChange = { removeDeviceId = it })
                        Text(
                            text = tr(
                                "Xóa luôn khóa mã hóa LocalDeviceId (tạo định danh thiết bị mới khi vào lại game)",
                                "Also clear encrypted LocalDeviceId (generates a fresh device ID on next launch)",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!state.lastSanitizeSummary.isNullOrBlank()) {
                        Surface(
                            color = OwlEmeraldContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = state.lastSanitizeSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = OwlEmerald,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.env.rootGranted,
                        onClick = { confirmSanitize = true },
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Dọn Tài Khoản Cho ${state.settings.selectedPackage}", "Sanitize Account (${state.settings.selectedPackage})"))
                    }
                }
            }
        }

        // 2. SSAID Spoofer (Plain XML + Android 12+ ABX Binary)
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
                        title = "SSAID Spoofer (Android ID)",
                        subtitle = tr(
                            "Hỗ trợ tự động cả Plain XML (Android 8–11 / Giả lập) & ABX Binary (Android 12–15) bằng Rust thuần.",
                            "Auto-supports both Plain XML (Android 8–11 / Emulators) & ABX Binary (Android 12–15) in pure Rust.",
                        ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            enabled = state.env.rootGranted,
                            onClick = onInspectSsaid,
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Đọc SSAID Hiện Tại", "Read Current SSAID"))
                        }
                        if (state.ssaidHasOriginalBackup) {
                            OutlinedButton(
                                enabled = state.env.rootGranted,
                                onClick = onRestoreOriginalSsaid,
                            ) {
                                Text(tr("Khôi Phục Gốc", "Restore Original"))
                            }
                        }
                    }

                    state.ssaidInspection?.let { insp ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(insp.format, OwlSapphireContainer, OwlSapphire)
                                    StatusPill(
                                        tr("${insp.entries.size}/${insp.totalEntries} mục", "${insp.entries.size}/${insp.totalEntries} entries"),
                                        OwlSurface,
                                        OwlTextPrimary,
                                    )
                                }
                                if (insp.entries.isEmpty()) {
                                    Text(
                                        text = tr(
                                            "Chưa tìm thấy mục SSAID cho ${state.settings.selectedPackage}. Hãy mở game 1 lần trước.",
                                            "No SSAID entry found for ${state.settings.selectedPackage}. Launch the game once first.",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OwlSoulGold,
                                    )
                                } else {
                                    insp.entries.forEach { e ->
                                        Text(
                                            text = "${e.packageName}: ${e.value}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = OwlEmerald,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.ssaidInput,
                            onValueChange = onSsaidInputChange,
                            label = { Text(tr("SSAID mới (16 ký tự hex)", "New SSAID (16 hex chars)")) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = onRandomizeSsaid) {
                            Icon(Icons.Default.Casino, contentDescription = tr("Ngẫu nhiên", "Randomize"))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1.2f),
                            enabled = state.env.rootGranted && state.ssaidInput.length == 16,
                            onClick = { confirmSsaidApply = true },
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Ghi SSAID Mới", "Write New SSAID"))
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(0.9f),
                            enabled = state.env.rootGranted,
                            onClick = { confirmSoftReboot = true },
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Soft Reboot")
                        }
                    }

                    if (state.env.emulatorType.isEmulator) {
                        Text(
                            text = tr(
                                "Lưu ý trên ${state.env.emulatorType.localizedLabel(false)}: Một số giả lập lưu cache SSAID ở tầng máy chủ giả lập; sau khi đổi SSAID nên khởi động lại hoàn toàn cửa sổ giả lập nếu Soft Reboot chưa đổi ngay.",
                                "Note on ${state.env.emulatorType.localizedLabel(true)}: Some emulators cache SSAID at the host level; fully restart the emulator instance if Soft Reboot doesn't immediately apply it.",
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = OwlSoulGold,
                        )
                    }
                }
            }
        }

        // 3. External Profile / Preset Hot-Import
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SectionTitle(
                        title = tr("Cập Nhật Profile / Preset / Catalog Ngoài", "Hot-Import External Profile / Presets"),
                        subtitle = tr(
                            "Nạp nhanh file JSON hoặc ZIP khi game ra phiên bản mới mà không cần biên dịch lại APK.",
                            "Load external JSON or ZIP definitions when new game updates drop without rebuilding the APK.",
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatusPill(
                            text = "Profile: ${state.profile.id}",
                            containerColor = OwlSapphireContainer,
                            contentColor = OwlSapphire,
                        )
                        StatusPill(
                            text = if (state.hasProfileOverrides) {
                                tr("Đang dùng cấu hình ngoài", "External Override Active")
                            } else {
                                tr("Mặc định APK (v${state.presets.version})", "Built-in APK (v${state.presets.version})")
                            },
                            containerColor = if (state.hasProfileOverrides) OwlSoulGoldContainer else OwlEmeraldContainer,
                            contentColor = if (state.hasProfileOverrides) OwlSoulGold else OwlEmerald,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            modifier = Modifier.weight(1f),
                            onClick = onImportExternalProfile,
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("Nhập JSON / ZIP", "Import JSON / ZIP"))
                        }
                        if (state.hasProfileOverrides) {
                            OutlinedButton(onClick = onResetExternalProfile) {
                                Text(tr("Về mặc định", "Reset Default"))
                            }
                        }
                    }
                }
            }
        }

        // 4. Safety & Behavior Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SectionTitle(title = tr("Cài Đặt An Toàn Khi Ghi Save", "Save Safety Settings"))
                    SettingSwitchRow(
                        title = tr("Tự động Force-Stop game trước khi ghi", "Auto Force-Stop game before writing"),
                        subtitle = tr(
                            "Tránh Unity ghi đè lại file XML cũ khi game vẫn chạy ngầm.",
                            "Prevents Unity from overwriting modified XML while running in background.",
                        ),
                        checked = state.settings.autoForceStop,
                        onCheckedChange = onSetAutoForceStop,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    SettingSwitchRow(
                        title = tr("Tự động tạo ảnh chụp Auto-Rollback", "Auto-create Rollback snapshot"),
                        subtitle = tr(
                            "Sao lưu bản gốc vào bộ nhớ riêng trước mỗi lần ghi nguyên tử.",
                            "Snapshots the original save into private storage before every atomic write.",
                        ),
                        checked = state.settings.autoBackupBeforeSave,
                        onCheckedChange = onSetAutoBackup,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    SettingSwitchRow(
                        title = tr("Hiển thị hộp thoại Diff trước khi lưu", "Show Diff preview before saving"),
                        subtitle = tr(
                            "Xem rõ từng khóa thay đổi và cảnh báo Safe-Max trước khi xác nhận.",
                            "Review every modified key and Safe-Max warnings prior to confirming writes.",
                        ),
                        checked = state.settings.showDiffBeforeSave,
                        onCheckedChange = onSetShowDiff,
                    )
                }
            }
        }

        // 5. Diagnostics Summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tr("Chẩn Đoán Hệ Thống", "System Diagnostics"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(onClick = onRefreshEnv) { Text(tr("Quét lại", "Refresh")) }
                    }
                    Text(
                        tr(
                            "• Môi trường: ${state.env.emulatorType.localizedLabel(false)}",
                            "• Environment: ${state.env.emulatorType.localizedLabel(true)}",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "• Root: ${state.env.suVariant.localizedLabel(isEn)} (granted=${state.env.rootGranted})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        tr(
                            "• Hệ điều hành: ${state.env.androidVersion} • ABI: ${state.env.abi}",
                            "• OS: ${state.env.androidVersion} • ABI: ${state.env.abi}",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text("• SELinux: ${state.env.selinuxMode}", style = MaterialTheme.typography.bodySmall)
                    Text("• Rust Core: ${state.env.coreBanner}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    Text("• Root Helper: ${state.env.helperBanner}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }

        // 6. Notes & Author Credit
        item {
            AboutAndNotesCard(versionName = BuildConfig.VERSION_NAME)
        }
    }

    if (confirmSanitize) {
        AlertDialog(
            onDismissRequest = { confirmSanitize = false },
            title = { Text(tr("Xác Nhận Dọn Tài Khoản", "Confirm Account Sanitize")) },
            text = {
                Text(
                    tr(
                        "Thao tác sẽ dừng game, tự tạo bản Auto-Rollback và xóa các khóa đăng nhập / CloudSaveId trong ${state.settings.selectedPackage}. Tiến trình tướng, tiền tệ và vật phẩm vẫn được giữ nguyên.",
                        "This will force-stop the game, create an Auto-Rollback snapshot, and remove login / CloudSaveId keys in ${state.settings.selectedPackage}. Your heroes, currencies, and items remain intact.",
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmSanitize = false
                    onRunSanitizer(removeDeviceId)
                }) { Text(tr("Thực Hiện", "Proceed")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSanitize = false }) { Text(tr("Hủy", "Cancel")) }
            },
        )
    }

    if (confirmSsaidApply) {
        AlertDialog(
            onDismissRequest = { confirmSsaidApply = false },
            title = { Text(tr("Ghi SSAID Mới Vào Hệ Thống?", "Write New System SSAID?")) },
            text = {
                Text(
                    tr(
                        "Đổi SSAID của ${state.settings.selectedPackage} sang '${state.ssaidInput}' trong /data/system/users/0/settings_ssaid.xml.\n\nBản gốc đã được sao lưu tự động trước khi sửa.",
                        "Change SSAID of ${state.settings.selectedPackage} to '${state.ssaidInput}' in /data/system/users/0/settings_ssaid.xml.\n\nThe original file is automatically backed up first.",
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmSsaidApply = false
                    onApplySsaid()
                }) { Text(tr("Xác Nhận Ghi", "Confirm Write")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSsaidApply = false }) { Text(tr("Hủy", "Cancel")) }
            },
        )
    }

    if (confirmSoftReboot) {
        AlertDialog(
            onDismissRequest = { confirmSoftReboot = false },
            title = { Text(tr("Khởi Động Lại Mềm (Soft Reboot)?", "Trigger Soft Reboot?")) },
            text = {
                Text(
                    tr(
                        "Khởi động lại tiến trình Android (zygote) để giá trị SSAID mới có hiệu lực ngay lập tức.",
                        "Restarts the Android framework (zygote) so the updated SSAID takes effect immediately.",
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    confirmSoftReboot = false
                    onSoftReboot()
                }) { Text("Soft Reboot") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSoftReboot = false }) { Text(tr("Hủy", "Cancel")) }
            },
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
