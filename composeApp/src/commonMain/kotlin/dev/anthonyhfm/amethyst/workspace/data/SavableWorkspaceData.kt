@file:OptIn(ExperimentalSerializationApi::class)

package dev.anthonyhfm.amethyst.workspace.data

import dev.anthonyhfm.amethyst.core.util.Version
import dev.anthonyhfm.amethyst.core.util.amethystVersion
import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.TimelineTrack
import dev.anthonyhfm.amethyst.workspace.chain.data.StateChain
import kotlinx.serialization.ExperimentalSerializationApi
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
    val audioSources: List<AudioSource> = emptyList(),

    @Transient
    var path: String? = null, // This is not serialized, used for file operations
) {
    @Serializable
    sealed interface SavableViewportLaunchpad {
        val positionX: Float
        val positionY: Float
        val id: String
        val rotationDegrees: Float
        val type: ViewportDeviceType

        enum class ViewportDeviceType {
            LAUNCHPAD_PRO,
            LAUNCHPAD_PRO_MK3,
            LAUNCHPAD_X,
            LAUNCHPAD_IDEALISED,
            LAUNCHPAD_MK2,
            MYSTRIX,
            MIDIFIGHTER64
        }

        @Serializable
        data class LaunchpadPro(
            @ProtoNumber(1) override val positionX: Float,
            @ProtoNumber(2) override val positionY: Float,
            @ProtoNumber(3) override val id: String = UUID.randomUUID(),
            @ProtoNumber(4) override val rotationDegrees: Float = 0f,
        ) : SavableViewportLaunchpad {
            override val type: ViewportDeviceType get() = ViewportDeviceType.LAUNCHPAD_PRO
        }

        @Serializable
        data class LaunchpadProMk3(
            @ProtoNumber(1) override val positionX: Float,
            @ProtoNumber(2) override val positionY: Float,
            @ProtoNumber(3) override val id: String = UUID.randomUUID(),
            @ProtoNumber(4) override val rotationDegrees: Float = 0f,
        ) : SavableViewportLaunchpad {
            override val type: ViewportDeviceType get() = ViewportDeviceType.LAUNCHPAD_PRO_MK3
        }

        @Serializable
        data class LaunchpadX(
            @ProtoNumber(1) override val positionX: Float,
            @ProtoNumber(2) override val positionY: Float,
            @ProtoNumber(3) override val id: String = UUID.randomUUID(),
            @ProtoNumber(4) override val rotationDegrees: Float = 0f,
        ) : SavableViewportLaunchpad {
            override val type: ViewportDeviceType get() = ViewportDeviceType.LAUNCHPAD_X
        }

        @Serializable
        data class LaunchpadIdealised(
            @ProtoNumber(1) override val positionX: Float,
            @ProtoNumber(2) override val positionY: Float,
            @ProtoNumber(3) override val id: String = UUID.randomUUID(),
            @ProtoNumber(4) override val rotationDegrees: Float = 0f,
        ) : SavableViewportLaunchpad {
            override val type: ViewportDeviceType get() = ViewportDeviceType.LAUNCHPAD_IDEALISED
        }

        @Serializable
        data class LaunchpadMk2(
            @ProtoNumber(1) override val positionX: Float,
            @ProtoNumber(2) override val positionY: Float,
            @ProtoNumber(3) override val id: String = UUID.randomUUID(),
            @ProtoNumber(4) override val rotationDegrees: Float = 0f,
        ) : SavableViewportLaunchpad {
            override val type: ViewportDeviceType get() = ViewportDeviceType.LAUNCHPAD_MK2
        }

        @Serializable
        data class Mystrix(
            @ProtoNumber(1) override val positionX: Float,
            @ProtoNumber(2) override val positionY: Float,
            @ProtoNumber(3) override val id: String = UUID.randomUUID(),
            @ProtoNumber(4) override val rotationDegrees: Float = 0f,
        ) : SavableViewportLaunchpad {
            override val type: ViewportDeviceType get() = ViewportDeviceType.MYSTRIX
        }

        @Serializable
        data class MidiFighter64(
            @ProtoNumber(1) override val positionX: Float,
            @ProtoNumber(2) override val positionY: Float,
            @ProtoNumber(3) override val id: String = UUID.randomUUID(),
            @ProtoNumber(4) override val rotationDegrees: Float = 0f,
            @ProtoNumber(5) val style: MidiFighter64Style = MidiFighter64Style.Black,
        ) : SavableViewportLaunchpad {
            override val type: ViewportDeviceType get() = ViewportDeviceType.MIDIFIGHTER64

            @Serializable
            enum class MidiFighter64Style {
                Black,
                White
            }
        }
    }
}

@Serializable
data class RecentWorkspace @OptIn(ExperimentalTime::class) constructor(
    val title: String,
    val path: String,
    val lastOpened: Long = Clock.System.now().toEpochMilliseconds()
)
