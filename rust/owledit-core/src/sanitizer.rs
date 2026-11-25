use regex::Regex;
use serde::{Deserialize, Serialize};

use crate::crypto::{md5_key, unity_url_decode};
use crate::error::{OwlError, OwlResult};
use crate::xml_prefs::PlayerPrefsDoc;

/// Verified default account / cloud-save / session keys in Otherworld Legends `v2.playerprefs.xml`.
pub const DEFAULT_OWL_SANITIZE_PATTERNS: &[&str] = &[
    r"^SdkStateCache.*$",
    r"^UsedAccounts.*$",
    r"^emptyLocalCloudSaveId$",
    r"^LastLoginUid$",
    r"^FrequentCheckCache_LoginTag$",
    r"^runtimeEmailArchive$",
    r"^unity\.cloud_userid$",
    r"^unity\.player_session.*$",
    r"^cloudSaveId$",
    r"^account.*$",
];

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AccountInfo {
    pub uid: Option<String>,
    pub cloud_save_id: Option<String>,
    pub email: Option<String>,
    pub found_keys: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SanitizedXmlResult {
    pub removed_keys: Vec<String>,
    pub remaining_entries: usize,
    pub xml_text: String,
}

pub fn find_account_info(doc: &PlayerPrefsDoc, extra_patterns: &[String]) -> OwlResult<AccountInfo> {
    let patterns = compile_patterns(extra_patterns)?;
    let local_device_md5 = md5_key("LocalDeviceId");

    let mut uid = None;
    let mut cloud_save_id = None;
    let mut email = None;
    let mut found_keys = Vec::new();

    for entry in &doc.entries {
        let dk = entry.decoded_key();
        if dk == "LastLoginUid" {
            let v = unity_url_decode(&entry.raw_value).unwrap_or_else(|_| entry.raw_value.clone());
            if !v.trim().is_empty() {
                uid = Some(v.trim().to_string());
            }
        } else if dk == "emptyLocalCloudSaveId" {
            let v = unity_url_decode(&entry.raw_value).unwrap_or_else(|_| entry.raw_value.clone());
            if !v.trim().is_empty() {
                cloud_save_id = Some(v.trim().to_string());
            }
        } else if dk.starts_with("SdkStateCache") {
            let v = unity_url_decode(&entry.raw_value).unwrap_or_else(|_| entry.raw_value.clone());
            if let Ok(json) = serde_json::from_str::<serde_json::Value>(&v) {
                if let Some(user) = json.get("User") {
                    if uid.is_none() {
                        if let Some(id) = user.get("Id").and_then(|x| x.as_i64()) {
                            uid = Some(id.to_string());
                        }
                    }
                    if let Some(em) = user.get("Email").and_then(|x| x.as_str()) {
                        email = Some(em.to_string());
                    }
                }
            }
        } else if dk.starts_with("UsedAccounts") && email.is_none() {
            let v = unity_url_decode(&entry.raw_value).unwrap_or_else(|_| entry.raw_value.clone());
            if let Ok(arr) = serde_json::from_str::<Vec<Option<String>>>(&v) {
                if let Some(em) = arr.into_iter().flatten().next() {
                    email = Some(em);
                }
            }
        }

        if entry.raw_key == local_device_md5 || patterns.iter().any(|re| re.is_match(&dk)) {
            found_keys.push(if entry.raw_key == local_device_md5 {
                "LocalDeviceId (MD5)".to_string()
            } else {
                dk
            });
        }
    }

    Ok(AccountInfo {
        uid,
        cloud_save_id,
        email,
        found_keys,
    })
}

pub fn sanitize_playerprefs_xml(
    xml_bytes: &[u8],
    extra_patterns: &[String],
    remove_local_device_id: bool,
) -> OwlResult<SanitizedXmlResult> {
    let mut doc = PlayerPrefsDoc::parse_bytes(xml_bytes)?;
    let patterns = compile_patterns(extra_patterns)?;
    let local_device_md5 = md5_key("LocalDeviceId");

    let removed = doc.retain(|entry| {
        if remove_local_device_id && entry.raw_key == local_device_md5 {
            return false;
        }
        let dk = entry.decoded_key();
        !patterns.iter().any(|re| re.is_match(&dk))
    });

    let out_bytes = doc.serialize_bytes()?;
    let xml_text = String::from_utf8(out_bytes)
        .map_err(|e| OwlError::Xml(format!("UTF-8 encode error: {e}")))?;

    Ok(SanitizedXmlResult {
        removed_keys: removed
            .into_iter()
            .map(|e| {
                if e.raw_key == local_device_md5 {
                    "LocalDeviceId".to_string()
                } else {
                    e.decoded_key()
                }
            })
            .collect(),
        remaining_entries: doc.len(),
        xml_text,
    })
}

fn compile_patterns(extra: &[String]) -> OwlResult<Vec<Regex>> {
    let mut res = Vec::new();
    for pat in DEFAULT_OWL_SANITIZE_PATTERNS {
        res.push(
            Regex::new(pat)
                .map_err(|e| OwlError::Validation(format!("Invalid regex '{pat}': {e}")))?,
        );
    }
    for pat in extra {
        if !pat.trim().is_empty() {
            res.push(
                Regex::new(pat)
                    .map_err(|e| OwlError::Validation(format!("Invalid regex '{pat}': {e}")))?,
            );
        }
    }
    Ok(res)
}
