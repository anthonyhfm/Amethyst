package dev.anthonyhfm.amethyst.core.network.connect

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.network.connect.DevicePathSegment.ChainStep
import dev.anthonyhfm.amethyst.core.network.connect.DevicePathSegment.ChokeStep
import dev.anthonyhfm.amethyst.core.network.connect.DevicePathSegment.GroupStep
import dev.anthonyhfm.amethyst.core.network.connect.DevicePathSegment.PreprocessStep
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice

fun Chain.resolve(path: DevicePath): GenericChainDevice<*>? {
    val segments = path.segments

    if (segments.isEmpty()) return null

    var currentDevices = devices.value

    var i = 0
    while (i < segments.size) {
        when (val segment = segments[i]) {
            is ChainStep -> {
                val device = currentDevices.getOrNull(segment.index) ?: return null

                if (i == segments.lastIndex) return device

                i++
                when (val nextSegment = segments[i]) {
                    is GroupStep -> {
                        currentDevices = when (device) {
                            is GroupChainDevice -> {
                                device.state.value.groups.getOrNull(nextSegment.groupIndex)?.chain?.devices?.value
                                    ?: return null
                            }
                            is MultiGroupChainDevice -> {
                                device.state.value.groups.getOrNull(nextSegment.groupIndex)?.chain?.devices?.value
                                    ?: return null
                            }
                            else -> return null
                        }
                    }

                    is ChokeStep -> {
                        currentDevices = when (device) {
                            is ChokeChainDevice -> device.state.value.chain.devices.value
                            else -> return null
                        }
                    }

                    is PreprocessStep -> {
                        currentDevices = when (device) {
                            is MultiGroupChainDevice -> {
                                device.state.value.preprocessChain.unpack().devices.value
                            }
                            else -> return null
                        }
                    }

                    is ChainStep -> return null
                }
            }

            else -> return null
        }
        i++
    }

    return null
}

fun Chain.pathOf(target: GenericChainDevice<*>): DevicePath? {
    return findPath(target, emptyList())
}

private fun Chain.findPath(
    target: GenericChainDevice<*>,
    prefix: List<DevicePathSegment>
): DevicePath? {
    devices.value.forEachIndexed { index, device ->
        val stepToDevice = prefix + ChainStep(index)

        if (device === target) {
            return DevicePath(stepToDevice)
        }

        if (device is GroupChainDevice) {
            device.state.value.groups.forEachIndexed { groupIndex, group ->
                val path = group.chain.findPath(target, stepToDevice + GroupStep(groupIndex))
                if (path != null) return path
            }
        }

        if (device is MultiGroupChainDevice) {
            device.state.value.groups.forEachIndexed { groupIndex, group ->
                val path = group.chain.findPath(target, stepToDevice + GroupStep(groupIndex))
                if (path != null) return path
            }

            val preprocessChain = device.state.value.preprocessChain.unpack()
            val path = preprocessChain.findPath(target, stepToDevice + PreprocessStep)

            if (path != null) return path
        }

        if (device is ChokeChainDevice) {
            val path = device.state.value.chain.findPath(target, stepToDevice + ChokeStep)
            if (path != null) return path
        }
    }

    return null
}
