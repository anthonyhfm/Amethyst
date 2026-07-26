# Copy

The Copy device duplicates incoming signals and translates them to new coordinates based on a list of user-defined offsets. It's most commonly used to create animated trails across a launchpad.

![Copy device](res://copy.jpg)

*Copy device*

The UI is divided into two sections. On the left side, you can select the Mode and Grid Mode using dropdown menus. Below these are checkboxes to toggle Wrap, Reverse, and Infinite behaviors. There are also Timing and Gate controls to adjust the rhythm and duration of the copying sequence, accompanied by a Pinch graph that adjusts the acceleration curve of the timing. The Pinch graph also features a Bilateral toggle. On the right side, you can add offsets by clicking the '+' button. Each offset block displays its X and Y coordinates, and you can click the directional arrows to shift the offset up, down, left, or right. Right-clicking an offset toggles it into absolute positioning mode. In Interpolate mode, each offset features a dial to adjust the rotation angle. You can remove an offset by clicking its '-' button.

When a signal enters the device, it calculates the new coordinates by applying each configured offset to the incoming signal. It respects the chosen grid mode and isolation bounds. The device then triggers the duplicated signals according to the active mode—either immediately, sequentially over time using the timing controls, or randomly. The timing sequence can also be skewed by the pinch settings.

The way the device operates depends on the selected mode:
* **Static** – Duplicates the incoming signal and sends it to all configured offsets immediately at the same time.
* **Animate** – Creates a sequence where the signal is copied to each offset step-by-step according to the Timing and Gate settings.
* **Interpolate** – Animates the signal seamlessly across offsets by generating intermediate interpolated frames based on angles.
* **Hold Interpol.** – Operates like Interpolate, but only runs as long as the trigger button is held down. Releasing the button cancels playback and clears active interpolated signals.
* **Random Single** – Randomly picks one offset (or the original position) and translates the signal only to that specific location.
* **Random Loop** – Randomly jumps between the different offsets in a continuous loop sequence based on the specified timing.

If the Wrap checkbox is checked, copied signals that move beyond the launchpad's edge will wrap around to the opposite side depending on the Grid Mode.
If the Reverse checkbox is checked, the order of the offsets is played backwards during animated modes.
If the Infinite checkbox is checked, the animated copying sequence will loop endlessly instead of stopping after playing once.
