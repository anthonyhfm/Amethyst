
#[derive(uniffi::Enum, Clone, Debug, PartialEq, Eq)]
pub enum MidiEvent {
    NoteOn {
        channel: u8,
        note: u8,
        velocity: u8,
    },
    NoteOff {
        channel: u8,
        note: u8,
        velocity: u8,
    },
    ControlChange {
        channel: u8,
        controller: u8,
        value: u8,
    },
    ProgramChange {
        channel: u8,
        program: u8,
    },
    PitchBend {
        channel: u8,
        value: u16,
    },
    ChannelPressure {
        channel: u8,
        pressure: u8,
    },
    PolyPressure {
        channel: u8,
        note: u8,
        pressure: u8,
    },
    SystemExclusive {
        data: Vec<u8>,
    },
    Raw {
        data: Vec<u8>,
    },
}

#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq)]
pub struct DeviceInquiryResponse {
    pub manufacturer: Vec<u8>,
    pub family: u16,
    pub model: u16,
    pub version: Vec<u8>,
    pub raw: Vec<u8>,
}

#[uniffi::export]
pub fn parse_midi_event(data: Vec<u8>) -> MidiEvent {
    if data.is_empty() {
        return MidiEvent::Raw { data };
    }

    let status = data[0];
    let channel = status & 0x0F;

    match status & 0xF0 {
        0x80 if data.len() >= 3 => MidiEvent::NoteOff {
            channel,
            note: data[1] & 0x7F,
            velocity: data[2] & 0x7F,
        },
        0x90 if data.len() >= 3 => {
            let velocity = data[2] & 0x7F;
            if velocity == 0 {
                MidiEvent::NoteOff {
                    channel,
                    note: data[1] & 0x7F,
                    velocity: 0,
                }
            } else {
                MidiEvent::NoteOn {
                    channel,
                    note: data[1] & 0x7F,
                    velocity,
                }
            }
        }
        0xA0 if data.len() >= 3 => MidiEvent::PolyPressure {
            channel,
            note: data[1] & 0x7F,
            pressure: data[2] & 0x7F,
        },
        0xB0 if data.len() >= 3 => MidiEvent::ControlChange {
            channel,
            controller: data[1] & 0x7F,
            value: data[2] & 0x7F,
        },
        0xC0 if data.len() >= 2 => MidiEvent::ProgramChange {
            channel,
            program: data[1] & 0x7F,
        },
        0xD0 if data.len() >= 2 => MidiEvent::ChannelPressure {
            channel,
            pressure: data[1] & 0x7F,
        },
        0xE0 if data.len() >= 3 => MidiEvent::PitchBend {
            channel,
            value: ((data[2] as u16 & 0x7F) << 7) | (data[1] as u16 & 0x7F),
        },
        _ if status == 0xF0 => MidiEvent::SystemExclusive { data },
        _ => MidiEvent::Raw { data },
    }
}

#[uniffi::export]
pub fn midi_event_to_bytes(event: MidiEvent) -> Vec<u8> {
    match event {
        MidiEvent::NoteOn { channel, note, velocity } => {
            vec![0x90 | (channel & 0x0F), note & 0x7F, velocity & 0x7F]
        }
        MidiEvent::NoteOff { channel, note, velocity } => {
            vec![0x80 | (channel & 0x0F), note & 0x7F, velocity & 0x7F]
        }
        MidiEvent::ControlChange { channel, controller, value } => {
            vec![0xB0 | (channel & 0x0F), controller & 0x7F, value & 0x7F]
        }
        MidiEvent::ProgramChange { channel, program } => {
            vec![0xC0 | (channel & 0x0F), program & 0x7F]
        }
        MidiEvent::PitchBend { channel, value } => {
            let clamped = value.min(16383);
            vec![
                0xE0 | (channel & 0x0F),
                (clamped & 0x7F) as u8,
                ((clamped >> 7) & 0x7F) as u8,
            ]
        }
        MidiEvent::ChannelPressure { channel, pressure } => {
            vec![0xD0 | (channel & 0x0F), pressure & 0x7F]
        }
        MidiEvent::PolyPressure { channel, note, pressure } => {
            vec![0xA0 | (channel & 0x0F), note & 0x7F, pressure & 0x7F]
        }
        MidiEvent::SystemExclusive { data } => data,
        MidiEvent::Raw { data } => data,
    }
}

#[uniffi::export]
pub fn midi_note_on(channel: u8, note: u8, velocity: u8) -> Vec<u8> {
    vec![0x90 | (channel & 0x0F), note & 0x7F, velocity & 0x7F]
}

#[uniffi::export]
pub fn midi_note_off(channel: u8, note: u8, velocity: u8) -> Vec<u8> {
    vec![0x80 | (channel & 0x0F), note & 0x7F, velocity & 0x7F]
}

#[uniffi::export]
pub fn midi_control_change(channel: u8, controller: u8, value: u8) -> Vec<u8> {
    vec![0xB0 | (channel & 0x0F), controller & 0x7F, value & 0x7F]
}

#[uniffi::export]
pub fn midi_program_change(channel: u8, program: u8) -> Vec<u8> {
    vec![0xC0 | (channel & 0x0F), program & 0x7F]
}

#[uniffi::export]
pub fn midi_pitch_bend(channel: u8, value: u16) -> Vec<u8> {
    let clamped = value.min(16383);
    vec![
        0xE0 | (channel & 0x0F),
        (clamped & 0x7F) as u8,
        ((clamped >> 7) & 0x7F) as u8,
    ]
}

#[uniffi::export]
pub fn midi_sysex(data: Vec<u8>) -> Vec<u8> {
    let mut result = Vec::with_capacity(data.len() + 2);
    if data.first() != Some(&0xF0) {
        result.push(0xF0);
    }
    result.extend_from_slice(&data);
    if data.last() != Some(&0xF7) {
        result.push(0xF7);
    }
    result
}

#[uniffi::export]
pub fn midi_device_inquiry() -> Vec<u8> {
    vec![0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7]
}

#[uniffi::export]
pub fn parse_device_inquiry_response(data: Vec<u8>) -> Option<DeviceInquiryResponse> {
    // Expected inquiry response: F0 7E <device_id> 06 02 <manufacturer_id(s)> <family_id> <model_number> <version> F7
    // Minimum length is 15 bytes if manufacturer ID is 3 bytes, or 13 if 1 byte.
    if data.len() < 13 || data[0] != 0xF0 || data[1] != 0x7E || data[3] != 0x06 || data[4] != 0x02 {
        return None;
    }

    // Manufacturer ID starts at index 5. It can be 1 byte (not 0x00) or 3 bytes (starts with 0x00).
    let manufacturer_len = if data[5] == 0x00 { 3 } else { 1 };
    if data.len() < 10 + manufacturer_len {
        return None;
    }

    let manufacturer = data[5..5 + manufacturer_len].to_vec();
    let family_idx = 5 + manufacturer_len;
    let family = (data[family_idx] as u16) | ((data[family_idx + 1] as u16) << 8);
    let model = (data[family_idx + 2] as u16) | ((data[family_idx + 3] as u16) << 8);
    
    let version_start = family_idx + 4;
    let version_end = data.len() - 1; // excluding F7
    let version = data[version_start..version_end].to_vec();

    Some(DeviceInquiryResponse {
        manufacturer,
        family,
        model,
        version,
        raw: data.clone(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_note_on() {
        let event = parse_midi_event(vec![0x90, 60, 127]);
        assert!(matches!(
            event,
            MidiEvent::NoteOn {
                channel: 0,
                note: 60,
                velocity: 127
            }
        ));
    }

    #[test]
    fn test_note_on_velocity_zero_is_note_off() {
        let event = parse_midi_event(vec![0x90, 60, 0]);
        assert!(matches!(
            event,
            MidiEvent::NoteOff {
                channel: 0,
                note: 60,
                velocity: 0
            }
        ));
    }

    #[test]
    fn test_parse_sysex() {
        let event = parse_midi_event(vec![0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7]);
        assert!(matches!(event, MidiEvent::SystemExclusive { .. }));
    }

    #[test]
    fn test_roundtrip_note_on() {
        let bytes = midi_event_to_bytes(MidiEvent::NoteOn {
            channel: 5,
            note: 64,
            velocity: 100,
        });
        let event = parse_midi_event(bytes);
        assert!(matches!(
            event,
            MidiEvent::NoteOn {
                channel: 5,
                note: 64,
                velocity: 100
            }
        ));
    }

    #[test]
    fn test_builder_functions() {
        assert_eq!(midi_note_on(0, 60, 127), vec![0x90, 60, 127]);
        assert_eq!(midi_note_off(0, 60, 64), vec![0x80, 60, 64]);
        assert_eq!(midi_control_change(0, 7, 100), vec![0xB0, 7, 100]);
        assert_eq!(midi_program_change(0, 42), vec![0xC0, 42]);
        assert_eq!(midi_device_inquiry(), vec![0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7]);
    }

    #[test]
    fn test_pitch_bend_center() {
        let bytes = midi_pitch_bend(0, 8192);
        assert_eq!(bytes, vec![0xE0, 0x00, 0x40]);
    }

    #[test]
    fn test_sysex_wrapping() {
        assert_eq!(midi_sysex(vec![0x7E, 0x7F]), vec![0xF0, 0x7E, 0x7F, 0xF7]);
        assert_eq!(midi_sysex(vec![0xF0, 0x7E, 0xF7]), vec![0xF0, 0x7E, 0xF7]);
    }
}
