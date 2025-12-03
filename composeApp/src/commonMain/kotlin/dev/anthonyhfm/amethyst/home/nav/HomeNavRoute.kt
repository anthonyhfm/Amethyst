package dev.anthonyhfm.amethyst.home.nav

import kotlinx.serialization.Serializable

@Serializable
sealed interface HomeNavRoute {
    @Serializable
    data object Recent : HomeNavRoute

    @Serializable
    data object Browser : HomeNavRoute

    @Serializable
    data object Settings : HomeNavRoute

    @Serializable
    data object About : HomeNavRoute

    @Serializable
    data object ProjectCreation : HomeNavRoute

    @Serializable
    data class ProjectEdit(
        val projectPath: String
    ) : HomeNavRoute

    @Serializable
    data class AbletonImportWizard(
        val liveSetPath: String
    ) : HomeNavRoute

    @Serializable
    data class LoadingScreen(
        val text: String
    ) : HomeNavRoute
}