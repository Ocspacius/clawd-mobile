package com.clawd.mobile.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.clawd.mobile.MainActivity
import com.clawd.mobile.R
import com.clawd.mobile.data.model.SessionData
import com.clawd.mobile.data.model.SessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ClawdNotif"
        private const val CHANNEL_SERVICE = "clawd_service"
        private const val CHANNEL_EVENTS = "clawd_events"
        private const val CHANNEL_APPROVAL = "clawd_approval"
        const val SERVICE_NOTIFICATION_ID = 1001
        private const val EVENT_NOTIFICATION_ID_BASE = 2000
        const val APPROVAL_NOTIFICATION_ID_BASE = 3000
        const val EXTRA_REQUEST_ID = "clawd_approval_request_id"
        const val EXTRA_DECISION = "clawd_approval_decision"
    }

    // Tracks the *last state we observed* for each session (always updated).
    // Used for detecting state transitions (e.g. WORKING → IDLE).
    private val lastSeenStates = mutableMapOf<String, SessionState>()

    // Tracks the *last state for which we actually sent a notification*.
    // Used to avoid duplicate notifications for the same state.
    private val lastNotifiedStates = mutableMapOf<String, SessionState>()

    // Sessions that underwent a notification-worthy transition while the app was
    // in foreground. When the app later goes to background, we catch up on these.
    // Maps sessionId → the transition type ("completed", "error", "attention").
    private val pendingTransitions = mutableMapOf<String, String>()

    // Active approval notification IDs so we can cancel them all when the
    // app comes to foreground or all approvals are handled.
    private val activeApprovalNotificationIds = mutableSetOf<Int>()

    init {
        createChannels()
    }

    private fun createChannels() {
        val manager = NotificationManagerCompat.from(context)

        // Foreground service channel — low importance, no sound
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "后台服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Clawd Mobile 后台连接服务"
            setShowBadge(false)
        }
        manager.createNotificationChannel(serviceChannel)

        // Event notification channel — high importance for state changes
        val eventsChannel = NotificationChannel(
            CHANNEL_EVENTS,
            "会话事件",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Claude Code 会话状态变化通知"
            setShowBadge(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(eventsChannel)

        // Approval notification channel — max importance for remote permission requests
        val approvalChannel = NotificationChannel(
            CHANNEL_APPROVAL,
            "远程审批",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Clawd Desktop 远程权限审批请求"
            setShowBadge(true)
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(approvalChannel)
    }

    fun buildServiceNotification(
        sessionCount: Int,
        connectionState: String
    ): android.app.Notification {
        val contentText = if (sessionCount > 0) {
            "$sessionCount 个活跃会话"
        } else {
            connectionState
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Clawd Mobile")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun updateServiceNotification(
        sessionCount: Int,
        connectionState: String
    ) {
        if (!hasNotificationPermission()) return
        val notification = buildServiceNotification(sessionCount, connectionState)
        NotificationManagerCompat.from(context).notify(SERVICE_NOTIFICATION_ID, notification)
    }

    /**
     * Called once when the service starts (or restarts via START_STICKY).
     * Seeds [lastSeenStates] with the current session states so that
     * subsequent transitions can be detected, but does NOT fire any
     * notifications — we don't know the previous state, so we can't
     * determine if a notification-worthy transition occurred.
     */
    fun syncInitialStates(sessions: List<SessionData>) {
        for (session in sessions) {
            lastSeenStates[session.sessionId] = session.state
            // Also reset notification tracking for sessions in working states,
            // so the next transition to IDLE/ERROR/ATTENTION triggers a notification.
            when (session.state) {
                SessionState.WORKING, SessionState.THINKING, SessionState.JUGGLING -> {
                    lastNotifiedStates.remove(session.sessionId)
                    pendingTransitions.remove(session.sessionId)
                }
                else -> {}
            }
        }
        Log.d(TAG, "Initial state sync: ${sessions.size} sessions seeded, lastSeenStates keys=${lastSeenStates.keys}")
    }

    /**
     * Called on every session state emission.
     * Records the state transition and fires a notification if appropriate.
     */
    fun onSessionStateChanged(
        session: SessionData,
        isAppForeground: Boolean
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No POST_NOTIFICATIONS permission — event notifications disabled")
            return
        }

        val prevSeen = lastSeenStates[session.sessionId]
        lastSeenStates[session.sessionId] = session.state

        // Reset notification tracking when session enters a working state.
        // This allows the NEXT transition to IDLE/ERROR/ATTENTION to fire a
        // new notification instead of being suppressed as a "duplicate".
        // Must clear BOTH lastNotifiedStates AND pendingTransitions —
        // pendingTransitions can hold a stale "completed" from a previous
        // round that would otherwise fire a false positive when the app
        // next goes to background while the session is still WORKING.
        when (session.state) {
            SessionState.WORKING, SessionState.THINKING, SessionState.JUGGLING -> {
                lastNotifiedStates.remove(session.sessionId)
                pendingTransitions.remove(session.sessionId)
            }
            else -> {}
        }

        // Detect notification-worthy transitions even when foreground,
        // so we can catch up later when the app goes to background.
        val transitionType = getTransitionType(prevSeen, session)
        if (transitionType != null) {
            Log.d(TAG, "Notifiable transition for ${session.sessionId}: $prevSeen → ${session.state} ($transitionType), foreground=$isAppForeground")
            pendingTransitions[session.sessionId] = transitionType
        }

        if (isAppForeground) return

        checkAndNotify(session, prevSeen)
    }

    /**
     * Called when the app transitions from foreground to background.
     * Re-evaluates all active sessions — if any have a notifiable state that
     * we haven't already notified for, fire now.
     */
    fun onAppWentToBackground(sessions: List<SessionData>) {
        if (!hasNotificationPermission()) return

        Log.d(TAG, "App went to background, re-evaluating ${sessions.size} sessions (pending: ${pendingTransitions.size})")
        for (session in sessions) {
            val prevSeen = lastSeenStates[session.sessionId]
            if (prevSeen == session.state) {
                // State hasn't changed since we last saw it.
                // But check if there's a pending transition from foreground time.
                val pendingType = pendingTransitions.remove(session.sessionId)
                if (pendingType != null) {
                    Log.d(TAG, "Catching up on pending $pendingType for ${session.sessionId}")
                    if (lastNotifiedStates[session.sessionId] != session.state) {
                        firePendingTransition(session, pendingType)
                        lastNotifiedStates[session.sessionId] = session.state
                    }
                } else {
                    // No pending transition — check if current state alone warrants notification
                    checkCurrentStateNotify(session)
                }
            } else {
                // State changed while we were in foreground — treat as new transition
                lastSeenStates[session.sessionId] = session.state
                checkAndNotify(session, prevSeen)
            }
        }
        // Clean up any stale pending transitions for sessions no longer present
        val activeIds = sessions.map { it.sessionId }.toSet()
        pendingTransitions.keys.removeAll { it !in activeIds }
    }

    /**
     * Check if a state transition should trigger a notification.
     */
    private fun checkAndNotify(session: SessionData, prevSeen: SessionState?) {
        val label = session.title ?: session.agentId ?: "Agent"

        when (session.state) {
            // Reset notification tracking when entering a working state.
            // This handles the background-catchup path where checkAndNotify
            // is called directly from onAppWentToBackground.
            SessionState.WORKING, SessionState.THINKING, SessionState.JUGGLING -> {
                lastNotifiedStates.remove(session.sessionId)
                pendingTransitions.remove(session.sessionId)
            }
            SessionState.ERROR, SessionState.ATTENTION -> {
                if (lastNotifiedStates[session.sessionId] != session.state) {
                    notifyEvent(
                        title = session.state.label,
                        body = "$label — ${session.state.label}",
                        sessionId = session.sessionId
                    )
                    lastNotifiedStates[session.sessionId] = session.state
                    pendingTransitions.remove(session.sessionId)
                }
            }
            SessionState.IDLE -> {
                if (prevSeen == SessionState.WORKING || prevSeen == SessionState.THINKING ||
                    prevSeen == SessionState.JUGGLING
                ) {
                    // Guard: if the session passed through ERROR but the
                    // server-side polling or StateFlow conflation dropped
                    // the intermediate emission, indicatesError() detects
                    // it via hasNotifiedError flag or recentEvents scan.
                    if (indicatesError(session)) {
                        if (lastNotifiedStates[session.sessionId] != SessionState.ERROR) {
                            notifyEvent(
                                title = "任务出错",
                                body = "$label 执行过程中发生错误",
                                sessionId = session.sessionId
                            )
                            lastNotifiedStates[session.sessionId] = SessionState.ERROR
                            pendingTransitions.remove(session.sessionId)
                        }
                    } else {
                        if (lastNotifiedStates[session.sessionId] != SessionState.IDLE) {
                            notifyEvent(
                                title = "任务完成",
                                body = "$label 已完成任务",
                                sessionId = session.sessionId
                            )
                            lastNotifiedStates[session.sessionId] = SessionState.IDLE
                            pendingTransitions.remove(session.sessionId)
                        }
                    }
                }
            }
            else -> { /* no notification for other states */ }
        }
    }

    /**
     * Determine if the transition from prevState → newState is notification-worthy.
     * Returns a type label ("completed", "error", "attention") or null.
     */
    private fun getTransitionType(prevState: SessionState?, session: SessionData): String? {
        if (prevState == null) return null  // initial observation, not a transition

        return when (session.state) {
            SessionState.ERROR -> "error"
            SessionState.ATTENTION -> "attention"
            SessionState.IDLE -> {
                if (prevState == SessionState.WORKING || prevState == SessionState.THINKING ||
                    prevState == SessionState.JUGGLING
                ) {
                    // Check indicatesError(): if the session passed through
                    // ERROR (detected via hasNotifiedError flag or
                    // recentEvents scan), treat this as an error transition
                    // rather than "completed".
                    if (indicatesError(session)) "error" else "completed"
                } else null
            }
            else -> null
        }
    }

    /**
     * Fire a notification for a transition that was detected while the app was
     * in foreground, now that the app has gone to background.
     */
    private fun firePendingTransition(session: SessionData, transitionType: String) {
        val label = session.title ?: session.agentId ?: "Agent"
        // Defense-in-depth: if indicatesError() is true, the "completed"
        // transition should really be "error" — the session had an error
        // before reaching IDLE (detected via flag or recentEvents scan).
        val effectiveType = if (transitionType == "completed" && indicatesError(session)) "error" else transitionType
        when (effectiveType) {
            "completed" -> notifyEvent("任务完成", "$label 已完成任务", session.sessionId)
            "error" -> notifyEvent("任务出错", "$label 执行过程中发生错误", session.sessionId)
            "attention" -> notifyEvent("需要关注", "$label — 需要关注", session.sessionId)
        }
    }

    /**
     * Check if the *current* state alone (without a transition) warrants a notification.
     * Used when the app goes to background and we need to catch up on what
     * happened while the user was watching.
     */
    private fun checkCurrentStateNotify(session: SessionData) {
        val label = session.title ?: session.agentId ?: "Agent"

        when (session.state) {
            SessionState.ERROR -> {
                if (lastNotifiedStates[session.sessionId] != SessionState.ERROR) {
                    notifyEvent("错误", "$label — 发生错误", session.sessionId)
                    lastNotifiedStates[session.sessionId] = SessionState.ERROR
                }
            }
            SessionState.ATTENTION -> {
                if (lastNotifiedStates[session.sessionId] != SessionState.ATTENTION) {
                    notifyEvent("需要关注", "$label — 需要关注", session.sessionId)
                    lastNotifiedStates[session.sessionId] = SessionState.ATTENTION
                }
            }
            // IDLE without a transition → don't notify (we only notify on WORKING→IDLE transition)
            else -> { /* no catch-up notification for other states */ }
        }
    }

    /**
     * Check whether a completed session actually had an error.
     *
     * Two-pronged detection:
     * 1. [SessionData.hasNotifiedError] — set when the server sent an explicit
     *    ERROR state message (handles StateFlow conflation on Android).
     * 2. [SessionData.recentEvents] — scanned for error-related events as a
     *    fallback when the server-side polling skips the intermediate ERROR
     *    state and only sends the final IDLE message (which bundles the error
     *    events in recentEvents).
     */
    /**
     * Check whether a completed session actually ended with an error.
     *
     * Mirrors the desktop client's deriveSessionBadge() logic
     * (state-session-snapshot.js:74-85). The core principle:
     *
     *   阶段性的错误不代表整个任务的错误 — intermediate errors
     *   (e.g. a tool call that failed and was retried) do NOT mean
     *   the task ended with an error. Only the FINAL event matters.
     *
     * Detection order:
     * 1. Check the LAST event in recentEvents (desktop exact match).
     *    StopFailure / PostToolUseFailure / ApiError → error.
     *    Stop / event_msg:task_complete → normal completion.
     * 2. Fallback: hasNotifiedError flag (explicit ERROR state was
     *    observed by the Android client).
     */
    private fun indicatesError(session: SessionData): Boolean {
        // 1. Last-event check — matches desktop deriveSessionBadge
        val lastEvent = session.recentEvents.lastOrNull()
        if (lastEvent != null) {
            val eventType = lastEvent.event ?: ""
            // Terminal failure events → task ended with error
            if (eventType == "StopFailure" || eventType == "PostToolUseFailure" || eventType == "ApiError") {
                return true
            }
            // Terminal success events → task completed normally.
            // This takes priority even if hasNotifiedError was set by an
            // intermediate ERROR state (the task recovered and finished).
            if (eventType == "Stop" || eventType == "event_msg:task_complete") {
                return false
            }
        }

        // 2. Fallback: explicit ERROR state was observed.
        // Catches the case where server sent ERROR state but the
        // terminal event is unclear (e.g. server polling skipped the
        // IDLE transition entirely).
        return session.hasNotifiedError
    }

    private fun notifyEvent(title: String, body: String, sessionId: String) {
        Log.i(TAG, "Firing notification: [$title] $body")

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setContentIntent(pendingIntent)
            .setGroup("clawd_sessions")
            .build()

        NotificationManagerCompat.from(context).notify(
            EVENT_NOTIFICATION_ID_BASE + sessionId.hashCode(),
            notification
        )
    }

    /**
     * Show a high-priority notification for a remote approval request.
     * Includes [Approve] and [Deny] action buttons that send intents to
     * [ClawdForegroundService] for handling.
     */
    fun notifyApprovalRequest(
        requestId: String,
        agentId: String,
        toolName: String,
        description: String
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission — approval notification suppressed")
            return
        }

        val approveIntent = Intent(context, ClawdForegroundService::class.java).apply {
            action = ClawdForegroundService.ACTION_APPROVE
            putExtra(EXTRA_REQUEST_ID, requestId)
        }
        val approvePending = PendingIntent.getService(
            context, requestId.hashCode(),
            approveIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val denyIntent = Intent(context, ClawdForegroundService::class.java).apply {
            action = ClawdForegroundService.ACTION_DENY
            putExtra(EXTRA_REQUEST_ID, requestId)
        }
        val denyPending = PendingIntent.getService(
            context, (requestId + "_deny").hashCode(),
            denyIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (description.isNotBlank()) description
            else "$agentId 请求 $toolName"

        val notification = NotificationCompat.Builder(context, CHANNEL_APPROVAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("权限审批请求")
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(EXTRA_REQUEST_ID, requestId)
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "拒绝",
                denyPending
            )
            .addAction(
                android.R.drawable.ic_menu_save,
                "批准",
                approvePending
            )
            .build()

        Log.i(TAG, "Firing approval notification: requestId=$requestId tool=$toolName")
        val notificationId = APPROVAL_NOTIFICATION_ID_BASE + requestId.hashCode()
        activeApprovalNotificationIds.add(notificationId)
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Cancel the approval notification for a specific request.
     */
    fun cancelApprovalNotification(requestId: String) {
        val notificationId = APPROVAL_NOTIFICATION_ID_BASE + requestId.hashCode()
        activeApprovalNotificationIds.remove(notificationId)
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * Cancel ALL active approval notifications.
     * Called when app comes to foreground or all approvals are cleared.
     */
    fun cancelAllApprovalNotifications() {
        val nm = NotificationManagerCompat.from(context)
        for (id in activeApprovalNotificationIds) {
            nm.cancel(id)
        }
        activeApprovalNotificationIds.clear()
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
