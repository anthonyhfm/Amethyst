package dev.anthonyhfm.amethyst.settings.data

import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*

object AudioSettings : SettingsGroup("Audio", Res.string.settings_audio_group_title) {
    const val SystemDefaultOutputDevice = "System Default"
    private val availableOutputDevices = Echo.outputDevices()
    private val outputDeviceLabels = availableOutputDevices.associate { it.id to it.displayName }
    val masterVolume: Setting.Slider = slider(
        key = "masterVolume",
        title = "Master Volume",
        titleRes = Res.string.settings_audio_master_volume_title,
        default = 1f,
        range = 0f..1f,
        onUpdate = Echo::setMasterGain,
    )

    val renderBufferFrames: Setting.Select<Int> = select(
        key = "echoRenderBufferFrames",
        title = "Buffer Size",
        titleRes = Res.string.settings_audio_buffer_size_title,
        default = 64,
        options = listOf(64, 128, 256),
        codec = SettingCodec.Int,
        label = { "$it frames" },
        onUpdate = Echo::setPreferredBufferFrames,
    )

    val outputDevice: Setting.Select<String> = select(
        key = "echoOutputDevice",
        title = "Output Device",
        titleRes = Res.string.settings_audio_output_device_title,
        default = SystemDefaultOutputDevice,
        options = listOf(SystemDefaultOutputDevice) + availableOutputDevices.map { it.id }.distinct(),
        codec = SettingCodec.String,
        label = { id -> outputDeviceLabels[id] ?: id },
        onUpdate = { device -> Echo.setPreferredOutputDevice(device.takeUnless { it == SystemDefaultOutputDevice }) },
    )

    val exclusiveMode: Setting.Toggle? = if (platform is Platform.Desktop.Windows) {
        toggle(
            key = "echoExclusiveMode",
            title = "WASAPI Exclusive Mode",
            titleRes = Res.string.settings_audio_exclusive_mode_title,
            default = false,
            onUpdate = Echo::setExclusiveMode,
        )
    } else {
        null
    }
}
