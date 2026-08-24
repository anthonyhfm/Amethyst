# Layer Filter

The Layer Filter device filters out LED signals based on their assigned layer. It's most commonly used to isolate specific parts of a visual configuration or prevent certain signals from affecting downstream devices.

![Layer Filter device](res://layer_filter.jpg)

*Layer Filter device*

The device interface features two main dials:
* **Target** – Adjusts the central layer to filter by, ranging from -100 to 100.
* **Range** – Adjusts the acceptable range of layers around the target layer, ranging from 0 to 200.

When an incoming LED signal enters the device, it evaluates the layer property of each LED. If the Range dial is set to 0, only LEDs with a layer that exactly matches the Target dial's value will be allowed to pass through. If the Range is greater than 0, the device allows LEDs to pass if their layer falls within the window defined by the Target plus or minus the Range (inclusive). All other LEDs are filtered out and will not reach the next device in the chain.
