# Macro Control

The Macro Control device drives a workspace macro whenever its pad is triggered. It is also the canonical way to build pad-triggered live automation.

![Macro Control device](res://macro_control.jpg)

*Macro Control device*

* **Macro** selects the macro to control. If the workspace has no macro yet, use **Create macro**.
* **Value** sets the target from 0 to 127.
* The status line shows how many parameters the selected macro controls. **AUTO** appears when Value has a dial automation.

To create live automation:

1. Right-click **Value** and choose **Automate Value**.
2. Set the automation length, curve, start, end, and retrigger behavior.
3. Right-click a parameter dial on the Sample or audio effect and map it to the same macro.
4. Trigger the pad containing Macro Control.

The automation runs on the audio frame clock and holds its final value. Without Value automation, a trigger immediately applies the selected value. Signals continue through the chain normally.
