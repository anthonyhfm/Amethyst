package dev.anthonyhfm.amethyst.core.network.sync

import dev.anthonyhfm.amethyst.workspace.data.Macro

object WorkspaceSyncCoordinator {
    private var broadcaster: WorkspaceEventBroadcaster? = null

    fun attach(broadcaster: WorkspaceEventBroadcaster) {
        this.broadcaster = broadcaster
    }

    fun detach(broadcaster: WorkspaceEventBroadcaster) {
        if (this.broadcaster === broadcaster) {
            this.broadcaster = null
        }
    }

    fun onBpmChanged(bpm: Double) {
        broadcaster?.onBpmChanged(bpm)
    }

    fun onMacrosChanged(macros: List<Macro>) {
        broadcaster?.onMacrosChanged(macros)
    }
}
