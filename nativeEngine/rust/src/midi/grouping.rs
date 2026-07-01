use crate::midi::types::*;

pub fn deduplicate_device_names(devices: &mut Vec<MidiDeviceInfo>) {
    use std::collections::HashMap;
    let mut name_counts = HashMap::new();

    // First count occurrences
    for dev in devices.iter() {
        let name = dev.name.clone();
        *name_counts.entry(name).or_insert(0) += 1;
    }

    // Now append suffixes to duplicates
    let mut current_counts = HashMap::new();
    for dev in devices.iter_mut() {
        let name = dev.name.clone();
        let total = *name_counts.get(&name).unwrap_or(&0);
        if total > 1 {
            let count = current_counts.entry(name.clone()).or_insert(0);
            *count += 1;
            dev.name = format!("{} ({})", name, count);
        }
    }
}

pub fn generate_port_id(device_id: &str, direction: &MidiPortDirection, port_number: u32) -> String {
    let dir_str = match direction {
        MidiPortDirection::Input => "in",
        MidiPortDirection::Output => "out",
    };
    format!("{}:{}:{}", device_id, dir_str, port_number)
}

pub fn sort_ports(ports: &mut Vec<MidiPortInfo>) {
    ports.sort_by(|a, b| {
        // Inputs first, then outputs
        let dir_order_a = match a.direction {
            MidiPortDirection::Input => 0,
            MidiPortDirection::Output => 1,
        };
        let dir_order_b = match b.direction {
            MidiPortDirection::Input => 0,
            MidiPortDirection::Output => 1,
        };

        match dir_order_a.cmp(&dir_order_b) {
            std::cmp::Ordering::Equal => a.port_number.cmp(&b.port_number),
            other => other,
        }
    });
}
