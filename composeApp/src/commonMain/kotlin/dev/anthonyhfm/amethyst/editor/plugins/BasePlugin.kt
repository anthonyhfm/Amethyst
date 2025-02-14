package dev.anthonyhfm.amethyst.editor.plugins

interface BasePlugin <Data> {
    suspend fun passData(data: Data)
}