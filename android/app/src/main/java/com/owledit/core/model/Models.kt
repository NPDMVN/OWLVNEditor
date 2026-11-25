package com.owledit.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RustEnvelope<T>(
    val ok: Boolean = false,
    val data: T? = null,
    val error: String? = null,
)

@Serializable
data class OpenSessionOut(
    @SerialName("session_id") val sessionId: Long,
    val dashboard: DashboardSummary,
)

@Serializable
data class AccountInfo(
    val uid: String? = null,
    @SerialName("cloud_save_id") val cloudSaveId: String? = null,
    val email: String? = null,
    @SerialName("found_keys") val foundKeys: List<String> = emptyList(),
)

@Serializable
data class CurrencyStatus(
    val key: String,
    val label: String,
    val category: String,
    val value: Int,
    @SerialName("encrypt_key") val encryptKey: Int,
    @SerialName("safe_max") val safeMax: Int,
    val exists: Boolean,
    val modified: Boolean,
)

@Serializable
data class AsuraStatStatus(
    @SerialName("stat_id") val statId: Int,
    val code: String,
    val label: String,
    val value: Int,
)

@Serializable
data class MenuSkinStatus(
    val id: Int,
    val code: String,
    val label: String,
    val unlocked: Boolean,
)

@Serializable
data class HeroStatus(
    @SerialName("body_name") val bodyName: String,
    @SerialName("body_id") val bodyId: Int,
    @SerialName("name_en") val nameEn: String,
    @SerialName("name_vi") val nameVi: String,
    val unlocked: Boolean,
    val level: Int,
    @SerialName("second_skill_unlocked") val secondSkillUnlocked: Boolean,
    @SerialName("equipped_skin") val equippedSkin: String = "",
    @SerialName("skills_unlocked") val skillsUnlocked: Int = 0,
    @SerialName("skills_total") val skillsTotal: Int = 0,
    @SerialName("styles_unlocked") val stylesUnlocked: Int = 0,
    @SerialName("styles_total") val stylesTotal: Int = 0,
    val exists: Boolean = true,
)

@Serializable
data class ContainerSummary(
    @SerialName("plain_key") val plainKey: String,
    @SerialName("md5_key") val md5Key: String,
    val category: String,
    @SerialName("entry_count") val entryCount: Int,
    val editable: Boolean,
    @SerialName("kind_label") val kindLabel: String,
)

@Serializable
data class UnlockOverview(
    @SerialName("hero_skins_unlocked") val heroSkinsUnlocked: Int = 0,
    @SerialName("hero_skins_total") val heroSkinsTotal: Int = 112,
    @SerialName("hero_skin_powers_unlocked") val heroSkinPowersUnlocked: Int = 0,
    @SerialName("hero_skin_powers_total") val heroSkinPowersTotal: Int = 23,
    @SerialName("npc_skins_unlocked") val npcSkinsUnlocked: Int = 0,
    @SerialName("npc_skins_total") val npcSkinsTotal: Int = 28,
    @SerialName("pets_unlocked") val petsUnlocked: Int = 0,
    @SerialName("pets_total") val petsTotal: Int = 32,
    @SerialName("naga_powers_maxed") val nagaPowersMaxed: Int = 0,
    @SerialName("naga_powers_total") val nagaPowersTotal: Int = 40,
    @SerialName("six_cycle_powers_unlocked") val sixCyclePowersUnlocked: Int = 0,
    @SerialName("six_cycle_powers_total") val sixCyclePowersTotal: Int = 37,
    @SerialName("albums_unlocked") val albumsUnlocked: Int = 0,
    @SerialName("albums_total") val albumsTotal: Int = 42,
    @SerialName("statues_unlocked") val statuesUnlocked: Int = 0,
    @SerialName("statues_total") val statuesTotal: Int = 122,
    @SerialName("shared_skills_unlocked") val sharedSkillsUnlocked: Int = 0,
    @SerialName("shared_skills_total") val sharedSkillsTotal: Int = 14,
    @SerialName("items_unlocked") val itemsUnlocked: Int = 0,
    @SerialName("items_total") val itemsTotal: Int = 401,
    @SerialName("garden_plots_unlocked") val gardenPlotsUnlocked: Int = 0,
    @SerialName("garden_plots_total") val gardenPlotsTotal: Int = 9,
    @SerialName("ban_slots_unlocked") val banSlotsUnlocked: Int = 0,
    @SerialName("ban_slots_total") val banSlotsTotal: Int = 31,
    @SerialName("build_projects_unlocked") val buildProjectsUnlocked: Int = 0,
    @SerialName("build_projects_total") val buildProjectsTotal: Int = 11,
    @SerialName("travel_features_unlocked") val travelFeaturesUnlocked: Int = 0,
    @SerialName("travel_features_total") val travelFeaturesTotal: Int = 26,
    @SerialName("battle_pass_high_unlocked") val battlePassHighUnlocked: Int = 0,
    @SerialName("battle_pass_seasons_seen") val battlePassSeasonsSeen: Int = 0,
    @SerialName("battle_pass_total_level") val battlePassTotalLevel: Int = 0,
    @SerialName("difficulty_unlocked") val difficultyUnlocked: Int = 0,
    @SerialName("adventure_level") val adventureLevel: Int = 0,
    @SerialName("hero_common_pieces") val heroCommonPieces: Int = 0,
    @SerialName("skin_common_pieces") val skinCommonPieces: Int = 0,
    @SerialName("orb_refine_tickets") val orbRefineTickets: Int = 0,
    @SerialName("esr_identify_tickets") val esrIdentifyTickets: Int = 0,
    @SerialName("pet_food_count") val petFoodCount: Int = 0,
    @SerialName("pet_toy_count") val petToyCount: Int = 0,
    @SerialName("moye_token_count") val moyeTokenCount: Int = 0,
    @SerialName("bar_vip_unlocked") val barVipUnlocked: Boolean = false,
    @SerialName("chaos_mode_unlocked") val chaosModeUnlocked: Boolean = false,
    @SerialName("imba_mode_unlocked") val imbaModeUnlocked: Boolean = false,
    @SerialName("bank_unlocked") val bankUnlocked: Boolean = false,
    @SerialName("level_portal_unlocked") val levelPortalUnlocked: Boolean = false,
    @SerialName("achievements_completed") val achievementsCompleted: Int = 0,
    @SerialName("achievements_claimed") val achievementsClaimed: Int = 0,
    @SerialName("achievements_total") val achievementsTotal: Int = 236,
)

@Serializable
data class DashboardSummary(
    val account: AccountInfo = AccountInfo(),
    @SerialName("total_entries") val totalEntries: Int = 0,
    @SerialName("encrypted_entries") val encryptedEntries: Int = 0,
    @SerialName("resolved_keys") val resolvedKeys: Int = 0,
    @SerialName("nrbf_containers") val nrbfContainers: Int = 0,
    @SerialName("json_entries") val jsonEntries: Int = 0,
    @SerialName("modified_count") val modifiedCount: Int = 0,
    val currencies: List<CurrencyStatus> = emptyList(),
    @SerialName("asura_stats") val asuraStats: List<AsuraStatStatus> = emptyList(),
    @SerialName("menu_skins") val menuSkins: List<MenuSkinStatus> = emptyList(),
    val heroes: List<HeroStatus> = emptyList(),
    val containers: List<ContainerSummary> = emptyList(),
    val flags: Map<String, String> = emptyMap(),
    @SerialName("unlock_overview") val unlockOverview: UnlockOverview = UnlockOverview(),
)

@Serializable
data class ExplorerItem(
    @SerialName("raw_key") val rawKey: String,
    @SerialName("display_key") val displayKey: String,
    val category: String,
    @SerialName("is_encrypted") val isEncrypted: Boolean,
    @SerialName("value_kind") val valueKind: String,
    val preview: String,
    @SerialName("byte_len") val byteLen: Int,
    val modified: Boolean,
)

@Serializable
data class ExplorerPage(
    @SerialName("total_matched") val totalMatched: Int = 0,
    val offset: Int = 0,
    val limit: Int = 60,
    val items: List<ExplorerItem> = emptyList(),
)

@Serializable
data class NrbfDictKindDto(
    val kind: String,
    @SerialName("key_enum") val keyEnum: String? = null,
    @SerialName("val_enum") val valEnum: String? = null,
)

@Serializable
data class NrbfEntry(
    val key: String,
    val value: String,
)

@Serializable
data class NrbfDict(
    val kind: NrbfDictKindDto,
    val version: Int = 1,
    @SerialName("hash_size") val hashSize: Int = 3,
    val entries: List<NrbfEntry> = emptyList(),
)

@Serializable
data class EntryInspection(
    @SerialName("raw_key") val rawKey: String,
    @SerialName("display_key") val displayKey: String,
    val category: String,
    @SerialName("is_encrypted") val isEncrypted: Boolean,
    @SerialName("value_kind") val valueKind: String,
    @SerialName("raw_plaintext") val rawPlaintext: String,
    val nrbf: NrbfDict? = null,
    @SerialName("json_pretty") val jsonPretty: String? = null,
    val modified: Boolean = false,
)

@Serializable
data class SetEncryptIntOut(
    @SerialName("m_value") val mValue: Int,
    @SerialName("encrypt_key") val encryptKey: Int,
    @SerialName("modified_count") val modifiedCount: Int,
)

@Serializable
data class ApplyPresetOut(
    val message: String,
    @SerialName("modified_count") val modifiedCount: Int,
)

@Serializable
data class DiffEntry(
    @SerialName("raw_key") val rawKey: String,
    @SerialName("display_key") val displayKey: String,
    @SerialName("change_type") val changeType: String,
    @SerialName("old_preview") val oldPreview: String,
    @SerialName("new_preview") val newPreview: String,
    val warning: String? = null,
)

@Serializable
data class CommitOut(
    @SerialName("staged_path") val stagedPath: String,
    val sha256: String,
    @SerialName("byte_len") val byteLen: Int,
    @SerialName("total_entries") val totalEntries: Int,
    @SerialName("modified_entries") val modifiedEntries: Int,
)

@Serializable
data class SanitizeOut(
    @SerialName("removed_keys") val removedKeys: List<String> = emptyList(),
    @SerialName("remaining_entries") val remainingEntries: Int = 0,
    val sha256: String = "",
)

@Serializable
data class BackupEntry(
    @SerialName("relative_path") val relativePath: String,
    val size: Long,
    val sha256: String,
)

@Serializable
data class BackupManifest(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("app_name") val appName: String = "OWLEditor",
    @SerialName("game_profile_id") val gameProfileId: String = "",
    @SerialName("package_name") val packageName: String = "",
    val uid: String? = null,
    val note: String = "",
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long = 0L,
    @SerialName("device_info") val deviceInfo: String = "",
    val entries: List<BackupEntry> = emptyList(),
)

@Serializable
data class BackupInspectResult(
    @SerialName("archive_path") val archivePath: String,
    @SerialName("archive_size") val archiveSize: Long,
    val manifest: BackupManifest,
    @SerialName("integrity_ok") val integrityOk: Boolean,
    @SerialName("integrity_errors") val integrityErrors: List<String> = emptyList(),
)

@Serializable
data class ExtractedBackupFile(
    @SerialName("relative_path") val relativePath: String,
    @SerialName("staged_path") val stagedPath: String,
    val size: Long,
    val sha256: String,
)

@Serializable
data class SsaidEntry(
    val id: String = "",
    val name: String = "",
    @SerialName("package") val packageName: String = "",
    val value: String = "",
    @SerialName("default_value") val defaultValue: String = "",
    val tag: String = "",
)

@Serializable
data class SsaidInspection(
    val format: String = "PLAIN_XML",
    @SerialName("total_entries") val totalEntries: Int = 0,
    val entries: List<SsaidEntry> = emptyList(),
)

@Serializable
data class SsaidUpdateOut(
    val format: String,
    @SerialName("package") val packageName: String,
    @SerialName("old_value") val oldValue: String,
    @SerialName("new_value") val newValue: String,
    val sha256: String,
    @SerialName("byte_len") val byteLen: Int,
)

@Serializable
data class ValidationResult(
    val valid: Boolean,
    val message: String,
)

// ==================== Profile, Catalog & Presets Assets ====================

@Serializable
data class OwlProfile(
    val schemaVersion: Int = 2,
    val id: String = "chillyroom.otherworld_legends",
    val displayName: String = "Otherworld Legends",
    val developer: String = "ChillyRoom",
    val engine: String = "unity_hybridclr",
    val packages: List<String> = emptyList(),
    val paths: OwlPaths = OwlPaths(),
    val crypto: OwlCrypto = OwlCrypto(),
    val sanitizer: OwlSanitizerConfig = OwlSanitizerConfig(),
    val features: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class OwlPaths(
    val filesDir: String = "files",
    val sharedPrefs: String = "shared_prefs/{package}.v2.playerprefs.xml",
)

@Serializable
data class OwlCrypto(
    val type: String = "owl_cryptoprefs_aes256_cbc_zero",
    val keyUtf8: String = "",
    val keyHex: String = "",
    val ivHex: String = "",
    val keyHash: String = "md5_hex_lower",
    val containerFormat: String = "ms_nrbf_base64",
)

@Serializable
data class OwlSanitizerConfig(
    val playerPrefsRemovePatterns: List<String> = emptyList(),
)

@Serializable
data class QuickPresetDef(
    val id: String,
    val title: String,
    val description: String,
    val badge: String = "",
    val titleEn: String = "",
    val descriptionEn: String = "",
    val badgeEn: String = "",
)

@Serializable
data class OwlPresetsFile(
    val version: String = "3.8.0",
    val heroSkinsAll: List<String> = emptyList(),
    val itemsAll: List<String> = emptyList(),
    val quickPresets: List<QuickPresetDef> = emptyList(),
)

@Serializable
data class BilingualLabel(
    val vi: String = "",
    val en: String = "",
    val safeMax: Int? = null,
)

@Serializable
data class OwlCatalog(
    val version: String = "3.8.0",
    val currencies: Map<String, BilingualLabel> = emptyMap(),
    val containers: Map<String, BilingualLabel> = emptyMap(),
)

// ==================== Root Helper & Environment Models ====================

@Serializable
data class HelperEnvelope<T>(
    val ok: Boolean = false,
    val data: T? = null,
    val error: String? = null,
)

@Serializable
data class HelperPingInfo(
    val version: String = "",
    val arch: String = "",
    val uid: Int = -1,
    val euid: Int = -1,
)

@Serializable
data class HelperStatInfo(
    val path: String = "",
    val exists: Boolean = false,
    @SerialName("is_dir") val isDir: Boolean = false,
    val size: Long = 0L,
    @SerialName("mode_octal") val modeOctal: String = "0660",
    val uid: Int = 0,
    val gid: Int = 0,
    val sha256: String? = null,
)

@Serializable
data class HelperAtomicWriteResult(
    val dst: String = "",
    val size: Long = 0L,
    val sha256: String = "",
    @SerialName("mode_octal") val modeOctal: String = "0660",
    val uid: Int = 0,
    val gid: Int = 0,
)

enum class SuVariant(val label: String) {
    MAGISK("Magisk (su -mm)"),
    KERNELSU("KernelSU"),
    APATCH("APatch"),
    GENERIC("Root SU (Giả lập / SuperSU)"),
    NONE("Chưa phát hiện Root"),
}

enum class EmulatorType(val label: String, val isEmulator: Boolean) {
    LDPLAYER("LDPlayer (PC Emulator)", true),
    MUMU("MuMu Player (PC Emulator)", true),
    BLUESTACKS("BlueStacks (PC Emulator)", true),
    NOX("NoxPlayer (PC Emulator)", true),
    MEMU("MEmu Play (PC Emulator)", true),
    GENERIC_EMULATOR("Giả lập Android (x86/x86_64)", true),
    REAL_DEVICE("Thiết bị Android thật", false),
}

data class InstalledGamePackage(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val saveExists: Boolean,
    val saveSizeBytes: Long = 0L,
)

data class RootEnvironmentInfo(
    val rootGranted: Boolean = false,
    val suVariant: SuVariant = SuVariant.NONE,
    val emulatorType: EmulatorType = EmulatorType.REAL_DEVICE,
    val abi: String = "",
    val androidVersion: String = "",
    val selinuxMode: String = "Unknown",
    val helperReady: Boolean = false,
    val helperBanner: String = "",
    val coreBanner: String = "",
    val installedPackages: List<InstalledGamePackage> = emptyList(),
    val diagnosticMessage: String = "",
)

data class AppSettings(
    val autoForceStop: Boolean = true,
    val autoBackupBeforeSave: Boolean = true,
    val showDiffBeforeSave: Boolean = true,
    val selectedPackage: String = "com.chillyroom.zhmr.gp",
    val language: String = "vi", // "vi" or "en"
)

data class RollbackRecord(
    val filePath: String,
    val fileName: String,
    val packageName: String,
    val createdAtMs: Long,
    val sizeBytes: Long,
)
