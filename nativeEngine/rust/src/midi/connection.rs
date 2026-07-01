use crate::midi::types::*;
use crate::midi::error::MidiError;
use crate::midi::backend::BackendPortHandle;
use crate::midi::event::{MidiEvent, midi_event_to_bytes};
use std::sync::{Arc, Mutex};
use std::sync::mpsc::Receiver;
use std::time::Duration;

pub(crate) struct MidiConnectionInner {
    port_id: String,
    direction: MidiPortDirection,
    handle: Box<dyn BackendPortHandle>,
    receiver: Option<Mutex<Receiver<MidiMessage>>>,
    is_open: Mutex<bool>,
}

#[derive(uniffi::Object)]
pub struct MidiConnection {
    inner: Arc<MidiConnectionInner>,
}

impl MidiConnection {
    pub(crate) fn new_input(
        port_id: String,
        handle: Box<dyn BackendPortHandle>,
        receiver: Receiver<MidiMessage>,
    ) -> Self {
        Self {
            inner: Arc::new(MidiConnectionInner {
                port_id,
                direction: MidiPortDirection::Input,
                handle,
                receiver: Some(Mutex::new(receiver)),
                is_open: Mutex::new(true),
            }),
        }
    }

    pub(crate) fn new_output(
        port_id: String,
        handle: Box<dyn BackendPortHandle>,
    ) -> Self {
        Self {
            inner: Arc::new(MidiConnectionInner {
                port_id,
                direction: MidiPortDirection::Output,
                handle,
                receiver: None,
                is_open: Mutex::new(true),
            }),
        }
    }
}

#[uniffi::export]
impl MidiConnection {
    pub fn send(&self, data: Vec<u8>) -> Result<(), MidiError> {
        if !self.is_open() {
            return Err(MidiError::PortNotOpen { port_id: self.port_id() });
        }
        if self.direction() != MidiPortDirection::Output {
            return Err(MidiError::SendFailed {
                reason: "Cannot send to an input port".into(),
            });
        }
        self.inner.handle.send(&data)
    }

    pub fn send_event(&self, event: MidiEvent) -> Result<(), MidiError> {
        let bytes = midi_event_to_bytes(event);
        self.send(bytes)
    }

    pub fn receive(&self) -> Option<MidiMessage> {
        let rx_lock = self.inner.receiver.as_ref()?;
        let rx = rx_lock.lock().unwrap();
        rx.recv().ok()
    }

    pub fn receive_timeout(&self, timeout_ms: u64) -> Option<MidiMessage> {
        let rx_lock = self.inner.receiver.as_ref()?;
        let rx = rx_lock.lock().unwrap();
        rx.recv_timeout(Duration::from_millis(timeout_ms)).ok()
    }

    pub fn try_receive(&self) -> Option<MidiMessage> {
        let rx_lock = self.inner.receiver.as_ref()?;
        let rx = rx_lock.lock().unwrap();
        rx.try_recv().ok()
    }

    pub fn port_id(&self) -> String {
        self.inner.port_id.clone()
    }

    pub fn direction(&self) -> MidiPortDirection {
        self.inner.direction
    }

    pub fn is_open(&self) -> bool {
        *self.inner.is_open.lock().unwrap()
    }

    pub fn disconnect(&self) -> Result<(), MidiError> {
        let mut open = self.inner.is_open.lock().unwrap();
        if *open {
            *open = false;
            self.inner.handle.close()?;
        }
        Ok(())
    }
}

impl Drop for MidiConnectionInner {
    fn drop(&mut self) {
        let mut open = self.is_open.lock().unwrap();
        if *open {
            *open = false;
            let _ = self.handle.close();
        }
    }
}
