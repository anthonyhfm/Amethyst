package dev.anthonyhfm.amethyst.home.nav

import kotlinx.serialization.Serializable

@Serializable
sealed interface HomeNavRoute {
    @Serializable
    data object Projects : HomeNavRoute

    @Serializable
    data object Browser : HomeNavRoute

    @Serializable
    data object Settings : HomeNavRoute
}
