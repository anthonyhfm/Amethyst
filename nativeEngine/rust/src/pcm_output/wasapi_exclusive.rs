use std::sync::atomic::Ordering;
use std::sync::{Arc, mpsc};
use std::time::Duration;

use cpal::traits::{DeviceTrait, HostTrait};
use windows::Win32::Foundation::{CloseHandle, WAIT_OBJECT_0};
use windows::Win32::Media::Audio::{
    AUDCLNT_SHAREMODE_EXCLUSIVE, AUDCLNT_STREAMFLAGS_EVENTCALLBACK, IAudioClient,
    IAudioRenderClient, IMMDevice, IMMDeviceEnumerator, MMDeviceEnumerator, WAVEFORMATEX,
    WAVEFORMATEXTENSIBLE, eConsole, eRender,
};
use windows::Win32::Media::KernelStreaming::{KSDATAFORMAT_SUBTYPE_PCM, WAVE_FORMAT_EXTENSIBLE};
use windows::Win32::Media::Multimedia::KSDATAFORMAT_SUBTYPE_IEEE_FLOAT;
use windows::Win32::System::Com::{
    CLSCTX_ALL, COINIT_MULTITHREADED, CoCreateInstance, CoInitializeEx, CoTaskMemFree,
    CoUninitialize,
};
use windows::Win32::System::Threading::{CreateEventW, WaitForSingleObject};
use windows::core::PCWSTR;

use super::ring::SpscFloatRing;
use super::{CallbackTelemetry, PcmOutputDeviceInfo, StartedOutput};

const RING_PERIODS: usize = 4;
const HNS_PER_SECOND: i64 = 10_000_000;

enum Command {
    Play(mpsc::SyncSender<Result<(), String>>),
    Pause(mpsc::SyncSender<Result<(), String>>),
    Shutdown(mpsc::SyncSender<()>),
}

pub(crate) struct ExclusiveOutputStream {
    commands: mpsc::Sender<Command>,
}

impl ExclusiveOutputStream {
    pub(crate) fn play(&self) -> Result<(), String> {
        self.lifecycle(Command::Play)
    }

    pub(crate) fn pause(&self) -> Result<(), String> {
        self.lifecycle(Command::Pause)
    }

    fn lifecycle(
        &self,
        make_command: impl FnOnce(mpsc::SyncSender<Result<(), String>>) -> Command,
    ) -> Result<(), String> {
        let (sender, receiver) = mpsc::sync_channel(1);
        self.commands
            .send(make_command(sender))
            .map_err(|_| "WASAPI exclusive thread is unavailable".to_owned())?;
        receiver
            .recv()
            .map_err(|_| "WASAPI exclusive thread terminated".to_owned())?
    }
}

impl Drop for ExclusiveOutputStream {
    fn drop(&mut self) {
        let (sender, receiver) = mpsc::sync_channel(1);
        if self.commands.send(Command::Shutdown(sender)).is_ok() {
            let _ = receiver.recv_timeout(Duration::from_secs(1));
        }
    }
}

pub(crate) fn build(
    preferred_period_frames: u32,
    preferred_output_device: Option<String>,
) -> Result<(ExclusiveOutputStream, StartedOutput), String> {
    // Older settings stored CPAL's display label. Resolve that value to the
    // stable endpoint id so enabling Exclusive does not require reselecting the
    // device after upgrading.
    let preferred_output_device = preferred_output_device.map(resolve_endpoint_id);
    let (commands, command_receiver) = mpsc::channel();
    let (startup_sender, startup_receiver) = mpsc::sync_channel(1);
    std::thread::Builder::new()
        .name("amethyst-wasapi-exclusive".to_owned())
        .spawn(move || {
            run(
                command_receiver,
                startup_sender,
                preferred_period_frames,
                preferred_output_device,
            )
        })
        .map_err(|error| format!("Cannot create WASAPI exclusive thread: {error}"))?;

    let started = startup_receiver
        .recv()
        .map_err(|_| "WASAPI exclusive thread terminated during startup".to_owned())??;
    Ok((ExclusiveOutputStream { commands }, started))
}

fn resolve_endpoint_id(identifier: String) -> String {
    cpal::default_host()
        .output_devices()
        .ok()
        .and_then(|devices| {
            devices
                .filter(|device| {
                    device.id().ok().is_some_and(|id| id.id() == identifier)
                        || device.to_string() == identifier
                })
                .find_map(|device| device.id().ok().map(|id| id.id().to_owned()))
        })
        .unwrap_or(identifier)
}

struct WasapiState {
    client: IAudioClient,
    render_client: IAudioRenderClient,
    event: windows::Win32::Foundation::HANDLE,
    ring: Arc<SpscFloatRing>,
    telemetry: Arc<CallbackTelemetry>,
    buffer_frames: u32,
    channels: usize,
    format: HardwareFormat,
    sample_rate: u32,
}

impl Drop for WasapiState {
    fn drop(&mut self) {
        let _ = unsafe { self.client.Stop() };
        let _ = unsafe { CloseHandle(self.event) };
    }
}

#[derive(Clone, Copy)]
enum HardwareFormat {
    Float32,
    Int16,
}

impl HardwareFormat {
    fn bits_per_sample(self) -> u16 {
        match self {
            HardwareFormat::Float32 => 32,
            HardwareFormat::Int16 => 16,
        }
    }

    fn is_float(self) -> bool {
        matches!(self, HardwareFormat::Float32)
    }

    fn label(self) -> &'static str {
        match self {
            HardwareFormat::Float32 => "F32",
            HardwareFormat::Int16 => "I16",
        }
    }
}

/// Number of channels WASAPI exclusive mode is negotiated for (stereo only).
const EXCLUSIVE_CHANNELS: u16 = 2;
/// Stereo channel mask (front-left | front-right), matching `EXCLUSIVE_CHANNELS`.
const STEREO_CHANNEL_MASK: u32 = 0x3;

/// Builds a `WAVEFORMATEXTENSIBLE` candidate for the given sample rate and
/// hardware format, deriving the dependent block-alignment fields.
fn build_waveformatextensible(sample_rate: u32, format: HardwareFormat) -> Box<WAVEFORMATEXTENSIBLE> {
    let bits_per_sample = format.bits_per_sample();
    let channels = EXCLUSIVE_CHANNELS;
    let block_align = channels * (bits_per_sample / 8);
    let mut wave_format = WAVEFORMATEXTENSIBLE::default();
    wave_format.Format.wFormatTag = WAVE_FORMAT_EXTENSIBLE as u16;
    wave_format.Format.nChannels = channels;
    wave_format.Format.nSamplesPerSec = sample_rate;
    wave_format.Format.nAvgBytesPerSec = sample_rate * block_align as u32;
    wave_format.Format.nBlockAlign = block_align;
    wave_format.Format.wBitsPerSample = bits_per_sample;
    wave_format.Format.cbSize =
        (std::mem::size_of::<WAVEFORMATEXTENSIBLE>() - std::mem::size_of::<WAVEFORMATEX>()) as u16;
    wave_format.Samples.wValidBitsPerSample = bits_per_sample;
    wave_format.dwChannelMask = STEREO_CHANNEL_MASK;
    wave_format.SubFormat = if format.is_float() {
        KSDATAFORMAT_SUBTYPE_IEEE_FLOAT
    } else {
        KSDATAFORMAT_SUBTYPE_PCM
    };
    Box::new(wave_format)
}

/// Builds an ordered, deduplicated list of candidate exclusive-mode formats
/// to probe via `IsFormatSupported`: Float32 and PCM16 at the endpoint's mix
/// sample rate first, then the same formats at common fallback rates.
fn candidate_formats(mix_sample_rate: u32) -> Vec<(HardwareFormat, u32, Box<WAVEFORMATEXTENSIBLE>)> {
    let mut sample_rates = vec![mix_sample_rate];
    for rate in [48_000u32, 44_100u32] {
        if !sample_rates.contains(&rate) {
            sample_rates.push(rate);
        }
    }

    let mut candidates = Vec::with_capacity(sample_rates.len() * 2);
    for sample_rate in sample_rates {
        for format in [HardwareFormat::Float32, HardwareFormat::Int16] {
            candidates.push((
                format,
                sample_rate,
                build_waveformatextensible(sample_rate, format),
            ));
        }
    }
    candidates
}

struct ComApartment;

impl Drop for ComApartment {
    fn drop(&mut self) {
        unsafe { CoUninitialize() };
    }
}

fn run(
    commands: mpsc::Receiver<Command>,
    startup: mpsc::SyncSender<Result<StartedOutput, String>>,
    preferred_period_frames: u32,
    preferred_output_device: Option<String>,
) {
    let com_result = unsafe { CoInitializeEx(None, COINIT_MULTITHREADED) };
    if com_result.is_err() {
        let _ = startup.send(Err(format!(
            "Cannot initialize the WASAPI COM apartment: {com_result:?}"
        )));
        return;
    }
    let _com_apartment = ComApartment;
    let mut state = match initialize(preferred_period_frames, preferred_output_device) {
        Ok(value) => value,
        Err(error) => {
            let _ = startup.send(Err(error));
            return;
        }
    };
    let info = PcmOutputDeviceInfo {
        device_id: state.1,
        device_name: state.2,
        sample_rate: state.0.sample_rate,
        channels: state.0.channels as u32,
        period_frames: state.0.buffer_frames,
        ring_capacity_frames: (state.0.buffer_frames as usize * RING_PERIODS) as u32,
        sample_format: state.0.format.label().to_owned(),
        backend: "WASAPI".to_owned(),
        requested_exclusive: true,
        active_exclusive: true,
        fallback_reason: None,
        available: true,
        error: None,
    };
    let started = StartedOutput {
        info,
        ring: Arc::clone(&state.0.ring),
        callback_telemetry: Arc::clone(&state.0.telemetry),
    };
    if startup.send(Ok(started)).is_err() {
        return;
    }

    let _priority = audio_thread_priority::promote_current_thread_to_real_time(
        state.0.buffer_frames,
        state.0.sample_rate,
    )
    .ok();
    let mut playing = false;
    loop {
        while let Ok(command) = commands.try_recv() {
            match command {
                Command::Play(result) => {
                    let value = if playing {
                        Ok(())
                    } else {
                        render_period(&mut state.0).and_then(|_| {
                            unsafe { state.0.client.Start() }.map_err(|error| {
                                format!("Cannot start WASAPI exclusive stream: {error}")
                            })
                        })
                    };
                    if value.is_ok() {
                        playing = true;
                    }
                    let _ = result.send(value);
                }
                Command::Pause(result) => {
                    let value = if !playing {
                        Ok(())
                    } else {
                        unsafe { state.0.client.Stop().and_then(|_| state.0.client.Reset()) }
                            .map_err(|error| {
                                format!("Cannot pause WASAPI exclusive stream: {error}")
                            })
                    };
                    if value.is_ok() {
                        playing = false;
                    }
                    let _ = result.send(value);
                }
                Command::Shutdown(done) => {
                    let _ = done.send(());
                    return;
                }
            }
        }

        if !playing {
            match commands.recv_timeout(Duration::from_millis(10)) {
                Ok(command) => {
                    // Put the command back through the same handling path.
                    match command {
                        Command::Play(result) => {
                            let value = render_period(&mut state.0).and_then(|_| {
                                unsafe { state.0.client.Start() }.map_err(|error| {
                                    format!("Cannot start WASAPI exclusive stream: {error}")
                                })
                            });
                            if value.is_ok() {
                                playing = true;
                            }
                            let _ = result.send(value);
                        }
                        Command::Pause(result) => {
                            let _ = result.send(Ok(()));
                        }
                        Command::Shutdown(done) => {
                            let _ = done.send(());
                            return;
                        }
                    }
                }
                Err(mpsc::RecvTimeoutError::Disconnected) => return,
                Err(mpsc::RecvTimeoutError::Timeout) => {}
            }
            continue;
        }

        let wait = unsafe { WaitForSingleObject(state.0.event, 20) };
        if wait == WAIT_OBJECT_0 && render_period(&mut state.0).is_err() {
            state
                .0
                .telemetry
                .stream_errors
                .fetch_add(1, Ordering::Relaxed);
        }
    }
}

fn initialize(
    preferred_period_frames: u32,
    preferred_output_device: Option<String>,
) -> Result<(WasapiState, String, String), String> {
    unsafe {
        let enumerator: IMMDeviceEnumerator =
            CoCreateInstance(&MMDeviceEnumerator, None, CLSCTX_ALL)
                .map_err(|error| format!("Cannot create WASAPI device enumerator: {error}"))?;
        let device = select_device(&enumerator, preferred_output_device)?;
        let device_id_pointer = device
            .GetId()
            .map_err(|error| format!("Cannot read WASAPI endpoint id: {error}"))?;
        let device_id = device_id_pointer
            .to_string()
            .map_err(|error| format!("Cannot decode WASAPI endpoint id: {error}"))?;
        let device_name = device_id.clone();
        let client: IAudioClient = device
            .Activate(CLSCTX_ALL, None)
            .map_err(|error| format!("Cannot activate WASAPI audio client: {error}"))?;
        let mix_format_ptr = client
            .GetMixFormat()
            .map_err(|error| format!("Cannot get WASAPI endpoint format: {error}"))?;
        let mix_channels = (*mix_format_ptr).nChannels as usize;
        let mix_sample_rate = (*mix_format_ptr).nSamplesPerSec;
        CoTaskMemFree(Some(mix_format_ptr.cast()));
        if mix_channels != 2 || mix_sample_rate == 0 {
            return Err(format!(
                "WASAPI exclusive requires stereo output; endpoint reports {mix_channels} channels"
            ));
        }

        let mut attempts = Vec::new();
        let mut negotiated = None;
        for (hardware_format, sample_rate, candidate) in candidate_formats(mix_sample_rate) {
            let candidate_ptr: *const WAVEFORMATEX = std::ptr::addr_of!(candidate.Format);
            let supported = client
                .IsFormatSupported(AUDCLNT_SHAREMODE_EXCLUSIVE, candidate_ptr, None)
                .ok();
            match supported {
                Ok(()) => {
                    negotiated = Some((hardware_format, sample_rate, candidate));
                    break;
                }
                Err(error) => {
                    attempts.push(format!(
                        "{} @ {sample_rate}Hz: {error}",
                        hardware_format.label()
                    ));
                }
            }
        }
        let (hardware_format, sample_rate, candidate) = negotiated.ok_or_else(|| {
            format!(
                "No WASAPI exclusive format is supported by the endpoint (tried: {})",
                attempts.join("; ")
            )
        })?;

        let mut minimum_period_hns = 0i64;
        client
            .GetDevicePeriod(None, Some(&mut minimum_period_hns))
            .map_err(|error| format!("Cannot query WASAPI device period: {error}"))?;
        let requested_hns = (preferred_period_frames.max(1) as i64 * HNS_PER_SECOND
            / sample_rate.max(1) as i64)
            .max(minimum_period_hns);
        let candidate_ptr: *const WAVEFORMATEX = std::ptr::addr_of!(candidate.Format);
        client
            .Initialize(
                AUDCLNT_SHAREMODE_EXCLUSIVE,
                AUDCLNT_STREAMFLAGS_EVENTCALLBACK,
                requested_hns,
                requested_hns,
                candidate_ptr,
                None,
            )
            .map_err(|error| format!("Cannot initialize WASAPI exclusive stream: {error}"))?;
        let channels = EXCLUSIVE_CHANNELS as usize;

        let event = CreateEventW(None, false, false, PCWSTR::null())
            .map_err(|error| format!("Cannot create WASAPI render event: {error}"))?;
        client
            .SetEventHandle(event)
            .map_err(|error| format!("Cannot attach WASAPI render event: {error}"))?;
        let buffer_frames = client
            .GetBufferSize()
            .map_err(|error| format!("Cannot get WASAPI buffer size: {error}"))?;
        let render_client: IAudioRenderClient = client
            .GetService()
            .map_err(|error| format!("Cannot get WASAPI render client: {error}"))?;
        let ring = Arc::new(SpscFloatRing::new(
            buffer_frames as usize * RING_PERIODS,
            channels,
        ));
        let telemetry = Arc::new(CallbackTelemetry::new());
        Ok((
            WasapiState {
                client,
                render_client,
                event,
                ring,
                telemetry,
                buffer_frames,
                channels,
                format: hardware_format,
                sample_rate,
            },
            device_id,
            device_name,
        ))
    }
}

fn select_device(
    enumerator: &IMMDeviceEnumerator,
    preferred_output_device: Option<String>,
) -> Result<IMMDevice, String> {
    match preferred_output_device {
        Some(id) => {
            let wide: Vec<u16> = id.encode_utf16().chain(std::iter::once(0)).collect();
            unsafe { enumerator.GetDevice(PCWSTR(wide.as_ptr())) }
                .map_err(|error| format!("Configured WASAPI endpoint is unavailable: {error}"))
        }
        None => unsafe { enumerator.GetDefaultAudioEndpoint(eRender, eConsole) }
            .map_err(|error| format!("No default WASAPI output endpoint: {error}")),
    }
}

fn render_period(state: &mut WasapiState) -> Result<(), String> {
    unsafe {
        let data = state
            .render_client
            .GetBuffer(state.buffer_frames)
            .map_err(|error| format!("Cannot acquire WASAPI render buffer: {error}"))?;
        let requested_samples = state.buffer_frames as usize * state.channels;
        let consumed = match state.format {
            HardwareFormat::Float32 => {
                let output = std::slice::from_raw_parts_mut(data.cast::<f32>(), requested_samples);
                let consumed = state.ring.read_f32(output);
                output[consumed..].fill(0.0);
                consumed
            }
            HardwareFormat::Int16 => {
                let output = std::slice::from_raw_parts_mut(data.cast::<i16>(), requested_samples);
                let consumed = state.ring.read_i16(output);
                output[consumed..].fill(0);
                consumed
            }
        };
        state
            .telemetry
            .record_callback(consumed, requested_samples, state.channels);
        state
            .render_client
            .ReleaseBuffer(state.buffer_frames, 0)
            .map_err(|error| format!("Cannot release WASAPI render buffer: {error}"))
    }
}
