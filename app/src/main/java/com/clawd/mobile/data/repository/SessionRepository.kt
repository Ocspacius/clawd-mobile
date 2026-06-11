package com.clawd.mobile.data.repository

import android.util.Log
import com.clawd.mobile.data.model.ServerMessage
import com.clawd.mobile.data.model.SessionData
import com.clawd.mobile.data.model.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    companion object {
        private const val TAG = "ClawdSessionRepo"
        private const val STALE_CLEANUP_MS = 15_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sessions = MutableStateFlow<Map<String, SessionData>>(emptyMap())

    /** UI-ready sorted session list. Excludes sleeping sessions. */
    val sessions: StateFlow<List<SessionData>> = _sessions
        .map { map ->
            map.values
                .filter { it.state != SessionState.SLEEPING }
                .sortedBy { it.state.priority }
        }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    val sessionCount: StateFlow<Int> = _sessions
        .map { map -> map.values.count { it.state != SessionState.SLEEPING } }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0)

    init {
        // Collect parsed messages from ConnectionRepository
        scope.launch {
            connectionRepository.messageFlow.collect { message ->
                when (message) {
                    is ServerMessage.Snapshot -> handleSnapshot(message.sessions)
                    is ServerMessage.State -> handleState(message.sessionId, message.data)
                    is ServerMessage.SessionDeleted -> handleDeleted(message.sessionId)
                    is ServerMessage.ApprovalRequest -> { /* handled by ApprovalRepository */ }
                }
            }
        }

        // Stale cleanup: remove sleeping sessions periodically
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(STALE_CLEANUP_MS)
                _sessions.update { map ->
                    map.filterValues { it.state != SessionState.SLEEPING }
                }
            }
        }
    }

    private fun handleSnapshot(sessions: Map<String, SessionData>) {
        Log.d(TAG, "Snapshot: ${sessions.size} sessions")
        _sessions.value = sessions.mapValues { (sid, data) ->
            data.copy(sessionId = sid)  // Ensure sessionId is set from key
        }
    }

    private fun handleState(sessionId: String, data: SessionData) {
        val existing = _sessions.value[sessionId]
        val merged = if (existing != null) {
            // Determine the error-flag: set on ERROR, clear on working states,
            // otherwise preserve the existing value so it survives StateFlow conflation.
            val newHasNotifiedError = when (data.state) {
                SessionState.ERROR -> true
                SessionState.WORKING, SessionState.THINKING, SessionState.JUGGLING -> false
                else -> existing.hasNotifiedError
            }
            // When entering a working state, discard old recentEvents —
            // they belong to the previous task round. Otherwise a normal
            // completion in the new round would still see stale error events
            // and indicatesError() would fire a false "task error" notification.
            // When entering a working state, ALWAYS start with a clean slate.
            // The server sends a sliding window of recentEvents that spans
            // multiple task rounds — it does NOT clear them between rounds.
            // If we preserved server-provided events here, old error events
            // from a previous round would leak into the next round's
            // indicatesError() check and cause false "任务出错" notifications.
            val newRecentEvents = when (data.state) {
                SessionState.WORKING, SessionState.THINKING, SessionState.JUGGLING -> {
                    // Start a new round with a clean slate. Old error events
                    // from previous rounds must not leak into indicatesError().
                    emptyList()
                }
                else -> {
                    if (data.recentEvents.isNotEmpty()) {
                        // If recentEvents was just cleared by a WORKING→…
                        // transition (new round started), filter server-provided
                        // events to only those that happened in the current round.
                        // The server sends a sliding window that spans rounds;
                        // without filtering, stale error events from previous
                        // rounds would leak back in and cause false "任务出错"
                        // notifications on normal completions.
                        if (existing.recentEvents.isEmpty() && existing.updatedAt != null) {
                            val threshold = existing.updatedAt
                            data.recentEvents.filter { it.at == null || it.at >= threshold }
                        } else {
                            data.recentEvents
                        }
                    } else {
                        existing.recentEvents
                    }
                }
            }
            existing.copy(
                agentId = data.agentId ?: existing.agentId,
                title = data.title ?: existing.title,
                basename = data.basename ?: existing.basename,
                state = if (data.state != SessionState.UNKNOWN) data.state else existing.state,
                updatedAt = data.updatedAt ?: existing.updatedAt,
                recentEvents = newRecentEvents,
                hasNotifiedError = newHasNotifiedError
            )
        } else {
            data.copy(sessionId = sessionId)
        }
        _sessions.update { it + (sessionId to merged) }
    }

    private fun handleDeleted(sessionId: String) {
        _sessions.update { it - sessionId }
    }
}
