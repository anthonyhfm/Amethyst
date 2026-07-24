package dev.anthonyhfm.amethyst.settings.data

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class Setting<T>(
    val key: String,
    private val rawTitle: String,
    val titleRes: StringResource? = null,
    val default: T,
    private val codec: SettingCodec<T>,
    private val onUpdate: (T) -> Unit = {},
) {
    val title: String @Composable get() = titleRes?.let { stringResource(it) } ?: rawTitle

    private val _flow: MutableStateFlow<T> = MutableStateFlow(
        SettingsRepository.platformSettings.getStringOrNull(key)
            ?.let { runCatching { codec.decode(it) }.getOrNull() }
            ?: default
    )

    val flow: StateFlow<T> = _flow.asStateFlow()

    val value: T get() = _flow.value

    fun update(value: T) {
        SettingsRepository.platformSettings.putString(key, codec.encode(value))
        _flow.value = value
        onUpdate(value)
    }

    class Toggle(
        key: String,
        title: String,
        titleRes: StringResource? = null,
        default: Boolean,
        onUpdate: (Boolean) -> Unit = {},
    ) : Setting<Boolean>(key, title, titleRes, default, SettingCodec.Boolean, onUpdate)

    class Select<T>(
        key: String,
        title: String,
        titleRes: StringResource? = null,
        default: T,
        val options: List<T>,
        codec: SettingCodec<T>,
        val label: (T) -> String = { it.toString() },
        onUpdate: (T) -> Unit = {},
    ) : Setting<T>(key, title, titleRes, default, codec, onUpdate)

    class Slider(
        key: String,
        title: String,
        titleRes: StringResource? = null,
        default: Float,
        val range: ClosedFloatingPointRange<Float> = 0f..1f,
        onUpdate: (Float) -> Unit = {},
    ) : Setting<Float>(key, title, titleRes, default, SettingCodec.Float, onUpdate)

    class TextField(
        key: String,
        title: String,
        titleRes: StringResource? = null,
        default: String = "",
        onUpdate: (String) -> Unit = {},
    ) : Setting<String>(key, title, titleRes, default, SettingCodec.String, onUpdate)
}
