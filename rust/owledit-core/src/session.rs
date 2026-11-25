use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use serde_json::{Map as JsonMap, Value as JsonValue};

use crate::crypto::{
    decode_encrypt_int, encode_encrypt_int, is_md5_hex, md5_key, sha256_hex, unity_url_decode,
    unity_url_encode, OwlCodec,
};
use crate::dictionary::{
    KeyDictionary, ASURA_STATS, ENCRYPT_INT_PAIRS, MENU_SKIN_TYPES, PLAYABLE_HEROES,
};
use crate::error::{OwlError, OwlResult};
use crate::nrbf::{NrbfDict, NrbfDictKind};
use crate::sanitizer::{find_account_info, AccountInfo};
use crate::unlockables::{
    ACHIEVEMENT_IDS_ALL, ALBUMS_ALL, AUTO_PICK_UNITS_ALL, BAR_DRINKS_ALL, BAR_MATERIAL_IDS,
    BUILD_CAT_PROJECTS_ALL, CE_AFFIXES_ALL, FLOOR_SKIN_PIECES_ALL, HERO_SKINS_ALL,
    HERO_SKIN_POWERS_ALL, ITEMS_ALL, ITEM_PACKS_ALL, NAGA_POWERS_ALL, NPC_SKINS_ALL, PETS_ALL,
    SHARED_SKILLS_ALL, SHARED_SKILL_MATERIALS_ALL, SIX_CYCLE_POWERS_ALL, STATUES_ALL,
    STORY_CHAPTERS_ALL, TRAVEL_UNLOCKS_ALL,
};
use crate::xml_prefs::{PlayerPrefsDoc, PrefValueType};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CurrencyStatus {
    pub key: String,
    pub label: String,
    pub category: String,
    pub value: i32,
    pub encrypt_key: i32,
    pub safe_max: i32,
    pub exists: bool,
    pub modified: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AsuraStatStatus {
    pub stat_id: i32,
    pub code: String,
    pub label: String,
    pub value: i32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MenuSkinStatus {
    pub id: i32,
    pub code: String,
    pub label: String,
    pub unlocked: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HeroStatus {
    pub body_name: String,
    pub body_id: i32,
    pub name_en: String,
    pub name_vi: String,
    pub unlocked: bool,
    pub level: i32,
    pub second_skill_unlocked: bool,
    pub equipped_skin: String,
    pub skills_unlocked: usize,
    pub skills_total: usize,
    pub styles_unlocked: usize,
    pub styles_total: usize,
    pub exists: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ContainerSummary {
    pub plain_key: String,
    pub md5_key: String,
    pub category: String,
    pub entry_count: usize,
    pub editable: bool,
    pub kind_label: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct UnlockOverview {
    pub hero_skins_unlocked: usize,
    pub hero_skins_total: usize,
    pub hero_skin_powers_unlocked: usize,
    pub hero_skin_powers_total: usize,
    pub npc_skins_unlocked: usize,
    pub npc_skins_total: usize,
    pub pets_unlocked: usize,
    pub pets_total: usize,
    pub naga_powers_maxed: usize,
    pub naga_powers_total: usize,
    pub six_cycle_powers_unlocked: usize,
    pub six_cycle_powers_total: usize,
    pub albums_unlocked: usize,
    pub albums_total: usize,
    pub statues_unlocked: usize,
    pub statues_total: usize,
    pub shared_skills_unlocked: usize,
    pub shared_skills_total: usize,
    pub items_unlocked: usize,
    pub items_total: usize,
    pub garden_plots_unlocked: usize,
    pub garden_plots_total: usize,
    pub ban_slots_unlocked: usize,
    pub ban_slots_total: usize,
    pub build_projects_unlocked: usize,
    pub build_projects_total: usize,
    pub travel_features_unlocked: usize,
    pub travel_features_total: usize,
    pub battle_pass_high_unlocked: usize,
    pub battle_pass_seasons_seen: usize,
    pub battle_pass_total_level: i32,
    pub difficulty_unlocked: i32,
    pub adventure_level: i32,
    pub hero_common_pieces: i32,
    pub skin_common_pieces: i32,
    pub orb_refine_tickets: i32,
    pub esr_identify_tickets: i32,
    pub pet_food_count: i32,
    pub pet_toy_count: i32,
    pub moye_token_count: i32,
    pub bar_vip_unlocked: bool,
    pub chaos_mode_unlocked: bool,
    pub imba_mode_unlocked: bool,
    pub bank_unlocked: bool,
    pub level_portal_unlocked: bool,
    pub achievements_completed: usize,
    pub achievements_claimed: usize,
    pub achievements_total: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DashboardSummary {
    pub account: AccountInfo,
    pub total_entries: usize,
    pub encrypted_entries: usize,
    pub resolved_keys: usize,
    pub nrbf_containers: usize,
    pub json_entries: usize,
    pub modified_count: usize,
    pub currencies: Vec<CurrencyStatus>,
    pub asura_stats: Vec<AsuraStatStatus>,
    pub menu_skins: Vec<MenuSkinStatus>,
    pub heroes: Vec<HeroStatus>,
    pub containers: Vec<ContainerSummary>,
    pub flags: BTreeMap<String, String>,
    pub unlock_overview: UnlockOverview,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExplorerItem {
    pub raw_key: String,
    pub display_key: String,
    pub category: String,
    pub is_encrypted: bool,
    pub value_kind: String, // "nrbf", "json", "number", "bool", "string", "plain"
    pub preview: String,
    pub byte_len: usize,
    pub modified: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExplorerPage {
    pub total_matched: usize,
    pub offset: usize,
    pub limit: usize,
    pub items: Vec<ExplorerItem>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct EntryInspection {
    pub raw_key: String,
    pub display_key: String,
    pub category: String,
    pub is_encrypted: bool,
    pub value_kind: String,
    pub raw_plaintext: String,
    pub nrbf: Option<NrbfDict>,
    pub json_pretty: Option<String>,
    pub modified: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DiffEntry {
    pub raw_key: String,
    pub display_key: String,
    pub change_type: String, // "ADDED", "MODIFIED", "REMOVED"
    pub old_preview: String,
    pub new_preview: String,
    pub warning: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CommitResult {
    pub xml_bytes: Vec<u8>,
    pub sha256: String,
    pub total_entries: usize,
    pub modified_entries: usize,
}

/// Stateful in-memory session holding a parsed & decrypted Otherworld Legends save file.
pub struct OwlSession {
    pub codec: OwlCodec,
    pub dict: KeyDictionary,
    pub doc: PlayerPrefsDoc,
    /// Original raw XML value per `raw_key` (for diffing & rollback tracking).
    original_raw: HashMap<String, (PrefValueType, String)>,
    /// Decrypted plaintext cache per `raw_key` (for encrypted MD5 entries).
    decrypted_cache: HashMap<String, String>,
    /// Original decrypted plaintext per `raw_key` (for human-readable diffing).
    original_decrypted: HashMap<String, String>,
}

impl OwlSession {
    pub fn open_xml_bytes(
        xml_bytes: &[u8],
        codec: OwlCodec,
        extra_dict_json: Option<&str>,
    ) -> OwlResult<Self> {
        let doc = PlayerPrefsDoc::parse_bytes(xml_bytes)?;
        let mut dict = KeyDictionary::new_builtin();
        if let Some(json) = extra_dict_json {
            dict.merge_json(json);
        }

        let mut original_raw = HashMap::with_capacity(doc.len());
        let mut decrypted_cache = HashMap::with_capacity(doc.len());
        let mut original_decrypted = HashMap::with_capacity(doc.len());

        for entry in &doc.entries {
            original_raw.insert(
                entry.raw_key.clone(),
                (entry.value_type.clone(), entry.raw_value.clone()),
            );
            if entry.value_type == PrefValueType::String && is_md5_hex(&entry.raw_key) {
                if let Ok(plain) = codec.decrypt_unity_value(&entry.raw_value) {
                    original_decrypted.insert(entry.raw_key.clone(), plain.clone());
                    decrypted_cache.insert(entry.raw_key.clone(), plain);
                }
            }
        }

        Ok(Self {
            codec,
            dict,
            doc,
            original_raw,
            decrypted_cache,
            original_decrypted,
        })
    }

    pub fn display_key(&self, raw_key: &str) -> (String, String) {
        if let Some(meta) = self.dict.lookup(raw_key) {
            (meta.plain_key.clone(), meta.category.clone())
        } else if is_md5_hex(raw_key) {
            (raw_key.to_string(), "unknown".to_string())
        } else {
            let dk = unity_url_decode(raw_key).unwrap_or_else(|_| raw_key.to_string());
            let cat = if dk.starts_with("KeyRebind:") {
                "controls"
            } else if dk.contains("Account")
                || dk.contains("SdkState")
                || dk.contains("Uid")
                || dk.contains("CloudSave")
            {
                "account"
            } else {
                "plain"
            };
            (dk, cat.to_string())
        }
    }

    pub fn is_modified(&self, raw_key: &str) -> bool {
        match (self.original_raw.get(raw_key), self.doc.get_raw(raw_key)) {
            (Some((_, orig_v)), Some(cur)) => orig_v != &cur.raw_value,
            (None, Some(_)) => true,
            (Some(_), None) => true,
            (None, None) => false,
        }
    }

    pub fn modified_count(&self) -> usize {
        let mut count = 0usize;
        for entry in &self.doc.entries {
            if self.is_modified(&entry.raw_key) {
                count += 1;
            }
        }
        for orig_k in self.original_raw.keys() {
            if self.doc.get_raw(orig_k).is_none() {
                count += 1;
            }
        }
        count
    }

    /// Reads the decrypted plaintext of an encrypted key by its plain name (e.g. `"SoulStoneCount"`).
    pub fn get_encrypted_plain(&self, plain_key: &str) -> Option<&str> {
        let h = md5_key(plain_key);
        self.decrypted_cache.get(&h).map(|s| s.as_str())
    }

    /// Writes a decrypted plaintext into an encrypted key by plain name (hashes key & AES-CBC encrypts value).
    pub fn set_encrypted_plain(&mut self, plain_key: &str, value: &str) -> OwlResult<()> {
        let h = md5_key(plain_key);
        self.dict.register(plain_key, "custom");
        let xml_val = self.codec.encrypt_unity_value(value)?;
        self.doc
            .upsert_raw(PrefValueType::String, h.clone(), xml_val);
        self.decrypted_cache.insert(h, value.to_string());
        Ok(())
    }

    /// Writes a decrypted plaintext directly by MD5 hash or plain key.
    pub fn set_raw_or_plain(
        &mut self,
        key: &str,
        value: &str,
        encrypt: bool,
    ) -> OwlResult<()> {
        if encrypt {
            let h = if is_md5_hex(key) {
                key.to_string()
            } else {
                self.dict.register(key, "custom");
                md5_key(key)
            };
            let xml_val = self.codec.encrypt_unity_value(value)?;
            self.doc
                .upsert_raw(PrefValueType::String, h.clone(), xml_val);
            self.decrypted_cache.insert(h, value.to_string());
        } else {
            let raw_key = if self.doc.get_raw(key).is_some() {
                key.to_string()
            } else {
                unity_url_encode(key)
            };
            let existing_type = self
                .doc
                .get_raw(&raw_key)
                .map(|e| e.value_type.clone())
                .unwrap_or(PrefValueType::String);
            let raw_val = if existing_type == PrefValueType::String {
                unity_url_encode(value)
            } else {
                value.to_string()
            };
            self.doc.upsert_raw(existing_type, raw_key, raw_val);
        }
        Ok(())
    }

    /// Reads an `EncryptValue` currency/ticket pair (`<key>` XOR `<key>Key`).
    pub fn get_encrypt_int_pair(&self, key_name: &str) -> (i32, i32, bool) {
        let m_str = self.get_encrypted_plain(key_name);
        let k_str = self.get_encrypted_plain(&format!("{key_name}Key"));
        match (
            m_str.and_then(|s| s.parse::<i32>().ok()),
            k_str.and_then(|s| s.parse::<i32>().ok()),
        ) {
            (Some(m), Some(k)) => (decode_encrypt_int(m, k), k, true),
            (Some(m), None) => (m, 0, true),
            _ => (0, 0, false),
        }
    }

    /// Sets an `EncryptValue` currency/ticket pair atomically with fresh random `encryptKey ∈ [200, 500)`.
    pub fn set_encrypt_int_pair(&mut self, key_name: &str, real_value: i32) -> OwlResult<(i32, i32)> {
        let clamped = real_value.max(0);
        let (m_val, enc_key) = encode_encrypt_int(clamped);
        self.set_encrypted_plain(key_name, &m_val.to_string())?;
        self.set_encrypted_plain(&format!("{key_name}Key"), &enc_key.to_string())?;
        Ok((m_val, enc_key))
    }

    /// Reads & parses a named NRBF dictionary container (by plain key or MD5).
    pub fn read_nrbf_dict(&self, key: &str) -> OwlResult<NrbfDict> {
        let h = if is_md5_hex(key) {
            key.to_string()
        } else {
            md5_key(key)
        };
        let b64 = self
            .decrypted_cache
            .get(&h)
            .ok_or_else(|| OwlError::Session(format!("Key '{key}' not found in save")))?;
        NrbfDict::parse_b64(b64)
    }

    /// Serializes & writes an NRBF dictionary container back into encrypted PlayerPrefs.
    pub fn write_nrbf_dict(&mut self, key: &str, dict: &NrbfDict) -> OwlResult<()> {
        let b64 = dict.to_b64()?;
        self.set_raw_or_plain(key, &b64, true)
    }

    /// Updates a single entry inside a named NRBF container (creating the container if given a default kind).
    pub fn patch_nrbf_entry(
        &mut self,
        container_key: &str,
        entry_key: &str,
        entry_value: &str,
        default_kind: Option<NrbfDictKind>,
    ) -> OwlResult<()> {
        let mut dict = match self.read_nrbf_dict(container_key) {
            Ok(d) => d,
            Err(_) => {
                let kind = default_kind.ok_or_else(|| {
                    OwlError::Session(format!(
                        "Container '{container_key}' does not exist and no default kind specified"
                    ))
                })?;
                NrbfDict::new(kind)
            }
        };
        dict.set(entry_key, entry_value);
        self.write_nrbf_dict(container_key, &dict)
    }

    /// Removes a single entry from a named NRBF container.
    pub fn remove_nrbf_entry(&mut self, container_key: &str, entry_key: &str) -> OwlResult<bool> {
        let mut dict = self.read_nrbf_dict(container_key)?;
        let removed = dict.remove(entry_key);
        if removed {
            self.write_nrbf_dict(container_key, &dict)?;
        }
        Ok(removed)
    }

    /// Batch-sets entries inside a named NRBF container.
    pub fn batch_patch_nrbf(
        &mut self,
        container_key: &str,
        updates: &[(String, String)],
        overwrite_existing: bool,
        default_kind: Option<NrbfDictKind>,
    ) -> OwlResult<usize> {
        let mut dict = match self.read_nrbf_dict(container_key) {
            Ok(d) => d,
            Err(_) => {
                let kind = default_kind.unwrap_or(NrbfDictKind::StringEncryptValue);
                NrbfDict::new(kind)
            }
        };
        let mut changed = 0usize;
        for (k, v) in updates {
            let exists = dict.get(k).is_some();
            if !exists || overwrite_existing {
                if dict.get(k) != Some(v.as_str()) {
                    dict.set(k.clone(), v.clone());
                    changed += 1;
                }
            }
        }
        if changed > 0 {
            self.write_nrbf_dict(container_key, &dict)?;
        }
        Ok(changed)
    }

    /// Sets every existing entry in an NRBF container to `uniform_value`.
    pub fn set_all_existing_nrbf(
        &mut self,
        container_key: &str,
        uniform_value: &str,
    ) -> OwlResult<usize> {
        let mut dict = self.read_nrbf_dict(container_key)?;
        let mut changed = 0usize;
        for entry in &mut dict.entries {
            if entry.value != uniform_value {
                entry.value = uniform_value.to_string();
                changed += 1;
            }
        }
        if changed > 0 {
            dict.version = dict.version.saturating_add(1);
            self.write_nrbf_dict(container_key, &dict)?;
        }
        Ok(changed)
    }

    /// Updates a hero's unlock state, level, second skill, and optionally unlocks all skills & styles.
    pub fn update_hero(
        &mut self,
        body_name: &str,
        unlocked: Option<bool>,
        level: Option<i32>,
        unlock_second_skill: Option<bool>,
        unlock_all_skills: bool,
        unlock_all_styles: bool,
    ) -> OwlResult<()> {
        if let Some(u) = unlocked {
            self.set_encrypted_plain(
                &format!("{body_name}isUnlock"),
                if u { "True" } else { "False" },
            )?;
        }
        if let Some(lvl) = level {
            let clamped = lvl.clamp(1, 15);
            self.set_encrypted_plain(&format!("{body_name}level"), &clamped.to_string())?;
        }
        if let Some(sec) = unlock_second_skill {
            self.set_encrypted_plain(
                &format!("{body_name}isUnlockSecondSkill"),
                if sec { "1" } else { "0" },
            )?;
        }
        if unlock_all_skills {
            let key = format!("{body_name}skillUnlockLibrary");
            if let Some(raw) = self.get_encrypted_plain(&key).map(|s| s.to_string()) {
                if let Ok(JsonValue::Object(mut map)) = serde_json::from_str::<JsonValue>(&raw) {
                    for (_, v) in map.iter_mut() {
                        *v = JsonValue::Bool(true);
                    }
                    let updated = serde_json::to_string(&JsonValue::Object(map))
                        .map_err(|e| OwlError::Json(e.to_string()))?;
                    self.set_encrypted_plain(&key, &updated)?;
                }
            }
        }
        if unlock_all_styles {
            let key = format!("{body_name}styleUnlockLibrary");
            if let Some(raw) = self.get_encrypted_plain(&key).map(|s| s.to_string()) {
                if let Ok(JsonValue::Object(mut map)) = serde_json::from_str::<JsonValue>(&raw) {
                    for (_, v) in map.iter_mut() {
                        *v = JsonValue::Bool(true);
                    }
                    let updated = serde_json::to_string(&JsonValue::Object(map))
                        .map_err(|e| OwlError::Json(e.to_string()))?;
                    self.set_encrypted_plain(&key, &updated)?;
                }
            }
        }
        Ok(())
    }

    /// Applies a 1-tap safe preset (parity with npdmVNEditor Quick Presets + full OWL v3.8.0 unlockables).
    pub fn apply_preset(
        &mut self,
        preset_id: &str,
        extra_skins: Option<&[String]>,
        extra_items: Option<&[String]>,
    ) -> OwlResult<String> {
        match preset_id {
            "safe_currencies" => {
                self.set_encrypt_int_pair("SoulStoneCount", 500_000)?;
                self.set_encrypt_int_pair("BlueStoneCount", 50_000)?;
                self.set_encrypt_int_pair("RedStoneCount", 50_000)?;
                self.set_encrypt_int_pair("WeaponUpgradeTicket", 200)?;
                self.set_encrypt_int_pair("RebornCard", 200)?;
                self.set_encrypt_int_pair("Fertilizer", 200)?;
                self.set_encrypt_int_pair("ProbationTicket", 99)?;
                let _ = self.patch_nrbf_entry(
                    "GameData",
                    "ESRIdentifyTicketCount",
                    "200",
                    Some(NrbfDictKind::StringEncryptValue),
                );
                let _ = self.patch_nrbf_entry(
                    "SixCycleOrb",
                    "OrbRefineTicketCount",
                    "200",
                    Some(NrbfDictKind::StringEncryptValue),
                );
                let _ = self.batch_patch_nrbf(
                    "exchangeablePieces",
                    &[
                        ("HP_HeroCommonPiece".into(), "500".into()),
                        ("SkinPiecesCount".into(), "500".into()),
                    ],
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                );
                let _ = self.patch_nrbf_entry(
                    "MiscArchive",
                    "Moye_Token",
                    "300",
                    Some(NrbfDictKind::StringEncryptValue),
                );
                Ok("Đã nạp Tiền tệ, Vé & Mảnh ở Mức An Toàn Chuẩn (500k Đá Hồn, 50k Đá Lam/Đỏ, 200 Vé, 500 Mảnh)".into())
            }
            "unlock_all_heroes" => {
                let mut cnt = 0usize;
                for &(body, _, _, _) in PLAYABLE_HEROES {
                    self.update_hero(body, Some(true), Some(15), Some(true), true, true)?;
                    cnt += 1;
                }
                Ok(format!(
                    "Đã mở khóa {cnt} nhân vật (Lv.15 Max + Kỹ năng phụ + Full Kỹ năng & Phong cách)"
                ))
            }
            "unlock_all_skins" => {
                let mut skin_updates: Vec<(String, String)> = HERO_SKINS_ALL
                    .iter()
                    .map(|s| ((*s).to_string(), "1".to_string()))
                    .collect();
                if let Some(list) = extra_skins {
                    for s in list {
                        if !HERO_SKINS_ALL.contains(&s.as_str()) {
                            skin_updates.push((s.clone(), "1".to_string()));
                        }
                    }
                }
                let mut skin_changed = self.set_all_existing_nrbf("HeroSkin", "1").unwrap_or(0);
                skin_changed += self
                    .batch_patch_nrbf(
                        "HeroSkin",
                        &skin_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                let power_updates: Vec<(String, String)> = HERO_SKIN_POWERS_ALL
                    .iter()
                    .map(|s| ((*s).to_string(), "1".to_string()))
                    .collect();
                let power_changed = self
                    .batch_patch_nrbf(
                        "HeroSkinPower",
                        &power_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                let menu_updates: Vec<(String, String)> = MENU_SKIN_TYPES
                    .iter()
                    .map(|(id, _, _)| (id.to_string(), "1".to_string()))
                    .collect();
                let menu_changed = self
                    .batch_patch_nrbf(
                        "MenuSkinUnlock",
                        &menu_updates,
                        true,
                        Some(NrbfDictKind::EnumEncryptValue {
                            key_enum: "MenuSkinType".to_string(),
                        }),
                    )
                    .unwrap_or(0);

                let npc_updates: Vec<(String, String)> = NPC_SKINS_ALL
                    .iter()
                    .map(|s| ((*s).to_string(), "1".to_string()))
                    .collect();
                let npc_changed = self
                    .batch_patch_nrbf(
                        "NPCSkin",
                        &npc_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                let floor_changed = self.set_all_existing_nrbf("FloorSkin", "1").unwrap_or(0);
                let floor_pieces: Vec<(String, String)> = FLOOR_SKIN_PIECES_ALL
                    .iter()
                    .map(|s| ((*s).to_string(), "99".to_string()))
                    .collect();
                let piece_changed = self
                    .batch_patch_nrbf(
                        "FloorSkinPiece",
                        &floor_pieces,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                Ok(format!(
                    "Đã mở Full Skin (+{skin_changed} Skin Tướng, +{power_changed} Skin Power, +{menu_changed} Sảnh, +{npc_changed} NPC, +{floor_changed} Sàn, +{piece_changed} Mảnh Sàn x99)"
                ))
            }
            "max_asura" => {
                // Safe natural Asura Power: 12,000 Max total = 6 stats x 2,000 (~+96% each via Pow(2000,0.6)*0.01)
                // Keeps sum(invest) == AsuraPowerPointMax strictly consistent!
                self.set_encrypt_int_pair("AsuraPowerPointMax", 12_000)?;
                self.set_encrypt_int_pair("AsuraPowerPointSoulStoneCount", 1_000)?;
                let updates: Vec<(String, String)> = ASURA_STATS
                    .iter()
                    .map(|(id, _, _)| (id.to_string(), "2000".to_string()))
                    .collect();
                self.batch_patch_nrbf(
                    "EXAsuraPowerPointInvest",
                    &updates,
                    true,
                    Some(NrbfDictKind::IntEncryptValue),
                )?;
                let cur_scheme = self
                    .read_nrbf_dict("AsuraPowerConfig")
                    .ok()
                    .and_then(|d| d.get_i32("current_scheme"))
                    .unwrap_or(1)
                    .max(1);
                let existing_count = self
                    .read_nrbf_dict("AsuraPowerConfig")
                    .ok()
                    .and_then(|d| d.get_i32("scheme_count"))
                    .unwrap_or(6)
                    .max(6);
                let mut cfg_updates = vec![
                    ("current_scheme".to_string(), cur_scheme.to_string()),
                    ("scheme_count".to_string(), existing_count.to_string()),
                ];
                for stat_idx in 0..6 {
                    cfg_updates.push((format!("scheme_{cur_scheme}_{stat_idx}"), "2000".to_string()));
                }
                let _ = self.batch_patch_nrbf(
                    "AsuraPowerConfig",
                    &cfg_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                );
                Ok(format!(
                    "Đã nâng Sức mạnh Tu La An Toàn (12.000 Max = 6 × 2.000 điểm, khớp 100% bảng Tu La #{cur_scheme})"
                ))
            }
            "max_naga_six_realms" => {
                // 1. All 40 NagaPower Chakra branches -> Level 5 (preserving seeds & ExtraPropertyOptionIndex!)
                let naga_updates: Vec<(String, String)> = NAGA_POWERS_ALL
                    .iter()
                    .map(|p| (format!("{p}_Level"), "5".to_string()))
                    .collect();
                let naga_changed = self.batch_patch_nrbf(
                    "NagaPower",
                    &naga_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;

                // 2. All 37 SixCyclePower passives unlocked
                let six_updates: Vec<(String, String)> = SIX_CYCLE_POWERS_ALL
                    .iter()
                    .map(|p| ((*p).to_string(), "1".to_string()))
                    .collect();
                let six_changed = self.batch_patch_nrbf(
                    "SixCyclePower",
                    &six_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;

                // 3. Chaos Mode, Imba Mode, 6 Realms First-Pass flags in Config
                let cfg_updates = vec![
                    ("SixCycleChaosUnlock".into(), "1".into()),
                    ("SixCycleChaosLevel".into(), "1".into()),
                    ("SixCycleType".into(), "7".into()),
                    ("SixCycleRen_FP".into(), "1".into()),
                    ("SixCycleShou_FP".into(), "1".into()),
                    ("SixCycleGui_FP".into(), "1".into()),
                    ("SixCycleYu_FP".into(), "1".into()),
                    ("SixCycleXiuLuo_FP".into(), "1".into()),
                    ("SixCycleTianRen_FP".into(), "1".into()),
                    ("ExperimentalGameplayArchive.ImbaMode.toggleOn".into(), "1".into()),
                    ("SpecialMode".into(), "3".into()),
                ];
                let _ = self.batch_patch_nrbf(
                    "Config",
                    &cfg_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                );

                // 4. Max difficulty (Real Mirage / Pandemonium 14) + safe Orb tickets (200)
                self.set_encrypted_plain("difficultLevel", "14")?;
                self.set_encrypted_plain("difficultLevelUnlockLevel", "14")?;
                let _ = self.patch_nrbf_entry(
                    "SixCycleOrb",
                    "OrbRefineTicketCount",
                    "200",
                    Some(NrbfDictKind::StringEncryptValue),
                );
                let _ = self.patch_nrbf_entry(
                    "GameData",
                    "ESRIdentifyTicketCount",
                    "200",
                    Some(NrbfDictKind::StringEncryptValue),
                );
                let _ = self.patch_nrbf_entry(
                    "GameData",
                    "SIX_CYCLE_MODE_PROCESS",
                    "7",
                    Some(NrbfDictKind::StringEncryptValue),
                );

                Ok(format!(
                    "Đã nâng Max 40 nhánh Thần Lực Naga (+{naga_changed}), mở 37 Sức mạnh Lục Đạo (+{six_changed}), Chế độ Hỗn Độn, Imba, Độ khó Lv.14 & 200 Vé Lục Đạo"
                ))
            }
            "unlock_pets_garden" => {
                // 1. All 32 pets unlocked + Friendliness 100 + Satiety 100 + 8 AutoPick + safe Food/Toy 300
                let mut pet_updates: Vec<(String, String)> = Vec::with_capacity(PETS_ALL.len() * 3 + 12);
                pet_updates.push(("PP_SystemUnlock".into(), "1".into()));
                pet_updates.push(("PP_FoodCount".into(), "300".into()));
                pet_updates.push(("PP_ToyCount".into(), "300".into()));
                for &pet in PETS_ALL {
                    pet_updates.push((format!("PP_PetUnlock_{pet}"), "1".into()));
                    pet_updates.push((format!("PP_PetFriendliness_{pet}"), "100".into()));
                    pet_updates.push((format!("PP_PetSatiety_{pet}"), "100".into()));
                }
                for &unit in AUTO_PICK_UNITS_ALL {
                    pet_updates.push((format!("PP_AutoPickConfig_{unit}"), "1".into()));
                }
                let pet_changed = self.batch_patch_nrbf(
                    "petParadise",
                    &pet_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;

                // 2. Garden system + all 9 flower beds (0..=8) + safe Fertilizer 200
                let mut garden_updates: Vec<(String, String)> = vec![("Garden_systemUnlock".into(), "1".into())];
                for plot in 0..=8 {
                    garden_updates.push((format!("G_FB_{plot}_isUnlock"), "1".into()));
                }
                let garden_changed = self.batch_patch_nrbf(
                    "Garden",
                    &garden_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;
                self.set_encrypt_int_pair("Fertilizer", 200)?;

                Ok(format!(
                    "Đã mở Full 32 Thú cưng (Thân thiện/No 100 + Tự động nhặt + 300 Thức ăn/Đồ chơi, +{pet_changed}) & 9 Ô Vườn (+{garden_changed})"
                ))
            }
            "unlock_battlepass_lobby" => {
                // 1. BattlePass: safely unlock Premium High Reward + Level 29 ONLY for existing seasons in save
                // (or current season BP26 if empty). Never invents fake seasons & leaves claim flags intact!
                let mut bp_seasons: Vec<i32> = Vec::new();
                let mut current_bp_total = 185;
                if let Ok(bp_dict) = self.read_nrbf_dict("battlePass") {
                    for e in &bp_dict.entries {
                        if let Some(rest) = e.key.strip_prefix("BP") {
                            if let Some((num_s, _)) = rest.split_once('_') {
                                if let Ok(n) = num_s.parse::<i32>() {
                                    if !bp_seasons.contains(&n) {
                                        bp_seasons.push(n);
                                    }
                                }
                            }
                        }
                    }
                    if let Some(lv) = bp_dict.get_i32("BattlePassLevelKey") {
                        current_bp_total = lv.max(185);
                    }
                }
                if bp_seasons.is_empty() {
                    bp_seasons.push(26);
                }
                bp_seasons.sort_unstable();
                let mut bp_updates: Vec<(String, String)> = vec![
                    ("BattlePassLevelKey".into(), current_bp_total.to_string()),
                ];
                for s in &bp_seasons {
                    bp_updates.push((format!("BP{s}_isBuyHighReward"), "1".into()));
                    bp_updates.push((format!("BP{s}_currentLevel"), "29".into()));
                }
                let bp_changed = self.batch_patch_nrbf(
                    "battlePass",
                    &bp_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;

                // 2. BuildCat (11 lobby facilities)
                let mut build_updates: Vec<(String, String)> = Vec::new();
                for &proj in BUILD_CAT_PROJECTS_ALL {
                    build_updates.push((format!("{proj}_IA"), "1".into()));
                    build_updates.push((format!("{proj}_IU"), "1".into()));
                    build_updates.push((format!("{proj}_FCD"), "1".into()));
                }
                let build_changed = self.batch_patch_nrbf(
                    "BuildCat",
                    &build_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;

                // 3. Travel (26 lobby & gameplay features + extras)
                let mut travel_updates: Vec<(String, String)> = TRAVEL_UNLOCKS_ALL
                    .iter()
                    .map(|t| (format!("{t}_IU"), "1".into()))
                    .collect();
                for extra in [
                    "NormalHeroCyclePower_IU",
                    "ExtraHeroCyclePower_IU",
                    "ItemMerge_IU",
                    "WeaponBank_IU",
                    "RougeMode_IU",
                ] {
                    travel_updates.push((extra.into(), "1".into()));
                }
                let travel_changed = self.batch_patch_nrbf(
                    "Travel",
                    &travel_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;

                // 4. Ban slots (all 31 ban slots + 9 schemes — verified BookrackBanCESlotArchive.MaxBanSchemeCount = 9)
                let mut ban_unlocks: Vec<(String, String)> = vec![("TransitBanSlotArchiveFlag".into(), "1".into())];
                for i in 0..=30 {
                    ban_unlocks.push((format!("BanSlotID_{i}"), "1".into()));
                }
                let ban_changed = self.batch_patch_nrbf(
                    "LocatableBanSlotUnlockState",
                    &ban_unlocks,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;
                let _ = self.batch_patch_nrbf(
                    "BanCharacterEffectSlot",
                    &[
                        ("BanSlotCount".into(), "31".into()),
                        ("BanSchemeCount".into(), "9".into()),
                    ],
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                );

                // 5. LevelPortal, Bank (safe ATM cap = 200), AdventureLevel 30, AchievementRoomLevel = 7 (CurrentMaxLevel!)
                let _ = self.patch_nrbf_entry(
                    "LevelPortal",
                    "LevelPortalSystemUnlock",
                    "1",
                    Some(NrbfDictKind::StringEncryptValue),
                );
                let _ = self.batch_patch_nrbf(
                    "Bank",
                    &[("systemUnlock".into(), "1".into()), ("savedCoinCount".into(), "200".into())],
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                );
                let _ = self.patch_nrbf_entry(
                    "AdventureLevel",
                    "AdventureLevelLevelArchiveKey",
                    "30",
                    Some(NrbfDictKind::StringEncryptValue),
                );
                self.set_encrypt_int_pair("AchievementRoomLevel", 7)?;
                self.set_encrypted_plain("HasBuyBarVipCard", "1")?;
                self.set_encrypted_plain("HasBeatStoreBoss", "1")?;
                self.set_encrypted_plain("HasBuyNoviePack", "1")?;
                self.set_encrypted_plain("HasBuyNoviePack_2", "1")?;

                let drink_updates: Vec<(String, String)> = BAR_DRINKS_ALL
                    .iter()
                    .map(|d| ((*d).to_string(), "4".into()))
                    .collect();
                let _ = self.batch_patch_nrbf(
                    "Config",
                    &drink_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                );
                let _ = self.batch_patch_nrbf(
                    "MiscArchive",
                    &[
                        ("CanOpenRottenLandBossPortalDoor".into(), "1".into()),
                        ("UnlockedDifficultyLevelInHalloweenMode".into(), "2".into()),
                    ],
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                );

                Ok(format!(
                    "Đã mở Chiến Lệnh Cao Cấp (+{bp_changed}), 11 Công trình (+{build_changed}), 26 Tính năng Sảnh (+{travel_changed}), 31 Ô Cấm (+{ban_changed}), Phòng Thành Tựu Lv.7 (Max thật), Bank 200 & Cấp Phiêu Lưu 30"
                ))
            }
            "max_materials_pets" => {
                // 1. All 49 Bar Materials (100..=148) -> safe 200
                let bar_updates: Vec<(String, String)> = BAR_MATERIAL_IDS
                    .map(|id| (id.to_string(), "200".to_string()))
                    .collect();
                let mat_changed = self
                    .batch_patch_nrbf(
                        "EXBarmaterials",
                        &bar_updates,
                        true,
                        Some(NrbfDictKind::IntEncryptValue),
                    )
                    .unwrap_or(0);

                // 2. All 14 SharedSkillMaterials -> safe 99
                let ssm_updates: Vec<(String, String)> = SHARED_SKILL_MATERIALS_ALL
                    .iter()
                    .map(|k| ((*k).to_string(), "99".to_string()))
                    .collect();
                let ssm_changed = self
                    .batch_patch_nrbf(
                        "SharedSkillMaterial",
                        &ssm_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                // 3. Exchangeable pieces — SAFE update (never touches HP_DAPCDOY / HP_DAPCY!)
                let mut piece_updates: Vec<(String, String)> = vec![
                    ("HP_HeroCommonPiece".into(), "500".into()),
                    ("SkinPiecesCount".into(), "500".into()),
                ];
                if let Ok(ex_dict) = self.read_nrbf_dict("exchangeablePieces") {
                    for e in &ex_dict.entries {
                        if e.key.starts_with("HP_HeroPiece_") {
                            piece_updates.push((e.key.clone(), "50".into()));
                        }
                    }
                }
                let piece_changed = self
                    .batch_patch_nrbf(
                        "exchangeablePieces",
                        &piece_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                // 4. Pet food/toy + Moye Token -> safe 300
                let pet_changed = self
                    .batch_patch_nrbf(
                        "petParadise",
                        &[("PP_FoodCount".into(), "300".into()), ("PP_ToyCount".into(), "300".into())],
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);
                let _ = self.patch_nrbf_entry(
                    "MiscArchive",
                    "Moye_Token",
                    "300",
                    Some(NrbfDictKind::StringEncryptValue),
                );

                Ok(format!(
                    "Đã nạp an toàn 49 NL Bar x200 (+{mat_changed}), 14 NL Kỹ năng chung x99 (+{ssm_changed}), Mảnh đổi x500 (+{piece_changed}), Đồ Pet x300 (+{pet_changed}) & Xu Ma Dạ x300"
                ))
            }
            "unlock_statues_albums" => {
                let statue_updates: Vec<(String, String)> = STATUES_ALL
                    .iter()
                    .map(|s| ((*s).to_string(), "1".to_string()))
                    .collect();
                let statue_changed = self.batch_patch_nrbf(
                    "AchievementRoomStatue",
                    &statue_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;

                let album_updates: Vec<(String, String)> = ALBUMS_ALL
                    .iter()
                    .map(|a| ((*a).to_string(), "1".to_string()))
                    .collect();
                let album_changed = self.batch_patch_nrbf(
                    "Album",
                    &album_updates,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;

                let floor_pieces: Vec<(String, String)> = FLOOR_SKIN_PIECES_ALL
                    .iter()
                    .map(|s| ((*s).to_string(), "99".to_string()))
                    .collect();
                let piece_changed = self.batch_patch_nrbf(
                    "FloorSkinPiece",
                    &floor_pieces,
                    true,
                    Some(NrbfDictKind::StringEncryptValue),
                )?;

                Ok(format!(
                    "Đã mở đủ 122 Tượng Phòng Thành Tựu (+{statue_changed}), 42 Đĩa Nhạc BGM (+{album_changed}) & 33 Mảnh Sàn x99 (+{piece_changed})"
                ))
            }
            "unlock_all_items_bestiary" => {
                let mut item_updates: Vec<(String, String)> = ITEMS_ALL
                    .iter()
                    .map(|s| ((*s).to_string(), "1".to_string()))
                    .collect();
                if let Some(list) = extra_items {
                    for s in list {
                        if !ITEMS_ALL.contains(&s.as_str()) {
                            item_updates.push((s.clone(), "1".to_string()));
                        }
                    }
                }
                let mut items_changed = self
                    .set_all_existing_nrbf("illustrated_characterEffect", "1")
                    .unwrap_or(0);
                items_changed += self
                    .batch_patch_nrbf(
                        "illustrated_characterEffect",
                        &item_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                let mon_changed = self
                    .set_all_existing_nrbf("illustrated_monster", "100")
                    .unwrap_or(0);
                let weap_changed = self
                    .set_all_existing_nrbf("illustrated_weapon", "1")
                    .unwrap_or(0);

                let shared_updates: Vec<(String, String)> = SHARED_SKILLS_ALL
                    .iter()
                    .map(|s| ((*s).to_string(), "1".to_string()))
                    .collect();
                let shared_changed = self
                    .batch_patch_nrbf(
                        "SharedSkill",
                        &shared_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                let album_updates: Vec<(String, String)> = ALBUMS_ALL
                    .iter()
                    .map(|a| ((*a).to_string(), "1".to_string()))
                    .collect();
                let album_changed = self
                    .batch_patch_nrbf(
                        "Album",
                        &album_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                let statue_updates: Vec<(String, String)> = STATUES_ALL
                    .iter()
                    .map(|s| ((*s).to_string(), "1".to_string()))
                    .collect();
                let statue_changed = self
                    .batch_patch_nrbf(
                        "AchievementRoomStatue",
                        &statue_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                let pack_updates: Vec<(String, String)> = ITEM_PACKS_ALL
                    .iter()
                    .map(|p| ((*p).to_string(), "840".to_string()))
                    .collect();
                let _ = self.batch_patch_nrbf(
                    "itemPack",
                    &pack_updates,
                    false,
                    Some(NrbfDictKind::StringEncryptValue),
                );
                let _ = self.patch_nrbf_entry(
                    "itemPack",
                    "completeCount",
                    "840",
                    Some(NrbfDictKind::StringEncryptValue),
                );

                let story_updates: Vec<(String, String)> = STORY_CHAPTERS_ALL
                    .iter()
                    .map(|c| ((*c).to_string(), "51".to_string()))
                    .collect();
                let _ = self.batch_patch_nrbf(
                    "storyChapter",
                    &story_updates,
                    false,
                    Some(NrbfDictKind::StringEncryptValue),
                );

                let affix_updates: Vec<(String, String)> = CE_AFFIXES_ALL
                    .iter()
                    .map(|a| ((*a).to_string(), "15".to_string()))
                    .collect();
                let affix_changed = self
                    .batch_patch_nrbf(
                        "ceAffixArchive",
                        &affix_updates,
                        true,
                        Some(NrbfDictKind::StringEncryptValue),
                    )
                    .unwrap_or(0);

                self.set_encrypted_plain("HasBuyBarVipCard", "1")?;
                self.set_encrypted_plain("HasBeatStoreBoss", "1")?;
                Ok(format!(
                    "Đã mở 401 Vật phẩm (+{items_changed}), Quái (+{mon_changed}), Vũ khí (+{weap_changed}), 14 Kỹ năng chung (+{shared_changed}), 42 Đĩa nhạc (+{album_changed}), 122 Tượng (+{statue_changed}) & 22 Phụ tố (+{affix_changed})"
                ))
            }
            "unlock_all_achievements" => self.unlock_all_achievements(false),
            "unlock_all_achievements_claimed" => self.unlock_all_achievements(true),
            "normalize_safe_caps" => self.normalize_safe_caps(),
            "full_safe_bundle" => {
                self.apply_preset("safe_currencies", extra_skins, extra_items)?;
                self.apply_preset("unlock_all_heroes", extra_skins, extra_items)?;
                self.apply_preset("unlock_all_skins", extra_skins, extra_items)?;
                self.apply_preset("max_asura", extra_skins, extra_items)?;
                self.apply_preset("max_naga_six_realms", extra_skins, extra_items)?;
                self.apply_preset("unlock_pets_garden", extra_skins, extra_items)?;
                self.apply_preset("unlock_battlepass_lobby", extra_skins, extra_items)?;
                self.apply_preset("max_materials_pets", extra_skins, extra_items)?;
                self.apply_preset("unlock_all_items_bestiary", extra_skins, extra_items)?;
                self.unlock_all_achievements(false)?;
                self.normalize_safe_caps()?;
                Ok("Đã áp dụng trọn bộ Full Safe Bundle v3.8.0 (10 nhóm tài nguyên An Toàn + Mở tất cả Thành tựu)!".into())
            }
            other => Err(OwlError::Validation(format!("Unknown preset_id '{other}'"))),
        }
    }

    /// Collects all known achievement IDs: the 236 verified `Achievement.AchievementID` entries
    /// PLUS any additional `pp_achievement_<name>_isComplete` keys already present in this save
    /// (forward-compatible with newer game versions!).
    fn collect_achievement_names(&self) -> Vec<String> {
        let mut names: BTreeSet<String> = ACHIEVEMENT_IDS_ALL
            .iter()
            .map(|s| (*s).to_string())
            .collect();
        for entry in &self.doc.entries {
            if let Some(meta) = self.dict.lookup(&entry.raw_key) {
                if let Some(rest) = meta.plain_key.strip_prefix("pp_achievement_") {
                    if let Some(ach_name) = rest.strip_suffix("_isComplete") {
                        if !ach_name.is_empty() && ach_name != "Empty" {
                            names.insert(ach_name.to_string());
                        }
                    }
                }
            }
        }
        names.into_iter().collect()
    }

    /// Unlocks all achievements matching `Achievement.PlayerPrefsDataLoader` format:
    /// - Preserves original `completeTime` / `awardTime` / `currentProgress` for already completed achievements.
    /// - When `claim_rewards == false` (recommended), newly completed achievements have `hasReceiveAward = 0`
    ///   and `awardTime = -1` so the player can tap "Có thể lấy được" in-game to claim rewards & points naturally!
    pub fn unlock_all_achievements(&mut self, claim_rewards: bool) -> OwlResult<String> {
        let now_ts = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs().min(i32::MAX as u64) as i32)
            .unwrap_or(1_767_225_600);
        let now_str = now_ts.to_string();

        let names = self.collect_achievement_names();
        let mut newly_completed = 0usize;
        let mut newly_claimed = 0usize;

        for name in &names {
            let k_comp = format!("pp_achievement_{name}_isComplete");
            let k_prog = format!("pp_achievement_{name}_currentProgress");
            let k_ctime = format!("pp_achievement_{name}_completeTime");
            let k_recv = format!("pp_achievement_{name}_hasReceiveAward");
            let k_atime = format!("pp_achievement_{name}_awardTime");

            let already_comp = self
                .get_encrypted_plain(&k_comp)
                .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
                .unwrap_or(false);

            if !already_comp {
                self.set_encrypted_plain(&k_comp, "1")?;
                let cur_prog = self
                    .get_encrypted_plain(&k_prog)
                    .and_then(|v| v.parse::<i32>().ok())
                    .unwrap_or(0);
                if cur_prog <= 0 {
                    self.set_encrypted_plain(&k_prog, "1")?;
                }
                let cur_ct = self
                    .get_encrypted_plain(&k_ctime)
                    .and_then(|v| v.parse::<i32>().ok())
                    .unwrap_or(-1);
                if cur_ct <= 0 {
                    self.set_encrypted_plain(&k_ctime, &now_str)?;
                }
                if claim_rewards {
                    self.set_encrypted_plain(&k_recv, "1")?;
                    self.set_encrypted_plain(&k_atime, &now_str)?;
                    newly_claimed += 1;
                } else {
                    self.set_encrypted_plain(&k_recv, "0")?;
                    self.set_encrypted_plain(&k_atime, "-1")?;
                }
                newly_completed += 1;
            } else if claim_rewards {
                let already_recv = self
                    .get_encrypted_plain(&k_recv)
                    .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
                    .unwrap_or(false);
                if !already_recv {
                    self.set_encrypted_plain(&k_recv, "1")?;
                    let cur_at = self
                        .get_encrypted_plain(&k_atime)
                        .and_then(|v| v.parse::<i32>().ok())
                        .unwrap_or(-1);
                    if cur_at <= 0 {
                        self.set_encrypted_plain(&k_atime, &now_str)?;
                    }
                    newly_claimed += 1;
                }
            }
        }

        if claim_rewards {
            Ok(format!(
                "Đã hoàn thành & nhận thưởng toàn bộ {total} Thành tựu (+{newly_completed} mới mở, +{newly_claimed} mới nhận — giữ nguyên ngày cũ)",
                total = names.len()
            ))
        } else {
            Ok(format!(
                "Đã mở hoàn thành toàn bộ {total} Thành tựu (+{newly_completed} mới mở — vào game bấm 'Có thể lấy được' để nhận thưởng!)",
                total = names.len()
            ))
        }
    }

    /// Audits every resource in the save and clamps any over-cap / abnormal values down to verified
    /// safe caps derived from `GameLogic.dll` v3.8.0 (e.g. AchievementRoomLevel<=7, Bank<=200,
    /// SoulStone<=500k, Blue/Red<=50k, AsuraMax<=12k & sum(invest)<=Max, HeroLevel<=15, etc.).
    pub fn normalize_safe_caps(&mut self) -> OwlResult<String> {
        let mut clamped = 0usize;

        // 1. Scalar EncryptValue pairs
        let scalar_caps: &[(&str, i32)] = &[
            ("SoulStoneCount", 500_000),
            ("BlueStoneCount", 50_000),
            ("RedStoneCount", 50_000),
            ("WeaponUpgradeTicket", 200),
            ("RebornCard", 200),
            ("Fertilizer", 200),
            ("ProbationTicket", 99),
            ("AsuraPowerPointMax", 12_000),
            ("AsuraPowerPointSoulStoneCount", 1_000),
            ("AchievementRoomLevel", 7),
        ];
        for &(key, cap) in scalar_caps {
            let (val, _, exists) = self.get_encrypt_int_pair(key);
            if exists && val > cap {
                self.set_encrypt_int_pair(key, cap)?;
                clamped += 1;
            }
        }

        // 2. Asura Power investments must never exceed AsuraPowerPointMax!
        let asura_max = self.get_encrypt_int_pair("AsuraPowerPointMax").0.max(0);
        let per_stat_cap = (asura_max / 6).max(0);
        if let Ok(mut inv) = self.read_nrbf_dict("EXAsuraPowerPointInvest") {
            let sum: i64 = inv
                .entries
                .iter()
                .map(|e| e.value.parse::<i64>().unwrap_or(0).max(0))
                .sum();
            if sum > asura_max as i64 {
                for e in &mut inv.entries {
                    e.value = per_stat_cap.to_string();
                }
                inv.version = inv.version.saturating_add(1);
                let _ = self.write_nrbf_dict("EXAsuraPowerPointInvest", &inv);
                clamped += 1;
            }
        }
        if let Ok(mut cfg) = self.read_nrbf_dict("AsuraPowerConfig") {
            let mut touched = false;
            for scheme in 1..=12 {
                let mut sum: i64 = 0;
                let mut has_any = false;
                for stat in 0..6 {
                    if let Some(v) = cfg.get_i32(&format!("scheme_{scheme}_{stat}")) {
                        has_any = true;
                        sum += v.max(0) as i64;
                    }
                }
                if has_any && sum > asura_max as i64 {
                    // Proportionally scale down scheme so sum <= asura_max
                    for stat in 0..6 {
                        let k = format!("scheme_{scheme}_{stat}");
                        if let Some(v) = cfg.get_i32(&k) {
                            let scaled = ((v.max(0) as i64) * (asura_max as i64) / sum) as i32;
                            cfg.set(k, scaled.to_string());
                            touched = true;
                        }
                    }
                }
            }
            if touched {
                let _ = self.write_nrbf_dict("AsuraPowerConfig", &cfg);
                clamped += 1;
            }
        }

        // 3. NRBF container safe caps
        let mut clamp_nrbf_key = |container: &str, key: &str, max_v: i32| {
            if let Ok(d) = self.read_nrbf_dict(container) {
                if let Some(cur) = d.get_i32(key) {
                    if cur > max_v {
                        if self.patch_nrbf_entry(container, key, &max_v.to_string(), None).is_ok() {
                            clamped += 1;
                        }
                    }
                }
            }
        };
        clamp_nrbf_key("Bank", "savedCoinCount", 200);
        clamp_nrbf_key("AdventureLevel", "AdventureLevelLevelArchiveKey", 30);
        clamp_nrbf_key("SixCycleOrb", "OrbRefineTicketCount", 200);
        clamp_nrbf_key("GameData", "ESRIdentifyTicketCount", 200);
        clamp_nrbf_key("exchangeablePieces", "HP_HeroCommonPiece", 500);
        clamp_nrbf_key("exchangeablePieces", "SkinPiecesCount", 500);
        clamp_nrbf_key("petParadise", "PP_FoodCount", 300);
        clamp_nrbf_key("petParadise", "PP_ToyCount", 300);
        clamp_nrbf_key("MiscArchive", "Moye_Token", 300);

        // Clamp hero pieces, bar materials, shared skill materials, floor pieces, affixes, naga power
        for (container, prefix_filter, cap) in [
            ("EXBarmaterials", "", 200),
            ("SharedSkillMaterial", "", 99),
            ("FloorSkinPiece", "", 99),
            ("exchangeablePieces", "HP_HeroPiece_", 50),
        ] {
            if let Ok(mut d) = self.read_nrbf_dict(container) {
                let mut touched = false;
                for e in &mut d.entries {
                    if !prefix_filter.is_empty() && !e.key.starts_with(prefix_filter) {
                        continue;
                    }
                    if let Ok(v) = e.value.parse::<i32>() {
                        if v > cap {
                            e.value = cap.to_string();
                            touched = true;
                        }
                    }
                }
                if touched {
                    d.version = d.version.saturating_add(1);
                    let _ = self.write_nrbf_dict(container, &d);
                    clamped += 1;
                }
            }
        }

        // Clamp hero levels to real v3.8.0 cap (1..=15) & difficulty to 14
        for &(body, _, _, _) in PLAYABLE_HEROES {
            let k = format!("{body}level");
            if let Some(lv) = self.get_encrypted_plain(&k).and_then(|s| s.parse::<i32>().ok()) {
                if lv > 15 {
                    self.set_encrypted_plain(&k, "15")?;
                    clamped += 1;
                }
            }
        }
        for dk in ["difficultLevel", "difficultLevelUnlockLevel"] {
            if let Some(lv) = self.get_encrypted_plain(dk).and_then(|s| s.parse::<i32>().ok()) {
                if lv > 14 {
                    self.set_encrypted_plain(dk, "14")?;
                    clamped += 1;
                }
            }
        }

        Ok(format!(
            "Đã kiểm tra & chuẩn hóa toàn bộ tài nguyên về Mức An Toàn Chuẩn (đã hạ {clamped} hạng mục vượt ngưỡng)!"
        ))
    }

    /// Builds the full dashboard summary for the Visual Editor & Home screens.
    pub fn build_dashboard(&self) -> OwlResult<DashboardSummary> {
        let account = find_account_info(&self.doc, &[])?;

        let mut encrypted_entries = 0usize;
        let mut resolved_keys = 0usize;
        let mut nrbf_containers = 0usize;
        let mut json_entries = 0usize;
        let mut containers = Vec::new();

        for entry in &self.doc.entries {
            if entry.value_type == PrefValueType::String && is_md5_hex(&entry.raw_key) {
                encrypted_entries += 1;
                if self.dict.lookup(&entry.raw_key).is_some() {
                    resolved_keys += 1;
                }
                if let Some(plain) = self.decrypted_cache.get(&entry.raw_key) {
                    if NrbfDict::is_nrbf_b64(plain) {
                        nrbf_containers += 1;
                        let (display, cat) = self.display_key(&entry.raw_key);
                        match NrbfDict::parse_b64(plain) {
                            Ok(d) => {
                                containers.push(ContainerSummary {
                                    plain_key: display,
                                    md5_key: entry.raw_key.clone(),
                                    category: cat,
                                    entry_count: d.entries.len(),
                                    editable: true,
                                    kind_label: format!("{:?}", d.kind),
                                });
                            }
                            Err(_) => {
                                containers.push(ContainerSummary {
                                    plain_key: display,
                                    md5_key: entry.raw_key.clone(),
                                    category: cat,
                                    entry_count: 0,
                                    editable: false,
                                    kind_label: "CustomNRBF".to_string(),
                                });
                            }
                        }
                    } else if plain.starts_with('{') || plain.starts_with('[') {
                        json_entries += 1;
                    }
                }
            }
        }
        containers.sort_by(|a, b| b.entry_count.cmp(&a.entry_count).then(a.plain_key.cmp(&b.plain_key)));

        // Currencies & EncryptValue pairs
        let currencies: Vec<CurrencyStatus> = ENCRYPT_INT_PAIRS
            .iter()
            .map(|&(key, label, safe_max, cat)| {
                let (val, enc_key, exists) = self.get_encrypt_int_pair(key);
                let modified = self.is_modified(&md5_key(key))
                    || self.is_modified(&md5_key(&format!("{key}Key")));
                CurrencyStatus {
                    key: key.to_string(),
                    label: label.to_string(),
                    category: cat.to_string(),
                    value: val,
                    encrypt_key: enc_key,
                    safe_max,
                    exists,
                    modified,
                }
            })
            .collect();

        // Asura invested stats
        let asura_dict = self.read_nrbf_dict("EXAsuraPowerPointInvest").ok();
        let asura_stats: Vec<AsuraStatStatus> = ASURA_STATS
            .iter()
            .map(|&(id, code, label)| {
                let val = asura_dict
                    .as_ref()
                    .and_then(|d| d.get_i32(&id.to_string()))
                    .unwrap_or(0);
                AsuraStatStatus {
                    stat_id: id,
                    code: code.to_string(),
                    label: label.to_string(),
                    value: val,
                }
            })
            .collect();

        // Menu skins
        let menu_dict = self.read_nrbf_dict("MenuSkinUnlock").ok();
        let menu_skins: Vec<MenuSkinStatus> = MENU_SKIN_TYPES
            .iter()
            .map(|&(id, code, label)| {
                let unlocked = menu_dict
                    .as_ref()
                    .and_then(|d| d.get_i32(&id.to_string()))
                    .map(|v| v > 0)
                    .unwrap_or(id == 0);
                MenuSkinStatus {
                    id,
                    code: code.to_string(),
                    label: label.to_string(),
                    unlocked,
                }
            })
            .collect();

        // Heroes
        let equip_skin_dict = self.read_nrbf_dict("EXHeroSkinEquip").ok();
        let heroes: Vec<HeroStatus> = PLAYABLE_HEROES
            .iter()
            .map(|&(body, body_id, name_en, name_vi)| {
                let unlock_raw = self.get_encrypted_plain(&format!("{body}isUnlock"));
                let exists = unlock_raw.is_some();
                let unlocked = unlock_raw
                    .map(|s| s.eq_ignore_ascii_case("true") || s == "1")
                    .unwrap_or(false);
                let level = self
                    .get_encrypted_plain(&format!("{body}level"))
                    .and_then(|s| s.parse::<i32>().ok())
                    .unwrap_or(1);
                let second_skill_unlocked = self
                    .get_encrypted_plain(&format!("{body}isUnlockSecondSkill"))
                    .map(|s| s == "1" || s.eq_ignore_ascii_case("true"))
                    .unwrap_or(false);
                let equipped_skin = equip_skin_dict
                    .as_ref()
                    .and_then(|d| d.get(&body_id.to_string()))
                    .unwrap_or(body)
                    .to_string();

                let (skills_unlocked, skills_total) =
                    count_bool_json_map(self.get_encrypted_plain(&format!("{body}skillUnlockLibrary")));
                let (styles_unlocked, styles_total) =
                    count_bool_json_map(self.get_encrypted_plain(&format!("{body}styleUnlockLibrary")));

                HeroStatus {
                    body_name: body.to_string(),
                    body_id,
                    name_en: name_en.to_string(),
                    name_vi: name_vi.to_string(),
                    unlocked,
                    level,
                    second_skill_unlocked,
                    equipped_skin,
                    skills_unlocked,
                    skills_total,
                    styles_unlocked,
                    styles_total,
                    exists,
                }
            })
            .collect();

        // Helpful scalar flags
        let mut flags = BTreeMap::new();
        for fk in [
            "HasBuyBarVipCard",
            "HasBeatStoreBoss",
            "SixCycleChaosUnlock",
            "difficultLevel",
            "difficultLevelUnlockLevel",
            "CurrentSelectedPetName",
            "CurrentMenuSkin",
            "AdventureLevelLevelArchiveKey",
        ] {
            if let Some(v) = self.get_encrypted_plain(fk) {
                flags.insert(fk.to_string(), v.to_string());
            }
        }

        let unlock_overview = self.build_unlock_overview();

        Ok(DashboardSummary {
            account,
            total_entries: self.doc.len(),
            encrypted_entries,
            resolved_keys,
            nrbf_containers,
            json_entries,
            modified_count: self.modified_count(),
            currencies,
            asura_stats,
            menu_skins,
            heroes,
            containers,
            flags,
            unlock_overview,
        })
    }

    fn build_unlock_overview(&self) -> UnlockOverview {
        let count_positive = |dict: Option<&NrbfDict>, keys: &[&str]| -> usize {
            let Some(d) = dict else { return 0 };
            keys.iter()
                .filter(|k| d.get_i32(k).unwrap_or(0) > 0)
                .count()
        };

        let hero_skin_d = self.read_nrbf_dict("HeroSkin").ok();
        let hero_skin_power_d = self.read_nrbf_dict("HeroSkinPower").ok();
        let npc_skin_d = self.read_nrbf_dict("NPCSkin").ok();
        let pet_d = self.read_nrbf_dict("petParadise").ok();
        let naga_d = self.read_nrbf_dict("NagaPower").ok();
        let six_d = self.read_nrbf_dict("SixCyclePower").ok();
        let album_d = self.read_nrbf_dict("Album").ok();
        let statue_d = self.read_nrbf_dict("AchievementRoomStatue").ok();
        let shared_d = self.read_nrbf_dict("SharedSkill").ok();
        let item_d = self.read_nrbf_dict("illustrated_characterEffect").ok();
        let garden_d = self.read_nrbf_dict("Garden").ok();
        let ban_d = self.read_nrbf_dict("LocatableBanSlotUnlockState").ok();
        let build_d = self.read_nrbf_dict("BuildCat").ok();
        let travel_d = self.read_nrbf_dict("Travel").ok();
        let bp_d = self.read_nrbf_dict("battlePass").ok();
        let ex_d = self.read_nrbf_dict("exchangeablePieces").ok();
        let orb_d = self.read_nrbf_dict("SixCycleOrb").ok();
        let gd_d = self.read_nrbf_dict("GameData").ok();
        let cfg_d = self.read_nrbf_dict("Config").ok();
        let adv_d = self.read_nrbf_dict("AdventureLevel").ok();
        let misc_d = self.read_nrbf_dict("MiscArchive").ok();
        let bank_d = self.read_nrbf_dict("Bank").ok();
        let portal_d = self.read_nrbf_dict("LevelPortal").ok();

        let pets_unlocked = pet_d
            .as_ref()
            .map(|d| {
                PETS_ALL
                    .iter()
                    .filter(|p| d.get_i32(&format!("PP_PetUnlock_{p}")).unwrap_or(0) > 0)
                    .count()
            })
            .unwrap_or(0);

        let naga_powers_maxed = naga_d
            .as_ref()
            .map(|d| {
                NAGA_POWERS_ALL
                    .iter()
                    .filter(|p| d.get_i32(&format!("{p}_Level")).unwrap_or(0) >= 5)
                    .count()
            })
            .unwrap_or(0);

        let garden_plots_unlocked = garden_d
            .as_ref()
            .map(|d| {
                (0..=8)
                    .filter(|i| {
                        *i <= 1
                            && d.get_i32("Garden_systemUnlock").unwrap_or(0) > 0
                            || d.get_i32(&format!("G_FB_{i}_isUnlock")).unwrap_or(0) > 0
                    })
                    .count()
            })
            .unwrap_or(0);

        let ban_slots_unlocked = ban_d
            .as_ref()
            .map(|d| {
                (0..=30)
                    .filter(|i| d.get_i32(&format!("BanSlotID_{i}")).unwrap_or(0) > 0)
                    .count()
            })
            .unwrap_or(0);

        let build_projects_unlocked = build_d
            .as_ref()
            .map(|d| {
                BUILD_CAT_PROJECTS_ALL
                    .iter()
                    .filter(|p| d.get_i32(&format!("{p}_IA")).unwrap_or(0) > 0)
                    .count()
            })
            .unwrap_or(0);

        let travel_features_unlocked = travel_d
            .as_ref()
            .map(|d| {
                TRAVEL_UNLOCKS_ALL
                    .iter()
                    .filter(|t| d.get_i32(&format!("{t}_IU")).unwrap_or(0) > 0)
                    .count()
            })
            .unwrap_or(0);

        let mut bp_seasons = std::collections::BTreeSet::new();
        let mut bp_high = 0usize;
        if let Some(d) = bp_d.as_ref() {
            for e in &d.entries {
                if let Some(rest) = e.key.strip_prefix("BP") {
                    if let Some((num_s, field)) = rest.split_once('_') {
                        if let Ok(n) = num_s.parse::<i32>() {
                            bp_seasons.insert(n);
                            if field == "isBuyHighReward" && e.value.parse::<i32>().unwrap_or(0) > 0 {
                                bp_high += 1;
                            }
                        }
                    }
                }
            }
        }

        let difficulty_unlocked = self
            .get_encrypted_plain("difficultLevelUnlockLevel")
            .and_then(|s| s.parse::<i32>().ok())
            .unwrap_or(0);

        let bar_vip_unlocked = self
            .get_encrypted_plain("HasBuyBarVipCard")
            .map(|s| s == "1" || s.eq_ignore_ascii_case("true"))
            .unwrap_or(false);

        let ach_names = self.collect_achievement_names();
        let mut achievements_completed = 0usize;
        let mut achievements_claimed = 0usize;
        for name in &ach_names {
            if self
                .get_encrypted_plain(&format!("pp_achievement_{name}_isComplete"))
                .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
                .unwrap_or(false)
            {
                achievements_completed += 1;
            }
            if self
                .get_encrypted_plain(&format!("pp_achievement_{name}_hasReceiveAward"))
                .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
                .unwrap_or(false)
            {
                achievements_claimed += 1;
            }
        }

        UnlockOverview {
            hero_skins_unlocked: count_positive(hero_skin_d.as_ref(), HERO_SKINS_ALL),
            hero_skins_total: HERO_SKINS_ALL.len(),
            hero_skin_powers_unlocked: count_positive(hero_skin_power_d.as_ref(), HERO_SKIN_POWERS_ALL),
            hero_skin_powers_total: HERO_SKIN_POWERS_ALL.len(),
            npc_skins_unlocked: count_positive(npc_skin_d.as_ref(), NPC_SKINS_ALL),
            npc_skins_total: NPC_SKINS_ALL.len(),
            pets_unlocked,
            pets_total: PETS_ALL.len(),
            naga_powers_maxed,
            naga_powers_total: NAGA_POWERS_ALL.len(),
            six_cycle_powers_unlocked: count_positive(six_d.as_ref(), SIX_CYCLE_POWERS_ALL),
            six_cycle_powers_total: SIX_CYCLE_POWERS_ALL.len(),
            albums_unlocked: count_positive(album_d.as_ref(), ALBUMS_ALL),
            albums_total: ALBUMS_ALL.len(),
            statues_unlocked: count_positive(statue_d.as_ref(), STATUES_ALL),
            statues_total: STATUES_ALL.len(),
            shared_skills_unlocked: count_positive(shared_d.as_ref(), SHARED_SKILLS_ALL),
            shared_skills_total: SHARED_SKILLS_ALL.len(),
            items_unlocked: count_positive(item_d.as_ref(), ITEMS_ALL),
            items_total: ITEMS_ALL.len(),
            garden_plots_unlocked,
            garden_plots_total: 9,
            ban_slots_unlocked,
            ban_slots_total: 31,
            build_projects_unlocked,
            build_projects_total: BUILD_CAT_PROJECTS_ALL.len(),
            travel_features_unlocked,
            travel_features_total: TRAVEL_UNLOCKS_ALL.len(),
            battle_pass_high_unlocked: bp_high,
            battle_pass_seasons_seen: bp_seasons.len(),
            battle_pass_total_level: bp_d.as_ref().and_then(|d| d.get_i32("BattlePassLevelKey")).unwrap_or(0),
            difficulty_unlocked,
            adventure_level: adv_d
                .as_ref()
                .and_then(|d| d.get_i32("AdventureLevelLevelArchiveKey"))
                .unwrap_or(0),
            hero_common_pieces: ex_d
                .as_ref()
                .and_then(|d| d.get_i32("HP_HeroCommonPiece"))
                .unwrap_or(0),
            skin_common_pieces: ex_d
                .as_ref()
                .and_then(|d| d.get_i32("SkinPiecesCount"))
                .unwrap_or(0),
            orb_refine_tickets: orb_d
                .as_ref()
                .and_then(|d| d.get_i32("OrbRefineTicketCount"))
                .unwrap_or(0),
            esr_identify_tickets: gd_d
                .as_ref()
                .and_then(|d| d.get_i32("ESRIdentifyTicketCount"))
                .unwrap_or(0),
            pet_food_count: pet_d
                .as_ref()
                .and_then(|d| d.get_i32("PP_FoodCount"))
                .unwrap_or(0),
            pet_toy_count: pet_d
                .as_ref()
                .and_then(|d| d.get_i32("PP_ToyCount"))
                .unwrap_or(0),
            moye_token_count: misc_d
                .as_ref()
                .and_then(|d| d.get_i32("Moye_Token"))
                .unwrap_or(0),
            bar_vip_unlocked,
            chaos_mode_unlocked: cfg_d
                .as_ref()
                .and_then(|d| d.get_i32("SixCycleChaosUnlock"))
                .unwrap_or(0)
                > 0,
            imba_mode_unlocked: cfg_d
                .as_ref()
                .and_then(|d| d.get_i32("ExperimentalGameplayArchive.ImbaMode.toggleOn"))
                .unwrap_or(0)
                > 0,
            bank_unlocked: bank_d
                .as_ref()
                .and_then(|d| d.get_i32("systemUnlock"))
                .unwrap_or(0)
                > 0,
            level_portal_unlocked: portal_d
                .as_ref()
                .and_then(|d| d.get_i32("LevelPortalSystemUnlock"))
                .unwrap_or(0)
                > 0,
            achievements_completed,
            achievements_claimed,
            achievements_total: ach_names.len(),
        }
    }

    /// Blazing-fast paged search over all 3,659+ PlayerPrefs entries inside Rust RAM.
    pub fn query_entries(
        &self,
        search: &str,
        category_filter: &str,
        only_modified: bool,
        offset: usize,
        limit: usize,
    ) -> ExplorerPage {
        let q = search.trim().to_ascii_lowercase();
        let cat_f = category_filter.trim().to_ascii_lowercase();
        let mut matched: Vec<ExplorerItem> = Vec::new();

        for entry in &self.doc.entries {
            let is_enc = entry.value_type == PrefValueType::String && is_md5_hex(&entry.raw_key);
            let (display_key, category) = self.display_key(&entry.raw_key);
            let modified = self.is_modified(&entry.raw_key);

            if only_modified && !modified {
                continue;
            }
            if !cat_f.is_empty() && cat_f != "all" {
                if cat_f == "nrbf" {
                    let is_nrbf = self
                        .decrypted_cache
                        .get(&entry.raw_key)
                        .map(|s| NrbfDict::is_nrbf_b64(s))
                        .unwrap_or(false);
                    if !is_nrbf {
                        continue;
                    }
                } else if cat_f == "json" {
                    let is_json = self
                        .decrypted_cache
                        .get(&entry.raw_key)
                        .map(|s| s.starts_with('{') || s.starts_with('['))
                        .unwrap_or(false);
                    if !is_json {
                        continue;
                    }
                } else if category.to_ascii_lowercase() != cat_f {
                    continue;
                }
            }

            let (value_kind, preview, byte_len) = if is_enc {
                if let Some(plain) = self.decrypted_cache.get(&entry.raw_key) {
                    classify_decrypted_preview(plain)
                } else {
                    ("encrypted_raw".to_string(), entry.raw_value.chars().take(60).collect(), entry.raw_value.len())
                }
            } else {
                let dec = unity_url_decode(&entry.raw_value).unwrap_or_else(|_| entry.raw_value.clone());
                let prev: String = dec.chars().take(90).collect();
                ("plain".to_string(), prev, dec.len())
            };

            if !q.is_empty() {
                let hit = display_key.to_ascii_lowercase().contains(&q)
                    || entry.raw_key.to_ascii_lowercase().contains(&q)
                    || category.to_ascii_lowercase().contains(&q)
                    || preview.to_ascii_lowercase().contains(&q);
                if !hit {
                    continue;
                }
            }

            matched.push(ExplorerItem {
                raw_key: entry.raw_key.clone(),
                display_key,
                category,
                is_encrypted: is_enc,
                value_kind,
                preview,
                byte_len,
                modified,
            });
        }

        // Prioritize known / modified / NRBF entries first for instant usability
        matched.sort_by(|a, b| {
            b.modified
                .cmp(&a.modified)
                .then((a.category == "unknown").cmp(&(b.category == "unknown")))
                .then(a.display_key.cmp(&b.display_key))
        });

        let total_matched = matched.len();
        let page_items = matched
            .into_iter()
            .skip(offset)
            .take(limit.max(1))
            .collect();

        ExplorerPage {
            total_matched,
            offset,
            limit,
            items: page_items,
        }
    }

    /// Inspects a single key (by MD5 hash or plain name), returning decoded NRBF or formatted JSON.
    pub fn inspect_entry(&self, key: &str) -> OwlResult<EntryInspection> {
        let raw_key = if self.doc.get_raw(key).is_some() {
            key.to_string()
        } else {
            let h = md5_key(key);
            if self.doc.get_raw(&h).is_some() {
                h
            } else if let Some(e) = self.doc.get_by_decoded_key(key) {
                e.raw_key.clone()
            } else {
                return Err(OwlError::Session(format!("Key '{key}' not found")));
            }
        };

        let entry = self
            .doc
            .get_raw(&raw_key)
            .ok_or_else(|| OwlError::Session(format!("Key '{key}' not found")))?;
        let is_enc = entry.value_type == PrefValueType::String && is_md5_hex(&raw_key);
        let (display_key, category) = self.display_key(&raw_key);
        let modified = self.is_modified(&raw_key);

        let plaintext = if is_enc {
            self.decrypted_cache
                .get(&raw_key)
                .cloned()
                .unwrap_or_else(|| entry.raw_value.clone())
        } else {
            unity_url_decode(&entry.raw_value).unwrap_or_else(|_| entry.raw_value.clone())
        };

        let (value_kind, _, _) = if is_enc {
            classify_decrypted_preview(&plaintext)
        } else {
            ("plain".to_string(), String::new(), plaintext.len())
        };

        let nrbf = if NrbfDict::is_nrbf_b64(&plaintext) {
            NrbfDict::parse_b64(&plaintext).ok()
        } else {
            None
        };
        let json_pretty = if plaintext.starts_with('{') || plaintext.starts_with('[') {
            serde_json::from_str::<JsonValue>(&plaintext)
                .ok()
                .and_then(|v| serde_json::to_string_pretty(&v).ok())
        } else {
            None
        };

        Ok(EntryInspection {
            raw_key,
            display_key,
            category,
            is_encrypted: is_enc,
            value_kind,
            raw_plaintext: plaintext,
            nrbf,
            json_pretty,
            modified,
        })
    }

    /// Computes human-readable diff between original loaded save and current in-memory state.
    pub fn compute_diff(&self) -> Vec<DiffEntry> {
        let mut diffs = Vec::new();

        for entry in &self.doc.entries {
            let rk = &entry.raw_key;
            let (display_key, _) = self.display_key(rk);
            match self.original_raw.get(rk) {
                None => {
                    let new_prev = self.preview_for_diff(rk, &entry.raw_value);
                    diffs.push(DiffEntry {
                        raw_key: rk.clone(),
                        display_key: display_key.clone(),
                        change_type: "ADDED".into(),
                        old_preview: "(chưa có)".into(),
                        new_preview: new_prev,
                        warning: self.safe_max_warning(&display_key),
                    });
                }
                Some((_, orig_v)) if orig_v != &entry.raw_value => {
                    let old_prev = self
                        .original_decrypted
                        .get(rk)
                        .map(|s| summarize_diff_val(s))
                        .unwrap_or_else(|| orig_v.chars().take(80).collect());
                    let new_prev = self.preview_for_diff(rk, &entry.raw_value);
                    diffs.push(DiffEntry {
                        raw_key: rk.clone(),
                        display_key: display_key.clone(),
                        change_type: "MODIFIED".into(),
                        old_preview: old_prev,
                        new_preview: new_prev,
                        warning: self.safe_max_warning(&display_key),
                    });
                }
                _ => {}
            }
        }

        for (orig_k, (_, orig_v)) in &self.original_raw {
            if self.doc.get_raw(orig_k).is_none() {
                let (display_key, _) = self.display_key(orig_k);
                let old_prev = self
                    .original_decrypted
                    .get(orig_k)
                    .map(|s| summarize_diff_val(s))
                    .unwrap_or_else(|| orig_v.chars().take(80).collect());
                diffs.push(DiffEntry {
                    raw_key: orig_k.clone(),
                    display_key,
                    change_type: "REMOVED".into(),
                    old_preview: old_prev,
                    new_preview: "(đã xóa)".into(),
                    warning: None,
                });
            }
        }

        diffs.sort_by(|a, b| a.display_key.cmp(&b.display_key));
        diffs
    }

    /// Serializes modified PlayerPrefs XML and runs an in-memory round-trip verification check!
    pub fn commit_xml_verified(&self) -> OwlResult<CommitResult> {
        let xml_bytes = self.doc.serialize_bytes()?;
        let modified_entries = self.modified_count();

        // Round-trip verification: re-parse serialized XML and re-decrypt every modified encrypted key!
        let reparsed = PlayerPrefsDoc::parse_bytes(&xml_bytes)?;
        if reparsed.len() != self.doc.len() {
            return Err(OwlError::Validation(format!(
                "XML round-trip count mismatch: expected {}, got {}",
                self.doc.len(),
                reparsed.len()
            )));
        }
        for entry in &self.doc.entries {
            if self.is_modified(&entry.raw_key)
                && entry.value_type == PrefValueType::String
                && is_md5_hex(&entry.raw_key)
            {
                let re_entry = reparsed.get_raw(&entry.raw_key).ok_or_else(|| {
                    OwlError::Validation(format!("Missing modified key '{}' after serialize", entry.raw_key))
                })?;
                let re_plain = self.codec.decrypt_unity_value(&re_entry.raw_value)?;
                if let Some(expected) = self.decrypted_cache.get(&entry.raw_key) {
                    if &re_plain != expected {
                        return Err(OwlError::Validation(format!(
                            "Round-trip crypto mismatch on key '{}'",
                            entry.raw_key
                        )));
                    }
                }
            }
        }

        let sha256 = sha256_hex(&xml_bytes);
        Ok(CommitResult {
            xml_bytes,
            sha256,
            total_entries: reparsed.len(),
            modified_entries,
        })
    }

    fn preview_for_diff(&self, raw_key: &str, raw_val: &str) -> String {
        if let Some(plain) = self.decrypted_cache.get(raw_key) {
            summarize_diff_val(plain)
        } else {
            unity_url_decode(raw_val)
                .unwrap_or_else(|_| raw_val.to_string())
                .chars()
                .take(80)
                .collect()
        }
    }

    fn safe_max_warning(&self, display_key: &str) -> Option<String> {
        for &(k, label, safe_max, _) in ENCRYPT_INT_PAIRS {
            if display_key == k {
                let (val, _, _) = self.get_encrypt_int_pair(k);
                if val > safe_max {
                    return Some(format!(
                        "{label} = {val} vượt ngưỡng khuyến nghị an toàn ({safe_max})"
                    ));
                }
            }
        }
        None
    }
}

fn summarize_diff_val(plain: &str) -> String {
    if NrbfDict::is_nrbf_b64(plain) {
        if let Ok(d) = NrbfDict::parse_b64(plain) {
            return format!("[NRBF {:?}: {} mục]", d.kind, d.entries.len());
        }
        return format!("[NRBF {} bytes]", plain.len());
    }
    if plain.len() > 96 {
        format!("{}… ({} ký tự)", &plain[..plain.floor_char_boundary_compat(90)], plain.len())
    } else {
        plain.to_string()
    }
}

trait StrCompat {
    fn floor_char_boundary_compat(&self, idx: usize) -> usize;
}
impl StrCompat for str {
    fn floor_char_boundary_compat(&self, idx: usize) -> usize {
        if idx >= self.len() {
            return self.len();
        }
        let mut i = idx;
        while i > 0 && !self.is_char_boundary(i) {
            i -= 1;
        }
        i
    }
}

fn classify_decrypted_preview(plain: &str) -> (String, String, usize) {
    if NrbfDict::is_nrbf_b64(plain) {
        let prev = match NrbfDict::parse_b64(plain) {
            Ok(d) => format!("NRBF ({} mục)", d.entries.len()),
            Err(_) => format!("NRBF ({} chars)", plain.len()),
        };
        ("nrbf".to_string(), prev, plain.len())
    } else if plain.starts_with('{') || plain.starts_with('[') {
        let prev: String = plain.chars().take(90).collect();
        ("json".to_string(), prev, plain.len())
    } else if plain.parse::<i64>().is_ok() || plain.parse::<f64>().is_ok() {
        ("number".to_string(), plain.to_string(), plain.len())
    } else if plain.eq_ignore_ascii_case("true") || plain.eq_ignore_ascii_case("false") {
        ("bool".to_string(), plain.to_string(), plain.len())
    } else {
        let prev: String = plain.chars().take(90).collect();
        ("string".to_string(), prev, plain.len())
    }
}

fn count_bool_json_map(raw: Option<&str>) -> (usize, usize) {
    let Some(s) = raw else {
        return (0, 0);
    };
    let Ok(JsonValue::Object(map)) = serde_json::from_str::<JsonValue>(s) else {
        return (0, 0);
    };
    let total = map.len();
    let unlocked = map
        .values()
        .filter(|v| v.as_bool().unwrap_or(false))
        .count();
    (unlocked, total)
}

/// Beautifies or minifies a JSON string (parity with npdmVNEditor).
pub fn beautify_json(input: &str) -> OwlResult<String> {
    let val: JsonValue =
        serde_json::from_str(input).map_err(|e| OwlError::Json(format!("Invalid JSON: {e}")))?;
    serde_json::to_string_pretty(&val).map_err(|e| OwlError::Json(e.to_string()))
}

pub fn minify_json(input: &str) -> OwlResult<String> {
    let val: JsonValue =
        serde_json::from_str(input).map_err(|e| OwlError::Json(format!("Invalid JSON: {e}")))?;
    serde_json::to_string(&val).map_err(|e| OwlError::Json(e.to_string()))
}

/// Validates JSON or XML content.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ValidationResult {
    pub valid: bool,
    pub message: String,
}

pub fn validate_content(content: &str, format: &str) -> ValidationResult {
    match format.to_ascii_lowercase().as_str() {
        "json" => match serde_json::from_str::<JsonValue>(content) {
            Ok(_) => ValidationResult {
                valid: true,
                message: "JSON hợp lệ".into(),
            },
            Err(e) => ValidationResult {
                valid: false,
                message: format!("Lỗi cú pháp JSON: {e}"),
            },
        },
        "xml" => match PlayerPrefsDoc::parse_bytes(content.as_bytes()) {
            Ok(doc) => ValidationResult {
                valid: true,
                message: format!("XML hợp lệ ({} khóa)", doc.len()),
            },
            Err(e) => ValidationResult {
                valid: false,
                message: format!("Lỗi XML: {e}"),
            },
        },
        _ => ValidationResult {
            valid: true,
            message: "Định dạng văn bản thuần".into(),
        },
    }
}

/// Utility for external JSON map patching (keeps profile-driven Raw JSON workflows fast).
pub fn patch_json_map_entry(json_text: &str, key: &str, value_json: &str) -> OwlResult<String> {
    let mut root: JsonValue = serde_json::from_str(json_text)
        .map_err(|e| OwlError::Json(format!("Invalid root JSON: {e}")))?;
    let val: JsonValue = serde_json::from_str(value_json)
        .map_err(|e| OwlError::Json(format!("Invalid value JSON: {e}")))?;
    if let JsonValue::Object(ref mut map) = root {
        map.insert(key.to_string(), val);
    } else {
        let mut map = JsonMap::new();
        map.insert(key.to_string(), val);
        root = JsonValue::Object(map);
    }
    serde_json::to_string(&root).map_err(|e| OwlError::Json(e.to_string()))
}
