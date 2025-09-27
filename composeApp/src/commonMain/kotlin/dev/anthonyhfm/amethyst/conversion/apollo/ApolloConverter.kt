package dev.anthonyhfm.amethyst.conversion.apollo

import dev.anthonyhfm.amethyst.conversion.AmethystConverter
import dev.anthonyhfm.amethyst.conversion.apollo.data.ApolloDecoder
import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import dev.anthonyhfm.amethyst.workspace.data.WorkspaceSettings
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.runBlocking

object ApolloConverter : AmethystConverter {
    // Accept the optional extra parameter but ignore it for Apollo format
    override fun convertToWorkspace(path: String, palettePath: String?): SaveableWorkspaceData {
        val file = PlatformFile(path)

        val bytes = runBlocking {
            file.readBytes()
        }

        val decoder = ApolloDecoder(bytes.copyOfRange(4, bytes.size))

        if (!decoder.decodeHeader(bytes.copyOfRange(0, 4))) {
            error("Not a valid Apollo file")
        }

        return SaveableWorkspaceData(
            settings = WorkspaceSettings(),
            lights = decoder.decode()
        )
    }
}