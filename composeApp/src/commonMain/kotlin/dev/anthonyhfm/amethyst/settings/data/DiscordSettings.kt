package dev.anthonyhfm.amethyst.settings.data

import dev.anthonyhfm.amethyst.desktop.DiscordRPCManager

import amethyst.composeapp.generated.resources.Res
import amethyst.composeapp.generated.resources.*

object DiscordSettings : SettingsGroup("Discord", Res.string.settings_discord_group_title) {
    val enableDiscordRPC: Setting.Toggle = toggle(
        key = "enableDiscordRPC",
        title = "Discord Rich Presence",
        titleRes = Res.string.settings_discord_rpc_title,
        default = true,
        onUpdate = { DiscordRPCManager.toggleRPC(it) },
    )

    val showCurrentProject: Setting.Toggle = toggle(
        key = "showCurrentProject",
        title = "Show Current Project",
        titleRes = Res.string.settings_discord_show_project_title,
        default = true,
        onUpdate = { DiscordRPCManager.forceUpdate() },
    )

    val showCurrentWorkspaceState: Setting.Toggle = toggle(
        key = "showCurrentWorkspaceState",
        title = "Show Current Workspace State",
        titleRes = Res.string.settings_discord_show_workspace_state_title,
        default = true,
        onUpdate = { DiscordRPCManager.forceUpdate() },
    )
}
