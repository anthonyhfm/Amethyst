package dev.anthonyhfm.amethyst.conversion.apollo.data

import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData

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
        } else {
            println("Apollo version $version detected")
        }

        val project = resolver.readNextType(reader) as ApolloModel.Project

        return SavableWorkspaceData(
            title = "Apollo converted project",
            author = project.author,
            lights = StateChain(
                devices = project.tracks.first().chain.devices.map {
                    it.device.let { deviceModel ->
                        ApolloAdapter.resolveAdapter(deviceModel)
                    }
                }
            ),
            launchpadDevices = listOf(
                SavableWorkspaceData.SavableViewportLaunchpad(
                    positionX = 0f,
                    positionY = 0f,
                    type = SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType.LAUNCHPAD_PRO
                )
            )
        )
    }
}