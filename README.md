# Amethyst

![Amethyst Banner](logo.svg)

----

### What is Amethyst?

Amethyst is a Multiplatform App for creating and playing Launchpad Covers running on all Desktop targets (Windows, MacOS, Linux) and Mobile (Android, iOS)

## Features

### Launchpad Support
- Visual representation of your Launchpad layout on screen
- Supports **Launchpad Pro**, **Launchpad X**, **Launchpad Mini Mk3**, **Launchpad MK2**, **Launchpad Pro Mk3**, and **Mystrix**
- Real-time output via the Heaven rendering engine at configurable FPS

### Chain Effect System
27+ non-destructive, stackable effects per chain:

| Effect | Description                                                  |
|---|--------------------------------------------------------------|
| **Color** | Tint LEDs to a solid color                                   |
| **Gradient** | Render multi-color gradients                                 |
| **Opacity** | Adjust LED transparency                                      |
| **Adjust** | Fine-tune LED output parameters                              |
| **Blur** | Spread/blur LED patterns with configurable shape and curve   |
| **Color Filter** | Filter LEDs by color                                         |
| **Coordinate Filter** | Filter LEDs by pad position or region                        |
| **Layer / Layer Filter** | Organize LEDs into named layers                              |
| **Flip / Rotate** | Mirror or rotate the LED layout                              |
| **Offset / Shift** | Spatially shift content                                      |
| **Copy** | Duplicate content with grid, wrap, reverse, or infinite modes |
| **Delay** | Time-shift LED output                                        |
| **Hold** | Latch LED states                                             |
| **Loop** | Repeat LED output                                            |
| **Choke** | Gate or interrupt downstream content                         |
| **Clear** | Reset LEDs or Multi                                          |
| **Preview** | Preview a branch output inline                               |
| **Transmit** | Pass-through LED output                                      |
| **Group / Multi-Group** | Nest multiple devices into one unit                          |
| **Piano Roll** | Piano-roll–style frame editor for LED patterns               |
| **Keyframes** | Frame-based animation editor                                 |
| **Macro Control / Filter** | Route and filter via parametrizable macros                   |

### Workspace & Editing
- Macro system for parameterizing effect chains
- Multiple workspace modes: Layout, Chain, Timeline, Performance
- LAN collaboration *(experimental)*

### Timeline
- MIDI tracks and audio tracks with full clip editing
- Piano-roll workspace mode
- Automation lanes with configurable automation points
- Flexible grid/snapping with multiple division modes
- Integrated audio sample playback via the Echo engine

### Multi-Origin Import
- **Ableton Live** (`.als`) — including merged Apollo lighting data
- **Apollo Studio** (`.approj`)
- **UniPad** (zipped project)
- Custom **palette file** support for color mapping
- Automatic format detection for zipped projects

### Desktop (Windows, macOS, Linux)
- Discord Rich Presence with optional project/state display
- In-app update checker
- macOS title bar customization
- Keyboard shortcut system

### 🚧 Mobile (Android & iOS) (work in progress) 🚧
- Adaptive UI (iOS Liquid Glass / Google Material 3)
- Full workspace editing on-device

![Rainbow Buttons](rainbowbuttons.svg)

### Credits

- [Mat1jaczyyy](https://github.com/mat1jaczyyy) for creating the Heaven Engine that was ported to Amethyst
- [Kaskobi](https://kaskobi.com) for allowing Amethyst to use MicroLight 3 graphics 