package dev.anthonyhfm.amethyst.core.network.user

import com.russhwolf.settings.Settings
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectRole
import dev.anthonyhfm.amethyst.core.network.connect.AmethystConnectContract.ConnectUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object LocalUserRepository {

    private val settings = Settings()

    private val COLLABORATION_COLORS = listOf(
        0xFF4F86F7.toInt(),
        0xFFFF6B6B.toInt(),
        0xFF4ECDC4.toInt(),
        0xFFFFE66D.toInt(),
        0xFFA8E6CF.toInt(),
        0xFFFF8B94.toInt(),
        0xFFC7B8EA.toInt()
    )

    private val _localUser = MutableStateFlow(loadOrCreate())
    val localUser: StateFlow<ConnectUser> = _localUser.asStateFlow()

    fun setUsername(name: String) {
        settings.putString(KEY_USERNAME, name)
        val current = _localUser.value

        val color = if (current.color == colorForName(current.name)) colorForName(name) else current.color
        _localUser.value = current.copy(name = name, color = color)
        persistUser(_localUser.value)
    }

    fun setColor(color: Int) {
        settings.putInt(KEY_COLOR, color)
        _localUser.value = _localUser.value.copy(color = color)
    }

    private fun loadOrCreate(): ConnectUser {
        val id = settings.getStringOrNull(KEY_ID) ?: generateUserId().also {
            settings.putString(KEY_ID, it)
        }
        val name = settings.getStringOrNull(KEY_USERNAME) ?: ""
        val color = settings.getIntOrNull(KEY_COLOR) ?: colorForName(name)
        return ConnectUser(id = id, name = name, color = color, role = ConnectRole.GUEST)
    }

    private fun persistUser(user: ConnectUser) {
        settings.putString(KEY_ID, user.id)
        settings.putString(KEY_USERNAME, user.name)
        settings.putInt(KEY_COLOR, user.color)
    }

    private fun generateUserId(): String {
        // Simple UUID-like stable identifier from random bytes
        val chars = ('a'..'f') + ('0'..'9')
        fun seg(len: Int) = (1..len).map { chars.random() }.joinToString("")
        return "${seg(8)}-${seg(4)}-${seg(4)}-${seg(4)}-${seg(12)}"
    }

    private fun colorForName(name: String): Int {
        if (name.isEmpty()) return COLLABORATION_COLORS.first()
        val index = kotlin.math.abs(name.hashCode()) % COLLABORATION_COLORS.size
        return COLLABORATION_COLORS[index]
    }

    private const val KEY_ID = "collaboration_user_id"
    private const val KEY_USERNAME = "collaboration_username"
    private const val KEY_COLOR = "collaboration_user_color"
}
