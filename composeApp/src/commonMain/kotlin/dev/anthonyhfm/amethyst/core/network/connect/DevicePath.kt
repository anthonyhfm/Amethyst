package dev.anthonyhfm.amethyst.core.network.connect

import kotlinx.serialization.Serializable

@Serializable
sealed interface DevicePathSegment {

    @Serializable
    data class ChainStep(val index: Int) : DevicePathSegment

    @Serializable
    data class GroupStep(val groupIndex: Int) : DevicePathSegment

    @Serializable
    data object ChokeStep : DevicePathSegment

    @Serializable
    data object PreprocessStep : DevicePathSegment
}

@Serializable
data class DevicePath(val segments: List<DevicePathSegment>)
