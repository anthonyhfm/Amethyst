package dev.anthonyhfm.amethyst.conversion.apollo.data

import dev.anthonyhfm.amethyst.conversion.apollo.ApolloConverter
import dev.anthonyhfm.amethyst.devices.effects.group.GroupChainDeviceState
import dev.anthonyhfm.amethyst.devices.effects.group.data.Group
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
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

        // Set version BEFORE decoding so ApolloDataResolver version gates work correctly
        ApolloConverter.version = version

        val project = resolver.readNextType(reader) as ApolloModel.Project

        val lights = when {
            project.tracks.isEmpty() -> StateChain(devices = emptyList())
            project.tracks.size == 1 -> StateChain(
                devices = project.tracks.first().chain.devices.map {
                    ApolloAdapter.resolveAdapter(it.device)
                }
            )
            else -> StateChain(
                devices = listOf(
                    GroupChainDeviceState(
                        groups = project.tracks.mapIndexed { index, track ->
                            Group(
                                name = track.name.ifBlank { "Track ${index + 1}" },
                                stateChain = StateChain(
                                    devices = track.chain.devices.map {
                                        ApolloAdapter.resolveAdapter(it.device)
                                    }
                                )
                            )
                        }
                    )
                )
            )
        }

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

        return SavableWorkspaceData(
            title = "Apollo converted project",
            author = project.author,
            settings = WorkspaceSettings(bpm = project.bpm.toDouble()),
            lights = lights,
            launchpadDevices = launchpadDevices
        )
    }
}