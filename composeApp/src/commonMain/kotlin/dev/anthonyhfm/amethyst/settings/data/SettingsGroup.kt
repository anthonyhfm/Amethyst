package dev.anthonyhfm.amethyst.settings.data

import androidx.compose.runtime.Composable
import dev.anthonyhfm.amethyst.core.util.Platform
import dev.anthonyhfm.amethyst.core.util.platform
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

abstract class SettingsGroup(
    private val rawTitle: String,
    val titleRes: StringResource? = null,
) {
    val title: String @Composable get() = titleRes?.let { stringResource(it) } ?: rawTitle
    val displayTitle: String get() = rawTitle

    private val _settings = mutableListOf<Setting<*>>()
    val settings: List<Setting<*>>
        get() = _settings.filter { it.platformQuery(platform) }

    val allSettings: List<Setting<*>>
        get() = _settings

    protected fun toggle(
        key: String,
        title: String,
        titleRes: StringResource? = null,
        default: Boolean,
        platformQuery: (Platform) -> Boolean = { true },
        onUpdate: (Boolean) -> Unit = {},
    ): Setting.Toggle = Setting.Toggle(key, title, titleRes, default, platformQuery, onUpdate).also { _settings += it }

    protected fun <T> select(
        key: String,
        title: String,
        titleRes: StringResource? = null,
        default: T,
        options: List<T>,
        codec: SettingCodec<T>,
        label: (T) -> String = { it.toString() },
        platformQuery: (Platform) -> Boolean = { true },
        onUpdate: (T) -> Unit = {},
    ): Setting.Select<T> = Setting.Select(key, title, titleRes, default, options, codec, label, platformQuery, onUpdate).also { _settings += it }

    protected fun slider(
        key: String,
        title: String,
        titleRes: StringResource? = null,
        default: Float,
        range: ClosedFloatingPointRange<Float> = 0f..1f,
        platformQuery: (Platform) -> Boolean = { true },
        onUpdate: (Float) -> Unit = {},
    ): Setting.Slider = Setting.Slider(key, title, titleRes, default, range, platformQuery, onUpdate).also { _settings += it }

    protected fun text(
        key: String,
        title: String,
        titleRes: StringResource? = null,
        default: String = "",
        platformQuery: (Platform) -> Boolean = { true },
        onUpdate: (String) -> Unit = {},
    ): Setting.TextField = Setting.TextField(key, title, titleRes, default, platformQuery, onUpdate).also { _settings += it }
}
