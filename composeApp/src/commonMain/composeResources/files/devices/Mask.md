# Mask

The Mask device combines two fixed LED chains. The Color chain supplies the output colors, while the Shape chain controls where and how strongly those colors are visible.

The two large buttons switch between the chains:

* **Color** – Produces the LED colors and output metadata.
* **Shape** – Produces a pixel mask. Its luminance and opacity determine the mask strength.

Only pixels produced by both chains are emitted. At each matching coordinate, the strongest Shape signal scales the opacity of every Color signal. Black or missing Shape pixels hide the corresponding Color output.
