use aes::cipher::{block_padding::NoPadding, BlockDecryptMut, BlockEncryptMut, KeyIvInit};
use base64::alphabet::STANDARD as B64_ALPHA;
use base64::engine::{GeneralPurpose, GeneralPurposeConfig};
use base64::Engine as _;
use md5::{Digest as _, Md5};
use rand::Rng;
use sha2::Sha256;

/// .NET-compatible Base64 engine (allows non-canonical trailing bits like `6yZyfDifa/PpeJ28qY4gaY==`).
const B64_DOTNET: GeneralPurpose = GeneralPurpose::new(
    &B64_ALPHA,
    GeneralPurposeConfig::new().with_decode_allow_trailing_bits(true),
);

use crate::error::{OwlError, OwlResult};

type Aes256CbcEnc = cbc::Encryptor<aes::Aes256>;
type Aes256CbcDec = cbc::Decryptor<aes::Aes256>;
type Aes128CbcEnc = cbc::Encryptor<aes::Aes128>;
type Aes128CbcDec = cbc::Decryptor<aes::Aes128>;

/// Default Otherworld Legends CryptoPrefs key & IV (extracted from GameLogic.dll CryptoPrefs..cctor).
pub const OWL_DEFAULT_KEY_UTF8: &str = "HTdkQTNmTMD1NTM4DNM0QNIhNTEeNwA3";
pub const OWL_DEFAULT_IV_B64: &str = "6yZyfDifa/PpeJ28qY4gaY==";

#[derive(Debug, Clone)]
pub struct OwlCodec {
    key: Vec<u8>,
    iv: [u8; 16],
}

impl Default for OwlCodec {
    fn default() -> Self {
        Self::new_default()
    }
}

impl OwlCodec {
    pub fn new_default() -> Self {
        let key = OWL_DEFAULT_KEY_UTF8.as_bytes().to_vec();
        let iv_vec = B64_DOTNET
            .decode(OWL_DEFAULT_IV_B64)
            .expect("valid default OWL IV base64");
        let mut iv = [0u8; 16];
        iv.copy_from_slice(&iv_vec);
        Self { key, iv }
    }

    pub fn from_raw(key: Vec<u8>, iv: [u8; 16]) -> OwlResult<Self> {
        if key.len() != 16 && key.len() != 32 {
            return Err(OwlError::Crypto(format!(
                "Unsupported AES key length: {} (expected 16 or 32 bytes)",
                key.len()
            )));
        }
        Ok(Self { key, iv })
    }

    pub fn from_hex(key_hex: &str, iv_hex: &str) -> OwlResult<Self> {
        let key = hex::decode(key_hex.trim())
            .map_err(|e| OwlError::Crypto(format!("Invalid keyHex: {e}")))?;
        let iv_vec = hex::decode(iv_hex.trim())
            .map_err(|e| OwlError::Crypto(format!("Invalid ivHex: {e}")))?;
        if iv_vec.len() != 16 {
            return Err(OwlError::Crypto(format!(
                "IV must be 16 bytes, got {}",
                iv_vec.len()
            )));
        }
        let mut iv = [0u8; 16];
        iv.copy_from_slice(&iv_vec);
        Self::from_raw(key, iv)
    }

    pub fn key_hex(&self) -> String {
        hex::encode(&self.key)
    }

    pub fn iv_hex(&self) -> String {
        hex::encode(self.iv)
    }

    /// Decrypts a raw Base64 (already URL-decoded) CryptoPrefs value.
    /// Matches GameLogic.dll CryptoPrefs.Decrypt (RijndaelManaged CBC + PaddingMode.Zeros + TrimEnd('\0')).
    pub fn decrypt_b64(&self, b64: &str) -> OwlResult<String> {
        let cleaned = b64.trim();
        let mut raw = B64_DOTNET
            .decode(cleaned)
            .map_err(|e| OwlError::Crypto(format!("Base64 decode failed: {e}")))?;
        if raw.is_empty() || raw.len() % 16 != 0 {
            return Err(OwlError::Crypto(format!(
                "Ciphertext length {} is not a positive multiple of 16",
                raw.len()
            )));
        }

        let decrypted = match self.key.len() {
            32 => {
                let mut key_arr = [0u8; 32];
                key_arr.copy_from_slice(&self.key);
                Aes256CbcDec::new(&key_arr.into(), &self.iv.into())
                    .decrypt_padded_mut::<NoPadding>(&mut raw)
                    .map_err(|e| OwlError::Crypto(format!("AES-256-CBC decrypt failed: {e:?}")))?
            }
            16 => {
                let mut key_arr = [0u8; 16];
                key_arr.copy_from_slice(&self.key);
                Aes128CbcDec::new(&key_arr.into(), &self.iv.into())
                    .decrypt_padded_mut::<NoPadding>(&mut raw)
                    .map_err(|e| OwlError::Crypto(format!("AES-128-CBC decrypt failed: {e:?}")))?
            }
            n => {
                return Err(OwlError::Crypto(format!("Unsupported AES key size: {n}")));
            }
        };

        let s = std::str::from_utf8(decrypted)
            .map_err(|e| OwlError::Crypto(format!("Decrypted bytes are not valid UTF-8: {e}")))?;
        Ok(s.trim_end_matches('\0').to_string())
    }

    /// Encrypts a plaintext string into Base64 matching CryptoPrefs.Encrypt (CBC + PaddingMode.Zeros).
    pub fn encrypt_to_b64(&self, plain: &str) -> OwlResult<String> {
        let bytes = plain.as_bytes();
        // .NET PaddingMode.Zeros: pads with 0x00 up to next 16-byte boundary.
        // If empty, produces 16 zero bytes; if already non-empty multiple of 16, stays exact multiple.
        let padded_len = if bytes.is_empty() {
            16
        } else {
            ((bytes.len() + 15) / 16) * 16
        };
        let mut buf = vec![0u8; padded_len];
        buf[..bytes.len()].copy_from_slice(bytes);

        let ct = match self.key.len() {
            32 => {
                let mut key_arr = [0u8; 32];
                key_arr.copy_from_slice(&self.key);
                Aes256CbcEnc::new(&key_arr.into(), &self.iv.into())
                    .encrypt_padded_mut::<NoPadding>(&mut buf, padded_len)
                    .map_err(|e| OwlError::Crypto(format!("AES-256-CBC encrypt failed: {e:?}")))?
            }
            16 => {
                let mut key_arr = [0u8; 16];
                key_arr.copy_from_slice(&self.key);
                Aes128CbcEnc::new(&key_arr.into(), &self.iv.into())
                    .encrypt_padded_mut::<NoPadding>(&mut buf, padded_len)
                    .map_err(|e| OwlError::Crypto(format!("AES-128-CBC encrypt failed: {e:?}")))?
            }
            n => {
                return Err(OwlError::Crypto(format!("Unsupported AES key size: {n}")));
            }
        };

        Ok(B64_DOTNET.encode(ct))
    }

    /// Decrypts a Unity PlayerPrefs XML string value (handles Unity URL-encoding first).
    pub fn decrypt_unity_value(&self, xml_val: &str) -> OwlResult<String> {
        let unescaped = unity_url_decode(xml_val)?;
        self.decrypt_b64(&unescaped)
    }

    /// Encrypts a plaintext string into a Unity PlayerPrefs XML string value (Base64 + Unity URL-encoding).
    pub fn encrypt_unity_value(&self, plain: &str) -> OwlResult<String> {
        let b64 = self.encrypt_to_b64(plain)?;
        Ok(unity_url_encode(&b64))
    }
}

/// Computes lowercase hex MD5 matching CryptoPrefs.GetHash(string).
pub fn md5_key(plain: &str) -> String {
    let mut hasher = Md5::new();
    hasher.update(plain.as_bytes());
    hex::encode(hasher.finalize())
}

/// Returns true if a string looks like a 32-character lowercase hex MD5 key.
pub fn is_md5_hex(s: &str) -> bool {
    s.len() == 32
        && s.bytes()
            .all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'))
}

pub fn sha256_hex(bytes: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(bytes);
    hex::encode(hasher.finalize())
}

/// Decodes an EncryptValue pair (mValue ^ encryptKey).
#[inline]
pub fn decode_encrypt_int(m_value: i32, encrypt_key: i32) -> i32 {
    m_value ^ encrypt_key
}

/// Encodes a real i32 into (mValue, encryptKey) matching GameLogic.dll EncryptValue.SetValue:
/// `encryptKey = Random.Range(200, 500)`, `mValue = realValue ^ encryptKey`.
pub fn encode_encrypt_int(real_value: i32) -> (i32, i32) {
    let mut rng = rand::thread_rng();
    let encrypt_key: i32 = rng.gen_range(200..500);
    (real_value ^ encrypt_key, encrypt_key)
}

/// Deterministic variant useful for unit tests or when keeping an existing encryptKey.
pub fn encode_encrypt_int_with_key(real_value: i32, encrypt_key: i32) -> (i32, i32) {
    (real_value ^ encrypt_key, encrypt_key)
}

/// Decodes Unity Android PlayerPrefs percent-encoding (e.g. `%2B`, `%2F`, `%3D`, `%20`).
pub fn unity_url_decode(input: &str) -> OwlResult<String> {
    let bytes = input.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            let hi = from_hex(bytes[i + 1]);
            let lo = from_hex(bytes[i + 2]);
            if let (Some(h), Some(l)) = (hi, lo) {
                out.push((h << 4) | l);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8(out)
        .map_err(|e| OwlError::Crypto(format!("Invalid UTF-8 after URL decode: {e}")))
}

/// Encodes a string with Unity Android PlayerPrefs v2 percent-encoding rules.
/// Unreserved bytes: A-Z, a-z, 0-9, '-', '_', '.', '~' (matches Unity's `WWW.EscapeURL` / v2 prefs).
pub fn unity_url_encode(input: &str) -> String {
    const HEX: &[u8; 16] = b"0123456789ABCDEF";
    let mut out = String::with_capacity(input.len() + input.len() / 4);
    for &b in input.as_bytes() {
        if b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.' | b'~') {
            out.push(b as char);
        } else {
            out.push('%');
            out.push(HEX[(b >> 4) as usize] as char);
            out.push(HEX[(b & 0x0F) as usize] as char);
        }
    }
    out
}

#[inline]
fn from_hex(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

/// Generic codec helpers for external profiles (parity with npdmVNEditor).
pub fn xor_repeating(data: &[u8], key: &[u8]) -> OwlResult<Vec<u8>> {
    if key.is_empty() {
        return Err(OwlError::Crypto("XOR key cannot be empty".into()));
    }
    Ok(data
        .iter()
        .enumerate()
        .map(|(i, b)| b ^ key[i % key.len()])
        .collect())
}

pub fn gzip_decompress(data: &[u8]) -> OwlResult<Vec<u8>> {
    use flate2::read::GzDecoder;
    use std::io::Read;
    let mut dec = GzDecoder::new(data);
    let mut out = Vec::new();
    dec.read_to_end(&mut out)
        .map_err(|e| OwlError::Crypto(format!("GZip decompress failed: {e}")))?;
    Ok(out)
}

pub fn gzip_compress(data: &[u8]) -> OwlResult<Vec<u8>> {
    use flate2::{write::GzEncoder, Compression};
    use std::io::Write;
    let mut enc = GzEncoder::new(Vec::new(), Compression::default());
    enc.write_all(data)
        .map_err(|e| OwlError::Crypto(format!("GZip compress failed: {e}")))?;
    enc.finish()
        .map_err(|e| OwlError::Crypto(format!("GZip finish failed: {e}")))
}
