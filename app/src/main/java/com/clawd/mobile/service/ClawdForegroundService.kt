package com.clawd.mobile.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.clawd.mobile.data.model.ConnectionState
import com.clawd.mobile.data.repository.ApprovalRepository
import com.clawd.mobile.data.repository.ConnectionRepository
import com.clawd.mobile.data.repository.SessionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ClawdForegroundService : Service() {

    @Inject
    lateinit var connectionRepository: ConnectionRepository

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var foregroundTracker: AppForegroundTracker

    @Inject
    lateinit var approvalRepository: ApprovalRepository

    // Use Dispatchers.Default instead of Main so notification processing
    // isn't blocked when the main thread looper is deprioritized in background.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observeJob: Job? = null
    private var eventJob: Job? = null
    private var wakeLockRenewalJob: Job? = null

    // Partial WakeLock keeps the CPU awake so coroutines can process
    // WebSocket messages even when the device screen is off. Without this,
    // the system may suspend our process and delay incoming message handling
    // until the user manually opens the app.
    // Re-acquired every WAKE_LOCK_RENEWAL_MS to work around the deprecated
    // indefinite acquire() on API 30+.
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startWakeLockRenewal()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopObserving()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APPROVE -> {
                val requestId = intent.getStringExtra(NotificationHelper.EXTRA_REQUEST_ID)
                if (requestId != null) {
                    Log.i(TAG, "Notification action: APPROVE requestId=$requestId")
                    approvalRepository.sendResponse(requestId, "allow")
                    notificationHelper.cancelApprovalNotification(requestId)
                }
                return START_STICKY
            }
            ACTION_DENY -> {
                val requestId = intent.getStringExtra(NotificationHelper.EXTRA_REQUEST_ID)
                if (requestId != null) {
                    Log.i(TAG, "Notification action: DENY requestId=$requestId")
                    approvalRepository.sendResponse(requestId, "deny", "User denied from notification")
                    notificationHelper.cancelApprovalNotification(requestId)
                }
                return START_STICKY
            }
        }

        // ACTION_START, null intent (system restart via START_STICKY on MIUI),
        // or any other action: ensure foreground notification + observers are alive.
        // Previous observers are cancelled inside startObserving() first, so this
        // is safe to call on every non-terminal start command.
        val initialNotification = notificationHelper.buildServiceNotification(0, "连接中...")
        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, initialNotification)
        startObserving()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopObserving()
        releaseWakeLock()
        wakeLockRenewalJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            combine(
                sessionRepository.sessionCount,
                connectionRepository.connectionState
            ) { count, state ->
                val stateText = when (state) {
                    ConnectionState.CONNECTED -> "已连接"
                    ConnectionState.CONNECTING -> "连接中..."
                    ConnectionState.RECONNECTING -> "重连中..."
                    ConnectionState.AUTH_FAILED -> "认证失败"
                    ConnectionState.DISCONNECTED -> "未连接"
                }
                notificationHelper.updateServiceNotification(count, stateText)
            }.collect { }
        }

        // Observe approval requests — fire/cancel notifications based on foreground state.
        // When app is in background, show a notification with Approve/Deny actions
        // for EACH pending request (they don't overwrite each other).
        // When app is in foreground, cancel all approval notifications (the dialog handles it).
        serviceScope.launch {
            combine(
                approvalRepository.pendingApprovals,
                foregroundTracker.isForeground
            ) { approvals, isForeground ->
                Pair(approvals, isForeground)
            }.collect { (approvals, isForeground) ->
                if (approvals.isNotEmpty() && !isForeground) {
                    // App is in background → fire a distinct notification per request
                    for (approval in approvals) {
                        notificationHelper.notifyApprovalRequest(
                            requestId = approval.requestId,
                            agentId = approval.agentId,
                            toolName = approval.toolName,
                            description = approval.description
                        )
                    }
                } else {
                    // App is foreground or no pending approvals → dismiss all
                    notificationHelper.cancelAllApprovalNotifications()
                }
            }
        }

        // Observe BOTH sessions and foreground state.
        // When either changes, evaluate all sessions for notifications.
        // Also detects foreground→background transitions to catch up on
        // state changes that happened while the user was watching.
        //
        // CRITICAL: The first emission after service start (wasForeground == null)
        // only syncs initial state — it does NOT fire notifications. When the
        // process is restarted by START_STICKY, we don't know what transitions
        // occurred while we were dead, so we must not guess.
        eventJob?.cancel()
        eventJob = serviceScope.launch {
            var wasForeground: Boolean? = null

            combine(
                sessionRepository.sessions,
                foregroundTracker.isForeground
            ) { sessions, isForeground ->
                Pair(sessions, isForeground)
            }.collect { (sessions, isForeground) ->
                // --- First emission after (re)start: sync only, no notifications ---
                if (wasForeground == null) {
                    wasForeground = isForeground
                    Log.i(TAG, "First emission after start: isForeground=$isForeground, sessions=${sessions.size}. Syncing initial states (no notifications).")
                    notificationHelper.syncInitialStates(sessions)
                    return@collect
                }

                val becameBackground = wasForeground == true && !isForeground
                wasForeground = isForeground

                if (becameBackground) {
                    Log.d(TAG, "App went to background, catching up on ${sessions.size} sessions")
                    notificationHelper.onAppWentToBackground(sessions)
                } else {
                    for (session in sessions) {
                        notificationHelper.onSessionStateChanged(session, isForeground)
                    }
                }
            }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        eventJob?.cancel()
        eventJob = null
    }

    /**
     * Acquire a partial WakeLock to keep the CPU running while the service
     * is alive. On devices with aggressive power management (MIUI, ColorOS,
     * etc.), the system may suspend CPU-bound work even for foreground services.
     * The WakeLock prevents this, ensuring coroutines that process WebSocket
     * messages can fire notifications in real time.
     *
     * The lock is acquired with a [WAKE_LOCK_TIMEOUT_MS] timeout and re-acquired
     * periodically by [startWakeLockRenewal] to avoid the deprecated indefinite
     * acquire() on API 30+.
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ClawdMobile:ServiceWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            Log.i(TAG, "WakeLock acquired (timeout=${WAKE_LOCK_TIMEOUT_MS / 1000}s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    /**
     * Release the WakeLock if held. Safe to call multiple times.
     */
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    Log.i(TAG, "WakeLock released")
                }
            }
        } catch (_: Exception) { }
        wakeLock = null
    }

    /**
     * Periodically re-acquire the WakeLock before the previous timeout expires.
     * On Android 13+ with aggressive battery management, the system may release
     * our WakeLock when the device enters deep doze. This renewal loop ensures
     * we re-acquire it promptly.
     */
    private fun startWakeLockRenewal() {
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = serviceScope.launch {
            while (true) {
                delay(WAKE_LOCK_RENEWAL_MS)
                // Refresh the WakeLock: release existing and re-acquire
                releaseWakeLock()
                acquireWakeLock()
            }
        }
    }

    companion object {
        const val TAG = "ClawdService"
        const val ACTION_START = "com.clawd.mobile.action.START_SERVICE"
        const val ACTION_STOP = "com.clawd.mobile.action.STOP_SERVICE"
        const val ACTION_APPROVE = "com.clawd.mobile.action.APPROVE"
        const val ACTION_DENY = "com.clawd.mobile.action.DENY"

        /** WakeLock timeout: 10 minutes. Re-acquired before expiry. */
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        /** WakeLock renewal interval: 9 minutes (1 min before timeout). */
        private const val WAKE_LOCK_RENEWAL_MS = 9 * 60 * 1000L
    }
}
