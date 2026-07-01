use crate::midi::types::*;
use crate::midi::error::MidiError;
use std::sync::mpsc;

pub trait BackendPortHandle: Send + Sync {
    fn send(&self, data: &[u8]) -> Result<(), MidiError>;
    fn close(&self) -> Result<(), MidiError>;
    fn port_id(&self) -> &str;
}

pub trait MidiBackend: Send + Sync {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError>;
    
    fn open_input(
        &self,
        port_id: &str,
        sender: mpsc::Sender<MidiMessage>,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError>;
    
    fn open_output(
        &self,
        port_id: &str,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError>;
    
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
