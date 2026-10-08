package dev.anthonyhfm.amethyst.hub.data

/** Platform-specific secure storage; session restore and updates remain in common Kotlin. */
interface HubSessionStore {
    fun load(): HubSessionTokens?
    fun save(tokens: HubSessionTokens?)
}

class HubSessionStorageException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)

class HubRepository internal constructor(val client: HubApiClient) {
    constructor(
        baseUrl: String = HubApiClient.DEFAULT_BASE_URL,
        sessionStore: HubSessionStore,
    ) : this(persistedHubClient(baseUrl, sessionStore))

    constructor(
        baseUrl: String = HubApiClient.DEFAULT_BASE_URL,
        bearerToken: String? = null,
        refreshToken: String? = null,
        onSessionChanged: ((HubSessionTokens?) -> Unit)? = null,
    ) : this(
        HubApiClient(
            baseUrl = baseUrl,
            bearerToken = bearerToken,
            refreshToken = refreshToken,
            onSessionChanged = onSessionChanged,
        )
    )

    val getHealth = GetHealthUseCase(client)
    val getHome = GetHomeUseCase(client)
    val search = SearchHubUseCase(client)

    val getAuthConfig = GetAuthConfigUseCase(client)
    val register = RegisterUseCase(client)
    val login = LoginUseCase(client)
    val completeMfa = CompleteMfaUseCase(client)
    val refreshSession = RefreshSessionUseCase(client)
    val requestPasswordReset = RequestPasswordResetUseCase(client)
    val resetPassword = ResetPasswordUseCase(client)
    val confirmEmail = ConfirmEmailUseCase(client)

    val getAccount = GetAccountUseCase(client)
    val updateArtistProfile = UpdateArtistProfileUseCase(client)
    val setAccountAvatar = SetAccountAvatarUseCase(client)
    val removeAccountAvatar = RemoveAccountAvatarUseCase(client)
    val getSessions = GetSessionsUseCase(client)
    val exportAccount = ExportAccountUseCase(client)
    val logout = LogoutUseCase(client)
    val changePassword = ChangePasswordUseCase(client)
    val changeEmail = ChangeEmailUseCase(client)
    val removeEmail = RemoveEmailUseCase(client)
    val setupTotp = SetupTotpUseCase(client)
    val confirmTotp = ConfirmTotpUseCase(client)
    val disableTotp = DisableTotpUseCase(client)
    val regenerateRecoveryCodes = RegenerateRecoveryCodesUseCase(client)
    val deleteAccount = DeleteAccountUseCase(client)

    val browseProjects = BrowseProjectsUseCase(client)
    val downloadPackage = DownloadProjectPackageUseCase(client)
    val downloadThumbnail = DownloadProjectThumbnailUseCase(client)
    val downloadOverride = DownloadProjectOverrideUseCase(client)
    val recordProjectView = RecordProjectViewUseCase(client)

    val browseArtists = BrowseArtistsUseCase(client)
    val getArtist = GetArtistUseCase(client)
    val getArtistProjects = GetArtistProjectsUseCase(client)
    val getArtistCollections = GetArtistCollectionsUseCase(client)
    val getPublishedProject = GetPublishedProjectUseCase(client)
    val getPublishedProjectById = GetPublishedProjectByIdUseCase(client)
    val downloadArtistAvatar = DownloadArtistAvatarUseCase(client)

    val getOwnedProjects = GetOwnedProjectsUseCase(client)
    val getLikedProjects = GetLikedProjectsUseCase(client)
    val getOwnedProject = GetOwnedProjectUseCase(client)
    val createProject = CreateProjectUseCase(client)
    val updateProject = UpdateProjectUseCase(client)
    val publishProject = PublishProjectUseCase(client)
    val unpublishProject = UnpublishProjectUseCase(client)
    val deleteProject = DeleteProjectUseCase(client)
    val uploadPackage = UploadProjectPackageUseCase(client)
    val uploadThumbnail = UploadProjectThumbnailUseCase(client)
    val deleteThumbnail = DeleteProjectThumbnailUseCase(client)
    val uploadOverride = UploadProjectOverrideUseCase(client)
    val deleteOverride = DeleteProjectOverrideUseCase(client)
    val toggleProjectLike = ToggleProjectLikeUseCase(client)

    val followArtist = FollowArtistUseCase(client)
    val unfollowArtist = UnfollowArtistUseCase(client)

    fun close() = client.close()
}

private fun persistedHubClient(baseUrl: String, store: HubSessionStore): HubApiClient {
    return HubApiClient(
        baseUrl = baseUrl,
        onSessionChanged = store::save,
    ).also { client ->
        client.attachSessionStore(store = store)
    }
}
