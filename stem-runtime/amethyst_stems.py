#!/usr/bin/env python3
"""Small JSON-lines Demucs v4 worker embedded in Amethyst desktop builds."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


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


def main() -> int:
    parser = argparse.ArgumentParser(description="Amethyst Demucs v4 worker")
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--device", choices=("auto", "cpu"), default="auto")
    args = parser.parse_args()

    try:
        import torch
        import torchaudio
        from demucs.apply import apply_model
        from demucs.audio import convert_audio, save_audio
        from demucs.pretrained import get_model

        device, display_device = choose_device(args.device)
        emit("backend", name=display_device)
        args.output.mkdir(parents=True, exist_ok=True)
        model = get_model(args.model.name.split("-", 1)[0], repo=args.model.parent)
        model.cpu()
        model.eval()

        wav, sample_rate = torchaudio.load(str(args.input))
        wav = convert_audio(wav, sample_rate, model.samplerate, model.audio_channels)
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

        stems = dict(zip(model.sources, separated))
        for name in ("vocals", "drums", "bass", "other"):
            save_audio(
                stems[name],
                args.output / f"{name}.wav",
                model.samplerate,
                clip="rescale",
                bits_per_sample=16,
            )
        emit("progress", value=1.0)
        emit("result", output=str(args.output))
        return 0
    except BaseException as error:
        emit("error", message=f"{type(error).__name__}: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
