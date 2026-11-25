package com.owledit.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owledit.app.OwlEditorApplication
import com.owledit.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class SaveSource {
    data object None : SaveSource()
    data class RootPackage(val packageName: String) : SaveSource()
    data class LocalXmlFile(val displayName: String, val stagedPath: String) : SaveSource()
}

data class MainUiState(
    val isBusy: Boolean = false,
    val busyMessage: String = "",
    val toastMessage: String? = null,
    val errorBanner: String? = null,

    val profile: OwlProfile = OwlProfile(),
    val presets: OwlPresetsFile = OwlPresetsFile(),
    val catalog: OwlCatalog = OwlCatalog(),
    val hasProfileOverrides: Boolean = false,

    val settings: AppSettings = AppSettings(),
    val env: RootEnvironmentInfo = RootEnvironmentInfo(),

    val sessionId: Long = 0L,
    val saveSource: SaveSource = SaveSource.None,
    val lastOpenMs: Long = 0L,
    val dashboard: DashboardSummary? = null,

    // Explorer state
    val explorerSearch: String = "",
    val explorerCategory: String = "all",
    val explorerOnlyModified: Boolean = false,
    val explorerPage: ExplorerPage = ExplorerPage(),
    val inspectingEntry: EntryInspection? = null,

    // Diff dialog state
    val showDiffDialog: Boolean = false,
    val pendingDiffs: List<DiffEntry> = emptyList(),
    val pendingExportLocalXml: Boolean = false,

    // Backups & Rollbacks
    val backupDirPath: String = "",
    val backups: List<BackupInspectResult> = emptyList(),
    val rollbacks: List<RollbackRecord> = emptyList(),

    // Tools: SSAID & Sanitizer
    val ssaidInspection: SsaidInspection? = null,
    val ssaidInput: String = "",
    val ssaidHasOriginalBackup: Boolean = false,
    val lastSanitizeSummary: String? = null,
) {
    val hasActiveSession: Boolean get() = sessionId > 0L && dashboard != null
    val modifiedCount: Int get() = dashboard?.modifiedCount ?: 0
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OwlEditorApplication
    private val rustCore = app.rustCore
    private val rootBridge = app.rootBridge
    private val profileRepo = app.profileRepo
    private val settingsStore = app.settingsStore

    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    init {
        loadAssetsAndSettings()
        refreshEnvironment()
        refreshBackupsAndRollbacks()
    }

    override fun onCleared() {
        val sid = _ui.value.sessionId
        if (sid > 0L) rustCore.closeSession(sid)
        super.onCleared()
    }

    fun consumeToast() {
        _ui.update { it.copy(toastMessage = null) }
    }

    fun dismissError() {
        _ui.update { it.copy(errorBanner = null) }
    }

    private fun postToast(msg: String) {
        _ui.update { it.copy(toastMessage = msg) }
    }

    private fun postError(msg: String) {
        _ui.update { it.copy(errorBanner = msg) }
    }

    private fun loadAssetsAndSettings() {
        val profile = profileRepo.loadProfile()
        val presets = profileRepo.loadPresets()
        val catalog = profileRepo.loadCatalog()
        _ui.update {
            it.copy(
                profile = profile,
                presets = presets,
                catalog = catalog,
                hasProfileOverrides = profileRepo.hasOverrides(),
                backupDirPath = rootBridge.defaultBackupDir().absolutePath,
                ssaidHasOriginalBackup = rootBridge.ssaidBackupFile().exists(),
            )
        }
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { s ->
                _ui.update { it.copy(settings = s) }
            }
        }
    }

    fun refreshEnvironment() {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang kiểm tra Root, Giả lập & gói Otherworld Legends...",
                    "Checking Root, Emulator & Otherworld Legends packages...",
                )
            )
            try {
                val pkgs = _ui.value.profile.packages.ifEmpty { listOf("com.chillyroom.zhmr.gp") }
                val banner = rustCore.coreBanner()
                val info = rootBridge.probeEnvironment(pkgs, banner)
                _ui.update { state ->
                    val autoPkg = info.installedPackages.firstOrNull { it.saveExists }?.packageName
                        ?: info.installedPackages.firstOrNull()?.packageName
                    if (autoPkg != null && info.installedPackages.none { it.packageName == state.settings.selectedPackage }) {
                        viewModelScope.launch { settingsStore.setSelectedPackage(autoPkg) }
                    }
                    state.copy(
                        env = info,
                        ssaidHasOriginalBackup = rootBridge.ssaidBackupFile().exists(),
                    )
                }
            } catch (t: Throwable) {
                postError(t("Lỗi kiểm tra môi trường: ${t.message}", "Environment check failed: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    // ==================== Settings ====================

    fun setAutoForceStop(enabled: Boolean) = viewModelScope.launch { settingsStore.setAutoForceStop(enabled) }
    fun setAutoBackupBeforeSave(enabled: Boolean) = viewModelScope.launch { settingsStore.setAutoBackupBeforeSave(enabled) }
    fun setShowDiffBeforeSave(enabled: Boolean) = viewModelScope.launch { settingsStore.setShowDiffBeforeSave(enabled) }
    fun setSelectedPackage(pkg: String) = viewModelScope.launch { settingsStore.setSelectedPackage(pkg) }
    fun setLanguage(lang: String) = viewModelScope.launch {
        settingsStore.setLanguage(lang)
        postToast(if (lang == "en") "Language switched to English" else "Đã chuyển ngôn ngữ sang Tiếng Việt")
    }

    fun toggleLanguage() {
        val next = if (_ui.value.settings.language == "en") "vi" else "en"
        setLanguage(next)
    }

    private val isEn: Boolean get() = _ui.value.settings.language == "en"
    private fun t(vi: String, en: String): String = if (isEn) en else vi

    // ==================== Open Save (Root or Local XML) ====================

    fun openGameSaveRoot(packageName: String = _ui.value.settings.selectedPackage) {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang đọc & giải mã save $packageName bằng lõi Rust...",
                    "Reading & decrypting $packageName save with Rust Core...",
                )
            )
            try {
                if (!_ui.value.env.rootGranted) {
                    postError(
                        t(
                            "Thiết bị chưa cấp quyền Root. Hãy cấp quyền Root hoặc dùng nút 'Mở file XML (Offline)'.",
                            "Root access is not granted. Please grant Root permission or use 'Open XML File (Offline)'.",
                        )
                    )
                    return@launch
                }
                val t0 = System.nanoTime()
                val stagedFile = rootBridge.pullGameSaveToStaging(packageName).getOrThrow()
                val extraDict = withContext(Dispatchers.IO) { profileRepo.loadExtraDictionaryJson() }
                val crypto = _ui.value.profile.crypto

                val oldSid = _ui.value.sessionId
                val opened = withContext(Dispatchers.IO) {
                    rustCore.openSessionFromFile(
                        xmlPath = stagedFile.absolutePath,
                        keyHex = crypto.keyHex,
                        ivHex = crypto.ivHex,
                        extraDictJson = extraDict,
                    )
                }
                if (oldSid > 0L) rustCore.closeSession(oldSid)

                val page = withContext(Dispatchers.IO) {
                    rustCore.queryEntries(opened.sessionId, "", "all", false, 0, 80)
                }
                val elapsedMs = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)

                _ui.update {
                    it.copy(
                        sessionId = opened.sessionId,
                        saveSource = SaveSource.RootPackage(packageName),
                        lastOpenMs = elapsedMs,
                        dashboard = opened.dashboard,
                        explorerSearch = "",
                        explorerCategory = "all",
                        explorerOnlyModified = false,
                        explorerPage = page,
                        inspectingEntry = null,
                        errorBanner = null,
                    )
                }
                postToast(
                    t(
                        "Đã mở save ($packageName): ${opened.dashboard.totalEntries} khóa trong ${elapsedMs}ms",
                        "Opened save ($packageName): ${opened.dashboard.totalEntries} keys in ${elapsedMs}ms",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Không thể mở save game: ${t.message}", "Failed to open game save: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun openLocalXmlUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            setBusy(t("Đang nạp & giải mã file XML...", "Loading & decrypting XML file..."))
            try {
                val t0 = System.nanoTime()
                val displayName = resolveDisplayName(context, uri) ?: "playerprefs.xml"
                val stagedFile = withContext(Dispatchers.IO) {
                    val out = File(rootBridge.stagingDir(), "local_import_${System.currentTimeMillis()}.xml")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException(
                        t("Không thể đọc nội dung tệp đã chọn", "Unable to read selected file")
                    )
                    out
                }
                val extraDict = withContext(Dispatchers.IO) { profileRepo.loadExtraDictionaryJson() }
                val crypto = _ui.value.profile.crypto

                val oldSid = _ui.value.sessionId
                val opened = withContext(Dispatchers.IO) {
                    rustCore.openSessionFromFile(
                        xmlPath = stagedFile.absolutePath,
                        keyHex = crypto.keyHex,
                        ivHex = crypto.ivHex,
                        extraDictJson = extraDict,
                    )
                }
                if (oldSid > 0L) rustCore.closeSession(oldSid)

                val page = withContext(Dispatchers.IO) {
                    rustCore.queryEntries(opened.sessionId, "", "all", false, 0, 80)
                }
                val elapsedMs = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)

                _ui.update {
                    it.copy(
                        sessionId = opened.sessionId,
                        saveSource = SaveSource.LocalXmlFile(displayName, stagedFile.absolutePath),
                        lastOpenMs = elapsedMs,
                        dashboard = opened.dashboard,
                        explorerSearch = "",
                        explorerCategory = "all",
                        explorerOnlyModified = false,
                        explorerPage = page,
                        inspectingEntry = null,
                        errorBanner = null,
                    )
                }
                postToast(
                    t(
                        "Đã mở $displayName (${opened.dashboard.totalEntries} khóa) trong ${elapsedMs}ms",
                        "Opened $displayName (${opened.dashboard.totalEntries} keys) in ${elapsedMs}ms",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Lỗi mở file XML: ${t.message}", "Failed to open XML file: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    // ==================== Visual Editor Mutations ====================

    fun setCurrencyValue(key: String, newValue: Int) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rustCore.setEncryptInt(sid, key, newValue.coerceAtLeast(0))
                }
                refreshSessionViews()
            } catch (t: Throwable) {
                postError(t("Lỗi cập nhật $key: ${t.message}", "Failed to update $key: ${t.message}"))
            }
        }
    }

    fun updateHero(
        bodyName: String,
        unlocked: Boolean? = null,
        level: Int? = null,
        secondSkill: Boolean? = null,
        unlockAllSkills: Boolean = false,
        unlockAllStyles: Boolean = false,
    ) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rustCore.updateHero(
                        sessionId = sid,
                        bodyName = bodyName,
                        unlocked = unlocked,
                        level = level,
                        secondSkill = secondSkill,
                        unlockAllSkills = unlockAllSkills,
                        unlockAllStyles = unlockAllStyles,
                    )
                }
                refreshSessionViews()
                if (unlockAllSkills && unlockAllStyles) {
                    postToast(
                        t(
                            "Đã mở full Kỹ năng & Phong cách cho $bodyName",
                            "Unlocked all Skills & Styles for $bodyName",
                        )
                    )
                }
            } catch (t: Throwable) {
                postError(t("Lỗi cập nhật tướng $bodyName: ${t.message}", "Failed to update hero $bodyName: ${t.message}"))
            }
        }
    }

    fun setAsuraStat(statId: Int, value: Int) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rustCore.patchNrbfEntry(
                        sessionId = sid,
                        containerKey = "EXAsuraPowerPointInvest",
                        entryKey = statId.toString(),
                        entryValue = value.coerceAtLeast(0).toString(),
                        defaultKind = NrbfDictKindDto(kind = "int_encrypt_value"),
                    )
                }
                refreshSessionViews()
            } catch (t: Throwable) {
                postError(t("Lỗi cập nhật Tu La: ${t.message}", "Failed to update Asura stat: ${t.message}"))
            }
        }
    }

    fun setMenuSkinUnlocked(id: Int, unlocked: Boolean) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rustCore.patchNrbfEntry(
                        sessionId = sid,
                        containerKey = "MenuSkinUnlock",
                        entryKey = id.toString(),
                        entryValue = if (unlocked) "1" else "0",
                        defaultKind = NrbfDictKindDto(kind = "enum_encrypt_value", keyEnum = "MenuSkinType"),
                    )
                }
                refreshSessionViews()
            } catch (t: Throwable) {
                postError(t("Lỗi cập nhật giao diện sảnh: ${t.message}", "Failed to update lobby skin: ${t.message}"))
            }
        }
    }

    fun setQuickResourceValue(containerKey: String, entryKey: String, value: Int) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rustCore.patchNrbfEntry(
                        sessionId = sid,
                        containerKey = containerKey,
                        entryKey = entryKey,
                        entryValue = value.coerceAtLeast(0).toString(),
                        defaultKind = NrbfDictKindDto(kind = "string_encrypt_value"),
                    )
                }
                refreshSessionViews()
                postToast(t("Đã cập nhật $entryKey = $value", "Updated $entryKey = $value"))
            } catch (t: Throwable) {
                postError(t("Lỗi cập nhật $containerKey[$entryKey]: ${t.message}", "Failed to update $containerKey[$entryKey]: ${t.message}"))
            }
        }
    }

    fun setDifficultyUnlocked(level: Int) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        val clamped = level.coerceIn(0, 14)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rustCore.setRawEntry(sid, "difficultLevel", clamped.toString(), true)
                    rustCore.setRawEntry(sid, "difficultLevelUnlockLevel", clamped.toString(), true)
                }
                refreshSessionViews()
                postToast(
                    t(
                        "Đã đặt cấp độ khó mở khóa = $clamped/14",
                        "Unlocked difficulty level set to $clamped/14",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Lỗi đặt cấp độ khó: ${t.message}", "Failed to set difficulty level: ${t.message}"))
            }
        }
    }

    fun setFeatureFlag(containerKey: String?, key: String, enabled: Boolean) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        val v = if (enabled) "1" else "0"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (containerKey == null) {
                        rustCore.setRawEntry(sid, key, v, true)
                    } else {
                        rustCore.patchNrbfEntry(
                            sessionId = sid,
                            containerKey = containerKey,
                            entryKey = key,
                            entryValue = v,
                            defaultKind = NrbfDictKindDto(kind = "string_encrypt_value"),
                        )
                    }
                }
                refreshSessionViews()
            } catch (t: Throwable) {
                postError(t("Lỗi bật/tắt $key: ${t.message}", "Failed to toggle $key: ${t.message}"))
            }
        }
    }

    fun applyQuickPreset(presetId: String) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) {
            postError(
                t(
                    "Hãy mở một file save trước khi áp dụng Preset.",
                    "Please open a save file before applying a preset.",
                )
            )
            return
        }
        viewModelScope.launch {
            setBusy(t("Đang áp dụng Preset '$presetId'...", "Applying preset '$presetId'..."))
            try {
                val presets = _ui.value.presets
                val out = withContext(Dispatchers.IO) {
                    rustCore.applyPreset(
                        sessionId = sid,
                        presetId = presetId,
                        extraSkins = presets.heroSkinsAll,
                        extraItems = presets.itemsAll,
                    )
                }
                refreshSessionViews()
                val msg = if (isEn) {
                    val presetDef = presets.quickPresets.firstOrNull { it.id == presetId }
                    val label = presetDef?.titleEn?.ifBlank { presetDef.title } ?: presetId
                    "Applied '$label' (${out.modifiedCount} total modified keys)"
                } else {
                    out.message
                }
                postToast(msg)
            } catch (t: Throwable) {
                postError(t("Lỗi áp dụng Preset: ${t.message}", "Failed to apply preset: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    // ==================== Explorer & Detail Inspector ====================

    fun updateExplorerQuery(
        search: String = _ui.value.explorerSearch,
        category: String = _ui.value.explorerCategory,
        onlyModified: Boolean = _ui.value.explorerOnlyModified,
        offset: Int = 0,
    ) {
        val sid = _ui.value.sessionId
        _ui.update {
            it.copy(
                explorerSearch = search,
                explorerCategory = category,
                explorerOnlyModified = onlyModified,
            )
        }
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    rustCore.queryEntries(sid, search, category, onlyModified, offset, 80)
                }
                _ui.update { it.copy(explorerPage = page) }
            } catch (t: Throwable) {
                postError(t("Lỗi tìm kiếm khóa: ${t.message}", "Key search error: ${t.message}"))
            }
        }
    }

    fun inspectKey(key: String) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { rustCore.inspectEntry(sid, key) }
                _ui.update { it.copy(inspectingEntry = info) }
            } catch (t: Throwable) {
                postError(
                    t(
                        "Không thể đọc chi tiết khóa '$key': ${t.message}",
                        "Unable to inspect key '$key': ${t.message}",
                    )
                )
            }
        }
    }

    fun closeInspector() {
        _ui.update { it.copy(inspectingEntry = null) }
    }

    fun saveRawEntryFromInspector(key: String, value: String, encrypt: Boolean) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rustCore.setRawEntry(sid, key, value, encrypt)
                }
                val refreshed = withContext(Dispatchers.IO) { rustCore.inspectEntry(sid, key) }
                _ui.update { it.copy(inspectingEntry = refreshed) }
                refreshSessionViews()
                postToast(
                    t(
                        "Đã cập nhật khóa '${refreshed.displayKey}' trong bộ nhớ phiên",
                        "Updated key '${refreshed.displayKey}' in current session",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Lỗi lưu khóa '$key': ${t.message}", "Failed to save key '$key': ${t.message}"))
            }
        }
    }

    fun patchNrbfEntryFromInspector(containerKey: String, entryKey: String, entryValue: String) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    rustCore.patchNrbfEntry(sid, containerKey, entryKey, entryValue, null)
                }
                val refreshed = withContext(Dispatchers.IO) { rustCore.inspectEntry(sid, containerKey) }
                _ui.update { it.copy(inspectingEntry = refreshed) }
                refreshSessionViews()
                postToast(
                    t(
                        "Đã cập nhật $containerKey[$entryKey] = $entryValue",
                        "Updated $containerKey[$entryKey] = $entryValue",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Lỗi sửa NRBF '$containerKey': ${t.message}", "Failed to patch NRBF '$containerKey': ${t.message}"))
            }
        }
    }

    fun setAllNrbfInInspector(containerKey: String, uniformValue: String) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                val changed = withContext(Dispatchers.IO) {
                    rustCore.setAllNrbfValues(sid, containerKey, uniformValue)
                }
                val refreshed = withContext(Dispatchers.IO) { rustCore.inspectEntry(sid, containerKey) }
                _ui.update { it.copy(inspectingEntry = refreshed) }
                refreshSessionViews()
                postToast(
                    t(
                        "Đã đặt $changed mục trong $containerKey = $uniformValue",
                        "Set $changed entries in $containerKey = $uniformValue",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Lỗi cập nhật hàng loạt '$containerKey': ${t.message}", "Batch update failed for '$containerKey': ${t.message}"))
            }
        }
    }

    fun beautifyJson(input: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { rustCore.beautifyJson(input) } }
                .onSuccess(onResult)
                .onFailure { postError(t("JSON không hợp lệ: ${it.message}", "Invalid JSON: ${it.message}")) }
        }
    }

    fun minifyJson(input: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { rustCore.minifyJson(input) } }
                .onSuccess(onResult)
                .onFailure { postError(t("JSON không hợp lệ: ${it.message}", "Invalid JSON: ${it.message}")) }
        }
    }

    // ==================== Diff Preview & Safe Atomic Commit ====================

    fun requestSaveFlow() {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            try {
                val diffs = withContext(Dispatchers.IO) { rustCore.computeDiff(sid) }
                if (diffs.isEmpty()) {
                    postToast(t("Chưa có thay đổi nào để lưu.", "No pending changes to save."))
                    return@launch
                }
                if (_ui.value.settings.showDiffBeforeSave) {
                    _ui.update { it.copy(showDiffDialog = true, pendingDiffs = diffs) }
                } else {
                    confirmCommitSave()
                }
            } catch (t: Throwable) {
                postError(t("Lỗi tính Diff: ${t.message}", "Diff computation failed: ${t.message}"))
            }
        }
    }

    fun dismissDiffDialog() {
        _ui.update { it.copy(showDiffDialog = false) }
    }

    fun confirmCommitSave() {
        val state = _ui.value
        val sid = state.sessionId
        if (sid <= 0L) return

        when (val src = state.saveSource) {
            is SaveSource.RootPackage -> {
                _ui.update { it.copy(showDiffDialog = false) }
                commitToRootGame(src.packageName)
            }
            is SaveSource.LocalXmlFile -> {
                _ui.update { it.copy(showDiffDialog = false, pendingExportLocalXml = true) }
            }
            SaveSource.None -> Unit
        }
    }

    fun consumePendingExportLocalXml() {
        _ui.update { it.copy(pendingExportLocalXml = false) }
    }

    fun exportCommittedSessionToUri(context: Context, outUri: Uri) {
        val sid = _ui.value.sessionId
        if (sid <= 0L) return
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang mã hóa, tự kiểm chứng và xuất file XML...",
                    "Encrypting, verifying & exporting XML file...",
                )
            )
            try {
                val stagedOut = File(rootBridge.stagingDir(), "committed_export_${System.currentTimeMillis()}.xml")
                val commitOut = withContext(Dispatchers.IO) {
                    rustCore.commitSessionToFile(sid, stagedOut.absolutePath)
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(outUri)?.use { os ->
                        stagedOut.inputStream().use { it.copyTo(os) }
                    } ?: throw IllegalStateException(
                        t("Không thể ghi vào tệp đích", "Unable to write to destination file")
                    )
                }
                postToast(
                    t(
                        "Đã xuất XML hợp lệ (${commitOut.modifiedEntries} thay đổi, SHA-256: ${commitOut.sha256.take(10)}…)",
                        "Exported verified XML (${commitOut.modifiedEntries} changes, SHA-256: ${commitOut.sha256.take(10)}…)",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Lỗi xuất file XML: ${t.message}", "Failed to export XML file: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    private fun commitToRootGame(packageName: String) {
        val sid = _ui.value.sessionId
        val settings = _ui.value.settings
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang kiểm chứng mã hóa & ghi nguyên tử (Atomic Write) vào $packageName...",
                    "Verifying encryption & atomic-writing to $packageName...",
                )
            )
            try {
                val stagedOut = File(rootBridge.stagingDir(), "commit_${packageName}.xml")
                val commitOut = withContext(Dispatchers.IO) {
                    rustCore.commitSessionToFile(sid, stagedOut.absolutePath)
                }

                val writeRes = rootBridge.pushStagedSaveAtomic(
                    packageName = packageName,
                    stagedFile = stagedOut,
                    forceStop = settings.autoForceStop,
                    createRollbackSnapshot = settings.autoBackupBeforeSave,
                ).getOrThrow()

                if (!writeRes.sha256.equals(commitOut.sha256, ignoreCase = true)) {
                    throw IllegalStateException(
                        t(
                            "Cảnh báo lệch SHA-256 sau khi ghi vào thiết bị!",
                            "SHA-256 mismatch detected after writing to device!",
                        )
                    )
                }

                val extraDict = withContext(Dispatchers.IO) { profileRepo.loadExtraDictionaryJson() }
                val crypto = _ui.value.profile.crypto
                val reopened = withContext(Dispatchers.IO) {
                    rustCore.openSessionFromFile(stagedOut.absolutePath, crypto.keyHex, crypto.ivHex, extraDict)
                }
                rustCore.closeSession(sid)
                val page = withContext(Dispatchers.IO) {
                    rustCore.queryEntries(
                        reopened.sessionId,
                        _ui.value.explorerSearch,
                        _ui.value.explorerCategory,
                        _ui.value.explorerOnlyModified,
                        0,
                        80,
                    )
                }
                _ui.update {
                    it.copy(
                        sessionId = reopened.sessionId,
                        dashboard = reopened.dashboard,
                        explorerPage = page,
                        inspectingEntry = null,
                    )
                }
                refreshBackupsAndRollbacks()
                postToast(
                    t(
                        "Đã lưu thành công ${commitOut.modifiedEntries} thay đổi! (mode=${writeRes.modeOctal}, uid=${writeRes.uid}, SHA-256 khớp 100%)",
                        "Saved ${commitOut.modifiedEntries} changes safely! (mode=${writeRes.modeOctal}, uid=${writeRes.uid}, SHA-256 verified)",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Lưu thất bại: ${t.message}", "Save failed: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun launchGame() {
        val pkg = _ui.value.settings.selectedPackage
        viewModelScope.launch {
            val res = rootBridge.launchGame(pkg)
            if (res.ok) {
                postToast(t("Đang mở Otherworld Legends ($pkg)...", "Launching Otherworld Legends ($pkg)..."))
            } else {
                postError(
                    t(
                        "Không thể mở game tự động: ${res.stderr.ifBlank { "kiểm tra tên gói" }}",
                        "Unable to launch game: ${res.stderr.ifBlank { "check package name" }}",
                    )
                )
            }
        }
    }

    // ==================== Backup & Restore (.owldb) ====================

    fun refreshBackupsAndRollbacks() {
        viewModelScope.launch {
            val dir = rootBridge.defaultBackupDir()
            val list = withContext(Dispatchers.IO) {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".owldb", ignoreCase = true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.mapNotNull { file ->
                        runCatching { rustCore.inspectBackup(file.absolutePath) }.getOrNull()
                    }
                    ?: emptyList()
            }
            val rbs = withContext(Dispatchers.IO) { rootBridge.listRollbacks() }
            _ui.update {
                it.copy(
                    backupDirPath = dir.absolutePath,
                    backups = list,
                    rollbacks = rbs,
                )
            }
        }
    }

    fun createBackupNow(note: String) {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang sao lưu dữ liệu save & tạo mã kiểm tra SHA-256 (.owldb)...",
                    "Creating .owldb save backup & SHA-256 manifest...",
                )
            )
            try {
                val pkg = _ui.value.settings.selectedPackage
                val tmpStage = File(rootBridge.stagingDir(), "bk_stage_${System.currentTimeMillis()}").apply { mkdirs() }
                try {
                    val prefsSub = File(tmpStage, "shared_prefs").apply { mkdirs() }
                    val targetXml = File(prefsSub, "$pkg.v2.playerprefs.xml")

                    if (_ui.value.env.rootGranted) {
                        val pulled = rootBridge.pullGameSaveToStaging(pkg).getOrThrow()
                        pulled.copyTo(targetXml, overwrite = true)
                    } else if (_ui.value.hasActiveSession) {
                        rustCore.commitSessionToFile(_ui.value.sessionId, targetXml.absolutePath)
                    } else {
                        throw IllegalStateException(
                            t(
                                "Cần quyền Root hoặc đang mở 1 file save để tạo backup.",
                                "Root permission or an active save session is required to create a backup.",
                            )
                        )
                    }

                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outArchive = File(rootBridge.defaultBackupDir(), "OWL_${pkg}_${ts}.owldb")
                    val uid = _ui.value.dashboard?.account?.uid
                    val devInfo = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} / ${_ui.value.env.emulatorType.label}"

                    val manifest = withContext(Dispatchers.IO) {
                        rustCore.createBackupFromStaging(
                            stagingDir = tmpStage.absolutePath,
                            outputArchive = outArchive.absolutePath,
                            packageName = pkg,
                            profileId = _ui.value.profile.id,
                            uid = uid,
                            note = note.ifBlank { t("Sao lưu thủ công", "Manual backup") },
                            deviceInfo = devInfo,
                        )
                    }
                    refreshBackupsAndRollbacks()
                    postToast(
                        t(
                            "Đã tạo backup ${outArchive.name} (${manifest.entries.size} file, SHA-256 đã xác thực)",
                            "Created backup ${outArchive.name} (${manifest.entries.size} files, SHA-256 verified)",
                        )
                    )
                } finally {
                    tmpStage.deleteRecursively()
                }
            } catch (t: Throwable) {
                postError(t("Tạo backup thất bại: ${t.message}", "Backup creation failed: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun restoreBackupToGame(archivePath: String) {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang kiểm tra toàn vẹn SHA-256 & khôi phục bản sao lưu...",
                    "Verifying SHA-256 integrity & restoring backup...",
                )
            )
            try {
                if (!_ui.value.env.rootGranted) {
                    throw IllegalStateException(
                        t(
                            "Khôi phục trực tiếp vào game yêu cầu quyền Root.",
                            "Restoring directly to the game requires Root access.",
                        )
                    )
                }
                val inspect = withContext(Dispatchers.IO) { rustCore.inspectBackup(archivePath) }
                if (!inspect.integrityOk) {
                    throw IllegalStateException(
                        t(
                            "File backup bị hỏng: ${inspect.integrityErrors.joinToString("; ")}",
                            "Corrupted backup archive: ${inspect.integrityErrors.joinToString("; ")}",
                        )
                    )
                }
                val extractDir = File(rootBridge.stagingDir(), "restore_${System.currentTimeMillis()}").apply { mkdirs() }
                try {
                    val extracted = withContext(Dispatchers.IO) {
                        rustCore.verifyAndExtractBackup(archivePath, extractDir.absolutePath)
                    }
                    val xmlFile = extracted.firstOrNull { it.relativePath.endsWith(".xml", ignoreCase = true) }
                        ?: throw IllegalStateException(
                            t(
                                "Không tìm thấy file playerprefs XML bên trong backup.",
                                "No playerprefs XML file found inside backup archive.",
                            )
                        )

                    val targetPkg = _ui.value.settings.selectedPackage
                    rootBridge.pushStagedSaveAtomic(
                        packageName = targetPkg,
                        stagedFile = File(xmlFile.stagedPath),
                        forceStop = _ui.value.settings.autoForceStop,
                        createRollbackSnapshot = true,
                    ).getOrThrow()

                    refreshBackupsAndRollbacks()
                    postToast(
                        t(
                            "Khôi phục backup thành công vào $targetPkg! (Đã tự động lưu rollback cũ)",
                            "Backup restored to $targetPkg! (Previous save snapshotted to Auto-Rollback)",
                        )
                    )
                    if (_ui.value.hasActiveSession) {
                        openGameSaveRoot(targetPkg)
                    }
                } finally {
                    extractDir.deleteRecursively()
                }
            } catch (t: Throwable) {
                postError(t("Khôi phục thất bại: ${t.message}", "Restore failed: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun restoreRollbackSnapshot(record: RollbackRecord) {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang hoàn tác từ bản Rollback ${record.fileName}...",
                    "Rolling back from snapshot ${record.fileName}...",
                )
            )
            try {
                if (!_ui.value.env.rootGranted) {
                    throw IllegalStateException(
                        t(
                            "Hoàn tác vào game yêu cầu quyền Root.",
                            "Rolling back requires Root access.",
                        )
                    )
                }
                val targetPkg = if (record.packageName.startsWith("com.")) {
                    record.packageName
                } else {
                    _ui.value.settings.selectedPackage
                }
                rootBridge.pushStagedSaveAtomic(
                    packageName = targetPkg,
                    stagedFile = File(record.filePath),
                    forceStop = _ui.value.settings.autoForceStop,
                    createRollbackSnapshot = false,
                ).getOrThrow()
                postToast(
                    t(
                        "Đã hoàn tác thành công save cho $targetPkg!",
                        "Successfully rolled back save for $targetPkg!",
                    )
                )
                if (_ui.value.hasActiveSession) {
                    openGameSaveRoot(targetPkg)
                }
            } catch (t: Throwable) {
                postError(t("Hoàn tác thất bại: ${t.message}", "Rollback failed: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun deleteBackupFile(archivePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(archivePath).delete() }
            refreshBackupsAndRollbacks()
            postToast(t("Đã xóa bản sao lưu", "Backup deleted"))
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            setBusy(t("Đang nhập & kiểm tra file .owldb...", "Importing & verifying .owldb archive..."))
            try {
                val name = resolveDisplayName(context, uri)
                    ?.let { if (it.endsWith(".owldb", ignoreCase = true)) it else "$it.owldb" }
                    ?: "imported_${System.currentTimeMillis()}.owldb"
                val dest = File(rootBridge.defaultBackupDir(), name)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException(t("Không thể đọc file đã chọn", "Unable to read selected file"))
                }
                val insp = withContext(Dispatchers.IO) { rustCore.inspectBackup(dest.absolutePath) }
                refreshBackupsAndRollbacks()
                if (insp.integrityOk) {
                    postToast(
                        t(
                            "Đã nhập backup hợp lệ: $name (${insp.manifest.entries.size} file)",
                            "Imported valid backup: $name (${insp.manifest.entries.size} files)",
                        )
                    )
                } else {
                    postError(
                        t(
                            "Đã nhập $name nhưng phát hiện lỗi toàn vẹn: ${insp.integrityErrors.joinToString()}",
                            "Imported $name with integrity errors: ${insp.integrityErrors.joinToString()}",
                        )
                    )
                }
            } catch (t: Throwable) {
                postError(t("Nhập backup thất bại: ${t.message}", "Backup import failed: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun exportBackupToUri(context: Context, archivePath: String, outUri: Uri) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(outUri)?.use { os ->
                        File(archivePath).inputStream().use { it.copyTo(os) }
                    } ?: throw IllegalStateException(t("Không thể ghi ra tệp đích", "Unable to write destination file"))
                }
                postToast(t("Đã xuất file sao lưu thành công", "Backup exported successfully"))
            } catch (t: Throwable) {
                postError(t("Xuất backup thất bại: ${t.message}", "Backup export failed: ${t.message}"))
            }
        }
    }

    // ==================== Tools: Account Sanitizer ====================

    fun runAccountSanitizer(removeLocalDeviceId: Boolean) {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang dọn dẹp dữ liệu tài khoản / phiên đăng nhập...",
                    "Sanitizing account / cloud session keys...",
                )
            )
            try {
                val pkg = _ui.value.settings.selectedPackage
                val extraPatterns = _ui.value.profile.sanitizer.playerPrefsRemovePatterns

                if (!_ui.value.env.rootGranted) {
                    throw IllegalStateException(
                        t(
                            "Dọn tài khoản trực tiếp yêu cầu quyền Root.",
                            "Direct account sanitization requires Root access.",
                        )
                    )
                }

                val pulled = rootBridge.pullGameSaveToStaging(pkg).getOrThrow()
                val outSanitized = File(rootBridge.stagingDir(), "sanitized_${pkg}.xml")
                val res = withContext(Dispatchers.IO) {
                    rustCore.sanitizeXmlFile(
                        inPath = pulled.absolutePath,
                        outPath = outSanitized.absolutePath,
                        extraPatterns = extraPatterns,
                        removeLocalDeviceId = removeLocalDeviceId,
                    )
                }

                rootBridge.pushStagedSaveAtomic(
                    packageName = pkg,
                    stagedFile = outSanitized,
                    forceStop = true,
                    createRollbackSnapshot = true,
                ).getOrThrow()

                val summary = if (res.removedKeys.isEmpty()) {
                    t(
                        "Không có khóa tài khoản/cloud nào cần xóa (save đã sạch).",
                        "No account/cloud keys found to remove (save is already clean).",
                    )
                } else {
                    t(
                        "Đã gỡ ${res.removedKeys.size} khóa: ${res.removedKeys.joinToString(", ")} (còn ${res.remainingEntries} khóa)",
                        "Removed ${res.removedKeys.size} keys: ${res.removedKeys.joinToString(", ")} (${res.remainingEntries} keys remaining)",
                    )
                }
                _ui.update { it.copy(lastSanitizeSummary = summary) }
                refreshBackupsAndRollbacks()
                postToast(summary)
                if (_ui.value.hasActiveSession) {
                    openGameSaveRoot(pkg)
                }
            } catch (t: Throwable) {
                postError(t("Lỗi dọn tài khoản: ${t.message}", "Account sanitize failed: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    // ==================== Tools: SSAID Spoofer ====================

    fun inspectSystemSsaid() {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang đọc settings_ssaid.xml (hỗ trợ Plain XML & Android 12+ ABX)...",
                    "Reading settings_ssaid.xml (Plain XML & Android 12+ ABX)...",
                )
            )
            try {
                if (!_ui.value.env.rootGranted) {
                    throw IllegalStateException(
                        t(
                            "Đọc settings_ssaid.xml yêu cầu quyền Root.",
                            "Reading settings_ssaid.xml requires Root access.",
                        )
                    )
                }
                val staged = rootBridge.pullSsaidToStaging().getOrThrow()
                val pkg = _ui.value.settings.selectedPackage
                val insp = withContext(Dispatchers.IO) {
                    rustCore.inspectSsaidFile(staged.absolutePath, pkg)
                }
                val randomNew = rustCore.generateRandomSsaid()
                _ui.update {
                    it.copy(
                        ssaidInspection = insp,
                        ssaidInput = it.ssaidInput.ifBlank { randomNew },
                        ssaidHasOriginalBackup = rootBridge.ssaidBackupFile().exists(),
                    )
                }
                postToast(
                    t(
                        "Đã đọc SSAID (${insp.format}, tìm thấy ${insp.entries.size}/${insp.totalEntries} mục khớp)",
                        "Loaded SSAID (${insp.format}, matched ${insp.entries.size}/${insp.totalEntries} entries)",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Lỗi đọc SSAID: ${t.message}", "Failed to read SSAID: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun updateSsaidInput(value: String) {
        _ui.update { it.copy(ssaidInput = value.lowercase().filter { ch -> ch in '0'..'9' || ch in 'a'..'f' }.take(16)) }
    }

    fun randomizeSsaidInput() {
        runCatching { rustCore.generateRandomSsaid() }.onSuccess { gen ->
            _ui.update { it.copy(ssaidInput = gen) }
        }
    }

    fun applySsaidSpoof() {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang ghi SSAID mới & kiểm chứng toàn vẹn...",
                    "Writing new SSAID & verifying file integrity...",
                )
            )
            try {
                if (!_ui.value.env.rootGranted) {
                    throw IllegalStateException(
                        t(
                            "Sửa settings_ssaid.xml yêu cầu quyền Root.",
                            "Modifying settings_ssaid.xml requires Root access.",
                        )
                    )
                }
                val pkg = _ui.value.settings.selectedPackage
                val newSsaid = _ui.value.ssaidInput.trim().lowercase()
                if (newSsaid.length != 16) {
                    throw IllegalStateException(
                        t(
                            "SSAID phải gồm đúng 16 ký tự hex (0-9, a-f).",
                            "SSAID must be exactly 16 hex characters (0-9, a-f).",
                        )
                    )
                }

                val stagedIn = rootBridge.pullSsaidToStaging().getOrThrow()
                val stagedOut = File(rootBridge.stagingDir(), "settings_ssaid_new.xml")
                val upd = withContext(Dispatchers.IO) {
                    rustCore.updateSsaidFileVerified(
                        inPath = stagedIn.absolutePath,
                        targetPackage = pkg,
                        newSsaid = newSsaid,
                        outPath = stagedOut.absolutePath,
                    )
                }
                rootBridge.pushSsaidAtomic(stagedOut).getOrThrow()
                val refreshed = withContext(Dispatchers.IO) {
                    rustCore.inspectSsaidFile(stagedOut.absolutePath, pkg)
                }
                _ui.update {
                    it.copy(
                        ssaidInspection = refreshed,
                        ssaidHasOriginalBackup = rootBridge.ssaidBackupFile().exists(),
                    )
                }
                postToast(
                    t(
                        "Đã đổi SSAID [${upd.format}] từ ${upd.oldValue} → ${upd.newValue}. Hãy Soft Reboot để có hiệu lực!",
                        "Updated SSAID [${upd.format}] from ${upd.oldValue} → ${upd.newValue}. Soft Reboot to apply!",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Đổi SSAID thất bại: ${t.message}", "SSAID spoof failed: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun restoreOriginalSsaid() {
        viewModelScope.launch {
            setBusy(t("Đang khôi phục settings_ssaid.xml gốc...", "Restoring original settings_ssaid.xml..."))
            try {
                rootBridge.restoreOriginalSsaid().getOrThrow()
                inspectSystemSsaid()
                postToast(
                    t(
                        "Đã khôi phục settings_ssaid.xml gốc thành công!",
                        "Restored original settings_ssaid.xml successfully!",
                    )
                )
            } catch (t: Throwable) {
                postError(t("Khôi phục SSAID gốc thất bại: ${t.message}", "Failed to restore original SSAID: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun triggerSoftReboot() {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang gửi lệnh Soft Reboot (khởi động lại Zygote)...",
                    "Triggering Soft Reboot (restarting Zygote)...",
                )
            )
            val res = rootBridge.softReboot()
            clearBusy()
            if (!res.ok) {
                postError(
                    t(
                        "Không thể Soft Reboot tự động: ${res.stderr}. Hãy khởi động lại thiết bị/giả lập thủ công.",
                        "Auto Soft Reboot failed: ${res.stderr}. Please reboot your device/emulator manually.",
                    )
                )
            }
        }
    }

    // ==================== External Profile / Preset Hot-Import ====================

    fun importExternalProfileUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            setBusy(
                t(
                    "Đang nhập cấu hình Profile / Preset ngoài...",
                    "Importing external Profile / Preset config...",
                )
            )
            try {
                val name = resolveDisplayName(context, uri) ?: "external_config.json"
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException(t("Không thể đọc tệp", "Unable to read file"))
                }
                val msg = withContext(Dispatchers.IO) {
                    profileRepo.importExternalFile(name, bytes).getOrThrow()
                }
                _ui.update {
                    it.copy(
                        profile = profileRepo.loadProfile(),
                        presets = profileRepo.loadPresets(),
                        catalog = profileRepo.loadCatalog(),
                        hasProfileOverrides = profileRepo.hasOverrides(),
                    )
                }
                postToast(msg)
            } catch (t: Throwable) {
                postError(t("Nhập cấu hình ngoài thất bại: ${t.message}", "External config import failed: ${t.message}"))
            } finally {
                clearBusy()
            }
        }
    }

    fun clearExternalProfileOverrides() {
        profileRepo.clearOverrides()
        _ui.update {
            it.copy(
                profile = profileRepo.loadProfile(),
                presets = profileRepo.loadPresets(),
                catalog = profileRepo.loadCatalog(),
                hasProfileOverrides = false,
            )
        }
        postToast(
            t(
                "Đã khôi phục cấu hình OWL mặc định đi kèm APK",
                "Restored built-in OWL profile & presets",
            )
        )
    }

    // ==================== Helpers ====================

    private suspend fun refreshSessionViews() {
        val state = _ui.value
        val sid = state.sessionId
        if (sid <= 0L) return
        val dash = withContext(Dispatchers.IO) { rustCore.getDashboard(sid) }
        val page = withContext(Dispatchers.IO) {
            rustCore.queryEntries(
                sid,
                state.explorerSearch,
                state.explorerCategory,
                state.explorerOnlyModified,
                state.explorerPage.offset,
                80,
            )
        }
        _ui.update { it.copy(dashboard = dash, explorerPage = page) }
    }

    private fun setBusy(msg: String) {
        _ui.update { it.copy(isBusy = true, busyMessage = msg) }
    }

    private fun clearBusy() {
        _ui.update { it.copy(isBusy = false, busyMessage = "") }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
