package dev.anthonyhfm.amethyst.core.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
actual fun UUID.randomUUID(): String {
    return Uuid.random().toString()
}