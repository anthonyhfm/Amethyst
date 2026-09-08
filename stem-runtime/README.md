# Amethyst stem runtime

The desktop application invokes `amethyst_stems.py` as an isolated local
worker supporting Demucs and BS-RoFormer models. Release builds bundle a
platform-specific portable Python runtime; model weights are deliberately
downloaded only after the user selects a model and confirms the download.

For local development, build the host runtime below. The Kotlin app discovers
it under `composeApp/build/generated/stemRuntime`; alternatively point directly
at a compatible worker with `AMETHYST_STEMS_EXECUTABLE`.

Build the release worker with:

```shell
python stem-runtime/build.py
```

Demucs code is MIT licensed. The official `htdemucs`, `htdemucs_ft`,
`hdemucs_mmi`, `mdx`, `mdx_extra`, `mdx_q`, and `mdx_extra_q` checkpoints are
downloaded from Meta's model host and are not bundled in this repository;
consult the model publisher's terms before use or redistribution.

The BS-RoFormer implementation is pinned to ZFTurbo's MIT-licensed source at
commit `aef04b2e52fb3beaf25e333199f5a7236e628e7b`. Its four-stem checkpoint is
downloaded from release `v1.0.12` and is not bundled. That checkpoint does not
have a clearly stated separate license. Release packages include
`THIRD_PARTY_NOTICES.txt` next to the worker.
