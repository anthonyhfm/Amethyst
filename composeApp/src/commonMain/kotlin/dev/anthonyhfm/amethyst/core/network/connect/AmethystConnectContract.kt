package dev.anthonyhfm.amethyst.core.network.connect

import dev.anthonyhfm.amethyst.devices.DeviceState
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

object AmethystConnectContract {

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
        data class DeviceStateChanged(val path: DevicePath, val state: @Polymorphic DeviceState) : ConnectEvent

        @Serializable
        data class CursorMoved(val userId: String, val x: Float, val y: Float) : ConnectEvent

        @Serializable
        data object Ping : ConnectEvent

        @Serializable
        data object Pong : ConnectEvent
    }

    sealed interface ConnectionState {
        data object Idle : ConnectionState
        data object Connecting : ConnectionState
        data class Connected(val session: ConnectSession) : ConnectionState
        data class Disconnected(val reason: String? = null) : ConnectionState
        data class Error(val cause: Throwable) : ConnectionState
    }
}
