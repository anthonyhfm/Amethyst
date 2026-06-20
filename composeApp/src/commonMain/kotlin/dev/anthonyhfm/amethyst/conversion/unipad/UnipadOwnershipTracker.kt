package dev.anthonyhfm.amethyst.conversion.unipad

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update

/**
 * Single-slot LED ownership tracker — mirrors Unipad's LedRunner.btnLed[x][y].
 *
 * Each pad has one "owner" (the animation that most recently sent an On).
 * An Off only clears the pad if the sender is still the owner; stale Offs
 * are silently dropped — exactly like Unipad.
 *
 * Opt-in only: activated by KeyframesChainDeviceState.useOwnershipTracking.
 */
internal object UnipadOwnershipTracker {
    private val owners = atomic(mapOf<Pair<Int, Int>, String>()) // (x,y) -> ownerUUID

    fun claimOwnership(x: Int, y: Int, ownerUUID: String) {
        owners.update { it + (x to y to ownerUUID) }
    }

    /** Returns true if [ownerUUID] is still the owner; clears ownership if so. */
    fun checkAndReleaseOwnership(x: Int, y: Int, ownerUUID: String): Boolean {
        var released = false
        owners.update { current ->
            if (current[x to y] == ownerUUID) {
                released = true
                current - (x to y)
            } else {
                released = false
                current
            }
        }
        return released
    }
}
