package dev.anthonyhfm.amethyst.desktop

/**
 * Android implementation of Discord RPC manager (no-op).
 * Discord RPC is only supported on desktop platforms.
 */
actual object DiscordRPCManager {
    actual fun initialize() {
        // No-op on Android
    }
    
    actual fun toggleRPC(enabled: Boolean) {
        // No-op on Android
    }
    
    actual fun setProjectName(name: String?) {
        // No-op on Android
    }
    
    actual fun forceUpdate() {
        // No-op on Android
    }
}
