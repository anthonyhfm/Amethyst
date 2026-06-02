package dev.anthonyhfm.amethyst.core.network.connect

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectSession
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectUser
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class AmethystConnectProvider {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _localUser = MutableStateFlow<ConnectUser?>(null)
    val localUser: StateFlow<ConnectUser?> = _localUser.asStateFlow()

    private val _session = MutableStateFlow<ConnectSession?>(null)
    val session: StateFlow<ConnectSession?> = _session.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<ConnectEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ConnectEvent> = _events.asSharedFlow()

    protected fun updateLocalUser(user: ConnectUser?) {
        _localUser.value = user
    }

    protected fun updateSession(session: ConnectSession?) {
        _session.value = session
    }

    protected fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    protected fun emitEvent(event: ConnectEvent) {
        scope.launch { _events.emit(event) }
    }

    protected fun handleEvent(event: ConnectEvent) {
        when (event) {
            is ConnectEvent.UserJoined -> {
                val current = _session.value ?: return
                val host = if (event.user.role == AmethystConnectContract.ConnectRole.HOST) {
                    event.user
                } else {
                    current.host
                }
                val participants = (current.participants.filterNot { it.id == event.user.id } + event.user)
                    .distinctBy { it.id }
                    .sortedWith(compareBy<ConnectUser> { it.role != AmethystConnectContract.ConnectRole.HOST }.thenBy { it.name })
                _session.value = current.copy(
                    host = host,
                    participants = participants
                )
            }

            is ConnectEvent.UserLeft -> {
                val current = _session.value ?: return
                _session.value = current.copy(
                    participants = current.participants.filter { it.id != event.userId }
                )
            }

            is ConnectEvent.SessionEnded -> {
                _session.value = null
                _connectionState.value = ConnectionState.Disconnected()
            }

            is ConnectEvent.SessionSnapshot -> {
                _session.value = event.session
            }

            else -> Unit
        }

        scope.launch { _events.emit(event) }
    }

    abstract suspend fun host(sessionName: String, localUser: ConnectUser): Result<ConnectSession>

    abstract suspend fun join(address: String, localUser: ConnectUser): Result<ConnectSession>

    abstract suspend fun leave()

    abstract suspend fun send(event: ConnectEvent)

    open suspend fun sendToUser(userId: String, event: ConnectEvent) {
        send(event)
    }
}
