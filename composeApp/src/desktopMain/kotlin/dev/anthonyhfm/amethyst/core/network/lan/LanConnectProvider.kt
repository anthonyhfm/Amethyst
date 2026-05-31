package dev.anthonyhfm.amethyst.core.network.lan

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectRole
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectSession
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectUser
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectionState
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import dev.anthonyhfm.amethyst.core.network.connect.decodeToConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.encodeToString
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * [AmethystConnectProvider] that communicates over a local-area network
 * using Ktor WebSockets (JSON wire format).
 */
class LanConnectProvider : AmethystConnectProvider() {

    companion object {
        const val SERVER_PORT = 7842
        const val WS_PATH = "/session"
    }

    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    private val connectedClients = ConcurrentHashMap<String, DefaultWebSocketSession>()
    private val sessionMutex = Mutex()

    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
    }

    private var clientSession: DefaultWebSocketSession? = null

    override suspend fun host(sessionName: String, localUser: ConnectUser): Result<ConnectSession> {
        return runCatching {
            updateConnectionState(ConnectionState.Connecting)

            val hostUser = localUser.copy(role = ConnectRole.HOST)
            val sessionId = generateId()
            val session = ConnectSession(
                id = sessionId,
                name = sessionName,
                host = hostUser,
                participants = listOf(hostUser)
            )

            updateLocalUser(hostUser)
            updateSession(session)

            startServer(session)

            updateConnectionState(ConnectionState.Connected(session))
            session
        }.onFailure { e ->
            updateConnectionState(ConnectionState.Error(e))
        }
    }

    override suspend fun join(address: String, localUser: ConnectUser): Result<ConnectSession> {
        return runCatching {
            updateConnectionState(ConnectionState.Connecting)

            val guestUser = localUser.copy(role = ConnectRole.GUEST)
            updateLocalUser(guestUser)

            connectToHost(address, guestUser)

            val placeholderSession = ConnectSession(
                id = "pending",
                name = address,
                host = ConnectUser("host", "Host", 0, ConnectRole.HOST),
                participants = listOf(guestUser)
            )
            updateSession(placeholderSession)
            updateConnectionState(ConnectionState.Connected(placeholderSession))
            placeholderSession
        }.onFailure { e ->
            updateConnectionState(ConnectionState.Error(e))
        }
    }

    override suspend fun leave() {
        val localId = localUser.value?.id
        val isHost = localUser.value?.role == ConnectRole.HOST

        if (isHost) {
            broadcastToClients(ConnectEvent.SessionEnded.encodeToString(), excludeId = null)
            server?.stop(500, 1000)
            server = null
            connectedClients.clear()
        } else {
            if (localId != null) {
                sendRaw(ConnectEvent.UserLeft(localId).encodeToString())
            }
            clientSession?.close(CloseReason(CloseReason.Codes.NORMAL, "User left"))
            clientSession = null
        }

        updateSession(null)
        updateLocalUser(null)
        updateConnectionState(ConnectionState.Disconnected())
    }

    override suspend fun send(event: ConnectEvent) {
        sendRaw(event.encodeToString())
    }

    override suspend fun sendToUser(userId: String, event: ConnectEvent) {
        val json = event.encodeToString()
        when (localUser.value?.role) {
            ConnectRole.HOST -> sessionMutex.withLock {
                connectedClients[userId]?.let { ws ->
                    runCatching { ws.send(Frame.Text(json)) }
                }
            }
            ConnectRole.GUEST -> sendRaw(json)
            null -> Unit
        }
    }

    private suspend fun sendRaw(json: String) {
        when (localUser.value?.role) {
            ConnectRole.HOST -> broadcastToClients(json, excludeId = null)
            ConnectRole.GUEST -> {
                clientSession?.send(Frame.Text(json))
            }
            null -> Unit
        }
    }

    private fun startServer(hostSession: ConnectSession) {
        server = embeddedServer(ServerCIO, port = SERVER_PORT) {
            install(ServerWebSockets)

            routing {
                webSocket(WS_PATH) {
                    handleIncomingClient(this, hostSession)
                }
            }
        }.start(wait = false)
    }

    private suspend fun handleIncomingClient(
        wsSession: DefaultWebSocketSession,
        hostSession: ConnectSession
    ) {
        var clientUserId: String? = null

        try {
            for (frame in wsSession.incoming) {
                if (frame !is Frame.Text) continue
                val json = frame.readText()

                val event = runCatching { json.decodeToConnectEvent() }.getOrNull() ?: continue

                if (event is ConnectEvent.UserJoined && clientUserId == null) {
                    clientUserId = event.user.id
                    sessionMutex.withLock {
                        connectedClients[clientUserId] = wsSession
                    }

                    handleEvent(event)

                    broadcastToClients(json, excludeId = clientUserId)

                    scope.launch(CoroutineName("full-state-sync-$clientUserId")) {
                        sendParticipantSnapshotTo(wsSession)
                        sendFullStateSyncTo(wsSession)
                    }
                } else {
                    handleEvent(event)
                    broadcastToClients(json, excludeId = clientUserId)
                }
            }
        } finally {
            val id = clientUserId ?: return
            sessionMutex.withLock { connectedClients.remove(id) }
            val leftEvent = ConnectEvent.UserLeft(id)
            handleEvent(leftEvent)
            broadcastToClients(leftEvent.encodeToString(), excludeId = id)
        }
    }

    private suspend fun sendParticipantSnapshotTo(wsSession: DefaultWebSocketSession) {
        session.value
            ?.participants
            .orEmpty()
            .forEach { user ->
                runCatching {
                    wsSession.send(Frame.Text(ConnectEvent.UserJoined(user).encodeToString()))
                }
            }
    }

    private suspend fun broadcastToClients(json: String, excludeId: String?) {
        sessionMutex.withLock {
            connectedClients.entries
                .filter { it.key != excludeId }
                .forEach { (_, ws) ->
                    runCatching { ws.send(Frame.Text(json)) }
                }
        }
    }

    private suspend fun sendFullStateSyncTo(wsSession: DefaultWebSocketSession) {
        delay(150)

        val syncEvent = buildFullStateSyncEvent() ?: return
        runCatching {
            wsSession.send(Frame.Text(syncEvent.encodeToString()))
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun buildFullStateSyncEvent(): ConnectEvent.FullStateSync? {
        return try {
            val workspaceRepo = dev.anthonyhfm.amethyst.workspace.WorkspaceRepository
            val data = workspaceRepo.saveWorkspace()
            val bytes = dev.anthonyhfm.amethyst.core.util.AmethystProtoBuf.encodeToByteArray(
                dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData.serializer(),
                data
            )
            ConnectEvent.FullStateSync(
                workspaceData = bytes,
                bpm = workspaceRepo.bpm.value,
                projectName = workspaceRepo.projectName.value ?: "",
                macros = workspaceRepo.macros.value
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun connectToHost(address: String, guestUser: ConnectUser) {
        scope.launch(CoroutineName("lan-client")) {
            try {
                httpClient.webSocket(
                    host = address,
                    port = SERVER_PORT,
                    path = WS_PATH
                ) {
                    clientSession = this

                    send(Frame.Text(ConnectEvent.UserJoined(guestUser).encodeToString()))

                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val json = frame.readText()
                            val event = runCatching { json.decodeToConnectEvent() }.getOrNull()
                                ?: continue
                            handleEvent(event)
                        }
                    } finally {
                        clientSession = null
                        if (connectionState.value is ConnectionState.Connected) {
                            updateConnectionState(ConnectionState.Disconnected("Connection lost"))
                        }
                    }
                }
            } catch (e: Exception) {
                clientSession = null
                updateConnectionState(ConnectionState.Error(e))
            }
        }
    }

    private fun generateId(): String {
        val chars = ('a'..'f') + ('0'..'9')
        fun seg(len: Int) = (1..len).map { chars.random() }.joinToString("")
        return "${seg(8)}-${seg(4)}-${seg(4)}-${seg(4)}-${seg(12)}"
    }
}
