package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class SavableWorkspaceData(
    @ProtoNumber(1)
    val version: Version = amethystVersion,
    @ProtoNumber(2)
    val title: String = "Untitled Workspace",
    @ProtoNumber(3)
    val author: String = "Unknown Author",
    @ProtoNumber(4)
    val settings: WorkspaceSettings = WorkspaceSettings(),
    @ProtoNumber(5)
    val timelineData: List<@Polymorphic TimelineTrack<*>> = emptyList(),
    @ProtoNumber(6)
    val lights: StateChain = StateChain(),
    @ProtoNumber(7)
    val sampling: StateChain = StateChain(),
    @ProtoNumber(8)
    val autoPlay: AutoPlayData = AutoPlayData(emptyMap()),
    @ProtoNumber(9)
    val launchpadDevices: List<SavableViewportLaunchpad> = emptyList(),
    @ProtoNumber(10)
    val macros: List<Macro> = listOf(Macro(0)),
    @ProtoNumber(11)
    val gemAssets: List<SavableWorkspaceGemAsset> = emptyList(),

    @Transient
    var path: String? = null, // This is not serialized, used for file operations
) {
    @Serializable
    data class SavableViewportLaunchpad(
        val positionX: Float,
        val positionY: Float,
        val type: ViewportDeviceType,
        val id: String = UUID.randomUUID()
    ) {
        enum class ViewportDeviceType {
            LAUNCHPAD_PRO,
            LAUNCHPAD_PRO_MK3,
            LAUNCHPAD_X,
            LAUNCHPAD_MK2,
            MYSTRIX,
            MIDIFIGHTER64
        }
    }
}

@Serializable
data class RecentWorkspace @OptIn(ExperimentalTime::class) constructor(
    val title: String,
    val path: String,
    val lastOpened: Long = Clock.System.now().toEpochMilliseconds()
)
