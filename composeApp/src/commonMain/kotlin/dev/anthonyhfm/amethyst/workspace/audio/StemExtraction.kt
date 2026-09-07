package dev.anthonyhfm.amethyst.workspace.audio

import dev.anthonyhfm.amethyst.core.util.UUID
import dev.anthonyhfm.amethyst.core.util.randomUUID
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.StemKind
import dev.anthonyhfm.amethyst.timeline.data.StemMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val DEMUCS_MODEL_ID = "htdemucs"
const val DEMUCS_MODEL_DOWNLOAD_BYTES = 84_141_911L
const val CUDA_RUNTIME_DOWNLOAD_BYTES = 2_700_000_000L

enum class StemExtractionStage {
    QUEUED,
    INSTALLING_ACCELERATION,
    DOWNLOADING_MODEL,
    PREPARING_AUDIO,
    SEPARATING,
    IMPORTING_STEMS,
    COMPLETE,
    FAILED,
    CANCELLED,
}

data class StemExtractionJob(
    val id: String,
    val sourceId: String,
    val sourceName: String,
    val stage: StemExtractionStage = StemExtractionStage.QUEUED,
    val progress: Float = 0f,
    val backend: String? = null,
    val error: String? = null,
    val forceCpu: Boolean = false,
) {
    val isTerminal: Boolean
        get() = stage == StemExtractionStage.COMPLETE ||
            stage == StemExtractionStage.FAILED ||
            stage == StemExtractionStage.CANCELLED
}

data class StemExtractionProgress(
    val stage: StemExtractionStage,
    val progress: Float,
    val backend: String? = null,
)

data class ExtractedStem(
    val kind: StemKind,
    val rawData: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
)

interface StemExtractionPlatformBackend {
    val isAvailable: Boolean
    val isModelReady: Boolean
    val canInstallCuda: Boolean
    val isCudaReady: Boolean

    suspend fun extract(
        source: AudioSource,
        forceCpu: Boolean,
        installCuda: Boolean,
        onProgress: (StemExtractionProgress) -> Unit,
    ): List<ExtractedStem>
}

expect fun createStemExtractionPlatformBackend(): StemExtractionPlatformBackend

/** Serial workspace-scoped queue for expensive local source-separation jobs. */
object StemExtractionRepository {
    private data class Request(
        val jobId: String,
        val source: AudioSource,
        val forceCpu: Boolean,
        val installCuda: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val backend by lazy(::createStemExtractionPlatformBackend)
    private val waiting = ArrayDeque<Request>()
    private var activeRequest: Request? = null
    private var activeJob: Job? = null

    private val _jobs = MutableStateFlow<List<StemExtractionJob>>(emptyList())
    val jobs: StateFlow<List<StemExtractionJob>> = _jobs.asStateFlow()
    val isAvailable: Boolean get() = backend.isAvailable
    val requiresModelConsent: Boolean get() = !backend.isModelReady && !_modelConsentGranted.value
    val shouldOfferCuda: Boolean
        get() = backend.canInstallCuda && !backend.isCudaReady && !_cudaDeclinedForSession.value

    private val _modelConsentGranted = MutableStateFlow(false)
    val modelConsentGranted: StateFlow<Boolean> = _modelConsentGranted.asStateFlow()
    private val _cudaDeclinedForSession = MutableStateFlow(false)
    val cudaDeclinedForSession: StateFlow<Boolean> = _cudaDeclinedForSession.asStateFlow()

    fun grantModelConsent() {
        _modelConsentGranted.value = true
    }

    fun declineCudaForSession() {
        _cudaDeclinedForSession.value = true
    }

    fun enqueue(sourceId: String, forceCpu: Boolean = false, installCuda: Boolean = false): String? {
        if (!isAvailable || requiresModelConsent) return null
        val source = AudioLibraryRepository.get(sourceId) ?: return null
        if (source.stemMetadata != null || AudioLibraryRepository.hasStems(sourceId)) return null
        if (_jobs.value.any { it.sourceId == sourceId && !it.isTerminal }) return null

        val id = UUID.randomUUID()
        waiting.addLast(Request(id, source, forceCpu, installCuda))
        _jobs.update { it + StemExtractionJob(id, source.id, source.fileName, forceCpu = forceCpu) }
        pump()
        return id
    }

    fun cancel(jobId: String) {
        val active = activeRequest
        if (active?.jobId == jobId) {
            activeJob?.cancel()
            return
        }
        val retained = waiting.filterNot { it.jobId == jobId }
        if (retained.size != waiting.size) {
            waiting.clear()
            waiting.addAll(retained)
            updateJob(jobId) { it.copy(stage = StemExtractionStage.CANCELLED, progress = 0f) }
        }
    }

    fun retryOnCpu(jobId: String): String? {
        val failed = _jobs.value.firstOrNull { it.id == jobId && it.stage == StemExtractionStage.FAILED }
            ?: return null
        _jobs.update { jobs -> jobs.filterNot { it.id == jobId } }
        return enqueue(failed.sourceId, forceCpu = true)
    }

    fun dismiss(jobId: String) {
        _jobs.update { jobs -> jobs.filterNot { it.id == jobId && it.isTerminal } }
    }

    fun reset() {
        activeJob?.cancel()
        waiting.clear()
        activeRequest = null
        activeJob = null
        _jobs.value = emptyList()
    }

    private fun pump() {
        if (activeJob != null) return
        val request = waiting.removeFirstOrNull() ?: return
        activeRequest = request
        val launchedJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val results = backend.extract(request.source, request.forceCpu, request.installCuda) { progress ->
                    updateJob(request.jobId) {
                        it.copy(
                            stage = progress.stage,
                            progress = progress.progress.coerceIn(0f, 1f),
                            backend = progress.backend ?: it.backend,
                            error = null,
                        )
                    }
                }
                updateJob(request.jobId) { it.copy(stage = StemExtractionStage.IMPORTING_STEMS, progress = 0.96f) }
                val sources = results.map { stem ->
                    AudioSource(
                        id = UUID.randomUUID(),
                        fileName = "${request.source.fileName.substringBeforeLast('.', request.source.fileName)} [${stem.kind.displayName()}].wav",
                        rawData = stem.rawData,
                        sampleRate = stem.sampleRate,
                        channels = stem.channels,
                        bitDepth = stem.bitDepth,
                        stemMetadata = StemMetadata(request.source.id, stem.kind, DEMUCS_MODEL_ID),
                    )
                }
                AudioLibraryRepository.addStemGroup(request.source.id, sources)
                updateJob(request.jobId) { it.copy(stage = StemExtractionStage.COMPLETE, progress = 1f) }
            } catch (_: CancellationException) {
                updateJob(request.jobId) { it.copy(stage = StemExtractionStage.CANCELLED, progress = 0f) }
            } catch (error: Throwable) {
                updateJob(request.jobId) {
                    it.copy(
                        stage = StemExtractionStage.FAILED,
                        error = error.message ?: "Stem extraction failed",
                    )
                }
            } finally {
                if (activeRequest?.jobId == request.jobId) {
                    activeRequest = null
                    activeJob = null
                    pump()
                }
            }
        }
        activeJob = launchedJob
        launchedJob.start()
    }

    private fun updateJob(jobId: String, transform: (StemExtractionJob) -> StemExtractionJob) {
        _jobs.update { jobs -> jobs.map { if (it.id == jobId) transform(it) else it } }
    }
}

fun StemKind.displayName(): String = when (this) {
    StemKind.VOCALS -> "Vocals"
    StemKind.DRUMS -> "Drums"
    StemKind.BASS -> "Bass"
    StemKind.OTHER -> "Other"
}
