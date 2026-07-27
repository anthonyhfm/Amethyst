package dev.anthonyhfm.amethyst.core.engine.echo

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Tears down and restarts the native Echo audio stream across app
 * foreground/background transitions, so no audio thread or native handle
 * lingers while the process has no visible activity.
 *
 * Register with `ProcessLifecycleOwner.get().lifecycle.addObserver(...)`.
 */
object AndroidAudioLifecycleObserver : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        Echo.onForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        Echo.onBackground()
    }
}
