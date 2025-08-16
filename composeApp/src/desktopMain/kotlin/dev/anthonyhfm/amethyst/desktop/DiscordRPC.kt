package dev.anthonyhfm.amethyst.desktop

import io.github.vyfor.kpresence.RichClient
import io.github.vyfor.kpresence.rpc.ActivityType

class DiscordRPC {
    val appId: Long = 1402215916573298869
    val client: RichClient = RichClient(appId)

    fun start() {
        try {
            client.connect()

            client.update {
                type = ActivityType.GAME
                details = "Private Beta 2"
                state = "Development Mode"

                assets {
                    largeImage = "amethyst_studio_logo"
                    largeText = "Amethyst"
                }
            }
        } catch (e: Exception) { /* Do nothing */ }
    }
}