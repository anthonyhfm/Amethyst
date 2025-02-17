package dev.anthonyhfm.amethyst.core.data.project

import dev.anthonyhfm.amethyst.core.data.ProjectRepository
import dev.anthonyhfm.amethyst.core.data.tracks.AudioTrack
import dev.anthonyhfm.amethyst.core.data.tracks.EffectTrack
import kotlinx.coroutines.flow.update
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

class AmethystReader(
    private val projectRepository: ProjectRepository
) {
    @OptIn(ExperimentalSerializationApi::class)
    fun readFromFile(byteArray: ByteArray) {
        val project = ProtoBuf.decodeFromByteArray(
            deserializer = AmethystProject.serializer(),
            bytes = byteArray
        )

        loadTracks(project.tracks)
    }

    fun loadTracks(tracks: List<TrackData>) {
        projectRepository.tracks.update {
            tracks.map { trackData ->
                when (trackData.type) {
                    TrackData.TrackType.EFFECT -> {
                        EffectTrack(
                            name = trackData.name
                        )
                    }

                    TrackData.TrackType.AUDIO -> {
                        AudioTrack(
                            name = trackData.name
                        )
                    }
                }
            }
        }
    }
}