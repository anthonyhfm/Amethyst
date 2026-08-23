use crate::midi::backend::{BackendPortHandle, MidiBackend, monotonic_micros};
use crate::midi::error::MidiError;
use crate::midi::grouping::sort_ports;
use crate::midi::types::*;
use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex, Weak, mpsc};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};
use windows::Devices::Enumeration::{DeviceInformation, DeviceInformationUpdate, DeviceWatcher};
use windows::Devices::Midi::{MidiInPort, MidiMessageReceivedEventArgs, MidiOutPort};
use windows::Foundation::{
    AsyncStatus, EventRegistrationToken, IAsyncOperation, IPropertyValue, TypedEventHandler,
};
use windows::Storage::Streams::{DataReader, DataWriter};
use windows::Win32::Devices::Properties::{
    DEVPKEY_Device_ContainerId, DEVPKEY_Device_InstanceId, DEVPROP_TYPE_GUID, DEVPROP_TYPE_STRING,
    DEVPROPKEY, DEVPROPTYPE,
};
use windows::Win32::Foundation::RO_E_CLOSED;
use windows::Win32::System::WinRT::{RO_INIT_MULTITHREADED, RoInitialize, RoUninitialize};
use windows::core::{GUID, HSTRING, IInspectable, Interface, PCWSTR, RuntimeType};

const BACKEND_PREFIX: &str = "winrt1";
const CONTAINER_ID_PROPERTY: &str = "System.Devices.ContainerId";
const FRIENDLY_NAME_PROPERTY: &str = "System.ItemNameDisplay";
const MANUFACTURER_PROPERTY: &str = "System.Devices.Manufacturer";
const MODEL_PROPERTY: &str = "System.Devices.ModelName";
const WINRT_ASYNC_TIMEOUT: Duration = Duration::from_secs(5);
const WINRT_WORKER_REPLY_TIMEOUT: Duration = Duration::from_secs(10);

#[link(name = "cfgmgr32")]
unsafe extern "system" {
    fn CM_Get_Device_Interface_PropertyW(
        device_interface: PCWSTR,
        property_key: *const DEVPROPKEY,
        property_type: *mut DEVPROPTYPE,
        property_buffer: *mut u8,
        property_buffer_size: *mut u32,
        flags: u32,
    ) -> u32;

    fn CM_Locate_DevNodeW(device_instance: *mut u32, device_id: PCWSTR, flags: u32) -> u32;

    fn CM_Get_DevNode_PropertyW(
        device_instance: u32,
        property_key: *const DEVPROPKEY,
        property_type: *mut DEVPROPTYPE,
        property_buffer: *mut u8,
        property_buffer_size: *mut u32,
        flags: u32,
    ) -> u32;
}

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

#[derive(Clone, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
struct PortKey {
    direction: u8,
    device_id: String,
}

impl PortKey {
    fn new(direction: MidiPortDirection, device_id: &str) -> Self {
        Self {
            direction: match direction {
                MidiPortDirection::Input => 0,
                MidiPortDirection::Output => 1,
            },
            device_id: device_id.to_string(),
        }
    }
}

struct ConnectionState {
    open: AtomicBool,
    input_sender: Mutex<Option<mpsc::SyncSender<MidiMessage>>>,
}

impl ConnectionState {
    fn input(sender: mpsc::SyncSender<MidiMessage>) -> Self {
        Self {
            open: AtomicBool::new(true),
            input_sender: Mutex::new(Some(sender)),
        }
    }

    fn output() -> Self {
        Self {
            open: AtomicBool::new(true),
            input_sender: Mutex::new(None),
        }
    }

    fn is_open(&self) -> bool {
        self.open.load(Ordering::Acquire)
    }

    fn invalidate(&self) -> bool {
        let was_open = self.open.swap(false, Ordering::AcqRel);
        // Dropping the only sender wakes a blocked receiver.
        self.input_sender.lock().unwrap().take();
        was_open
    }

    fn send_input(&self, message: MidiMessage) -> Result<(), ()> {
        if !self.is_open() {
            return Err(());
        }

        let result = {
            let sender = self.input_sender.lock().unwrap();
            match sender.as_ref().ok_or(())?.try_send(message) {
                Ok(()) => Ok(()),
                Err(mpsc::TrySendError::Full(_)) | Err(mpsc::TrySendError::Disconnected(_)) => {
                    Err(())
                }
            }
        };
        if result.is_err() {
            self.invalidate();
        }
        result
    }
}

#[derive(Default)]
struct ConnectionRegistry {
    connections: Mutex<HashMap<PortKey, Vec<Weak<ConnectionState>>>>,
}

impl ConnectionRegistry {
    fn register(&self, key: PortKey, state: &Arc<ConnectionState>) {
        let mut connections = self.connections.lock().unwrap();
        let entries = connections.entry(key).or_default();
        entries.retain(|entry| entry.upgrade().is_some_and(|state| state.is_open()));
        entries.push(Arc::downgrade(state));
    }

    fn invalidate(&self, key: &PortKey) {
        let states = self.connections.lock().unwrap().remove(key);
        if let Some(states) = states {
            for state in states.into_iter().filter_map(|state| state.upgrade()) {
                state.invalidate();
            }
        }
    }

    fn invalidate_missing(&self, live_ports: &BTreeSet<PortKey>) {
        let stale_keys = {
            let connections = self.connections.lock().unwrap();
            connections
                .keys()
                .filter(|key| !live_ports.contains(*key))
                .cloned()
                .collect::<Vec<_>>()
        };

        for key in stale_keys {
            self.invalidate(&key);
        }
    }
}

enum WorkerCommand {
    Discover {
        reply: mpsc::Sender<Result<Vec<WinRtPort>, MidiError>>,
    },
    OpenInput {
        port_id: String,
        device_id: String,
        state: Arc<ConnectionState>,
        reply: mpsc::Sender<Result<u64, MidiError>>,
    },
    OpenOutput {
        port_id: String,
        device_id: String,
        state: Arc<ConnectionState>,
        reply: mpsc::Sender<Result<u64, MidiError>>,
    },
    SendOutput {
        handle_id: u64,
        data: Vec<u8>,
        reply: mpsc::Sender<Result<(), MidiError>>,
    },
    Close {
        handle_id: u64,
        reply: mpsc::Sender<Result<(), MidiError>>,
    },
    InvalidateEndpoint(PortKey),
    Shutdown,
}

struct WinRtWorker {
    commands: mpsc::Sender<WorkerCommand>,
    thread: Mutex<Option<JoinHandle<()>>>,
}

impl WinRtWorker {
    fn start(
        device_change_sender: mpsc::Sender<()>,
        connections: Arc<ConnectionRegistry>,
    ) -> Result<(Arc<Self>, Vec<WinRtPort>), MidiError> {
        let (commands, command_receiver) = mpsc::channel();
        let worker_commands = commands.clone();
        let (startup_sender, startup_receiver) = mpsc::channel();

        let thread = thread::Builder::new()
            .name("amethyst-winrt-midi".into())
            .spawn(move || {
                let _apartment = match WinRtApartment::initialize_mta() {
                    Ok(apartment) => apartment,
                    Err(error) => {
                        let _ = startup_sender.send(Err(error));
                        return;
                    }
                };

                match WorkerRuntime::new(device_change_sender, connections, worker_commands) {
                    Ok((mut runtime, initial_ports)) => {
                        if startup_sender.send(Ok(initial_ports)).is_ok() {
                            runtime.run(command_receiver);
                        }
                        // Release WinRT objects before their apartment.
                        runtime.shutdown();
                        drop(runtime);
                    }
                    Err(error) => {
                        let _ = startup_sender.send(Err(error));
                    }
                }
            })
            .map_err(|error| MidiError::BackendError {
                reason: format!("Could not start WinRT MIDI worker: {error}"),
            })?;

        let worker = Arc::new(Self {
            commands,
            thread: Mutex::new(Some(thread)),
        });
        let initial_ports = startup_receiver
            .recv_timeout(WINRT_WORKER_REPLY_TIMEOUT)
            .map_err(|error| MidiError::BackendError {
                reason: format!("WinRT MIDI worker did not initialize: {error}"),
            })??;
        Ok((worker, initial_ports))
    }

    fn discover(&self) -> Result<Vec<WinRtPort>, MidiError> {
        let (reply, receiver) = mpsc::channel();
        self.send(WorkerCommand::Discover { reply })?;
        receive_worker_reply(receiver)
    }

    fn open_input(
        &self,
        port_id: &str,
        device_id: &str,
        state: Arc<ConnectionState>,
    ) -> Result<u64, MidiError> {
        let (reply, receiver) = mpsc::channel();
        self.send(WorkerCommand::OpenInput {
            port_id: port_id.to_string(),
            device_id: device_id.to_string(),
            state,
            reply,
        })?;
        receive_worker_reply(receiver)
    }

    fn open_output(
        &self,
        port_id: &str,
        device_id: &str,
        state: Arc<ConnectionState>,
    ) -> Result<u64, MidiError> {
        let (reply, receiver) = mpsc::channel();
        self.send(WorkerCommand::OpenOutput {
            port_id: port_id.to_string(),
            device_id: device_id.to_string(),
            state,
            reply,
        })?;
        receive_worker_reply(receiver)
    }

    fn send_output(&self, handle_id: u64, data: &[u8]) -> Result<(), MidiError> {
        let (reply, receiver) = mpsc::channel();
        self.send(WorkerCommand::SendOutput {
            handle_id,
            data: data.to_vec(),
            reply,
        })?;
        receive_worker_reply(receiver)
    }

    fn close(&self, handle_id: u64) -> Result<(), MidiError> {
        let (reply, receiver) = mpsc::channel();
        self.send(WorkerCommand::Close { handle_id, reply })?;
        receive_worker_reply(receiver)
    }

    fn invalidate_endpoint(&self, key: PortKey) {
        let _ = self.send(WorkerCommand::InvalidateEndpoint(key));
    }

    fn send(&self, command: WorkerCommand) -> Result<(), MidiError> {
        self.commands
            .send(command)
            .map_err(|_| MidiError::BackendError {
                reason: "WinRT MIDI worker is not running".into(),
            })
    }
}

impl Drop for WinRtWorker {
    fn drop(&mut self) {
        let _ = self.commands.send(WorkerCommand::Shutdown);
        if let Some(thread) = self.thread.lock().unwrap().take() {
            let _ = thread.join();
        }
    }
}

fn receive_worker_reply<T>(receiver: mpsc::Receiver<Result<T, MidiError>>) -> Result<T, MidiError> {
    receiver
        .recv_timeout(WINRT_WORKER_REPLY_TIMEOUT)
        .map_err(|error| MidiError::BackendError {
            reason: format!("WinRT MIDI worker did not complete the request: {error}"),
        })?
}

enum WorkerOpenPort {
    Input {
        key: PortKey,
        port: MidiInPort,
        event_token: EventRegistrationToken,
        state: Arc<ConnectionState>,
    },
    Output {
        key: PortKey,
        port: windows::Devices::Midi::IMidiOutPort,
        state: Arc<ConnectionState>,
    },
}

impl WorkerOpenPort {
    fn key(&self) -> &PortKey {
        match self {
            Self::Input { key, .. } | Self::Output { key, .. } => key,
        }
    }

    fn close(self) -> Result<(), MidiError> {
        match self {
            Self::Input {
                port,
                event_token,
                state,
                ..
            } => {
                state.invalidate();
                let _ = port.RemoveMessageReceived(event_token);
                port.Close().map_err(to_connection_error)
            }
            Self::Output { port, state, .. } => {
                state.invalidate();
                port.Close().map_err(to_connection_error)
            }
        }
    }
}

struct WorkerRuntime {
    watchers: Vec<WinRtDeviceWatcher>,
    open_ports: HashMap<u64, WorkerOpenPort>,
    next_handle_id: u64,
}

impl WorkerRuntime {
    fn new(
        device_change_sender: mpsc::Sender<()>,
        connections: Arc<ConnectionRegistry>,
        worker_commands: mpsc::Sender<WorkerCommand>,
    ) -> Result<(Self, Vec<WinRtPort>), MidiError> {
        let initial_ports = discover_all_ports()?;
        let mut watchers = Vec::new();

        for direction in [MidiPortDirection::Input, MidiPortDirection::Output] {
            let initial_ids = initial_ports
                .iter()
                .filter(|port| port.direction == direction)
                .map(|port| port.device_id.clone())
                .collect();
            watchers.push(WinRtDeviceWatcher::start(
                direction,
                device_selector(direction)?,
                initial_ids,
                device_change_sender.clone(),
                connections.clone(),
                worker_commands.clone(),
            )?);
        }

        Ok((
            Self {
                watchers,
                open_ports: HashMap::new(),
                next_handle_id: 1,
            },
            initial_ports,
        ))
    }

    fn run(&mut self, commands: mpsc::Receiver<WorkerCommand>) {
        while let Ok(command) = commands.recv() {
            match command {
                WorkerCommand::Discover { reply } => {
                    let _ = reply.send(discover_all_ports());
                }
                WorkerCommand::OpenInput {
                    port_id,
                    device_id,
                    state,
                    reply,
                } => {
                    let result = self.open_input(&port_id, &device_id, state);
                    self.reply_with_open_handle(reply, result);
                }
                WorkerCommand::OpenOutput {
                    port_id,
                    device_id,
                    state,
                    reply,
                } => {
                    let result = self.open_output(&port_id, &device_id, state);
                    self.reply_with_open_handle(reply, result);
                }
                WorkerCommand::SendOutput {
                    handle_id,
                    data,
                    reply,
                } => {
                    let _ = reply.send(self.send_output(handle_id, &data));
                }
                WorkerCommand::Close { handle_id, reply } => {
                    let _ = reply.send(self.close_port(handle_id));
                }
                WorkerCommand::InvalidateEndpoint(key) => self.invalidate_endpoint(&key),
                WorkerCommand::Shutdown => break,
            }
        }
    }

    fn reply_with_open_handle(
        &mut self,
        reply: mpsc::Sender<Result<u64, MidiError>>,
        result: Result<u64, MidiError>,
    ) {
        match result {
            Ok(handle_id) => {
                if reply.send(Ok(handle_id)).is_err() {
                    let _ = self.close_port(handle_id);
                }
            }
            Err(error) => {
                let _ = reply.send(Err(error));
            }
        }
    }

    fn open_input(
        &mut self,
        port_id: &str,
        device_id: &str,
        state: Arc<ConnectionState>,
    ) -> Result<u64, MidiError> {
        let port_timestamp_origin_us = monotonic_micros();
        let device_id_hstring = HSTRING::from(device_id);
        let operation = MidiInPort::FromIdAsync(&device_id_hstring).map_err(to_connection_error)?;
        let input_port = wait_for_async(
            &operation,
            WINRT_ASYNC_TIMEOUT,
            "opening MIDI input",
            to_connection_error,
        )?;

        let port_id_clone = port_id.to_string();
        let callback_state = state.clone();
        let callback_lock = Mutex::new(());
        let handler = TypedEventHandler::<MidiInPort, MidiMessageReceivedEventArgs>::new(
            move |_port, args: &Option<MidiMessageReceivedEventArgs>| {
                let _callback_guard = callback_lock.lock().unwrap();
                if !callback_state.is_open() {
                    return Ok(());
                }

                if let Some(args) = args {
                    match read_midi_message(args) {
                        Ok((data, relative_timestamp_us)) => {
                            if callback_state
                                .send_input(MidiMessage {
                                    data,
                                    timestamp_us: port_timestamp_origin_us
                                        .saturating_add(relative_timestamp_us),
                                    port_id: port_id_clone.clone(),
                                })
                                .is_err()
                            {
                                return Ok(());
                            }
                        }
                        Err(error) => {
                            if callback_state.invalidate() {
                                eprintln!(
                                    "WinRT MIDI input '{}' disconnected: {}",
                                    port_id_clone, error
                                );
                            }
                        }
                    }
                }
                Ok(())
            },
        );

        let event_token = match input_port
            .MessageReceived(&handler)
            .map_err(to_connection_error)
        {
            Ok(token) => token,
            Err(error) => {
                state.invalidate();
                let _ = input_port.Close();
                return Err(error);
            }
        };
        if !state.is_open() {
            let _ = input_port.RemoveMessageReceived(event_token);
            let _ = input_port.Close();
            return Err(MidiError::ConnectionFailed {
                reason: format!("MIDI input '{port_id}' disconnected while opening"),
            });
        }

        let handle_id = self.allocate_handle_id();
        self.open_ports.insert(
            handle_id,
            WorkerOpenPort::Input {
                key: PortKey::new(MidiPortDirection::Input, device_id),
                port: input_port,
                event_token,
                state,
            },
        );
        Ok(handle_id)
    }

    fn open_output(
        &mut self,
        port_id: &str,
        device_id: &str,
        state: Arc<ConnectionState>,
    ) -> Result<u64, MidiError> {
        let device_id_hstring = HSTRING::from(device_id);
        let operation =
            MidiOutPort::FromIdAsync(&device_id_hstring).map_err(to_connection_error)?;
        let output_port = wait_for_async(
            &operation,
            WINRT_ASYNC_TIMEOUT,
            "opening MIDI output",
            to_connection_error,
        )?;
        if !state.is_open() {
            let _ = output_port.Close();
            return Err(MidiError::ConnectionFailed {
                reason: format!("MIDI output '{port_id}' disconnected while opening"),
            });
        }

        let handle_id = self.allocate_handle_id();
        self.open_ports.insert(
            handle_id,
            WorkerOpenPort::Output {
                key: PortKey::new(MidiPortDirection::Output, device_id),
                port: output_port,
                state,
            },
        );
        Ok(handle_id)
    }

    fn send_output(&mut self, handle_id: u64, data: &[u8]) -> Result<(), MidiError> {
        let result = match self.open_ports.get(&handle_id) {
            Some(WorkerOpenPort::Output { port, state, .. }) if state.is_open() => {
                let writer = DataWriter::new().map_err(to_send_error)?;
                writer.WriteBytes(data).map_err(to_send_error)?;
                let buffer = writer.DetachBuffer().map_err(to_send_error)?;
                port.SendBuffer(&buffer)
            }
            Some(WorkerOpenPort::Output { .. }) | None => {
                return Err(MidiError::PortNotOpen {
                    port_id: format!("WinRT handle {handle_id}"),
                });
            }
            Some(WorkerOpenPort::Input { .. }) => {
                return Err(MidiError::SendFailed {
                    reason: "Port is not opened for output".into(),
                });
            }
        };

        if let Err(error) = result {
            // Other HRESULTs may be transient and leave the port usable.
            if error.code() == RO_E_CLOSED {
                let _ = self.close_port(handle_id);
            }
            return Err(to_send_error(error));
        }

        Ok(())
    }

    fn close_port(&mut self, handle_id: u64) -> Result<(), MidiError> {
        match self.open_ports.remove(&handle_id) {
            Some(port) => port.close(),
            None => Ok(()),
        }
    }

    fn invalidate_endpoint(&mut self, key: &PortKey) {
        let handle_ids = self
            .open_ports
            .iter()
            .filter_map(|(handle_id, port)| (port.key() == key).then_some(*handle_id))
            .collect::<Vec<_>>();
        for handle_id in handle_ids {
            let _ = self.close_port(handle_id);
        }
    }

    fn allocate_handle_id(&mut self) -> u64 {
        loop {
            let handle_id = self.next_handle_id;
            self.next_handle_id = self.next_handle_id.wrapping_add(1).max(1);
            if !self.open_ports.contains_key(&handle_id) {
                return handle_id;
            }
        }
    }

    fn shutdown(&mut self) {
        let handle_ids = self.open_ports.keys().copied().collect::<Vec<_>>();
        for handle_id in handle_ids {
            let _ = self.close_port(handle_id);
        }
        self.watchers.clear();
    }
}

pub struct WinRtBackend {
    worker: Arc<WinRtWorker>,
    device_changes: Mutex<mpsc::Receiver<()>>,
    snapshot: Mutex<BTreeSet<PortKey>>,
    connections: Arc<ConnectionRegistry>,
}

impl WinRtBackend {
    pub fn new() -> Result<Self, MidiError> {
        let connections = Arc::new(ConnectionRegistry::default());
        let (device_change_sender, device_changes) = mpsc::channel();
        let (worker, initial_ports) =
            WinRtWorker::start(device_change_sender, connections.clone())?;

        Ok(Self {
            worker,
            device_changes: Mutex::new(device_changes),
            snapshot: Mutex::new(port_snapshot(&initial_ports)),
            connections,
        })
    }

    fn reconcile_snapshot(&self, live_ports: BTreeSet<PortKey>) -> bool {
        let (changed, removed_ports) = {
            let mut snapshot = self.snapshot.lock().unwrap();
            if *snapshot == live_ports {
                (false, Vec::new())
            } else {
                let removed = snapshot
                    .difference(&live_ports)
                    .cloned()
                    .collect::<Vec<_>>();
                *snapshot = live_ports.clone();
                (true, removed)
            }
        };
        self.connections.invalidate_missing(&live_ports);
        for key in removed_ports {
            self.worker.invalidate_endpoint(key);
        }
        changed
    }
}

impl MidiBackend for WinRtBackend {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        let ports = self.worker.discover()?;
        self.reconcile_snapshot(port_snapshot(&ports));

        let mut grouped: BTreeMap<String, Vec<WinRtPort>> = BTreeMap::new();
        for port in ports {
            grouped
                .entry(port.container_id.clone())
                .or_default()
                .push(port);
        }

        let mut devices = Vec::new();
        for (container_id, mut grouped_ports) in grouped {
            grouped_ports.sort_by(|left, right| left.id.cmp(&right.id));
            let first = match grouped_ports.first() {
                Some(port) => port,
                None => continue,
            };

            let mut midi_ports = Vec::with_capacity(grouped_ports.len());
            let mut input_port_number = 0;
            let mut output_port_number = 0;
            for port in &grouped_ports {
                let port_number = match port.direction {
                    MidiPortDirection::Input => {
                        let number = input_port_number;
                        input_port_number += 1;
                        number
                    }
                    MidiPortDirection::Output => {
                        let number = output_port_number;
                        output_port_number += 1;
                        number
                    }
                };
                midi_ports.push(MidiPortInfo {
                    id: port.id.clone(),
                    name: port.name.clone(),
                    direction: port.direction,
                    port_number,
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
        let timeout = Duration::from_millis(timeout_ms);
        let receiver = self.device_changes.lock().unwrap();
        if receiver.recv_timeout(timeout).is_err() {
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
        let decoded = decode_port_id(port_id)?;
        if decoded.direction != MidiPortDirection::Input {
            return Err(MidiError::PortNotFound {
                port_id: port_id.to_string(),
            });
        }

        let key = PortKey::new(decoded.direction, &decoded.device_id);
        let connection_state = Arc::new(ConnectionState::input(sender));
        self.connections.register(key, &connection_state);
        let handle_id =
            match self
                .worker
                .open_input(port_id, &decoded.device_id, connection_state.clone())
            {
                Ok(handle_id) => handle_id,
                Err(error) => {
                    connection_state.invalidate();
                    return Err(error);
                }
            };

        Ok(Box::new(WinRtInputPortHandle {
            port_id: port_id.to_string(),
            handle_id,
            worker: self.worker.clone(),
            connection_state,
            closed: AtomicBool::new(false),
        }))
    }

    fn open_output(&self, port_id: &str) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let decoded = decode_port_id(port_id)?;
        if decoded.direction != MidiPortDirection::Output {
            return Err(MidiError::PortNotFound {
                port_id: port_id.to_string(),
            });
        }

        let key = PortKey::new(decoded.direction, &decoded.device_id);
        let connection_state = Arc::new(ConnectionState::output());
        self.connections.register(key, &connection_state);
        let handle_id =
            match self
                .worker
                .open_output(port_id, &decoded.device_id, connection_state.clone())
            {
                Ok(handle_id) => handle_id,
                Err(error) => {
                    connection_state.invalidate();
                    return Err(error);
                }
            };

        Ok(Box::new(WinRtOutputPortHandle {
            port_id: port_id.to_string(),
            handle_id,
            worker: self.worker.clone(),
            connection_state,
            closed: AtomicBool::new(false),
        }))
    }

    fn name(&self) -> &str {
        "WinRT MIDI 1.0 (Windows MIDI Services fallback)"
    }
}

struct WinRtInputPortHandle {
    port_id: String,
    handle_id: u64,
    worker: Arc<WinRtWorker>,
    connection_state: Arc<ConnectionState>,
    closed: AtomicBool,
}

impl BackendPortHandle for WinRtInputPortHandle {
    fn send(&self, _data: &[u8]) -> Result<(), MidiError> {
        Err(MidiError::SendFailed {
            reason: "Port is not opened for output".into(),
        })
    }

    fn close(&self) -> Result<(), MidiError> {
        self.connection_state.invalidate();
        if self.closed.swap(true, Ordering::AcqRel) {
            return Ok(());
        }
        self.worker.close(self.handle_id)
    }

    fn port_id(&self) -> &str {
        &self.port_id
    }

    fn is_open(&self) -> bool {
        !self.closed.load(Ordering::Acquire) && self.connection_state.is_open()
    }
}

struct WinRtOutputPortHandle {
    port_id: String,
    handle_id: u64,
    worker: Arc<WinRtWorker>,
    connection_state: Arc<ConnectionState>,
    closed: AtomicBool,
}

impl BackendPortHandle for WinRtOutputPortHandle {
    fn send(&self, data: &[u8]) -> Result<(), MidiError> {
        if !self.connection_state.is_open() {
            return Err(MidiError::PortNotOpen {
                port_id: self.port_id.clone(),
            });
        }

        self.worker.send_output(self.handle_id, data)
    }

    fn close(&self) -> Result<(), MidiError> {
        self.connection_state.invalidate();
        if self.closed.swap(true, Ordering::AcqRel) {
            return Ok(());
        }
        self.worker.close(self.handle_id)
    }

    fn port_id(&self) -> &str {
        &self.port_id
    }

    fn is_open(&self) -> bool {
        !self.closed.load(Ordering::Acquire) && self.connection_state.is_open()
    }
}

struct WatcherState {
    initial_ids: BTreeSet<String>,
    current_ids: BTreeSet<String>,
    enumeration_complete: bool,
    saw_live_change_during_bootstrap: bool,
}

struct WinRtDeviceWatcher {
    watcher: DeviceWatcher,
    added: EventRegistrationToken,
    updated: EventRegistrationToken,
    removed: EventRegistrationToken,
    enumeration_completed: EventRegistrationToken,
    stopped: EventRegistrationToken,
}

impl WinRtDeviceWatcher {
    fn start(
        direction: MidiPortDirection,
        selector: HSTRING,
        initial_ids: BTreeSet<String>,
        sender: mpsc::Sender<()>,
        connections: Arc<ConnectionRegistry>,
        worker_commands: mpsc::Sender<WorkerCommand>,
    ) -> Result<Self, MidiError> {
        let watcher =
            DeviceInformation::CreateWatcherAqsFilter(&selector).map_err(to_backend_error)?;
        let state = Arc::new(Mutex::new(WatcherState {
            initial_ids,
            current_ids: BTreeSet::new(),
            enumeration_complete: false,
            saw_live_change_during_bootstrap: false,
        }));

        let added_state = state.clone();
        let added_sender = sender.clone();
        let added_connections = connections.clone();
        let added_worker_commands = worker_commands.clone();
        let added_handler = TypedEventHandler::<DeviceWatcher, DeviceInformation>::new(
            move |_watcher, info: &Option<DeviceInformation>| {
                let Some(device_id) = info
                    .as_ref()
                    .and_then(|info| info.Id().ok())
                    .map(|id| id.to_string_lossy())
                else {
                    return Ok(());
                };

                let notify = {
                    let mut state = added_state.lock().unwrap();
                    state.current_ids.insert(device_id.clone());
                    state.enumeration_complete
                };
                if notify {
                    // Reconnects may reuse the same endpoint ID.
                    let key = PortKey::new(direction, &device_id);
                    added_connections.invalidate(&key);
                    let _ = added_worker_commands.send(WorkerCommand::InvalidateEndpoint(key));
                    let _ = added_sender.send(());
                }
                Ok(())
            },
        );
        let added = watcher.Added(&added_handler).map_err(to_backend_error)?;

        let updated_state = state.clone();
        let updated_sender = sender.clone();
        let updated_handler = TypedEventHandler::<DeviceWatcher, DeviceInformationUpdate>::new(
            move |_watcher, _info| {
                let notify = {
                    let mut state = updated_state.lock().unwrap();
                    if !state.enumeration_complete {
                        state.saw_live_change_during_bootstrap = true;
                    }
                    state.enumeration_complete
                };
                if notify {
                    let _ = updated_sender.send(());
                }
                Ok(())
            },
        );
        let updated = watcher
            .Updated(&updated_handler)
            .map_err(to_backend_error)?;

        let removed_state = state.clone();
        let removed_sender = sender.clone();
        let removed_connections = connections;
        let removed_worker_commands = worker_commands;
        let removed_handler = TypedEventHandler::<DeviceWatcher, DeviceInformationUpdate>::new(
            move |_watcher, info: &Option<DeviceInformationUpdate>| {
                let Some(device_id) = info
                    .as_ref()
                    .and_then(|info| info.Id().ok())
                    .map(|id| id.to_string_lossy())
                else {
                    return Ok(());
                };

                let key = PortKey::new(direction, &device_id);
                removed_connections.invalidate(&key);
                let _ = removed_worker_commands.send(WorkerCommand::InvalidateEndpoint(key));
                let notify = {
                    let mut state = removed_state.lock().unwrap();
                    state.current_ids.remove(&device_id);
                    if !state.enumeration_complete {
                        state.saw_live_change_during_bootstrap = true;
                    }
                    state.enumeration_complete
                };
                if notify {
                    let _ = removed_sender.send(());
                }
                Ok(())
            },
        );
        let removed = watcher
            .Removed(&removed_handler)
            .map_err(to_backend_error)?;

        let completed_state = state;
        let completed_sender = sender.clone();
        let enumeration_completed_handler =
            TypedEventHandler::<DeviceWatcher, IInspectable>::new(move |_watcher, _args| {
                let notify = {
                    let mut state = completed_state.lock().unwrap();
                    if state.enumeration_complete {
                        false
                    } else {
                        state.enumeration_complete = true;
                        state.saw_live_change_during_bootstrap
                            || state.current_ids != state.initial_ids
                    }
                };
                if notify {
                    let _ = completed_sender.send(());
                }
                Ok(())
            });
        let enumeration_completed = watcher
            .EnumerationCompleted(&enumeration_completed_handler)
            .map_err(to_backend_error)?;

        let stopped_handler =
            TypedEventHandler::<DeviceWatcher, IInspectable>::new(move |watcher, _args| {
                if let Some(watcher) = watcher {
                    let _ = watcher.Start();
                }
                let _ = sender.send(());
                Ok(())
            });
        let stopped = watcher
            .Stopped(&stopped_handler)
            .map_err(to_backend_error)?;

        watcher.Start().map_err(to_backend_error)?;

        Ok(Self {
            watcher,
            added,
            updated,
            removed,
            enumeration_completed,
            stopped,
        })
    }
}

impl Drop for WinRtDeviceWatcher {
    fn drop(&mut self) {
        let _ = self.watcher.RemoveAdded(self.added);
        let _ = self.watcher.RemoveUpdated(self.updated);
        let _ = self.watcher.RemoveRemoved(self.removed);
        let _ = self
            .watcher
            .RemoveEnumerationCompleted(self.enumeration_completed);
        let _ = self.watcher.RemoveStopped(self.stopped);
        let _ = self.watcher.Stop();
    }
}

struct WinRtApartment;

impl WinRtApartment {
    fn initialize_mta() -> Result<Self, MidiError> {
        unsafe { RoInitialize(RO_INIT_MULTITHREADED) }
            .map(|()| Self)
            .map_err(|error| MidiError::BackendError {
                reason: format!(
                    "Could not initialize the WinRT MIDI MTA: {}",
                    error.message()
                ),
            })
    }
}

impl Drop for WinRtApartment {
    fn drop(&mut self) {
        unsafe { RoUninitialize() };
    }
}

fn wait_for_async<T>(
    operation: &IAsyncOperation<T>,
    timeout: Duration,
    operation_name: &str,
    map_error: fn(windows::core::Error) -> MidiError,
) -> Result<T, MidiError>
where
    T: RuntimeType + 'static,
{
    let started = Instant::now();
    loop {
        let status = operation.Status().map_err(map_error)?;
        if status == AsyncStatus::Completed
            || status == AsyncStatus::Canceled
            || status == AsyncStatus::Error
        {
            return operation.GetResults().map_err(map_error);
        }
        if started.elapsed() >= timeout {
            let _ = operation.Cancel();
            return Err(MidiError::Timeout {
                reason: format!("Timed out while {operation_name}"),
            });
        }
        thread::sleep(Duration::from_millis(2));
    }
}

fn device_selector(direction: MidiPortDirection) -> Result<HSTRING, MidiError> {
    match direction {
        MidiPortDirection::Input => MidiInPort::GetDeviceSelector(),
        MidiPortDirection::Output => MidiOutPort::GetDeviceSelector(),
    }
    .map_err(to_backend_error)
}

fn discover_all_ports() -> Result<Vec<WinRtPort>, MidiError> {
    let mut ports = discover_ports(MidiPortDirection::Input)?;
    ports.extend(discover_ports(MidiPortDirection::Output)?);
    Ok(ports)
}

fn port_snapshot(ports: &[WinRtPort]) -> BTreeSet<PortKey> {
    ports
        .iter()
        .map(|port| PortKey::new(port.direction, &port.device_id))
        .collect()
}

fn discover_ports(direction: MidiPortDirection) -> Result<Vec<WinRtPort>, MidiError> {
    let selector = device_selector(direction)?;
    let operation =
        DeviceInformation::FindAllAsyncAqsFilter(&selector).map_err(to_backend_error)?;
    let collection = wait_for_async(
        &operation,
        WINRT_ASYNC_TIMEOUT,
        "enumerating MIDI endpoints",
        to_backend_error,
    )?;

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
        .or_else(|| device_interface_container_id(&device_id))
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

fn read_midi_message(args: &MidiMessageReceivedEventArgs) -> Result<(Vec<u8>, u64), MidiError> {
    let message = args.Message().map_err(to_connection_error)?;
    let timestamp_us = message.Timestamp().map_err(to_connection_error)?.Duration as u64 / 10;
    let buffer = message.RawData().map_err(to_connection_error)?;
    let length = buffer.Length().map_err(to_connection_error)? as usize;
    let reader = DataReader::FromBuffer(&buffer).map_err(to_connection_error)?;
    let mut bytes = vec![0; length];
    reader.ReadBytes(&mut bytes).map_err(to_connection_error)?;
    Ok((bytes, timestamp_us))
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

fn device_interface_container_id(device_id: &str) -> Option<String> {
    // The WinRT MIDI selector does not guarantee that ContainerId is included in
    // DeviceInformation::Properties unless it was requested explicitly. First
    // try the interface property, then resolve the interface to its PnP devnode;
    // ContainerId is normally stored on that devnode rather than the interface.
    interface_guid_property(device_id, &DEVPKEY_Device_ContainerId).or_else(|| {
        let instance_id = device_interface_instance_id(device_id)?;
        devnode_container_id(&instance_id)
    })
}

fn interface_guid_property(device_id: &str, property_key: &DEVPROPKEY) -> Option<String> {
    let device_id_wide = wide_string(device_id);
    let mut container_id = GUID::from_u128(0);
    let mut property_type = DEVPROPTYPE(0);
    let mut property_size = std::mem::size_of::<GUID>() as u32;
    let result = unsafe {
        CM_Get_Device_Interface_PropertyW(
            PCWSTR(device_id_wide.as_ptr()),
            property_key,
            &mut property_type,
            (&mut container_id as *mut GUID).cast::<u8>(),
            &mut property_size,
            0,
        )
    };

    (result == 0
        && property_type == DEVPROP_TYPE_GUID
        && property_size == std::mem::size_of::<GUID>() as u32)
        .then(|| format_guid(container_id))
}

fn device_interface_instance_id(device_id: &str) -> Option<String> {
    const CR_SUCCESS: u32 = 0;
    const CR_BUFFER_SMALL: u32 = 26;

    let device_id_wide = wide_string(device_id);
    let mut property_type = DEVPROPTYPE(0);
    let mut property_size = 0u32;
    let result = unsafe {
        CM_Get_Device_Interface_PropertyW(
            PCWSTR(device_id_wide.as_ptr()),
            &DEVPKEY_Device_InstanceId,
            &mut property_type,
            std::ptr::null_mut(),
            &mut property_size,
            0,
        )
    };

    if (result != CR_SUCCESS && result != CR_BUFFER_SMALL) || property_size < 2 {
        return None;
    }

    let mut buffer = vec![0u16; property_size.div_ceil(2) as usize];
    let result = unsafe {
        CM_Get_Device_Interface_PropertyW(
            PCWSTR(device_id_wide.as_ptr()),
            &DEVPKEY_Device_InstanceId,
            &mut property_type,
            buffer.as_mut_ptr().cast::<u8>(),
            &mut property_size,
            0,
        )
    };
    if result != CR_SUCCESS || property_type != DEVPROP_TYPE_STRING {
        return None;
    }

    let end = buffer
        .iter()
        .position(|character| *character == 0)
        .unwrap_or(buffer.len());
    String::from_utf16(&buffer[..end]).ok()
}

fn devnode_container_id(instance_id: &str) -> Option<String> {
    const CR_SUCCESS: u32 = 0;

    let instance_id_wide = wide_string(instance_id);
    let mut device_instance = 0u32;
    let result =
        unsafe { CM_Locate_DevNodeW(&mut device_instance, PCWSTR(instance_id_wide.as_ptr()), 0) };
    if result != CR_SUCCESS {
        return None;
    }

    let mut container_id = GUID::from_u128(0);
    let mut property_type = DEVPROPTYPE(0);
    let mut property_size = std::mem::size_of::<GUID>() as u32;
    let result = unsafe {
        CM_Get_DevNode_PropertyW(
            device_instance,
            &DEVPKEY_Device_ContainerId,
            &mut property_type,
            (&mut container_id as *mut GUID).cast::<u8>(),
            &mut property_size,
            0,
        )
    };

    (result == CR_SUCCESS
        && property_type == DEVPROP_TYPE_GUID
        && property_size == std::mem::size_of::<GUID>() as u32)
        .then(|| format_guid(container_id))
}

fn wide_string(value: &str) -> Vec<u16> {
    value.encode_utf16().chain(std::iter::once(0)).collect()
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
    let tokens = haystack
        .split(|character: char| !character.is_ascii_alphanumeric())
        .filter(|token| !token.is_empty())
        .collect::<BTreeSet<_>>();

    if tokens.contains("bluetooth") || tokens.contains("ble") {
        MidiTransportType::Bluetooth
    } else if tokens.contains("network") || tokens.contains("rtp") {
        MidiTransportType::Network
    } else if tokens.contains("loopback") || tokens.contains("virtual") {
        MidiTransportType::Virtual
    } else if tokens.contains("usb") {
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
