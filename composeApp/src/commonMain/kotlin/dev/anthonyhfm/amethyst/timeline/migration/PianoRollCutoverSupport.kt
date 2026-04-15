package dev.anthonyhfm.amethyst.timeline.migration

import dev.anthonyhfm.amethyst.timeline.contract.TimelineClipContext

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER
)
@Retention(AnnotationRetention.BINARY)
annotation class LegacyPianoRollPath(
    val replacement: String,
    val cutover: String
)

enum class PianoRollEditPath {
    TimelineCommandSurface,
    LegacyCallbacks
}

data class PianoRollCutoverChecklist(
    val clipContextBound: Boolean,
    val commandSurfaceActive: Boolean,
    val legacyCallbackBridgeRequired: Boolean
) {
    val isReadyForLegacyCutover: Boolean
        get() = clipContextBound && commandSurfaceActive && !legacyCallbackBridgeRequired
}

data class PianoRollCutoverMarker(
    val editPath: PianoRollEditPath,
    val legacySource: String?,
    val checklist: PianoRollCutoverChecklist,
    val nextStep: String
) {
    val usesTimelineCommandSurface: Boolean
        get() = editPath == PianoRollEditPath.TimelineCommandSurface

    val requiresLegacyCallbacks: Boolean
        get() = editPath == PianoRollEditPath.LegacyCallbacks
}

object PianoRollCutoverSupport {
    fun resolveEditPath(clipContext: TimelineClipContext?): PianoRollEditPath {
        return if (clipContext == null) {
            PianoRollEditPath.LegacyCallbacks
        } else {
            PianoRollEditPath.TimelineCommandSurface
        }
    }

    fun marker(
        clipContext: TimelineClipContext?,
        legacySource: String? = null
    ): PianoRollCutoverMarker {
        val editPath = resolveEditPath(clipContext)
        val checklist = PianoRollCutoverChecklist(
            clipContextBound = clipContext != null,
            commandSurfaceActive = editPath == PianoRollEditPath.TimelineCommandSurface,
            legacyCallbackBridgeRequired = editPath == PianoRollEditPath.LegacyCallbacks
        )

        val nextStep = if (checklist.isReadyForLegacyCutover) {
            "Keep routing note mutations through TimelineCommandSurface and remove callback-only entry points."
        } else {
            "Re-enter the piano roll from a TimelineClipContext-backed adapter before cutting over the legacy callback path."
        }

        return PianoRollCutoverMarker(
            editPath = editPath,
            legacySource = legacySource,
            checklist = checklist,
            nextStep = nextStep
        )
    }
}
