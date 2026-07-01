use crate::midi::types::*;
use crate::midi::error::MidiError;
use crate::midi::backend::{MidiBackend, BackendPortHandle};
use std::sync::mpsc;

pub struct WinRtBackend;

impl WinRtBackend {
    pub fn new() -> Result<Self, MidiError> {
        Ok(Self)
    }
}

impl MidiBackend for WinRtBackend {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        // Implement full WinRT discovery in the future.
        Ok(vec![])
    }

    fn open_input(
        &self,
        _port_id: &str,
        _sender: mpsc::Sender<MidiMessage>,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        Err(MidiError::BackendError { reason: "Not implemented yet on Windows".into() })
    }

    fn open_output(
        &self,
        _port_id: &str,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        Err(MidiError::BackendError { reason: "Not implemented yet on Windows".into() })
    }

    fn name(&self) -> &str {
        "WinRT (Windows)"
    }
}
