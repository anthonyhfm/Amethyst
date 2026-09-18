use std::io::Cursor;
use std::sync::Arc;

use symphonia::core::{
    audio::SampleBuffer, codecs::DecoderOptions, errors::Error as SymphoniaError,
    formats::FormatOptions, io::MediaSourceStream, meta::MetadataOptions, probe::Hint,
};

/**
 * Decoded audio as interleaved signed 24-bit little-endian PCM.
 *
 * The conversion happens here rather than in Kotlin on purpose: UniFFI lowers a
 * `Vec<f32>` into a boxed `List<Float>`, which costs one heap object per sample
 * and OOMs Android on multi-minute samples. `Vec<u8>` crosses as a `ByteArray`,
 * which is also the representation `Signal.AudioSignal.rawData` wants anyway.
 */
#[derive(uniffi::Record, Clone)]
pub struct EchoAudioBuffer {
    pub pcm24: Vec<u8>,
    pub sample_rate: u32,
    pub channels: u32,
}

#[derive(uniffi::Record, Clone)]
pub struct EchoDecodeResult {
    pub buffer: Option<EchoAudioBuffer>,
    pub error: Option<String>,
}

#[derive(uniffi::Record, Clone)]
pub struct EchoAudioMetadata {
    pub duration_ms: u64,
    pub sample_rate: u32,
    pub channels: u32,
    pub total_samples: u64,
    pub bit_depth: u32,
}

#[derive(uniffi::Record, Clone)]
pub struct EchoProbeResult {
    pub metadata: Option<EchoAudioMetadata>,
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

    pub fn get_active_drag_file(&self) -> Option<String> {
        get_active_drag_file_native()
    }

    pub fn probe_file(&self, path: String) -> EchoProbeResult {
        match probe_file_internal(&path) {
            Ok(metadata) => EchoProbeResult {
                metadata: Some(metadata),
                error: None,
            },
            Err(error) => EchoProbeResult {
                metadata: None,
                error: Some(error),
            },
        }
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
    let estimated_frames = track
        .codec_params
        .n_frames
        .unwrap_or(0)
        .min(MAXIMUM_RESERVED_FRAMES);
    let estimated_channels = track
        .codec_params
        .channels
        .map(|channels| channels.count())
        .unwrap_or(2) as u64;
    let mut pcm24 =
        Vec::with_capacity((estimated_frames * estimated_channels * PCM24_BYTES as u64) as usize);
    let mut sample_rate = None;
    let mut channels = None;
    let mut sample_buffer: Option<SampleBuffer<f32>> = None;

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
        // Reuse the scratch buffer across packets; only a format/size change
        // forces a reallocation.
        let required_samples = decoded.frames() * decoded_channels as usize;
        if sample_buffer
            .as_ref()
            .is_none_or(|buffer| buffer.capacity() < required_samples)
        {
            sample_buffer = Some(SampleBuffer::<f32>::new(
                decoded.capacity() as u64,
                *decoded.spec(),
            ));
        }
        let buffer = sample_buffer
            .as_mut()
            .expect("sample buffer is allocated above");
        buffer.copy_interleaved_ref(decoded);
        append_pcm24(&mut pcm24, buffer.samples());
    }

    Ok(EchoAudioBuffer {
        pcm24,
        sample_rate: sample_rate.ok_or_else(|| "No audio frames decoded".to_owned())?,
        channels: channels.ok_or_else(|| "No audio frames decoded".to_owned())?,
    })
}

/** Appends interleaved f32 samples as signed 24-bit little-endian PCM. */
fn append_pcm24(output: &mut Vec<u8>, samples: &[f32]) {
    output.reserve(samples.len() * PCM24_BYTES);
    for sample in samples {
        let normalized = sample.clamp(-1.0, 1.0);
        let value = if normalized <= -1.0 {
            -8_388_608i32
        } else {
            (normalized * 8_388_607.0) as i32
        };
        output.push(value as u8);
        output.push((value >> 8) as u8);
        output.push((value >> 16) as u8);
    }
}

const PCM24_BYTES: usize = 3;
/** Caps the pre-allocation so a corrupt frame count cannot request gigabytes. */
const MAXIMUM_RESERVED_FRAMES: u64 = 60 * 60 * 192_000;

fn probe_wav_header(path: &str) -> Option<EchoAudioMetadata> {
    use std::io::{Read, Seek, SeekFrom};
    let mut f = std::fs::File::open(path).ok()?;
    let mut header = [0u8; 12];
    f.read_exact(&mut header).ok()?;
    if &header[0..4] != b"RIFF" || &header[8..12] != b"WAVE" {
        return None;
    }
    let mut channels = 2u32;
    let mut sample_rate = 44100u32;
    let mut bit_depth = 16u32;
    let mut data_size = 0u64;

    let mut chunk_hdr = [0u8; 8];
    while f.read_exact(&mut chunk_hdr).is_ok() {
        let chunk_id = &chunk_hdr[0..4];
        let chunk_len = u32::from_le_bytes(chunk_hdr[4..8].try_into().unwrap()) as u64;
        if chunk_id == b"fmt " && chunk_len >= 16 {
            let mut fmt = [0u8; 16];
            f.read_exact(&mut fmt).ok()?;
            channels = u16::from_le_bytes(fmt[2..4].try_into().unwrap()) as u32;
            sample_rate = u32::from_le_bytes(fmt[4..8].try_into().unwrap());
            bit_depth = u16::from_le_bytes(fmt[14..16].try_into().unwrap()) as u32;
            if chunk_len > 16 {
                f.seek(SeekFrom::Current((chunk_len - 16) as i64)).ok()?;
            }
        } else if chunk_id == b"data" {
            data_size = chunk_len;
            break;
        } else {
            f.seek(SeekFrom::Current(chunk_len as i64)).ok()?;
        }
    }
    if data_size == 0 {
        let file_len = f.metadata().map(|m| m.len()).unwrap_or(0);
        if file_len > 44 {
            data_size = file_len - 44;
        }
    }
    let bytes_per_frame = ((bit_depth.max(8) / 8) * channels.max(1)) as u64;
    let total_frames = if bytes_per_frame > 0 { data_size / bytes_per_frame } else { 0 };
    let duration_ms = if sample_rate > 0 { (total_frames * 1000) / (sample_rate as u64) } else { 0 };

    Some(EchoAudioMetadata {
        duration_ms,
        sample_rate,
        channels,
        total_samples: total_frames,
        bit_depth,
    })
}

fn probe_file_internal(path: &str) -> Result<EchoAudioMetadata, String> {
    if let Some(metadata) = probe_wav_header(path) {
        if metadata.duration_ms > 0 {
            return Ok(metadata);
        }
    }

    let file = std::fs::File::open(path).map_err(|e| format!("Cannot open file: {e}"))?;
    let file_len = file.metadata().map(|m| m.len()).unwrap_or(0);
    let mut hint = Hint::new();
    if let Some(extension) = std::path::Path::new(path)
        .extension()
        .and_then(|extension| extension.to_str())
    {
        hint.with_extension(extension);
    }
    let media = MediaSourceStream::new(Box::new(file), Default::default());
    let probed = symphonia::default::get_probe()
        .format(
            &hint,
            media,
            &FormatOptions::default(),
            &MetadataOptions::default(),
        )
        .map_err(|e| format!("Probe failed: {e}"))?;
    let format = probed.format;
    let track = format
        .default_track()
        .ok_or_else(|| "No audio track found in file".to_owned())?;

    let sample_rate = track.codec_params.sample_rate.unwrap_or(44100);
    let channels = track.codec_params.channels.map(|c| c.count() as u32).unwrap_or(2);
    let bit_depth = track.codec_params.bits_per_sample.unwrap_or(16);

    let total_frames = if let Some(n_frames) = track.codec_params.n_frames {
        n_frames
    } else {
        // Fallback for unindexed or raw formats based on file size and bitrate / format
        let bytes_per_frame = (channels * (bit_depth.max(8) / 8)).max(1) as u64;
        if file_len > 0 {
            file_len / bytes_per_frame
        } else {
            0
        }
    };

    let duration_ms = if sample_rate > 0 && total_frames > 0 {
        (total_frames * 1000) / (sample_rate as u64)
    } else {
        0
    };

    Ok(EchoAudioMetadata {
        duration_ms,
        sample_rate,
        channels,
        total_samples: total_frames,
        bit_depth,
    })
}

pub fn get_active_drag_file_native() -> Option<String> {
    crate::drag_drop::get_active_drag_file()
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
        assert_eq!(decoded.pcm24.len(), source.len() * super::PCM24_BYTES);
        for (index, expected) in source.into_iter().enumerate() {
            let offset = index * super::PCM24_BYTES;
            let actual = i32::from_le_bytes([
                0,
                decoded.pcm24[offset],
                decoded.pcm24[offset + 1],
                decoded.pcm24[offset + 2],
            ]) >> 8;
            let actual = actual as f32 / 8_388_608.0;
            let expected = expected as f32 / 32_768.0;
            assert!(
                (actual - expected).abs() <= 1.0 / 32_768.0,
                "decoded {actual}, expected {expected}"
            );
        }
    }

    #[test]
    fn test_probe_wav_file() {
        let path = "../../build/epic0-fixture-b/synthetic-loopback.wav";
        if std::path::Path::new(path).exists() {
            let meta = super::probe_file_internal(path).expect("probe should succeed");
            println!("TEST PROBE RESULT: duration_ms={}, sample_rate={}, channels={}, total_samples={}",
                meta.duration_ms, meta.sample_rate, meta.channels, meta.total_samples);
            assert!(meta.duration_ms > 0);
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
