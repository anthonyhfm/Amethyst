use crate::midi::backend::{BackendPortHandle, MidiBackend, monotonic_micros};
use crate::midi::error::MidiError;
use crate::midi::parser::MidiStreamParser;
use crate::midi::types::{
    MidiDeviceInfo, MidiMessage, MidiPortDirection, MidiPortInfo, MidiTransportType,
};
use alsa::Direction;
use alsa::poll;
use alsa::seq::{
    Addr, ClientIter, EventType, MidiEvent, PortCap, PortIter, PortSubscribe, PortSubscribeIter,
    PortType, QuerySubsType, Seq,
};
use std::collections::BTreeMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, mpsc};
use std::thread::JoinHandle;

const BACKEND_PREFIX: &str = "alsa-seq1";
const MIDI_BUFFER_SIZE: u32 = 65_536;
const MAX_EVENTS_PER_POLL: usize = 1_024;

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
        let client = parts[1].parse().map_err(|_| port_not_found(value))?;
        let port = parts[2].parse().map_err(|_| port_not_found(value))?;
        // alsa-rs casts Addr fields to u8; 253..=255 are reserved broadcasts.
        if !(0..=252).contains(&client) || !(0..=252).contains(&port) {
            return Err(port_not_found(value));
        }
        Ok(Self {
            client,
            port,
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
                PortCap::WRITE | PortCap::SUBS_WRITE | PortCap::NO_EXPORT,
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
    discovery: Mutex<Seq>,
}

impl AlsaBackend {
    pub fn new() -> Result<Self, MidiError> {
        let discovery = open_seq(None, false)?;
        discovery
            .set_client_name(c"Amethyst MIDI Discovery")
            .map_err(alsa_error)?;
        Ok(Self {
            monitor: Mutex::new(HotplugMonitor::new()?),
            discovery: Mutex::new(discovery),
        })
    }
}

impl MidiBackend for AlsaBackend {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        // A persistent client prevents discovery from generating hotplug events.
        let seq = self.discovery.lock().unwrap();
        let own_client = seq.client_id().map_err(alsa_error)?;
        let mut devices = BTreeMap::<String, MidiDeviceInfo>::new();

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
                alsa_card_identity(card, &name)
            } else {
                format!("virtual-client:{client_id}:{}", encode_component(&name))
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

            let device_id = format!("{BACKEND_PREFIX}:device:{stable_parent}");
            let discovered_device = MidiDeviceInfo {
                id: device_id.clone(),
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
            };
            if let Some(existing) = devices.get_mut(&device_id) {
                existing.ports.extend(discovered_device.ports);
                if discovered_device.transport == MidiTransportType::Usb {
                    existing.transport = MidiTransportType::Usb;
                }
            } else {
                devices.insert(device_id, discovered_device);
            }
        }

        for device in devices.values_mut() {
            device
                .ports
                .sort_by_key(|port| (port.direction as u8, port.port_number, port.id.clone()));
            let mut next_input = 0;
            let mut next_output = 0;
            for port in &mut device.ports {
                port.port_number = match port.direction {
                    MidiPortDirection::Input => {
                        let number = next_input;
                        next_input += 1;
                        number
                    }
                    MidiPortDirection::Output => {
                        let number = next_output;
                        next_output += 1;
                        number
                    }
                };
            }
        }

        Ok(devices.into_values().collect())
    }

    fn wait_for_device_change(&self, timeout_ms: u64) -> bool {
        match self.monitor.lock() {
            Ok(mut monitor) => monitor.wait(timeout_ms),
            Err(poisoned) => poisoned.into_inner().wait(timeout_ms),
        }
    }

    fn open_input(
        &self,
        port_id: &str,
        sender: mpsc::SyncSender<MidiMessage>,
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
                PortCap::READ | PortCap::SUBS_READ | PortCap::NO_EXPORT,
                PortType::MIDI_GENERIC | PortType::APPLICATION,
            )
            .map_err(alsa_error)?;
        let source = Addr {
            client: seq.client_id().map_err(alsa_error)?,
            port: source_port,
        };
        let destination = address.alsa();
        subscribe(&seq, source, destination)?;

        Ok(Box::new(AlsaOutputHandle {
            port_id: port_id.to_string(),
            seq: Mutex::new(Some(seq)),
            source_port,
            source,
            destination,
            encoder: Mutex::new(SendMidiEncoder(
                MidiEvent::new(MIDI_BUFFER_SIZE).map_err(alsa_error)?,
            )),
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
    source: Addr,
    destination: Addr,
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
        if !has_subscription(seq, self.source, self.destination) {
            self.open.store(false, Ordering::Release);
            return Err(MidiError::PortNotOpen {
                port_id: self.port_id.clone(),
            });
        }
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
                // SUBSCRIBERS can succeed with no recipients after an unplug.
                event.set_dest(self.destination);
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
        if !self.open.load(Ordering::Acquire) {
            return false;
        }
        let connected = self
            .seq
            .lock()
            .unwrap()
            .as_ref()
            .is_some_and(|seq| has_subscription(seq, self.source, self.destination));
        if !connected {
            self.open.store(false, Ordering::Release);
        }
        connected
    }
}

fn run_input_connection(
    address: PortAddress,
    port_id: String,
    sender: mpsc::SyncSender<MidiMessage>,
    open: &AtomicBool,
    ready: mpsc::SyncSender<Result<(), String>>,
) -> Result<(), MidiError> {
    let seq = open_seq(Some(Direction::Capture), true)?;
    seq.set_client_name(c"Amethyst MIDI Input")
        .map_err(alsa_error)?;
    let destination_port = seq
        .create_simple_port(
            c"Input",
            PortCap::WRITE | PortCap::SUBS_WRITE | PortCap::NO_EXPORT,
            PortType::MIDI_GENERIC | PortType::APPLICATION,
        )
        .map_err(alsa_error)?;
    let source = address.alsa();
    let destination = Addr {
        client: seq.client_id().map_err(alsa_error)?,
        port: destination_port,
    };
    subscribe(&seq, source, destination)?;
    let _ = ready.send(Ok(()));

    let descriptor = (&seq, Some(Direction::Capture));
    let descriptors: [&dyn poll::Descriptors; 1] = [&descriptor];
    let decoder = MidiEvent::new(0).map_err(alsa_error)?;
    // Emit a status byte for every decoded sequencer event.
    decoder.enable_running_status(false);
    let mut parser = MidiStreamParser::new();
    let mut decode_buffer = vec![0u8; MIDI_BUFFER_SIZE as usize];
    while open.load(Ordering::Acquire) {
        // ALSA may reuse client and port numbers after a replug.
        if !has_subscription(&seq, source, destination) {
            return Ok(());
        }
        match poll::poll_all(&descriptors, 50) {
            Ok(events) if events.is_empty() => continue,
            Ok(_) => {}
            Err(error) if io_error_kind(&error) == std::io::ErrorKind::Interrupted => {
                continue;
            }
            Err(error) => return Err(alsa_error(error)),
        }
        let mut input = seq.input();
        let mut drained = 0usize;
        while open.load(Ordering::Acquire) && drained < MAX_EVENTS_PER_POLL {
            match input.event_input_pending(true) {
                Ok(pending) if pending > 0 => {}
                Ok(_) => break,
                Err(error)
                    if matches!(
                        io_error_kind(&error),
                        std::io::ErrorKind::Interrupted | std::io::ErrorKind::WouldBlock
                    ) =>
                {
                    break;
                }
                Err(error) => return Err(alsa_error(error)),
            }
            drained += 1;
            let mut event = match input.event_input() {
                Ok(event) => event,
                Err(error)
                    if matches!(
                        io_error_kind(&error),
                        std::io::ErrorKind::Interrupted | std::io::ErrorKind::WouldBlock
                    ) =>
                {
                    break;
                }
                Err(error) => return Err(alsa_error(error)),
            };
            if event.get_source() != source {
                continue;
            }
            let Ok(size) = decoder.decode(&mut decode_buffer, &mut event) else {
                continue;
            };
            if size == 0 {
                continue;
            }
            let timestamp_us = monotonic_micros();
            for (message_data, message_timestamp_us) in
                parser.push_with_timestamp(&decode_buffer[..size], timestamp_us)
            {
                match sender.try_send(MidiMessage {
                    data: message_data,
                    timestamp_us: message_timestamp_us,
                    port_id: port_id.clone(),
                }) {
                    Ok(()) => {}
                    Err(mpsc::TrySendError::Full(_) | mpsc::TrySendError::Disconnected(_)) => {
                        return Ok(());
                    }
                }
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

fn has_subscription(seq: &Seq, sender: Addr, destination: Addr) -> bool {
    PortSubscribeIter::new(seq, sender, QuerySubsType::READ)
        .any(|subscription| subscription.get_dest() == destination)
}

fn open_seq(direction: Option<Direction>, nonblock: bool) -> Result<Seq, MidiError> {
    Seq::open(None, direction, nonblock).map_err(alsa_error)
}

fn io_error_kind(error: &alsa::Error) -> std::io::ErrorKind {
    std::io::Error::from_raw_os_error(error.errno()).kind()
}

fn encode_component(value: &str) -> String {
    value
        .as_bytes()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect()
}

fn alsa_card_identity(card: i32, name: &str) -> String {
    let card_device = std::path::PathBuf::from(format!("/sys/class/sound/card{card}/device"));
    if let Ok(device_path) = std::fs::canonicalize(&card_device) {
        let mut current = Some(device_path.as_path());
        while let Some(path) = current {
            let serial = read_trimmed(path.join("serial"));
            let vendor = read_trimmed(path.join("idVendor"));
            let product = read_trimmed(path.join("idProduct"));
            if let Some(serial) = serial {
                return format!(
                    "hardware:{}:{}:{}",
                    vendor.unwrap_or_else(|| "unknown".into()),
                    product.unwrap_or_else(|| "unknown".into()),
                    encode_component(&serial),
                );
            }
            current = path.parent();
        }

        return format!(
            "hardware-path:{}",
            encode_component(&device_path.to_string_lossy())
        );
    }

    let card_id = read_trimmed(format!("/sys/class/sound/card{card}/id"))
        .unwrap_or_else(|| format!("{card}:{}", encode_component(name)));
    format!("alsa-card:{}", encode_component(&card_id))
}

fn read_trimmed(path: impl AsRef<std::path::Path>) -> Option<String> {
    std::fs::read_to_string(path)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
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
