//! Manual test tool for the macOS CoreMIDI backend.
//!
//! Creates a virtual MIDI device (a virtual source + a virtual destination
//! sharing one name, exactly what `CoreMidiBackend::discover_devices` pairs
//! into a single bidirectional `vdevice:` device) that behaves like a
//! Launchpad X for the purposes of device inquiry. This lets the Amethyst app
//! be exercised on a Mac that has no physical Launchpad attached.
//!
//! Usage:
//!   cargo run --example launchpad_sim -- [name] [--firmware original]
//!
//! - `name` (optional, default "Launchpad X Sim"): the shared name given to
//!   both the virtual source and virtual destination.
//! - `--firmware original`: replies with the original Launchpad X firmware's
//!   version bytes (`00 01 00 00`) instead of the default (`00 03 05 02`).
//!
//! When the destination receives a MIDI Device Inquiry
//! (`F0 7E <device id> 06 01 F7`, device id `7F` or `00`), it replies on the
//! source with the Launchpad X identity reply. Any other bytes received are
//! printed as hex. Stays running until Ctrl-C.

use coremidi::{Client, PacketBuffer};
use core_foundation::runloop::CFRunLoop;
use std::sync::Arc;

const DEFAULT_NAME: &str = "Launchpad X Sim";

/// `F0 7E <device id> 06 01 F7`, with device id `7F` (broadcast) or `00`.
fn is_device_inquiry(bytes: &[u8]) -> bool {
    bytes.len() == 6
        && bytes[0] == 0xF0
        && bytes[1] == 0x7E
        && (bytes[2] == 0x7F || bytes[2] == 0x00)
        && bytes[3] == 0x06
        && bytes[4] == 0x01
        && bytes[5] == 0xF7
}

/// Launchpad X identity reply: `F0 7E 00 06 02 <mfr id 00 20 29> <family 03 01>
/// <family member 00 00> <firmware, 4 bytes> F7`.
fn inquiry_response(firmware_original: bool) -> [u8; 17] {
    let firmware: [u8; 4] = if firmware_original {
        [0x00, 0x01, 0x00, 0x00]
    } else {
        [0x00, 0x03, 0x05, 0x02]
    };
    [
        0xF0,
        0x7E,
        0x00,
        0x06,
        0x02,
        0x00,
        0x20,
        0x29,
        0x03,
        0x01,
        0x00,
        0x00,
        firmware[0],
        firmware[1],
        firmware[2],
        firmware[3],
        0xF7,
    ]
}

fn parse_args() -> (String, bool) {
    let mut name = DEFAULT_NAME.to_string();
    let mut firmware_original = false;
    let mut name_taken = false;

    let mut args = std::env::args().skip(1);
    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--firmware" => match args.next().as_deref() {
                Some("original") => firmware_original = true,
                Some(other) => {
                    eprintln!("Unknown --firmware value '{other}', expected 'original'");
                    std::process::exit(1);
                }
                None => {
                    eprintln!("--firmware requires a value (e.g. --firmware original)");
                    std::process::exit(1);
                }
            },
            other if !name_taken => {
                name = other.to_string();
                name_taken = true;
            }
            other => {
                eprintln!("Unexpected argument: {other}");
                std::process::exit(1);
            }
        }
    }

    (name, firmware_original)
}

fn format_hex(bytes: &[u8]) -> String {
    bytes
        .iter()
        .map(|b| format!("{b:02X}"))
        .collect::<Vec<_>>()
        .join(" ")
}

fn main() {
    let (name, firmware_original) = parse_args();
    let response = inquiry_response(firmware_original);

    println!("Amethyst Launchpad simulator");
    println!("  Device name: {name}");
    println!(
        "  Firmware: {} ({})",
        if firmware_original { "original" } else { "default" },
        format_hex(&response[12..16])
    );

    let client = Client::new(&format!("{name} sim client"))
        .expect("failed to create CoreMIDI client");

    let source = Arc::new(
        client
            .virtual_source(&name)
            .expect("failed to create virtual source"),
    );
    println!("Created virtual source '{name}'");

    let responder_source = Arc::clone(&source);
    let responder_name = name.clone();
    let _destination = client
        .virtual_destination(&name, move |packet_list| {
            for packet in packet_list.iter() {
                let bytes = packet.data();
                if is_device_inquiry(bytes) {
                    println!(
                        "Received Device Inquiry ({}) -> replying as {responder_name}",
                        format_hex(bytes)
                    );
                    let packets = PacketBuffer::new(0, &response);
                    if let Err(status) = responder_source.received(&packets) {
                        eprintln!("Failed to send inquiry response: {status}");
                    }
                } else {
                    println!("Received: {}", format_hex(bytes));
                }
            }
        })
        .expect("failed to create virtual destination");
    println!("Created virtual destination '{name}'");

    println!("Ready. Waiting for MIDI messages. Press Ctrl-C to quit.");
    CFRunLoop::run_current();
}
