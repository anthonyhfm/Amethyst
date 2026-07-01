use crate::midi::types::*;
use crate::midi::error::MidiError;
use crate::midi::backend::{create_platform_backend, MidiBackend};
use crate::midi::connection::MidiConnection;
use std::sync::{Arc, Mutex, RwLock};
use std::collections::HashMap;

#[derive(uniffi::Object)]
pub struct MidiAccess {
    backend: Box<dyn MidiBackend>,
    devices_cache: RwLock<Vec<MidiDeviceInfo>>,
    open_connections: Mutex<HashMap<String, Arc<MidiConnection>>>,
}

#[uniffi::export]
impl MidiAccess {
    #[uniffi::constructor]
    pub fn new() -> Result<Self, MidiError> {
        let backend = create_platform_backend()?;
        Ok(Self {
            backend,
            devices_cache: RwLock::new(Vec::new()),
            open_connections: Mutex::new(HashMap::new()),
        })
    }

    pub fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        let devices = self.backend.discover_devices()?;
        let mut cache = self.devices_cache.write().unwrap();
        *cache = devices.clone();
        Ok(devices)
    }

    pub fn get_cached_devices(&self) -> Vec<MidiDeviceInfo> {
        self.devices_cache.read().unwrap().clone()
    }

    pub fn wait_for_device_change(&self, timeout_ms: u64) -> bool {
        self.backend.wait_for_device_change(timeout_ms)
    }

    pub fn get_device(&self, device_id: String) -> Option<MidiDeviceInfo> {
        self.get_cached_devices()
            .into_iter()
            .find(|d| d.id == device_id)
    }

    pub fn get_port(&self, port_id: String) -> Option<MidiPortInfo> {
        self.get_cached_devices()
            .into_iter()
            .flat_map(|d| d.ports)
            .find(|p| p.id == port_id)
    }

    pub fn get_device_for_port(&self, port_id: String) -> Option<MidiDeviceInfo> {
        self.get_cached_devices()
            .into_iter()
            .find(|d| d.ports.iter().any(|p| p.id == port_id))
    }

    pub fn open_input(&self, port_id: String) -> Result<Arc<MidiConnection>, MidiError> {
        let mut conns = self.open_connections.lock().unwrap();
        if let Some(conn) = conns.get(&port_id) {
            if conn.is_open() {
                return Err(MidiError::PortAlreadyOpen { port_id });
            }
        }
        conns.remove(&port_id);

        let (sender, receiver) = std::sync::mpsc::channel();
        let handle = self.backend.open_input(&port_id, sender)?;
        
        let conn = Arc::new(MidiConnection::new_input(port_id.clone(), handle, receiver));
        conns.insert(port_id, conn.clone());
        Ok(conn)
    }

    pub fn open_output(&self, port_id: String) -> Result<Arc<MidiConnection>, MidiError> {
        let mut conns = self.open_connections.lock().unwrap();
        if let Some(conn) = conns.get(&port_id) {
            if conn.is_open() {
                return Err(MidiError::PortAlreadyOpen { port_id });
            }
        }
        conns.remove(&port_id);

        let handle = self.backend.open_output(&port_id)?;
        
        let conn = Arc::new(MidiConnection::new_output(port_id.clone(), handle));
        conns.insert(port_id, conn.clone());
        Ok(conn)
    }

    pub fn close_port(&self, port_id: String) -> Result<(), MidiError> {
        let mut conns = self.open_connections.lock().unwrap();
        if let Some(conn) = conns.remove(&port_id) {
            conn.disconnect()?;
        }
        Ok(())
    }

    pub fn close_all(&self) -> Result<(), MidiError> {
        let mut conns = self.open_connections.lock().unwrap();
        for conn in conns.values() {
            let _ = conn.disconnect();
        }
        conns.clear();
        Ok(())
    }

    pub fn backend_name(&self) -> String {
        self.backend.name().to_string()
    }
}
