package dev.anthonyhfm.amethyst.home.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.anthonyhfm.amethyst.hub.data.HubAccount
import dev.anthonyhfm.amethyst.hub.data.HubAccountService
import dev.anthonyhfm.amethyst.hub.data.HubApiException
import dev.anthonyhfm.amethyst.hub.data.HubArtistProfileInput
import dev.anthonyhfm.amethyst.hub.data.HubAuthResult
import dev.anthonyhfm.amethyst.hub.data.HubAvatarInput
import dev.anthonyhfm.amethyst.hub.data.HubRepository
import dev.anthonyhfm.amethyst.hub.data.HubSessionStorageException
import dev.anthonyhfm.amethyst.hub.data.invalidateHubAvatar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import java.util.Base64

class DesktopHubAccount private constructor() {
    val repository = HubRepository(
        sessionStore = DesktopHubTokenStore()
    )

    private val service = HubAccountService(repository)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var account by mutableStateOf<HubAccount?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf(repository.client.sessionStorageError)
        private set

    var restoringSession by mutableStateOf(
        repository.client.isAuthenticated || repository.client.sessionRestorePending
    )
        private set

    var sessionStorageNeedsRetry by mutableStateOf(repository.client.sessionStorageError != null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var mfaChallenge by mutableStateOf<String?>(null)
        private set

    var sessionRevision by mutableIntStateOf(0)
        private set

    init {
        scope.launch {
            var previousStorageError: String? = null
            repository.client.sessionStorageErrors.collect { storageError ->
                sessionStorageNeedsRetry = storageError != null
                if (storageError != null) {
                    error = storageError
                } else if (error == previousStorageError) {
                    error = null
                }
                previousStorageError = storageError
            }
        }

        if (repository.client.isAuthenticated) {
            refresh()
        }
    }

    fun clearMessages() {
        error = null
        message = null
    }

    fun showError(value: String) {
        error = value
    }

    fun prepareAuth() {
        clearMessages()
        mfaChallenge = null
    }

    fun refresh() {
        if (!repository.client.isAuthenticated && !repository.client.sessionRestorePending &&
            repository.client.sessionStorageError == null
        ) {
            return
        }

        runAction {
            requireSessionStorage()
            if (repository.client.isAuthenticated) {
                acceptAccount(value = repository.getAccount.execute())
            }
        }
    }

    private fun requireSessionStorage() {
        if (!repository.client.retrySessionStorage()) {
            throw HubSessionStorageException(
                message = repository.client.sessionStorageError ?: "Your saved session could not be accessed. Try again."
            )
        }
    }

    fun signIn(
        username: String,
        password: String,
        register: Boolean = false,
        displayName: String = "",
        email: String = "",
    ) {
        runAction {
            requireSessionStorage()
            val result = if (register) {
                service.registerAndLogin(
                    username = username,
                    password = password,
                    displayName = displayName,
                    email = email,
                )
            } else {
                service.login(
                    username = username,
                    password = password,
                )
            }

            handleAuth(result)
        }
    }

    fun completeMfa(code: String) {
        val challenge = mfaChallenge ?: return

        runAction {
            handleAuth(
                service.completeMfa(
                    challenge = challenge,
                    code = code,
                )
            )
        }
    }

    fun requestReset(
        username: String,
        success: String = "If recovery is available, instructions will be sent shortly.",
    ) {
        runAction {
            repository.requestPasswordReset.execute(
                username = username.trim().replace("@", "")
            )

            message = success
        }
    }

    fun updateProfile(
        displayName: String,
        bio: String,
        onSuccess: () -> Unit = {},
    ) {
        runAction {
            acceptAccount(
                repository.updateArtistProfile.execute(
                    HubArtistProfileInput(
                        displayName = displayName,
                        bio = bio,
                    )
                )
            )

            message = "Profile updated."
            onSuccess()
        }
    }

    fun updateAvatar(bytes: ByteArray, mimeType: String) {
        runAction {
            val data = withContext(Dispatchers.Default) {
                Base64.getEncoder().encodeToString(bytes)
            }

            val updated = repository.setAccountAvatar.execute(
                input = HubAvatarInput(
                    data = data,
                    mimeType = mimeType,
                )
            )
            invalidateHubAvatar(previous = account?.avatarUrl, updated = updated.avatarUrl)
            acceptAccount(value = updated)

            message = "Avatar updated."
        }
    }

    fun removeAvatar() {
        runAction {
            val updated = repository.removeAccountAvatar.execute()
            invalidateHubAvatar(previous = account?.avatarUrl, updated = updated.avatarUrl)
            acceptAccount(value = updated)

            message = "Avatar removed."
        }
    }

    fun changePassword(
        current: String,
        replacement: String,
        onSuccess: () -> Unit = {},
    ) {
        val username = account?.username ?: return

        runAction {
            service.changePassword(
                username = username,
                current = current,
                replacement = replacement,
            )

            clearAccount()
            message = "Password changed. Please sign in again."
            onSuccess()
        }
    }

    fun changeEmail(
        password: String,
        email: String,
        code: String,
        success: String = "Check your new email address for a confirmation link.",
    ) {
        val username = account?.username ?: return

        runAction {
            service.changeEmail(
                username = username,
                password = password,
                email = email,
                code = code,
            )

            message = success
        }
    }

    fun removeEmail(
        password: String,
        code: String,
        success: String = "Email removed.",
    ) {
        val username = account?.username ?: return

        runAction {
            service.removeEmail(
                username = username,
                password = password,
                code = code,
            )

            acceptAccount(repository.getAccount.execute())
            message = success
        }
    }

    fun signOut() {
        runAction {
            try {
                service.signOut()
            } finally {
                clearAccount()
            }
        }
    }

    private suspend fun handleAuth(result: HubAuthResult) {
        mfaChallenge = result.challenge

        if (result.challenge != null) {
            return
        }

        acceptAccount(result.account ?: repository.getAccount.execute())
    }

    private fun acceptAccount(value: HubAccount) {
        if (account == null) {
            sessionRevision++
        }

        account = value
        mfaChallenge = null
        clearMessages()
    }

    private fun clearAccount() {
        val hadSession = account != null || repository.client.isAuthenticated

        repository.client.clearSession()
        account = null
        mfaChallenge = null

        if (hadSession) {
            sessionRevision++
        }
    }

    private fun runAction(block: suspend () -> Unit) {
        if (busy) {
            return
        }

        clearMessages()
        busy = true

        scope.launch {
            try {
                block()
            } catch (cause: Exception) {
                if (cause is HubApiException && cause.statusCode == 401 && !repository.client.isAuthenticated) {
                    clearAccount()
                }

                error = cause.message ?: cause.toString()
            } finally {
                sessionStorageNeedsRetry = repository.client.sessionStorageError != null
                restoringSession = repository.client.sessionRestorePending ||
                    (account == null && repository.client.isAuthenticated)
                repository.client.sessionStorageError?.let { value ->
                    error = value
                }
                busy = false
            }
        }
    }

    companion object {
        @Volatile
        private var instance: DesktopHubAccount? = null

        fun get(): DesktopHubAccount = instance ?: synchronized(this) {
            instance ?: DesktopHubAccount().also { instance = it }
        }
    }
}
