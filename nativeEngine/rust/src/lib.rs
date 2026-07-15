pub mod midi;
pub mod echo;

pub use midi::types::*;
pub use midi::error::*;
pub use midi::event::*;
pub use midi::access::MidiAccess;
pub use midi::connection::MidiConnection;
pub use echo::{EchoAudioBuffer, EchoDecodeResult, EchoDeviceInfo, EchoEngine};

#[derive(uniffi::Record)]
pub struct NativeEngineInfo {
    pub name: String,
    pub version: String,
}

#[uniffi::export]
pub fn native_engine_info() -> NativeEngineInfo {
    NativeEngineInfo {
        name: "Amethyst Native Engine".to_string(),
        version: env!("CARGO_PKG_VERSION").to_string(),
    }
}

#[derive(uniffi::Object)]
pub struct NativeEngine;

#[uniffi::export]
impl NativeEngine {
    #[uniffi::constructor]
    pub fn new() -> Self {
        Self
    }

    pub fn ping(&self) -> String {
        "native-engine-ready".to_string()
    }
}

uniffi::setup_scaffolding!();
