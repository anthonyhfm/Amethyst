package dev.anthonyhfm.amethyst.nativeengine

import android.content.Context

/**
 * Installs the Android application context required by CPAL/Oboe.
 *
 * The retained reference lives in Rust for the lifetime of the app process, so
 * an application context must be used instead of an Activity.
 */
object AndroidNativeContext {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context): Boolean {
        if (initialized) return true

        try {
            System.loadLibrary("c++_shared")
        } catch (_: UnsatisfiedLinkError) {
            // The runtime may already be loaded by another native dependency.
        }
        System.loadLibrary("amethyst_native_engine")
        initialized = initializeAndroidContext(context.applicationContext)
        return initialized
    }

    @JvmStatic
    private external fun initializeAndroidContext(applicationContext: Context): Boolean
}
