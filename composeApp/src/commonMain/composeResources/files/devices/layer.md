# Layer

The **Layer** device controls the rendering order and blending behavior of LED signals as they pass through the chain. By assigning signals to different layers and choosing a blending mode, you can create complex visual compositions where multiple effects interact with each other.

## Parameters

### Layer (–20 to 20)

Sets the layer index for all signals passing through. Signals on higher layers are rendered on top of signals on lower layers. The default value is **0**.

- **Negative values** push signals behind other effects.
- **Positive values** bring signals to the front.
- Use multiple Layer devices at different points in your chain to create depth.

### Range (0 to 20)

Defines how many neighboring layers are affected by the blending mode. A range of **0** means only the exact layer is affected. Higher values blend across more layers. This parameter is **disabled** when the blending mode is set to *Normal*.

### Mode

The blending mode determines how signals on this layer interact with signals on other layers.

| Mode | Description |
|------|-------------|
| **Normal** | Default behavior — signals simply stack by layer order. Higher layers cover lower layers. Range has no effect. |
| **Multiply** | Darkens the result by multiplying colors. Useful for shadow and dimming effects. |
| **Screen** | Brightens the result — the inverse of Multiply. Great for glow and highlight effects. |
| **Mask** | Uses one layer's brightness to control the visibility of another. Creates cutout and reveal effects. |

## Typical Use Cases

- **Foreground / Background separation:** Place a Layer device before your background pattern (layer –5) and another before your foreground effect (layer 5) to control which visuals appear on top.
- **Blending effects:** Set one layer to *Screen* mode to make bright pads glow through a darker base layer.
- **Masking:** Use a simple pad pattern as a mask to reveal or hide parts of a more complex animation underneath.

## Tips

- The Layer device only modifies the **layer**, **blending mode**, and **blending range** metadata of signals — it does not alter colors or positions.
- Combine multiple Layer devices in a chain to build layered compositions that go far beyond simple sequential processing.
