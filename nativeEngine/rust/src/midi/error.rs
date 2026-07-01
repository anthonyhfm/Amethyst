#[derive(uniffi::Error, Debug, Clone)]
pub enum MidiError {
    DeviceNotFound { device_id: String },
    PortNotFound { port_id: String },
    PortAlreadyOpen { port_id: String },
    PortNotOpen { port_id: String },
    ConnectionFailed { reason: String },
    SendFailed { reason: String },
    BackendError { reason: String },
    PermissionDenied { reason: String },
    Timeout { reason: String },
}

impl std::fmt::Display for MidiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            MidiError::DeviceNotFound { device_id } => write!(f, "Device not found: {}", device_id),
            MidiError::PortNotFound { port_id } => write!(f, "Port not found: {}", port_id),
            MidiError::PortAlreadyOpen { port_id } => write!(f, "Port already open: {}", port_id),
            MidiError::PortNotOpen { port_id } => write!(f, "Port not open: {}", port_id),
            MidiError::ConnectionFailed { reason } => write!(f, "Connection failed: {}", reason),
            MidiError::SendFailed { reason } => write!(f, "Send failed: {}", reason),
            MidiError::BackendError { reason } => write!(f, "Backend error: {}", reason),
            MidiError::PermissionDenied { reason } => write!(f, "Permission denied: {}", reason),
            MidiError::Timeout { reason } => write!(f, "Timeout: {}", reason),
        }
    }
}

impl std::error::Error for MidiError {}
