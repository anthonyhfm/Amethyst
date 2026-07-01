#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq, Eq)]
pub enum MidiPortDirection {
    Input,
    Output,
}

#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq, Eq)]
pub enum MidiTransportType {
    Usb,
    Bluetooth,
    Network,
    Virtual,
    Unknown,
}

#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq)]
pub struct MidiPortInfo {
    pub id: String,
    pub name: String,
    pub direction: MidiPortDirection,
    pub port_number: u32,
    pub is_available: bool,
}

#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq)]
pub struct MidiDeviceInfo {
    pub id: String,
    pub name: String,
    pub manufacturer: Option<String>,
    pub model: Option<String>,
    pub serial_number: Option<String>,
    pub usb_vendor_id: Option<u16>,
    pub usb_product_id: Option<u16>,
    pub transport: MidiTransportType,
    pub ports: Vec<MidiPortInfo>,
}

#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq)]
pub struct MidiMessage {
    pub data: Vec<u8>,
    pub timestamp_us: u64,
    pub port_id: String,
}
