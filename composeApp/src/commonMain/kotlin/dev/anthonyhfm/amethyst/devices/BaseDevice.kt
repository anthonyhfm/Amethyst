package dev.anthonyhfm.amethyst.devices

interface BaseDevice <Data> {
    suspend fun passData(data: Data)
}