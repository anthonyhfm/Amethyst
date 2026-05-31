package dev.anthonyhfm.amethyst.core.network.connect

import dev.anthonyhfm.amethyst.core.engine.elements.Chain
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ChainPath
import dev.anthonyhfm.amethyst.core.network.connect.DevicePathSegment.ChainStep
import dev.anthonyhfm.amethyst.core.network.connect.DevicePathSegment.ChokeStep
import dev.anthonyhfm.amethyst.core.network.connect.DevicePathSegment.GroupStep
import dev.anthonyhfm.amethyst.core.network.connect.DevicePathSegment.PreprocessStep
import dev.anthonyhfm.amethyst.devices.GenericChainDevice
import dev.anthonyhfm.amethyst.devices.effects.choke.ChokeChainDevice
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDevice
import dev.anthonyhfm.amethyst.devices.effects.multi.MultiGroupChainDevice
import dev.anthonyhfm.amethyst.workspace.WorkspaceRepository

fun ChainPath.resolveRootChain(): Chain = when (this) {
    ChainPath.LIGHTS -> WorkspaceRepository.lightsChain
    ChainPath.SAMPLING -> WorkspaceRepository.samplingChain
}

fun ChainPath.resolveChain(path: DevicePath = DevicePath(emptyList())): Chain? =
    resolveRootChain().resolveChain(path)

fun Chain.toChainAddress(): ChainAddress? {
    WorkspaceRepository.lightsChain.pathOfChain(this)?.let { return ChainAddress(ChainPath.LIGHTS, it) }
    WorkspaceRepository.samplingChain.pathOfChain(this)?.let { return ChainAddress(ChainPath.SAMPLING, it) }
    return null
}

data class ChainAddress(
    val chainPath: ChainPath,
    val parentPath: DevicePath
)

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
                            is MultiGroupChainDevice -> device.preprocessChain.devices.value
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

fun Chain.resolveChain(path: DevicePath): Chain? {
    if (path.segments.isEmpty()) return this

    var currentChain = this
    var i = 0
    while (i < path.segments.size) {
        val deviceIndex = (path.segments.getOrNull(i) as? ChainStep)?.index ?: return null
        val device = currentChain.devices.value.getOrNull(deviceIndex) ?: return null
        val chainSegment = path.segments.getOrNull(i + 1) ?: return null

        currentChain = when (chainSegment) {
            is GroupStep -> when (device) {
                is GroupChainDevice -> device.state.value.groups.getOrNull(chainSegment.groupIndex)?.chain
                is MultiGroupChainDevice -> device.state.value.groups.getOrNull(chainSegment.groupIndex)?.chain
                else -> null
            }

            is ChokeStep -> (device as? ChokeChainDevice)?.state?.value?.chain
            is PreprocessStep -> (device as? MultiGroupChainDevice)?.preprocessChain
            is ChainStep -> null
        } ?: return null

        i += 2
    }

    return currentChain
}

fun Chain.pathOf(target: GenericChainDevice<*>): DevicePath? {
    return findPath(target, emptyList())
}

fun Chain.pathOfChain(target: Chain): DevicePath? {
    if (this === target) return DevicePath(emptyList())
    return findChainPath(target, emptyList())
}

private fun Chain.findChainPath(
    target: Chain,
    prefix: List<DevicePathSegment>
): DevicePath? {
    devices.value.forEachIndexed { index, device ->
        val stepToDevice = prefix + ChainStep(index)

        if (device is GroupChainDevice) {
            device.state.value.groups.forEachIndexed { groupIndex, group ->
                val chainPath = stepToDevice + GroupStep(groupIndex)
                if (group.chain === target) return DevicePath(chainPath)
                group.chain.findChainPath(target, chainPath)?.let { return it }
            }
        }

        if (device is MultiGroupChainDevice) {
            device.state.value.groups.forEachIndexed { groupIndex, group ->
                val chainPath = stepToDevice + GroupStep(groupIndex)
                if (group.chain === target) return DevicePath(chainPath)
                group.chain.findChainPath(target, chainPath)?.let { return it }
            }

            val preprocessPath = stepToDevice + PreprocessStep
            if (device.preprocessChain === target) return DevicePath(preprocessPath)
            device.preprocessChain.findChainPath(target, preprocessPath)?.let { return it }
        }

        if (device is ChokeChainDevice) {
            val chainPath = stepToDevice + ChokeStep
            if (device.state.value.chain === target) return DevicePath(chainPath)
            device.state.value.chain.findChainPath(target, chainPath)?.let { return it }
        }
    }

    return null
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

            val path = device.preprocessChain.findPath(target, stepToDevice + PreprocessStep)

            if (path != null) return path
        }

        if (device is ChokeChainDevice) {
            val path = device.state.value.chain.findPath(target, stepToDevice + ChokeStep)
            if (path != null) return path
        }
    }

    return null
}
