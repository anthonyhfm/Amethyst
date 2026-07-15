use std::io::Cursor;
use std::sync::{Arc, Mutex, mpsc};
use std::sync::atomic::{AtomicU64, Ordering};

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use symphonia::core::{
    audio::SampleBuffer,
    codecs::DecoderOptions,
    errors::Error as SymphoniaError,
    formats::FormatOptions,
    io::MediaSourceStream,
    meta::MetadataOptions,
    probe::Hint,
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

#[derive(uniffi::Record, Clone)]
pub struct EchoDeviceInfo {
    pub sample_rate: u32,
    pub channels: u32,
    pub buffer_frames: u32,
    pub available: bool,
    pub error: Option<String>,
}

#[derive(Clone)]
struct Voice {
    id: String,
    origin: String,
    buffer: EchoAudioBuffer,
    cursor: f64,
    gain: f32,
    pan: f32,
}

struct MixerState {
    voices: Vec<Voice>,
    sample_rate: u32,
    channels: usize,
    master_gain: f32,
}

impl MixerState {
    fn render(&mut self, output: &mut [f32]) {
        output.fill(0.0);
        let output_channels = self.channels;
        let frames = output.len() / output_channels;
        let rate = self.sample_rate as f64;

        for voice_index in (0..self.voices.len()).rev() {
            let voice = &mut self.voices[voice_index];
            let source_channels = voice.buffer.channels.max(1) as usize;
            let source_frames = voice.buffer.samples.len() / source_channels;
            let step = voice.buffer.sample_rate.max(1) as f64 / rate;
            let pan = voice.pan.clamp(-1.0, 1.0);
            let left_gain = voice.gain * ((1.0 - pan) * 0.5).sqrt();
            let right_gain = voice.gain * ((1.0 + pan) * 0.5).sqrt();

            for frame in 0..frames {
                let source_frame = voice.cursor as usize;
                if source_frame >= source_frames {
                    break;
                }
                let base = source_frame * source_channels;
                let mono = if source_channels == 1 {
                    voice.buffer.samples[base]
                } else {
                    (voice.buffer.samples[base] + voice.buffer.samples[base + 1]) * 0.5
                };
                let offset = frame * output_channels;
                if output_channels == 1 {
                    output[offset] += mono * voice.gain;
                } else {
                    output[offset] += mono * left_gain;
                    output[offset + 1] += mono * right_gain;
                    for channel in 2..output_channels {
                        output[offset + channel] += mono * voice.gain;
                    }
                }
                voice.cursor += step;
            }
            if voice.cursor as usize >= source_frames {
                self.voices.swap_remove(voice_index);
            }
        }
        for value in output.iter_mut() {
            *value = (*value * self.master_gain).clamp(-1.0, 1.0);
        }
    }
}

#[derive(uniffi::Object)]
pub struct EchoEngine {
    mixer: Arc<Mutex<MixerState>>,
    service: Mutex<Option<mpsc::Sender<ServiceCommand>>>,
    next_voice_id: AtomicU64,
    preferred_buffer_frames: AtomicU64,
    preferred_output_device: Mutex<Option<String>>,
    device_info: Mutex<EchoDeviceInfo>,
}

enum ServiceCommand { Shutdown }

#[uniffi::export]
impl EchoEngine {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            mixer: Arc::new(Mutex::new(MixerState { voices: Vec::new(), sample_rate: 48_000, channels: 2, master_gain: 1.0 })),
            service: Mutex::new(None),
            next_voice_id: AtomicU64::new(1),
            preferred_buffer_frames: AtomicU64::new(256),
            preferred_output_device: Mutex::new(None),
            device_info: Mutex::new(EchoDeviceInfo { sample_rate: 0, channels: 0, buffer_frames: 0, available: false, error: None }),
        })
    }

    pub fn initialize(&self) -> EchoDeviceInfo {
        if self.service.lock().expect("service mutex").is_some() {
            return self.device_info();
        }
        let (command_tx, command_rx) = mpsc::channel();
        let (result_tx, result_rx) = mpsc::sync_channel(1);
        let mixer = Arc::clone(&self.mixer);
        let preferred_buffer_frames = self.preferred_buffer_frames.load(Ordering::Relaxed) as u32;
        let preferred_output_device = self.preferred_output_device.lock().expect("device mutex").clone();
        std::thread::spawn(move || {
            match build_stream(mixer, preferred_buffer_frames, preferred_output_device) {
                Ok((stream, info)) => {
                    let _ = result_tx.send(info);
                    let _stream = stream;
                    while !matches!(command_rx.recv(), Ok(ServiceCommand::Shutdown) | Err(_)) {}
                }
                Err(error) => { let _ = result_tx.send(EchoDeviceInfo { sample_rate: 0, channels: 0, buffer_frames: 0, available: false, error: Some(error) }); }
            }
        });
        let info = result_rx.recv().unwrap_or(EchoDeviceInfo { sample_rate: 0, channels: 0, buffer_frames: 0, available: false, error: Some("Echo output service terminated during startup".to_owned()) });
        if info.available { *self.service.lock().expect("service mutex") = Some(command_tx); }
        *self.device_info.lock().expect("info mutex") = info.clone();
        return info;
        /*
        let host = cpal::default_host();
        let Some(device) = host.default_output_device() else {
            return self.set_error("No default output device available");
        };
        let config = match device.default_output_config() {
            Ok(config) => config,
            Err(error) => return self.set_error(&format!("Cannot get output config: {error}")),
        };
        let sample_rate = config.sample_rate().0;
        let channels = config.channels() as usize;
        let buffer_frames = match config.buffer_size() {
            cpal::SupportedBufferSize::Range { min, max } => (*min).clamp(128, (*max).min(512)),
            cpal::SupportedBufferSize::Unknown => 0,
        };
        {
            let mut mixer = self.mixer.lock().expect("mixer mutex");
            mixer.sample_rate = sample_rate;
            mixer.channels = channels;
        }
        let mixer = Arc::clone(&self.mixer);
        let error_callback = |error| eprintln!("Echo output stream error: {error}");
        let stream_config: cpal::StreamConfig = config.clone().into();
        let stream_result = match config.sample_format() {
            cpal::SampleFormat::F32 => device.build_output_stream(&stream_config, move |output: &mut [f32], _| {
                if let Ok(mut mixer) = mixer.try_lock() { mixer.render(output); } else { output.fill(0.0); }
            }, error_callback, None),
            cpal::SampleFormat::I16 => {
                let mixer = Arc::clone(&self.mixer);
                device.build_output_stream(&stream_config, move |output: &mut [i16], _| {
                    if let Ok(mut mixer) = mixer.try_lock() {
                        let mut scratch = vec![0.0; output.len()];
                        mixer.render(&mut scratch);
                        for (dst, src) in output.iter_mut().zip(scratch) { *dst = (src * i16::MAX as f32) as i16; }
                    } else { output.fill(0); }
                }, error_callback, None)
            }
            cpal::SampleFormat::U16 => {
                let mixer = Arc::clone(&self.mixer);
                device.build_output_stream(&stream_config, move |output: &mut [u16], _| {
                    if let Ok(mut mixer) = mixer.try_lock() {
                        let mut scratch = vec![0.0; output.len()];
                        mixer.render(&mut scratch);
                        for (dst, src) in output.iter_mut().zip(scratch) { *dst = ((src * 0.5 + 0.5) * u16::MAX as f32) as u16; }
                    } else { output.fill(u16::MAX / 2); }
                }, error_callback, None)
            }
            format => return self.set_error(&format!("Unsupported output sample format: {format:?}")),
        };
        let stream = match stream_result.and_then(|stream| { stream.play()?; Ok(stream) }) {
            Ok(stream) => stream,
            Err(error) => return self.set_error(&format!("Cannot start output stream: {error}")),
        };
        *self.stream.lock().expect("stream mutex") = Some(stream);
        let info = EchoDeviceInfo { sample_rate, channels: channels as u32, buffer_frames, available: true, error: None };
        *self.device_info.lock().expect("info mutex") = info.clone();
        info
        */
    }

    pub fn device_info(&self) -> EchoDeviceInfo { self.device_info.lock().expect("info mutex").clone() }

    pub fn decode_file(&self, path: String) -> EchoDecodeResult {
        match std::fs::read(&path) {
            Ok(bytes) => self.decode_bytes(bytes, path),
            Err(error) => EchoDecodeResult { buffer: None, error: Some(format!("Cannot read audio file: {error}")) },
        }
    }

    pub fn decode_bytes(&self, bytes: Vec<u8>, name: String) -> EchoDecodeResult {
        match decode(bytes, &name) {
            Ok(buffer) => EchoDecodeResult { buffer: Some(buffer), error: None },
            Err(error) => EchoDecodeResult { buffer: None, error: Some(error) },
        }
    }

    pub fn play(&self, buffer: EchoAudioBuffer, origin: String, gain: f32, pan: f32) -> Option<String> {
        if !self.device_info().available || buffer.samples.is_empty() || buffer.channels == 0 { return None; }
        let id = format!("echo-{}", self.next_voice_id.fetch_add(1, Ordering::Relaxed));
        self.mixer.lock().expect("mixer mutex").voices.push(Voice { id: id.clone(), origin, buffer, cursor: 0.0, gain: gain.max(0.0), pan });
        Some(id)
    }

    pub fn update(&self, id: String, gain: f32, pan: f32) {
        if let Some(voice) = self.mixer.lock().expect("mixer mutex").voices.iter_mut().find(|voice| voice.id == id) {
            voice.gain = gain.max(0.0); voice.pan = pan;
        }
    }

    pub fn stop(&self, id: String) { self.mixer.lock().expect("mixer mutex").voices.retain(|voice| voice.id != id); }
    pub fn stop_by_origin(&self, origin: String) { self.mixer.lock().expect("mixer mutex").voices.retain(|voice| voice.origin != origin); }
    pub fn stop_all(&self) { self.mixer.lock().expect("mixer mutex").voices.clear(); }
    pub fn set_master_gain(&self, gain: f32) { self.mixer.lock().expect("mixer mutex").master_gain = gain.max(0.0); }
    pub fn set_preferred_buffer_frames(&self, frames: u32) {
        self.preferred_buffer_frames.store(frames.clamp(64, 2048) as u64, Ordering::Relaxed);
    }
    pub fn output_devices(&self) -> Vec<String> {
        cpal::default_host().output_devices()
            .map(|devices| devices.filter_map(|device| device.name().ok()).collect())
            .unwrap_or_default()
    }
    pub fn set_preferred_output_device(&self, name: String) {
        *self.preferred_output_device.lock().expect("device mutex") = (!name.trim().is_empty()).then_some(name);
    }
    pub fn shutdown(&self) {
        self.stop_all();
        if let Some(service) = self.service.lock().expect("service mutex").take() { let _ = service.send(ServiceCommand::Shutdown); }
    }
}

fn build_stream(mixer: Arc<Mutex<MixerState>>, preferred_buffer_frames: u32, preferred_output_device: Option<String>) -> Result<(cpal::Stream, EchoDeviceInfo), String> {
    let host = cpal::default_host();
    let device = match preferred_output_device {
        Some(name) => host.output_devices().map_err(|error| format!("Cannot enumerate output devices: {error}"))?
            .find(|device| device.name().map(|device_name| device_name == name).unwrap_or(false))
            .ok_or_else(|| format!("Configured output device is unavailable: {name}"))?,
        None => host.default_output_device().ok_or_else(|| "No default output device available".to_owned())?,
    };
    let config = device.default_output_config().map_err(|error| format!("Cannot get output config: {error}"))?;
    let sample_rate = config.sample_rate().0;
    let channels = config.channels() as usize;
    let buffer_frames = match config.buffer_size() { cpal::SupportedBufferSize::Range { min, max } => preferred_buffer_frames.clamp(*min, *max), cpal::SupportedBufferSize::Unknown => 0 };
    { let mut state = mixer.lock().expect("mixer mutex"); state.sample_rate = sample_rate; state.channels = channels; }
    let error_callback = |error| eprintln!("Echo output stream error: {error}");
    let mut stream_config: cpal::StreamConfig = config.clone().into();
    if buffer_frames > 0 { stream_config.buffer_size = cpal::BufferSize::Fixed(buffer_frames); }
    let stream = match config.sample_format() {
        cpal::SampleFormat::F32 => device.build_output_stream(&stream_config, move |output: &mut [f32], _| { if let Ok(mut state) = mixer.try_lock() { state.render(output); } else { output.fill(0.0); } }, error_callback, None),
        cpal::SampleFormat::I16 => {
            let mixer = Arc::clone(&mixer);
            device.build_output_stream(&stream_config, move |output: &mut [i16], _| { if let Ok(mut state) = mixer.try_lock() { let mut scratch = vec![0.0; output.len()]; state.render(&mut scratch); for (dst, src) in output.iter_mut().zip(scratch) { *dst = (src * i16::MAX as f32) as i16; } } else { output.fill(0); } }, error_callback, None)
        }
        cpal::SampleFormat::U16 => {
            let mixer = Arc::clone(&mixer);
            device.build_output_stream(&stream_config, move |output: &mut [u16], _| { if let Ok(mut state) = mixer.try_lock() { let mut scratch = vec![0.0; output.len()]; state.render(&mut scratch); for (dst, src) in output.iter_mut().zip(scratch) { *dst = ((src * 0.5 + 0.5) * u16::MAX as f32) as u16; } } else { output.fill(u16::MAX / 2); } }, error_callback, None)
        }
        format => return Err(format!("Unsupported output sample format: {format:?}")),
    }.map_err(|error| format!("Cannot build output stream: {error}"))?;
    stream.play().map_err(|error| format!("Cannot start output stream: {error}"))?;
    Ok((stream, EchoDeviceInfo { sample_rate, channels: channels as u32, buffer_frames, available: true, error: None }))
}

impl EchoEngine {
    fn set_error(&self, error: &str) -> EchoDeviceInfo {
        let info = EchoDeviceInfo { sample_rate: 0, channels: 0, buffer_frames: 0, available: false, error: Some(error.to_owned()) };
        *self.device_info.lock().expect("info mutex") = info.clone(); info
    }
}

fn decode(bytes: Vec<u8>, name: &str) -> Result<EchoAudioBuffer, String> {
    let mut hint = Hint::new();
    if let Some(extension) = std::path::Path::new(name).extension().and_then(|extension| extension.to_str()) { hint.with_extension(extension); }
    let mss = MediaSourceStream::new(Box::new(Cursor::new(bytes)), Default::default());
    let probed = symphonia::default::get_probe().format(&hint, mss, &FormatOptions::default(), &MetadataOptions::default()).map_err(|error| error.to_string())?;
    let mut format = probed.format;
    let track = format.default_track().ok_or_else(|| "No audio track found".to_string())?;
    let track_id = track.id;
    let sample_rate = track.codec_params.sample_rate.ok_or_else(|| "Missing sample rate".to_string())?;
    let channels = track.codec_params.channels.ok_or_else(|| "Missing channel layout".to_string())?.count() as u32;
    let mut decoder = symphonia::default::get_codecs().make(&track.codec_params, &DecoderOptions::default()).map_err(|error| error.to_string())?;
    let mut samples = Vec::new();
    loop {
        let packet = match format.next_packet() { Ok(packet) => packet, Err(SymphoniaError::IoError(_)) => break, Err(error) => return Err(error.to_string()) };
        if packet.track_id() != track_id { continue; }
        let decoded = match decoder.decode(&packet) { Ok(decoded) => decoded, Err(SymphoniaError::DecodeError(_)) => continue, Err(error) => return Err(error.to_string()) };
        let mut buffer = SampleBuffer::<f32>::new(decoded.capacity() as u64, *decoded.spec());
        buffer.copy_interleaved_ref(decoded);
        samples.extend_from_slice(buffer.samples());
    }
    Ok(EchoAudioBuffer { samples, sample_rate, channels })
}
