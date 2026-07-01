use crate::midi::types::*;
use crate::midi::error::MidiError;
use crate::midi::backend::{MidiBackend, BackendPortHandle};
use std::sync::mpsc;

pub struct AlsaBackend;

impl AlsaBackend {
    pub fn new() -> Result<Self, MidiError> {
        Ok(Self)
    }
}

impl MidiBackend for AlsaBackend {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        // Implement full ALSA discovery in the future.
        Ok(vec![])
    }

    fn open_input(
        &self,
        _port_id: &str,
        _sender: mpsc::Sender<MidiMessage>,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        Err(MidiError::BackendError { reason: "Not implemented yet on Linux".into() })
    }

    fn open_output(
        &self,
        _port_id: &str,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        Err(MidiError::BackendError { reason: "Not implemented yet on Linux".into() })
    }

    fn name(&self) -> &str {
        "ALSA (Linux)"
    }
}
