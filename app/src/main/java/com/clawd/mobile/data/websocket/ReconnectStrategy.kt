package com.clawd.mobile.data.websocket

/**
 * Exponential backoff reconnect strategy matching the PWA's logic:
 * 1s → 2s → 4s → 8s → 16s → 30s (max)
 */
class ReconnectStrategy {

    private var currentDelay = INITIAL_DELAY_MS

    fun nextDelay(): Long {
        val delay = currentDelay
        currentDelay = minOf(currentDelay * 2, MAX_DELAY_MS)
        return delay
    }

    fun reset() {
        currentDelay = INITIAL_DELAY_MS
    }

    companion object {
        private const val INITIAL_DELAY_MS = 1000L
        private const val MAX_DELAY_MS = 30000L
    }
}
