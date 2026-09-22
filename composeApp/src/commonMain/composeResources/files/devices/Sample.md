# Sample

The Sample device plays a loaded audio file when its pad receives a MIDI note. Use it for one-shot sounds, drum samples, and loops.

![Sample device](placeholder)

*Sample device*

Load or drag in an audio file, then use the waveform handles to set the active sample region. The waveform volume envelope remains available for detailed, sample-local shaping.

The compact controls are split into pages:

* **Envelope** – Gain, Fade In, and Fade Out. Fade ranges automatically follow the active sample region.
* **Playback** – Pan, playback mode, and choke group.
* **Loop** – Loop bounds. This page appears only in Gate Loop mode.

**One Shot** plays through the active region after a pad press. **Gate Loop** repeats the loop region while the pad is held and releases when the pad is released. Samples in the same non-zero choke group stop each other, which is useful for open and closed hi-hats.

Gain, Pan, Fade In, and Fade Out use Amethyst's normal dial menu. Right-click a dial to add an automation or map it to a macro.

The device is monophonic: retriggering it replaces its current voice with a short click-safe transition.
