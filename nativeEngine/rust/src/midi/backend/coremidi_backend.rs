use crate::midi::types::*;
use crate::midi::error::MidiError;
use crate::midi::backend::{MidiBackend, BackendPortHandle};
use std::sync::mpsc;
use std::sync::OnceLock;
use core_foundation::base::TCFType;
use core_foundation::string::{CFString, CFStringRef};

#[repr(C)]
#[derive(Clone, Copy)]
struct MachTimebaseInfo {
    numer: u32,
    denom: u32,
}

unsafe extern "C" {
    fn mach_timebase_info(info: *mut MachTimebaseInfo) -> i32;
}

fn get_timebase() -> &'static MachTimebaseInfo {
    static TIMEBASE: OnceLock<MachTimebaseInfo> = OnceLock::new();
    TIMEBASE.get_or_init(|| {
        let mut info = MachTimebaseInfo { numer: 0, denom: 0 };
        unsafe { mach_timebase_info(&mut info); }
        info
    })
}

unsafe fn get_string_property(obj: coremidi_sys::MIDIObjectRef, prop_key: CFStringRef) -> Option<String> {
    let mut name_ref: CFStringRef = std::ptr::null();
    let status = unsafe { coremidi_sys::MIDIObjectGetStringProperty(obj, prop_key, &mut name_ref) };
    if status == 0 && !name_ref.is_null() {
        let cf_str = unsafe { CFString::wrap_under_create_rule(name_ref) };
        Some(cf_str.to_string())
    } else {
        None
    }
}

unsafe fn get_integer_property(obj: coremidi_sys::MIDIObjectRef, prop_key: CFStringRef) -> Option<i32> {
    let mut val = 0;
    let status = unsafe { coremidi_sys::MIDIObjectGetIntegerProperty(obj, prop_key, &mut val) };
    if status == 0 {
        Some(val)
    } else {
        None
    }
}

pub struct CoreMidiBackend {
    client: coremidi::Client,
}

impl CoreMidiBackend {
    pub fn new() -> Result<Self, MidiError> {
        let client = coremidi::Client::new("Amethyst")
            .map_err(|e| MidiError::BackendError { reason: format!("Failed to create CoreMIDI client: {:?}", e) })?;
        Ok(Self { client })
    }
}

pub struct CoreMidiPortHandle {
    port_id: String,
    input_port: Option<coremidi::InputPort>,
    source: Option<coremidi::Source>,
    output_port: Option<coremidi::OutputPort>,
    destination: Option<coremidi::Destination>,
}

impl BackendPortHandle for CoreMidiPortHandle {
    fn send(&self, data: &[u8]) -> Result<(), MidiError> {
        if let (Some(port), Some(dest)) = (&self.output_port, &self.destination) {
            let packets = coremidi::PacketBuffer::new(0, data);
            port.send(dest, &packets)
                .map_err(|e| MidiError::SendFailed { reason: format!("CoreMIDI send error: {}", e) })?;
            Ok(())
        } else {
            Err(MidiError::SendFailed { reason: "Port is not opened for output".into() })
        }
    }

    fn close(&self) -> Result<(), MidiError> {
        if let (Some(port), Some(src)) = (&self.input_port, &self.source) {
            let _ = port.disconnect_source(src);
        }
        Ok(())
    }

    fn port_id(&self) -> &str {
        &self.port_id
    }
}

impl MidiBackend for CoreMidiBackend {
    fn discover_devices(&self) -> Result<Vec<MidiDeviceInfo>, MidiError> {
        let mut devices = Vec::new();
        
        unsafe {
            let device_count = coremidi_sys::MIDIGetNumberOfDevices();
            for i in 0..device_count {
                let dev = coremidi_sys::MIDIGetDevice(i);
                if dev == 0 { continue; }
                
                let name = get_string_property(dev, coremidi_sys::kMIDIPropertyName)
                    .unwrap_or_else(|| "Unknown Device".to_string());
                let manufacturer = get_string_property(dev, coremidi_sys::kMIDIPropertyManufacturer);
                let model = get_string_property(dev, coremidi_sys::kMIDIPropertyModel);
                let unique_id = get_integer_property(dev, coremidi_sys::kMIDIPropertyUniqueID);
                
                let entity_count = coremidi_sys::MIDIDeviceGetNumberOfEntities(dev);
                let mut ports = Vec::new();
                
                for j in 0..entity_count {
                    let entity = coremidi_sys::MIDIDeviceGetEntity(dev, j);
                    if entity == 0 { continue; }
                    
                    let src_count = coremidi_sys::MIDIEntityGetNumberOfSources(entity);
                    for k in 0..src_count {
                        let endpoint = coremidi_sys::MIDIEntityGetSource(entity, k);
                        if endpoint == 0 { continue; }
                        
                        let port_id = get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                            .map(|id| id.to_string())
                            .unwrap_or_else(|| format!("src:{}", endpoint));
                        let port_name = get_string_property(endpoint, coremidi_sys::kMIDIPropertyName)
                            .unwrap_or_else(|| name.clone());
                            
                        ports.push(MidiPortInfo {
                            id: port_id,
                            name: port_name,
                            direction: MidiPortDirection::Input,
                            port_number: k as u32,
                            is_available: true,
                        });
                    }
                    
                    let dest_count = coremidi_sys::MIDIEntityGetNumberOfDestinations(entity);
                    for k in 0..dest_count {
                        let endpoint = coremidi_sys::MIDIEntityGetDestination(entity, k);
                        if endpoint == 0 { continue; }
                        
                        let port_id = get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                            .map(|id| id.to_string())
                            .unwrap_or_else(|| format!("dest:{}", endpoint));
                        let port_name = get_string_property(endpoint, coremidi_sys::kMIDIPropertyName)
                            .unwrap_or_else(|| name.clone());
                            
                        ports.push(MidiPortInfo {
                            id: port_id,
                            name: port_name,
                            direction: MidiPortDirection::Output,
                            port_number: k as u32,
                            is_available: true,
                        });
                    }
                }
                
                if !ports.is_empty() {
                    let dev_id = unique_id.map(|id| id.to_string()).unwrap_or_else(|| format!("dev:{}", dev));
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
                if endpoint == 0 { continue; }
                
                let mut entity = 0;
                coremidi_sys::MIDIEndpointGetEntity(endpoint, &mut entity);
                if entity == 0 {
                    let port_id = get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                        .map(|id| id.to_string())
                        .unwrap_or_else(|| format!("vsrc:{}", endpoint));
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
                if endpoint == 0 { continue; }
                
                let mut entity = 0;
                coremidi_sys::MIDIEndpointGetEntity(endpoint, &mut entity);
                if entity == 0 {
                    let port_id = get_integer_property(endpoint, coremidi_sys::kMIDIPropertyUniqueID)
                        .map(|id| id.to_string())
                        .unwrap_or_else(|| format!("vdest:{}", endpoint));
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

    fn open_input(
        &self,
        port_id: &str,
        sender: mpsc::Sender<MidiMessage>,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let unique_id = port_id.parse::<i32>()
            .map_err(|_| MidiError::PortNotFound { port_id: port_id.to_string() })?;
            
        let source = coremidi::Sources.into_iter()
            .find(|s| s.unique_id() == Some(unique_id as u32))
            .ok_or_else(|| MidiError::PortNotFound { port_id: port_id.to_string() })?;
            
        let port_id_clone = port_id.to_string();
        let input_port = self.client.input_port("Amethyst Input Port", move |packet_list| {
            let timebase = get_timebase();
            for packet in packet_list.iter() {
                let bytes = packet.data().to_vec();
                let raw_timestamp = packet.timestamp();
                let nanos = (raw_timestamp * timebase.numer as u64) / timebase.denom as u64;
                let timestamp_us = nanos / 1000;
                
                let msg = MidiMessage {
                    data: bytes,
                    timestamp_us,
                    port_id: port_id_clone.clone(),
                };
                let _ = sender.send(msg);
            }
        }).map_err(|e| MidiError::ConnectionFailed { reason: format!("Failed to create CoreMIDI input port: {}", e) })?;
        
        input_port.connect_source(&source)
            .map_err(|e| MidiError::ConnectionFailed { reason: format!("Failed to connect CoreMIDI source: {}", e) })?;
            
        Ok(Box::new(CoreMidiPortHandle {
            port_id: port_id.to_string(),
            input_port: Some(input_port),
            source: Some(source),
            output_port: None,
            destination: None,
        }))
    }

    fn open_output(
        &self,
        port_id: &str,
    ) -> Result<Box<dyn BackendPortHandle>, MidiError> {
        let unique_id = port_id.parse::<i32>()
            .map_err(|_| MidiError::PortNotFound { port_id: port_id.to_string() })?;
            
        let destination = coremidi::Destinations.into_iter()
            .find(|d| d.unique_id() == Some(unique_id as u32))
            .ok_or_else(|| MidiError::PortNotFound { port_id: port_id.to_string() })?;
            
        let output_port = self.client.output_port("Amethyst Output Port")
            .map_err(|e| MidiError::ConnectionFailed { reason: format!("Failed to create CoreMIDI output port: {}", e) })?;
            
        Ok(Box::new(CoreMidiPortHandle {
            port_id: port_id.to_string(),
            input_port: None,
            source: None,
            output_port: Some(output_port),
            destination: Some(destination),
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
            println!("  Device: {} (Manufacturer: {:?}, Model: {:?})", dev.name, dev.manufacturer, dev.model);
            for port in dev.ports {
                println!("    Port: {} ({:?})", port.name, port.direction);
            }
        }
    }
}
