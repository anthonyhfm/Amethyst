package dev.anthonyhfm.amethyst.desktop

/**
 * iOS implementation of Discord RPC manager (no-op).
 * Discord RPC is only supported on desktop platforms.
 */
actual object DiscordRPCManager {
    actual fun initialize() {
        // No-op on iOS
    }
    
    actual fun toggleRPC(enabled: Boolean) {
        // No-op on iOS
    }
    
    actual fun setProjectName(name: String?) {
        // No-op on iOS
    }
    
    actual fun forceUpdate() {
        // No-op on iOS
    }
}
