use std::io::Cursor;
use std::sync::Arc;

use symphonia::core::{
    audio::SampleBuffer, codecs::DecoderOptions, errors::Error as SymphoniaError,
    formats::FormatOptions, io::MediaSourceStream, meta::MetadataOptions, probe::Hint,
};

#[derive(uniffi::Record, Clone)]
pub struct EchoAudioBuffer {
    pub samples: Vec<f32>,
    pub sample_rate: u32,
    pub channels: u32,
}

#[derive(uniffi::Record, Clone)]
pub struct EchoDecodeResult {
    pub buffer: Option<EchoAudioBuffer>,
    pub error: Option<String>,
}

/**
 * Platform decoder only.
 *
 * Voice scheduling, mixing, DSP and master limiting deliberately live in the
 * common Kotlin audio graph. Native platform modules only decode and output.
 */
#[derive(uniffi::Object)]
pub struct EchoEngine;

#[uniffi::export]
impl EchoEngine {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self)
    }

    pub fn decode_file(&self, path: String) -> EchoDecodeResult {
        match std::fs::read(&path) {
            Ok(bytes) => self.decode_bytes(bytes, path),
            Err(error) => EchoDecodeResult {
                buffer: None,
                error: Some(format!("Cannot read audio file: {error}")),
            },
        }
    }

    pub fn decode_bytes(&self, bytes: Vec<u8>, name: String) -> EchoDecodeResult {
        match decode(bytes, &name) {
            Ok(buffer) => EchoDecodeResult {
                buffer: Some(buffer),
                error: None,
            },
            Err(error) => EchoDecodeResult {
                buffer: None,
                error: Some(error),
            },
        }
    }

    /** Kept as a lifecycle-compatible no-op for the platform decoder. */
    pub fn shutdown(&self) {}
}

fn decode(bytes: Vec<u8>, name: &str) -> Result<EchoAudioBuffer, String> {
    let mut hint = Hint::new();
    if let Some(extension) = std::path::Path::new(name)
        .extension()
        .and_then(|extension| extension.to_str())
    {
        hint.with_extension(extension);
    }
    let media = MediaSourceStream::new(Box::new(Cursor::new(bytes)), Default::default());
    let probed = symphonia::default::get_probe()
        .format(
            &hint,
            media,
            &FormatOptions::default(),
            &MetadataOptions::default(),
        )
        .map_err(|error| error.to_string())?;
    let mut format = probed.format;
    let track = format
        .default_track()
        .ok_or_else(|| "No audio track found".to_owned())?;
    let track_id = track.id;
    let sample_rate = track
        .codec_params
        .sample_rate
        .ok_or_else(|| "Missing sample rate".to_owned())?;
    let channels = track
        .codec_params
        .channels
        .ok_or_else(|| "Missing channel layout".to_owned())?
        .count() as u32;
    let mut decoder = symphonia::default::get_codecs()
        .make(&track.codec_params, &DecoderOptions::default())
        .map_err(|error| error.to_string())?;
    let mut samples = Vec::new();

    loop {
        let packet = match format.next_packet() {
            Ok(packet) => packet,
            Err(SymphoniaError::IoError(_)) => break,
            Err(error) => return Err(error.to_string()),
        };
        if packet.track_id() != track_id {
            continue;
        }
        let decoded = match decoder.decode(&packet) {
            Ok(decoded) => decoded,
            Err(SymphoniaError::DecodeError(_)) => continue,
            Err(error) => return Err(error.to_string()),
        };
        let mut buffer = SampleBuffer::<f32>::new(decoded.capacity() as u64, *decoded.spec());
        buffer.copy_interleaved_ref(decoded);
        samples.extend_from_slice(buffer.samples());
    }

    Ok(EchoAudioBuffer {
        samples,
        sample_rate,
        channels,
    })
}
