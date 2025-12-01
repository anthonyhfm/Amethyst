package dev.anthonyhfm.amethyst.settings.ui.views

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.anthonyhfm.amethyst.core.data.settings.GlobalSettings
import dev.anthonyhfm.amethyst.desktop.DiscordRPCManager
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsCategory
import dev.anthonyhfm.amethyst.settings.ui.components.SettingsItem

@Composable
fun DiscordSettingsView() {
    var discordRPC by remember { mutableStateOf(GlobalSettings.enableDiscordRPC) }
    var showCurrentProject by remember { mutableStateOf(GlobalSettings.showCurrentProject) }
    var showCurrentWorkspaceState by remember { mutableStateOf(GlobalSettings.showCurrentWorkspaceState) }

    SettingsCategory(
        title = "Discord",
    ) {
        SettingsItem(
            title = "Discord Rich Presence",
        ) {
            Switch(
                checked = discordRPC,
                onCheckedChange = {
                    discordRPC = it
                    GlobalSettings.enableDiscordRPC = it
                    // Toggle Discord RPC connection
                    DiscordRPCManager.toggleRPC(it)
                }
            )
        }

        SettingsItem(
            title = "Show Current Project",
        ) {
            Switch(
                checked = showCurrentProject,
                enabled = discordRPC,
                onCheckedChange = {
                    showCurrentProject = it
                    GlobalSettings.showCurrentProject = it
                    // Force update Discord presence
                    DiscordRPCManager.forceUpdate()
                }
            )
        }

        SettingsItem(
            title = "Show Current Workspace State",
        ) {
            Switch(
                checked = showCurrentWorkspaceState,
                enabled = discordRPC,
                onCheckedChange = {
                    showCurrentWorkspaceState = it
                    GlobalSettings.showCurrentWorkspaceState = it
                    // Force update Discord presence
                    DiscordRPCManager.forceUpdate()
                }
            )
        }
    }
}