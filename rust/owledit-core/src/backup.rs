use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Component, Path, PathBuf};

use serde::{Deserialize, Serialize};
use zip::write::SimpleFileOptions;
use zip::{CompressionMethod, ZipArchive, ZipWriter};

use crate::crypto::sha256_hex;
use crate::error::{OwlError, OwlResult};

pub const MANIFEST_FILENAME: &str = "manifest.json";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackupEntry {
    pub relative_path: String,
    pub size: u64,
    pub sha256: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackupManifest {
    pub schema_version: u32,
    pub app_name: String,
    pub game_profile_id: String,
    pub package_name: String,
    pub uid: Option<String>,
    pub note: String,
    pub created_at_epoch_ms: u64,
    pub device_info: String,
    pub entries: Vec<BackupEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BackupInspectResult {
    pub archive_path: String,
    pub archive_size: u64,
    pub manifest: BackupManifest,
    pub integrity_ok: bool,
    pub integrity_errors: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ExtractedBackupFile {
    pub relative_path: String,
    pub staged_path: String,
    pub size: u64,
    pub sha256: String,
}

/// Creates a verified `.owldb` ZIP archive from a staging directory.
pub fn create_backup_from_staging(
    staging_dir: &Path,
    output_archive: &Path,
    package_name: &str,
    game_profile_id: &str,
    uid: Option<String>,
    note: &str,
    device_info: &str,
    created_at_epoch_ms: u64,
) -> OwlResult<BackupManifest> {
    if !staging_dir.exists() {
        return Err(OwlError::Backup(format!(
            "Staging directory does not exist: {}",
            staging_dir.display()
        )));
    }
    if let Some(parent) = output_archive.parent() {
        fs::create_dir_all(parent)?;
    }

    let mut files = Vec::new();
    collect_files_recursive(staging_dir, staging_dir, &mut files)?;
    files.sort_by(|a, b| a.0.cmp(&b.0));

    let tmp_path = output_archive.with_extension("owldb.tmp");
    let file = File::create(&tmp_path)?;
    let mut zip = ZipWriter::new(file);
    let opts = SimpleFileOptions::default().compression_method(CompressionMethod::Deflated);

    let mut manifest_entries = Vec::with_capacity(files.len());
    for (rel, abs) in files {
        let bytes = fs::read(&abs)?;
        let hash = sha256_hex(&bytes);
        let size = bytes.len() as u64;
        zip.start_file(&rel, opts)
            .map_err(|e| OwlError::Backup(format!("Zip start_file '{rel}' failed: {e}")))?;
        zip.write_all(&bytes)?;
        manifest_entries.push(BackupEntry {
            relative_path: rel,
            size,
            sha256: hash,
        });
    }

    let manifest = BackupManifest {
        schema_version: 1,
        app_name: "OWLEditor".to_string(),
        game_profile_id: game_profile_id.to_string(),
        package_name: package_name.to_string(),
        uid,
        note: note.to_string(),
        created_at_epoch_ms,
        device_info: device_info.to_string(),
        entries: manifest_entries,
    };

    let manifest_bytes = serde_json::to_vec_pretty(&manifest)
        .map_err(|e| OwlError::Json(format!("Manifest serialize error: {e}")))?;
    zip.start_file(MANIFEST_FILENAME, opts)
        .map_err(|e| OwlError::Backup(format!("Zip manifest write failed: {e}")))?;
    zip.write_all(&manifest_bytes)?;
    zip.finish()
        .map_err(|e| OwlError::Backup(format!("Zip finish failed: {e}")))?;

    if output_archive.exists() {
        fs::remove_file(output_archive)?;
    }
    fs::rename(&tmp_path, output_archive)?;

    Ok(manifest)
}

/// Inspects a `.owldb` archive and verifies every entry's SHA-256 against `manifest.json`.
pub fn inspect_backup(archive_path: &Path) -> OwlResult<BackupInspectResult> {
    let meta = fs::metadata(archive_path)?;
    let file = File::open(archive_path)?;
    let mut zip = ZipArchive::new(file)
        .map_err(|e| OwlError::Backup(format!("Invalid .owldb archive: {e}")))?;

    let manifest: BackupManifest = {
        let mut mf = zip
            .by_name(MANIFEST_FILENAME)
            .map_err(|e| OwlError::Backup(format!("Missing manifest.json: {e}")))?;
        let mut buf = Vec::new();
        mf.read_to_end(&mut buf)?;
        serde_json::from_slice(&buf)
            .map_err(|e| OwlError::Json(format!("Invalid manifest.json: {e}")))?
    };

    let mut errors = Vec::new();
    for entry in &manifest.entries {
        if !is_safe_rel_path(&entry.relative_path) {
            errors.push(format!("Unsafe path in manifest: {}", entry.relative_path));
            continue;
        }
        match zip.by_name(&entry.relative_path) {
            Ok(mut zf) => {
                let mut data = Vec::new();
                zf.read_to_end(&mut data)?;
                if data.len() as u64 != entry.size {
                    errors.push(format!(
                        "Size mismatch for {}: expected {}, got {}",
                        entry.relative_path,
                        entry.size,
                        data.len()
                    ));
                }
                let actual = sha256_hex(&data);
                if actual != entry.sha256 {
                    errors.push(format!("SHA-256 mismatch for {}", entry.relative_path));
                }
            }
            Err(_) => {
                errors.push(format!("Missing entry in archive: {}", entry.relative_path));
            }
        }
    }

    Ok(BackupInspectResult {
        archive_path: archive_path.display().to_string(),
        archive_size: meta.len(),
        manifest,
        integrity_ok: errors.is_empty(),
        integrity_errors: errors,
    })
}

/// Verifies SHA-256 integrity and extracts selected (or all) files to `staging_dir`.
pub fn verify_and_extract_to_staging(
    archive_path: &Path,
    staging_dir: &Path,
    selected_paths: Option<&[String]>,
) -> OwlResult<Vec<ExtractedBackupFile>> {
    let inspection = inspect_backup(archive_path)?;
    if !inspection.integrity_ok {
        return Err(OwlError::Backup(format!(
            "Backup integrity check failed: {}",
            inspection.integrity_errors.join("; ")
        )));
    }

    fs::create_dir_all(staging_dir)?;
    let file = File::open(archive_path)?;
    let mut zip = ZipArchive::new(file)
        .map_err(|e| OwlError::Backup(format!("Open archive failed: {e}")))?;

    let mut extracted = Vec::new();
    for entry in &inspection.manifest.entries {
        if let Some(filter) = selected_paths {
            if !filter.is_empty() && !filter.iter().any(|p| p == &entry.relative_path) {
                continue;
            }
        }
        if !is_safe_rel_path(&entry.relative_path) {
            return Err(OwlError::Backup(format!(
                "Refusing unsafe path traversal: {}",
                entry.relative_path
            )));
        }
        let mut zf = zip
            .by_name(&entry.relative_path)
            .map_err(|e| OwlError::Backup(format!("Read entry '{}' failed: {e}", entry.relative_path)))?;
        let mut data = Vec::new();
        zf.read_to_end(&mut data)?;

        let dest = staging_dir.join(&entry.relative_path);
        if let Some(parent) = dest.parent() {
            fs::create_dir_all(parent)?;
        }
        fs::write(&dest, &data)?;

        extracted.push(ExtractedBackupFile {
            relative_path: entry.relative_path.clone(),
            staged_path: dest.display().to_string(),
            size: entry.size,
            sha256: entry.sha256.clone(),
        });
    }

    Ok(extracted)
}

/// Reads and verifies a single entry from a `.owldb` backup directly into memory.
pub fn verify_and_read_entry(archive_path: &Path, relative_path: &str) -> OwlResult<Vec<u8>> {
    let inspection = inspect_backup(archive_path)?;
    let entry = inspection
        .manifest
        .entries
        .iter()
        .find(|e| e.relative_path == relative_path)
        .ok_or_else(|| OwlError::Backup(format!("Entry '{relative_path}' not found in manifest")))?;

    let file = File::open(archive_path)?;
    let mut zip = ZipArchive::new(file)
        .map_err(|e| OwlError::Backup(format!("Open archive failed: {e}")))?;
    let mut zf = zip
        .by_name(relative_path)
        .map_err(|e| OwlError::Backup(format!("Read entry '{relative_path}' failed: {e}")))?;
    let mut data = Vec::new();
    zf.read_to_end(&mut data)?;
    if sha256_hex(&data) != entry.sha256 {
        return Err(OwlError::Backup(format!(
            "SHA-256 verification failed for '{relative_path}'"
        )));
    }
    Ok(data)
}

fn collect_files_recursive(
    root: &Path,
    dir: &Path,
    out: &mut Vec<(String, PathBuf)>,
) -> OwlResult<()> {
    for entry in fs::read_dir(dir)? {
        let entry = entry?;
        let path = entry.path();
        if path.is_dir() {
            collect_files_recursive(root, &path, out)?;
        } else if path.is_file() {
            let rel = path
                .strip_prefix(root)
                .map_err(|e| OwlError::Backup(e.to_string()))?;
            let rel_str = rel
                .components()
                .map(|c| c.as_os_str().to_string_lossy().into_owned())
                .collect::<Vec<_>>()
                .join("/");
            out.push((rel_str, path));
        }
    }
    Ok(())
}

fn is_safe_rel_path(p: &str) -> bool {
    if p.is_empty() || p.starts_with('/') || p.starts_with('\\') {
        return false;
    }
    for comp in Path::new(p).components() {
        if matches!(comp, Component::ParentDir | Component::RootDir | Component::Prefix(_)) {
            return false;
        }
    }
    true
}
