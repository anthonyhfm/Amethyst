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
    let mut decoder = symphonia::default::get_codecs()
        .make(&track.codec_params, &DecoderOptions::default())
        .map_err(|error| error.to_string())?;
    let mut samples = Vec::new();
    let mut sample_rate = None;
    let mut channels = None;

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
        let decoded_sample_rate = decoded.spec().rate;
        let decoded_channels = decoded.spec().channels.count() as u32;
        if decoded_sample_rate == 0 || decoded_channels == 0 {
            return Err("Decoder produced an invalid PCM format".to_owned());
        }
        match (sample_rate, channels) {
            (Some(rate), Some(channel_count))
                if rate != decoded_sample_rate || channel_count != decoded_channels =>
            {
                return Err(format!(
                    "Decoded PCM format changed from {rate} Hz/{channel_count} channels to \
                     {decoded_sample_rate} Hz/{decoded_channels} channels"
                ));
            }
            (None, None) => {
                sample_rate = Some(decoded_sample_rate);
                channels = Some(decoded_channels);
            }
            _ => {}
        }
        let mut buffer = SampleBuffer::<f32>::new(decoded.capacity() as u64, *decoded.spec());
        buffer.copy_interleaved_ref(decoded);
        samples.extend_from_slice(buffer.samples());
    }

    Ok(EchoAudioBuffer {
        samples,
        sample_rate: sample_rate.ok_or_else(|| "No audio frames decoded".to_owned())?,
        channels: channels.ok_or_else(|| "No audio frames decoded".to_owned())?,
    })
}

#[cfg(test)]
mod tests {
    use super::decode;

    #[test]
    fn pcm16_wav_decode_preserves_samples_and_format() {
        let source = [0_i16, 16_384, -16_384, 32_767, -32_768];
        let decoded = decode(pcm16_mono_wav(48_000, &source), "quality-test.wav")
            .expect("PCM WAV should decode");

        assert_eq!(decoded.sample_rate, 48_000);
        assert_eq!(decoded.channels, 1);
        assert_eq!(decoded.samples.len(), source.len());
        for (actual, expected) in decoded.samples.iter().zip(source) {
            let expected = expected as f32 / 32_768.0;
            assert!(
                (actual - expected).abs() <= 1.0 / 32_768.0,
                "decoded {actual}, expected {expected}"
            );
        }
    }

    fn pcm16_mono_wav(sample_rate: u32, samples: &[i16]) -> Vec<u8> {
        let data_size = (samples.len() * std::mem::size_of::<i16>()) as u32;
        let mut bytes = Vec::with_capacity(44 + data_size as usize);
        bytes.extend_from_slice(b"RIFF");
        bytes.extend_from_slice(&(36 + data_size).to_le_bytes());
        bytes.extend_from_slice(b"WAVEfmt ");
        bytes.extend_from_slice(&16_u32.to_le_bytes());
        bytes.extend_from_slice(&1_u16.to_le_bytes());
        bytes.extend_from_slice(&1_u16.to_le_bytes());
        bytes.extend_from_slice(&sample_rate.to_le_bytes());
        bytes.extend_from_slice(&(sample_rate * 2).to_le_bytes());
        bytes.extend_from_slice(&2_u16.to_le_bytes());
        bytes.extend_from_slice(&16_u16.to_le_bytes());
        bytes.extend_from_slice(b"data");
        bytes.extend_from_slice(&data_size.to_le_bytes());
        for sample in samples {
            bytes.extend_from_slice(&sample.to_le_bytes());
        }
        bytes
    }
}
