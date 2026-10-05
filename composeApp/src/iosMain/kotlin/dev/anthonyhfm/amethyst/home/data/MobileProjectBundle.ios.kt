package dev.anthonyhfm.amethyst.home.data

import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.utils.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID

@OptIn(ExperimentalForeignApi::class)
actual object MobileProjectBundle {
    actual suspend fun save(projectId: String, originalPath: String, workspace: SavableWorkspaceData): String? {
        val manager = NSFileManager.defaultManager
        val root = originalPath.substringBeforeLast("/Original/", "")
        if (root.isBlank()) return null
        val target = "$root/Converted"
        val staging = "$root/.converted-${NSUUID().UUIDString}"
        val audioDir = "$staging/audio"
        if (!manager.createDirectoryAtPath(audioDir, true, null, null)) return null
        try {
            workspace.audioSources.forEachIndexed { index, source ->
                source.writePcm(path = "$audioDir/$index.pcm")
            }
            check(manager.createFileAtPath("$staging/workspace.pb.gz", MobileProjectBundleCodec.encodeHeader(workspace).toNSData(), null))
            val previous = "$root/.previous-${NSUUID().UUIDString}"
            val hadTarget = manager.fileExistsAtPath(target)
            if (hadTarget) check(manager.moveItemAtPath(target, previous, null))
            if (!manager.moveItemAtPath(staging, target, null)) {
                if (hadTarget) manager.moveItemAtPath(previous, target, null)
                return null
            }
            if (hadTarget) manager.removeItemAtPath(previous, null)
            return target
        } catch (_: Throwable) {
            return null
        } finally {
            manager.removeItemAtPath(staging, null)
        }
    }

    actual suspend fun load(bundlePath: String): SavableWorkspaceData? = runCatching {
        val header = PlatformFile("$bundlePath/workspace.pb.gz").readBytes()
        val workspace = MobileProjectBundleCodec.decodeHeader(header)
        workspace.copy(audioSources = workspace.audioSources.mapIndexed { index, source ->
            val path = "$bundlePath/audio/$index.pcm"
            source.copyFromPcmFile(path = path) ?: source.copy(rawData = PlatformFile(path).readBytes())
        })
    }.getOrNull()

}
