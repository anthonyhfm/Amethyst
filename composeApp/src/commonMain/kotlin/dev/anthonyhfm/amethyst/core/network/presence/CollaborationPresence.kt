package dev.anthonyhfm.amethyst.core.network.presence

import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectEvent
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectRole
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectUser
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class RemoteCursor(
    val user: ConnectUser,
    val x: Float,
    val y: Float,
    val lastSeen: Long
)

data class ActivityToast(
    val id: String,
    val message: String,
    val userColor: Int,
    val type: ActivityToastType,
    val shownAt: Long
)

enum class ActivityToastType {
    USER_JOINED,
    USER_LEFT,
    SESSION_ENDED
}

object CollaborationPresence {
    private val _remoteCursors = MutableStateFlow<Map<String, RemoteCursor>>(emptyMap())
    val remoteCursors: StateFlow<Map<String, RemoteCursor>> = _remoteCursors.asStateFlow()

    private val _remoteFocuses = MutableStateFlow<Map<String, String?>>(emptyMap())
    val remoteFocuses: StateFlow<Map<String, String?>> = _remoteFocuses.asStateFlow()

    private val _activityToasts = MutableStateFlow<List<ActivityToast>>(emptyList())
    val activityToasts: StateFlow<List<ActivityToast>> = _activityToasts.asStateFlow()

    private var provider: AmethystConnectProvider? = null
    private var eventsJob: Job? = null
    private var sessionJob: Job? = null
    private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val knownUsers = mutableMapOf<String, ConnectUser>()
    private var lastCursorSentAt = 0L
    private var lastFocusedElementId: String? = null

    fun attach(provider: AmethystConnectProvider, scope: CoroutineScope) {
        detach()
        this.provider = provider
        provider.session.value?.participants?.forEach { knownUsers[it.id] = it }
        sessionJob = scope.launch {
            provider.session.collect { session ->
                session?.participants.orEmpty().forEach { user ->
                    knownUsers[user.id] = user
                }
            }
        }
        eventsJob = scope.launch {
            provider.events.collect(::handleEvent)
        }
    }

    fun detach() {
        eventsJob?.cancel()
        eventsJob = null
        sessionJob?.cancel()
        sessionJob = null
        provider = null
        knownUsers.clear()
        lastCursorSentAt = 0L
        lastFocusedElementId = null
        _remoteCursors.value = emptyMap()
        _remoteFocuses.value = emptyMap()
        _activityToasts.value = emptyList()
    }

    @OptIn(ExperimentalTime::class)
    fun sendCursorMoved(x: Float, y: Float) {
        val activeProvider = provider ?: return
        val userId = activeProvider.localUser.value?.id ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastCursorSentAt < 33L) return

        lastCursorSentAt = now
        activeProvider.scopeSend(ConnectEvent.CursorMoved(userId = userId, x = x, y = y))
    }

    fun sendFocusedElement(focusedElementId: String?) {
        if (focusedElementId == lastFocusedElementId) return

        val activeProvider = provider ?: return
        val userId = activeProvider.localUser.value?.id ?: return
        lastFocusedElementId = focusedElementId
        activeProvider.scopeSend(
            ConnectEvent.UserFocused(
                userId = userId,
                focusedElementId = focusedElementId
            )
        )
    }

    fun dismissToast(id: String) {
        _activityToasts.update { toasts -> toasts.filterNot { it.id == id } }
    }

    private fun handleEvent(event: ConnectEvent) {
        val activeProvider = provider ?: return
        val localUserId = activeProvider.localUser.value?.id

        when (event) {
            is ConnectEvent.CursorMoved -> {
                if (event.userId == localUserId) return
                val user = activeProvider.session.value
                    ?.participants
                    ?.firstOrNull { it.id == event.userId }
                    ?: knownUsers[event.userId]
                    ?: fallbackUser(event.userId)
                knownUsers[user.id] = user

                _remoteCursors.update {
                    it + (event.userId to RemoteCursor(
                        user = user,
                        x = event.x,
                        y = event.y,
                        lastSeen = nowMillis()
                    ))
                }
            }

            is ConnectEvent.UserFocused -> {
                if (event.userId == localUserId) return
                _remoteFocuses.update { it + (event.userId to event.focusedElementId) }
            }

            is ConnectEvent.UserJoined -> {
                knownUsers[event.user.id] = event.user
                if (event.user.id != localUserId) {
                    showToast(
                        message = "${event.user.name.ifBlank { "Someone" }} joined",
                        userColor = event.user.color,
                        type = ActivityToastType.USER_JOINED
                    )
                }
            }

            is ConnectEvent.UserLeft -> {
                val user = knownUsers[event.userId]
                    ?: activeProvider.session.value
                    ?.participants
                    ?.firstOrNull { it.id == event.userId }

                _remoteCursors.update { it - event.userId }
                _remoteFocuses.update { it - event.userId }
                knownUsers.remove(event.userId)
                showToast(
                    message = "${user?.name?.ifBlank { null } ?: "Someone"} left",
                    userColor = user?.color ?: 0xFF888888.toInt(),
                    type = ActivityToastType.USER_LEFT
                )
            }

            ConnectEvent.SessionEnded -> {
                _remoteCursors.value = emptyMap()
                _remoteFocuses.value = emptyMap()
                showToast(
                    message = "Sharing session ended",
                    userColor = 0xFF888888.toInt(),
                    type = ActivityToastType.SESSION_ENDED
                )
            }

            else -> Unit
        }
    }

    private fun showToast(
        message: String,
        userColor: Int,
        type: ActivityToastType
    ) {
        val toast = ActivityToast(
            id = "${nowMillis()}-${message.hashCode()}",
            message = message,
            userColor = userColor,
            type = type,
            shownAt = nowMillis()
        )
        _activityToasts.update { (it + toast).takeLast(4) }
    }

    @OptIn(ExperimentalTime::class)
    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private fun fallbackUser(userId: String): ConnectUser =
        ConnectUser(
            id = userId,
            name = "Guest",
            color = fallbackColor(userId),
            role = ConnectRole.GUEST
        )

    private fun fallbackColor(userId: String): Int {
        val palette = listOf(
            0xFFE11D48.toInt(),
            0xFF2563EB.toInt(),
            0xFF16A34A.toInt(),
            0xFFF59E0B.toInt(),
            0xFF9333EA.toInt(),
            0xFF0891B2.toInt()
        )
        return palette[kotlin.math.abs(userId.hashCode()) % palette.size]
    }

    private fun AmethystConnectProvider.scopeSend(event: ConnectEvent) {
        sendScope.launch {
            runCatching { send(event) }
        }
    }
}
