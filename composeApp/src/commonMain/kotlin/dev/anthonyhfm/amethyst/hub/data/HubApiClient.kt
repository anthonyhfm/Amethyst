package dev.anthonyhfm.amethyst.hub.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class HubApiException(
    val errorCode: String,
    val statusCode: Int,
    val retryAfterSeconds: Long? = null,
) : Exception("Amethyst Hub request failed ($statusCode): $errorCode")

class HubApiClient internal constructor(
    baseUrl: String,
    bearerToken: String?,
    refreshToken: String?,
    private val onSessionChanged: ((HubSessionTokens?) -> Unit)?,
    internal val http: HttpClient,
) {
    constructor(
        baseUrl: String = DEFAULT_BASE_URL,
        bearerToken: String? = null,
        refreshToken: String? = null,
        onSessionChanged: ((HubSessionTokens?) -> Unit)? = null,
    ) : this(baseUrl, bearerToken, refreshToken, onSessionChanged, createHubHttpClient())

    internal val baseUrl = baseUrl.trimEnd('/')
    private val refreshMutex = Mutex()
    private val sessionStateLock = SynchronizedObject()
    private var accessTokenIssuedAt: TimeMark? = null
    private var accessTokenLifetimeSeconds: Long? = null
    private var sessionStore: HubSessionStore? = null
    private var pendingSession: HubSessionTokens? = null
    private var hasPendingSessionWrite = false
    private var sessionGeneration = 0L
    private val storageErrors = MutableStateFlow<String?>(null)

    val sessionStorageErrors: StateFlow<String?> = storageErrors.asStateFlow()

    var sessionStorageError: String? = null
        private set(value) {
            field = value
            storageErrors.value = value
        }

    var sessionRestorePending = false
        private set

    var bearerToken: String? = bearerToken
        get() = synchronized(lock = sessionStateLock) { field }
        set(value) {
            synchronized(lock = sessionStateLock) {
                field = value
                sessionGeneration++
                accessTokenIssuedAt = null
                accessTokenLifetimeSeconds = null
            }
        }

    var refreshToken: String? = refreshToken
        get() = synchronized(lock = sessionStateLock) { field }
        set(value) {
            synchronized(lock = sessionStateLock) {
                field = value
                sessionGeneration++
            }
        }

    val isAuthenticated: Boolean
        get() = synchronized(lock = sessionStateLock) { bearerToken != null && refreshToken != null }

    fun restoreSession(tokens: HubSessionTokens) {
        synchronized(lock = sessionStateLock) {
            bearerToken = tokens.accessToken
            refreshToken = tokens.refreshToken
        }
    }

    internal fun attachSessionStore(store: HubSessionStore) {
        sessionStore = store
        sessionRestorePending = true
        retrySessionStorage()
    }

    fun retrySessionStorage(): Boolean = synchronized(lock = sessionStateLock) {
        if (hasPendingSessionWrite) {
            persistSession(tokens = pendingSession)
        } else if (sessionRestorePending) {
            try {
                val restored = sessionStore?.load()
                if (restored != null) {
                    restoreSession(tokens = restored)
                }
                sessionRestorePending = false
                sessionStorageError = null
            } catch (error: Exception) {
                sessionStorageError = storageErrorMessage(error = error)
            }
        }

        sessionStorageError == null
    }

    private fun persistSession(tokens: HubSessionTokens?) {
        pendingSession = tokens
        hasPendingSessionWrite = true

        try {
            onSessionChanged?.invoke(tokens)
            pendingSession = null
            hasPendingSessionWrite = false
            sessionStorageError = null
        } catch (error: Exception) {
            sessionStorageError = storageErrorMessage(error = error)
        }
    }

    private fun storageErrorMessage(error: Exception): String =
        if (error is HubSessionStorageException) {
            error.message ?: SESSION_STORAGE_ERROR
        } else {
            SESSION_STORAGE_ERROR
        }

    private fun requireSessionStorage() {
        if (!retrySessionStorage()) {
            throw HubSessionStorageException(message = sessionStorageError ?: SESSION_STORAGE_ERROR)
        }
    }

    fun clearSession() {
        synchronized(lock = sessionStateLock) {
            bearerToken = null
            refreshToken = null
            sessionRestorePending = false
            persistSession(tokens = null)
        }
    }

    fun resolveUrl(pathOrUrl: String): String = when {
        pathOrUrl.startsWith("https://") || pathOrUrl.startsWith("http://") -> pathOrUrl
        pathOrUrl.startsWith("/api/projects/") -> "$baseUrl${pathOrUrl.removePrefix("/api")}"
        pathOrUrl.startsWith('/') -> "$baseUrl$pathOrUrl"
        else -> "$baseUrl/$pathOrUrl"
    }

    internal fun acceptAuthentication(result: HubAuthResult) {
        val access = result.accessToken ?: return
        val refresh = result.refreshToken ?: return
        applySession(
            tokens = HubSessionTokens(
                accessToken = access,
                refreshToken = refresh,
                expiresIn = result.expiresIn ?: DEFAULT_ACCESS_TOKEN_TTL
            )
        )
    }

    suspend fun refreshSession(): HubAuthResult {
        return refreshMutex.withLock {
            requireSessionStorage()
            val token = refreshToken
                ?: throw HubApiException("authentication_required", HttpStatusCode.Unauthorized.value)
            requestRefresh(token = token)
        }
    }

    private fun applySession(tokens: HubSessionTokens) {
        synchronized(lock = sessionStateLock) {
            sessionRestorePending = false
            bearerToken = tokens.accessToken
            refreshToken = tokens.refreshToken
            accessTokenIssuedAt = TimeSource.Monotonic.markNow()
            accessTokenLifetimeSeconds = tokens.expiresIn
            persistSession(tokens = tokens)
        }
    }

    private fun accessTokenNearExpiry(): Boolean = synchronized(lock = sessionStateLock) {
        val issuedAt = accessTokenIssuedAt ?: return@synchronized false
        val lifetime = accessTokenLifetimeSeconds ?: return@synchronized false
        issuedAt.elapsedNow() >= (lifetime - REFRESH_SKEW_SECONDS).coerceAtLeast(0).seconds
    }

    private suspend fun freshAccessToken(force: Boolean, failedToken: String? = null): String? {
        val current = bearerToken
        if (!force && !accessTokenNearExpiry()) return current
        if (refreshToken == null) {
            return current
        }

        return refreshMutex.withLock {
            if (force && failedToken != null && bearerToken != failedToken) return@withLock bearerToken
            if (!force && !accessTokenNearExpiry()) return@withLock bearerToken

            requireSessionStorage()
            val token = refreshToken ?: return@withLock bearerToken
            requestRefresh(token = token)
            bearerToken
        }
    }

    private suspend fun requestRefresh(token: String): HubAuthResult {
        val generation = synchronized(lock = sessionStateLock) {
            if (token != refreshToken) {
                throw HubApiException(errorCode = "session_changed", statusCode = HttpStatusCode.Conflict.value)
            }
            sessionGeneration
        }
        try {
            val result = http.post("$baseUrl/v1/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(HubRefreshInput(token))
            }.hubBody<HubAuthResult>()
            synchronized(lock = sessionStateLock) {
                if (generation != sessionGeneration) {
                    throw HubApiException(errorCode = "session_changed", statusCode = HttpStatusCode.Conflict.value)
                }
                acceptAuthentication(result = result)
            }
            return result
        } catch (error: HubApiException) {
            synchronized(lock = sessionStateLock) {
                if (error.statusCode == HttpStatusCode.Unauthorized.value && generation == sessionGeneration) {
                    clearSession()
                }
            }
            throw error
        }
    }

    internal suspend fun authorized(request: suspend (String) -> HttpResponse): HttpResponse {
        val token = freshAccessToken(force = false)
            ?: throw HubApiException("authentication_required", HttpStatusCode.Unauthorized.value)
        val response = request(token)
        if (response.status != HttpStatusCode.Unauthorized || refreshToken == null) return response
        val refreshed = freshAccessToken(force = true, failedToken = token)
            ?: throw HubApiException("authentication_required", HttpStatusCode.Unauthorized.value)
        return request(refreshed)
    }

    internal suspend fun optionallyAuthorized(request: suspend (String?) -> HttpResponse): HttpResponse {
        val token = if (bearerToken != null) freshAccessToken(force = false) else null
        return request(token)
    }

    fun close() = http.close()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthonyhfm.dev"
        private const val DEFAULT_ACCESS_TOKEN_TTL = 300L
        private const val REFRESH_SKEW_SECONDS = 30L
        private const val SESSION_STORAGE_ERROR =
            "Your session could not be saved or restored. Keep Amethyst open and try again."
    }
}

private fun createHubHttpClient() = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        })
    }
}

internal fun HttpRequestBuilder.applyBearerAuth(token: String?) {
    if (token != null) headers.append(HttpHeaders.Authorization, "Bearer $token")
}

internal suspend inline fun <reified T> HttpResponse.hubBody(): T {
    if (status.isSuccess()) return body()
    val error = runCatching { body<HubErrorResponse>().error }.getOrNull() ?: "http_${status.value}"
    throw HubApiException(
        errorCode = error,
        statusCode = status.value,
        retryAfterSeconds = headers[HttpHeaders.RetryAfter]?.toLongOrNull(),
    )
}
