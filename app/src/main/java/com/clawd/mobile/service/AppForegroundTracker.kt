package com.clawd.mobile.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the main Activity is currently in the foreground.
 * Used to suppress event notifications when the user is actively
 * looking at the session list.
 *
 * IMPORTANT: Default is FALSE, not true. When the process is killed and
 * restarted by START_STICKY (common on MIUI), the Activity is NOT recreated
 * — only the Service restarts. If we default to true, the notification
 * observer thinks the app is foreground and silently suppresses all
 * background notifications. The correct default is false; MainActivity.onResume
 * will set it to true when the user actually opens the app.
 */
@Singleton
class AppForegroundTracker @Inject constructor() {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun onActivityResumed() {
        _isForeground.value = true
    }

    fun onActivityPaused() {
        _isForeground.value = false
    }
}
