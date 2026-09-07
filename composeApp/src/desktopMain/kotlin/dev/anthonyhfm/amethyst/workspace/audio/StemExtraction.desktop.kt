package dev.anthonyhfm.amethyst.workspace.audio

import dev.anthonyhfm.amethyst.core.engine.echo.Echo
import dev.anthonyhfm.amethyst.timeline.data.AudioSource
import dev.anthonyhfm.amethyst.timeline.data.StemKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

actual fun createStemExtractionPlatformBackend(): StemExtractionPlatformBackend = DesktopStemBackend()

private class DesktopStemBackend : StemExtractionPlatformBackend {
    override val isAvailable: Boolean
        get() = resolveSidecarCommand() != null
    override val isModelReady: Boolean
        get() = runCatching {
            val model = modelDirectory().resolve(MODEL_FILE_NAME)
            model.exists() && Files.size(model) == DEMUCS_MODEL_DOWNLOAD_BYTES && sha256(model) == MODEL_SHA256
        }.getOrDefault(false)
    override val canInstallCuda: Boolean by lazy {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("mac")) return@lazy false
        runCatching {
            val process = ProcessBuilder("nvidia-smi", "-L").redirectErrorStream(true).start()
            val completed = process.waitFor(3, TimeUnit.SECONDS)
            if (!completed) process.destroyForcibly()
            completed && process.exitValue() == 0
        }.getOrDefault(false)
    }
    override val isCudaReady: Boolean
        get() = cudaDirectory().resolve(CUDA_READY_MARKER).exists() &&
            cudaDirectory().resolve("torch").toFile().isDirectory

    override suspend fun extract(
        source: AudioSource,
        forceCpu: Boolean,
        installCuda: Boolean,
        onProgress: (StemExtractionProgress) -> Unit,
    ): List<ExtractedStem> = withContext(Dispatchers.IO) {
        if (installCuda && !isCudaReady) installCudaPack(onProgress)
        val model = ensureModel(onProgress)
        val tempDirectory = Files.createTempDirectory("amethyst-stems-")
        try {
            onProgress(StemExtractionProgress(StemExtractionStage.PREPARING_AUDIO, 0.09f))
            val input = tempDirectory.resolve("input.wav")
            writePcmWave(input, source)
            val output = tempDirectory.resolve("output")
            Files.createDirectories(output)
            val command = resolveSidecarCommand()
                ?: error("The bundled Amethyst stem runtime could not be found")
            val cudaOverlay = cudaDirectory().takeIf { !forceCpu && canInstallCuda && isCudaReady }
            runSidecar(command, input, output, model, forceCpu, cudaOverlay, onProgress)
            onProgress(StemExtractionProgress(StemExtractionStage.IMPORTING_STEMS, 0.94f))
            STEM_FILES.map { (kind, fileName) ->
                val path = output.resolve(fileName)
                require(path.exists()) { "Demucs did not produce $fileName" }
                val signal = Echo.decodeAudioFile(path.absolutePathString())
                    ?: error("Could not decode the generated $fileName")
                val rawData = signal.rawData ?: error("Generated $fileName contains no PCM data")
                ExtractedStem(kind, rawData, signal.sampleRate, signal.channels, signal.bitDepth)
            }
        } finally {
            tempDirectory.toFile().deleteRecursively()
        }
    }

    private suspend fun installCudaPack(onProgress: (StemExtractionProgress) -> Unit) {
        require(canInstallCuda) { "No supported NVIDIA GPU was detected" }
        val baseCommand = resolveSidecarCommand() ?: error("The bundled Amethyst stem runtime could not be found")
        val python = baseCommand.first()
        val target = cudaDirectory()
        val partial = target.resolveSibling("${target.fileName}.part")
        partial.toFile().deleteRecursively()
        Files.createDirectories(partial)
        onProgress(StemExtractionProgress(StemExtractionStage.INSTALLING_ACCELERATION, 0.02f, "CUDA"))
        val command = listOf(
            python, "-m", "pip", "install",
            "--disable-pip-version-check",
            "--no-cache-dir",
            "--no-compile",
            "--no-deps",
            "--target", partial.absolutePathString(),
            "--index-url", CUDA_WHEEL_INDEX,
            "torch==$CUDA_TORCH_VERSION",
            "torchaudio==$CUDA_TORCHAUDIO_VERSION",
        )
        val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
        isolatePythonEnvironment(processBuilder)
        val process = processBuilder.start()
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion {
            if (process.isAlive) process.destroyForcibly()
        }
        val diagnostics = ArrayDeque<String>()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    currentCoroutineContext().ensureActive()
                    if (diagnostics.size == 12) diagnostics.removeFirst()
                    diagnostics.addLast(line)
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                error(diagnostics.lastOrNull { it.isNotBlank() } ?: "CUDA runtime installation failed")
            }
            partial.resolve(CUDA_READY_MARKER).toFile().writeText("$CUDA_TORCH_VERSION\n")
            target.toFile().deleteRecursively()
            try {
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial, target)
            }
        } finally {
            cancellationHandle.dispose()
            if (process.isAlive) process.destroyForcibly()
            if (!isCudaReady) partial.toFile().deleteRecursively()
        }
    }

    private suspend fun ensureModel(
        onProgress: (StemExtractionProgress) -> Unit,
    ): Path {
        val directory = modelDirectory()
        Files.createDirectories(directory)
        val target = directory.resolve(MODEL_FILE_NAME)
        if (isModelReady) return target
        target.deleteIfExists()
        val partial = directory.resolve("$MODEL_FILE_NAME.part")
        partial.deleteIfExists()
        onProgress(StemExtractionProgress(StemExtractionStage.DOWNLOADING_MODEL, 0f))

        val connection = URI(MODEL_URL).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        try {
            require(connection.responseCode in 200..299) {
                "Model download failed with HTTP ${connection.responseCode}"
            }
            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(partial.outputStream()).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var downloaded = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(
                            StemExtractionProgress(
                                StemExtractionStage.DOWNLOADING_MODEL,
                                (downloaded.toDouble() / DEMUCS_MODEL_DOWNLOAD_BYTES).toFloat() * 0.08f,
                            )
                        )
                    }
                }
            }
            require(Files.size(partial) == DEMUCS_MODEL_DOWNLOAD_BYTES) { "Downloaded model has an unexpected size" }
            require(sha256(partial) == MODEL_SHA256) { "Downloaded model failed its SHA-256 check" }
            try {
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
            }
            return target
        } finally {
            connection.disconnect()
            if (!target.exists()) partial.deleteIfExists()
        }
    }

    private suspend fun runSidecar(
        baseCommand: List<String>,
        input: Path,
        output: Path,
        model: Path,
        forceCpu: Boolean,
        cudaOverlay: Path?,
        onProgress: (StemExtractionProgress) -> Unit,
    ) {
        val command = baseCommand + listOf(
            "--input", input.absolutePathString(),
            "--output", output.absolutePathString(),
            "--model", model.absolutePathString(),
            "--device", if (forceCpu) "cpu" else "auto",
        )
        val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
        isolatePythonEnvironment(processBuilder, cudaOverlay)
        val process = processBuilder.start()
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion {
            if (process.isAlive) process.destroyForcibly()
        }
        var reportedBackend: String? = if (forceCpu) "CPU" else null
        var reportedError: String? = null
        val diagnostics = ArrayDeque<String>()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    currentCoroutineContext().ensureActive()
                    if (diagnostics.size == 12) diagnostics.removeFirst()
                    diagnostics.addLast(line)
                    val event = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull()
                        ?: return@forEach
                    when (event["type"]?.jsonPrimitive?.content) {
                        "backend" -> {
                            reportedBackend = event["name"]?.jsonPrimitive?.content
                            onProgress(
                                StemExtractionProgress(
                                    StemExtractionStage.SEPARATING,
                                    0.1f,
                                    reportedBackend,
                                )
                            )
                        }
                        "progress" -> {
                            val value = event["value"]?.jsonPrimitive?.floatOrNull ?: 0f
                            onProgress(
                                StemExtractionProgress(
                                    StemExtractionStage.SEPARATING,
                                    0.1f + value.coerceIn(0f, 1f) * 0.82f,
                                    reportedBackend,
                                )
                            )
                        }
                        "error" -> reportedError = event["message"]?.jsonPrimitive?.content
                    }
                }
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                reportedError?.let(::error)
                val detail = diagnostics.lastOrNull { it.isNotBlank() }.orEmpty()
                error(if (detail.isBlank()) "Demucs exited with code $exitCode" else detail)
            }
        } finally {
            cancellationHandle.dispose()
            if (process.isAlive) process.destroyForcibly()
        }
    }
}

private fun isolatePythonEnvironment(processBuilder: ProcessBuilder, overlay: Path? = null) {
    val environment = processBuilder.environment()
    environment.remove("PYTHONHOME")
    environment["PYTHONNOUSERSITE"] = "1"
    environment["PYTHONDONTWRITEBYTECODE"] = "1"
    if (overlay == null) {
        environment.remove("PYTHONPATH")
    } else {
        environment["PYTHONPATH"] = overlay.absolutePathString()
    }
}

private fun resolveSidecarCommand(): List<String>? {
    System.getProperty("amethyst.stems.executable")
        ?.takeIf(String::isNotBlank)
        ?.let { return listOf(it) }
    System.getenv("AMETHYST_STEMS_EXECUTABLE")
        ?.takeIf(String::isNotBlank)
        ?.let { return listOf(it) }

    System.getProperty("compose.application.resources.dir")
        ?.let(Path::of)
        ?.resolve("stems")
        ?.let(::runtimeCommand)
        ?.let { return it }

    val developmentRuntime = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .take(DEVELOPMENT_ROOT_SEARCH_DEPTH)
        .map {
            it.resolve("composeApp/build/generated/stemRuntime")
                .resolve(runtimeTargetName())
                .resolve("stems")
        }
        .mapNotNull(::runtimeCommand)
        .firstOrNull()
    if (developmentRuntime != null) return developmentRuntime
    return null
}

private fun runtimeTargetName(): String {
    val os = System.getProperty("os.name").lowercase()
    val system = when {
        os.contains("mac") -> "macos"
        os.contains("win") -> "windows"
        else -> "linux"
    }
    val architecture = System.getProperty("os.arch").lowercase()
    val arch = if (architecture == "aarch64" || architecture == "arm64") "arm64" else "x64"
    return "$system-$arch"
}

private fun runtimeCommand(stemsDirectory: Path): List<String>? {
    val windows = System.getProperty("os.name").startsWith("Windows", true)
    val python = stemsDirectory.resolve("runtime").resolve(if (windows) "python.exe" else "bin/python3")
    val worker = stemsDirectory.resolve("amethyst_stems.py")
    return if (python.exists() && worker.exists()) {
        listOf(python.absolutePathString(), worker.absolutePathString())
    } else {
        null
    }
}

private fun modelDirectory(): Path {
    return appDataDirectory().resolve("models").resolve(DEMUCS_MODEL_ID)
}

private fun cudaDirectory(): Path = appDataDirectory().resolve("stem-runtime").resolve("cuda-cu118")

private fun appDataDirectory(): Path {
    val os = System.getProperty("os.name").lowercase()
    val root = when {
        os.contains("win") -> System.getenv("LOCALAPPDATA")?.let(Path::of)
        os.contains("mac") -> Path.of(System.getProperty("user.home"), "Library", "Application Support")
        else -> System.getenv("XDG_DATA_HOME")?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".local", "share")
    } ?: Path.of(System.getProperty("user.home"), ".amethyst")
    return root.resolve("Amethyst")
}

private fun sha256(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    path.inputStream().buffered().use { input ->
        val buffer = ByteArray(256 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun writePcmWave(path: Path, source: AudioSource) {
    require(source.bitDepth in setOf(8, 16, 24, 32)) { "Unsupported PCM depth ${source.bitDepth}" }
    val byteRate = source.sampleRate * source.channels * source.bitDepth / 8
    val blockAlign = source.channels * source.bitDepth / 8
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".encodeToByteArray())
        putInt(36 + source.rawData.size)
        put("WAVEfmt ".encodeToByteArray())
        putInt(16)
        putShort(1.toShort())
        putShort(source.channels.toShort())
        putInt(source.sampleRate)
        putInt(byteRate)
        putShort(blockAlign.toShort())
        putShort(source.bitDepth.toShort())
        put("data".encodeToByteArray())
        putInt(source.rawData.size)
    }.array()
    BufferedOutputStream(path.outputStream()).use { output ->
        output.write(header)
        output.write(source.rawData)
    }
}

private const val MODEL_FILE_NAME = "955717e8-8726e21a.th"
private const val MODEL_URL = "https://dl.fbaipublicfiles.com/demucs/hybrid_transformer/$MODEL_FILE_NAME"
private const val MODEL_SHA256 = "8726e21a993978c7ba086d3872e7608d7d5bfca646ca4aca459ffda844faa8b4"
private const val DEVELOPMENT_ROOT_SEARCH_DEPTH = 5
private const val CUDA_WHEEL_INDEX = "https://download.pytorch.org/whl/cu118"
private const val CUDA_TORCH_VERSION = "2.0.1+cu118"
private const val CUDA_TORCHAUDIO_VERSION = "2.0.2+cu118"
private const val CUDA_READY_MARKER = ".amethyst-cuda-ready"
private val STEM_FILES = listOf(
    StemKind.VOCALS to "vocals.wav",
    StemKind.DRUMS to "drums.wav",
    StemKind.BASS to "bass.wav",
    StemKind.OTHER to "other.wav",
)
