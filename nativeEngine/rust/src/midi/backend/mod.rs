use crate::midi::error::MidiError;
use crate::midi::types::*;
use std::sync::OnceLock;
use std::sync::mpsc;
use std::time::{Duration, Instant};

pub(super) fn monotonic_micros() -> u64 {
    static ORIGIN: OnceLock<Instant> = OnceLock::new();
    ORIGIN
        .get_or_init(Instant::now)
        .elapsed()
        .as_micros()
        .min(u64::MAX as u128) as u64
}

pub trait BackendPortHandle: Send + Sync {
    fn send(&self, data: &[u8]) -> Result<(), MidiError>;
    fn close(&self) -> Result<(), MidiError>;
    fn port_id(&self) -> &str;
    fn is_open(&self) -> bool {
        true
    }
}

pub trait MidiBackend: Send + Sync {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError>;

    fn wait_for_device_change(&self, timeout_ms: u64) -> bool {
        std::thread::sleep(Duration::from_millis(timeout_ms));
        false
    }

    fn open_input(
        &self,
        port_id: &str,
        sender: mpsc::SyncSender<MidiMessage>,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError>;

    fn open_output(&self, port_id: &str) -> Result<Box<dyn BackendPortHandle>, MidiError>;

    fn name(&self) -> &str;
}

#[cfg(target_os = "macos")]
pub mod coremidi_backend;

#[cfg(target_os = "linux")]
pub mod alsa_backend;

#[cfg(target_os = "windows")]
pub mod winrt_backend;

pub fn create_platform_backend() -> Result<Box<dyn MidiBackend>, MidiError> {
    #[cfg(target_os = "macos")]
    {
        Ok(Box::new(coremidi_backend::CoreMidiBackend::new()?))
    }

    #[cfg(target_os = "linux")]
    {
        Ok(Box::new(alsa_backend::AlsaBackend::new()?))
    }

    #[cfg(target_os = "windows")]
    {
        Ok(Box::new(winrt_backend::WinRtBackend::new()?))
    }

    #[cfg(not(any(target_os = "macos", target_os = "linux", target_os = "windows")))]
    {
        Err(MidiError::BackendError {
            reason: "No MIDI backend available for this platform".into(),
        })
    }
}
