package dev.anthonyhfm.amethyst.conversion

import dev.anthonyhfm.amethyst.workspace.data.SaveableWorkspaceData

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
    ) : SaveableWorkspaceData
}