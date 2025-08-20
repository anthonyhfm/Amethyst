package dev.anthonyhfm.amethyst.core.controls.undo

import dev.anthonyhfm.amethyst.core.heaven.elements.Chain
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDevice
import dev.anthonyhfm.amethyst.devices.effects.keyframes.KeyframesChainDeviceContract.Frame

sealed interface UndoableAction {
    data class ChainDeviceCreation(
        val parent: Chain,
        val device: dev.anthonyhfm.amethyst.devices.ChainDevice<*>,
    ) : UndoableAction

    data class ChainDeviceRemoval(
        val parent: Chain,
        val device: dev.anthonyhfm.amethyst.devices.ChainDevice<*>,
    ) : UndoableAction

    data class MovedChainDevice(
        val chainBefore: Chain,
        val chainAfter: Chain,
        val device: dev.anthonyhfm.amethyst.devices.ChainDevice<*>,
        val fromIndex: Int,
        val toIndex: Int,
    ) : UndoableAction

    data class KeyframeCreation(
        val device: KeyframesChainDevice,
        val frameIndex: Int,
        val frame: Frame
    ) : UndoableAction

    data class KeyframeDeletion(
        val device: KeyframesChainDevice,
        val frameIndex: Int,
        val frame: Frame
    ) : UndoableAction

    data class KeyframeDuplication(
        val device: KeyframesChainDevice,
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedFrame: Frame
    ) : UndoableAction

    data class MultiKeyframeDuplication(
        val device: KeyframesChainDevice,
        val duplications: List<KeyframeDuplicationInfo>
    ) : UndoableAction

    data class KeyframeDuplicationInfo(
        val originalIndex: Int,
        val duplicatedIndex: Int,
        val duplicatedFrame: Frame
    )

    data class MultiKeyframeDeletion(
        val device: KeyframesChainDevice,
        val deletions: List<KeyframeDeletionInfo>
    ) : UndoableAction

    data class KeyframeDeletionInfo(
        val frameIndex: Int,
        val frame: Frame
    )

    data class KeyframePaste(
        val device: KeyframesChainDevice,
        val pastedFrames: List<KeyframePasteInfo>
    ) : UndoableAction

    data class KeyframePasteInfo(
        val frameIndex: Int,
        val frame: Frame
    )
}