package dev.anthonyhfm.amethyst.core.midi

import dev.anthonyhfm.amethyst.core.midi.devices.LaunchpadDeviceType
import dev.anthonyhfm.amethyst.ui.launchpad.viewport.ViewportLaunchpadX
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * End-to-end check of the desktop MIDI stack on macOS: real `DesktopMidiAccess` →
 * Rust CoreMIDI backend → [AmethystMidiManager], against the virtual Launchpad from
 * `nativeEngine/rust/examples/launchpad_sim.rs`.
 *
 * Opt-in only: runs when `AMETHYST_MIDI_E2E=1` is set and the simulator binary has
 * been built with `cargo build --example launchpad_sim`. Otherwise the test is a no-op.
 */
class CoreMidiLaunchpadSimE2ETest {

    private val simBinary: File? = listOf(
        "../nativeEngine/rust/target/debug/examples/launchpad_sim",
        "nativeEngine/rust/target/debug/examples/launchpad_sim",
    ).map(::File).firstOrNull { it.canExecute() }

    @Test
    fun `manager binds, releases and rebinds a virtual Launchpad over real CoreMIDI`() {
        if (System.getenv("AMETHYST_MIDI_E2E") != "1") {
            println("CoreMidiLaunchpadSimE2ETest skipped: AMETHYST_MIDI_E2E is not set")
            return
        }
        if (!System.getProperty("os.name").lowercase().contains("mac")) {
            println("CoreMidiLaunchpadSimE2ETest skipped: not running on macOS")
            return
        }
        val binary = simBinary ?: fail("launchpad_sim binary not built; run `cargo build --example launchpad_sim`")
        val access = platformMidiAccess ?: fail("no desktop MIDI access")

        val element = ViewportLaunchpadX()
        val manager = AmethystMidiManager(
            midiAccess = access,
            elementsProvider = { listOf(element) },
        )

        var sim = startSim(binary)
        try {
            manager.startAutoDetectLoop()

            waitUntil(15_000, "element bound to the simulated Launchpad X") {
                element.launchpadDevice != null &&
                    AmethystMidiManager.detectedDevices.value.any { it.type == LaunchpadDeviceType.LAUNCHPAD_X }
            }
            println("E2E: bound to ${element.savedInputPortName} (${element.savedMidiDeviceId})")

            sim.destroy()
            sim.waitFor()
            waitUntil(10_000, "element released after the simulator vanished") {
                element.launchpadDevice == null && AmethystMidiManager.detectedDevices.value.isEmpty()
            }
            println("E2E: released")

            sim = startSim(binary)
            waitUntil(15_000, "element re-bound after the simulator came back") {
                element.launchpadDevice != null
            }
            println("E2E: re-bound")
        } finally {
            sim.destroyForcibly()
            manager.close()
        }
    }

    private fun startSim(binary: File): Process =
        ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .start()

    private fun waitUntil(timeoutMs: Long, description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        fail("Timed out after ${timeoutMs}ms waiting for: $description")
    }
}
