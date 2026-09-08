#!/usr/bin/env python3
"""Build Amethyst's small, relocatable stem-separation runtime.

The runtime deliberately uses a stripped python-build-standalone distribution
instead of the machine's Python or a relocatability-fragile virtualenv. CUDA is
not part of the base image: macOS gets the MPS-capable PyTorch wheel and
Windows/Linux get the CPU wheel. A CUDA wheel can therefore be installed as an
optional app-data overlay without making every Amethyst installer several GB.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import shutil
import stat
import subprocess
import tarfile
import tempfile
import urllib.request
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
BUILD_ROOT = PROJECT_ROOT / "composeApp" / "build"
OUTPUT_ROOT = BUILD_ROOT / "generated" / "stemRuntime"
CACHE = BUILD_ROOT / "stemRuntimeCache"
PYTHON_RELEASE = "20260510"
PYTHON_VERSION = "3.10.20"
PYTORCH_VERSION = "2.11.0"
TORCHAUDIO_VERSION = "2.11.0"
INTEL_MAC_PYTORCH_VERSION = "2.0.1"
INTEL_MAC_TORCHAUDIO_VERSION = "2.0.2"
BS_ROFORMER_SOURCE_COMMIT = "aef04b2e52fb3beaf25e333199f5a7236e628e7b"
BS_ROFORMER_SOURCES = {
    "attend.py": "0459d799ade55541df2994b0becf7aec12214491360c5a06e346f6d615eaed15",
    "bs_roformer.py": "93408c7254c60c48e47be0657a64745065396b0b1c6da4e02c75aca57eb62bf3",
}


@dataclass(frozen=True)
class Target:
    name: str
    archive_target: str
    archive_sha256: str
    python_relative: str
    torch_index: str | None


TARGETS = {
    "macos-arm64": Target(
        "macos-arm64", "aarch64-apple-darwin",
        "36b7364a5cd75e5b8591c4dc6cc30d84d9112b62a2b8199c406d63f2ca2f981f",
        "bin/python3", None,
    ),
    "macos-x64": Target(
        "macos-x64", "x86_64-apple-darwin",
        "e7ce16965714c05b2cc4515f20cd0c1f8d4fef037bd34daf18d924677f6545b8",
        "bin/python3", None,
    ),
    "windows-x64": Target(
        "windows-x64", "x86_64-pc-windows-msvc",
        "d1e8fb30cba04e6bb5a703e0186da77f833957de027562fa4df9fd0424ae5f7e",
        "python.exe", "https://download.pytorch.org/whl/cpu",
    ),
    "linux-x64": Target(
        "linux-x64", "x86_64-unknown-linux-gnu",
        "dc734bdd388975c0b093fe730b272af741a2e192475d38bc6845a687b6405922",
        "bin/python3", "https://download.pytorch.org/whl/cpu",
    ),
}


def host_target() -> str:
    system = {"Darwin": "macos", "Windows": "windows", "Linux": "linux"}.get(platform.system())
    arch = "arm64" if platform.machine().lower() in {"arm64", "aarch64"} else "x64"
    target = f"{system}-{arch}" if system else ""
    if target not in TARGETS:
        raise RuntimeError(f"Unsupported stem-runtime host: {platform.system()} {platform.machine()}")
    return target


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_python(target: Target) -> Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    archive_name = (
        f"cpython-{PYTHON_VERSION}+{PYTHON_RELEASE}-{target.archive_target}"
        "-install_only_stripped.tar.gz"
    )
    archive = CACHE / archive_name
    if archive.exists() and sha256(archive) == target.archive_sha256:
        return archive
    archive.unlink(missing_ok=True)
    partial = archive.with_suffix(archive.suffix + ".part")
    partial.unlink(missing_ok=True)
    url = f"https://github.com/astral-sh/python-build-standalone/releases/download/{PYTHON_RELEASE}/{archive_name}"
    print(f"Downloading portable Python {PYTHON_VERSION} for {target.name}...")
    request = urllib.request.Request(url, headers={"User-Agent": "Amethyst stem-runtime builder"})
    with urllib.request.urlopen(request) as source, partial.open("wb") as destination:
        shutil.copyfileobj(source, destination, 1024 * 1024)
    actual = sha256(partial)
    if actual != target.archive_sha256:
        partial.unlink(missing_ok=True)
        raise RuntimeError(f"Portable Python SHA-256 mismatch: expected {target.archive_sha256}, got {actual}")
    partial.replace(archive)
    return archive


def download_bs_roformer_sources() -> dict[str, Path]:
    source_cache = CACHE / "bs-roformer" / BS_ROFORMER_SOURCE_COMMIT
    source_cache.mkdir(parents=True, exist_ok=True)
    downloaded = {}
    for file_name, expected_sha256 in BS_ROFORMER_SOURCES.items():
        target = source_cache / file_name
        if not target.exists() or sha256(target) != expected_sha256:
            target.unlink(missing_ok=True)
            partial = target.with_suffix(target.suffix + ".part")
            partial.unlink(missing_ok=True)
            url = (
                "https://raw.githubusercontent.com/ZFTurbo/Music-Source-Separation-Training/"
                f"{BS_ROFORMER_SOURCE_COMMIT}/models/bs_roformer/{file_name}"
            )
            print(f"Downloading pinned BS-RoFormer source {file_name}...")
            request = urllib.request.Request(url, headers={"User-Agent": "Amethyst stem-runtime builder"})
            with urllib.request.urlopen(request) as source, partial.open("wb") as destination:
                shutil.copyfileobj(source, destination)
            actual = sha256(partial)
            if actual != expected_sha256:
                partial.unlink(missing_ok=True)
                raise RuntimeError(
                    f"BS-RoFormer source SHA-256 mismatch for {file_name}: "
                    f"expected {expected_sha256}, got {actual}"
                )
            partial.replace(target)
        downloaded[file_name] = target
    return downloaded


def safe_extract(archive: Path, destination: Path) -> None:
    destination = destination.resolve()
    with tarfile.open(archive, "r:gz") as bundle:
        for member in bundle.getmembers():
            resolved = (destination / member.name).resolve()
            if destination != resolved and destination not in resolved.parents:
                raise RuntimeError(f"Unsafe archive entry: {member.name}")
        bundle.extractall(destination)


def run(command: list[str], *, environment: dict[str, str] | None = None) -> None:
    print("+ " + " ".join(command))
    subprocess.run(command, check=True, env=environment)


def install_packages(runtime: Path, target: Target) -> None:
    python = runtime / target.python_relative
    python.chmod(python.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    pip = [str(python), "-m", "pip", "install", "--disable-pip-version-check", "--no-cache-dir", "--no-compile"]
    torch_version, torchaudio_version = torch_versions(target)
    if target.torch_index:
        run(pip + [
            "--index-url", target.torch_index,
            f"torch=={torch_version}+cpu", f"torchaudio=={torchaudio_version}+cpu",
        ])
    else:
        run(pip + [f"torch=={torch_version}", f"torchaudio=={torchaudio_version}"])
    run(pip + ["-r", str(ROOT / "requirements.txt")])


def torch_versions(target: Target) -> tuple[str, str]:
    if target.name == "macos-x64":
        return INTEL_MAC_PYTORCH_VERSION, INTEL_MAC_TORCHAUDIO_VERSION
    return PYTORCH_VERSION, TORCHAUDIO_VERSION


def prune(runtime: Path) -> None:
    # Keep pip: it is used later by the explicitly opt-in CUDA overlay installer.
    for directory in list(runtime.rglob("__pycache__")):
        shutil.rmtree(directory, ignore_errors=True)
    for bytecode in list(runtime.rglob("*.py[co]")):
        bytecode.unlink(missing_ok=True)
    for relative in ("include", "share/doc", "share/man"):
        shutil.rmtree(runtime / relative, ignore_errors=True)
    stdlib = runtime / "lib" / f"python{'.'.join(PYTHON_VERSION.split('.')[:2])}"
    for relative in ("idlelib", "tkinter", "turtledemo", "unittest/test", "test"):
        shutil.rmtree(stdlib / relative, ignore_errors=True)
    site_packages = stdlib / "site-packages"
    for relative in ("torch/include", "torch/share"):
        shutil.rmtree(site_packages / relative, ignore_errors=True)
    shutil.rmtree(stdlib / "ensurepip", ignore_errors=True)


def write_manifest(destination: Path, target: Target) -> None:
    torch_version, torchaudio_version = torch_versions(target)
    manifest = {
        "format": 1,
        "target": target.name,
        "python": PYTHON_VERSION,
        "pythonBuild": PYTHON_RELEASE,
        "torch": torch_version,
        "torchaudio": torchaudio_version,
        "demucs": "4.0.1",
        "bsRoFormerSource": BS_ROFORMER_SOURCE_COMMIT,
        "baseAcceleration": "mps" if target.name.startswith("macos-") else "cpu",
        "cudaBundled": False,
    }
    (destination / "runtime-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=sorted(TARGETS), default=host_target())
    args = parser.parse_args()
    target = TARGETS[args.target]
    output = OUTPUT_ROOT / target.name / "stems"
    if args.target != host_target():
        raise RuntimeError("The portable runtime must be built on its target operating system and architecture")

    archive = download_python(target)
    bs_roformer_sources = download_bs_roformer_sources()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="amethyst-stem-runtime-", dir=BUILD_ROOT) as temporary:
        staging = Path(temporary)
        safe_extract(archive, staging)
        extracted = staging / "python"
        if not extracted.is_dir():
            raise RuntimeError("Portable Python archive did not contain its expected python/ directory")
        runtime = staging / "runtime"
        extracted.replace(runtime)
        install_packages(runtime, target)
        prune(runtime)
        validation_environment = os.environ.copy()
        validation_environment["PYTHONDONTWRITEBYTECODE"] = "1"
        run(
            [
                str(runtime / target.python_relative), "-c",
                "import pkg_resources, torch, torchaudio; "
                "from demucs.apply import apply_model; "
                "from demucs.audio import convert_audio; "
                "from demucs.pretrained import get_model; "
                "import beartype, einops, packaging, rotary_embedding_torch; "
                "print(torch.__version__)",
            ],
            environment=validation_environment,
        )

        stems = staging / "stems"
        stems.mkdir()
        runtime.replace(stems / "runtime")
        shutil.copy2(ROOT / "amethyst_stems.py", stems / "amethyst_stems.py")
        models = stems / "models"
        bs_roformer = models / "bs_roformer"
        bs_roformer.mkdir(parents=True)
        (models / "__init__.py").write_text("", encoding="utf-8")
        (bs_roformer / "__init__.py").write_text("", encoding="utf-8")
        for file_name, source in bs_roformer_sources.items():
            shutil.copy2(source, bs_roformer / file_name)
        shutil.copy2(ROOT / "THIRD_PARTY_NOTICES.txt", stems / "THIRD_PARTY_NOTICES.txt")
        write_manifest(stems, target)
        bundled_environment = validation_environment.copy()
        bundled_environment["PYTHONPATH"] = str(stems)
        run(
            [
                str(stems / "runtime" / target.python_relative), "-c",
                "from models.bs_roformer.bs_roformer import BSRoformer; "
                "print('BS-RoFormer import OK')",
            ],
            environment=bundled_environment,
        )

        shutil.rmtree(output, ignore_errors=True)
        stems.replace(output)

    # Remove the layout used by early development builds. Nucleus only consumes
    # common/, <os>/ and <os>-<arch>/ directories below appResourcesRootDir.
    shutil.rmtree(OUTPUT_ROOT / "stems", ignore_errors=True)
    size = sum(path.stat().st_size for path in output.rglob("*") if path.is_file())
    print(f"Stem runtime ready at {output} ({size / 1024 / 1024:.1f} MiB unpacked)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
