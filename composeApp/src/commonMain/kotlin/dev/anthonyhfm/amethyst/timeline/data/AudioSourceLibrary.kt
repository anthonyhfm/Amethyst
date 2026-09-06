package dev.anthonyhfm.amethyst.timeline.data

import kotlinx.coroutines.flow.StateFlow
import dev.anthonyhfm.amethyst.workspace.audio.AudioLibraryRepository

/**
 * In-memory pool of [AudioSource] objects.
 * Each source is keyed by its UUID [AudioSource.id].
 *
 * Populated on project load (from [SavableWorkspaceData.audioSources]) and
 * updated whenever a new audio file is added to the timeline.
 * Persisted alongside the project so [AudioEntry] objects never need to
 * embed the raw PCM bytes themselves.
 */
@Deprecated("Use AudioLibraryRepository; this facade only preserves source compatibility")
object AudioSourceLibrary {
    val sources: StateFlow<Map<String, AudioSource>> = AudioLibraryRepository.sources

    fun add(source: AudioSource): AudioSource = AudioLibraryRepository.add(source)

    fun get(id: String): AudioSource? = AudioLibraryRepository.get(id)

    fun all(): List<AudioSource> = AudioLibraryRepository.all()

    /** Replaces the entire library — called on project load. */
    fun load(sources: List<AudioSource>) {
        AudioLibraryRepository.load(sources)
    }

    fun clear() {
        AudioLibraryRepository.clear()
    }
}
