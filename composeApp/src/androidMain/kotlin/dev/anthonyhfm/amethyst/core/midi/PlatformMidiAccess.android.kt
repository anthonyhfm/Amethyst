package dev.anthonyhfm.amethyst.core.midi

import android.content.Context

object AndroidMidiAccessProvider {
    private var applicationContext: Context? = null
    private var access: AndroidMidiAccess? = null

    @Synchronized
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    @Synchronized
    internal fun get(): AndroidMidiAccess? {
        val context = applicationContext ?: return null
        return access ?: AndroidMidiAccess(context) { closedAccess ->
            release(closedAccess)
        }.also { access = it }
    }

    @Synchronized
    private fun release(closedAccess: AndroidMidiAccess) {
        if (access === closedAccess) {
            access = null
        }
    }
}

actual val platformMidiAccess: AmethystMidiAccess?
    get() = AndroidMidiAccessProvider.get()
