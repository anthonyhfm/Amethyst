# Layer

The Layer device assigns a specific depth layer index and blending properties to incoming LED signals. It's most commonly used for managing complex overlapping light animations by dictating which signals appear above or below others and how their colors blend together.

![Layer device](res://layer.jpg)

*Layer device*

The interface features two numerical step dials and a dropdown menu to configure the layer attributes:
* **Layer Dial**: Adjusts the specific Z-index layer for the incoming signal, allowing values from -100 to 100. Signals with a higher layer index will render above signals with lower ones.
* **Range Dial**: Sets the blending range depth from 1 to 200. This dial dictates how far down through the layers the chosen blending mode affects, and it is disabled when the device is set to the Normal blending mode.
* **Mode Dropdown**: Selects the blending operation to apply when the signal overlaps with lower layers.

When an LED signal enters the device, its internal layer index is updated to the value set by the Layer dial. The device also applies the selected blending mode and range to the signal, ensuring that these properties affect how the signal renders down the chain.

The way the device operates depends on the selected mode:
* **Normal** – The signal is drawn over underlying layers normally without complex color interaction. The Range parameter is ignored.
* **Multiply** – The signal's color values are multiplied with underlying layers within the specified range, typically darkening the overlapped regions.
* **Screen** – The signal's color values are screened onto underlying layers within the specified range, typically lightening the overlapped regions.
* **Mask** – The signal acts as a visual mask, hiding or revealing underlying layers based on the signal's bounds and intensity over the specified range.
