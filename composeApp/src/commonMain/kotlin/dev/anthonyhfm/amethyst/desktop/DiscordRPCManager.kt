package dev.anthonyhfm.amethyst.desktop

/**
 * Platform-specific Discord RPC manager.
 * Desktop implementation provides full functionality,
 * while other platforms provide no-op implementation.
 */
expect object DiscordRPCManager {
    /**
     * Initialize the Discord RPC manager.
     */
    fun initialize()
    
    /**
     * Toggle Discord RPC on or off.
     */
    fun toggleRPC(enabled: Boolean)
    
    /**
     * Set the current project name.
     */
    fun setProjectName(name: String?)
    
    /**
     * Force update the Discord presence.
     */
    fun forceUpdate()
}
