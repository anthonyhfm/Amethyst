package dev.anthonyhfm.amethyst.editor.plugins

interface BaseDevice <Data> {
    suspend fun passData(data: Data)
}