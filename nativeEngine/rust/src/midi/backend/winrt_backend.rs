use crate::midi::backend::{BackendPortHandle, MidiBackend};
use crate::midi::error::MidiError;
use crate::midi::grouping::sort_ports;
use crate::midi::types::*;
use std::collections::BTreeMap;
use std::sync::{Mutex, OnceLock, mpsc};
use windows::Devices::Enumeration::{DeviceInformation, DeviceInformationUpdate, DeviceWatcher};
use windows::Devices::Midi::{MidiInPort, MidiMessageReceivedEventArgs, MidiOutPort};
use windows::Foundation::{EventRegistrationToken, IPropertyValue, TypedEventHandler};
use windows::Storage::Streams::{DataReader, DataWriter};
use windows::Win32::System::Com::CoIncrementMTAUsage;
use windows::core::{GUID, HSTRING, Interface};

const BACKEND_PREFIX: &str = "winrt1";
const CONTAINER_ID_PROPERTY: &str = "System.Devices.ContainerId";
const FRIENDLY_NAME_PROPERTY: &str = "System.ItemNameDisplay";
const MANUFACTURER_PROPERTY: &str = "System.Devices.Manufacturer";
const MODEL_PROPERTY: &str = "System.Devices.ModelName";

#[derive(Clone)]
struct WinRtPort {
    id: String,
    name: String,
    direction: MidiPortDirection,
    device_id: String,
    container_id: String,
    manufacturer: Option<String>,
    model: Option<String>,
}

pub struct WinRtBackend {
    device_changes: Mutex<mpsc::Receiver<()>>,
    _watchers: Vec<WinRtDeviceWatcher>,
}

impl WinRtBackend {
    pub fn new() -> Result<Self, MidiError> {
        ensure_mta();

        let (device_change_sender, device_changes) = mpsc::channel();
        let mut watchers = Vec::new();

        for selector in [
            MidiInPort::GetDeviceSelector().map_err(to_backend_error)?,
            MidiOutPort::GetDeviceSelector().map_err(to_backend_error)?,
        ] {
            watchers.push(WinRtDeviceWatcher::start(
                selector,
                device_change_sender.clone(),
            )?);
        }

        Ok(Self {
            device_changes: Mutex::new(device_changes),
            _watchers: watchers,
        })
    }
}

impl MidiBackend for WinRtBackend {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        let mut ports = Vec::new();
        ports.extend(discover_ports(MidiPortDirection::Input)?);
        ports.extend(discover_ports(MidiPortDirection::Output)?);

        let mut grouped: BTreeMap<String, Vec<WinRtPort>> = BTreeMap::new();
        for port in ports {
            grouped
                .entry(port.container_id.clone())
                .or_default()
                .push(port);
        }

        let mut devices = Vec::new();
        for (container_id, grouped_ports) in grouped {
            let first = match grouped_ports.first() {
                Some(port) => port,
                None => continue,
            };

            let mut midi_ports = Vec::with_capacity(grouped_ports.len());
            for (index, port) in grouped_ports.iter().enumerate() {
                midi_ports.push(MidiPortInfo {
                    id: port.id.clone(),
                    name: port.name.clone(),
                    direction: port.direction,
                    port_number: index as u32,
                    is_available: true,
                });
            }
            sort_ports(&mut midi_ports);

            devices.push(MidiDeviceInfo {
                id: format!("{}:{}", BACKEND_PREFIX, encode_component(&container_id)),
                name: first.model.clone().unwrap_or_else(|| first.name.clone()),
                manufacturer: first.manufacturer.clone(),
                model: first.model.clone(),
                serial_number: None,
                usb_vendor_id: None,
                usb_product_id: None,
                transport: infer_transport(&grouped_ports),
                ports: midi_ports,
            });
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
        sender: mpsc::Sender<MidiMessage>,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let decoded = decode_port_id(port_id)?;
        if decoded.direction != MidiPortDirection::Input {
            return Err(MidiError::PortNotFound {
                port_id: port_id.to_string(),
            });
        }

        let device_id = HSTRING::from(decoded.device_id);
        let input_port = MidiInPort::FromIdAsync(&device_id)
            .map_err(to_connection_error)?
            .get()
            .map_err(to_connection_error)?;

        let port_id_clone = port_id.to_string();
        let handler = TypedEventHandler::<MidiInPort, MidiMessageReceivedEventArgs>::new(
            move |_port, args: &Option<MidiMessageReceivedEventArgs>| {
                if let Some(args) = args {
                    if let Some((data, timestamp_us)) = read_midi_message(args) {
                        let _ = sender.send(MidiMessage {
                            data,
                            timestamp_us,
                            port_id: port_id_clone.clone(),
                        });
                    }
                }
                Ok(())
            },
        );
        let event_token = input_port
            .MessageReceived(&handler)
            .map_err(to_connection_error)?;

        Ok(Box::new(WinRtInputPortHandle {
            port_id: port_id.to_string(),
            port: input_port,
            event_token: Mutex::new(Some(event_token)),
        }))
    }

    fn open_output(&self, port_id: &str) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let decoded = decode_port_id(port_id)?;
        if decoded.direction != MidiPortDirection::Output {
            return Err(MidiError::PortNotFound {
                port_id: port_id.to_string(),
            });
        }

        let device_id = HSTRING::from(decoded.device_id);
        let output_port = MidiOutPort::FromIdAsync(&device_id)
            .map_err(to_connection_error)?
            .get()
            .map_err(to_connection_error)?;

        Ok(Box::new(WinRtOutputPortHandle {
            port_id: port_id.to_string(),
            port: output_port,
        }))
    }

    fn name(&self) -> &str {
        "WinRT MIDI 1.0 (Windows MIDI Services fallback)"
    }
}

struct WinRtInputPortHandle {
    port_id: String,
    port: MidiInPort,
    event_token: Mutex<Option<EventRegistrationToken>>,
}

impl BackendPortHandle for WinRtInputPortHandle {
    fn send(&self, _data: &[u8]) -> Result<(), MidiError> {
        Err(MidiError::SendFailed {
            reason: "Port is not opened for output".into(),
        })
    }

    fn close(&self) -> Result<(), MidiError> {
        if let Some(token) = self.event_token.lock().unwrap().take() {
            let _ = self.port.RemoveMessageReceived(token);
        }
        self.port.Close().map_err(to_connection_error)
    }

    fn port_id(&self) -> &str {
        &self.port_id
    }
}

struct WinRtOutputPortHandle {
    port_id: String,
    port: windows::Devices::Midi::IMidiOutPort,
}

// SAFETY: WinRT MIDI ports are agile objects (they carry no apartment affinity),
// so the underlying `IMidiOutPort` can be used from any thread. The `windows`
// crate does not auto-derive Send/Sync for the bare interface type, hence the
// manual impls.
unsafe impl Send for WinRtOutputPortHandle {}
unsafe impl Sync for WinRtOutputPortHandle {}

impl BackendPortHandle for WinRtOutputPortHandle {
    fn send(&self, data: &[u8]) -> Result<(), MidiError> {
        let writer = DataWriter::new().map_err(to_send_error)?;
        writer.WriteBytes(data).map_err(to_send_error)?;
        let buffer = writer.DetachBuffer().map_err(to_send_error)?;
        self.port.SendBuffer(&buffer).map_err(to_send_error)
    }

    fn close(&self) -> Result<(), MidiError> {
        self.port.Close().map_err(to_connection_error)
    }

    fn port_id(&self) -> &str {
        &self.port_id
    }
}

struct WinRtDeviceWatcher {
    watcher: DeviceWatcher,
    added: EventRegistrationToken,
    updated: EventRegistrationToken,
    removed: EventRegistrationToken,
}

impl WinRtDeviceWatcher {
    fn start(selector: HSTRING, sender: mpsc::Sender<()>) -> Result<Self, MidiError> {
        let watcher =
            DeviceInformation::CreateWatcherAqsFilter(&selector).map_err(to_backend_error)?;

        let added_sender = sender.clone();
        let added_handler =
            TypedEventHandler::<DeviceWatcher, DeviceInformation>::new(move |_watcher, _info| {
                let _ = added_sender.send(());
                Ok(())
            });
        let added = watcher.Added(&added_handler).map_err(to_backend_error)?;

        let updated_sender = sender.clone();
        let updated_handler = TypedEventHandler::<DeviceWatcher, DeviceInformationUpdate>::new(
            move |_watcher, _info| {
                let _ = updated_sender.send(());
                Ok(())
            },
        );
        let updated = watcher
            .Updated(&updated_handler)
            .map_err(to_backend_error)?;

        let removed_handler = TypedEventHandler::<DeviceWatcher, DeviceInformationUpdate>::new(
            move |_watcher, _info| {
                let _ = sender.send(());
                Ok(())
            },
        );
        let removed = watcher
            .Removed(&removed_handler)
            .map_err(to_backend_error)?;

        watcher.Start().map_err(to_backend_error)?;

        Ok(Self {
            watcher,
            added,
            updated,
            removed,
        })
    }
}

impl Drop for WinRtDeviceWatcher {
    fn drop(&mut self) {
        let _ = self.watcher.RemoveAdded(self.added);
        let _ = self.watcher.RemoveUpdated(self.updated);
        let _ = self.watcher.RemoveRemoved(self.removed);
        let _ = self.watcher.Stop();
    }
}

fn ensure_mta() {
    // WinRT calls in this backend are invoked from arbitrary threads handed to us
    // by uniffi/Kotlin. Blocking `IAsyncOperation::get()` calls and `DeviceWatcher`
    // callbacks require a multithreaded apartment (MTA). A thread that has never
    // called `CoInitializeEx` implicitly joins the process-wide MTA on first COM
    // use, but that implicit MTA is torn down as soon as the last MTA thread goes
    // away. `CoIncrementMTAUsage` keeps an implicit MTA alive for the entire
    // process lifetime, so our calls never deadlock (STA) or fail (uninitialized).
    static MTA_GUARD: OnceLock<()> = OnceLock::new();
    MTA_GUARD.get_or_init(|| {
        unsafe {
            // Intentionally leak the returned cookie: we never want to decrement
            // the usage count, so the MTA stays alive until the process exits.
            let _ = CoIncrementMTAUsage();
        }
    });
}

fn discover_ports(direction: MidiPortDirection) -> Result<Vec<WinRtPort>, MidiError> {
    let selector = match direction {
        MidiPortDirection::Input => MidiInPort::GetDeviceSelector(),
        MidiPortDirection::Output => MidiOutPort::GetDeviceSelector(),
    }
    .map_err(to_backend_error)?;

    let collection = DeviceInformation::FindAllAsyncAqsFilter(&selector)
        .map_err(to_backend_error)?
        .get()
        .map_err(to_backend_error)?;

    let mut ports = Vec::new();
    for info in collection {
        ports.push(port_from_device_info(info, direction)?);
    }
    Ok(ports)
}

fn port_from_device_info(
    info: DeviceInformation,
    direction: MidiPortDirection,
) -> Result<WinRtPort, MidiError> {
    let device_id = info.Id().map_err(to_backend_error)?.to_string_lossy();
    let name = property_string(&info, FRIENDLY_NAME_PROPERTY)
        .or_else(|| info.Name().ok().map(|value| value.to_string_lossy()))
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| "Windows MIDI Device".to_string());
    let container_id = property_guid(&info, CONTAINER_ID_PROPERTY)
        .or_else(|| property_string(&info, CONTAINER_ID_PROPERTY))
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| fallback_container_id(&device_id, &name));
    let manufacturer = property_string(&info, MANUFACTURER_PROPERTY);
    let model = property_string(&info, MODEL_PROPERTY);

    Ok(WinRtPort {
        id: encode_port_id(direction, &device_id),
        name,
        direction,
        device_id,
        container_id,
        manufacturer,
        model,
    })
}

fn read_midi_message(args: &MidiMessageReceivedEventArgs) -> Option<(Vec<u8>, u64)> {
    let message = args.Message().ok()?;
    let timestamp_us = message.Timestamp().ok()?.Duration as u64 / 10;
    let buffer = message.RawData().ok()?;
    let length = buffer.Length().ok()? as usize;
    let reader = DataReader::FromBuffer(&buffer).ok()?;
    let mut bytes = vec![0; length];
    reader.ReadBytes(&mut bytes).ok()?;
    Some((bytes, timestamp_us))
}

fn property_string(info: &DeviceInformation, key: &str) -> Option<String> {
    let properties = info.Properties().ok()?;
    let key = HSTRING::from(key);
    if !properties.HasKey(&key).ok()? {
        return None;
    }

    let value = properties.Lookup(&key).ok()?;
    let property_value = value.cast::<IPropertyValue>().ok()?;
    property_value
        .GetString()
        .ok()
        .map(|value| value.to_string_lossy())
}

fn property_guid(info: &DeviceInformation, key: &str) -> Option<String> {
    let properties = info.Properties().ok()?;
    let key = HSTRING::from(key);
    if !properties.HasKey(&key).ok()? {
        return None;
    }

    let value = properties.Lookup(&key).ok()?;
    let property_value = value.cast::<IPropertyValue>().ok()?;
    property_value.GetGuid().ok().map(format_guid)
}

fn fallback_container_id(device_id: &str, _name: &str) -> String {
    // Without a real ContainerId we cannot prove that two ports belong to the same
    // physical device. Grouping by name would pair an in/out port nicely, but it
    // would also wrongly merge two identical controllers (e.g. two Launchpads of
    // the same model) into a single device. For a light-show app that addresses
    // specific hardware this is worse than losing the in/out pairing, so we fall
    // back to the unique endpoint id and keep each endpoint separate.
    format!("device:{}", device_id)
}

fn infer_transport(ports: &[WinRtPort]) -> MidiTransportType {
    let haystack = ports
        .iter()
        .map(|port| format!("{} {}", port.device_id, port.name).to_ascii_lowercase())
        .collect::<Vec<_>>()
        .join(" ");

    if haystack.contains("bluetooth") || haystack.contains("ble") {
        MidiTransportType::Bluetooth
    } else if haystack.contains("network") || haystack.contains("rtp") {
        MidiTransportType::Network
    } else if haystack.contains("loopback") || haystack.contains("virtual") {
        MidiTransportType::Virtual
    } else if haystack.contains("usb") || haystack.contains("ks") {
        MidiTransportType::Usb
    } else {
        MidiTransportType::Unknown
    }
}

#[derive(Debug, PartialEq, Eq)]
struct DecodedPortId {
    direction: MidiPortDirection,
    device_id: String,
}

fn encode_port_id(direction: MidiPortDirection, device_id: &str) -> String {
    let dir = match direction {
        MidiPortDirection::Input => "in",
        MidiPortDirection::Output => "out",
    };
    format!("{}:{}:{}", BACKEND_PREFIX, dir, encode_component(device_id))
}

fn decode_port_id(port_id: &str) -> Result<DecodedPortId, MidiError> {
    let mut parts = port_id.splitn(3, ':');
    let prefix = parts.next();
    let direction = parts.next();
    let encoded_device_id = parts.next();

    if prefix != Some(BACKEND_PREFIX) {
        return Err(MidiError::PortNotFound {
            port_id: port_id.to_string(),
        });
    }

    let direction = match direction {
        Some("in") => MidiPortDirection::Input,
        Some("out") => MidiPortDirection::Output,
        _ => {
            return Err(MidiError::PortNotFound {
                port_id: port_id.to_string(),
            });
        }
    };

    let device_id = encoded_device_id
        .and_then(decode_component)
        .ok_or_else(|| MidiError::PortNotFound {
            port_id: port_id.to_string(),
        })?;

    Ok(DecodedPortId {
        direction,
        device_id,
    })
}

fn encode_component(value: &str) -> String {
    value
        .as_bytes()
        .iter()
        .map(|byte| format!("{:02x}", byte))
        .collect()
}

fn decode_component(value: &str) -> Option<String> {
    if value.len() % 2 != 0 {
        return None;
    }

    let mut bytes = Vec::with_capacity(value.len() / 2);
    for pair in value.as_bytes().chunks_exact(2) {
        let hex = std::str::from_utf8(pair).ok()?;
        bytes.push(u8::from_str_radix(hex, 16).ok()?);
    }
    String::from_utf8(bytes).ok()
}

fn format_guid(guid: GUID) -> String {
    format!(
        "{:08x}-{:04x}-{:04x}-{:02x}{:02x}-{:02x}{:02x}{:02x}{:02x}{:02x}{:02x}",
        guid.data1,
        guid.data2,
        guid.data3,
        guid.data4[0],
        guid.data4[1],
        guid.data4[2],
        guid.data4[3],
        guid.data4[4],
        guid.data4[5],
        guid.data4[6],
        guid.data4[7],
    )
}

fn to_backend_error(error: windows::core::Error) -> MidiError {
    MidiError::BackendError {
        reason: error.message().to_string(),
    }
}

fn to_connection_error(error: windows::core::Error) -> MidiError {
    MidiError::ConnectionFailed {
        reason: error.message().to_string(),
    }
}

fn to_send_error(error: windows::core::Error) -> MidiError {
    MidiError::SendFailed {
        reason: error.message().to_string(),
    }
}
