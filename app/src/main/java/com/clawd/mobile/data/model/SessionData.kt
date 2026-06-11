package com.clawd.mobile.data.model

import com.google.gson.annotations.SerializedName

/**
 * Session data as received from the Clawd on Desk WebSocket server.
 * Fields match the Protocol v1 payload.
 */
data class SessionData(
    val sessionId: String,
    val agentId: String? = null,
    val title: String? = null,
    val basename: String? = null,
    val state: SessionState = SessionState.UNKNOWN,
    val updatedAt: Long? = null,
    val recentEvents: List<SessionEvent> = emptyList(),
    /**
     * Set to true when this session transitions through ERROR state.
     * Survives StateFlow conflation: even if the ERROR emission is skipped
     * (e.g. WORKING→ERROR→IDLE in rapid succession), the flag remains set
     * so NotificationHelper can fire an error notification rather than a
     * false "task completed" notification.
     *
     * Reset to false when the session enters WORKING/THINKING/JUGGLING.
     */
    val hasNotifiedError: Boolean = false
)

data class SessionEvent(
    val event: String? = null,
    val state: String? = null,
    @SerializedName("time")
    val at: Long? = null
)
