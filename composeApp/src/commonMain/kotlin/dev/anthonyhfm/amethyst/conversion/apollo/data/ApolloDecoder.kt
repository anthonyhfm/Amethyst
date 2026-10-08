package dev.anthonyhfm.amethyst.conversion.apollo.data

import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.CoordinateFilterChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.coordinate_filter.LaunchpadPadFilter
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.chain.data.findMaxMacroIndex
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings

class ApolloDecoder(
    data: ByteArray,
) {
    private val MAX_APOLLO_VERSION = 32
    private val reader: ApolloBinaryReader = data.asApolloBinaryReader()
    val resolver = ApolloDataResolver()

    fun decode(): SavableWorkspaceData {
        reader.expectMagic()

        val version = reader.readInt32()

        if (version > MAX_APOLLO_VERSION) {
            error("Apollo version $version is not supported. Current max supported version is $MAX_APOLLO_VERSION")
        }

        ApolloConverter.version = version

        val project = resolver.readNextType(reader) as ApolloModel.Project

        val launchpadDevices = project.tracks.mapIndexed { index, track ->
            SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro(
                positionX = (index * 10).toFloat(),
                positionY = 0f
            )
        }.ifEmpty {
            listOf(
                SavableWorkspaceData.SavableViewportLaunchpad.LaunchpadPro(
                    positionX = 0f,
                    positionY = 0f
                )
            )
        }

        val launchpadIds = launchpadDevices.map { it.id }
        val trackChains = project.tracks.mapIndexed { index, track ->
            val launchpadId = launchpadIds[index]
            val imported = ApolloAdapter.resolveChain(model = track.chain).bindApolloLaunchpads(
                launchpadId = launchpadId,
                launchpadIds = launchpadIds,
            )
            val inputFilter = CoordinateFilterChainDeviceState(
                padFilters = if (track.enabled) {
                    (0..9).flatMap { x ->
                        (0..9).map { y -> LaunchpadPadFilter(launchpadId = launchpadId, localX = x, localY = y) }
                    }
                } else {
                    emptyList()
                },
            )
            imported.copy(
                devices = listOf(inputFilter) + imported.devices,
                mutedDeviceIndices = imported.mutedDeviceIndices.map { it + 1 },
            )
        }
        val lights = when (trackChains.size) {
            0 -> StateChain()
            1 -> trackChains.first()
            else -> StateChain(
                devices = listOf(
                    GroupChainDeviceState(
                        groups = project.tracks.mapIndexed { index, track ->
                            Group(
                                name = track.name.ifBlank { "Track ${index + 1}" },
                                stateChain = trackChains[index],
                            )
                        },
                    )
                ),
            )
        }

        val maxMacroIndex = lights.findMaxMacroIndex()
        val apolloMacros = project.macros.map { Macro(value = maxOf(0, it - 1)) }
        val macroCount = maxOf(apolloMacros.size, maxMacroIndex + 1, 1)
        val macros = List(macroCount) { apolloMacros.getOrElse(it) { Macro(0) } }

        return SavableWorkspaceData(
            title = "Apollo converted project",
            author = project.author,
            settings = WorkspaceSettings(bpm = project.bpm.toDouble()),
            lights = lights,
            launchpadDevices = launchpadDevices,
            macros = macros
        )
    }
}