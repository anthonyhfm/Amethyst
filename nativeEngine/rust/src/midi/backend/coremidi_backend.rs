use crate::midi::backend::{BackendPortHandle, MidiBackend, monotonic_micros};
use crate::midi::error::MidiError;
use crate::midi::parser::MidiStreamParser;
use crate::midi::types::*;
use core_foundation::base::TCFType;
use core_foundation::string::{CFString, CFStringRef};
use coremidi::Notification;
use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, mpsc};

#[repr(C)]
#[derive(Clone, Copy)]
struct MachTimebaseInfo {
    numer: u32,
    denom: u32,
}

unsafe extern "C" {
    fn mach_timebase_info(info: *mut MachTimebaseInfo) -> i32;
    fn mach_absolute_time() -> u64;
}

const MAX_PACKET_DATA_SIZE: usize = 65_522;

fn source_fallback_id(endpoint: coremidi_sys::MIDIEndpointRef) -> String {
    format!("src-ref:{endpoint}")
}

fn destination_fallback_id(endpoint: coremidi_sys::MIDIEndpointRef) -> String {
    format!("dst-ref:{endpoint}")
}

fn find_source(port_id: &str) -> Option<coremidi::Source> {
    if let Some(endpoint) = port_id
        .strip_prefix("src-ref:")
        .and_then(|value| value.parse::<coremidi_sys::MIDIEndpointRef>().ok())
    {
        unsafe {
            for index in 0..coremidi_sys::MIDIGetNumberOfSources() {
                if coremidi_sys::MIDIGetSource(index) == endpoint {
                    return coremidi::Source::from_index(index as usize);
                }
            }
        }
        return None;
    }

    let unique_id = port_id.parse::<i32>().ok()? as u32;
    coremidi::Sources
        .into_iter()
        .find(|source| source.unique_id() == Some(unique_id))
}

fn find_destination(port_id: &str) -> Option<coremidi::Destination> {
    if let Some(endpoint) = port_id
        .strip_prefix("dst-ref:")
        .and_then(|value| value.parse::<coremidi_sys::MIDIEndpointRef>().ok())
    {
        unsafe {
            for index in 0..coremidi_sys::MIDIGetNumberOfDestinations() {
                if coremidi_sys::MIDIGetDestination(index) == endpoint {
                    return coremidi::Destination::from_index(index as usize);
                }
            }
        }
        return None;
    }

    let unique_id = port_id.parse::<i32>().ok()? as u32;
    coremidi::Destinations
        .into_iter()
        .find(|destination| destination.unique_id() == Some(unique_id))
}

fn get_timebase() -> &'static MachTimebaseInfo {
    static TIMEBASE: OnceLock<MachTimebaseInfo> = OnceLock::new();
    TIMEBASE.get_or_init(|| {
        let mut info = MachTimebaseInfo { numer: 0, denom: 0 };
        let status = unsafe { mach_timebase_info(&mut info) };
        if status != 0 || info.denom == 0 {
            MachTimebaseInfo { numer: 1, denom: 1 }
        } else {
            info
        }
    })
}

fn host_time_to_process_micros(raw_timestamp: u64) -> u64 {
    static ORIGIN: OnceLock<(u64, u64)> = OnceLock::new();
    let (origin_host_time, origin_process_us) =
        *ORIGIN.get_or_init(|| (unsafe { mach_absolute_time() }, monotonic_micros()));
    let timebase = get_timebase();
    let delta_ticks = raw_timestamp.abs_diff(origin_host_time);
    let delta_us =
        (u128::from(delta_ticks) * u128::from(timebase.numer) / u128::from(timebase.denom) / 1_000)
            .min(u64::MAX as u128) as u64;
    if raw_timestamp >= origin_host_time {
        origin_process_us.saturating_add(delta_us)
    } else {
        origin_process_us.saturating_sub(delta_us)
    }
}

unsafe fn get_string_property(
    obj: coremidi_sys::MIDIObjectRef,
    prop_key: CFStringRef,
) -> Option<String> {
    let mut name_ref: CFStringRef = std::ptr::null();
    let status = unsafe { coremidi_sys::MIDIObjectGetStringProperty(obj, prop_key, &mut name_ref) };
    if status == 0 && !name_ref.is_null() {
        let cf_str = unsafe { CFString::wrap_under_create_rule(name_ref) };
        Some(cf_str.to_string())
    } else {
        None
    }
}

unsafe fn get_integer_property(
    obj: coremidi_sys::MIDIObjectRef,
    prop_key: CFStringRef,
) -> Option<i32> {
    let mut val = 0;
    let status = unsafe { coremidi_sys::MIDIObjectGetIntegerProperty(obj, prop_key, &mut val) };
    if status == 0 { Some(val) } else { None }
}

pub struct CoreMidiBackend {
    client: coremidi::Client,
    device_changes: Mutex<mpsc::Receiver<()>>,
    topology_generation: Arc<AtomicU64>,
}

impl CoreMidiBackend {
    pub fn new() -> Result<Self, MidiError> {
        let (device_change_sender, device_changes) = mpsc::channel();
        let topology_generation = Arc::new(AtomicU64::new(0));
        let notification_generation = Arc::clone(&topology_generation);
        let client = coremidi::Client::new_with_notifications(
            "Amethyst",
            move |notification: &Notification| {
                let topology_changed = matches!(
                    notification,
                    Notification::SetupChanged
                        | Notification::ObjectAdded(_)
                        | Notification::ObjectRemoved(_)
                        | Notification::PropertyChanged(_)
                        | Notification::IoError(_)
                );
                if topology_changed {
                    notification_generation.fetch_add(1, Ordering::AcqRel);
                    let _ = device_change_sender.send(());
                }
            },
        )
        .map_err(|e| MidiError::BackendError {
            reason: format!("Failed to create CoreMIDI client: {:?}", e),
        })?;
        Ok(Self {
            client,
            device_changes: Mutex::new(device_changes),
            topology_generation,
        })
    }
}

pub struct CoreMidiPortHandle {
    port_id: String,
    input_port: Option<coremidi::InputPort>,
    source: Option<coremidi::Source>,
    output_port: Option<coremidi::OutputPort>,
    destination: Option<coremidi::Destination>,
    open: Arc<AtomicBool>,
    topology_generation: Arc<AtomicU64>,
    opened_generation: u64,
}

impl BackendPortHandle for CoreMidiPortHandle {
    fn send(&self, data: &[u8]) -> Result<(), MidiError> {
        if !self.is_open() {
            return Err(MidiError::PortNotOpen {
                port_id: self.port_id.clone(),
            });
        }
        if let (Some(port), Some(dest)) = (&self.output_port, &self.destination) {
            if data.len() > MAX_PACKET_DATA_SIZE
                && (data.first() != Some(&0xF0) || data.last() != Some(&0xF7))
            {
                return Err(MidiError::SendFailed {
                    reason: "CoreMIDI messages larger than one packet must be SysEx".into(),
                });
            }
            for chunk in data.chunks(MAX_PACKET_DATA_SIZE) {
                let packets = coremidi::PacketBuffer::new(0, chunk);
                if let Err(error) = port.send(dest, &packets) {
                    self.open.store(false, Ordering::Release);
                    return Err(MidiError::SendFailed {
                        reason: format!("CoreMIDI send error: {error}"),
                    });
                }
            }
            Ok(())
        } else {
            Err(MidiError::SendFailed {
                reason: "Port is not opened for output".into(),
            })
        }
    }

    fn close(&self) -> Result<(), MidiError> {
        self.open.store(false, Ordering::Release);
        if let (Some(port), Some(src)) = (&self.input_port, &self.source) {
            let _ = port.disconnect_source(src);
        }
        Ok(())
    }

    fn port_id(&self) -> &str {
        &self.port_id
    }

    fn is_open(&self) -> bool {
        let is_current = self.opened_generation == self.topology_generation.load(Ordering::Acquire);
        if !is_current {
            self.open.store(false, Ordering::Release);
        }
        is_current && self.open.load(Ordering::Acquire)
    }
}
impl MidiBackend for CoreMidiBackend {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        let mut devices = Vec::new();

        unsafe {
            let device_count = coremidi_sys::MIDIGetNumberOfDevices();
            for i in 0..device_count {
                let dev = coremidi_sys::MIDIGetDevice(i);
                if dev == 0 {
                    continue;
                }

                let offline =
                    get_integer_property(dev, coremidi_sys::kMIDIPropertyOffline).unwrap_or(0);
                if offline != 0 {
                    continue;
                }

                let name = get_string_property(dev, coremidi_sys::kMIDIPropertyName)
                    .unwrap_or_else(|| "Unknown Device".to_string());
                let manufacturer =
                    get_string_property(dev, coremidi_sys::kMIDIPropertyManufacturer);
                let model = get_string_property(dev, coremidi_sys::kMIDIPropertyModel);
                let unique_id = get_integer_property(dev, coremidi_sys::kMIDIPropertyUniqueID);

                let entity_count = coremidi_sys::MIDIDeviceGetNumberOfEntities(dev);
                let mut ports = Vec::new();
                let mut input_port_number = 0;
                let mut output_port_number = 0;

                for j in 0..entity_count {
                    let entity = coremidi_sys::MIDIDeviceGetEntity(dev, j);
                    if entity == 0 {
                        continue;
                    }

                    let src_count = coremidi_sys::MIDIEntityGetNumberOfSources(entity);
                    for k in 0..src_count {
                        let endpoint = coremidi_sys::MIDIEntityGetSource(entity, k);
                        if endpoint == 0 {
                            continue;
                        }

                        let offline =
                            get_integer_property(endpoint, coremidi_sys::kMIDIPropertyOffline)
                                .unwrap_or(0);
                        if offline != 0 {
                            continue;
                        }

                        let port_id =
                            get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                                .map(|id| id.to_string())
                                .unwrap_or_else(|| source_fallback_id(endpoint));
                        let port_name =
                            get_string_property(endpoint, coremidi_sys::kMIDIPropertyName)
                                .unwrap_or_else(|| name.clone());

                        ports.push(MidiPortInfo {
                            id: port_id,
                            name: port_name,
                            direction: MidiPortDirection::Input,
                            port_number: input_port_number,
                            is_available: true,
                        });
                        input_port_number += 1;
                    }

                    let dest_count = coremidi_sys::MIDIEntityGetNumberOfDestinations(entity);
                    for k in 0..dest_count {
                        let endpoint = coremidi_sys::MIDIEntityGetDestination(entity, k);
                        if endpoint == 0 {
                            continue;
                        }

                        let offline =
                            get_integer_property(endpoint, coremidi_sys::kMIDIPropertyOffline)
                                .unwrap_or(0);
                        if offline != 0 {
                            continue;
                        }

                        let port_id =
                            get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                                .map(|id| id.to_string())
                                .unwrap_or_else(|| destination_fallback_id(endpoint));
                        let port_name =
                            get_string_property(endpoint, coremidi_sys::kMIDIPropertyName)
                                .unwrap_or_else(|| name.clone());

                        ports.push(MidiPortInfo {
                            id: port_id,
                            name: port_name,
                            direction: MidiPortDirection::Output,
                            port_number: output_port_number,
                            is_available: true,
                        });
                        output_port_number += 1;
                    }
                }

                if !ports.is_empty() {
                    let dev_id = unique_id
                        .map(|id| id.to_string())
                        .unwrap_or_else(|| format!("dev:{}", dev));
                    devices.push(MidiDeviceInfo {
                        id: dev_id,
                        name,
                        manufacturer,
                        model,
                        serial_number: None,
                        usb_vendor_id: None,
                        usb_product_id: None,
                        transport: MidiTransportType::Usb,
                        ports,
                    });
                }
            }

            // Discover virtual sources (endpoints without parent device)
            let src_count = coremidi_sys::MIDIGetNumberOfSources();
            for i in 0..src_count {
                let endpoint = coremidi_sys::MIDIGetSource(i);
                if endpoint == 0 {
                    continue;
                }

                let offline =
                    get_integer_property(endpoint, coremidi_sys::kMIDIPropertyOffline).unwrap_or(0);
                if offline != 0 {
                    continue;
                }

                let mut entity = 0;
                coremidi_sys::MIDIEndpointGetEntity(endpoint, &mut entity);
                if entity == 0 {
                    let port_id =
                        get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                            .map(|id| id.to_string())
                            .unwrap_or_else(|| source_fallback_id(endpoint));
                    let port_name = get_string_property(endpoint, coremidi_sys::kMIDIPropertyName)
                        .unwrap_or_else(|| "Virtual Input".to_string());

                    devices.push(MidiDeviceInfo {
                        id: format!("vdevice_in:{}", port_id),
                        name: port_name.clone(),
                        manufacturer: Some("Virtual".to_string()),
                        model: Some(port_name.clone()),
                        serial_number: None,
                        usb_vendor_id: None,
                        usb_product_id: None,
                        transport: MidiTransportType::Virtual,
                        ports: vec![MidiPortInfo {
                            id: port_id,
                            name: port_name,
                            direction: MidiPortDirection::Input,
                            port_number: 0,
                            is_available: true,
                        }],
                    });
                }
            }

            // Discover virtual destinations (endpoints without parent device)
            let dest_count = coremidi_sys::MIDIGetNumberOfDestinations();
            for i in 0..dest_count {
                let endpoint = coremidi_sys::MIDIGetDestination(i);
                if endpoint == 0 {
                    continue;
                }

                let offline =
                    get_integer_property(endpoint, coremidi_sys::kMIDIPropertyOffline).unwrap_or(0);
                if offline != 0 {
                    continue;
                }

                let mut entity = 0;
                coremidi_sys::MIDIEndpointGetEntity(endpoint, &mut entity);
                if entity == 0 {
                    let port_id =
                        get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                            .map(|id| id.to_string())
                            .unwrap_or_else(|| destination_fallback_id(endpoint));
                    let port_name = get_string_property(endpoint, coremidi_sys::kMIDIPropertyName)
                        .unwrap_or_else(|| "Virtual Output".to_string());

                    devices.push(MidiDeviceInfo {
                        id: format!("vdevice_out:{}", port_id),
                        name: port_name.clone(),
                        manufacturer: Some("Virtual".to_string()),
                        model: Some(port_name.clone()),
                        serial_number: None,
                        usb_vendor_id: None,
                        usb_product_id: None,
                        transport: MidiTransportType::Virtual,
                        ports: vec![MidiPortInfo {
                            id: port_id,
                            name: port_name,
                            direction: MidiPortDirection::Output,
                            port_number: 0,
                            is_available: true,
                        }],
                    });
                }
            }
        }

        Ok(devices)
    }

    fn wait_for_device_change(&self, timeout_ms: u64) -> bool {
        let receiver = self.device_changes.lock().unwrap();
        if receiver
            .recv_timeout(std::time::Duration::from_millis(timeout_ms))
            .is_err()
        {
            return false;
        }

        while receiver.try_recv().is_ok() {}
        true
    }

    fn open_input(
        &self,
        port_id: &str,
        sender: mpsc::SyncSender<MidiMessage>,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let opened_generation = self.topology_generation.load(Ordering::Acquire);
        let source = find_source(port_id).ok_or_else(|| MidiError::PortNotFound {
            port_id: port_id.to_string(),
        })?;

        let port_id_clone = port_id.to_string();
        let parser = Mutex::new(MidiStreamParser::new());
        let open = Arc::new(AtomicBool::new(true));
        let callback_open = Arc::clone(&open);
        let input_port = self
            .client
            .input_port("Amethyst Input Port", move |packet_list| {
                if !callback_open.load(Ordering::Acquire) {
                    return;
                }
                for packet in packet_list.iter() {
                    let bytes = packet.data();
                    let raw_timestamp = match packet.timestamp() {
                        0 => unsafe { mach_absolute_time() },
                        timestamp => timestamp,
                    };
                    let timestamp_us = host_time_to_process_micros(raw_timestamp);

                    let messages = parser
                        .lock()
                        .unwrap()
                        .push_with_timestamp(bytes, timestamp_us);
                    for (msg_bytes, message_timestamp_us) in messages {
                        let msg = MidiMessage {
                            data: msg_bytes,
                            timestamp_us: message_timestamp_us,
                            port_id: port_id_clone.clone(),
                        };
                        if sender.try_send(msg).is_err() {
                            callback_open.store(false, Ordering::Release);
                            return;
                        }
                    }
                }
            })
            .map_err(|e| MidiError::ConnectionFailed {
                reason: format!("Failed to create CoreMIDI input port: {}", e),
            })?;

        input_port
            .connect_source(&source)
            .map_err(|e| MidiError::ConnectionFailed {
                reason: format!("Failed to connect CoreMIDI source: {}", e),
            })?;
        if self.topology_generation.load(Ordering::Acquire) != opened_generation {
            let _ = input_port.disconnect_source(&source);
            open.store(false, Ordering::Release);
            return Err(MidiError::ConnectionFailed {
                reason: "CoreMIDI topology changed while opening input".into(),
            });
        }

        Ok(Box::new(CoreMidiPortHandle {
            port_id: port_id.to_string(),
            input_port: Some(input_port),
            source: Some(source),
            output_port: None,
            destination: None,
            open,
            topology_generation: Arc::clone(&self.topology_generation),
            opened_generation,
        }))
    }

    fn open_output(&self, port_id: &str) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let opened_generation = self.topology_generation.load(Ordering::Acquire);
        let destination = find_destination(port_id).ok_or_else(|| MidiError::PortNotFound {
            port_id: port_id.to_string(),
        })?;

        let output_port = self
            .client
            .output_port("Amethyst Output Port")
            .map_err(|e| MidiError::ConnectionFailed {
                reason: format!("Failed to create CoreMIDI output port: {}", e),
            })?;
        if self.topology_generation.load(Ordering::Acquire) != opened_generation {
            return Err(MidiError::ConnectionFailed {
                reason: "CoreMIDI topology changed while opening output".into(),
            });
        }

        Ok(Box::new(CoreMidiPortHandle {
            port_id: port_id.to_string(),
            input_port: None,
            source: None,
            output_port: Some(output_port),
            destination: Some(destination),
            open: Arc::new(AtomicBool::new(true)),
            topology_generation: Arc::clone(&self.topology_generation),
            opened_generation,
        }))
    }

    fn name(&self) -> &str {
        "CoreMIDI"
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_coremidi_api() {
        let backend = CoreMidiBackend::new().unwrap();
        let devices = backend.discover_devices().unwrap();
        println!("Discovered {} MIDI devices on macOS", devices.len());
        for dev in devices {
            println!(
                "  Device: {} (Manufacturer: {:?}, Model: {:?})",
                dev.name, dev.manufacturer, dev.model
            );
            for port in dev.ports {
                println!("    Port: {} ({:?})", port.name, port.direction);
            }
        }
    }

    #[test]
    fn topology_change_invalidates_an_open_handle() {
        let topology_generation = Arc::new(AtomicU64::new(4));
        let handle = CoreMidiPortHandle {
            port_id: "test".into(),
            input_port: None,
            source: None,
            output_port: None,
            destination: None,
            open: Arc::new(AtomicBool::new(true)),
            topology_generation: Arc::clone(&topology_generation),
            opened_generation: 4,
        };

        assert!(handle.is_open());
        topology_generation.fetch_add(1, Ordering::AcqRel);
        assert!(!handle.is_open());
        assert!(!handle.open.load(Ordering::Acquire));
    }
}
