use thiserror::Error;

#[derive(Debug, Error)]
pub enum OwlError {
    #[error("Crypto error: {0}")]
    Crypto(String),

    #[error("XML PlayerPrefs error: {0}")]
    Xml(String),

    #[error("NRBF BinaryFormatter error: {0}")]
    Nrbf(String),

    #[error("JSON error: {0}")]
    Json(String),

    #[error("Backup archive error: {0}")]
    Backup(String),

    #[error("SSAID error: {0}")]
    Ssaid(String),

    #[error("Session error: {0}")]
    Session(String),

    #[error("Validation error: {0}")]
    Validation(String),

    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
}

pub type OwlResult<T> = Result<T, OwlError>;
