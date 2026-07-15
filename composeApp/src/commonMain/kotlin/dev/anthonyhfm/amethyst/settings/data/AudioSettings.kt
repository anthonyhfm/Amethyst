package dev.anthonyhfm.amethyst.settings.data

import dev.anthonyhfm.amethyst.core.engine.echo.Echo

object AudioSettings : SettingsGroup("Audio") {
    const val SystemDefaultOutputDevice = "System Default"
    val masterVolume: Setting.Slider = slider(
        key = "masterVolume",
        title = "Master Volume",
        default = 1f,
        range = 0f..1f,
    )

    val renderBufferFrames: Setting.Select<Int> = select(
        key = "echoRenderBufferFrames",
        title = "Buffer Size",
        default = 256,
        options = listOf(128, 256, 512),
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
