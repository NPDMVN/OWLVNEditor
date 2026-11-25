pub mod backup;
pub mod crypto;
pub mod dictionary;
pub mod error;
pub mod nrbf;
pub mod sanitizer;
pub mod session;
pub mod ssaid;
pub mod unlockables;
pub mod xml_prefs;

pub fn core_banner() -> String {
    format!(
        "owledit-core v{} (Rust native • AES-256-CBC/Zero + MS-NRBF + ABX SSAID)",
        env!("CARGO_PKG_VERSION")
    )
}
