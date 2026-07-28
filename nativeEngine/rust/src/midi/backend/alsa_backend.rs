use crate::midi::backend::{BackendPortHandle, MidiBackend};
use crate::midi::error::MidiError;
use crate::midi::types::{
    MidiDeviceInfo, MidiMessage, MidiPortDirection, MidiPortInfo, MidiTransportType,
};
use alsa::Direction;
use alsa::poll;
use alsa::seq::{
    Addr, ClientIter, EventType, MidiEvent, PortCap, PortIter, PortSubscribe, PortType, Seq,
};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, mpsc};
use std::thread::JoinHandle;
use std::time::{SystemTime, UNIX_EPOCH};

const BACKEND_PREFIX: &str = "alsa-seq1";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum PortDirection {
    Input,
    Output,
}

impl PortDirection {
    fn label(self) -> &'static str {
        match self {
            Self::Input => "in",
            Self::Output => "out",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct PortAddress {
    client: i32,
    port: i32,
    direction: PortDirection,
}

impl PortAddress {
    fn encode(self) -> String {
        format!(
            "{}:{}:{}:{}",
            BACKEND_PREFIX,
            self.client,
            self.port,
            self.direction.label()
        )
    }

    fn decode(value: &str) -> Result<Self, MidiError> {
        let parts = value.split(':').collect::<Vec<_>>();
        if parts.len() != 4 || parts[0] != BACKEND_PREFIX {
            return Err(port_not_found(value));
        }
        let direction = match parts[3] {
            "in" => PortDirection::Input,
            "out" => PortDirection::Output,
            _ => return Err(port_not_found(value)),
        };
        Ok(Self {
            client: parts[1].parse().map_err(|_| port_not_found(value))?,
            port: parts[2].parse().map_err(|_| port_not_found(value))?,
            direction,
        })
    }

    fn alsa(self) -> Addr {
        Addr {
            client: self.client,
            port: self.port,
        }
    }
}

struct HotplugMonitor {
    seq: Seq,
    _port: i32,
}

impl HotplugMonitor {
    fn new() -> Result<Self, MidiError> {
        let seq = open_seq(None, true)?;
        seq.set_client_name(c"Amethyst MIDI Monitor")
            .map_err(alsa_error)?;
        let port = seq
            .create_simple_port(
                c"Hotplug",
                PortCap::WRITE | PortCap::SUBS_WRITE,
                PortType::APPLICATION,
            )
            .map_err(alsa_error)?;
        subscribe(
            &seq,
            Addr::system_announce(),
            Addr {
                client: seq.client_id().map_err(alsa_error)?,
                port,
            },
        )?;
        Ok(Self { seq, _port: port })
    }

    fn wait(&mut self, timeout_ms: u64) -> bool {
        let descriptor = (&self.seq, Some(Direction::Capture));
        let descriptors: [&dyn poll::Descriptors; 1] = [&descriptor];
        match poll::poll_all(&descriptors, timeout_ms.min(i32::MAX as u64) as i32) {
            Ok(events) if !events.is_empty() => {
                let mut input = self.seq.input();
                let mut changed = false;
                while input.event_input_pending(true).unwrap_or(0) > 0 {
                    let Ok(event) = input.event_input() else {
                        break;
                    };
                    changed |= matches!(
                        event.get_type(),
                        EventType::ClientStart
                            | EventType::ClientExit
                            | EventType::PortStart
                            | EventType::PortExit
                            | EventType::PortChange
                    );
                }
                changed
            }
            _ => false,
        }
    }
}

pub struct AlsaBackend {
    monitor: Mutex<HotplugMonitor>,
}

impl AlsaBackend {
    pub fn new() -> Result<Self, MidiError> {
        Ok(Self {
            monitor: Mutex::new(HotplugMonitor::new()?),
        })
    }
}

impl MidiBackend for AlsaBackend {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        let seq = open_seq(None, false)?;
        let own_client = seq.client_id().map_err(alsa_error)?;
        let mut devices = Vec::new();

        for client in ClientIter::new(&seq) {
            let client_id = client.get_client();
            if client_id == own_client || client_id == Addr::system_announce().client {
                continue;
            }
            let name = client
                .get_name()
                .unwrap_or("Unknown ALSA MIDI Device")
                .to_string();
            if name.starts_with("Amethyst MIDI ") {
                continue;
            }

            let mut ports = Vec::new();
            for port in PortIter::new(&seq, client_id) {
                let caps = port.get_capability();
                let name = port
                    .get_name()
                    .unwrap_or("Unnamed ALSA MIDI Port")
                    .to_string();
                let port_number = port.get_port().max(0) as u32;

                if caps.contains(PortCap::READ | PortCap::SUBS_READ) {
                    ports.push(MidiPortInfo {
                        id: PortAddress {
                            client: client_id,
                            port: port.get_port(),
                            direction: PortDirection::Input,
                        }
                        .encode(),
                        name: name.clone(),
                        direction: MidiPortDirection::Input,
                        port_number,
                        is_available: true,
                    });
                }
                if caps.contains(PortCap::WRITE | PortCap::SUBS_WRITE) {
                    ports.push(MidiPortInfo {
                        id: PortAddress {
                            client: client_id,
                            port: port.get_port(),
                            direction: PortDirection::Output,
                        }
                        .encode(),
                        name,
                        direction: MidiPortDirection::Output,
                        port_number,
                        is_available: true,
                    });
                }
            }

            if ports.is_empty() {
                continue;
            }
            ports.sort_by_key(|port| (port.direction as u8, port.port_number));

            let card = client.get_card().unwrap_or(-1);
            let stable_parent = if card >= 0 {
                format!("card:{card}:{}", encode_component(&name))
            } else {
                format!("client-name:{}", encode_component(&name))
            };
            let hardware = ports.iter().any(|port| {
                seq.get_any_port_info(
                    PortAddress::decode(&port.id)
                        .expect("internally generated ALSA port id")
                        .alsa(),
                )
                .map(|info| info.get_type().contains(PortType::HARDWARE))
                .unwrap_or(false)
            });

            devices.push(MidiDeviceInfo {
                id: format!("{BACKEND_PREFIX}:device:{stable_parent}"),
                name: name.clone(),
                manufacturer: None,
                model: Some(name),
                serial_number: None,
                usb_vendor_id: None,
                usb_product_id: None,
                transport: if hardware {
                    MidiTransportType::Usb
                } else {
                    MidiTransportType::Virtual
                },
                ports,
            });
        }

        Ok(devices)
    }

    fn wait_for_device_change(&self, timeout_ms: u64) -> bool {
        self.monitor
            .lock()
            .map(|mut monitor| monitor.wait(timeout_ms))
            .unwrap_or(true)
    }

    fn open_input(
        &self,
        port_id: &str,
        sender: mpsc::Sender<MidiMessage>,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let address = PortAddress::decode(port_id)?;
        if address.direction != PortDirection::Input {
            return Err(port_not_found(port_id));
        }

        let open = Arc::new(AtomicBool::new(true));
        let thread_open = Arc::clone(&open);
        let thread_port_id = port_id.to_string();
        let (ready_tx, ready_rx) = mpsc::sync_channel::<Result<(), String>>(1);
        let failure_tx = ready_tx.clone();
        let thread = std::thread::Builder::new()
            .name("amethyst-alsa-midi-in".into())
            .spawn(move || {
                let result =
                    run_input_connection(address, thread_port_id, sender, &thread_open, ready_tx);
                if let Err(error) = result {
                    let _ = failure_tx.send(Err(error.to_string()));
                    eprintln!("ALSA MIDI input stopped: {error}");
                }
                thread_open.store(false, Ordering::Release);
            })
            .map_err(|error| backend_error(error.to_string()))?;
        match ready_rx.recv() {
            Ok(Ok(())) => {}
            Ok(Err(reason)) => {
                open.store(false, Ordering::Release);
                let _ = thread.join();
                return Err(backend_error(reason));
            }
            Err(error) => {
                open.store(false, Ordering::Release);
                let _ = thread.join();
                return Err(backend_error(error.to_string()));
            }
        }

        Ok(Box::new(AlsaInputHandle {
            port_id: port_id.to_string(),
            open,
            thread: Mutex::new(Some(thread)),
        }))
    }

    fn open_output(&self, port_id: &str) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let address = PortAddress::decode(port_id)?;
        if address.direction != PortDirection::Output {
            return Err(port_not_found(port_id));
        }

        let seq = open_seq(Some(Direction::Playback), false)?;
        seq.set_client_name(c"Amethyst MIDI Output")
            .map_err(alsa_error)?;
        let source_port = seq
            .create_simple_port(
                c"Output",
                PortCap::READ | PortCap::SUBS_READ,
                PortType::MIDI_GENERIC | PortType::APPLICATION,
            )
            .map_err(alsa_error)?;
        subscribe(
            &seq,
            Addr {
                client: seq.client_id().map_err(alsa_error)?,
                port: source_port,
            },
            address.alsa(),
        )?;

        Ok(Box::new(AlsaOutputHandle {
            port_id: port_id.to_string(),
            seq: Mutex::new(Some(seq)),
            source_port,
            encoder: Mutex::new(SendMidiEncoder(MidiEvent::new(65_536).map_err(alsa_error)?)),
            open: AtomicBool::new(true),
        }))
    }

    fn name(&self) -> &str {
        "ALSA Sequencer MIDI"
    }
}

struct AlsaInputHandle {
    port_id: String,
    open: Arc<AtomicBool>,
    thread: Mutex<Option<JoinHandle<()>>>,
}

impl BackendPortHandle for AlsaInputHandle {
    fn send(&self, _data: &[u8]) -> Result<(), MidiError> {
        Err(MidiError::SendFailed {
            reason: "Port is not opened for output".into(),
        })
    }

    fn close(&self) -> Result<(), MidiError> {
        self.open.store(false, Ordering::Release);
        if let Some(thread) = self.thread.lock().unwrap().take() {
            let _ = thread.join();
        }
        Ok(())
    }

    fn port_id(&self) -> &str {
        &self.port_id
    }

    fn is_open(&self) -> bool {
        self.open.load(Ordering::Acquire)
    }
}

struct AlsaOutputHandle {
    port_id: String,
    seq: Mutex<Option<Seq>>,
    source_port: i32,
    encoder: Mutex<SendMidiEncoder>,
    open: AtomicBool,
}

struct SendMidiEncoder(MidiEvent);

// SAFETY: snd_midi_event_t has no thread affinity. Access is serialized by the
// containing Mutex, so the encoder can never be used concurrently.
unsafe impl Send for SendMidiEncoder {}

impl BackendPortHandle for AlsaOutputHandle {
    fn send(&self, data: &[u8]) -> Result<(), MidiError> {
        if !self.open.load(Ordering::Acquire) {
            return Err(MidiError::PortNotOpen {
                port_id: self.port_id.clone(),
            });
        }
        let mut seq_guard = self.seq.lock().unwrap();
        let seq = seq_guard.as_mut().ok_or_else(|| MidiError::PortNotOpen {
            port_id: self.port_id.clone(),
        })?;
        let mut encoder = self.encoder.lock().unwrap();
        encoder.0.reset_encode();
        let mut remaining = data;

        while !remaining.is_empty() {
            let (consumed, event) = encoder.0.encode(remaining).map_err(alsa_error)?;
            if consumed == 0 {
                return Err(MidiError::SendFailed {
                    reason: "ALSA MIDI encoder made no progress".into(),
                });
            }
            remaining = &remaining[consumed..];
            if let Some(mut event) = event {
                event.set_source(self.source_port);
                event.set_subs();
                event.set_direct();
                if let Err(error) = seq.event_output_direct(&mut event) {
                    self.open.store(false, Ordering::Release);
                    return Err(alsa_error(error));
                }
            }
        }
        Ok(())
    }

    fn close(&self) -> Result<(), MidiError> {
        self.open.store(false, Ordering::Release);
        self.seq.lock().unwrap().take();
        Ok(())
    }

    fn port_id(&self) -> &str {
        &self.port_id
    }

    fn is_open(&self) -> bool {
        self.open.load(Ordering::Acquire)
    }
}

fn run_input_connection(
    address: PortAddress,
    port_id: String,
    sender: mpsc::Sender<MidiMessage>,
    open: &AtomicBool,
    ready: mpsc::SyncSender<Result<(), String>>,
) -> Result<(), MidiError> {
    let seq = open_seq(Some(Direction::Capture), true)?;
    seq.set_client_name(c"Amethyst MIDI Input")
        .map_err(alsa_error)?;
    let destination_port = seq
        .create_simple_port(
            c"Input",
            PortCap::WRITE | PortCap::SUBS_WRITE,
            PortType::MIDI_GENERIC | PortType::APPLICATION,
        )
        .map_err(alsa_error)?;
    subscribe(
        &seq,
        address.alsa(),
        Addr {
            client: seq.client_id().map_err(alsa_error)?,
            port: destination_port,
        },
    )?;
    let _ = ready.send(Ok(()));

    let descriptor = (&seq, Some(Direction::Capture));
    let descriptors: [&dyn poll::Descriptors; 1] = [&descriptor];
    let decoder = MidiEvent::new(65_536).map_err(alsa_error)?;
    while open.load(Ordering::Acquire) {
        if poll::poll_all(&descriptors, 50)
            .map_err(alsa_error)?
            .is_empty()
        {
            continue;
        }
        let mut input = seq.input();
        while input.event_input_pending(true).unwrap_or(0) > 0 {
            let mut event = input.event_input().map_err(alsa_error)?;
            let mut data = vec![0u8; 65_536];
            let size = decoder.decode(&mut data, &mut event).map_err(alsa_error)?;
            if size == 0 {
                continue;
            }
            data.truncate(size);
            if sender
                .send(MidiMessage {
                    data,
                    timestamp_us: now_micros(),
                    port_id: port_id.clone(),
                })
                .is_err()
            {
                return Ok(());
            }
        }
    }
    Ok(())
}

fn subscribe(seq: &Seq, sender: Addr, destination: Addr) -> Result<(), MidiError> {
    let subscription = PortSubscribe::empty().map_err(alsa_error)?;
    subscription.set_sender(sender);
    subscription.set_dest(destination);
    seq.subscribe_port(&subscription).map_err(alsa_error)
}

fn open_seq(direction: Option<Direction>, nonblock: bool) -> Result<Seq, MidiError> {
    Seq::open(None, direction, nonblock).map_err(alsa_error)
}

fn now_micros() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_micros()
        .min(u64::MAX as u128) as u64
}

fn encode_component(value: &str) -> String {
    value
        .as_bytes()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect()
}

fn alsa_error(error: alsa::Error) -> MidiError {
    backend_error(error.to_string())
}

fn backend_error(reason: String) -> MidiError {
    MidiError::BackendError { reason }
}

fn port_not_found(port_id: &str) -> MidiError {
    MidiError::PortNotFound {
        port_id: port_id.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn port_address_round_trips_with_parent_client() {
        let address = PortAddress {
            client: 24,
            port: 3,
            direction: PortDirection::Output,
        };
        assert_eq!(PortAddress::decode(&address.encode()).unwrap(), address);
    }

    #[test]
    fn malformed_port_address_is_rejected() {
        assert!(PortAddress::decode("alsa-seq1:24:3:sideways").is_err());
    }
}
