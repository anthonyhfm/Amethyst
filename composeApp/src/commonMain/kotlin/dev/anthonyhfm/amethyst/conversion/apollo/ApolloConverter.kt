package dev.anthonyhfm.amethyst.conversion.apollo

import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloDecoder
import dev.anthonyhfm.amethyst.workspace.data.SavableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

object ApolloConverter : AmethystConverter {
    var bpm: Int = 150
    var version: Int = 32

    fun convertBytesToWorkspace(bytes: ByteArray): SavableWorkspaceData {
        bpm = 150
        return ApolloDecoder(bytes).decode()
    }

    fun convertFileToWorkspace(file: PlatformFile): SavableWorkspaceData {
        val bytes = runBlocking { file.readBytes() }
        return convertBytesToWorkspace(bytes)
    }

    override fun convertZipToWorkspace(file: PlatformFile): SavableWorkspaceData {
        TODO("Not implemented yet")
    }

    override fun convertToWorkspace(path: String, palettePath: String?): SavableWorkspaceData {
        return convertFileToWorkspace(PlatformFile(path))
    }
}