use libflac_rs::{decode, Encoder, EncoderConfig};

const FLAC_COMPRESSION_LEVEL: u32 = 5;

#[derive(uniffi::Record, Clone)]
pub struct FlacCodecResult {
    pub data: Option<Vec<u8>>,
    pub error: Option<String>,
}

#[uniffi::export]
pub fn encode_flac_pcm(
    pcm_data: Vec<u8>,
    sample_rate: u32,
    channels: u32,
    bit_depth: u32,
) -> FlacCodecResult {
    result_to_record(encode_pcm_internal(
        &pcm_data,
        sample_rate,
        channels,
        bit_depth,
    ))
}

#[uniffi::export]
pub fn decode_flac_pcm(
    flac_data: Vec<u8>,
    expected_sample_rate: u32,
    expected_channels: u32,
    expected_bit_depth: u32,
) -> FlacCodecResult {
    result_to_record(decode_pcm_internal(
        &flac_data,
        expected_sample_rate,
        expected_channels,
        expected_bit_depth,
    ))
}

fn result_to_record(result: Result<Vec<u8>, String>) -> FlacCodecResult {
    match result {
        Ok(data) => FlacCodecResult {
            data: Some(data),
            error: None,
        },
        Err(error) => FlacCodecResult {
            data: None,
            error: Some(error),
        },
    }
}

fn validate_format(sample_rate: u32, channels: u32, bit_depth: u32) -> Result<usize, String> {
    if sample_rate == 0 || sample_rate > 655_350 {
        return Err(format!("Unsupported FLAC sample rate: {sample_rate}"));
    }
    if !(1..=8).contains(&channels) {
        return Err(format!("Unsupported FLAC channel count: {channels}"));
    }
    if !matches!(bit_depth, 8 | 16 | 24 | 32) {
        return Err(format!("Unsupported PCM bit depth: {bit_depth}"));
    }
    Ok((bit_depth / 8) as usize)
}

fn encode_pcm_internal(
    pcm_data: &[u8],
    sample_rate: u32,
    channels: u32,
    bit_depth: u32,
) -> Result<Vec<u8>, String> {
    let bytes_per_sample = validate_format(sample_rate, channels, bit_depth)?;
    let bytes_per_frame = bytes_per_sample * channels as usize;
    if pcm_data.len() % bytes_per_frame != 0 {
        return Err(format!(
            "PCM byte count {} is not aligned to a {bytes_per_frame}-byte frame",
            pcm_data.len()
        ));
    }

    let samples = pcm_bytes_to_samples(pcm_data, bit_depth);
    let encoder = Encoder::new(
        EncoderConfig::new(channels, bit_depth, sample_rate)
            .with_compression_level(FLAC_COMPRESSION_LEVEL),
    );
    Ok(encoder.encode(&samples))
}

fn decode_pcm_internal(
    flac_data: &[u8],
    expected_sample_rate: u32,
    expected_channels: u32,
    expected_bit_depth: u32,
) -> Result<Vec<u8>, String> {
    validate_format(expected_sample_rate, expected_channels, expected_bit_depth)?;
    let decoded = decode(flac_data).ok_or_else(|| "Invalid or corrupted FLAC data".to_owned())?;
    if !decoded.md5_ok {
        return Err("FLAC audio checksum mismatch".to_owned());
    }
    if decoded.sample_rate != expected_sample_rate
        || decoded.channels != expected_channels
        || decoded.bits_per_sample != expected_bit_depth
    {
        return Err(format!(
            "FLAC format mismatch: expected {expected_sample_rate} Hz/{expected_channels} ch/\
             {expected_bit_depth} bit, found {} Hz/{} ch/{} bit",
            decoded.sample_rate, decoded.channels, decoded.bits_per_sample
        ));
    }

    Ok(samples_to_pcm_bytes(
        &decoded.interleaved,
        expected_bit_depth,
    ))
}

fn pcm_bytes_to_samples(bytes: &[u8], bit_depth: u32) -> Vec<i32> {
    match bit_depth {
        8 => bytes.iter().map(|byte| (*byte as i8) as i32).collect(),
        16 => bytes
            .chunks_exact(2)
            .map(|sample| i16::from_le_bytes([sample[0], sample[1]]) as i32)
            .collect(),
        24 => bytes
            .chunks_exact(3)
            .map(|sample| {
                let value =
                    (sample[0] as i32) | ((sample[1] as i32) << 8) | ((sample[2] as i32) << 16);
                (value << 8) >> 8
            })
            .collect(),
        32 => bytes
            .chunks_exact(4)
            .map(|sample| i32::from_le_bytes([sample[0], sample[1], sample[2], sample[3]]))
            .collect(),
        _ => unreachable!("bit depth is validated before conversion"),
    }
}

fn samples_to_pcm_bytes(samples: &[i32], bit_depth: u32) -> Vec<u8> {
    let bytes_per_sample = (bit_depth / 8) as usize;
    let mut bytes = Vec::with_capacity(samples.len() * bytes_per_sample);
    for sample in samples {
        let little_endian = sample.to_le_bytes();
        bytes.extend_from_slice(&little_endian[..bytes_per_sample]);
    }
    bytes
}

#[cfg(test)]
mod tests {
    use super::{decode_pcm_internal, encode_pcm_internal};

    #[test]
    fn flac_roundtrip_preserves_pcm_bytes_for_supported_depths() {
        for bit_depth in [8_u32, 16, 24, 32] {
            let bytes_per_sample = (bit_depth / 8) as usize;
            let mut pcm = Vec::new();
            for sample in [-8_000_000_i32, -32_768, -1, 0, 1, 32_767, 8_000_000] {
                let bounded = match bit_depth {
                    8 => sample.clamp(i8::MIN as i32, i8::MAX as i32),
                    16 => sample.clamp(i16::MIN as i32, i16::MAX as i32),
                    24 => sample.clamp(-8_388_608, 8_388_607),
                    32 => sample,
                    _ => unreachable!(),
                };
                pcm.extend_from_slice(&bounded.to_le_bytes()[..bytes_per_sample]);
                pcm.extend_from_slice(&(-bounded).to_le_bytes()[..bytes_per_sample]);
            }

            let encoded = encode_pcm_internal(&pcm, 48_000, 2, bit_depth).unwrap();
            assert_eq!(&encoded[..4], b"fLaC");
            let decoded = decode_pcm_internal(&encoded, 48_000, 2, bit_depth).unwrap();
            assert_eq!(decoded, pcm, "{bit_depth}-bit PCM changed during roundtrip");
        }
    }

    #[test]
    fn flac_rejects_mismatched_metadata() {
        let pcm = vec![0_u8; 128 * 2 * 3];
        let encoded = encode_pcm_internal(&pcm, 44_100, 2, 24).unwrap();
        assert!(decode_pcm_internal(&encoded, 48_000, 2, 24).is_err());
    }
}
