package dev.anthonyhfm.amethyst.core.network.lan

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectSession
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * UDP-based LAN session discovery.
 */
object LanDiscoveryService {

    private const val BROADCAST_PORT = 7843
    private const val BROADCAST_INTERVAL_MS = 2_000L
    private const val SESSION_TIMEOUT_MS = 6_000L
    private const val PAYLOAD_PREFIX = "AMETHYST_SESSION:"
    private const val MAX_PACKET_SIZE = 8192

    private val broadcastJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class BroadcastPayload(
        val session: ConnectSession,
        val port: Int
    )

    private var broadcastJob: Job? = null
    private val broadcastScope = CoroutineScope(Dispatchers.IO + CoroutineName("lan-broadcast"))

    fun startBroadcasting(session: ConnectSession, hostPort: Int = LanConnectProvider.SERVER_PORT) {
        broadcastJob?.cancel()
        broadcastJob = broadcastScope.launch {
            val payload = PAYLOAD_PREFIX + broadcastJson.encodeToString(
                BroadcastPayload.serializer(),
                BroadcastPayload(session, hostPort)
            )
            val bytes = payload.toByteArray(Charsets.UTF_8)
            val broadcastAddress = InetAddress.getByName("255.255.255.255")

            val socket = DatagramSocket().apply { broadcast = true }
            try {
                while (isActive) {
                    val packet = DatagramPacket(bytes, bytes.size, broadcastAddress, BROADCAST_PORT)
                    runCatching { socket.send(packet) }
                    delay(BROADCAST_INTERVAL_MS)
                }
            } finally {
                socket.close()
            }
        }
    }

    fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null
    }

    fun discoverSessions(): Flow<List<DiscoveredSession>> = callbackFlow {
        val socket = DatagramSocket(BROADCAST_PORT).apply {
            soTimeout = 3_000
        }

        val seen = mutableMapOf<String, DiscoveredSession>()

        val receiveJob = launch(Dispatchers.IO + CoroutineName("lan-discovery-recv")) {
            val buf = ByteArray(MAX_PACKET_SIZE)
            while (isActive) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: java.net.SocketTimeoutException) {
                    pruneAndEmit(seen)
                    continue
                }

                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (!text.startsWith(PAYLOAD_PREFIX)) continue

                val jsonPart = text.removePrefix(PAYLOAD_PREFIX)
                val payload = runCatching {
                    broadcastJson.decodeFromString(BroadcastPayload.serializer(), jsonPart)
                }.getOrNull() ?: continue

                val discovered = DiscoveredSession(
                    session = payload.session,
                    hostAddress = packet.address.hostAddress ?: continue,
                    hostPort = payload.port,
                    discoveredAt = System.currentTimeMillis()
                )

                seen[discovered.session.id] = discovered
                pruneAndEmit(seen)
            }
        }

        awaitClose {
            receiveJob.cancel()
            runCatching { socket.close() }
        }
    }

    private suspend fun kotlinx.coroutines.channels.ProducerScope<List<DiscoveredSession>>.pruneAndEmit(
        seen: MutableMap<String, DiscoveredSession>
    ) {
        val now = System.currentTimeMillis()
        seen.entries.removeIf { (_, s) -> now - s.discoveredAt > SESSION_TIMEOUT_MS }
        trySend(seen.values.toList())
    }
}

data class DiscoveredSession(
    val session: ConnectSession,
    val hostAddress: String,
    val hostPort: Int,
    val discoveredAt: Long
)
