# Keyframes

The Keyframes device triggers a custom, multi-frame LED animation upon receiving an incoming signal. It's most commonly used for creating complex light shows, drawn shapes, and intricate visual patterns.

![Keyframes device](res://keyframes.jpg)

*Keyframes device*

The device's interface features a single **Draw** button. Clicking this button switches the workspace into the Keyframes Editor mode. In this mode, you can paint colors onto virtual devices across multiple frames, adjust their individual timings, and set animation properties.

When a positive (non-black) LED signal enters the device, it plays back the pre-computed sequence of frames, emitting LED signals based on your drawn animation and timing. When an off signal (black LED) is received, it will stop the sequence if it is currently looping.

The way the device operates depends on the selected playback mode:
* **Mono** – Starts the animation playback. Any new incoming signal will stop currently running sequences, ensuring only the most recently triggered animation plays.
* **Poly** – Starts the animation independently for each incoming signal, allowing multiple animations to overlap and play concurrently without interrupting each other.
* **Loop** – Plays up to the configured Repeats count while the incoming signal is held. Releasing the pad stops playback early; a new press starts the count again. Converted projects using the explicit Continuous mode continue to loop without a repeat limit.

### Checkboxes & Settings

* **Root Key** – Acts as the anchor point for the animation. When a signal is received on a pad other than the root key, the entire animation is offset relative to the trigger pad's distance from the root key.
* **Isolate** – When enabled, the animation is strictly confined to the boundaries of the specific device (Launchpad) that triggered it. It prevents light effects from overflowing and overwriting LEDs on adjacent devices. Additionally, incoming signals forcefully clear all currently active animations on the device.
* **Wrap** – When enabled, any parts of the animation that extend beyond the device boundaries (or global bounds if Isolate is off) will wrap around and appear on the opposite side.
* **Infinity** – If enabled, the final frame of the animation will not emit off-signals when completed, leaving its LEDs persistently lit.
