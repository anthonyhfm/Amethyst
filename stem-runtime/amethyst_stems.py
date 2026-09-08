#!/usr/bin/env python3
"""JSON-lines stem-separation worker embedded in Amethyst desktop builds."""

from __future__ import annotations

import argparse
import json
import sys
from contextlib import nullcontext
from pathlib import Path


BS_ROFORMER_MODEL_ID = "bs_roformer_4stem"
BS_ROFORMER_CHECKPOINT = "model_bs_roformer_ep_17_sdr_9.6568.ckpt"
BS_ROFORMER_SAMPLE_RATE = 44_100
BS_ROFORMER_CHUNK_SIZE = 485_100
BS_ROFORMER_STEMS = ("drums", "bass", "other", "vocals")


def emit(event_type: str, **values: object) -> None:
    print(json.dumps({"type": event_type, **values}, separators=(",", ":")), flush=True)


def choose_device(requested: str) -> tuple[str, str]:
    import torch

    if requested == "cpu":
        return "cpu", "CPU"
    if sys.platform == "darwin" and torch.backends.mps.is_available():
        return "mps", "MPS"
    if torch.cuda.is_available():
        return "cuda:0", f"CUDA · {torch.cuda.get_device_name(0)}"
    return "cpu", "CPU"


def load_audio(path: Path, sample_rate: int, channels: int):
    import wave

    import numpy as np
    import torch
    from demucs.audio import convert_audio

    with wave.open(str(path), "rb") as source:
        if source.getcomptype() != "NONE":
            raise ValueError("Amethyst stem input must be uncompressed PCM")
        input_channels = source.getnchannels()
        sample_width = source.getsampwidth()
        input_sample_rate = source.getframerate()
        frame_count = source.getnframes()
        raw = source.readframes(frame_count)

    if sample_width == 1:
        samples = (np.frombuffer(raw, dtype=np.uint8).astype(np.float32) - 128.0) / 128.0
    elif sample_width == 2:
        samples = np.frombuffer(raw, dtype="<i2").astype(np.float32) / 32_768.0
    elif sample_width == 3:
        bytes_24 = np.frombuffer(raw, dtype=np.uint8).reshape(-1, 3).astype(np.int32)
        packed = bytes_24[:, 0] | (bytes_24[:, 1] << 8) | (bytes_24[:, 2] << 16)
        signed = np.where(packed & 0x80_0000, packed - 0x100_0000, packed)
        samples = signed.astype(np.float32) / 8_388_608.0
    elif sample_width == 4:
        samples = np.frombuffer(raw, dtype="<i4").astype(np.float32) / 2_147_483_648.0
    else:
        raise ValueError(f"Unsupported PCM sample width: {sample_width * 8} bits")

    if samples.size != frame_count * input_channels:
        raise ValueError("PCM payload size does not match its WAV header")
    wav = torch.from_numpy(samples.reshape(frame_count, input_channels).T.copy())
    return convert_audio(wav, input_sample_rate, sample_rate, channels)


def save_stems(stems: dict[str, object], output: Path, sample_rate: int) -> None:
    import wave

    import numpy as np

    for name in ("vocals", "drums", "bass", "other"):
        audio = stems[name].detach().float().cpu().numpy()
        audio = np.nan_to_num(audio, copy=False, nan=0.0, posinf=0.0, neginf=0.0)
        peak = float(np.max(np.abs(audio), initial=0.0))
        if peak > 0.99:
            audio *= 0.99 / peak
        pcm = np.rint(np.clip(audio.T, -1.0, 1.0) * 32_767.0).astype("<i2")
        with wave.open(str(output / f"{name}.wav"), "wb") as destination:
            destination.setnchannels(audio.shape[0])
            destination.setsampwidth(2)
            destination.setframerate(sample_rate)
            destination.writeframes(pcm.tobytes())


def separate_demucs(model_id: str, repository: Path, input_path: Path, output: Path, device: str) -> None:
    import torch
    from demucs.apply import apply_model
    from demucs.pretrained import get_model

    # Demucs 4 packages contain the model class and constructor metadata in
    # addition to tensors. PyTorch 2.6+ defaults torch.load to weights_only,
    # which cannot deserialize that older trusted package format. Kotlin has
    # already verified every selected checkpoint against its publisher hash.
    original_torch_load = torch.load

    def load_verified_demucs_package(*args, **kwargs):
        kwargs.setdefault("weights_only", False)
        return original_torch_load(*args, **kwargs)

    torch.load = load_verified_demucs_package
    try:
        model = get_model(model_id, repo=repository)
    finally:
        torch.load = original_torch_load
    model.cpu()
    model.eval()

    wav = load_audio(input_path, model.samplerate, model.audio_channels)
    reference = wav.mean(0)
    reference_mean = reference.mean()
    reference_std = reference.std()
    if not torch.isfinite(reference_std) or reference_std <= 1e-8:
        reference_std = torch.tensor(1.0, dtype=wav.dtype)
    wav = (wav - reference_mean) / reference_std
    emit("progress", value=0.03)
    separated = apply_model(
        model,
        wav[None],
        device=device,
        shifts=1,
        split=True,
        overlap=0.25,
        progress=False,
        num_workers=0,
    )[0]
    separated = separated * reference_std + reference_mean
    emit("progress", value=0.92)
    save_stems(dict(zip(model.sources, separated)), output, model.samplerate)


def create_bs_roformer():
    from models.bs_roformer.bs_roformer import BSRoformer

    frequencies = (
        (2,) * 24
        + (4,) * 12
        + (12,) * 8
        + (24,) * 8
        + (48,) * 8
        + (128, 129)
    )
    return BSRoformer(
        dim=384,
        depth=8,
        stereo=True,
        num_stems=4,
        time_transformer_depth=1,
        freq_transformer_depth=1,
        linear_transformer_depth=0,
        freqs_per_bands=frequencies,
        dim_head=64,
        heads=8,
        attn_dropout=0.1,
        ff_dropout=0.1,
        flash_attn=True,
        dim_freqs_in=1025,
        stft_n_fft=2048,
        stft_hop_length=441,
        stft_win_length=2048,
        stft_normalized=False,
        mask_estimator_depth=2,
        multi_stft_resolution_loss_weight=1.0,
        multi_stft_resolutions_window_sizes=(4096, 2048, 1024, 512, 256),
        multi_stft_hop_size=147,
        multi_stft_normalized=False,
        mlp_expansion_factor=2,
        use_torch_checkpoint=False,
        skip_connection=False,
    )


def bs_roformer_window(torch, length: int, fade_size: int, first: bool, last: bool):
    window = torch.ones(length, dtype=torch.float32)
    fade = min(fade_size, length)
    if not first and fade:
        window[:fade] *= torch.linspace(0, 1, fade)
    if not last and fade:
        window[-fade:] *= torch.linspace(1, 0, fade)
    return window


def separate_bs_roformer(repository: Path, input_path: Path, output: Path, device: str) -> None:
    import torch
    import torch.nn.functional as functional

    model = create_bs_roformer()
    checkpoint_path = repository / BS_ROFORMER_CHECKPOINT
    state_dict = torch.load(checkpoint_path, map_location="cpu", weights_only=True)
    if "state_dict" in state_dict:
        state_dict = state_dict["state_dict"]
    model.load_state_dict(state_dict)
    model.eval()
    model.to(device)

    mix = load_audio(input_path, BS_ROFORMER_SAMPLE_RATE, 2)
    chunk_size = BS_ROFORMER_CHUNK_SIZE
    step = chunk_size // 2
    border = chunk_size - step
    original_length = mix.shape[-1]
    padded = original_length > 2 * border
    if padded:
        mix = functional.pad(mix, (border, border), mode="reflect")

    total_length = mix.shape[-1]
    starts = list(range(0, total_length, step))
    result = torch.zeros((4, 2, total_length), dtype=torch.float32)
    counter = torch.zeros(total_length, dtype=torch.float32)
    fade_size = chunk_size // 10

    with torch.inference_mode():
        for index, start in enumerate(starts):
            part = mix[:, start:start + chunk_size]
            length = part.shape[-1]
            if length < chunk_size:
                if length > chunk_size // 2 + 1:
                    part = functional.pad(part, (0, chunk_size - length), mode="reflect")
                else:
                    part = functional.pad(part, (0, chunk_size - length), mode="constant", value=0)
            precision = torch.cuda.amp.autocast() if device.startswith("cuda") else nullcontext()
            with precision:
                prediction = model(part.unsqueeze(0).to(device))[0, ..., :length].float().cpu()
            window = bs_roformer_window(
                torch,
                length,
                fade_size,
                first=index == 0,
                last=index == len(starts) - 1,
            )
            result[..., start:start + length] += prediction * window
            counter[start:start + length] += window
            emit("progress", value=0.03 + ((index + 1) / len(starts)) * 0.89)

    result /= counter.clamp_min(1e-8)
    if padded:
        result = result[..., border:-border]
    save_stems(dict(zip(BS_ROFORMER_STEMS, result)), output, BS_ROFORMER_SAMPLE_RATE)


def main() -> int:
    parser = argparse.ArgumentParser(description="Amethyst stem-separation worker")
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--model", required=True)
    parser.add_argument("--model-repository", required=True, type=Path)
    parser.add_argument("--device", choices=("auto", "cpu"), default="auto")
    args = parser.parse_args()

    try:
        device, display_device = choose_device(args.device)
        emit("backend", name=display_device)
        args.output.mkdir(parents=True, exist_ok=True)
        if args.model == BS_ROFORMER_MODEL_ID:
            separate_bs_roformer(args.model_repository, args.input, args.output, device)
        else:
            separate_demucs(args.model, args.model_repository, args.input, args.output, device)
        emit("progress", value=1.0)
        emit("result", output=str(args.output))
        return 0
    except BaseException as error:
        emit("error", message=f"{type(error).__name__}: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
