use std::collections::HashMap;
use std::fs;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::Path;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Mutex, OnceLock};

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring, JNI_TRUE};
use jni::JNIEnv;
use serde::Serialize;

use owledit_core::backup::{
    create_backup_from_staging, inspect_backup, verify_and_extract_to_staging,
};
use owledit_core::crypto::{sha256_hex, OwlCodec};
use owledit_core::nrbf::NrbfDictKind;
use owledit_core::sanitizer::sanitize_playerprefs_xml;
use owledit_core::session::{beautify_json, minify_json, validate_content, OwlSession};
use owledit_core::ssaid::{generate_random_ssaid, inspect_ssaid, update_ssaid_verified};

static NEXT_SESSION_ID: AtomicI64 = AtomicI64::new(1);
static SESSIONS: OnceLock<Mutex<HashMap<i64, OwlSession>>> = OnceLock::new();

fn registry() -> &'static Mutex<HashMap<i64, OwlSession>> {
    SESSIONS.get_or_init(|| Mutex::new(HashMap::new()))
}

#[derive(Serialize)]
struct Envelope<T: Serialize> {
    ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    data: Option<T>,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<String>,
}

fn ok_json<T: Serialize>(data: T) -> String {
    serde_json::to_string(&Envelope {
        ok: true,
        data: Some(data),
        error: None,
    })
    .unwrap_or_else(|e| err_json(format!("JSON serialize failed: {e}")))
}

fn err_json(msg: impl Into<String>) -> String {
    serde_json::to_string(&Envelope::<()>::from_err(msg.into()))
        .unwrap_or_else(|_| r#"{"ok":false,"error":"internal serialization failure"}"#.into())
}

impl Envelope<()> {
    fn from_err(error: String) -> Self {
        Self {
            ok: false,
            data: None,
            error: Some(error),
        }
    }
}

fn jstr(env: &mut JNIEnv<'_>, s: &JString<'_>) -> Result<String, String> {
    if s.is_null() {
        return Ok(String::new());
    }
    env.get_string(s)
        .map(|js| js.into())
        .map_err(|e| format!("JNI get_string failed: {e}"))
}

fn jbytes(env: &JNIEnv<'_>, arr: &JByteArray<'_>) -> Result<Vec<u8>, String> {
    if arr.is_null() {
        return Ok(Vec::new());
    }
    env.convert_byte_array(arr)
        .map_err(|e| format!("JNI convert_byte_array failed: {e}"))
}

fn return_jstring(env: &mut JNIEnv<'_>, s: String) -> jstring {
    match env.new_string(s) {
        Ok(js) => js.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

fn guarded<F: FnOnce(&mut JNIEnv<'_>) -> Result<String, String>>(
    env: &mut JNIEnv<'_>,
    f: F,
) -> jstring {
    let out = match catch_unwind(AssertUnwindSafe(|| f(env))) {
        Ok(Ok(s)) => s,
        Ok(Err(e)) => err_json(e),
        Err(_) => err_json("Rust core panicked unexpectedly"),
    };
    return_jstring(env, out)
}

fn build_codec(key_hex: &str, iv_hex: &str) -> Result<OwlCodec, String> {
    if key_hex.trim().is_empty() || iv_hex.trim().is_empty() {
        Ok(OwlCodec::default())
    } else {
        OwlCodec::from_hex(key_hex, iv_hex).map_err(|e| e.to_string())
    }
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeCoreBanner(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
) -> jstring {
    return_jstring(&mut env, owledit_core::core_banner())
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeOpenSession(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    xml_bytes: JByteArray<'_>,
    key_hex: JString<'_>,
    iv_hex: JString<'_>,
    extra_dict_json: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let bytes = jbytes(env, &xml_bytes)?;
        let kh = jstr(env, &key_hex)?;
        let ih = jstr(env, &iv_hex)?;
        let extra = jstr(env, &extra_dict_json)?;
        let codec = build_codec(&kh, &ih)?;
        let session = OwlSession::open_xml_bytes(
            &bytes,
            codec,
            if extra.trim().is_empty() {
                None
            } else {
                Some(extra.as_str())
            },
        )
        .map_err(|e| e.to_string())?;
        let dashboard = session.build_dashboard().map_err(|e| e.to_string())?;
        let sid = NEXT_SESSION_ID.fetch_add(1, Ordering::Relaxed);
        registry()
            .lock()
            .map_err(|_| "Session registry lock poisoned".to_string())?
            .insert(sid, session);

        #[derive(Serialize)]
        struct OpenOut {
            session_id: i64,
            dashboard: owledit_core::session::DashboardSummary,
        }
        Ok(ok_json(OpenOut {
            session_id: sid,
            dashboard,
        }))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeOpenSessionFromFile(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    xml_path: JString<'_>,
    key_hex: JString<'_>,
    iv_hex: JString<'_>,
    extra_dict_json: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let path = jstr(env, &xml_path)?;
        let bytes = fs::read(&path).map_err(|e| format!("Read '{path}' failed: {e}"))?;
        let kh = jstr(env, &key_hex)?;
        let ih = jstr(env, &iv_hex)?;
        let extra = jstr(env, &extra_dict_json)?;
        let codec = build_codec(&kh, &ih)?;
        let session = OwlSession::open_xml_bytes(
            &bytes,
            codec,
            if extra.trim().is_empty() {
                None
            } else {
                Some(extra.as_str())
            },
        )
        .map_err(|e| e.to_string())?;
        let dashboard = session.build_dashboard().map_err(|e| e.to_string())?;
        let sid = NEXT_SESSION_ID.fetch_add(1, Ordering::Relaxed);
        registry()
            .lock()
            .map_err(|_| "Session registry lock poisoned".to_string())?
            .insert(sid, session);

        #[derive(Serialize)]
        struct OpenOut {
            session_id: i64,
            dashboard: owledit_core::session::DashboardSummary,
        }
        Ok(ok_json(OpenOut {
            session_id: sid,
            dashboard,
        }))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeCloseSession(
    mut _env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
) {
    if let Ok(mut map) = registry().lock() {
        map.remove(&session_id);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeGetDashboard(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
) -> jstring {
    guarded(&mut env, |_| {
        let map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        let dash = session.build_dashboard().map_err(|e| e.to_string())?;
        Ok(ok_json(dash))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeQueryEntries(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
    search: JString<'_>,
    category: JString<'_>,
    only_modified: jboolean,
    offset: jint,
    limit: jint,
) -> jstring {
    guarded(&mut env, |env| {
        let q = jstr(env, &search)?;
        let cat = jstr(env, &category)?;
        let map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        let page = session.query_entries(
            &q,
            &cat,
            only_modified == JNI_TRUE,
            offset.max(0) as usize,
            limit.clamp(1, 500) as usize,
        );
        Ok(ok_json(page))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeInspectEntry(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
    key: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let k = jstr(env, &key)?;
        let map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        let info = session.inspect_entry(&k).map_err(|e| e.to_string())?;
        Ok(ok_json(info))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeSetEncryptInt(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
    key_name: JString<'_>,
    value: jint,
) -> jstring {
    guarded(&mut env, |env| {
        let kn = jstr(env, &key_name)?;
        let mut map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get_mut(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        let (m, ek) = session
            .set_encrypt_int_pair(&kn, value)
            .map_err(|e| e.to_string())?;
        #[derive(Serialize)]
        struct Out {
            m_value: i32,
            encrypt_key: i32,
            modified_count: usize,
        }
        Ok(ok_json(Out {
            m_value: m,
            encrypt_key: ek,
            modified_count: session.modified_count(),
        }))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeUpdateHero(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
    body_name: JString<'_>,
    has_unlocked: jboolean,
    unlocked: jboolean,
    has_level: jboolean,
    level: jint,
    has_second_skill: jboolean,
    second_skill: jboolean,
    unlock_all_skills: jboolean,
    unlock_all_styles: jboolean,
) -> jstring {
    guarded(&mut env, |env| {
        let body = jstr(env, &body_name)?;
        let mut map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get_mut(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        session
            .update_hero(
                &body,
                if has_unlocked == JNI_TRUE {
                    Some(unlocked == JNI_TRUE)
                } else {
                    None
                },
                if has_level == JNI_TRUE {
                    Some(level)
                } else {
                    None
                },
                if has_second_skill == JNI_TRUE {
                    Some(second_skill == JNI_TRUE)
                } else {
                    None
                },
                unlock_all_skills == JNI_TRUE,
                unlock_all_styles == JNI_TRUE,
            )
            .map_err(|e| e.to_string())?;
        Ok(ok_json(session.modified_count()))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativePatchNrbfEntry(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
    container_key: JString<'_>,
    entry_key: JString<'_>,
    entry_value: JString<'_>,
    default_kind_json: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let ck = jstr(env, &container_key)?;
        let ek = jstr(env, &entry_key)?;
        let ev = jstr(env, &entry_value)?;
        let dk = jstr(env, &default_kind_json)?;
        let default_kind: Option<NrbfDictKind> = if dk.trim().is_empty() {
            None
        } else {
            Some(serde_json::from_str(&dk).map_err(|e| format!("Invalid default_kind_json: {e}"))?)
        };
        let mut map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get_mut(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        session
            .patch_nrbf_entry(&ck, &ek, &ev, default_kind)
            .map_err(|e| e.to_string())?;
        Ok(ok_json(session.modified_count()))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeSetAllNrbfValues(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
    container_key: JString<'_>,
    uniform_value: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let ck = jstr(env, &container_key)?;
        let uv = jstr(env, &uniform_value)?;
        let mut map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get_mut(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        let changed = session
            .set_all_existing_nrbf(&ck, &uv)
            .map_err(|e| e.to_string())?;
        Ok(ok_json(changed))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeSetRawEntry(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
    key: JString<'_>,
    value: JString<'_>,
    encrypt: jboolean,
) -> jstring {
    guarded(&mut env, |env| {
        let k = jstr(env, &key)?;
        let v = jstr(env, &value)?;
        let mut map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get_mut(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        session
            .set_raw_or_plain(&k, &v, encrypt == JNI_TRUE)
            .map_err(|e| e.to_string())?;
        Ok(ok_json(session.modified_count()))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeApplyPreset(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
    preset_id: JString<'_>,
    extra_skins_json: JString<'_>,
    extra_items_json: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let pid = jstr(env, &preset_id)?;
        let es = jstr(env, &extra_skins_json)?;
        let ei = jstr(env, &extra_items_json)?;
        let skins: Option<Vec<String>> = if es.trim().is_empty() {
            None
        } else {
            serde_json::from_str(&es).ok()
        };
        let items: Option<Vec<String>> = if ei.trim().is_empty() {
            None
        } else {
            serde_json::from_str(&ei).ok()
        };
        let mut map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get_mut(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        let msg = session
            .apply_preset(&pid, skins.as_deref(), items.as_deref())
            .map_err(|e| e.to_string())?;
        #[derive(Serialize)]
        struct Out {
            message: String,
            modified_count: usize,
        }
        Ok(ok_json(Out {
            message: msg,
            modified_count: session.modified_count(),
        }))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeComputeDiff(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
) -> jstring {
    guarded(&mut env, |_| {
        let map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        Ok(ok_json(session.compute_diff()))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeCommitSessionToFile(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    session_id: jlong,
    out_path: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let path = jstr(env, &out_path)?;
        let map = registry()
            .lock()
            .map_err(|_| "Session lock poisoned".to_string())?;
        let session = map
            .get(&session_id)
            .ok_or_else(|| format!("Session {session_id} not found"))?;
        let commit = session.commit_xml_verified().map_err(|e| e.to_string())?;
        if let Some(parent) = Path::new(&path).parent() {
            fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        fs::write(&path, &commit.xml_bytes)
            .map_err(|e| format!("Failed writing staged XML '{path}': {e}"))?;
        #[derive(Serialize)]
        struct Out {
            staged_path: String,
            sha256: String,
            byte_len: usize,
            total_entries: usize,
            modified_entries: usize,
        }
        Ok(ok_json(Out {
            staged_path: path,
            sha256: commit.sha256,
            byte_len: commit.xml_bytes.len(),
            total_entries: commit.total_entries,
            modified_entries: commit.modified_entries,
        }))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeSanitizeXmlFile(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    in_path: JString<'_>,
    out_path: JString<'_>,
    extra_patterns_json: JString<'_>,
    remove_local_device_id: jboolean,
) -> jstring {
    guarded(&mut env, |env| {
        let inp = jstr(env, &in_path)?;
        let outp = jstr(env, &out_path)?;
        let ep = jstr(env, &extra_patterns_json)?;
        let extra: Vec<String> = if ep.trim().is_empty() {
            Vec::new()
        } else {
            serde_json::from_str(&ep).unwrap_or_default()
        };
        let bytes = fs::read(&inp).map_err(|e| format!("Read '{inp}' failed: {e}"))?;
        let res = sanitize_playerprefs_xml(&bytes, &extra, remove_local_device_id == JNI_TRUE)
            .map_err(|e| e.to_string())?;
        if let Some(parent) = Path::new(&outp).parent() {
            fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let out_bytes = res.xml_text.as_bytes();
        fs::write(&outp, out_bytes).map_err(|e| e.to_string())?;
        #[derive(Serialize)]
        struct Out {
            removed_keys: Vec<String>,
            remaining_entries: usize,
            sha256: String,
        }
        Ok(ok_json(Out {
            removed_keys: res.removed_keys,
            remaining_entries: res.remaining_entries,
            sha256: sha256_hex(out_bytes),
        }))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeCreateBackupFromStaging(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    staging_dir: JString<'_>,
    output_archive: JString<'_>,
    package_name: JString<'_>,
    profile_id: JString<'_>,
    uid: JString<'_>,
    note: JString<'_>,
    device_info: JString<'_>,
    epoch_ms: jlong,
) -> jstring {
    guarded(&mut env, |env| {
        let sdir = jstr(env, &staging_dir)?;
        let out_arc = jstr(env, &output_archive)?;
        let pkg = jstr(env, &package_name)?;
        let pid = jstr(env, &profile_id)?;
        let u = jstr(env, &uid)?;
        let nt = jstr(env, &note)?;
        let dev = jstr(env, &device_info)?;
        let manifest = create_backup_from_staging(
            Path::new(&sdir),
            Path::new(&out_arc),
            &pkg,
            &pid,
            if u.trim().is_empty() { None } else { Some(u) },
            &nt,
            &dev,
            epoch_ms.max(0) as u64,
        )
        .map_err(|e| e.to_string())?;
        Ok(ok_json(manifest))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeInspectBackup(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    archive_path: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let ap = jstr(env, &archive_path)?;
        let insp = inspect_backup(Path::new(&ap)).map_err(|e| e.to_string())?;
        Ok(ok_json(insp))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeVerifyAndExtractBackup(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    archive_path: JString<'_>,
    staging_dir: JString<'_>,
    selected_paths_json: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let ap = jstr(env, &archive_path)?;
        let sdir = jstr(env, &staging_dir)?;
        let sp = jstr(env, &selected_paths_json)?;
        let selected: Option<Vec<String>> = if sp.trim().is_empty() {
            None
        } else {
            serde_json::from_str(&sp).ok()
        };
        let extracted = verify_and_extract_to_staging(
            Path::new(&ap),
            Path::new(&sdir),
            selected.as_deref(),
        )
        .map_err(|e| e.to_string())?;
        Ok(ok_json(extracted))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeInspectSsaidFile(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    ssaid_path: JString<'_>,
    package_filter: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let path = jstr(env, &ssaid_path)?;
        let pf = jstr(env, &package_filter)?;
        let bytes = fs::read(&path).map_err(|e| format!("Read SSAID '{path}' failed: {e}"))?;
        let insp = inspect_ssaid(
            &bytes,
            if pf.trim().is_empty() {
                None
            } else {
                Some(pf.as_str())
            },
        )
        .map_err(|e| e.to_string())?;
        Ok(ok_json(insp))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeGenerateRandomSsaid(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
) -> jstring {
    return_jstring(&mut env, generate_random_ssaid())
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeUpdateSsaidFileVerified(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    in_path: JString<'_>,
    target_package: JString<'_>,
    new_ssaid: JString<'_>,
    out_path: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let inp = jstr(env, &in_path)?;
        let pkg = jstr(env, &target_package)?;
        let ns = jstr(env, &new_ssaid)?;
        let outp = jstr(env, &out_path)?;
        let bytes = fs::read(&inp).map_err(|e| format!("Read SSAID '{inp}' failed: {e}"))?;
        let upd = update_ssaid_verified(&bytes, &pkg, &ns).map_err(|e| e.to_string())?;
        if let Some(parent) = Path::new(&outp).parent() {
            fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        fs::write(&outp, &upd.updated_bytes).map_err(|e| e.to_string())?;
        #[derive(Serialize)]
        struct Out {
            format: owledit_core::ssaid::SsaidFormat,
            package: String,
            old_value: String,
            new_value: String,
            sha256: String,
            byte_len: usize,
        }
        Ok(ok_json(Out {
            format: upd.format,
            package: upd.package,
            old_value: upd.old_value,
            new_value: upd.new_value,
            sha256: sha256_hex(&upd.updated_bytes),
            byte_len: upd.updated_bytes.len(),
        }))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeBeautifyJson(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    input: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let s = jstr(env, &input)?;
        let out = beautify_json(&s).map_err(|e| e.to_string())?;
        Ok(ok_json(out))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeMinifyJson(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    input: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let s = jstr(env, &input)?;
        let out = minify_json(&s).map_err(|e| e.to_string())?;
        Ok(ok_json(out))
    })
}

#[no_mangle]
pub extern "system" fn Java_com_owledit_core_nativebridge_RustCoreJni_nativeValidateContent(
    mut env: JNIEnv<'_>,
    _cls: JClass<'_>,
    content: JString<'_>,
    format: JString<'_>,
) -> jstring {
    guarded(&mut env, |env| {
        let c = jstr(env, &content)?;
        let f = jstr(env, &format)?;
        Ok(ok_json(validate_content(&c, &f)))
    })
}
