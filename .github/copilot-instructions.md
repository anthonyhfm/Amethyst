# Amethyst – Copilot Instructions

Amethyst is a Kotlin Multiplatform (KMP) app for creating and playing Launchpad light-show performances on Desktop (JVM), Android, and iOS. It uses Compose Multiplatform for UI.

---

## Build & Run

All commands use the Gradle wrapper from the repo root.

| Task | Command |
|---|---|
| Run desktop app | `./gradlew :composeApp:run` |
| Build Android debug APK | `./gradlew :composeApp:assembleDebug` |
| Build desktop distributable | `./gradlew :composeApp:packageDistributionForCurrentOS` |
| Run all tests | `./gradlew :composeApp:allTests` |
| Run desktop tests only | `./gradlew :composeApp:desktopTest` |
| Run a single test class | `./gradlew :composeApp:desktopTest --tests "dev.anthonyhfm.amethyst.gem.GemRepositoryTest"` |
| Run Android unit tests | `./gradlew :composeApp:testDebugUnitTest` |

iOS is built via Xcode using the `iosApp/` project. The KMP framework is `ComposeApp` (static).

Desktop entry point: `dev.anthonyhfm.amethyst.MainKt` (`desktopMain/…/main.kt`).

Gradle wrapper version: **9.3.1**. Kotlin: **2.3.0**. Compose Multiplatform: **1.10.0**. Android min/target SDK: 29/36, JVM target: 17.

---

## Architecture Overview

There is one Gradle module (`:composeApp`) with these source sets:

- `commonMain` — all shared logic and Compose UI
- `desktopMain` — JVM-specific implementations
- `androidMain` — Android-specific implementations
- `iosMain` — iOS-specific implementations
- `commonTest` / `androidUnitTest` — tests

All source lives under `composeApp/src/` with the root package `dev.anthonyhfm.amethyst`.

### Top-level packages in `commonMain`

| Package | Purpose |
|---|---|
| `core/engine/heaven` | **Heaven** — singleton rendering engine that converts LED signals into Launchpad output at a configurable FPS using a coroutine-driven scheduler |
| `core/engine/echo` | Audio playback engine (platform-specific decoders via `expect`/`actual`) |
| `core/engine/elements` | `Signal`, `SignalReceiver`, `Chain` — the signal-flow primitives |
| `core/midi` | MIDI device access (`PlatformMidiAccess`, `AmethystMidiManager`); MIDI access is `expect`/`actual` per platform |
| `core/controls` | `UndoManager`, `SelectionManager`, `ShortcutManager`, clipboard, keyboard modifier state |
| `core/util` | `Platform` (sealed interface), `UUID`, `Zip`, `Version`, `StopWatch`, `DeviceCapabilities` — all `expect`/`actual` |
| `devices/` | Every effect in the chain is a `GenericChainDevice<State>` or `LEDChainDevice<State>` subclass; effects live under `devices/effects/`, audio under `devices/audio/` |
| `gem/` | Gem system — reusable visual node-graph programs; see below |
| `workspace/` | Active session state (`WorkspaceRepository` singleton), chain viewport, workspace modes |
| `timeline/` | Timeline/automation system with tracks, lanes, and automation evaluators |
| `ui/` | Shared Compose components, theme tokens, DnD infrastructure, custom Modifier extensions |
| `conversion/` | Format importers: Ableton (`.als`), Apollo (`.approj`) |
| `home/` | Home screen (project browser / launcher) |
| `settings/` | `SettingsDialog` (expect/actual; desktop opens a Swing dialog, Android uses Compose) |
| `desktop/` | Desktop platform features (Discord RPC, FlatLaf LAF, macOS title bar, about handler) |

---

## Signal / Chain Model

Signals (`Signal.LED`) flow through a `Chain` — an ordered list of `GenericChainDevice` instances. Each device:

1. Receives signals via `signalEnter(n: List<Signal>)`
2. Transforms them
3. Forwards via its `signalExit` lambda to the next device

`Chain.reroute()` must be called after the device list changes to rewire the `signalExit` lambdas. `Heaven` sits at the end of the signal path and schedules LED output to physical Launchpad devices.

---

## Device Pattern

Every chain effect is a class that extends `GenericChainDevice<State>` (or `LEDChainDevice<State>` for LED-only effects):

- `State` must be a `@Serializable` data class implementing `DeviceState`
- State is exposed as `MutableStateFlow<State>`
- Mutations call `pushStateChange(before, after)` — this auto-registers an undo action if the state actually changed
- Each device provides a `@Composable fun Content()` for its inline chain editor UI
- The device list and serialization registry live in `DeviceSerializationModule`

---

## Gem System

A **Gem** is a reusable, serializable visual node-graph program:

- `GemAsset` — top-level container (metadata + `GemDefinition`)
- `GemDefinition` — holds one or more `GemGraph` instances; `Gem.rootGraphId = "root"` is the entry point
- `GemGraph` — immutable data class with `nodes: List<GemNodeInstance>` and `connections: List<GemConnection>`; all mutations return a new copy (use `putNode`, `removeNode`, `connect`, `disconnect`, etc.)
- `GemNodeRegistry` — maps type IDs (`GemNodeTypeId`) to `GemNodeDescriptor`s; built-in types are in `GemBuiltInNodes`
- `GemCompiler` / `GemExecutor` — compiles a graph into an execution plan and runs it
- `GemRepository` (in `gem/data/`) — persists assets as JSON
- Schema versioning: `Gem.phase1SchemaVersion = 1.0.0`, `phase4SchemaVersion = 1.1.0`

---

## `expect`/`actual` Convention

Platform-specific behaviour is split across source sets using standard KMP `expect`/`actual`. The suffix in the filename mirrors the source set:

- `Foo.kt` in `commonMain` — `expect` declaration
- `Foo.desktop.kt` in `desktopMain` — `actual` for JVM desktop
- `Foo.android.kt` in `androidMain` — `actual` for Android
- `Foo.ios.kt` in `iosMain` — `actual` for iOS

Key `expect`/`actual` sites: `PlatformMidiAccess`, `Platform`, `UUID`, `Zip`, `AudioDecoder`, `AudioOutput`, `SortedList`, `DeviceCapabilities`, `MidiInputData`, `WorkspaceChainScroller`, `FileDrop`, several `Modifier` extensions in `ui/modifier/`.

Prefer `expect`/`actual` over `if (platform == …)` checks. The `platform` val (a `Platform` sealed interface) exists but its own docstring says it should only be used in rare cases.

---

## UI & Theming

- **Compose Multiplatform 1.10.0** with Material 3 (`org.jetbrains.compose.material3:material3`)
- UI primitives use `com.composeunstyled` (composables.com), not raw Material components — wrap with `AmethystTheme { }` and access design tokens via top-level `ThemeToken<Color>` vals (e.g. `background`, `foreground`, `primary`, `muted`, `destructive`, `selectionSurface`)
- Icons come from `com.composables:icons-lucide-cmp` (Lucide icon set)
- `AmethystTheme(darkMode = true)` is the default; it builds a theme from `buildTheme { }` blocks in `ui/theme/AmethystTheme.kt`

---

## State & Persistence

- **Settings**: `multiplatform-settings` (`GlobalSettings` object), no-arg factory
- **Serialization**: `kotlinx.serialization` — JSON for Gem assets, Protobuf available
- **File I/O**: `okio` + `filekit` (file dialogs cross-platform)
- **XML**: `io.github.pdvrieze.xmlutil` (for format conversion)
- No DI framework — dependencies are passed explicitly or accessed via Kotlin `object` singletons (`Heaven`, `UndoManager`, `WorkspaceRepository`, `AmethystMidiManager`, etc.)

---

## ViewModel Pattern

ViewModels extend `BaseViewModel<State, Event, Effect>` from `core/util/BaseViewModel.kt`:

- `State` is a data class held in `MutableStateFlow` (exposed as `StateFlow`)
- `Effect` is a one-shot side-effect emitted via `MutableSharedFlow`
- `Event` is the user-intent sealed interface; subclasses implement `onEvent(event: Event)`

Simpler screens without effects can extend `ViewModel` directly.

---

## Navigation

Navigation uses `org.jetbrains.androidx.navigation:navigation-compose`. The top-level `App()` composable in `App.kt` switches between `Home` and `Workspace` screens by holding a single `Boolean` state; deeper navigation inside each screen uses the nav-compose graph.

Route destinations are `@Serializable` sealed interfaces named `*NavRoute` (e.g. `HomeNavRoute` with objects like `HomeNavRoute.Recent`, `HomeNavRoute.Browser`, `HomeNavRoute.ProjectCreation`).
