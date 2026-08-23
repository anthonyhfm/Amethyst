mod ring;
#[cfg(target_os = "windows")]
mod wasapi_exclusive;

use std::sync::atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering};
use std::sync::{Arc, Mutex, mpsc};

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};

use ring::SpscFloatRing;

const DEFAULT_PERIOD_FRAMES: u32 = 128;
#[cfg(target_os = "android")]
const RING_PERIODS: usize = 2;
#[cfg(not(target_os = "android"))]
const RING_PERIODS: usize = 4;

#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq)]
pub struct PcmOutputDeviceInfo {
    pub device_id: String,
    pub device_name: String,
    pub sample_rate: u32,
    pub channels: u32,
    pub period_frames: u32,
    pub ring_capacity_frames: u32,
    pub sample_format: String,
    pub backend: String,
    pub requested_exclusive: bool,
    pub active_exclusive: bool,
    pub fallback_reason: Option<String>,
    pub available: bool,
    pub error: Option<String>,
}

impl PcmOutputDeviceInfo {
    fn unavailable(error: Option<String>) -> Self {
        Self {
            device_id: String::new(),
            device_name: String::new(),
            sample_rate: 0,
            channels: 0,
            period_frames: 0,
            ring_capacity_frames: 0,
            sample_format: String::new(),
            backend: String::new(),
            requested_exclusive: false,
            active_exclusive: false,
            fallback_reason: None,
            available: false,
            error,
        }
    }
}

#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq)]
pub struct PcmOutputDevice {
    pub id: String,
    pub display_name: String,
    pub is_default: bool,
}

#[derive(uniffi::Record, Clone, Debug, PartialEq, Eq)]
pub struct PcmOutputTelemetry {
    pub queued_frames: u64,
    pub available_write_frames: u64,
    pub written_frames: u64,
    pub consumed_frames: u64,
    pub underruns: u64,
    pub stream_errors: u64,
    pub running: bool,
}

pub(crate) struct CallbackTelemetry {
    consumed_frames: AtomicU64,
    underruns: AtomicU64,
    pub(crate) stream_errors: AtomicU64,
}

impl CallbackTelemetry {
    pub(crate) fn new() -> Self {
        Self {
            consumed_frames: AtomicU64::new(0),
            underruns: AtomicU64::new(0),
            stream_errors: AtomicU64::new(0),
        }
    }

    pub(crate) fn record_callback(
        &self,
        consumed_samples: usize,
        requested_samples: usize,
        channels: usize,
    ) {
        self.consumed_frames
            .fetch_add((consumed_samples / channels) as u64, Ordering::Relaxed);
        if consumed_samples < requested_samples {
            self.underruns.fetch_add(1, Ordering::Relaxed);
        }
    }
}

enum ServiceCommand {
    Start(mpsc::SyncSender<Result<(), String>>),
    Pause(mpsc::SyncSender<Result<(), String>>),
    Shutdown(Option<mpsc::SyncSender<()>>),
}

pub(crate) struct StartedOutput {
    pub(crate) info: PcmOutputDeviceInfo,
    pub(crate) ring: Arc<SpscFloatRing>,
    pub(crate) callback_telemetry: Arc<CallbackTelemetry>,
}

enum OutputStream {
    Cpal(cpal::Stream),
    #[cfg(target_os = "windows")]
    WasapiExclusive(wasapi_exclusive::ExclusiveOutputStream),
}

impl OutputStream {
    fn play(&self) -> Result<(), String> {
        match self {
            Self::Cpal(stream) => stream
                .play()
                .map_err(|error| format!("Cannot start output stream: {error}")),
            #[cfg(target_os = "windows")]
            Self::WasapiExclusive(stream) => stream.play(),
        }
    }

    fn pause(&self) -> Result<(), String> {
        match self {
            Self::Cpal(stream) => stream
                .pause()
                .map_err(|error| format!("Cannot pause output stream: {error}")),
            #[cfg(target_os = "windows")]
            Self::WasapiExclusive(stream) => stream.pause(),
        }
    }
}

#[derive(uniffi::Object)]
pub struct PcmOutputService {
    command_sender: Mutex<Option<mpsc::Sender<ServiceCommand>>>,
    active_ring: Mutex<Option<Arc<SpscFloatRing>>>,
    // Handles are passed to the direct JVM bridge. Retaining old rings makes a
    // stale handle harmless during a coordinated output restart.
    retired_rings: Mutex<Vec<Arc<SpscFloatRing>>>,
    callback_telemetry: Mutex<Option<Arc<CallbackTelemetry>>>,
    preferred_period_frames: AtomicU32,
    preferred_output_device: Mutex<Option<String>>,
    preferred_exclusive: AtomicBool,
    device_info: Mutex<PcmOutputDeviceInfo>,
    running: AtomicBool,
}

#[uniffi::export]
impl PcmOutputService {
    #[uniffi::constructor]
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            command_sender: Mutex::new(None),
            active_ring: Mutex::new(None),
            retired_rings: Mutex::new(Vec::new()),
            callback_telemetry: Mutex::new(None),
            preferred_period_frames: AtomicU32::new(DEFAULT_PERIOD_FRAMES),
            preferred_output_device: Mutex::new(None),
            preferred_exclusive: AtomicBool::new(false),
            device_info: Mutex::new(PcmOutputDeviceInfo::unavailable(None)),
            running: AtomicBool::new(false),
        })
    }

    /// Opens and prepares the output stream without starting hardware playback.
    ///
    /// The producer should fill at least one period through the direct bridge
    /// before calling `start`.
    pub fn initialize(&self) -> PcmOutputDeviceInfo {
        if self.command_sender.lock().expect("command mutex").is_some() {
            return self.device_info();
        }

        let preferred_period_frames = self.preferred_period_frames.load(Ordering::Relaxed);
        let preferred_output_device = self
            .preferred_output_device
            .lock()
            .expect("device mutex")
            .clone();
        let preferred_exclusive = self.preferred_exclusive.load(Ordering::Relaxed);
        let (command_sender, command_receiver) = mpsc::channel();
        let (startup_sender, startup_receiver) = mpsc::sync_channel(1);
        if let Err(error) = std::thread::Builder::new()
            .name("amethyst-pcm-output".to_owned())
            .spawn(move || {
                run_output_service(
                    command_receiver,
                    startup_sender,
                    preferred_period_frames,
                    preferred_output_device,
                    preferred_exclusive,
                );
            })
        {
            return self.set_error(format!("Cannot create PCM output service thread: {error}"));
        }

        match startup_receiver.recv() {
            Ok(Ok(started)) => {
                let mut active_ring = self.active_ring.lock().expect("ring mutex");
                if let Some(previous) = active_ring.replace(Arc::clone(&started.ring)) {
                    self.retired_rings
                        .lock()
                        .expect("retired ring mutex")
                        .push(previous);
                }
                *self.callback_telemetry.lock().expect("telemetry mutex") =
                    Some(started.callback_telemetry);
                *self.command_sender.lock().expect("command mutex") = Some(command_sender);
                *self.device_info.lock().expect("info mutex") = started.info.clone();
                started.info
            }
            Ok(Err(error)) => self.set_error(error),
            Err(_) => self.set_error("PCM output service terminated during startup".to_owned()),
        }
    }

    pub fn start(&self) -> Option<String> {
        let result = self.send_lifecycle_command(ServiceCommand::Start);
        self.running.store(result.is_ok(), Ordering::Release);
        result.err()
    }

    pub fn pause(&self) -> Option<String> {
        let result = self.send_lifecycle_command(ServiceCommand::Pause);
        if result.is_ok() {
            self.running.store(false, Ordering::Release);
        }
        result.err()
    }

    pub fn shutdown(&self) {
        self.running.store(false, Ordering::Release);
        if let Some(sender) = self.command_sender.lock().expect("command mutex").take() {
            let (completion_sender, completion_receiver) = mpsc::sync_channel(1);
            if sender
                .send(ServiceCommand::Shutdown(Some(completion_sender)))
                .is_ok()
            {
                let _ = completion_receiver.recv();
            }
        }
        let mut info = self.device_info.lock().expect("info mutex");
        info.available = false;
    }

    pub fn device_info(&self) -> PcmOutputDeviceInfo {
        self.device_info.lock().expect("info mutex").clone()
    }

    pub fn telemetry(&self) -> PcmOutputTelemetry {
        let active_ring = self.active_ring.lock().expect("ring mutex");
        let callback = self.callback_telemetry.lock().expect("telemetry mutex");
        let queued_frames = active_ring
            .as_ref()
            .map(|ring| ring.available_read_frames() as u64)
            .unwrap_or(0);
        let available_write_frames = active_ring
            .as_ref()
            .map(|ring| ring.available_write_frames() as u64)
            .unwrap_or(0);
        let written_frames = active_ring
            .as_ref()
            .map(|ring| ring.written_frames())
            .unwrap_or(0);
        PcmOutputTelemetry {
            queued_frames,
            available_write_frames,
            written_frames,
            consumed_frames: callback
                .as_ref()
                .map(|value| value.consumed_frames.load(Ordering::Relaxed))
                .unwrap_or(0),
            underruns: callback
                .as_ref()
                .map(|value| value.underruns.load(Ordering::Relaxed))
                .unwrap_or(0),
            stream_errors: callback
                .as_ref()
                .map(|value| value.stream_errors.load(Ordering::Relaxed))
                .unwrap_or(0),
            running: self.running.load(Ordering::Acquire),
        }
    }

    /// Returns an opaque handle for `amethyst_pcm_output_write_direct`.
    ///
    /// The service object must outlive all calls made with this handle.
    pub fn ring_handle(&self) -> u64 {
        self.active_ring
            .lock()
            .expect("ring mutex")
            .as_ref()
            .map(|ring| Arc::as_ptr(ring) as usize as u64)
            .unwrap_or(0)
    }

    pub fn output_devices(&self) -> Vec<PcmOutputDevice> {
        let host = cpal::default_host();
        let default_id = host
            .default_output_device()
            .and_then(|device| device.id().ok())
            .map(|id| id.to_string());
        host.output_devices()
            .map(|devices| {
                devices
                    .filter_map(|device| {
                        let id = device.id().ok()?.to_string();
                        let display_name = device.description().ok()?.name().to_owned();
                        Some(PcmOutputDevice {
                            is_default: default_id.as_deref() == Some(id.as_str()),
                            id,
                            display_name,
                        })
                    })
                    .collect()
            })
            .unwrap_or_default()
    }

    pub fn set_preferred_period_frames(&self, frames: u32) {
        self.preferred_period_frames
            .store(frames.clamp(64, 2_048), Ordering::Relaxed);
    }

    pub fn set_preferred_output_device(&self, name: String) {
        *self.preferred_output_device.lock().expect("device mutex") =
            (!name.trim().is_empty()).then_some(name);
    }

    pub fn set_preferred_exclusive(&self, exclusive: bool) {
        self.preferred_exclusive.store(exclusive, Ordering::Relaxed);
    }

    pub fn promote_current_thread_to_realtime(
        &self,
        period_frames: u32,
        sample_rate: u32,
    ) -> Option<String> {
        match audio_thread_priority::promote_current_thread_to_real_time(period_frames, sample_rate)
        {
            Ok(handle) => {
                // The caller is the long-lived audio render thread. Demotion is
                // unnecessary when that thread exits, and retaining the handle
                // in Kotlin would add lifecycle traffic to the realtime path.
                std::mem::forget(handle);
                None
            }
            Err(error) => Some(error.to_string()),
        }
    }
}

impl PcmOutputService {
    fn send_lifecycle_command(
        &self,
        make_command: impl FnOnce(mpsc::SyncSender<Result<(), String>>) -> ServiceCommand,
    ) -> Result<(), String> {
        let sender = self.command_sender.lock().expect("command mutex").clone();
        let sender = sender.ok_or_else(|| "PCM output is not initialized".to_owned())?;
        let (result_sender, result_receiver) = mpsc::sync_channel(1);
        sender
            .send(make_command(result_sender))
            .map_err(|_| "PCM output service is unavailable".to_owned())?;
        result_receiver
            .recv()
            .map_err(|_| "PCM output service terminated".to_owned())?
    }

    fn set_error(&self, error: String) -> PcmOutputDeviceInfo {
        let info = PcmOutputDeviceInfo::unavailable(Some(error));
        *self.device_info.lock().expect("info mutex") = info.clone();
        info
    }
}

impl Drop for PcmOutputService {
    fn drop(&mut self) {
        if let Ok(sender) = self.command_sender.get_mut()
            && let Some(sender) = sender.take()
        {
            let _ = sender.send(ServiceCommand::Shutdown(None));
        }
    }
}

fn run_output_service(
    command_receiver: mpsc::Receiver<ServiceCommand>,
    startup_sender: mpsc::SyncSender<Result<StartedOutput, String>>,
    preferred_period_frames: u32,
    preferred_output_device: Option<String>,
    preferred_exclusive: bool,
) {
    let (stream, started) = match build_stream(
        preferred_period_frames,
        preferred_output_device,
        preferred_exclusive,
    ) {
        Ok(value) => value,
        Err(error) => {
            let _ = startup_sender.send(Err(error));
            return;
        }
    };
    if startup_sender.send(Ok(started)).is_err() {
        return;
    }

    while let Ok(command) = command_receiver.recv() {
        match command {
            ServiceCommand::Start(result_sender) => {
                let result = stream.play();
                let _ = result_sender.send(result);
            }
            ServiceCommand::Pause(result_sender) => {
                let result = stream.pause();
                let _ = result_sender.send(result);
            }
            ServiceCommand::Shutdown(completion_sender) => {
                drop(stream);
                if let Some(completion_sender) = completion_sender {
                    let _ = completion_sender.send(());
                }
                return;
            }
        }
    }
}

fn build_stream(
    preferred_period_frames: u32,
    preferred_output_device: Option<String>,
    preferred_exclusive: bool,
) -> Result<(OutputStream, StartedOutput), String> {
    #[cfg(target_os = "windows")]
    if preferred_exclusive {
        match wasapi_exclusive::build(preferred_period_frames, preferred_output_device.clone()) {
            Ok((stream, started)) => {
                return Ok((OutputStream::WasapiExclusive(stream), started));
            }
            Err(exclusive_error) => {
                return build_cpal_stream(
                    preferred_period_frames,
                    preferred_output_device,
                    true,
                    Some(exclusive_error),
                );
            }
        }
    }
    build_cpal_stream(
        preferred_period_frames,
        preferred_output_device,
        preferred_exclusive,
        None,
    )
}

fn build_cpal_stream(
    preferred_period_frames: u32,
    preferred_output_device: Option<String>,
    preferred_exclusive: bool,
    fallback_reason: Option<String>,
) -> Result<(OutputStream, StartedOutput), String> {
    let host = cpal::default_host();
    let mut fallback_reason = fallback_reason;
    let (device, device_name, device_id) = match preferred_output_device.as_deref() {
        Some(identifier) if !identifier.trim().is_empty() => {
            let found = resolve_output_device(&host, identifier);
            match found {
                Some(dev) => {
                    let name = output_device_name(&dev);
                    let id = dev.id().map(|id| id.to_string()).unwrap_or_default();
                    (dev, name, id)
                }
                None => {
                    let default_dev = host
                        .default_output_device()
                        .ok_or_else(|| "No default output device available".to_owned())?;
                    let name = output_device_name(&default_dev);
                    let id = default_dev
                        .id()
                        .map(|id| id.to_string())
                        .unwrap_or_default();
                    fallback_reason = Some(format!(
                        "Configured device '{identifier}' unavailable; using default '{name}'"
                    ));
                    (default_dev, name, id)
                }
            }
        }
        _ => {
            let default_dev = host
                .default_output_device()
                .ok_or_else(|| "No default output device available".to_owned())?;
            let name = output_device_name(&default_dev);
            let id = default_dev
                .id()
                .map(|id| id.to_string())
                .unwrap_or_default();
            (default_dev, name, id)
        }
    };
    let supported_config = device
        .default_output_config()
        .map_err(|error| format!("Cannot get output config: {error}"))?;
    let sample_rate = supported_config.sample_rate();
    let hardware_channels = supported_config.channels() as usize;
    if hardware_channels == 0 {
        return Err("Output device reported zero channels".to_owned());
    }
    let (period_frames, fixed_period) = match supported_config.buffer_size() {
        cpal::SupportedBufferSize::Range { min, max } => {
            (preferred_period_frames.clamp(*min, *max), true)
        }
        // AAudio discovers its native burst size only after opening the stream.
        // Supplying the requested callback size is nevertheless required for
        // CPAL's realtime backend to keep the hardware capacity small.
        cpal::SupportedBufferSize::Unknown => {
            (preferred_period_frames, cfg!(target_os = "android"))
        }
    };
    const ENGINE_CHANNELS: usize = 2;
    let ring_capacity_frames = period_frames as usize * RING_PERIODS;
    let ring = Arc::new(SpscFloatRing::new(ring_capacity_frames, ENGINE_CHANNELS));
    let callback_telemetry = Arc::new(CallbackTelemetry::new());
    let mut stream_config: cpal::StreamConfig = supported_config.clone().into();
    if fixed_period {
        stream_config.buffer_size = cpal::BufferSize::Fixed(period_frames);
    }

    let stream_builder = match supported_config.sample_format() {
        cpal::SampleFormat::I8 => build_output_stream::<i8>,
        cpal::SampleFormat::I16 => build_output_stream::<i16>,
        cpal::SampleFormat::I24 => build_output_stream::<cpal::I24>,
        cpal::SampleFormat::I32 => build_output_stream::<i32>,
        cpal::SampleFormat::I64 => build_output_stream::<i64>,
        cpal::SampleFormat::U8 => build_output_stream::<u8>,
        cpal::SampleFormat::U16 => build_output_stream::<u16>,
        cpal::SampleFormat::U24 => build_output_stream::<cpal::U24>,
        cpal::SampleFormat::U32 => build_output_stream::<u32>,
        cpal::SampleFormat::U64 => build_output_stream::<u64>,
        cpal::SampleFormat::F32 => build_output_stream::<f32>,
        cpal::SampleFormat::F64 => build_output_stream::<f64>,
        format => return Err(format!("Unsupported output sample format: {format:?}")),
    };
    let stream = stream_builder(
        &device,
        stream_config,
        Arc::clone(&ring),
        Arc::clone(&callback_telemetry),
        hardware_channels,
    )
    .map_err(|error| format!("Cannot build output stream: {error}"))?;

    let info = PcmOutputDeviceInfo {
        device_id,
        device_name,
        sample_rate,
        channels: ENGINE_CHANNELS as u32,
        period_frames,
        ring_capacity_frames: ring_capacity_frames as u32,
        sample_format: format!("{:?}", supported_config.sample_format()),
        backend: "cpal".to_owned(),
        requested_exclusive: preferred_exclusive,
        active_exclusive: false,
        fallback_reason: fallback_reason.or_else(|| {
            preferred_exclusive.then(|| {
                "Exclusive output is unavailable on this platform/backend; using shared output"
                    .to_owned()
            })
        }),
        available: true,
        error: None,
    };
    Ok((
        OutputStream::Cpal(stream),
        StartedOutput {
            info,
            ring,
            callback_telemetry,
        },
    ))
}

fn resolve_output_device(host: &cpal::Host, identifier: &str) -> Option<cpal::Device> {
    identifier
        .parse::<cpal::DeviceId>()
        .ok()
        .and_then(|id| host.device_by_id(&id))
        .filter(DeviceTrait::supports_output)
        .or_else(|| {
            host.output_devices()
                .ok()?
                .find(|device| output_device_matches(device, identifier))
        })
}

fn output_device_matches(device: &cpal::Device, identifier: &str) -> bool {
    let Some(id) = device.id().ok() else {
        return false;
    };
    let display_name = device
        .description()
        .ok()
        .map(|description| description.name().to_owned());
    identifier_matches_device(
        identifier,
        &id.to_string(),
        id.id(),
        display_name.as_deref(),
    )
}

fn identifier_matches_device(
    identifier: &str,
    canonical_id: &str,
    legacy_id: &str,
    display_name: Option<&str>,
) -> bool {
    identifier == canonical_id || identifier == legacy_id || display_name == Some(identifier)
}

fn output_device_name(device: &cpal::Device) -> String {
    device
        .description()
        .map(|description| description.name().to_owned())
        .unwrap_or_else(|_| "Unknown output device".to_owned())
}

fn build_output_stream<T>(
    device: &cpal::Device,
    config: cpal::StreamConfig,
    ring: Arc<SpscFloatRing>,
    telemetry: Arc<CallbackTelemetry>,
    hardware_channels: usize,
) -> Result<cpal::Stream, cpal::Error>
where
    T: cpal::SizedSample + cpal::FromSample<f32>,
{
    let error_telemetry = Arc::clone(&telemetry);
    device.build_output_stream(
        config,
        move |output: &mut [T], _| consume_output(&ring, &telemetry, output, hardware_channels),
        stream_error_callback(error_telemetry),
        None,
    )
}

fn stream_error_callback(
    telemetry: Arc<CallbackTelemetry>,
) -> impl FnMut(cpal::Error) + Send + 'static {
    move |_| {
        telemetry.stream_errors.fetch_add(1, Ordering::Relaxed);
    }
}

fn consume_output<T>(
    ring: &SpscFloatRing,
    telemetry: &CallbackTelemetry,
    output: &mut [T],
    hardware_channels: usize,
) where
    T: cpal::Sample + cpal::FromSample<f32>,
{
    let total_frames = output.len() / hardware_channels;
    let mut consumed_samples = 0;
    let mut stereo_buffer = [0.0f32; 256];
    let mut frame_offset = 0;

    while frame_offset < total_frames {
        let chunk_frames = (total_frames - frame_offset).min(stereo_buffer.len() / 2);
        let requested_samples = chunk_frames * 2;
        let read_samples = ring.read_f32(&mut stereo_buffer[..requested_samples]);
        let read_frames = read_samples / 2;
        consumed_samples += read_samples;

        for frame in 0..chunk_frames {
            let output_offset = (frame_offset + frame) * hardware_channels;
            if frame < read_frames {
                let left = stereo_buffer[frame * 2];
                let right = stereo_buffer[frame * 2 + 1];
                if hardware_channels == 1 {
                    output[output_offset] = T::from_sample((left + right) * 0.5);
                } else {
                    output[output_offset] = T::from_sample(left);
                    output[output_offset + 1] = T::from_sample(right);
                    output[output_offset + 2..output_offset + hardware_channels]
                        .fill(T::EQUILIBRIUM);
                }
            } else {
                output[output_offset..output_offset + hardware_channels].fill(T::EQUILIBRIUM);
            }
        }
        frame_offset += chunk_frames;
    }

    telemetry.record_callback(consumed_samples, total_frames * 2, 2);
}

/// Direct producer entry point for the JVM bridge.
///
/// # Safety
///
/// `handle` must come from a live `PcmOutputService`, and `samples` must point
/// to at least `sample_count` native-endian floats.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn amethyst_pcm_output_write_direct(
    handle: u64,
    samples: *const f32,
    sample_count: u32,
) -> u32 {
    if handle == 0 || samples.is_null() || sample_count == 0 {
        return 0;
    }
    // SAFETY: the caller owns the direct buffer for the duration of this call
    // and keeps the service (which retains the ring) alive.
    let ring = unsafe { &*(handle as usize as *const SpscFloatRing) };
    let samples = unsafe { std::slice::from_raw_parts(samples, sample_count as usize) };
    ring.write_interleaved(samples) as u32
}

/// Allocation-free queue-depth query for the real-time producer loop.
///
/// # Safety
///
/// `handle` must come from a live `PcmOutputService`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn amethyst_pcm_output_queued_frames(handle: u64) -> u64 {
    if handle == 0 {
        return 0;
    }
    // SAFETY: the service retains active and retired rings for the lifetime of
    // every handle exposed to the JVM.
    let ring = unsafe { &*(handle as usize as *const SpscFloatRing) };
    ring.available_read_frames() as u64
}

#[cfg(test)]
mod tests {
    use super::{
        CallbackTelemetry, SpscFloatRing, amethyst_pcm_output_queued_frames,
        amethyst_pcm_output_write_direct, consume_output, identifier_matches_device,
    };
    use std::sync::Arc;

    #[test]
    fn callback_reads_pcm_and_zero_fills_underrun() {
        let ring = SpscFloatRing::new(4, 2);
        let telemetry = CallbackTelemetry::new();
        ring.write_interleaved(&[0.25, -0.25]);
        let mut output = [99.0; 4];

        consume_output(&ring, &telemetry, &mut output, 2);

        assert_eq!(output, [0.25, -0.25, 0.0, 0.0]);
        assert_eq!(telemetry.consumed_frames.load(Ordering::Relaxed), 1);
        assert_eq!(telemetry.underruns.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn two_primed_periods_are_consumed_without_underruns() {
        let ring = SpscFloatRing::new(4, 2);
        let telemetry = CallbackTelemetry::new();
        let primed = [0.25, -0.25, 0.5, -0.5, 0.75, -0.75, 1.0, -1.0];
        assert_eq!(ring.write_interleaved(&primed), primed.len());

        let mut first_period = [0.0; 4];
        let mut second_period = [0.0; 4];
        consume_output(&ring, &telemetry, &mut first_period, 2);
        consume_output(&ring, &telemetry, &mut second_period, 2);

        assert_eq!(first_period, primed[..4]);
        assert_eq!(second_period, primed[4..]);
        assert_eq!(telemetry.underruns.load(Ordering::Relaxed), 0);
    }

    #[test]
    fn mono_downmixes_stereo_input() {
        let ring = SpscFloatRing::new(4, 2);
        let telemetry = CallbackTelemetry::new();
        ring.write_interleaved(&[0.2, 0.8, -0.4, 0.4]);
        let mut output = [0.0; 2];

        consume_output(&ring, &telemetry, &mut output, 1);

        assert_eq!(output, [0.5, 0.0]);
        assert_eq!(telemetry.consumed_frames.load(Ordering::Relaxed), 2);
        assert_eq!(telemetry.underruns.load(Ordering::Relaxed), 0);
    }

    #[test]
    fn multichannel_maps_stereo_to_front_and_zeroes_extra_channels() {
        let ring = SpscFloatRing::new(4, 2);
        let telemetry = CallbackTelemetry::new();
        ring.write_interleaved(&[0.3, -0.3, 0.6, -0.6]);
        let mut output = [9.0f32; 8]; // 2 frames of 4 channels

        consume_output(&ring, &telemetry, &mut output, 4);

        assert_eq!(output, [0.3, -0.3, 0.0, 0.0, 0.6, -0.6, 0.0, 0.0]);
        assert_eq!(telemetry.consumed_frames.load(Ordering::Relaxed), 2);
        assert_eq!(telemetry.underruns.load(Ordering::Relaxed), 0);
    }

    #[test]
    fn integer_callbacks_use_format_correct_silence() {
        let signed_ring = SpscFloatRing::new(2, 2);
        let signed_telemetry = CallbackTelemetry::new();
        let mut signed = [1i16; 2];
        consume_output(&signed_ring, &signed_telemetry, &mut signed, 2);
        assert_eq!(signed, [0, 0]);

        let unsigned_ring = SpscFloatRing::new(2, 2);
        let unsigned_telemetry = CallbackTelemetry::new();
        let mut unsigned = [1u16; 2];
        consume_output(&unsigned_ring, &unsigned_telemetry, &mut unsigned, 2);
        assert_eq!(unsigned, [32_768, 32_768]);
    }

    #[test]
    fn callbacks_support_additional_pcm_formats() {
        let ring = SpscFloatRing::new(2, 2);
        let telemetry = CallbackTelemetry::new();
        ring.write_interleaved(&[-1.0, 1.0]);
        let mut output = [0i32; 2];

        consume_output(&ring, &telemetry, &mut output, 2);

        assert_eq!(output, [i32::MIN, i32::MAX]);
    }

    #[test]
    fn device_matching_accepts_canonical_and_legacy_preferences() {
        let canonical = "coreaudio:BuiltInSpeakerDevice";
        let legacy = "BuiltInSpeakerDevice";
        let name = Some("MacBook Pro Speakers");

        assert!(identifier_matches_device(
            canonical, canonical, legacy, name
        ));
        assert!(identifier_matches_device(legacy, canonical, legacy, name));
        assert!(identifier_matches_device(
            "MacBook Pro Speakers",
            canonical,
            legacy,
            name,
        ));
        assert!(!identifier_matches_device(
            "BlackHole 2ch",
            canonical,
            legacy,
            name,
        ));
    }

    #[test]
    fn direct_bridge_writes_to_the_preallocated_ring() {
        let ring = Arc::new(SpscFloatRing::new(2, 2));
        let handle = Arc::as_ptr(&ring) as usize as u64;
        let input = [0.1, 0.2, 0.3, 0.4];
        // SAFETY: both the ring handle and input slice remain alive for the call.
        let written =
            unsafe { amethyst_pcm_output_write_direct(handle, input.as_ptr(), input.len() as u32) };
        assert_eq!(written, 4);
        // SAFETY: the ring remains alive for the query.
        assert_eq!(unsafe { amethyst_pcm_output_queued_frames(handle) }, 2);

        let mut output = [0.0; 4];
        assert_eq!(ring.read_f32(&mut output), 4);
        assert_eq!(output, input);
    }

    use std::sync::atomic::Ordering;
}
