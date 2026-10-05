package dev.anthonyhfm.amethyst.home.data

import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import java.io.File

actual object MobileProjectBundle {
    actual suspend fun save(projectId: String, originalPath: String, workspace: SavableWorkspaceData): String? = runCatching {
        val root = File(originalPath).parentFile?.takeIf { it.name == "Original" }?.parentFile
            ?: return@runCatching null
        val staging = File(root, ".converted-${java.util.UUID.randomUUID()}")
        val audio = File(staging, "audio")
        check(audio.mkdirs())
        try {
            workspace.audioSources.forEachIndexed { index, source ->
                source.writePcm(path = File(audio, "$index.pcm").absolutePath)
            }
            File(staging, "workspace.pb.gz").writeBytes(MobileProjectBundleCodec.encodeHeader(workspace))
            val target = File(root, "Converted")
            val previous = File(root, ".previous-${java.util.UUID.randomUUID()}")
            if (target.exists()) check(target.renameTo(previous))
            if (!staging.renameTo(target)) {
                if (previous.exists()) previous.renameTo(target)
                error("Could not commit project bundle")
            }
            previous.deleteRecursively()
            target.absolutePath
        } finally {
            staging.deleteRecursively()
        }
    }.getOrNull()

    actual suspend fun load(bundlePath: String): SavableWorkspaceData? = runCatching {
        val root = File(bundlePath)
        val workspace = MobileProjectBundleCodec.decodeHeader(File(root, "workspace.pb.gz").readBytes())
        workspace.copy(audioSources = workspace.audioSources.mapIndexed { index, source ->
            val file = File(root, "audio/$index.pcm")
            source.copyFromPcmFile(path = file.absolutePath) ?: source.copy(rawData = file.readBytes())
        })
    }.getOrNull()
}
