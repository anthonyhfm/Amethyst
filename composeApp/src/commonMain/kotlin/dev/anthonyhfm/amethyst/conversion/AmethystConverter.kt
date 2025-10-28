package dev.anthonyhfm.amethyst.conversion

import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData
import io.github.vinceglb.filekit.PlatformFile

/**
 * # AmethystConverter
 *
 * The Amethyst Converter interfaces defines a contract for converting various
 * Launchpad file formats into a workspace format that Amethyst can use.
 *
 * Formats like this may be:
 * - Ableton Live Sets
 * - Apollo Projects
 * - Unipad Projects
 */
interface AmethystConverter {
    fun convertToWorkspace(
        path: String,
        palettePath: String? = ""
    ) : SaveableWorkspaceData

    fun convertZipToWorkspace(file: PlatformFile): SaveableWorkspaceData
}