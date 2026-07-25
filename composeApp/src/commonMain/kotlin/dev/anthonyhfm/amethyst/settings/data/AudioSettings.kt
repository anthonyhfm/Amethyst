package dev.anthonyhfm.amethyst.settings.data

import dev.anthonyhfm.amethyst.core.engine.echo.Echo

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*

object AudioSettings : SettingsGroup("Audio", Res.string.settings_audio_group_title) {
    const val SystemDefaultOutputDevice = "System Default"
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
        default = 128,
        options = listOf(64, 128, 256),
        codec = SettingCodec.Int,
        label = { "$it frames" },
        onUpdate = Echo::setPreferredBufferFrames,
    )

    val outputDevice: Setting.Select<String> = select(
        key = "echoOutputDevice",
        title = "Output Device",
        default = SystemDefaultOutputDevice,
        options = listOf(SystemDefaultOutputDevice) + Echo.outputDevices().distinct(),
        codec = SettingCodec.String,
        onUpdate = { device -> Echo.setPreferredOutputDevice(device.takeUnless { it == SystemDefaultOutputDevice }) },
    )
}
