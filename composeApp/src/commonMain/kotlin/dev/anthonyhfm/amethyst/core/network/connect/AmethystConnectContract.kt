package dev.anthonyhfm.amethyst.core.network.connect

import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import dev.anthonyhfm.amethyst.workspace.data.Macro
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData.SavableViewportLaunchpad.ViewportDeviceType
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

object AmethystConnectContract {
    @Serializable
    enum class ChainPath {
        LIGHTS,
        SAMPLING
    }


    enum class ConnectRole {
        HOST,
        GUEST
    }

    @Serializable
    data class ConnectUser(
        val id: String,
        val name: String,
        val color: Int,
        val role: ConnectRole
    )

    @Serializable
    data class ConnectSession(
        val id: String,
        val name: String,
        val host: ConnectUser,
        val participants: List<ConnectUser>
    )

    @Serializable
    sealed interface ConnectEvent {
        @Serializable
        data class UserJoined(val user: ConnectUser) : ConnectEvent

        @Serializable
        data class UserLeft(val userId: String) : ConnectEvent

        @Serializable
        data object SessionEnded : ConnectEvent

        @Serializable
        data class ChainStateChanged(val payload: StateChain) : ConnectEvent

        @Serializable
        data class DeviceStateChanged(
            val chainPath: ChainPath,
            val path: DevicePath,
            val state: @Polymorphic DeviceState
        ) : ConnectEvent

        @Serializable
        data class CursorMoved(val userId: String, val x: Float, val y: Float) : ConnectEvent

        @Serializable
        data class UserFocused(
            val userId: String,
            val focusedElementId: String?
        ) : ConnectEvent

        @Serializable
        data object Ping : ConnectEvent

        @Serializable
        data object Pong : ConnectEvent

        /**
         * Sent by the host to a newly joined client to synchronize the full workspace state.
         * [workspaceData] is a ProtoBuf-encoded [dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData].
         */
        @Serializable
        data class FullStateSync(
            val workspaceData: ByteArray,
            val bpm: Double,
            val projectName: String,
            val macros: List<Macro>
        ) : ConnectEvent {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is FullStateSync) return false
                return workspaceData.contentEquals(other.workspaceData) &&
                    bpm == other.bpm &&
                    projectName == other.projectName &&
                    macros == other.macros
            }

            override fun hashCode(): Int {
                var result = workspaceData.contentHashCode()
                result = 31 * result + bpm.hashCode()
                result = 31 * result + projectName.hashCode()
                result = 31 * result + macros.hashCode()
                return result
            }
        }

        @Serializable
        data class RequestResync(val userId: String) : ConnectEvent

        @Serializable
        data class ResyncResponse(
            val workspaceData: ByteArray,
            val bpm: Double,
            val projectName: String,
            val macros: List<Macro>
        ) : ConnectEvent {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is ResyncResponse) return false
                return workspaceData.contentEquals(other.workspaceData) &&
                    bpm == other.bpm &&
                    projectName == other.projectName &&
                    macros == other.macros
            }

            override fun hashCode(): Int {
                var result = workspaceData.contentHashCode()
                result = 31 * result + bpm.hashCode()
                result = 31 * result + projectName.hashCode()
                result = 31 * result + macros.hashCode()
                return result
            }
        }

        @Serializable
        data class BpmChanged(val bpm: Double) : ConnectEvent

        @Serializable
        data class ProjectNameChanged(val name: String) : ConnectEvent

        @Serializable
        data class MacrosChanged(val macros: List<Macro>) : ConnectEvent

        @Serializable
        data class GridTypeChanged(val gridTypeKey: String) : ConnectEvent

        @Serializable
        data class DevicePlaced(
            val deviceId: String,
            val deviceType: ViewportDeviceType,
            val positionX: Float,
            val positionY: Float
        ) : ConnectEvent

        @Serializable
        data class DeviceRemoved(val deviceId: String) : ConnectEvent

        @Serializable
        data class DeviceMoved(
            val deviceId: String,
            val positionX: Float,
            val positionY: Float
        ) : ConnectEvent

        @Serializable
        data class DevicePropertyChanged(
            val deviceId: String,
            val property: DeviceProperty,
            val value: String
        ) : ConnectEvent

        @Serializable
        enum class DeviceProperty {
            ROTATION
        }

        @Serializable
        data class ChainDevicePlaced(
            val chainPath: ChainPath,
            val parentPath: DevicePath,
            val deviceId: String,
            val atIndex: Int,
            val initialState: @Polymorphic DeviceState
        ) : ConnectEvent

        @Serializable
        data class ChainDeviceRemoved(
            val chainPath: ChainPath,
            val parentPath: DevicePath,
            val deviceId: String
        ) : ConnectEvent

        @Serializable
        data class ChainDeviceMoved(
            val chainPath: ChainPath,
            val fromParentPath: DevicePath,
            val toParentPath: DevicePath,
            val deviceId: String,
            val fromIndex: Int,
            val toIndex: Int
        ) : ConnectEvent

        @Serializable
        data class GroupCreated(
            val chainPath: ChainPath,
            val devicePath: DevicePath,
            val groupIndex: Int,
            val groupName: String
        ) : ConnectEvent

        @Serializable
        data class GroupRemoved(
            val chainPath: ChainPath,
            val devicePath: DevicePath,
            val groupIndex: Int
        ) : ConnectEvent

        @Serializable
        data class GroupReordered(
            val chainPath: ChainPath,
            val devicePath: DevicePath,
            val fromIndex: Int,
            val toIndex: Int
        ) : ConnectEvent

        @Serializable
        data class GroupRenamed(
            val chainPath: ChainPath,
            val devicePath: DevicePath,
            val groupIndex: Int,
            val newName: String
        ) : ConnectEvent
    }

    sealed interface ConnectionState {
        data object Idle : ConnectionState
        data object Connecting : ConnectionState
        data class Connected(val session: ConnectSession) : ConnectionState
        data class Disconnected(val reason: String? = null) : ConnectionState
        data class Error(val cause: Throwable) : ConnectionState
    }
}
