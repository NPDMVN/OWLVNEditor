use std::path::Path;

use owledit_core::backup::{
    create_backup_from_staging, inspect_backup, verify_and_extract_to_staging, verify_and_read_entry,
};
use owledit_core::crypto::{decode_encrypt_int, encode_encrypt_int, md5_key, OwlCodec};
use owledit_core::nrbf::{NrbfDict, NrbfDictKind};
use owledit_core::sanitizer::sanitize_playerprefs_xml;
use owledit_core::session::OwlSession;
use owledit_core::ssaid::{
    detect_ssaid_format, generate_random_ssaid, inspect_ssaid, update_ssaid_verified, SsaidFormat,
};

#[test]
fn test_crypto_roundtrip_and_known_vectors() {
    let codec = OwlCodec::default();
    assert_eq!(md5_key("GameData"), "a3ebea62920576afe6c9fee22a11bff4");
    assert_eq!(md5_key("SoulStoneCount"), "ad56a815b981310f5355f9cd0299c5b8");

    for sample in ["0", "1", "1000000361", "GoldBread", "", "1234567890123456", "{\"a\":true}"] {
        let enc = codec.encrypt_unity_value(sample).unwrap();
        let dec = codec.decrypt_unity_value(&enc).unwrap();
        assert_eq!(dec, sample);
    }

    let (m, k) = encode_encrypt_int(999_999);
    assert!((200..500).contains(&k));
    assert_eq!(decode_encrypt_int(m, k), 999_999);
}

#[test]
fn test_nrbf_all_kinds_roundtrip() {
    let cases = vec![
        (
            NrbfDictKind::StringEncryptValue,
            vec![("hero_akaisha_hunter", "1"), ("hero_linxiao_singer", "1")],
        ),
        (
            NrbfDictKind::IntEncryptValue,
            vec![("0", "5000"), ("1", "3000"), ("5", "2500")],
        ),
        (
            NrbfDictKind::EnumEncryptValue {
                key_enum: "MenuSkinType".into(),
            },
            vec![("0", "1"), ("13", "1"), ("17", "1")],
        ),
        (
            NrbfDictKind::StringString,
            vec![("asura", "npc_asura_spring2023"), ("ken", "npc_ken_spring2023")],
        ),
        (
            NrbfDictKind::EnumString {
                key_enum: "BodyName".into(),
            },
            vec![("1", "hero_quanhuying"), ("374", "hero_licco")],
        ),
        (
            NrbfDictKind::EnumEnum {
                key_enum: "BodyName".into(),
                val_enum: "Archive+PlayerArchive+TalentName".into(),
            },
            vec![("1", "2"), ("2", "3")],
        ),
    ];

    for (kind, pairs) in cases {
        let mut d = NrbfDict::new(kind.clone());
        for (k, v) in &pairs {
            d.set(*k, *v);
        }
        let b64 = d.to_b64().unwrap();
        assert!(NrbfDict::is_nrbf_b64(&b64));
        let parsed = NrbfDict::parse_b64(&b64).unwrap();
        assert_eq!(parsed.kind, kind);
        assert_eq!(parsed.entries.len(), pairs.len());
        for (k, v) in &pairs {
            assert_eq!(parsed.get(k), Some(*v));
        }
    }
}

#[test]
fn test_ssaid_plain_xml_roundtrip() {
    let xml = br#"<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<settings version="-1">
  <setting id="1" name="10234" value="1122334455667788" package="com.chillyroom.zhmr.gp" defaultValue="1122334455667788" defaultSysSet="false" tag="null" />
  <setting id="2" name="10235" value="aabbccddeeff0011" package="com.example.other" defaultValue="aabbccddeeff0011" defaultSysSet="false" tag="null" />
</settings>"#;

    assert_eq!(detect_ssaid_format(xml), SsaidFormat::PlainXml);
    let insp = inspect_ssaid(xml, Some("com.chillyroom.zhmr.gp")).unwrap();
    assert_eq!(insp.entries.len(), 1);
    assert_eq!(insp.entries[0].value, "1122334455667788");

    let new_id = generate_random_ssaid();
    let upd = update_ssaid_verified(xml, "com.chillyroom.zhmr.gp", &new_id).unwrap();
    assert_eq!(upd.old_value, "1122334455667788");
    assert_eq!(upd.new_value, new_id);

    let after = inspect_ssaid(&upd.updated_bytes, None).unwrap();
    assert_eq!(after.entries.len(), 2);
    assert_eq!(after.entries[1].value, "aabbccddeeff0011");
}

#[test]
fn test_backup_owldb_and_sanitizer() {
    let tmp = std::env::temp_dir().join("owledit_core_test_backup");
    let _ = std::fs::remove_dir_all(&tmp);
    let stage = tmp.join("stage");
    let extract = tmp.join("extract");
    let archive = tmp.join("test.owldb");
    std::fs::create_dir_all(stage.join("shared_prefs")).unwrap();

    let xml = br#"<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="LastLoginUid">66589082</string>
    <string name="emptyLocalCloudSaveId">GP_66589082</string>
    <string name="ad56a815b981310f5355f9cd0299c5b8">Qqgy6g%2B9T5hB9k04bbX5Fw%3D%3D</string>
</map>"#;
    std::fs::write(
        stage.join("shared_prefs/com.chillyroom.zhmr.gp.v2.playerprefs.xml"),
        xml,
    )
    .unwrap();

    let manifest = create_backup_from_staging(
        &stage,
        &archive,
        "com.chillyroom.zhmr.gp",
        "chillyroom.otherworld_legends",
        Some("66589082".into()),
        "Unit test backup",
        "TestDevice",
        1700000000000,
    )
    .unwrap();
    assert_eq!(manifest.entries.len(), 1);

    let insp = inspect_backup(&archive).unwrap();
    assert!(insp.integrity_ok);

    let extracted = verify_and_extract_to_staging(&archive, &extract, None).unwrap();
    assert_eq!(extracted.len(), 1);

    let read_back = verify_and_read_entry(
        &archive,
        "shared_prefs/com.chillyroom.zhmr.gp.v2.playerprefs.xml",
    )
    .unwrap();
    assert_eq!(read_back, xml);

    let san = sanitize_playerprefs_xml(&read_back, &[], true).unwrap();
    assert_eq!(san.removed_keys.len(), 2);
    assert_eq!(san.remaining_entries, 1);

    let _ = std::fs::remove_dir_all(&tmp);
}

#[test]
fn test_real_workspace_save_if_present() {
    let real_save = Path::new(
        r"C:\Users\Windows\Downloads\OWLEditor\com.chillyroom.zhmr.gp\shared_prefs\com.chillyroom.zhmr.gp.v2.playerprefs.xml",
    );
    if !real_save.exists() {
        return;
    }
    let bytes = std::fs::read(real_save).unwrap();
    let mut session = OwlSession::open_xml_bytes(&bytes, OwlCodec::default(), None).unwrap();
    let dash = session.build_dashboard().unwrap();

    assert_eq!(dash.total_entries, 3659);
    assert_eq!(dash.encrypted_entries, 3576);
    assert_eq!(dash.account.uid.as_deref(), Some("66589082"));

    // Verify exact decoded currency values from our Phase 0 audit!
    let (red, _, _) = session.get_encrypt_int_pair("RedStoneCount");
    assert_eq!(red, 999_999_999);

    // Apply full safe preset and commit with self-verification
    session.apply_preset("full_safe_bundle", None, None).unwrap();
    let commit = session.commit_xml_verified().unwrap();
    assert!(commit.modified_entries > 0);
    assert_eq!(commit.total_entries, session.doc.len());
}
