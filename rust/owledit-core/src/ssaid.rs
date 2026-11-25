//! Android `settings_ssaid.xml` inspector and verified updater.
//! Supports BOTH:
//! - Plain XML (Android 8–11 and most PC Android emulators like LDPlayer/MuMu/Nox/BlueStacks)
//! - Android 12+ Binary XML (`ABX\0` magic header) — pure Rust parser & serializer!

use quick_xml::events::{BytesStart, Event};
use quick_xml::{Reader, Writer};
use rand::Rng;
use serde::{Deserialize, Serialize};

use crate::error::{OwlError, OwlResult};

const ABX_MAGIC: &[u8; 4] = b"ABX\0";

// Android TypedXmlPullParser / BinaryXmlSerializer token & type constants
const TOKEN_START_DOCUMENT: u8 = 0;
const TOKEN_END_DOCUMENT: u8 = 1;
const TOKEN_START_TAG: u8 = 2;
const TOKEN_END_TAG: u8 = 3;
const TOKEN_TEXT: u8 = 4;
const TOKEN_CDSECT: u8 = 5;
const TOKEN_ENTITY_REF: u8 = 6;
const TOKEN_IGNORABLE_WHITESPACE: u8 = 7;
const TOKEN_PROCESSING_INSTRUCTION: u8 = 8;
const TOKEN_COMMENT: u8 = 9;
const TOKEN_DOCDECL: u8 = 10;
const TOKEN_ATTRIBUTE: u8 = 15;

const TYPE_NULL: u8 = 1 << 4;
const TYPE_STRING: u8 = 2 << 4;
const TYPE_STRING_INTERNED: u8 = 3 << 4;
const TYPE_BYTES_HEX: u8 = 4 << 4;
const TYPE_BYTES_BASE64: u8 = 5 << 4;
const TYPE_INT: u8 = 6 << 4;
const TYPE_INT_HEX: u8 = 7 << 4;
const TYPE_LONG: u8 = 8 << 4;
const TYPE_LONG_HEX: u8 = 9 << 4;
const TYPE_FLOAT: u8 = 10 << 4;
const TYPE_DOUBLE: u8 = 11 << 4;
const TYPE_BOOLEAN_TRUE: u8 = 12 << 4;
const TYPE_BOOLEAN_FALSE: u8 = 13 << 4;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum SsaidFormat {
    PlainXml,
    AbxBinary,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SsaidEntry {
    pub id: String,
    pub name: String,
    pub package: String,
    pub value: String,
    pub default_value: String,
    pub tag: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SsaidInspection {
    pub format: SsaidFormat,
    pub total_entries: usize,
    pub entries: Vec<SsaidEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SsaidUpdateResult {
    pub format: SsaidFormat,
    pub package: String,
    pub old_value: String,
    pub new_value: String,
    pub updated_bytes: Vec<u8>,
}

/// Generates a valid 16-character lowercase hex Android SSAID.
pub fn generate_random_ssaid() -> String {
    let mut rng = rand::thread_rng();
    let mut bytes = [0u8; 8];
    rng.fill(&mut bytes);
    hex::encode(bytes)
}

pub fn detect_ssaid_format(bytes: &[u8]) -> SsaidFormat {
    if bytes.starts_with(ABX_MAGIC) {
        SsaidFormat::AbxBinary
    } else {
        SsaidFormat::PlainXml
    }
}

pub fn inspect_ssaid(bytes: &[u8], package_filter: Option<&str>) -> OwlResult<SsaidInspection> {
    let format = detect_ssaid_format(bytes);
    let mut entries = match format {
        SsaidFormat::PlainXml => inspect_plain_xml(bytes)?,
        SsaidFormat::AbxBinary => inspect_abx(bytes)?,
    };
    let total_entries = entries.len();
    if let Some(pkg) = package_filter {
        if !pkg.is_empty() {
            entries.retain(|e| e.package.contains(pkg) || e.name.contains(pkg));
        }
    }
    Ok(SsaidInspection {
        format,
        total_entries,
        entries,
    })
}

/// Updates the SSAID `value` (and `defaultValue`) for `target_package` and verifies by re-parsing!
pub fn update_ssaid_verified(
    bytes: &[u8],
    target_package: &str,
    new_ssaid: &str,
) -> OwlResult<SsaidUpdateResult> {
    let clean = new_ssaid.trim().to_ascii_lowercase();
    if clean.len() != 16 || !clean.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err(OwlError::Ssaid(format!(
            "SSAID must be exactly 16 hex characters, got '{clean}'"
        )));
    }

    let format = detect_ssaid_format(bytes);
    let (old_value, updated_bytes) = match format {
        SsaidFormat::PlainXml => update_plain_xml(bytes, target_package, &clean)?,
        SsaidFormat::AbxBinary => update_abx(bytes, target_package, &clean)?,
    };

    // Round-trip self-verification before returning bytes for root write!
    let verify = inspect_ssaid(&updated_bytes, Some(target_package))?;
    let matched = verify
        .entries
        .iter()
        .find(|e| e.package == target_package)
        .ok_or_else(|| {
            OwlError::Ssaid(format!(
                "Round-trip verification failed: package '{target_package}' missing after patch"
            ))
        })?;
    if matched.value != clean {
        return Err(OwlError::Ssaid(format!(
            "Round-trip verification failed: expected '{clean}', got '{}'",
            matched.value
        )));
    }

    Ok(SsaidUpdateResult {
        format,
        package: target_package.to_string(),
        old_value,
        new_value: clean,
        updated_bytes,
    })
}

// ============================================================================
// Plain XML implementation (Android 8-11 & most emulators)
// ============================================================================

fn inspect_plain_xml(bytes: &[u8]) -> OwlResult<Vec<SsaidEntry>> {
    let mut reader = Reader::from_reader(bytes);
    let mut buf = Vec::new();
    let mut entries = Vec::new();
    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Empty(ref e)) | Ok(Event::Start(ref e)) if e.name().as_ref() == b"setting" => {
                entries.push(extract_plain_entry(e)?);
            }
            Ok(Event::Eof) => break,
            Err(e) => return Err(OwlError::Ssaid(format!("Plain XML parse error: {e}"))),
            _ => {}
        }
        buf.clear();
    }
    Ok(entries)
}

fn extract_plain_entry(e: &BytesStart<'_>) -> OwlResult<SsaidEntry> {
    let mut entry = SsaidEntry {
        id: String::new(),
        name: String::new(),
        package: String::new(),
        value: String::new(),
        default_value: String::new(),
        tag: String::new(),
    };
    for attr in e.attributes().with_checks(false) {
        let attr = attr.map_err(|err| OwlError::Ssaid(err.to_string()))?;
        let val = attr
            .unescape_value()
            .map_err(|err| OwlError::Ssaid(err.to_string()))?
            .into_owned();
        match attr.key.as_ref() {
            b"id" => entry.id = val,
            b"name" => entry.name = val,
            b"package" => entry.package = val,
            b"value" => entry.value = val,
            b"defaultValue" => entry.default_value = val,
            b"tag" => entry.tag = val,
            _ => {}
        }
    }
    Ok(entry)
}

fn update_plain_xml(
    bytes: &[u8],
    target_package: &str,
    new_ssaid: &str,
) -> OwlResult<(String, Vec<u8>)> {
    let mut reader = Reader::from_reader(bytes);
    let mut out = Vec::with_capacity(bytes.len() + 64);
    let mut writer = Writer::new(&mut out);
    let mut buf = Vec::new();
    let mut old_val: Option<String> = None;

    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Empty(ref e)) if e.name().as_ref() == b"setting" => {
                let entry = extract_plain_entry(e)?;
                if entry.package == target_package {
                    old_val = Some(entry.value.clone());
                    let patched = rebuild_setting_tag(e, new_ssaid)?;
                    writer
                        .write_event(Event::Empty(patched))
                        .map_err(|err| OwlError::Ssaid(err.to_string()))?;
                } else {
                    writer
                        .write_event(Event::Empty(e.to_owned()))
                        .map_err(|err| OwlError::Ssaid(err.to_string()))?;
                }
            }
            Ok(Event::Start(ref e)) if e.name().as_ref() == b"setting" => {
                let entry = extract_plain_entry(e)?;
                if entry.package == target_package {
                    old_val = Some(entry.value.clone());
                    let patched = rebuild_setting_tag(e, new_ssaid)?;
                    writer
                        .write_event(Event::Start(patched))
                        .map_err(|err| OwlError::Ssaid(err.to_string()))?;
                } else {
                    writer
                        .write_event(Event::Start(e.to_owned()))
                        .map_err(|err| OwlError::Ssaid(err.to_string()))?;
                }
            }
            Ok(Event::Eof) => break,
            Ok(ev) => {
                writer
                    .write_event(ev.into_owned())
                    .map_err(|err| OwlError::Ssaid(err.to_string()))?;
            }
            Err(e) => return Err(OwlError::Ssaid(format!("Plain XML rewrite error: {e}"))),
        }
        buf.clear();
    }

    let old = old_val.ok_or_else(|| {
        OwlError::Ssaid(format!(
            "Package '{target_package}' not found in settings_ssaid.xml (launch game once first)"
        ))
    })?;
    Ok((old, out))
}

fn rebuild_setting_tag(e: &BytesStart<'_>, new_ssaid: &str) -> OwlResult<BytesStart<'static>> {
    let mut elem = BytesStart::new("setting");
    let mut has_value = false;
    let mut has_default = false;
    for attr in e.attributes().with_checks(false) {
        let attr = attr.map_err(|err| OwlError::Ssaid(err.to_string()))?;
        let key_str = std::str::from_utf8(attr.key.as_ref())
            .map_err(|err| OwlError::Ssaid(err.to_string()))?;
        let val_str = attr
            .unescape_value()
            .map_err(|err| OwlError::Ssaid(err.to_string()))?;
        if key_str == "value" {
            has_value = true;
            elem.push_attribute(("value", new_ssaid));
        } else if key_str == "defaultValue" {
            has_default = true;
            elem.push_attribute(("defaultValue", new_ssaid));
        } else {
            elem.push_attribute((key_str, val_str.as_ref()));
        }
    }
    if !has_value {
        elem.push_attribute(("value", new_ssaid));
    }
    if !has_default {
        elem.push_attribute(("defaultValue", new_ssaid));
    }
    Ok(elem)
}

// ============================================================================
// Android 12+ ABX Binary XML implementation (`ABX\0`)
// ============================================================================

#[derive(Debug, Clone)]
enum AbxValue {
    Null,
    String(String),
    StringInterned(String),
    BytesHex(Vec<u8>),
    BytesBase64(Vec<u8>),
    Int(i32),
    IntHex(i32),
    Long(i64),
    LongHex(i64),
    Float(f32),
    Double(f64),
    Bool(bool),
}

impl AbxValue {
    fn as_display_string(&self) -> String {
        match self {
            AbxValue::Null => String::new(),
            AbxValue::String(s) | AbxValue::StringInterned(s) => s.clone(),
            AbxValue::BytesHex(b) => hex::encode(b),
            AbxValue::BytesBase64(b) => {
                use base64::Engine as _;
                base64::engine::general_purpose::STANDARD.encode(b)
            }
            AbxValue::Int(v) => v.to_string(),
            AbxValue::IntHex(v) => format!("{v:x}"),
            AbxValue::Long(v) => v.to_string(),
            AbxValue::LongHex(v) => format!("{v:x}"),
            AbxValue::Float(v) => v.to_string(),
            AbxValue::Double(v) => v.to_string(),
            AbxValue::Bool(v) => v.to_string(),
        }
    }
}

#[derive(Debug, Clone)]
enum AbxEvent {
    StartDocument,
    EndDocument,
    StartTag(String),
    EndTag(String),
    Attribute { name: String, value: AbxValue },
    TextLike { token: u8, value: AbxValue },
}

fn parse_abx_events(bytes: &[u8]) -> OwlResult<Vec<AbxEvent>> {
    if !bytes.starts_with(ABX_MAGIC) {
        return Err(OwlError::Ssaid("Missing ABX\\0 header".into()));
    }
    let mut pos = 4usize;
    let mut interned: Vec<String> = Vec::new();
    let mut events = Vec::new();

    fn read_u8(b: &[u8], pos: &mut usize) -> OwlResult<u8> {
        if *pos >= b.len() {
            return Err(OwlError::Ssaid("ABX EOF u8".into()));
        }
        let v = b[*pos];
        *pos += 1;
        Ok(v)
    }
    fn read_u16_be(b: &[u8], pos: &mut usize) -> OwlResult<u16> {
        if *pos + 2 > b.len() {
            return Err(OwlError::Ssaid("ABX EOF u16".into()));
        }
        let v = u16::from_be_bytes([b[*pos], b[*pos + 1]]);
        *pos += 2;
        Ok(v)
    }
    fn read_i32_be(b: &[u8], pos: &mut usize) -> OwlResult<i32> {
        if *pos + 4 > b.len() {
            return Err(OwlError::Ssaid("ABX EOF i32".into()));
        }
        let v = i32::from_be_bytes(b[*pos..*pos + 4].try_into().unwrap());
        *pos += 4;
        Ok(v)
    }
    fn read_i64_be(b: &[u8], pos: &mut usize) -> OwlResult<i64> {
        if *pos + 8 > b.len() {
            return Err(OwlError::Ssaid("ABX EOF i64".into()));
        }
        let v = i64::from_be_bytes(b[*pos..*pos + 8].try_into().unwrap());
        *pos += 8;
        Ok(v)
    }
    fn read_utf(b: &[u8], pos: &mut usize) -> OwlResult<String> {
        let len = read_u16_be(b, pos)? as usize;
        if *pos + len > b.len() {
            return Err(OwlError::Ssaid("ABX EOF utf".into()));
        }
        let s = std::str::from_utf8(&b[*pos..*pos + len])
            .map_err(|e| OwlError::Ssaid(format!("ABX UTF-8 error: {e}")))?
            .to_string();
        *pos += len;
        Ok(s)
    }
    fn read_interned(b: &[u8], pos: &mut usize, pool: &mut Vec<String>) -> OwlResult<String> {
        let idx = read_u16_be(b, pos)?;
        if idx == 0xFFFF {
            let s = read_utf(b, pos)?;
            pool.push(s.clone());
            Ok(s)
        } else {
            pool.get(idx as usize).cloned().ok_or_else(|| {
                OwlError::Ssaid(format!(
                    "ABX invalid interned string index {idx} (pool size {})",
                    pool.len()
                ))
            })
        }
    }
    fn read_val(
        b: &[u8],
        pos: &mut usize,
        ty: u8,
        pool: &mut Vec<String>,
    ) -> OwlResult<AbxValue> {
        match ty {
            TYPE_NULL => Ok(AbxValue::Null),
            TYPE_STRING => Ok(AbxValue::String(read_utf(b, pos)?)),
            TYPE_STRING_INTERNED => Ok(AbxValue::StringInterned(read_interned(b, pos, pool)?)),
            TYPE_BYTES_HEX => {
                let len = read_u16_be(b, pos)? as usize;
                if *pos + len > b.len() {
                    return Err(OwlError::Ssaid("ABX EOF bytes hex".into()));
                }
                let v = b[*pos..*pos + len].to_vec();
                *pos += len;
                Ok(AbxValue::BytesHex(v))
            }
            TYPE_BYTES_BASE64 => {
                let len = read_u16_be(b, pos)? as usize;
                if *pos + len > b.len() {
                    return Err(OwlError::Ssaid("ABX EOF bytes b64".into()));
                }
                let v = b[*pos..*pos + len].to_vec();
                *pos += len;
                Ok(AbxValue::BytesBase64(v))
            }
            TYPE_INT => Ok(AbxValue::Int(read_i32_be(b, pos)?)),
            TYPE_INT_HEX => Ok(AbxValue::IntHex(read_i32_be(b, pos)?)),
            TYPE_LONG => Ok(AbxValue::Long(read_i64_be(b, pos)?)),
            TYPE_LONG_HEX => Ok(AbxValue::LongHex(read_i64_be(b, pos)?)),
            TYPE_FLOAT => Ok(AbxValue::Float(f32::from_bits(
                read_i32_be(b, pos)? as u32
            ))),
            TYPE_DOUBLE => Ok(AbxValue::Double(f64::from_bits(
                read_i64_be(b, pos)? as u64
            ))),
            TYPE_BOOLEAN_TRUE => Ok(AbxValue::Bool(true)),
            TYPE_BOOLEAN_FALSE => Ok(AbxValue::Bool(false)),
            other => Err(OwlError::Ssaid(format!("Unknown ABX data type 0x{other:02x}"))),
        }
    }

    while pos < bytes.len() {
        let header = read_u8(bytes, &mut pos)?;
        let token = header & 0x0F;
        let ty = header & 0xF0;

        match token {
            TOKEN_START_DOCUMENT => events.push(AbxEvent::StartDocument),
            TOKEN_END_DOCUMENT => {
                events.push(AbxEvent::EndDocument);
                break;
            }
            TOKEN_START_TAG => {
                let name = read_interned(bytes, &mut pos, &mut interned)?;
                events.push(AbxEvent::StartTag(name));
            }
            TOKEN_END_TAG => {
                let name = read_interned(bytes, &mut pos, &mut interned)?;
                events.push(AbxEvent::EndTag(name));
            }
            TOKEN_ATTRIBUTE => {
                let name = read_interned(bytes, &mut pos, &mut interned)?;
                let value = read_val(bytes, &mut pos, ty, &mut interned)?;
                events.push(AbxEvent::Attribute { name, value });
            }
            TOKEN_TEXT
            | TOKEN_CDSECT
            | TOKEN_ENTITY_REF
            | TOKEN_IGNORABLE_WHITESPACE
            | TOKEN_PROCESSING_INSTRUCTION
            | TOKEN_COMMENT
            | TOKEN_DOCDECL => {
                let value = read_val(bytes, &mut pos, ty, &mut interned)?;
                events.push(AbxEvent::TextLike { token, value });
            }
            other => {
                return Err(OwlError::Ssaid(format!(
                    "Unknown ABX token 0x{other:02x} at offset {}",
                    pos - 1
                )));
            }
        }
    }

    Ok(events)
}

fn serialize_abx_events(events: &[AbxEvent]) -> OwlResult<Vec<u8>> {
    let mut out = Vec::with_capacity(4096);
    out.extend_from_slice(ABX_MAGIC);
    let mut pool: Vec<String> = Vec::new();

    fn write_utf(out: &mut Vec<u8>, s: &str) -> OwlResult<()> {
        let b = s.as_bytes();
        if b.len() > u16::MAX as usize {
            return Err(OwlError::Ssaid("ABX string exceeds u16 length".into()));
        }
        out.extend_from_slice(&(b.len() as u16).to_be_bytes());
        out.extend_from_slice(b);
        Ok(())
    }
    fn write_interned(out: &mut Vec<u8>, s: &str, pool: &mut Vec<String>) -> OwlResult<()> {
        if let Some(idx) = pool.iter().position(|x| x == s) {
            out.extend_from_slice(&(idx as u16).to_be_bytes());
        } else {
            out.extend_from_slice(&0xFFFFu16.to_be_bytes());
            write_utf(out, s)?;
            pool.push(s.to_string());
        }
        Ok(())
    }
    fn type_byte(v: &AbxValue) -> u8 {
        match v {
            AbxValue::Null => TYPE_NULL,
            AbxValue::String(_) => TYPE_STRING,
            AbxValue::StringInterned(_) => TYPE_STRING_INTERNED,
            AbxValue::BytesHex(_) => TYPE_BYTES_HEX,
            AbxValue::BytesBase64(_) => TYPE_BYTES_BASE64,
            AbxValue::Int(_) => TYPE_INT,
            AbxValue::IntHex(_) => TYPE_INT_HEX,
            AbxValue::Long(_) => TYPE_LONG,
            AbxValue::LongHex(_) => TYPE_LONG_HEX,
            AbxValue::Float(_) => TYPE_FLOAT,
            AbxValue::Double(_) => TYPE_DOUBLE,
            AbxValue::Bool(true) => TYPE_BOOLEAN_TRUE,
            AbxValue::Bool(false) => TYPE_BOOLEAN_FALSE,
        }
    }
    fn write_val(out: &mut Vec<u8>, v: &AbxValue, pool: &mut Vec<String>) -> OwlResult<()> {
        match v {
            AbxValue::Null | AbxValue::Bool(_) => Ok(()),
            AbxValue::String(s) => write_utf(out, s),
            AbxValue::StringInterned(s) => write_interned(out, s, pool),
            AbxValue::BytesHex(b) | AbxValue::BytesBase64(b) => {
                out.extend_from_slice(&(b.len() as u16).to_be_bytes());
                out.extend_from_slice(b);
                Ok(())
            }
            AbxValue::Int(x) | AbxValue::IntHex(x) => {
                out.extend_from_slice(&x.to_be_bytes());
                Ok(())
            }
            AbxValue::Long(x) | AbxValue::LongHex(x) => {
                out.extend_from_slice(&x.to_be_bytes());
                Ok(())
            }
            AbxValue::Float(x) => {
                out.extend_from_slice(&x.to_bits().to_be_bytes());
                Ok(())
            }
            AbxValue::Double(x) => {
                out.extend_from_slice(&x.to_bits().to_be_bytes());
                Ok(())
            }
        }
    }

    for ev in events {
        match ev {
            AbxEvent::StartDocument => out.push(TOKEN_START_DOCUMENT | TYPE_NULL),
            AbxEvent::EndDocument => out.push(TOKEN_END_DOCUMENT | TYPE_NULL),
            AbxEvent::StartTag(name) => {
                out.push(TOKEN_START_TAG | TYPE_STRING_INTERNED);
                write_interned(&mut out, name, &mut pool)?;
            }
            AbxEvent::EndTag(name) => {
                out.push(TOKEN_END_TAG | TYPE_STRING_INTERNED);
                write_interned(&mut out, name, &mut pool)?;
            }
            AbxEvent::Attribute { name, value } => {
                out.push(TOKEN_ATTRIBUTE | type_byte(value));
                write_interned(&mut out, name, &mut pool)?;
                write_val(&mut out, value, &mut pool)?;
            }
            AbxEvent::TextLike { token, value } => {
                out.push(*token | type_byte(value));
                write_val(&mut out, value, &mut pool)?;
            }
        }
    }

    Ok(out)
}

fn inspect_abx(bytes: &[u8]) -> OwlResult<Vec<SsaidEntry>> {
    let events = parse_abx_events(bytes)?;
    let mut entries = Vec::new();
    let mut cur: Option<SsaidEntry> = None;

    for ev in events {
        match ev {
            AbxEvent::StartTag(name) => {
                if let Some(e) = cur.take() {
                    entries.push(e);
                }
                if name == "setting" {
                    cur = Some(SsaidEntry {
                        id: String::new(),
                        name: String::new(),
                        package: String::new(),
                        value: String::new(),
                        default_value: String::new(),
                        tag: String::new(),
                    });
                }
            }
            AbxEvent::Attribute { name, value } => {
                if let Some(ref mut e) = cur {
                    let s = value.as_display_string();
                    match name.as_str() {
                        "id" => e.id = s,
                        "name" => e.name = s,
                        "package" => e.package = s,
                        "value" => e.value = s,
                        "defaultValue" => e.default_value = s,
                        "tag" => e.tag = s,
                        _ => {}
                    }
                }
            }
            AbxEvent::EndTag(name) if name == "setting" => {
                if let Some(e) = cur.take() {
                    entries.push(e);
                }
            }
            _ => {}
        }
    }
    if let Some(e) = cur.take() {
        entries.push(e);
    }
    Ok(entries)
}

fn update_abx(
    bytes: &[u8],
    target_package: &str,
    new_ssaid: &str,
) -> OwlResult<(String, Vec<u8>)> {
    let mut events = parse_abx_events(bytes)?;
    let mut old_val: Option<String> = None;

    let mut i = 0;
    while i < events.len() {
        if let AbxEvent::StartTag(ref name) = events[i] {
            if name == "setting" {
                // Find the attribute slice immediately following StartTag("setting")
                let attr_start = i + 1;
                let mut attr_end = attr_start;
                while attr_end < events.len() && matches!(events[attr_end], AbxEvent::Attribute { .. }) {
                    attr_end += 1;
                }
                let is_target = events[attr_start..attr_end].iter().any(|ev| {
                    matches!(ev, AbxEvent::Attribute { name, value } if name == "package" && value.as_display_string() == target_package)
                });
                if is_target {
                    for ev in &mut events[attr_start..attr_end] {
                        if let AbxEvent::Attribute { name, value } = ev {
                            if name == "value" {
                                old_val = Some(value.as_display_string());
                                *value = AbxValue::String(new_ssaid.to_string());
                            } else if name == "defaultValue" {
                                *value = AbxValue::String(new_ssaid.to_string());
                            }
                        }
                    }
                }
                i = attr_end;
                continue;
            }
        }
        i += 1;
    }

    let old = old_val.ok_or_else(|| {
        OwlError::Ssaid(format!(
            "Package '{target_package}' not found in ABX settings_ssaid.xml"
        ))
    })?;
    let updated = serialize_abx_events(&events)?;
    Ok((old, updated))
}
