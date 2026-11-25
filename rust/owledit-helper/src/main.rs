//! `owledit-helper` — Lightweight native Rust root helper binary for rooted Android devices
//! and rooted PC emulators (LDPlayer, MuMu, BlueStacks, Nox, MEmu).
//!
//! Supports both one-shot CLI invocations and persistent JSON-Lines `serve` mode over `su` stdin/stdout
//! to eliminate repeated `su` spawn overhead.

use std::env;
use std::fs::{self, File, OpenOptions};
use std::io::{self, BufRead, Read, Write};
use std::path::Path;

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

#[derive(Serialize)]
struct HelperResponse<T: Serialize> {
    ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    data: Option<T>,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<String>,
}

#[derive(Serialize)]
struct PingInfo {
    version: &'static str,
    arch: &'static str,
    uid: u32,
    euid: u32,
}

#[derive(Serialize)]
struct StatInfo {
    path: String,
    exists: bool,
    is_dir: bool,
    size: u64,
    mode_octal: String,
    uid: u32,
    gid: u32,
    #[serde(skip_serializing_if = "Option::is_none")]
    sha256: Option<String>,
}

#[derive(Serialize)]
struct DirEntryInfo {
    name: String,
    path: String,
    is_dir: bool,
    size: u64,
    mode_octal: String,
    uid: u32,
    gid: u32,
}

#[derive(Serialize)]
struct AtomicWriteResult {
    dst: String,
    size: u64,
    sha256: String,
    mode_octal: String,
    uid: u32,
    gid: u32,
}

#[derive(Deserialize)]
#[serde(tag = "cmd", rename_all = "kebab-case")]
enum ServeRequest {
    Ping,
    Stat {
        path: String,
        #[serde(default)]
        hash: bool,
    },
    Ls {
        dir: String,
    },
    Sha256 {
        path: String,
    },
    Copy {
        src: String,
        dst: String,
        mode_octal: Option<String>,
    },
    AtomicWrite {
        src: String,
        dst: String,
        mode_octal: String,
        uid: u32,
        gid: u32,
    },
    Quit,
}

fn main() {
    let args: Vec<String> = env::args().collect();
    if args.len() < 2 {
        eprintln!("Usage: owledit-helper <ping|stat <path> [--hash]|ls <dir>|sha256 <path>|copy <src> <dst> [mode]|atomic-write <src> <dst> <mode_octal> <uid> <gid>|serve>");
        std::process::exit(1);
    }

    let out = match args[1].as_str() {
        "serve" => {
            run_serve();
            return;
        }
        "ping" => wrap(Ok(do_ping())),
        "stat" => {
            if args.len() < 3 {
                err_str("Missing <path>")
            } else {
                let want_hash = args.iter().any(|a| a == "--hash");
                wrap(do_stat(&args[2], want_hash))
            }
        }
        "ls" => {
            if args.len() < 3 {
                err_str("Missing <dir>")
            } else {
                wrap(do_ls(&args[2]))
            }
        }
        "sha256" => {
            if args.len() < 3 {
                err_str("Missing <path>")
            } else {
                wrap(file_sha256(Path::new(&args[2])))
            }
        }
        "copy" => {
            if args.len() < 4 {
                err_str("Usage: copy <src> <dst> [mode_octal]")
            } else {
                let mode = args.get(4).map(|s| s.as_str());
                wrap(do_copy(&args[2], &args[3], mode))
            }
        }
        "atomic-write" => {
            if args.len() < 7 {
                err_str("Usage: atomic-write <src> <dst> <mode_octal> <uid> <gid>")
            } else {
                let uid = args[5].parse::<u32>().unwrap_or(0);
                let gid = args[6].parse::<u32>().unwrap_or(0);
                wrap(do_atomic_write(&args[2], &args[3], &args[4], uid, gid))
            }
        }
        other => err_str(&format!("Unknown subcommand: {other}")),
    };

    println!("{out}");
}

fn run_serve() {
    let stdin = io::stdin();
    let mut stdout = io::stdout();
    for line in stdin.lock().lines() {
        let Ok(line) = line else { break };
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }
        let resp = match serde_json::from_str::<ServeRequest>(trimmed) {
            Ok(ServeRequest::Quit) => {
                let _ = writeln!(stdout, "{}", wrap(Ok("bye")));
                let _ = stdout.flush();
                break;
            }
            Ok(ServeRequest::Ping) => wrap(Ok(do_ping())),
            Ok(ServeRequest::Stat { path, hash }) => wrap(do_stat(&path, hash)),
            Ok(ServeRequest::Ls { dir }) => wrap(do_ls(&dir)),
            Ok(ServeRequest::Sha256 { path }) => wrap(file_sha256(Path::new(&path))),
            Ok(ServeRequest::Copy {
                src,
                dst,
                mode_octal,
            }) => wrap(do_copy(&src, &dst, mode_octal.as_deref())),
            Ok(ServeRequest::AtomicWrite {
                src,
                dst,
                mode_octal,
                uid,
                gid,
            }) => wrap(do_atomic_write(&src, &dst, &mode_octal, uid, gid)),
            Err(e) => err_str(&format!("Invalid request JSON: {e}")),
        };
        if writeln!(stdout, "{resp}").is_err() || stdout.flush().is_err() {
            break;
        }
    }
}

fn wrap<T: Serialize>(res: Result<T, String>) -> String {
    match res {
        Ok(data) => serde_json::to_string(&HelperResponse {
            ok: true,
            data: Some(data),
            error: None,
        })
        .unwrap_or_else(|e| err_str(&e.to_string())),
        Err(error) => err_str(&error),
    }
}

fn err_str(msg: &str) -> String {
    serde_json::to_string(&HelperResponse::<()> {
        ok: false,
        data: None,
        error: Some(msg.to_string()),
    })
    .unwrap_or_else(|_| r#"{"ok":false,"error":"serialize error"}"#.into())
}

fn do_ping() -> PingInfo {
    let (uid, euid) = current_uids();
    PingInfo {
        version: env!("CARGO_PKG_VERSION"),
        arch: env::consts::ARCH,
        uid,
        euid,
    }
}

fn do_stat(path: &str, want_hash: bool) -> Result<StatInfo, String> {
    let p = Path::new(path);
    match fs::metadata(p) {
        Ok(meta) => {
            let (mode_octal, uid, gid) = unix_meta(&meta);
            let sha = if want_hash && meta.is_file() {
                Some(file_sha256(p)?)
            } else {
                None
            };
            Ok(StatInfo {
                path: path.to_string(),
                exists: true,
                is_dir: meta.is_dir(),
                size: meta.len(),
                mode_octal,
                uid,
                gid,
                sha256: sha,
            })
        }
        Err(e) if e.kind() == io::ErrorKind::NotFound => Ok(StatInfo {
            path: path.to_string(),
            exists: false,
            is_dir: false,
            size: 0,
            mode_octal: "0000".into(),
            uid: 0,
            gid: 0,
            sha256: None,
        }),
        Err(e) => Err(format!("stat failed: {e}")),
    }
}

fn do_ls(dir: &str) -> Result<Vec<DirEntryInfo>, String> {
    let mut out = Vec::new();
    let rd = fs::read_dir(dir).map_err(|e| format!("read_dir failed: {e}"))?;
    for entry in rd {
        let entry = entry.map_err(|e| format!("dir entry error: {e}"))?;
        let path = entry.path();
        let meta = fs::metadata(&path).map_err(|e| format!("stat entry failed: {e}"))?;
        let (mode_octal, uid, gid) = unix_meta(&meta);
        out.push(DirEntryInfo {
            name: entry.file_name().to_string_lossy().into_owned(),
            path: path.to_string_lossy().into_owned(),
            is_dir: meta.is_dir(),
            size: meta.len(),
            mode_octal,
            uid,
            gid,
        });
    }
    out.sort_by(|a, b| a.name.cmp(&b.name));
    Ok(out)
}

fn do_copy(src: &str, dst: &str, mode_octal: Option<&str>) -> Result<StatInfo, String> {
    let dst_p = Path::new(dst);
    if let Some(parent) = dst_p.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("mkdir failed: {e}"))?;
    }
    fs::copy(src, dst).map_err(|e| format!("copy failed: {e}"))?;
    if let Some(m) = mode_octal {
        if let Ok(mode) = u32::from_str_radix(m.trim_start_matches('0'), 8) {
            set_permissions_unix(dst_p, mode)?;
        }
    }
    do_stat(dst, true)
}

fn do_atomic_write(
    src: &str,
    dst: &str,
    mode_octal: &str,
    uid: u32,
    gid: u32,
) -> Result<AtomicWriteResult, String> {
    let src_p = Path::new(src);
    let dst_p = Path::new(dst);
    if let Some(parent) = dst_p.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("mkdir failed: {e}"))?;
    }

    let tmp_name = format!(
        "{}.owledit.tmp",
        dst_p
            .file_name()
            .and_then(|s| s.to_str())
            .unwrap_or("file")
    );
    let tmp_p = dst_p.with_file_name(tmp_name);

    // Stream copy + fsync
    let mut reader = File::open(src_p).map_err(|e| format!("open src failed: {e}"))?;
    let mut writer = OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .open(&tmp_p)
        .map_err(|e| format!("create tmp failed: {e}"))?;

    let mut hasher = Sha256::new();
    let mut total = 0u64;
    let mut buf = [0u8; 32768];
    loop {
        let n = reader
            .read(&mut buf)
            .map_err(|e| format!("read src failed: {e}"))?;
        if n == 0 {
            break;
        }
        writer
            .write_all(&buf[..n])
            .map_err(|e| format!("write tmp failed: {e}"))?;
        hasher.update(&buf[..n]);
        total += n as u64;
    }
    writer
        .sync_all()
        .map_err(|e| format!("fsync tmp failed: {e}"))?;
    drop(writer);

    let mode = u32::from_str_radix(mode_octal.trim().trim_start_matches('0'), 8).unwrap_or(0o660);
    set_permissions_unix(&tmp_p, mode)?;
    set_owner_unix(&tmp_p, uid, gid)?;

    // Atomic rename over target
    fs::rename(&tmp_p, dst_p).map_err(|e| {
        let _ = fs::remove_file(&tmp_p);
        format!("atomic rename failed: {e}")
    })?;

    // Read-back verification
    let expected_sha = hex::encode(hasher.finalize());
    let actual_sha = file_sha256(dst_p)?;
    if actual_sha != expected_sha {
        return Err(format!(
            "read-back SHA-256 mismatch: expected {expected_sha}, got {actual_sha}"
        ));
    }

    Ok(AtomicWriteResult {
        dst: dst.to_string(),
        size: total,
        sha256: actual_sha,
        mode_octal: format!("{:04o}", mode),
        uid,
        gid,
    })
}

fn file_sha256(path: &Path) -> Result<String, String> {
    let mut f = File::open(path).map_err(|e| format!("open for sha256 failed: {e}"))?;
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 32768];
    loop {
        let n = f.read(&mut buf).map_err(|e| format!("read failed: {e}"))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

#[cfg(unix)]
fn current_uids() -> (u32, u32) {
    unsafe { (libc::getuid(), libc::geteuid()) }
}

#[cfg(not(unix))]
fn current_uids() -> (u32, u32) {
    (0, 0)
}

#[cfg(unix)]
fn unix_meta(meta: &fs::Metadata) -> (String, u32, u32) {
    use std::os::unix::fs::MetadataExt;
    let mode = meta.mode() & 0o7777;
    (format!("{mode:04o}"), meta.uid(), meta.gid())
}

#[cfg(not(unix))]
fn unix_meta(_meta: &fs::Metadata) -> (String, u32, u32) {
    ("0660".into(), 0, 0)
}

#[cfg(unix)]
fn set_permissions_unix(path: &Path, mode: u32) -> Result<(), String> {
    use std::os::unix::fs::PermissionsExt;
    fs::set_permissions(path, fs::Permissions::from_mode(mode))
        .map_err(|e| format!("chmod failed: {e}"))
}

#[cfg(not(unix))]
fn set_permissions_unix(_path: &Path, _mode: u32) -> Result<(), String> {
    Ok(())
}

#[cfg(unix)]
fn set_owner_unix(path: &Path, uid: u32, gid: u32) -> Result<(), String> {
    use std::ffi::CString;
    use std::os::unix::ffi::OsStrExt;
    let cpath = CString::new(path.as_os_str().as_bytes())
        .map_err(|_| "invalid path for chown".to_string())?;
    let rc = unsafe { libc::chown(cpath.as_ptr(), uid, gid) };
    if rc != 0 {
        return Err(format!(
            "chown({uid},{gid}) failed: {}",
            io::Error::last_os_error()
        ));
    }
    Ok(())
}

#[cfg(not(unix))]
fn set_owner_unix(_path: &Path, _uid: u32, _gid: u32) -> Result<(), String> {
    Ok(())
}
