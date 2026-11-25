package com.owledit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.owledit.app.ui.MainViewModel
import com.owledit.app.ui.SaveSource
import com.owledit.app.ui.components.AboutAndNotesDialog
import com.owledit.app.ui.components.DiffPreviewDialog
import com.owledit.app.ui.components.EntryInspectorDialog
import com.owledit.app.ui.i18n.LocalIsEnglish
import com.owledit.app.ui.i18n.tr
import com.owledit.app.ui.screens.*
import com.owledit.app.ui.theme.OwlEditorTheme
import com.owledit.app.ui.theme.OwlRuby
import com.owledit.app.ui.theme.OwlSapphire
import com.owledit.app.ui.theme.OwlSapphireContainer

private enum class MainTab(val labelVi: String, val labelEn: String, val icon: ImageVector) {
    HOME("Trang chủ", "Home", Icons.Default.Home),
    VISUAL("Chỉnh nhanh", "Quick Edit", Icons.Default.Tune),
    EXPLORER("Explorer", "Explorer", Icons.Default.Code),
    BACKUP("Sao lưu", "Backup", Icons.Default.Backup),
    TOOLS("Công cụ", "Tools", Icons.Default.Build),
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OwlEditorTheme {
                OwlEditorRootScaffold(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwlEditorRootScaffold(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeTab by remember { mutableStateOf(MainTab.HOME) }
    var pendingExportBackupPath by remember { mutableStateOf<String?>(null) }
    var showAboutNotesDialog by remember { mutableStateOf(false) }
    val isEn = state.settings.language == "en"

    // SAF Launchers
    val openXmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.openLocalXmlUri(context, uri)
    }

    val exportXmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/xml")
    ) { uri ->
        if (uri != null) viewModel.exportCommittedSessionToUri(context, uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackupFromUri(context, uri)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val src = pendingExportBackupPath
        pendingExportBackupPath = null
        if (uri != null && src != null) {
            viewModel.exportBackupToUri(context, src, uri)
        }
    }

    val importProfileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importExternalProfileUri(context, uri)
    }

    // Trigger SAF export when saving in Local XML mode
    LaunchedEffect(state.pendingExportLocalXml) {
        if (state.pendingExportLocalXml) {
            viewModel.consumePendingExportLocalXml()
            val suggested = when (val src = state.saveSource) {
                is SaveSource.LocalXmlFile -> "edited_${src.displayName}"
                else -> "${state.settings.selectedPackage}.v2.playerprefs.xml"
            }
            exportXmlLauncher.launch(suggested)
        }
    }

    // Toast messages
    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        viewModel.consumeToast()
    }

    CompositionLocalProvider(LocalIsEnglish provides isEn) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "OWLVNEditor",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    text = "Otherworld Legends • Rust Core v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            // About & Notes Dialog trigger
                            IconButton(
                                onClick = { showAboutNotesDialog = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = tr("Ghi chú & Tác giả", "Notes & About"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // 1-Tap VI / EN Language Switcher
                            OutlinedButton(
                                onClick = { viewModel.toggleLanguage() },
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, OwlSapphire.copy(alpha = 0.65f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = OwlSapphireContainer.copy(alpha = 0.45f),
                                    contentColor = OwlSapphire,
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .padding(end = 6.dp),
                            ) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = tr("Đổi ngôn ngữ", "Switch language"),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isEn) "EN" else "VI",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            if (state.hasActiveSession && state.modifiedCount > 0) {
                                FilledTonalButton(
                                    onClick = { viewModel.requestSaveFlow() },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = OwlRuby,
                                        contentColor = Color.White,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.padding(end = 8.dp),
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        tr("Lưu (${state.modifiedCount})", "Save (${state.modifiedCount})"),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        MainTab.entries.forEach { tab ->
                            val label = if (isEn) tab.labelEn else tab.labelVi
                            NavigationBarItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = { Icon(tab.icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) },
                            )
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    // Global Error Banner
                    state.errorBanner?.let { err ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.dismissError() }) {
                                    Icon(Icons.Default.Close, contentDescription = tr("Đóng", "Close"))
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            MainTab.HOME -> HomeScreen(
                                state = state,
                                onRefreshEnv = viewModel::refreshEnvironment,
                                onSelectPackage = viewModel::setSelectedPackage,
                                onOpenSaveRoot = viewModel::openGameSaveRoot,
                                onOpenLocalXml = { openXmlLauncher.launch(arrayOf("*/*")) },
                                onQuickBackup = {
                                    viewModel.createBackupNow(
                                        if (isEn) "Quick backup from Home" else "Sao lưu nhanh từ Trang chủ"
                                    )
                                },
                                onLaunchGame = viewModel::launchGame,
                                onRequestSave = viewModel::requestSaveFlow,
                                onGoVisualEditor = { activeTab = MainTab.VISUAL },
                            )
                            MainTab.VISUAL -> VisualEditorScreen(
                                state = state,
                                onOpenRoot = { viewModel.openGameSaveRoot() },
                                onOpenLocalXml = { openXmlLauncher.launch(arrayOf("*/*")) },
                                onApplyPreset = viewModel::applyQuickPreset,
                                onSetCurrency = viewModel::setCurrencyValue,
                                onUpdateHero = viewModel::updateHero,
                                onSetAsuraStat = viewModel::setAsuraStat,
                                onSetMenuSkin = viewModel::setMenuSkinUnlocked,
                                onInspectContainer = viewModel::inspectKey,
                                onSetQuickResource = viewModel::setQuickResourceValue,
                                onSetDifficultyUnlocked = viewModel::setDifficultyUnlocked,
                                onSetFeatureFlag = viewModel::setFeatureFlag,
                            )
                            MainTab.EXPLORER -> ExplorerScreen(
                                state = state,
                                onOpenRoot = { viewModel.openGameSaveRoot() },
                                onOpenLocalXml = { openXmlLauncher.launch(arrayOf("*/*")) },
                                onQueryChange = viewModel::updateExplorerQuery,
                                onInspectKey = viewModel::inspectKey,
                                onAddRawKey = viewModel::saveRawEntryFromInspector,
                            )
                            MainTab.BACKUP -> BackupScreen(
                                state = state,
                                onCreateBackup = viewModel::createBackupNow,
                                onRefreshBackups = viewModel::refreshBackupsAndRollbacks,
                                onImportBackup = { importBackupLauncher.launch(arrayOf("*/*")) },
                                onExportBackup = { path, suggestedName ->
                                    pendingExportBackupPath = path
                                    exportBackupLauncher.launch(suggestedName)
                                },
                                onRestoreBackup = viewModel::restoreBackupToGame,
                                onDeleteBackup = viewModel::deleteBackupFile,
                                onRestoreRollback = viewModel::restoreRollbackSnapshot,
                            )
                            MainTab.TOOLS -> ToolsSettingsScreen(
                                state = state,
                                onSetLanguage = viewModel::setLanguage,
                                onRunSanitizer = viewModel::runAccountSanitizer,
                                onInspectSsaid = viewModel::inspectSystemSsaid,
                                onSsaidInputChange = viewModel::updateSsaidInput,
                                onRandomizeSsaid = viewModel::randomizeSsaidInput,
                                onApplySsaid = viewModel::applySsaidSpoof,
                                onRestoreOriginalSsaid = viewModel::restoreOriginalSsaid,
                                onSoftReboot = viewModel::triggerSoftReboot,
                                onImportExternalProfile = { importProfileLauncher.launch(arrayOf("*/*")) },
                                onResetExternalProfile = viewModel::clearExternalProfileOverrides,
                                onSetAutoForceStop = viewModel::setAutoForceStop,
                                onSetAutoBackup = viewModel::setAutoBackupBeforeSave,
                                onSetShowDiff = viewModel::setShowDiffBeforeSave,
                                onRefreshEnv = viewModel::refreshEnvironment,
                            )
                        }
                    }
                }
            }

            // Diff Dialog
            if (state.showDiffDialog) {
                DiffPreviewDialog(
                    diffs = state.pendingDiffs,
                    isRootMode = state.saveSource is SaveSource.RootPackage,
                    onDismiss = viewModel::dismissDiffDialog,
                    onConfirm = viewModel::confirmCommitSave,
                )
            }

            // About & Notes Dialog
            if (showAboutNotesDialog) {
                AboutAndNotesDialog(
                    versionName = BuildConfig.VERSION_NAME,
                    onDismiss = { showAboutNotesDialog = false },
                )
            }

            // Entry / NRBF Inspector Dialog
            state.inspectingEntry?.let { entry ->
                EntryInspectorDialog(
                    entry = entry,
                    onDismiss = viewModel::closeInspector,
                    onSaveRaw = viewModel::saveRawEntryFromInspector,
                    onPatchNrbf = viewModel::patchNrbfEntryFromInspector,
                    onSetAllNrbf = viewModel::setAllNrbfInInspector,
                    onBeautifyJson = viewModel::beautifyJson,
                    onMinifyJson = viewModel::minifyJson,
                )
            }

            // Busy Overlay
            if (state.isBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text(
                                text = state.busyMessage.ifBlank { tr("Đang xử lý...", "Processing...") },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
