package com.clawd.mobile.data.repository

import android.util.Log
import com.clawd.mobile.data.model.ServerMessage
import com.clawd.mobile.data.websocket.ClawdWebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages pending remote approval requests received via WebSocket.
 *
 * Subscribes to [ConnectionRepository.messageFlow], filters out
 * [ServerMessage.ApprovalRequest] messages, and exposes them via
 * [pendingApprovals] StateFlow. Multiple simultaneous requests are
 * supported — each receives its own notification.
 *
 * Also provides [sendResponse] to reply back to the desktop server
 * with an allow/deny decision.
 */
@Singleton
class ApprovalRepository @Inject constructor(
    private val client: ClawdWebSocketClient,
    private val connectionRepository: ConnectionRepository
) {
    companion object {
        private const val TAG = "ClawdApprovalRepo"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null

    /** All pending approval requests. Order: oldest first. */
    private val _pendingApprovals = MutableStateFlow<List<ServerMessage.ApprovalRequest>>(emptyList())
    val pendingApprovals: StateFlow<List<ServerMessage.ApprovalRequest>> = _pendingApprovals.asStateFlow()

    /** Convenience: the first (oldest) pending request, for single-dialog UI. */
    val pendingApproval: StateFlow<ServerMessage.ApprovalRequest?> = _pendingApprovals
        .map { it.firstOrNull() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // When the first request was received (for timeout tracking in UI)
    private val _receivedAtMs = MutableStateFlow(0L)
    val receivedAtMs: StateFlow<Long> = _receivedAtMs.asStateFlow()

    init {
        Log.i(TAG, "ApprovalRepository INIT — starting collector")
        startCollecting()
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = scope.launch {
            Log.i(TAG, "ApprovalRepository collector ACTIVE — waiting for messages")
            connectionRepository.messageFlow.collect { message ->
                Log.d(TAG, "ApprovalRepository received message: ${message::class.simpleName}")
                if (message is ServerMessage.ApprovalRequest) {
                    Log.i(TAG, "★★★★ Approval request received: requestId=${message.requestId} tool=${message.toolName} agentId=${message.agentId} desc=${message.description}")
                    // Append to list so multiple simultaneous requests don't overwrite each other.
                    // Each request gets its own notification in ClawdForegroundService.
                    _pendingApprovals.update { current ->
                        current + message
                    }
                    // Track the first request's arrival time for UI timeout display.
                    if (_pendingApprovals.value.size == 1) {
                        _receivedAtMs.value = System.currentTimeMillis()
                    }
                    Log.i(TAG, "★★★★ Pending approvals count: ${_pendingApprovals.value.size}")
                }
            }
        }
    }

    /**
     * Clear the oldest pending approval without sending a response.
     * Used when the dialog is dismissed.
     */
    fun clearPending() {
        _pendingApprovals.update { current ->
            if (current.isNotEmpty()) current.drop(1) else current
        }
        if (_pendingApprovals.value.isEmpty()) {
            _receivedAtMs.value = 0L
        }
    }

    /**
     * Clear all pending approvals.
     */
    fun clearAllPending() {
        _pendingApprovals.value = emptyList()
        _receivedAtMs.value = 0L
    }

    /**
     * Send an approval response back to the desktop server and remove
     * the matching request from the pending list.
     *
     * @param requestId The request to respond to
     * @param decision  "allow" or "deny"
     * @param reason    Optional reason message (shown for denials)
     */
    fun sendResponse(requestId: String, decision: String, reason: String? = null) {
        val payload = buildString {
            append("{")
            append("\"version\":\"v2\",")
            append("\"type\":\"approval_response\",")
            append("\"requestId\":\"${requestId.replace("\"", "\\\"")}\",")
            append("\"decision\":\"${decision.replace("\"", "\\\"")}\"")
            if (!reason.isNullOrBlank()) {
                append(",\"reason\":\"${reason.replace("\"", "\\\"")}\"")
            }
            append("}")
        }

        Log.i(TAG, "Sending approval response: requestId=$requestId decision=$decision")
        val sent = client.send(payload)
        if (sent) {
            Log.i(TAG, "Approval response sent successfully")
        } else {
            Log.w(TAG, "Failed to send approval response — not connected")
        }

        // Remove the matching request from the list
        _pendingApprovals.update { current ->
            current.filter { it.requestId != requestId }
        }
        if (_pendingApprovals.value.isEmpty()) {
            _receivedAtMs.value = 0L
        }
    }
}
