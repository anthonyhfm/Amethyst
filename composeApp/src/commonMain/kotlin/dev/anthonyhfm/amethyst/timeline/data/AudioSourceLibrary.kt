package dev.anthonyhfm.amethyst.timeline.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory pool of [AudioSource] objects.
 * Each source is keyed by its UUID [AudioSource.id].
 *
 * Populated on project load (from [SavableWorkspaceData.audioSources]) and
 * updated whenever a new audio file is added to the timeline.
 * Persisted alongside the project so [AudioEntry] objects never need to
 * embed the raw PCM bytes themselves.
 */
object AudioSourceLibrary {
    private val _sources = MutableStateFlow<Map<String, AudioSource>>(emptyMap())
    val sources: StateFlow<Map<String, AudioSource>> = _sources.asStateFlow()

    fun add(source: AudioSource) {
        _sources.value = _sources.value + (source.id to source)
    }

    fun get(id: String): AudioSource? = _sources.value[id]

    fun all(): List<AudioSource> = _sources.value.values.toList()

    /** Replaces the entire library — called on project load. */
    fun load(sources: List<AudioSource>) {
        _sources.value = sources.associateBy { it.id }
    }

    fun clear() {
        _sources.value = emptyMap()
    }
}
