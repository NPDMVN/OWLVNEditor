//! Pure Rust MS-NRBF (.NET BinaryFormatter) parser & serializer specialized for
//! Otherworld Legends (`PlayerPrefsUtility.Serialize` / `Deserialize`) containers.
//!
//! Supports full round-trip reading & writing of all editable Dictionary types in OWL:
//! - `Dictionary<string, EncryptValue>` (all 62 ResourceTag containers: HeroSkin, petParadise, GameData, battlePass, ...)
//! - `Dictionary<int, EncryptValue>` (`EXAsuraPowerPointInvest`, `EXBarmaterials`)
//! - `Dictionary<Enum, EncryptValue>` (`MenuSkinUnlock` with `MenuSkinType`)
//! - `Dictionary<string, string>` (all 22 CustomResourceTag containers + `EXNpcSkinEquip`)
//! - `Dictionary<Enum, string>` (`EXHeroSkinEquip` with `BodyName`)
//! - `Dictionary<Enum, Enum>` (`EXHeroTalent` with `BodyName -> Archive+PlayerArchive+TalentName`)

use std::collections::HashMap;

use base64::{engine::general_purpose::STANDARD as B64, Engine as _};
use serde::{Deserialize, Serialize};

use crate::crypto::{decode_encrypt_int, encode_encrypt_int};
use crate::error::{OwlError, OwlResult};

const MSCORLIB_STR: &str = "System.String, mscorlib, Version=4.0.0.0, Culture=neutral, PublicKeyToken=b77a5c561934e089";
const MSCORLIB_I32: &str = "System.Int32, mscorlib, Version=4.0.0.0, Culture=neutral, PublicKeyToken=b77a5c561934e089";
const GAMELOGIC_ASM: &str = "GameLogic, Version=0.0.0.0, Culture=neutral, PublicKeyToken=null";

/// Kind of Otherworld Legends NRBF Dictionary container.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "snake_case")]
pub enum NrbfDictKind {
    StringEncryptValue,
    IntEncryptValue,
    EnumEncryptValue { key_enum: String },
    StringString,
    EnumString { key_enum: String },
    EnumEnum { key_enum: String, val_enum: String },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NrbfEntry {
    /// Logical key (string, or decimal integer string for int/enum keys).
    pub key: String,
    /// Decoded value (integer string for EncryptValue/Enum, or raw string for String values).
    pub value: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NrbfDict {
    pub kind: NrbfDictKind,
    pub version: i32,
    pub hash_size: i32,
    pub entries: Vec<NrbfEntry>,
}

impl NrbfDict {
    pub fn new(kind: NrbfDictKind) -> Self {
        Self {
            kind,
            version: 1,
            hash_size: 3,
            entries: Vec::new(),
        }
    }

    pub fn is_nrbf_b64(s: &str) -> bool {
        s.starts_with("AAEAAAD")
    }

    pub fn get(&self, key: &str) -> Option<&str> {
        self.entries
            .iter()
            .find(|e| e.key == key)
            .map(|e| e.value.as_str())
    }

    pub fn get_i32(&self, key: &str) -> Option<i32> {
        self.get(key).and_then(|v| v.parse::<i32>().ok())
    }

    pub fn set(&mut self, key: impl Into<String>, value: impl Into<String>) {
        let key = key.into();
        let value = value.into();
        if let Some(existing) = self.entries.iter_mut().find(|e| e.key == key) {
            existing.value = value;
        } else {
            self.entries.push(NrbfEntry { key, value });
        }
        self.version = self.version.saturating_add(1);
    }

    pub fn remove(&mut self, key: &str) -> bool {
        let before = self.entries.len();
        self.entries.retain(|e| e.key != key);
        if self.entries.len() != before {
            self.version = self.version.saturating_add(1);
            true
        } else {
            false
        }
    }

    pub fn parse_b64(b64: &str) -> OwlResult<Self> {
        let raw = B64
            .decode(b64.trim())
            .map_err(|e| OwlError::Nrbf(format!("Invalid NRBF base64: {e}")))?;
        Self::parse_bytes(&raw)
    }

    pub fn to_b64(&self) -> OwlResult<String> {
        let bytes = self.serialize_bytes()?;
        Ok(B64.encode(bytes))
    }

    /// Parses an MS-NRBF binary stream (ignoring trailing MemoryStream.GetBuffer() zero bytes).
    pub fn parse_bytes(raw: &[u8]) -> OwlResult<Self> {
        let mut r = BinReader::new(raw);

        // 0x00 SerializedStreamHeader
        let rec = r.u8()?;
        if rec != 0x00 {
            return Err(OwlError::Nrbf(format!(
                "Expected SerializedStreamHeader (0x00), got 0x{rec:02x}"
            )));
        }
        let _root_id = r.i32()?;
        let _header_id = r.i32()?;
        let _major = r.i32()?;
        let _minor = r.i32()?;

        // Expect root 0x04 SystemClassWithMembersAndTypes (Dictionary`2)
        let root_rec = r.u8()?;
        if root_rec != 0x04 {
            return Err(OwlError::Nrbf(format!(
                "Expected root SystemClassWithMembersAndTypes (0x04), got 0x{root_rec:02x}"
            )));
        }
        let _dict_obj_id = r.i32()?;
        let dict_type_name = r.len_prefixed_str()?;
        let kind = classify_dict_type(&dict_type_name)?;

        let member_count = r.i32()? as usize;
        let mut member_names = Vec::with_capacity(member_count);
        for _ in 0..member_count {
            member_names.push(r.len_prefixed_str()?);
        }
        // BinaryTypeEnums
        let mut btypes = Vec::with_capacity(member_count);
        for _ in 0..member_count {
            btypes.push(r.u8()?);
        }
        // AdditionalInfos
        for &bt in &btypes {
            skip_additional_info(&mut r, bt)?;
        }

        // Member values of Dictionary`2: Version (i32), Comparer (MemberReference), HashSize (i32), [KeyValuePairs (MemberReference)]
        let mut version = 0i32;
        let mut hash_size = 0i32;
        let mut has_kvp = false;
        for name in &member_names {
            match name.as_str() {
                "Version" => version = r.i32()?,
                "HashSize" => hash_size = r.i32()?,
                "Comparer" => {
                    let tag = r.u8()?;
                    match tag {
                        0x09 => {
                            let _id_ref = r.i32()?;
                        }
                        0x0A => {} // ObjectNull
                        other => {
                            return Err(OwlError::Nrbf(format!(
                                "Unexpected Comparer record 0x{other:02x}"
                            )));
                        }
                    }
                }
                "KeyValuePairs" => {
                    let tag = r.u8()?;
                    match tag {
                        0x09 => {
                            let _id_ref = r.i32()?;
                            has_kvp = true;
                        }
                        0x0A => {
                            has_kvp = false;
                        }
                        other => {
                            return Err(OwlError::Nrbf(format!(
                                "Unexpected KeyValuePairs record 0x{other:02x}"
                            )));
                        }
                    }
                }
                _ => {
                    return Err(OwlError::Nrbf(format!(
                        "Unsupported Dictionary member '{name}'"
                    )));
                }
            }
        }

        if !has_kvp {
            return Ok(NrbfDict {
                kind,
                version,
                hash_size,
                entries: Vec::new(),
            });
        }

        // Now read subsequent records until 0x0B (MessageEnd)
        // We collect string table, EncryptValue objects by objectId, and the KeyValuePair slots!
        let mut string_by_id: HashMap<i32, String> = HashMap::new();
        let mut encrypt_by_id: HashMap<i32, i32> = HashMap::new();
        let mut raw_pairs: Vec<(RawVal, RawVal)> = Vec::new();

        // Class schemas by objectId for ClassWithId (0x01)
        #[derive(Clone)]
        struct ClassSchema {
            name: String,
            members: Vec<(String, u8)>,
        }
        let mut schemas: HashMap<i32, ClassSchema> = HashMap::new();

        // Helper to read a member value based on binary type enum
        fn read_member_val(
            r: &mut BinReader<'_>,
            bt: u8,
            string_by_id: &mut HashMap<i32, String>,
            schemas: &mut HashMap<i32, ClassSchema>,
        ) -> OwlResult<RawVal> {
            match bt {
                0x00 => {
                    // Primitive — in OWL keys/values this is always Int32 (8) or Bool (1)
                    Ok(RawVal::Int(r.i32()?))
                }
                0x01 => {
                    // String record inline: 0x06 BinaryObjectString, 0x09 MemberReference, 0x0A ObjectNull
                    let tag = r.u8()?;
                    match tag {
                        0x06 => {
                            let oid = r.i32()?;
                            let s = r.len_prefixed_str()?;
                            string_by_id.insert(oid, s.clone());
                            Ok(RawVal::Str(s))
                        }
                        0x09 => Ok(RawVal::Ref(r.i32()?)),
                        0x0A => Ok(RawVal::Null),
                        other => Err(OwlError::Nrbf(format!(
                            "Unexpected string member record 0x{other:02x}"
                        ))),
                    }
                }
                0x03 | 0x04 => {
                    // SystemClass / Class (could be inline Enum value type, ObjectNull, or MemberReference!)
                    let tag = r.u8()?;
                    match tag {
                        0x09 => Ok(RawVal::Ref(r.i32()?)),
                        0x0A => Ok(RawVal::Null),
                        0x05 => {
                            // Inline ClassWithMembersAndTypes (e.g. Enum with single `value__` i32 field!)
                            let oid = r.i32()?;
                            let cname = r.len_prefixed_str()?;
                            let mc = r.i32()? as usize;
                            let mut mnames = Vec::with_capacity(mc);
                            for _ in 0..mc {
                                mnames.push(r.len_prefixed_str()?);
                            }
                            let mut mbts = Vec::with_capacity(mc);
                            for _ in 0..mc {
                                mbts.push(r.u8()?);
                            }
                            for &b in &mbts {
                                skip_additional_info(r, b)?;
                            }
                            let _lib_id = r.i32()?;
                            let schema = ClassSchema {
                                name: cname,
                                members: mnames.into_iter().zip(mbts).collect(),
                            };
                            schemas.insert(oid, schema.clone());
                            if schema.members.len() == 1 && schema.members[0].0 == "value__" {
                                Ok(RawVal::Int(r.i32()?))
                            } else {
                                Err(OwlError::Nrbf(format!(
                                    "Unsupported inline ClassWithMembersAndTypes '{}'",
                                    schema.name
                                )))
                            }
                        }
                        0x01 => {
                            // Inline ClassWithId (Enum value reusing earlier schema!)
                            let _oid = r.i32()?;
                            let meta_id = r.i32()?;
                            let schema = schemas.get(&meta_id).cloned().ok_or_else(|| {
                                OwlError::Nrbf(format!("Missing schema for inline ClassWithId {meta_id}"))
                            })?;
                            if schema.members.len() == 1 && schema.members[0].0 == "value__" {
                                Ok(RawVal::Int(r.i32()?))
                            } else {
                                Err(OwlError::Nrbf(format!(
                                    "Unsupported inline ClassWithId '{}'",
                                    schema.name
                                )))
                            }
                        }
                        other => Err(OwlError::Nrbf(format!(
                            "Unexpected class member record 0x{other:02x}"
                        ))),
                    }
                }
                other => Err(OwlError::Nrbf(format!(
                    "Unsupported member binary type 0x{other:02x}"
                ))),
            }
        }

        loop {
            if r.is_eof() {
                break;
            }
            let tag = r.u8()?;
            match tag {
                0x0B => break, // MessageEnd
                0x0C => {
                    // BinaryLibrary
                    let _lib_id = r.i32()?;
                    let _lib_name = r.len_prefixed_str()?;
                }
                0x04 => {
                    // SystemClassWithMembersAndTypes (Comparer or first KeyValuePair`2)
                    let oid = r.i32()?;
                    let cname = r.len_prefixed_str()?;
                    let mc = r.i32()? as usize;
                    let mut mnames = Vec::with_capacity(mc);
                    for _ in 0..mc {
                        mnames.push(r.len_prefixed_str()?);
                    }
                    let mut mbts = Vec::with_capacity(mc);
                    for _ in 0..mc {
                        mbts.push(r.u8()?);
                    }
                    for &b in &mbts {
                        skip_additional_info(&mut r, b)?;
                    }
                    let schema = ClassSchema {
                        name: cname.clone(),
                        members: mnames.into_iter().zip(mbts).collect(),
                    };
                    schemas.insert(oid, schema.clone());

                    if cname.starts_with("System.Collections.Generic.KeyValuePair`2") {
                        let k = read_member_val(&mut r, schema.members[0].1, &mut string_by_id, &mut schemas)?;
                        let v = read_member_val(&mut r, schema.members[1].1, &mut string_by_id, &mut schemas)?;
                        raw_pairs.push((k, v));
                    } else if mc == 0 {
                        // Comparer with 0 fields — nothing more to read
                    } else {
                        return Err(OwlError::Nrbf(format!(
                            "Unsupported top-level SystemClass '{cname}' ({mc} members)"
                        )));
                    }
                }
                0x07 => {
                    // BinaryArray (KeyValuePair`2[])
                    let _arr_oid = r.i32()?;
                    let arr_type = r.u8()?; // 0 = Single
                    let rank = r.i32()?;
                    if arr_type != 0 || rank != 1 {
                        return Err(OwlError::Nrbf(format!(
                            "Unsupported BinaryArray type={arr_type} rank={rank}"
                        )));
                    }
                    let _len = r.i32()?;
                    let elem_bt = r.u8()?;
                    skip_additional_info(&mut r, elem_bt)?;
                    // Elements follow directly as records in the stream!
                }
                0x05 => {
                    // ClassWithMembersAndTypes (e.g. EncryptValue)
                    let oid = r.i32()?;
                    let cname = r.len_prefixed_str()?;
                    let mc = r.i32()? as usize;
                    let mut mnames = Vec::with_capacity(mc);
                    for _ in 0..mc {
                        mnames.push(r.len_prefixed_str()?);
                    }
                    let mut mbts = Vec::with_capacity(mc);
                    for _ in 0..mc {
                        mbts.push(r.u8()?);
                    }
                    for &b in &mbts {
                        skip_additional_info(&mut r, b)?;
                    }
                    let _lib_id = r.i32()?;
                    let schema = ClassSchema {
                        name: cname.clone(),
                        members: mnames.into_iter().zip(mbts).collect(),
                    };
                    schemas.insert(oid, schema.clone());

                    if cname == "EncryptValue" {
                        let show_val = r.i32()?;
                        let m_val = r.i32()?;
                        let enc_key = r.i32()?;
                        let real = if m_val == 0 && enc_key == 0 {
                            show_val
                        } else {
                            decode_encrypt_int(m_val, enc_key)
                        };
                        encrypt_by_id.insert(oid, real);
                    } else {
                        return Err(OwlError::Nrbf(format!(
                            "Unsupported ClassWithMembersAndTypes '{cname}' (use Raw view for custom structs)"
                        )));
                    }
                }
                0x01 => {
                    // ClassWithId (reusing KeyValuePair`2 or EncryptValue schema!)
                    let oid = r.i32()?;
                    let meta_id = r.i32()?;
                    let schema = schemas.get(&meta_id).cloned().ok_or_else(|| {
                        OwlError::Nrbf(format!("Unknown ClassWithId metadataId {meta_id}"))
                    })?;
                    if schema.name.starts_with("System.Collections.Generic.KeyValuePair`2") {
                        let k = read_member_val(&mut r, schema.members[0].1, &mut string_by_id, &mut schemas)?;
                        let v = read_member_val(&mut r, schema.members[1].1, &mut string_by_id, &mut schemas)?;
                        raw_pairs.push((k, v));
                    } else if schema.name == "EncryptValue" {
                        let show_val = r.i32()?;
                        let m_val = r.i32()?;
                        let enc_key = r.i32()?;
                        let real = if m_val == 0 && enc_key == 0 {
                            show_val
                        } else {
                            decode_encrypt_int(m_val, enc_key)
                        };
                        encrypt_by_id.insert(oid, real);
                    } else {
                        return Err(OwlError::Nrbf(format!(
                            "Unsupported ClassWithId '{}'",
                            schema.name
                        )));
                    }
                }
                0x06 => {
                    // Standalone BinaryObjectString
                    let oid = r.i32()?;
                    let s = r.len_prefixed_str()?;
                    string_by_id.insert(oid, s);
                }
                other => {
                    return Err(OwlError::Nrbf(format!(
                        "Unexpected NRBF record tag 0x{other:02x} at offset {}",
                        r.pos.saturating_sub(1)
                    )));
                }
            }
        }

        // Resolve raw_pairs into NrbfEntry list
        let mut entries = Vec::with_capacity(raw_pairs.len());
        for (rk, rv) in raw_pairs {
            let key = resolve_raw_val(&rk, &string_by_id, &encrypt_by_id)?;
            let value = resolve_raw_val(&rv, &string_by_id, &encrypt_by_id)?;
            entries.push(NrbfEntry { key, value });
        }

        Ok(NrbfDict {
            kind,
            version,
            hash_size,
            entries,
        })
    }

    /// Serializes this `NrbfDict` into valid MS-NRBF bytes compatible with Unity/Mono `BinaryFormatter`,
    /// padded to power-of-2 buffer size (>= 512) matching `MemoryStream.GetBuffer()`.
    pub fn serialize_bytes(&self) -> OwlResult<Vec<u8>> {
        let mut w = BinWriter::new();

        // 0x00 SerializedStreamHeader
        w.u8(0x00);
        w.i32(1); // rootId
        w.i32(-1); // headerId
        w.i32(1); // majorVersion
        w.i32(0); // minorVersion

        let (key_full, val_full, comparer_name, kvp_full) = type_strings_for_kind(&self.kind);
        let dict_full = format!("System.Collections.Generic.Dictionary`2[[{key_full}],[{val_full}]]");

        let count = self.entries.len();
        let hash_size = next_prime_at_least(count.max(self.hash_size.max(0) as usize));

        // 0x04 SystemClassWithMembersAndTypes (Dictionary`2, objectId = 1)
        w.u8(0x04);
        w.i32(1);
        w.len_prefixed_str(&dict_full);

        if count == 0 {
            w.i32(3);
            w.len_prefixed_str("Version");
            w.len_prefixed_str("Comparer");
            w.len_prefixed_str("HashSize");
            // BinaryTypeEnums: Primitive(0), SystemClass(3), Primitive(0)
            w.u8(0x00);
            w.u8(0x03);
            w.u8(0x00);
            // AdditionalInfos: Int32(8), Comparer class name, Int32(8)
            w.u8(0x08);
            w.len_prefixed_str(&comparer_name);
            w.u8(0x08);
            // Values
            w.i32(self.version);
            w.u8(0x09); // MemberReference -> Comparer (id=2)
            w.i32(2);
            w.i32(0);

            // Comparer record (id=2)
            w.u8(0x04);
            w.i32(2);
            w.len_prefixed_str(&comparer_name);
            w.i32(0);

            w.u8(0x0B); // MessageEnd
            return Ok(pad_power_of_two(w.buf, 512));
        }

        // Non-empty Dictionary`2 (4 members: Version, Comparer, HashSize, KeyValuePairs)
        w.i32(4);
        w.len_prefixed_str("Version");
        w.len_prefixed_str("Comparer");
        w.len_prefixed_str("HashSize");
        w.len_prefixed_str("KeyValuePairs");
        // BinaryTypeEnums: Primitive(0), SystemClass(3), Primitive(0), SystemClass(3)
        w.u8(0x00);
        w.u8(0x03);
        w.u8(0x00);
        w.u8(0x03);
        // AdditionalInfos
        w.u8(0x08); // Int32
        w.len_prefixed_str(&comparer_name);
        w.u8(0x08); // Int32
        w.len_prefixed_str(&format!("{kvp_full}[]"));

        // Values
        w.i32(self.version);
        w.u8(0x09);
        w.i32(2); // Comparer
        w.i32(hash_size as i32);
        w.u8(0x09);
        w.i32(3); // KeyValuePairs array

        // Record 2: Comparer
        w.u8(0x04);
        w.i32(2);
        w.len_prefixed_str(&comparer_name);
        w.i32(0);

        // Record 3: BinaryArray (KeyValuePair`2[])
        w.u8(0x07);
        w.i32(3);
        w.u8(0x00); // Single
        w.i32(1); // Rank 1
        w.i32(count as i32);
        w.u8(0x03); // SystemClass element type
        w.len_prefixed_str(&kvp_full);

        // If any key or value uses GameLogic assembly, emit BinaryLibrary (id=5)
        let uses_gamelogic = !matches!(self.kind, NrbfDictKind::StringString);
        let gamelogic_lib_id = 5i32;
        if uses_gamelogic {
            w.u8(0x0C);
            w.i32(gamelogic_lib_id);
            w.len_prefixed_str(GAMELOGIC_ASM);
        }

        // Allocate strictly unique absolute object IDs from a single monotonic counter,
        // matching Mono/Unity & .NET BinaryFormatter's `ObjectIDGenerator` invariant
        // (ValueType records use `-id`, ReferenceType records use `+id`, never sharing `|id|`).
        // IDs 1=Dict, 2=Comparer, 3=Array, 5=GameLogic BinaryLibrary.
        let mut next_id = 6i32;
        let mut alloc_id = || -> i32 {
            let id = next_id;
            next_id += 1;
            id
        };
        let mut first_kvp_id: Option<i32> = None;
        let mut deferred_encrypts: Vec<(i32, i32)> = Vec::new();
        let mut key_enum_schema_id: Option<i32> = None;
        let mut val_enum_schema_id: Option<i32> = None;

        for (idx, entry) in self.entries.iter().enumerate() {
            let elem_id = -alloc_id();
            if idx == 0 {
                first_kvp_id = Some(elem_id);
                // 0x04 SystemClassWithMembersAndTypes (KeyValuePair`2)
                w.u8(0x04);
                w.i32(elem_id);
                w.len_prefixed_str(&kvp_full);
                w.i32(2);
                w.len_prefixed_str("key");
                w.len_prefixed_str("value");
                match &self.kind {
                    NrbfDictKind::StringEncryptValue => {
                        w.u8(0x01); // String
                        w.u8(0x04); // Class
                        w.len_prefixed_str("EncryptValue");
                        w.i32(gamelogic_lib_id);
                    }
                    NrbfDictKind::IntEncryptValue => {
                        w.u8(0x00); // Primitive
                        w.u8(0x04); // Class
                        w.u8(0x08); // Int32
                        w.len_prefixed_str("EncryptValue");
                        w.i32(gamelogic_lib_id);
                    }
                    NrbfDictKind::EnumEncryptValue { key_enum } => {
                        w.u8(0x04); // Class (Enum)
                        w.u8(0x04); // Class (EncryptValue)
                        w.len_prefixed_str(key_enum);
                        w.i32(gamelogic_lib_id);
                        w.len_prefixed_str("EncryptValue");
                        w.i32(gamelogic_lib_id);
                    }
                    NrbfDictKind::StringString => {
                        w.u8(0x01);
                        w.u8(0x01);
                    }
                    NrbfDictKind::EnumString { key_enum } => {
                        w.u8(0x04);
                        w.u8(0x01);
                        w.len_prefixed_str(key_enum);
                        w.i32(gamelogic_lib_id);
                    }
                    NrbfDictKind::EnumEnum { key_enum, val_enum } => {
                        w.u8(0x04);
                        w.u8(0x04);
                        w.len_prefixed_str(key_enum);
                        w.i32(gamelogic_lib_id);
                        w.len_prefixed_str(val_enum);
                        w.i32(gamelogic_lib_id);
                    }
                }
            } else {
                // 0x01 ClassWithId referencing first_kvp_id
                w.u8(0x01);
                w.i32(elem_id);
                w.i32(first_kvp_id.unwrap_or(-6));
            }

            // Emit key value
            match &self.kind {
                NrbfDictKind::StringEncryptValue | NrbfDictKind::StringString => {
                    let sid = alloc_id();
                    w.u8(0x06); // BinaryObjectString
                    w.i32(sid);
                    w.len_prefixed_str(&entry.key);
                }
                NrbfDictKind::IntEncryptValue => {
                    let k = entry.key.parse::<i32>().map_err(|e| {
                        OwlError::Nrbf(format!("Invalid Int32 key '{}': {e}", entry.key))
                    })?;
                    w.i32(k);
                }
                NrbfDictKind::EnumEncryptValue { key_enum }
                | NrbfDictKind::EnumString { key_enum }
                | NrbfDictKind::EnumEnum { key_enum, .. } => {
                    let k = entry.key.parse::<i32>().map_err(|e| {
                        OwlError::Nrbf(format!("Invalid Enum key '{}': {e}", entry.key))
                    })?;
                    let oid = -alloc_id();
                    write_enum_value(&mut w, oid, key_enum, gamelogic_lib_id, k, &mut key_enum_schema_id);
                }
            }

            // Emit value
            match &self.kind {
                NrbfDictKind::StringEncryptValue
                | NrbfDictKind::IntEncryptValue
                | NrbfDictKind::EnumEncryptValue { .. } => {
                    let v = entry.value.parse::<i32>().map_err(|e| {
                        OwlError::Nrbf(format!("Invalid EncryptValue int '{}': {e}", entry.value))
                    })?;
                    let ev_id = alloc_id();
                    w.u8(0x09); // MemberReference -> EncryptValue object emitted after array
                    w.i32(ev_id);
                    deferred_encrypts.push((ev_id, v));
                }
                NrbfDictKind::StringString | NrbfDictKind::EnumString { .. } => {
                    let sid = alloc_id();
                    w.u8(0x06);
                    w.i32(sid);
                    w.len_prefixed_str(&entry.value);
                }
                NrbfDictKind::EnumEnum { val_enum, .. } => {
                    let v = entry.value.parse::<i32>().map_err(|e| {
                        OwlError::Nrbf(format!("Invalid Enum value '{}': {e}", entry.value))
                    })?;
                    let oid = -alloc_id();
                    write_enum_value(&mut w, oid, val_enum, gamelogic_lib_id, v, &mut val_enum_schema_id);
                }
            }
        }

        // Emit deferred EncryptValue objects
        let mut first_ev_id: Option<i32> = None;
        for (ev_id, real_val) in deferred_encrypts {
            let (m_val, enc_key) = encode_encrypt_int(real_val);
            if let Some(meta_id) = first_ev_id {
                w.u8(0x01); // ClassWithId
                w.i32(ev_id);
                w.i32(meta_id);
            } else {
                first_ev_id = Some(ev_id);
                w.u8(0x05); // ClassWithMembersAndTypes
                w.i32(ev_id);
                w.len_prefixed_str("EncryptValue");
                w.i32(3);
                w.len_prefixed_str("_showValue");
                w.len_prefixed_str("mValue");
                w.len_prefixed_str("encryptKey");
                w.u8(0x00);
                w.u8(0x00);
                w.u8(0x00);
                w.u8(0x08);
                w.u8(0x08);
                w.u8(0x08);
                w.i32(gamelogic_lib_id);
            }
            w.i32(real_val);
            w.i32(m_val);
            w.i32(enc_key);
        }

        w.u8(0x0B); // MessageEnd
        Ok(pad_power_of_two(w.buf, 512))
    }
}

fn write_enum_value(
    w: &mut BinWriter,
    oid: i32,
    enum_name: &str,
    lib_id: i32,
    val: i32,
    schema_id: &mut Option<i32>,
) {
    if let Some(meta_id) = *schema_id {
        w.u8(0x01);
        w.i32(oid);
        w.i32(meta_id);
    } else {
        *schema_id = Some(oid);
        w.u8(0x05);
        w.i32(oid);
        w.len_prefixed_str(enum_name);
        w.i32(1);
        w.len_prefixed_str("value__");
        w.u8(0x00); // Primitive
        w.u8(0x08); // Int32
        w.i32(lib_id);
    }
    w.i32(val);
}

#[derive(Debug, Clone)]
enum RawVal {
    Str(String),
    Int(i32),
    Ref(i32),
    Null,
}

fn resolve_raw_val(
    rv: &RawVal,
    strings: &HashMap<i32, String>,
    encrypts: &HashMap<i32, i32>,
) -> OwlResult<String> {
    match rv {
        RawVal::Str(s) => Ok(s.clone()),
        RawVal::Int(v) => Ok(v.to_string()),
        RawVal::Null => Ok(String::new()),
        RawVal::Ref(id) => {
            if let Some(v) = encrypts.get(id) {
                Ok(v.to_string())
            } else if let Some(s) = strings.get(id) {
                Ok(s.clone())
            } else {
                Err(OwlError::Nrbf(format!("Unresolved NRBF reference id {id}")))
            }
        }
    }
}

fn classify_dict_type(full: &str) -> OwlResult<NrbfDictKind> {
    let inner = full
        .strip_prefix("System.Collections.Generic.Dictionary`2[[")
        .and_then(|s| s.strip_suffix("]]"))
        .ok_or_else(|| OwlError::Nrbf(format!("Not a Dictionary`2 type: {full}")))?;

    // Split top-level `],[`
    let (k_part, v_part) = split_generic_args(inner)
        .ok_or_else(|| OwlError::Nrbf(format!("Cannot split generic args in: {full}")))?;

    let k_name = k_part.split(',').next().unwrap_or("").trim();
    let v_name = v_part.split(',').next().unwrap_or("").trim();

    match (k_name, v_name) {
        ("System.String", "EncryptValue") => Ok(NrbfDictKind::StringEncryptValue),
        ("System.Int32", "EncryptValue") => Ok(NrbfDictKind::IntEncryptValue),
        ("System.String", "System.String") => Ok(NrbfDictKind::StringString),
        (ke, "EncryptValue") if !ke.starts_with("System.") => Ok(NrbfDictKind::EnumEncryptValue {
            key_enum: ke.to_string(),
        }),
        (ke, "System.String") if !ke.starts_with("System.") => Ok(NrbfDictKind::EnumString {
            key_enum: ke.to_string(),
        }),
        (ke, ve)
            if !ke.starts_with("System.")
                && !ve.starts_with("System.")
                && !ve.contains("Dictionary")
                && !ve.contains("StatueData")
                && !ve.contains("TaskArgs") =>
        {
            Ok(NrbfDictKind::EnumEnum {
                key_enum: ke.to_string(),
                val_enum: ve.to_string(),
            })
        }
        _ => Err(OwlError::Nrbf(format!(
            "Unsupported Dictionary<{k_name}, {v_name}> for structured edit (preserved intact)"
        ))),
    }
}

fn split_generic_args(s: &str) -> Option<(&str, &str)> {
    let mut depth = 0i32;
    let bytes = s.as_bytes();
    for i in 0..bytes.len() {
        match bytes[i] {
            b'[' => depth += 1,
            b']' => {
                if depth == 0 && i + 2 < bytes.len() && bytes[i + 1] == b',' && bytes[i + 2] == b'['
                {
                    return Some((&s[..i], &s[i + 3..]));
                }
                depth -= 1;
            }
            _ => {}
        }
    }
    None
}

fn type_strings_for_kind(kind: &NrbfDictKind) -> (String, String, String, String) {
    let (k_full, v_full, comparer) = match kind {
        NrbfDictKind::StringEncryptValue => (
            MSCORLIB_STR.to_string(),
            format!("EncryptValue, {GAMELOGIC_ASM}"),
            "System.Collections.Generic.InternalStringComparer".to_string(),
        ),
        NrbfDictKind::IntEncryptValue => (
            MSCORLIB_I32.to_string(),
            format!("EncryptValue, {GAMELOGIC_ASM}"),
            format!("System.Collections.Generic.GenericEqualityComparer`1[[{MSCORLIB_I32}]]"),
        ),
        NrbfDictKind::EnumEncryptValue { key_enum } => {
            let kf = format!("{key_enum}, {GAMELOGIC_ASM}");
            (
                kf.clone(),
                format!("EncryptValue, {GAMELOGIC_ASM}"),
                format!("System.Collections.Generic.EnumEqualityComparer`1[[{kf}]]"),
            )
        }
        NrbfDictKind::StringString => (
            MSCORLIB_STR.to_string(),
            MSCORLIB_STR.to_string(),
            "System.Collections.Generic.InternalStringComparer".to_string(),
        ),
        NrbfDictKind::EnumString { key_enum } => {
            let kf = format!("{key_enum}, {GAMELOGIC_ASM}");
            (
                kf.clone(),
                MSCORLIB_STR.to_string(),
                format!("System.Collections.Generic.EnumEqualityComparer`1[[{kf}]]"),
            )
        }
        NrbfDictKind::EnumEnum { key_enum, val_enum } => {
            let kf = format!("{key_enum}, {GAMELOGIC_ASM}");
            let vf = format!("{val_enum}, {GAMELOGIC_ASM}");
            (
                kf.clone(),
                vf,
                format!("System.Collections.Generic.EnumEqualityComparer`1[[{kf}]]"),
            )
        }
    };
    let kvp_full = format!("System.Collections.Generic.KeyValuePair`2[[{k_full}],[{v_full}]]");
    (k_full, v_full, comparer, kvp_full)
}

fn skip_additional_info(r: &mut BinReader<'_>, bt: u8) -> OwlResult<()> {
    match bt {
        0x00 | 0x07 => {
            let _prim = r.u8()?;
            Ok(())
        }
        0x01 | 0x02 | 0x05 | 0x06 => Ok(()),
        0x03 => {
            let _s = r.len_prefixed_str()?;
            Ok(())
        }
        0x04 => {
            let _s = r.len_prefixed_str()?;
            let _lib = r.i32()?;
            Ok(())
        }
        other => Err(OwlError::Nrbf(format!(
            "Unknown BinaryTypeEnum 0x{other:02x}"
        ))),
    }
}

fn pad_power_of_two(mut buf: Vec<u8>, min_size: usize) -> Vec<u8> {
    let mut cap = min_size;
    while cap < buf.len() {
        cap <<= 1;
    }
    buf.resize(cap, 0);
    buf
}

fn next_prime_at_least(n: usize) -> usize {
    const PRIMES: &[usize] = &[
        3, 7, 11, 17, 23, 29, 37, 47, 59, 71, 89, 107, 131, 163, 197, 239, 293, 353, 431, 521,
        631, 761, 919, 1103, 1327, 1597, 1931, 2333, 2801, 3371, 4049, 4861, 5839, 7013, 8419,
        10103,
    ];
    for &p in PRIMES {
        if p >= n {
            return p;
        }
    }
    n.next_power_of_two() + 1
}

struct BinReader<'a> {
    data: &'a [u8],
    pos: usize,
}

impl<'a> BinReader<'a> {
    fn new(data: &'a [u8]) -> Self {
        Self { data, pos: 0 }
    }

    fn is_eof(&self) -> bool {
        self.pos >= self.data.len()
    }

    fn u8(&mut self) -> OwlResult<u8> {
        if self.pos >= self.data.len() {
            return Err(OwlError::Nrbf("Unexpected EOF reading u8".into()));
        }
        let b = self.data[self.pos];
        self.pos += 1;
        Ok(b)
    }

    fn i32(&mut self) -> OwlResult<i32> {
        if self.pos + 4 > self.data.len() {
            return Err(OwlError::Nrbf("Unexpected EOF reading i32".into()));
        }
        let v = i32::from_le_bytes(self.data[self.pos..self.pos + 4].try_into().unwrap());
        self.pos += 4;
        Ok(v)
    }

    fn read_7bit_len(&mut self) -> OwlResult<usize> {
        let mut val = 0usize;
        let mut shift = 0u32;
        for _ in 0..5 {
            let b = self.u8()?;
            val |= ((b & 0x7F) as usize) << shift;
            if (b & 0x80) == 0 {
                return Ok(val);
            }
            shift += 7;
        }
        Err(OwlError::Nrbf("Malformed 7-bit string length".into()))
    }

    fn len_prefixed_str(&mut self) -> OwlResult<String> {
        let len = self.read_7bit_len()?;
        if self.pos + len > self.data.len() {
            return Err(OwlError::Nrbf(format!(
                "Unexpected EOF reading string of len {len} at pos {}",
                self.pos
            )));
        }
        let s = std::str::from_utf8(&self.data[self.pos..self.pos + len])
            .map_err(|e| OwlError::Nrbf(format!("Invalid UTF-8 string: {e}")))?
            .to_string();
        self.pos += len;
        Ok(s)
    }
}

struct BinWriter {
    buf: Vec<u8>,
}

impl BinWriter {
    fn new() -> Self {
        Self {
            buf: Vec::with_capacity(1024),
        }
    }

    fn u8(&mut self, v: u8) {
        self.buf.push(v);
    }

    fn i32(&mut self, v: i32) {
        self.buf.extend_from_slice(&v.to_le_bytes());
    }

    fn write_7bit_len(&mut self, mut len: usize) {
        loop {
            let mut b = (len & 0x7F) as u8;
            len >>= 7;
            if len != 0 {
                b |= 0x80;
            }
            self.buf.push(b);
            if len == 0 {
                break;
            }
        }
    }

    fn len_prefixed_str(&mut self, s: &str) {
        let bytes = s.as_bytes();
        self.write_7bit_len(bytes.len());
        self.buf.extend_from_slice(bytes);
    }
}
