use std::collections::HashMap;

use quick_xml::events::{BytesDecl, BytesEnd, BytesStart, BytesText, Event};
use quick_xml::{Reader, Writer};
use serde::{Deserialize, Serialize};

use crate::crypto::{unity_url_decode, unity_url_encode};
use crate::error::{OwlError, OwlResult};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum PrefValueType {
    String,
    Int,
    Long,
    Float,
    Boolean,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PrefEntry {
    pub value_type: PrefValueType,
    /// Raw attribute `name="..."` as written in XML (may be MD5 hex or URL-encoded plain key).
    pub raw_key: String,
    /// Raw value text (for `<string>`) or `value="..."` attribute (for primitives), as stored in XML.
    pub raw_value: String,
}

impl PrefEntry {
    pub fn decoded_key(&self) -> String {
        unity_url_decode(&self.raw_key).unwrap_or_else(|_| self.raw_key.clone())
    }
}

/// Lossless, order-preserving representation of Android Unity `*.v2.playerprefs.xml`.
#[derive(Debug, Clone)]
pub struct PlayerPrefsDoc {
    pub entries: Vec<PrefEntry>,
    /// Index from `raw_key` -> index in `entries`.
    index: HashMap<String, usize>,
}

impl Default for PlayerPrefsDoc {
    fn default() -> Self {
        Self::new()
    }
}

impl PlayerPrefsDoc {
    pub fn new() -> Self {
        Self {
            entries: Vec::new(),
            index: HashMap::new(),
        }
    }

    pub fn parse_bytes(xml: &[u8]) -> OwlResult<Self> {
        let mut reader = Reader::from_reader(xml);
        reader.config_mut().trim_text(false);

        let mut doc = Self::new();
        let mut buf = Vec::with_capacity(4096);
        let mut current_string_key: Option<String> = None;
        let mut current_string_val = String::new();

        loop {
            match reader.read_event_into(&mut buf) {
                Ok(Event::Start(ref e)) => {
                    let tag = e.name();
                    if tag.as_ref() == b"string" {
                        let name = read_attr(e, b"name")?;
                        current_string_key = Some(name);
                        current_string_val.clear();
                    }
                }
                Ok(Event::Text(e)) => {
                    if current_string_key.is_some() {
                        let text = e
                            .unescape()
                            .map_err(|err| OwlError::Xml(format!("XML text unescape error: {err}")))?;
                        current_string_val.push_str(&text);
                    }
                }
                Ok(Event::CData(e)) => {
                    if current_string_key.is_some() {
                        let text = std::str::from_utf8(&e)
                            .map_err(|err| OwlError::Xml(format!("CDATA UTF-8 error: {err}")))?;
                        current_string_val.push_str(text);
                    }
                }
                Ok(Event::End(ref e)) => {
                    if e.name().as_ref() == b"string" {
                        if let Some(raw_key) = current_string_key.take() {
                            doc.upsert_raw(
                                PrefValueType::String,
                                raw_key,
                                std::mem::take(&mut current_string_val),
                            );
                        }
                    }
                }
                Ok(Event::Empty(ref e)) => {
                    let tag = e.name();
                    match tag.as_ref() {
                        b"string" => {
                            let name = read_attr(e, b"name")?;
                            doc.upsert_raw(PrefValueType::String, name, String::new());
                        }
                        b"int" => {
                            let name = read_attr(e, b"name")?;
                            let val = read_attr(e, b"value")?;
                            doc.upsert_raw(PrefValueType::Int, name, val);
                        }
                        b"long" => {
                            let name = read_attr(e, b"name")?;
                            let val = read_attr(e, b"value")?;
                            doc.upsert_raw(PrefValueType::Long, name, val);
                        }
                        b"float" => {
                            let name = read_attr(e, b"name")?;
                            let val = read_attr(e, b"value")?;
                            doc.upsert_raw(PrefValueType::Float, name, val);
                        }
                        b"boolean" => {
                            let name = read_attr(e, b"name")?;
                            let val = read_attr(e, b"value")?;
                            doc.upsert_raw(PrefValueType::Boolean, name, val);
                        }
                        _ => {}
                    }
                }
                Ok(Event::Eof) => break,
                Err(e) => {
                    return Err(OwlError::Xml(format!(
                        "XML parse error at byte {}: {e}",
                        reader.error_position()
                    )));
                }
                _ => {}
            }
            buf.clear();
        }

        Ok(doc)
    }

    pub fn serialize_bytes(&self) -> OwlResult<Vec<u8>> {
        let mut out = Vec::with_capacity(self.entries.len() * 128 + 128);
        let mut writer = Writer::new(&mut out);

        // Match Android SharedPreferences XML declaration exactly
        writer
            .write_event(Event::Decl(BytesDecl::new("1.0", Some("utf-8"), Some("yes"))))
            .map_err(|e| OwlError::Xml(e.to_string()))?;
        writer
            .write_event(Event::Text(BytesText::new("\n")))
            .map_err(|e| OwlError::Xml(e.to_string()))?;

        writer
            .write_event(Event::Start(BytesStart::new("map")))
            .map_err(|e| OwlError::Xml(e.to_string()))?;

        for entry in &self.entries {
            writer
                .write_event(Event::Text(BytesText::new("\n    ")))
                .map_err(|e| OwlError::Xml(e.to_string()))?;

            match entry.value_type {
                PrefValueType::String => {
                    let mut elem = BytesStart::new("string");
                    elem.push_attribute(("name", entry.raw_key.as_str()));
                    writer
                        .write_event(Event::Start(elem))
                        .map_err(|e| OwlError::Xml(e.to_string()))?;
                    if !entry.raw_value.is_empty() {
                        writer
                            .write_event(Event::Text(BytesText::new(&entry.raw_value)))
                            .map_err(|e| OwlError::Xml(e.to_string()))?;
                    }
                    writer
                        .write_event(Event::End(BytesEnd::new("string")))
                        .map_err(|e| OwlError::Xml(e.to_string()))?;
                }
                PrefValueType::Int
                | PrefValueType::Long
                | PrefValueType::Float
                | PrefValueType::Boolean => {
                    let tag = match entry.value_type {
                        PrefValueType::Int => "int",
                        PrefValueType::Long => "long",
                        PrefValueType::Float => "float",
                        PrefValueType::Boolean => "boolean",
                        PrefValueType::String => unreachable!(),
                    };
                    let mut elem = BytesStart::new(tag);
                    elem.push_attribute(("name", entry.raw_key.as_str()));
                    elem.push_attribute(("value", entry.raw_value.as_str()));
                    writer
                        .write_event(Event::Empty(elem))
                        .map_err(|e| OwlError::Xml(e.to_string()))?;
                }
            }
        }

        writer
            .write_event(Event::Text(BytesText::new("\n")))
            .map_err(|e| OwlError::Xml(e.to_string()))?;
        writer
            .write_event(Event::End(BytesEnd::new("map")))
            .map_err(|e| OwlError::Xml(e.to_string()))?;
        writer
            .write_event(Event::Text(BytesText::new("\n")))
            .map_err(|e| OwlError::Xml(e.to_string()))?;

        Ok(out)
    }

    pub fn len(&self) -> usize {
        self.entries.len()
    }

    pub fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    pub fn get_raw(&self, raw_key: &str) -> Option<&PrefEntry> {
        self.index.get(raw_key).map(|&idx| &self.entries[idx])
    }

    /// Lookup by plain (decoded) key name (handles URL-encoded keys such as `SdkStateCache#2`).
    pub fn get_by_decoded_key(&self, decoded_key: &str) -> Option<&PrefEntry> {
        if let Some(e) = self.get_raw(decoded_key) {
            return Some(e);
        }
        let encoded = unity_url_encode(decoded_key);
        if let Some(e) = self.get_raw(&encoded) {
            return Some(e);
        }
        self.entries.iter().find(|e| e.decoded_key() == decoded_key)
    }

    pub fn upsert_raw(&mut self, value_type: PrefValueType, raw_key: String, raw_value: String) {
        if let Some(&idx) = self.index.get(&raw_key) {
            self.entries[idx].value_type = value_type;
            self.entries[idx].raw_value = raw_value;
        } else {
            let idx = self.entries.len();
            self.index.insert(raw_key.clone(), idx);
            self.entries.push(PrefEntry {
                value_type,
                raw_key,
                raw_value,
            });
        }
    }

    pub fn remove_raw(&mut self, raw_key: &str) -> Option<PrefEntry> {
        let idx = self.index.remove(raw_key)?;
        let removed = self.entries.remove(idx);
        self.rebuild_index();
        Some(removed)
    }

    pub fn retain<F>(&mut self, mut f: F) -> Vec<PrefEntry>
    where
        F: FnMut(&PrefEntry) -> bool,
    {
        let mut removed = Vec::new();
        let mut kept = Vec::with_capacity(self.entries.len());
        for e in self.entries.drain(..) {
            if f(&e) {
                kept.push(e);
            } else {
                removed.push(e);
            }
        }
        self.entries = kept;
        self.rebuild_index();
        removed
    }

    fn rebuild_index(&mut self) {
        self.index.clear();
        for (i, e) in self.entries.iter().enumerate() {
            self.index.insert(e.raw_key.clone(), i);
        }
    }
}

fn read_attr(e: &BytesStart<'_>, attr_name: &[u8]) -> OwlResult<String> {
    for attr in e.attributes().with_checks(false) {
        let attr = attr.map_err(|err| OwlError::Xml(format!("Attribute error: {err}")))?;
        if attr.key.as_ref() == attr_name {
            let val = attr
                .unescape_value()
                .map_err(|err| OwlError::Xml(format!("Attribute unescape error: {err}")))?;
            return Ok(val.into_owned());
        }
    }
    Err(OwlError::Xml(format!(
        "Missing attribute '{}'",
        String::from_utf8_lossy(attr_name)
    )))
}
