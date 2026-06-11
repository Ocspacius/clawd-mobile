package com.clawd.mobile.data.model

/**
 * Parsed server messages from the Clawd on Desk WebSocket.
 * Protocol v2 adds bidirectional remote approval (approval_request / approval_response).
 */
sealed class ServerMessage {
    data class Snapshot(val sessions: Map<String, SessionData>) : ServerMessage()
    data class State(val sessionId: String, val data: SessionData) : ServerMessage()
    data class SessionDeleted(val sessionId: String) : ServerMessage()
    data class ApprovalRequest(
        val requestId: String,
        val sessionId: String,
        val agentId: String,
        val toolName: String,
        val toolInput: String,
        val description: String,
        val timestamp: Long
    ) : ServerMessage()
}
