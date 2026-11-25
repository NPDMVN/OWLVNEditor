use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::crypto::md5_key;
use crate::unlockables::ACHIEVEMENT_IDS_ALL;

/// All 45 playable heroes (`BodyName` enum values verified from GameLogic.dll v3.8.0).
pub const PLAYABLE_HEROES: &[(&str, i32, &str, &str)] = &[
    ("hero_quanhuying", 1, "Quan Huying", "Quyền Hổ Anh"),
    ("hero_seebee", 2, "Ginzo", "Ngân Tàng (Ginzo)"),
    ("hero_sakari", 3, "Peddler", "Thương Nhân (Peddler)"),
    ("hero_akaisha", 4, "Akaisha", "Akaisha"),
    ("hero_olivia", 5, "Oli", "Oli (Thợ Máy)"),
    ("hero_gangdan", 6, "Gerard Quentin", "Gerard Quentin"),
    ("hero_katherine", 7, "Katherine", "Katherine"),
    ("hero_hannah", 92, "Hannah", "Hannah"),
    ("hero_hillding", 149, "Hilding", "Hilding"),
    ("hero_penhaligon", 172, "Penhaligon", "Penhaligon"),
    ("hero_hayfa", 187, "Hayfa", "Hayfa"),
    ("hero_xuanshe", 208, "Ebonfang", "Huyền Xà (Ebonfang)"),
    ("hero_heyao", 210, "Jadetalon", "Hạc Dao (Jadetalon)"),
    ("hero_aimi", 214, "Mirelha", "Mirelha"),
    ("hero_uliana", 217, "Uliana", "Uliana"),
    ("hero_annice", 223, "Aigneis", "Aigneis"),
    ("hero_ruthven", 225, "Ruthven", "Ruthven"),
    ("hero_anibella", 230, "Anibella", "Anibella"),
    ("hero_adira", 233, "Alasdair", "Alasdair"),
    ("hero_yangkyi", 267, "Huoran", "Hoặc Nhiên (Huoran)"),
    ("hero_youzi", 268, "Synthia", "Synthia"),
    ("hero_yufeng", 271, "Wu Fei", "Vũ Phi"),
    ("hero_wufei", 272, "Wu Fei (Alt)", "Vũ Phi (Bản 2)"),
    ("hero_bunny", 279, "Fluffette", "Fluffette"),
    ("hero_qifeng", 280, "Lothair", "Lothair"),
    ("hero_linxiao", 288, "Lu Linxiao", "Lục Lâm Tiêu"),
    ("hero_feiyu", 291, "Feiyu", "Phi Vũ"),
    ("hero_hualing", 295, "Hualing", "Hoa Linh"),
    ("hero_rana", 302, "Lana", "Lana"),
    ("hero_erdene", 305, "Erdene", "Erdene"),
    ("hero_dragon", 310, "Ao Bing / Long", "Long Tướng"),
    ("hero_adeline", 314, "Adeline", "Adeline"),
    ("hero_alessia", 316, "Alessia", "Alessia"),
    ("hero_clara", 318, "Clara", "Clara"),
    ("hero_vesper", 320, "Vesper", "Vesper"),
    ("hero_lionKnight", 334, "Lion Knight", "Sư Tử Kỵ Sĩ"),
    ("hero_yoiko", 337, "Yoiko", "Yoiko"),
    ("hero_dolly", 342, "Dolly", "Dolly"),
    ("hero_ixchel", 344, "Ixchel", "Ixchel"),
    ("hero_rosilia", 346, "Rosilia", "Rosilia"),
    ("hero_feim", 348, "Feim", "Feim"),
    ("hero_sawra", 351, "Sawra", "Sawra"),
    ("hero_brunhild", 353, "Brunhild", "Brunhild"),
    ("hero_selenis", 355, "Selenis", "Selenis"),
    ("hero_kilulu", 357, "Kilulu", "Kilulu"),
    ("hero_licco", 374, "Licco", "Licco"),
];

/// All 10 EncryptValue scalar pairs in PlayerArchive (`<Key>` + `<Key>Key`),
/// with verified safe maximum caps calibrated from `GameLogic.dll` v3.8.0.
pub const ENCRYPT_INT_PAIRS: &[(&str, &str, i32, &str)] = &[
    ("SoulStoneCount", "Đá Hồn (Soul Stones)", 1_000_000, "currency"),
    ("BlueStoneCount", "Đá Lam (Sapphires)", 100_000, "currency"),
    ("RedStoneCount", "Đá Đỏ (Rubies)", 100_000, "currency"),
    ("WeaponUpgradeTicket", "Vé Nâng Cấp Vũ Khí", 999, "ticket"),
    ("RebornCard", "Thẻ Hồi Sinh (Revive)", 999, "ticket"),
    ("Fertilizer", "Phân Bón Vườn", 999, "ticket"),
    ("ProbationTicket", "Vé Dùng Thử Tướng", 999, "ticket"),
    ("AsuraPowerPointMax", "Điểm Sức Mạnh Tu La (Max)", 25_000, "asura"),
    (
        "AsuraPowerPointSoulStoneCount",
        "Đá Hồn Tích Lũy Tu La",
        100_000,
        "asura",
    ),
    ("AchievementRoomLevel", "Cấp Phòng Thành Tựu (Max 7)", 7, "progression"),
];

/// All 18 MenuSkinType enum values verified from GameLogic.dll.
pub const MENU_SKIN_TYPES: &[(i32, &str, &str)] = &[
    (0, "Default", "Mặc định"),
    (1, "TapTap", "TapTap"),
    (2, "BiliBili", "BiliBili"),
    (3, "Appstore", "App Store"),
    (4, "VIVO", "VIVO"),
    (5, "HuaWei", "Huawei"),
    (6, "OPPO", "OPPO"),
    (7, "t4399hezi", "4399"),
    (8, "GamePop", "GamePop"),
    (9, "Christmas2020", "Giáng Sinh 2020"),
    (10, "SpringFestival", "Tết Nguyên Đán"),
    (11, "FirstYear", "Kỷ niệm 1 năm"),
    (12, "GooglePlay", "Google Play"),
    (13, "SecondYear", "Kỷ niệm 2 năm"),
    (14, "ThirdYear", "Kỷ niệm 3 năm"),
    (15, "FourthYear", "Kỷ niệm 4 năm"),
    (16, "FifthYear", "Kỷ niệm 5 năm"),
    (17, "SixthYear", "Kỷ niệm 6 năm"),
];

/// All 6 Asura Power stats (`EXAsuraPowerPointInvest` keys 0..5).
pub const ASURA_STATS: &[(i32, &str, &str)] = &[
    (0, "Damage", "Sát thương (Damage)"),
    (1, "AttackSpeed", "Tốc độ đánh (Attack Speed)"),
    (2, "CritRate", "Tỷ lệ chí mạng (Crit Rate)"),
    (3, "MaxHP", "Máu tối đa (Max HP)"),
    (4, "MoveSpeed", "Tốc độ chạy (Move Speed)"),
    (5, "Armor", "Giáp tối đa (Max Armor)"),
];

/// All 62 ResourceTag enum names (`Dictionary<string, EncryptValue>`).
pub const RESOURCE_TAGS: &[(&str, &str)] = &[
    ("Empty", "system"),
    ("HeroSkin", "skin"),
    ("Album", "cosmetic"),
    ("illustrated_characterEffect", "item"),
    ("illustrated_monster", "bestiary"),
    ("illustrated_weapon", "item"),
    ("ChristmasQuest", "event"),
    ("SharedSkillMaterial", "item"),
    ("LivingRoomSkinQuestMaterial", "cosmetic"),
    ("SharedSkill", "skill"),
    ("SixCyclePower", "six_realms"),
    ("menuSkinUnlockQuestShare", "cosmetic"),
    ("ceAffixArchive", "item"),
    ("AchievementRoomStatue", "cosmetic"),
    ("exchangeablePieces", "currency"),
    ("dailyActivity", "event"),
    ("battlePass", "battle_pass"),
    ("petParadise", "pet"),
    ("FloorSkin", "cosmetic"),
    ("FloorSkinPiece", "cosmetic"),
    ("Compensate", "system"),
    ("Bank", "currency"),
    ("itemPack", "progression"),
    ("storyChapter", "progression"),
    ("storyChapterPlaying", "progression"),
    ("LevelPortal", "progression"),
    ("LegendItem", "item"),
    ("LegendItemEntry", "item"),
    ("BanCharacterEffectSlot", "item"),
    ("Config", "config"),
    ("Novice", "system"),
    ("Travel", "progression"),
    ("GameData", "progression"),
    ("BuildCat", "cosmetic"),
    ("AsuraPowerConfig", "asura"),
    ("EventRoom", "progression"),
    ("NagaPower", "hero"),
    ("Kanban", "cosmetic"),
    ("Garden", "progression"),
    ("Halloween", "event"),
    ("WarmUp", "event"),
    ("NPCSkin", "skin"),
    ("HeroSkinPower", "skin"),
    ("LimitedTimeMall", "event"),
    ("NewProgressiveItemUnlock", "item"),
    ("HeroSelectMark", "hero"),
    ("DefeatTrainingRoomMonster", "bestiary"),
    ("SixCycleOrb", "six_realms"),
    ("LocatableBanSlotUnlockState", "item"),
    ("AdventureLevel", "progression"),
    ("ActivityIntegration", "event"),
    ("DailyBox", "event"),
    ("DailyBoxMissionProgress", "event"),
    ("DailyBoxTimeLimitBoxRecord", "event"),
    ("MiscArchive", "system"),
    ("ConsumableRecord", "item"),
    ("HeroLevelAbility", "hero"),
    ("LadderGiftPack", "event"),
    ("BattlePassLoopReward", "battle_pass"),
    ("SixCycleNodeTask", "six_realms"),
    ("AutoSendToMailResource", "system"),
    ("SailingGiftPack", "event"),
];

/// All 23 CustomResourceTag enum names (`Dictionary<string, string>`).
pub const CUSTOM_RESOURCE_TAGS: &[(&str, &str)] = &[
    ("EmptyTag", "system"),
    ("ExclusiveSixRealmSeed", "six_realms"),
    ("ExclusiveSixRealmBallSave", "six_realms"),
    ("ExclusiveSixRealmBallIdentificationSave", "six_realms"),
    ("SixCycleOrbData", "six_realms"),
    ("SixCycleOrbAccountSeed", "six_realms"),
    ("EquippedSixCycleOrbData", "six_realms"),
    ("CustomSixCycleOrbData", "six_realms"),
    ("AdventureLevelMiscNewUnlockWineFormula", "progression"),
    ("AdventureLevelMiscNewUnlockedTravel", "progression"),
    ("SixCycleOrbDroppedFirstOrb", "six_realms"),
    ("LimitActivity", "event"),
    ("DailyBoxBoxesArchive", "event"),
    ("DailyBoxChosenRewardArchive", "event"),
    ("DailyBoxExtraFreeBoxArchive", "event"),
    ("RandomBuff", "progression"),
    ("DailyBoxChosenRewardArchiveNew", "event"),
    ("PlayRelicsBattle", "progression"),
    ("PlayBossRush", "progression"),
    ("MailBoxClaimRecord", "system"),
    ("BattlePassLoopRewardFetchRecord", "battle_pass"),
    ("MythicDivinityArchive", "progression"),
    ("MythicInRunUpgradeArchive", "progression"),
];

const DIRECT_ARCHIVE_KEYS: &[(&str, &str)] = &[
    ("EXAsuraPowerPointInvest", "asura"),
    ("EXBarmaterials", "item"),
    ("MenuSkinUnlock", "cosmetic"),
    ("EXHeroSkinEquip", "skin"),
    ("EXNpcSkinEquip", "skin"),
    ("EXSixPowerConfig", "six_realms"),
    ("EXHeroTalent", "six_realms"),
    ("PlayerStatues", "cosmetic"),
    ("PubRoomSeatLayoutDictionary", "cosmetic"),
    ("PubRoomSeatList", "cosmetic"),
    ("TaskDic", "progression"),
    ("GuideTipLogs", "system"),
    ("sdk_email_read", "system"),
    ("sdk_email_delete", "system"),
    ("sdk_email_claim", "system"),
    ("HasBuyBarVipCard", "progression"),
    ("HasBuyNoviePack", "progression"),
    ("HasBuyNoviePack_2", "progression"),
    ("HasBeatStoreBoss", "progression"),
    ("hasCopyOldResource", "system"),
    ("hasCopyOldAsuraPower", "system"),
    ("HaveFixOldVersionStatueProblem", "system"),
    ("HasUpdatedDifficultSystem202504", "system"),
    ("SixCycleChaosUnlock", "six_realms"),
    ("difficultLevel", "progression"),
    ("difficultLevelUnlockLevel", "progression"),
    ("CurrentMenuSkin", "cosmetic"),
    ("CurrentSelectedPetName", "pet"),
    ("SkinPiecesCount", "currency"),
    ("SeasonCoin", "currency"),
    ("AdventureLevelExpArchiveKey", "progression"),
    ("AdventureLevelLevelArchiveKey", "progression"),
    ("AdventureLevelToAddExpArchiveKey", "progression"),
    ("IapChangeManager._excelCache", "system"),
    ("Moye_OfflineCacheDigest", "system"),
    ("isFirstLaunch", "system"),
    ("game_length", "system"),
    ("lastGameTimeSpan", "system"),
    ("lastMenuTvDateTime_int", "system"),
    ("statistic_todayRandomSeed", "system"),
    ("statistic_drinkCountOverall", "progression"),
    ("statistic_coinCountOverall", "progression"),
    ("statistic_soulStoneCountOverall", "progression"),
    ("statistic_blueStoneCountOverall", "progression"),
    ("statistic_redStoneCountOverall", "progression"),
    ("CrystallCeAffixFirst", "item"),
    ("CrystallCeAffixSecond", "item"),
    ("CrystallCeAffix", "item"),
    ("hasCompleteTutorial", "system"),
    ("hasCompleteChangeSeatTutorial", "system"),
    ("hasPlayedEnding", "progression"),
    ("LocalDeviceId", "account"),
];

const HERO_KEY_SUFFIXES: &[&str] = &[
    "isUnlock",
    "experience",
    "level",
    "isUnlockSecondSkill",
    "currentSecondSkillName",
    "currentSecondStyleName",
    "currentSkillName",
    "skillStyleSelect",
    "skillUnlockLibrary",
    "styleUnlockLibrary",
    "currentHeroBodyStyleName",
    "hasPlay5CharacterEffectUnlockAnim",
    "processed2024AprilSkillFreeChanges",
    "weaponSkill",
];

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct KeyMeta {
    pub plain_key: String,
    pub category: String,
}

#[derive(Debug, Clone)]
pub struct KeyDictionary {
    /// md5_hex -> KeyMeta
    by_md5: HashMap<String, KeyMeta>,
}

impl Default for KeyDictionary {
    fn default() -> Self {
        Self::new_builtin()
    }
}

impl KeyDictionary {
    pub fn new_builtin() -> Self {
        let mut dict = Self {
            by_md5: HashMap::with_capacity(4500),
        };

        for &(key, _, _, cat) in ENCRYPT_INT_PAIRS {
            dict.register(key, cat);
            dict.register(&format!("{key}Key"), cat);
        }
        for &(tag, cat) in RESOURCE_TAGS {
            dict.register(tag, cat);
        }
        for &(tag, cat) in CUSTOM_RESOURCE_TAGS {
            dict.register(tag, cat);
        }
        for &(key, cat) in DIRECT_ARCHIVE_KEYS {
            dict.register(key, cat);
        }

        // All 45 heroes x 14 HeroArchive keys + UnlockMsgArchive + Guided/Showed
        for &(body, _, _, _) in PLAYABLE_HEROES {
            for suf in HERO_KEY_SUFFIXES {
                dict.register(&format!("{body}{suf}"), "hero");
            }
            dict.register(&format!("GuidedHeroSeat_{body}"), "hero");
            dict.register(&format!("ShowedNewHeroAdded_{body}"), "hero");
            dict.register(
                &format!("UnlockMsgArchive.heroUnlockDateTime.{body}"),
                "hero",
            );
            dict.register(&format!("UnlockMsgArchive.heroUnlockMethod.{body}"), "hero");
            dict.register(&format!("CharacterESRSeedInfo@{body}"), "six_realms");
        }

        // All 236 verified Achievement.AchievementID enum names (+ Licco forward-compat) x 5 PlayerPrefs fields
        for &ach_name in ACHIEVEMENT_IDS_ALL
            .iter()
            .chain(["unlockHeroLicco", "maxLevelLicco"].iter())
        {
            for field in [
                "isComplete",
                "hasReceiveAward",
                "awardTime",
                "currentProgress",
                "completeTime",
            ] {
                dict.register(&format!("pp_achievement_{ach_name}_{field}"), "achievement");
            }
        }

        dict
    }

    pub fn register(&mut self, plain_key: &str, category: &str) {
        let h = md5_key(plain_key);
        self.by_md5.entry(h).or_insert_with(|| KeyMeta {
            plain_key: plain_key.to_string(),
            category: category.to_string(),
        });
    }

    /// Merges an external JSON object `{ "plain_key": "category", ... }` or array `["plain_key", ...]`.
    pub fn merge_json(&mut self, json_str: &str) -> usize {
        let Ok(val) = serde_json::from_str::<serde_json::Value>(json_str) else {
            return 0;
        };
        let mut added = 0;
        if let Some(obj) = val.as_object() {
            for (k, v) in obj {
                let cat = v.as_str().unwrap_or("custom");
                self.register(k, cat);
                added += 1;
            }
        } else if let Some(arr) = val.as_array() {
            for item in arr {
                if let Some(k) = item.as_str() {
                    self.register(k, "custom");
                    added += 1;
                }
            }
        }
        added
    }

    pub fn lookup(&self, md5_hex: &str) -> Option<&KeyMeta> {
        self.by_md5.get(md5_hex)
    }

    pub fn len(&self) -> usize {
        self.by_md5.len()
    }
}
