use crate::midi::backend::{BackendPortHandle, MidiBackend, monotonic_micros};
use crate::midi::error::MidiError;
use crate::midi::parser::MidiStreamParser;
use crate::midi::types::*;
use core_foundation::base::TCFType;
use core_foundation::runloop::CFRunLoop;
use core_foundation::string::{CFString, CFStringRef};
use coremidi::Notification;
use std::collections::{HashMap, VecDeque};
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

/// Finds a system source endpoint by port id, returning both its index (so a
/// `coremidi::Source` can be built) and its raw endpoint reference (so it can
/// be revalidated later without needing to hold on to a live `Source`).
fn locate_source(port_id: &str) -> Option<(usize, coremidi_sys::MIDIEndpointRef)> {
    if let Some(endpoint) = port_id
        .strip_prefix("src-ref:")
        .and_then(|value| value.parse::<coremidi_sys::MIDIEndpointRef>().ok())
    {
        unsafe {
            for index in 0..coremidi_sys::MIDIGetNumberOfSources() {
                if coremidi_sys::MIDIGetSource(index) == endpoint {
                    return Some((index as usize, endpoint));
                }
            }
        }
        return None;
    }

    let unique_id = port_id.parse::<i32>().ok()? as u32;
    unsafe {
        for index in 0..coremidi_sys::MIDIGetNumberOfSources() {
            let endpoint = coremidi_sys::MIDIGetSource(index);
            if endpoint == 0 {
                continue;
            }
            let endpoint_unique_id =
                get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                    .map(|value| value as u32);
            if endpoint_unique_id == Some(unique_id) {
                return Some((index as usize, endpoint));
            }
        }
    }
    None
}

/// Finds a system destination endpoint by port id. See [`locate_source`].
fn locate_destination(port_id: &str) -> Option<(usize, coremidi_sys::MIDIEndpointRef)> {
    if let Some(endpoint) = port_id
        .strip_prefix("dst-ref:")
        .and_then(|value| value.parse::<coremidi_sys::MIDIEndpointRef>().ok())
    {
        unsafe {
            for index in 0..coremidi_sys::MIDIGetNumberOfDestinations() {
                if coremidi_sys::MIDIGetDestination(index) == endpoint {
                    return Some((index as usize, endpoint));
                }
            }
        }
        return None;
    }

    let unique_id = port_id.parse::<i32>().ok()? as u32;
    unsafe {
        for index in 0..coremidi_sys::MIDIGetNumberOfDestinations() {
            let endpoint = coremidi_sys::MIDIGetDestination(index);
            if endpoint == 0 {
                continue;
            }
            let endpoint_unique_id =
                get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                    .map(|value| value as u32);
            if endpoint_unique_id == Some(unique_id) {
                return Some((index as usize, endpoint));
            }
        }
    }
    None
}

fn find_source(port_id: &str) -> Option<coremidi::Source> {
    let (index, _) = locate_source(port_id)?;
    coremidi::Source::from_index(index)
}

fn find_destination(port_id: &str) -> Option<coremidi::Destination> {
    let (index, _) = locate_destination(port_id)?;
    coremidi::Destination::from_index(index)
}

fn find_source_ref(port_id: &str) -> Option<coremidi_sys::MIDIEndpointRef> {
    locate_source(port_id).map(|(_, endpoint)| endpoint)
}

fn find_destination_ref(port_id: &str) -> Option<coremidi_sys::MIDIEndpointRef> {
    locate_destination(port_id).map(|(_, endpoint)| endpoint)
}

/// Returns whether an endpoint is still usable: it (and, when it belongs to
/// one, its owning entity's device) must not be marked offline. Endpoints
/// without an owning entity (virtual endpoints) are only checked directly.
fn endpoint_is_valid(endpoint: coremidi_sys::MIDIEndpointRef) -> bool {
    unsafe {
        let endpoint_offline =
            get_integer_property(endpoint, coremidi_sys::kMIDIPropertyOffline).unwrap_or(0);
        if endpoint_offline != 0 {
            return false;
        }

        let mut entity: coremidi_sys::MIDIEntityRef = 0;
        if coremidi_sys::MIDIEndpointGetEntity(endpoint, &mut entity) == 0 && entity != 0 {
            let mut device: coremidi_sys::MIDIDeviceRef = 0;
            if coremidi_sys::MIDIEntityGetDevice(entity, &mut device) == 0 && device != 0 {
                let device_offline =
                    get_integer_property(device, coremidi_sys::kMIDIPropertyOffline).unwrap_or(0);
                if device_offline != 0 {
                    return false;
                }
            }
        }

        true
    }
}

fn source_still_valid(port_id: &str) -> bool {
    find_source_ref(port_id).is_some_and(endpoint_is_valid)
}

fn destination_still_valid(port_id: &str) -> bool {
    find_destination_ref(port_id).is_some_and(endpoint_is_valid)
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

fn hex_lower(bytes: &[u8]) -> String {
    let mut out = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        out.push_str(&format!("{byte:02x}"));
    }
    out
}

/// Pairs virtual MIDI endpoints (sources/destinations with no owning entity)
/// that share the same name into a single bidirectional device, so protocols
/// that need both an input and an output on the same device (like device
/// inquiry) can find one. An endpoint with no same-named counterpart of the
/// opposite direction keeps the previous single-port `vdevice_in:`/
/// `vdevice_out:` behaviour.
///
/// This is a pure function over `(name, port_id, direction)` tuples so it can
/// be unit-tested without any CoreMIDI state.
fn pair_virtual_endpoints(
    entries: Vec<(String, String, MidiPortDirection)>,
) -> Vec<MidiDeviceInfo> {
    let mut order: Vec<String> = Vec::new();
    let mut inputs: HashMap<String, VecDeque<String>> = HashMap::new();
    let mut outputs: HashMap<String, VecDeque<String>> = HashMap::new();

    for (name, port_id, direction) in entries {
        if !order.contains(&name) {
            order.push(name.clone());
        }
        match direction {
            MidiPortDirection::Input => inputs.entry(name).or_default().push_back(port_id),
            MidiPortDirection::Output => outputs.entry(name).or_default().push_back(port_id),
        }
    }

    fn single_port_device(
        id: String,
        name: String,
        port_id: String,
        direction: MidiPortDirection,
    ) -> MidiDeviceInfo {
        MidiDeviceInfo {
            id,
            name: name.clone(),
            manufacturer: Some("Virtual".to_string()),
            model: Some(name.clone()),
            serial_number: None,
            usb_vendor_id: None,
            usb_product_id: None,
            transport: MidiTransportType::Virtual,
            ports: vec![MidiPortInfo {
                id: port_id,
                name,
                direction,
                port_number: 0,
                is_available: true,
            }],
        }
    }

    let mut devices = Vec::new();
    for name in order {
        let mut ins = inputs.remove(&name).unwrap_or_default();
        let mut outs = outputs.remove(&name).unwrap_or_default();

        // NOTE: this must not be a `while let (Some(a), Some(b)) = (ins.pop_front(), outs.pop_front())`
        // loop: that tuple is constructed eagerly, so both deques get popped
        // even when only one has an element left, silently dropping it when
        // the pattern then fails to match.
        while !ins.is_empty() && !outs.is_empty() {
            let input_id = ins.pop_front().expect("checked non-empty above");
            let output_id = outs.pop_front().expect("checked non-empty above");
            devices.push(MidiDeviceInfo {
                id: format!("vdevice:{}", hex_lower(name.as_bytes())),
                name: name.clone(),
                manufacturer: Some("Virtual".to_string()),
                model: Some(name.clone()),
                serial_number: None,
                usb_vendor_id: None,
                usb_product_id: None,
                transport: MidiTransportType::Virtual,
                ports: vec![
                    MidiPortInfo {
                        id: input_id,
                        name: name.clone(),
                        direction: MidiPortDirection::Input,
                        port_number: 0,
                        is_available: true,
                    },
                    MidiPortInfo {
                        id: output_id,
                        name: name.clone(),
                        direction: MidiPortDirection::Output,
                        port_number: 0,
                        is_available: true,
                    },
                ],
            });
        }

        for input_id in ins {
            devices.push(single_port_device(
                format!("vdevice_in:{input_id}"),
                name.clone(),
                input_id,
                MidiPortDirection::Input,
            ));
        }

        for output_id in outs {
            devices.push(single_port_device(
                format!("vdevice_out:{output_id}"),
                name.clone(),
                output_id,
                MidiPortDirection::Output,
            ));
        }
    }

    devices
}

pub struct CoreMidiBackend {
    client: coremidi::Client,
    device_changes: Mutex<mpsc::Receiver<()>>,
    topology_generation: Arc<AtomicU64>,
}

type HostJob = Box<dyn FnOnce() + Send>;

/// How long one run-loop pump may block before the host thread looks for
/// queued jobs again. Only affects the latency of client creation.
const CLIENT_HOST_PUMP_INTERVAL: std::time::Duration = std::time::Duration::from_millis(50);

/// Process-wide host thread for CoreMIDI clients.
///
/// CoreMIDI delivers notifications on the run loop that was current when
/// `MIDIClientCreate` was called for the *first time in the process*, and it
/// keeps using that run loop for every client created afterwards. That has two
/// consequences: every client must be created on a thread whose run loop is
/// actually running, and that run loop must never stop for the lifetime of the
/// process (stopping it would silently end notifications for all clients,
/// including ones created later). Hence a single, never-exiting host thread.
fn client_host() -> &'static Mutex<mpsc::Sender<HostJob>> {
    static HOST: OnceLock<Mutex<mpsc::Sender<HostJob>>> = OnceLock::new();
    HOST.get_or_init(|| {
        let (job_sender, job_receiver) = mpsc::channel::<HostJob>();
        std::thread::Builder::new()
            .name("amethyst-coremidi".to_string())
            .spawn(move || {
                loop {
                    loop {
                        match job_receiver.try_recv() {
                            Ok(job) => job(),
                            Err(mpsc::TryRecvError::Empty) => break,
                            Err(mpsc::TryRecvError::Disconnected) => return,
                        }
                    }
                    // Pump the run loop so CoreMIDI can deliver notifications.
                    // Before the first client exists the loop has no sources and
                    // returns `Finished` immediately; back off briefly then.
                    let result = unsafe {
                        CFRunLoop::run_in_mode(
                            core_foundation::runloop::kCFRunLoopDefaultMode,
                            CLIENT_HOST_PUMP_INTERVAL,
                            false,
                        )
                    };
                    if result == core_foundation::runloop::CFRunLoopRunResult::Finished {
                        std::thread::sleep(CLIENT_HOST_PUMP_INTERVAL);
                    }
                }
            })
            .expect("failed to spawn the CoreMIDI host thread");
        Mutex::new(job_sender)
    })
}

/// Runs `job` on the CoreMIDI host thread and waits for its result.
fn run_on_client_host<T: Send + 'static>(
    job: impl FnOnce() -> T + Send + 'static,
) -> Result<T, MidiError> {
    let (result_sender, result_receiver) = mpsc::channel::<T>();
    client_host()
        .lock()
        .unwrap()
        .send(Box::new(move || {
            let _ = result_sender.send(job());
        }))
        .map_err(|_| MidiError::BackendError {
            reason: "CoreMIDI host thread is gone".into(),
        })?;
    result_receiver.recv().map_err(|_| MidiError::BackendError {
        reason: "CoreMIDI host thread dropped a client creation job".into(),
    })
}

impl CoreMidiBackend {
    pub fn new() -> Result<Self, MidiError> {
        let (device_change_sender, device_changes) = mpsc::channel();
        let topology_generation = Arc::new(AtomicU64::new(0));
        let notification_generation = Arc::clone(&topology_generation);

        // See `client_host` for why the client must be created on that thread.
        let client = run_on_client_host(move || {
            coremidi::Client::new_with_notifications(
                "Amethyst",
                move |_notification: &Notification| {
                    // Every notification kind (including PropertyChanged, which
                    // is how we learn about offline transitions) bumps the
                    // generation. This does not invalidate open handles by
                    // itself: `CoreMidiPortHandle` revalidates lazily against
                    // the real endpoint state instead of treating every
                    // notification as fatal.
                    notification_generation.fetch_add(1, Ordering::AcqRel);
                    let _ = device_change_sender.send(());
                },
            )
        })?
        .map_err(|status| MidiError::BackendError {
            reason: format!("Failed to create CoreMIDI client: {status:?}"),
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
    last_validated_generation: AtomicU64,
    /// Checks whether the underlying endpoint still exists and is not
    /// offline. Boxed so it can be swapped out with a fake in unit tests,
    /// and built in `open_input`/`open_output` to capture the port id.
    validator: Box<dyn Fn() -> bool + Send + Sync>,
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
        if !self.open.load(Ordering::Acquire) {
            return false;
        }

        let current_generation = self.topology_generation.load(Ordering::Acquire);
        if self.last_validated_generation.load(Ordering::Acquire) == current_generation {
            return true;
        }

        // Topology changed since we last checked (which happens on *any*
        // CoreMIDI notification, not just ones affecting this endpoint).
        // Revalidate against the real endpoint state instead of assuming the
        // connection is dead.
        if (self.validator)() {
            self.last_validated_generation
                .store(current_generation, Ordering::Release);
            true
        } else {
            self.open.store(false, Ordering::Release);
            false
        }
    }
}
impl MidiBackend for CoreMidiBackend {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        let mut devices = Vec::new();
        let mut virtual_entries: Vec<(String, String, MidiPortDirection)> = Vec::new();

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

            // Collect virtual sources (endpoints without a parent entity).
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

                    virtual_entries.push((port_name, port_id, MidiPortDirection::Input));
                }
            }

            // Collect virtual destinations (endpoints without a parent entity).
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

                    virtual_entries.push((port_name, port_id, MidiPortDirection::Output));
                }
            }
        }

        // Virtual sources and destinations that share a name are folded into
        // a single bidirectional device so device-inquiry-style protocols
        // (which need both an input and an output) can drive them.
        devices.extend(pair_virtual_endpoints(virtual_entries));

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
                        match sender.try_send(msg) {
                            Ok(()) => {}
                            Err(mpsc::TrySendError::Full(_)) => {
                                // The consumer is behind; drop this message
                                // but keep the connection open. A single slow
                                // reader shouldn't be treated as a
                                // disconnect.
                            }
                            Err(mpsc::TrySendError::Disconnected(_)) => {
                                callback_open.store(false, Ordering::Release);
                                return;
                            }
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

        if !source_still_valid(port_id) {
            let _ = input_port.disconnect_source(&source);
            open.store(false, Ordering::Release);
            return Err(MidiError::ConnectionFailed {
                reason: "CoreMIDI source is gone or offline".into(),
            });
        }

        let last_validated_generation = self.topology_generation.load(Ordering::Acquire);
        let validator_port_id = port_id.to_string();
        let validator: Box<dyn Fn() -> bool + Send + Sync> =
            Box::new(move || source_still_valid(&validator_port_id));

        Ok(Box::new(CoreMidiPortHandle {
            port_id: port_id.to_string(),
            input_port: Some(input_port),
            source: Some(source),
            output_port: None,
            destination: None,
            open,
            topology_generation: Arc::clone(&self.topology_generation),
            last_validated_generation: AtomicU64::new(last_validated_generation),
            validator,
        }))
    }

    fn open_output(&self, port_id: &str) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let destination = find_destination(port_id).ok_or_else(|| MidiError::PortNotFound {
            port_id: port_id.to_string(),
        })?;

        let output_port = self
            .client
            .output_port("Amethyst Output Port")
            .map_err(|e| MidiError::ConnectionFailed {
                reason: format!("Failed to create CoreMIDI output port: {}", e),
            })?;

        if !destination_still_valid(port_id) {
            return Err(MidiError::ConnectionFailed {
                reason: "CoreMIDI destination is gone or offline".into(),
            });
        }

        let last_validated_generation = self.topology_generation.load(Ordering::Acquire);
        let validator_port_id = port_id.to_string();
        let validator: Box<dyn Fn() -> bool + Send + Sync> =
            Box::new(move || destination_still_valid(&validator_port_id));

        Ok(Box::new(CoreMidiPortHandle {
            port_id: port_id.to_string(),
            input_port: None,
            source: None,
            output_port: Some(output_port),
            destination: Some(destination),
            open: Arc::new(AtomicBool::new(true)),
            topology_generation: Arc::clone(&self.topology_generation),
            last_validated_generation: AtomicU64::new(last_validated_generation),
            validator,
        }))
    }

    fn name(&self) -> &str {
        "CoreMIDI"
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{Duration, Instant};

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

    fn test_handle(
        topology_generation: Arc<AtomicU64>,
        last_validated_generation: u64,
        validator_result: bool,
    ) -> CoreMidiPortHandle {
        CoreMidiPortHandle {
            port_id: "test".into(),
            input_port: None,
            source: None,
            output_port: None,
            destination: None,
            open: Arc::new(AtomicBool::new(true)),
            topology_generation,
            last_validated_generation: AtomicU64::new(last_validated_generation),
            validator: Box::new(move || validator_result),
        }
    }

    #[test]
    fn generation_bump_alone_does_not_close_a_handle_when_endpoint_is_still_valid() {
        let topology_generation = Arc::new(AtomicU64::new(4));
        let handle = test_handle(Arc::clone(&topology_generation), 4, true);

        assert!(handle.is_open());
        topology_generation.fetch_add(1, Ordering::AcqRel);
        // A bump alone (e.g. from an unrelated PropertyChanged notification)
        // must not close the handle: it should revalidate and stay open.
        assert!(handle.is_open());
        assert!(handle.open.load(Ordering::Acquire));
        assert_eq!(
            handle.last_validated_generation.load(Ordering::Acquire),
            topology_generation.load(Ordering::Acquire)
        );
    }

    #[test]
    fn failed_revalidation_closes_the_handle() {
        let topology_generation = Arc::new(AtomicU64::new(4));
        let handle = test_handle(Arc::clone(&topology_generation), 4, false);

        // Still on the last-validated generation, so no revalidation happens yet.
        assert!(handle.is_open());
        topology_generation.fetch_add(1, Ordering::AcqRel);
        // Now the generation differs, forcing revalidation, which fails.
        assert!(!handle.is_open());
        assert!(!handle.open.load(Ordering::Acquire));
    }

    #[test]
    fn closed_handle_never_revalidates() {
        let topology_generation = Arc::new(AtomicU64::new(4));
        let handle = test_handle(Arc::clone(&topology_generation), 4, true);
        handle.open.store(false, Ordering::Release);
        assert!(!handle.is_open());
    }

    #[test]
    fn pairs_same_named_virtual_input_and_output() {
        let entries = vec![
            (
                "Launchpad X Sim".to_string(),
                "src-ref:11".to_string(),
                MidiPortDirection::Input,
            ),
            (
                "Launchpad X Sim".to_string(),
                "dst-ref:22".to_string(),
                MidiPortDirection::Output,
            ),
            (
                "Lonely Input".to_string(),
                "src-ref:33".to_string(),
                MidiPortDirection::Input,
            ),
            (
                "Lonely Output".to_string(),
                "dst-ref:44".to_string(),
                MidiPortDirection::Output,
            ),
        ];

        let devices = pair_virtual_endpoints(entries);
        assert_eq!(devices.len(), 3);

        let paired = devices
            .iter()
            .find(|d| d.name == "Launchpad X Sim")
            .expect("paired device present");
        assert_eq!(
            paired.id,
            format!("vdevice:{}", hex_lower(b"Launchpad X Sim"))
        );
        assert_eq!(paired.transport, MidiTransportType::Virtual);
        assert_eq!(paired.ports.len(), 2);
        assert!(paired.ports.iter().any(
            |p| p.direction == MidiPortDirection::Input && p.id == "src-ref:11" && p.port_number == 0
        ));
        assert!(paired.ports.iter().any(
            |p| p.direction == MidiPortDirection::Output
                && p.id == "dst-ref:22"
                && p.port_number == 0
        ));

        let lonely_in = devices
            .iter()
            .find(|d| d.name == "Lonely Input")
            .expect("lonely input device present");
        assert_eq!(lonely_in.id, "vdevice_in:src-ref:33");
        assert_eq!(lonely_in.ports.len(), 1);
        assert_eq!(lonely_in.ports[0].direction, MidiPortDirection::Input);

        let lonely_out = devices
            .iter()
            .find(|d| d.name == "Lonely Output")
            .expect("lonely output device present");
        assert_eq!(lonely_out.id, "vdevice_out:dst-ref:44");
        assert_eq!(lonely_out.ports.len(), 1);
        assert_eq!(lonely_out.ports[0].direction, MidiPortDirection::Output);
    }

    #[test]
    fn pairs_only_one_input_output_pair_when_duplicates_share_a_name() {
        let entries = vec![
            (
                "Dup".to_string(),
                "src-ref:1".to_string(),
                MidiPortDirection::Input,
            ),
            (
                "Dup".to_string(),
                "src-ref:2".to_string(),
                MidiPortDirection::Input,
            ),
            (
                "Dup".to_string(),
                "dst-ref:3".to_string(),
                MidiPortDirection::Output,
            ),
        ];

        let devices = pair_virtual_endpoints(entries);
        // One paired bidirectional device plus one leftover lone input.
        assert_eq!(devices.len(), 2);
        let paired = devices
            .iter()
            .find(|d| d.id.starts_with("vdevice:"))
            .expect("paired device present");
        assert_eq!(paired.ports.len(), 2);
        let lone = devices
            .iter()
            .find(|d| d.id.starts_with("vdevice_in:"))
            .expect("lone input device present");
        assert_eq!(lone.ports.len(), 1);
    }

    /// Integration test exercising the real CoreMIDI stack end-to-end:
    /// creates a paired virtual device (mirroring what `examples/launchpad_sim.rs`
    /// does), verifies device-change notifications reach `CoreMidiBackend`
    /// (which only works if the dedicated run-loop thread is actually
    /// running its run loop), discovers it as one `vdevice:` device, opens
    /// both directions, exchanges a device-inquiry request/response, and
    /// finally verifies the handles close once the virtual endpoints go away.
    #[test]
    fn coremidi_backend_handles_virtual_device_lifecycle_and_notifications() {
        const DEVICE_NAME: &str = "Amethyst Test LP";
        const INQUIRY: [u8; 6] = [0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7];
        const RESPONSE: [u8; 17] = [
            0xF0, 0x7E, 0x00, 0x06, 0x02, 0x00, 0x20, 0x29, 0x03, 0x01, 0x00, 0x00, 0x00, 0x03,
            0x05, 0x02, 0xF7,
        ];

        // The backend must exist (and be registered for notifications)
        // before we create the virtual endpoints, otherwise there is nothing
        // to prove the run-loop thread fix actually works.
        let backend = CoreMidiBackend::new().expect("failed to create CoreMidiBackend");

        // A second, independent client that just owns the virtual endpoints
        // and answers the device inquiry, mirroring `examples/launchpad_sim.rs`.
        // CoreMIDI dispatches data callbacks on its own I/O thread, so this
        // client doesn't need a run loop of its own.
        let sim_client =
            coremidi::Client::new("amethyst-test-sim-client").expect("failed to create sim client");
        let virtual_source = Arc::new(
            sim_client
                .virtual_source(DEVICE_NAME)
                .expect("failed to create virtual source"),
        );
        let responder_source = Arc::clone(&virtual_source);
        let virtual_destination = sim_client
            .virtual_destination(DEVICE_NAME, move |packet_list| {
                for packet in packet_list.iter() {
                    let bytes = packet.data();
                    if bytes == INQUIRY {
                        let packets = coremidi::PacketBuffer::new(0, &RESPONSE);
                        let _ = responder_source.received(&packets);
                    }
                }
            })
            .expect("failed to create virtual destination");

        assert!(
            backend.wait_for_device_change(3000),
            "expected a device-change notification after creating the virtual endpoints; \
             this only passes if the dedicated CoreMIDI run-loop thread is running"
        );

        let devices = backend.discover_devices().expect("discover_devices failed");
        let paired = devices
            .iter()
            .find(|d| d.name == DEVICE_NAME && d.id.starts_with("vdevice:"))
            .expect("expected one paired vdevice: device for the virtual source+destination");
        assert_eq!(paired.transport, MidiTransportType::Virtual);
        assert_eq!(paired.ports.len(), 2);
        let input_port = paired
            .ports
            .iter()
            .find(|p| p.direction == MidiPortDirection::Input)
            .expect("paired device has an input port");
        let output_port = paired
            .ports
            .iter()
            .find(|p| p.direction == MidiPortDirection::Output)
            .expect("paired device has an output port");

        let output_handle = backend
            .open_output(&output_port.id)
            .expect("failed to open output");
        let (sender, receiver) = mpsc::sync_channel(64);
        let input_handle = backend
            .open_input(&input_port.id, sender)
            .expect("failed to open input");

        output_handle
            .send(&INQUIRY)
            .expect("failed to send device inquiry");

        let response = receiver
            .recv_timeout(Duration::from_secs(2))
            .expect("expected an inquiry response within 2 seconds");
        assert_eq!(response.data, RESPONSE.to_vec());

        drop(virtual_source);
        drop(virtual_destination);
        drop(sim_client);

        let deadline = Instant::now() + Duration::from_secs(3);
        loop {
            let still_open = input_handle.is_open() || output_handle.is_open();
            if !still_open {
                break;
            }
            assert!(
                Instant::now() < deadline,
                "handles did not close within 3s of the virtual endpoints being removed"
            );
            std::thread::sleep(Duration::from_millis(100));
        }
    }
}
