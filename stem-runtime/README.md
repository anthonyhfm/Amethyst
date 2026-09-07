# Amethyst stem runtime

The desktop application invokes `amethyst_stems.py` as an isolated Demucs v4
worker. Release builds bundle a platform-specific PyInstaller executable; the
`htdemucs` weights are deliberately downloaded by the application on first use.

For local development, create a Python 3.10 environment, install the matching
PyTorch and TorchAudio wheels, then install `requirements.txt`. The Kotlin app
automatically uses the script through `python3`; override that interpreter with
`AMETHYST_STEMS_PYTHON` or point directly at a worker with
`AMETHYST_STEMS_EXECUTABLE`.

Build the release worker with:

```shell
python stem-runtime/build.py
```

Demucs code is MIT licensed. The official `htdemucs` checkpoint is downloaded
from Meta's model host and is not bundled in this repository; consult the model
publisher's terms before use or redistribution. Release packages also include
`THIRD_PARTY_NOTICES.txt` next to the worker.
