package com.clawd.mobile.data.repository

import android.util.Log
import com.clawd.mobile.data.local.ConnectionPrefs
import com.clawd.mobile.data.model.ConnectionConfig
import com.clawd.mobile.data.model.ConnectionState
import com.clawd.mobile.data.model.ServerMessage
import com.clawd.mobile.data.websocket.ClawdEvent
import com.clawd.mobile.data.websocket.ClawdWebSocketClient
import com.clawd.mobile.data.websocket.MessageParser
import com.clawd.mobile.data.websocket.ReconnectStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepository @Inject constructor(
    private val client: ClawdWebSocketClient,
    private val connectionPrefs: ConnectionPrefs
) {
    companion object {
        private const val TAG = "ClawdConnRepo"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val reconnectStrategy = ReconnectStrategy()
    @Volatile
    private var currentConfig: ConnectionConfig? = null
    private var reconnectJob: Job? = null
    private var collectJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messageFlow = MutableSharedFlow<ServerMessage>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val messageFlow: SharedFlow<ServerMessage> = _messageFlow.asSharedFlow()

    fun connect(config: ConnectionConfig) {
        disconnect()
        currentConfig = config
        reconnectStrategy.reset()
        scope.launch {
            connectionPrefs.saveToHistory(config)
        }
        doConnect()
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        collectJob?.cancel()
        collectJob = null
        client.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        currentConfig = null
    }

    fun reconnect() {
        currentConfig?.let {
            reconnectStrategy.reset()
            doConnect()
        }
    }

    private fun doConnect() {
        val config = currentConfig ?: return
        _connectionState.value = ConnectionState.CONNECTING
        client.connect(config)

        collectJob?.cancel()
        collectJob = scope.launch {
            client.events.collect { event ->
                when (event) {
                    is ClawdEvent.Connected -> {
                        Log.i(TAG, "WebSocket connected")
                        reconnectStrategy.reset()
                        _connectionState.value = ConnectionState.CONNECTED
                    }

                    is ClawdEvent.MessageReceived -> {
                        val msg = MessageParser.parse(event.text)
                        if (msg != null) {
                            _messageFlow.emit(msg)
                        }
                    }

                    is ClawdEvent.AuthFailed -> {
                        Log.w(TAG, "Auth failed")
                        _connectionState.value = ConnectionState.AUTH_FAILED
                        // Stop retrying on auth failure
                    }

                    is ClawdEvent.Closed -> {
                        Log.d(TAG, "Connection closed (code: ${event.code})")
                        if (currentConfig != null) {
                            _connectionState.value = ConnectionState.RECONNECTING
                            scheduleReconnect()
                        } else {
                            _connectionState.value = ConnectionState.DISCONNECTED
                        }
                    }

                    is ClawdEvent.Failure -> {
                        Log.e(TAG, "Connection failure: ${event.throwable.message}")
                        if (currentConfig != null) {
                            _connectionState.value = ConnectionState.RECONNECTING
                            scheduleReconnect()
                        }
                    }
                }
            }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = reconnectStrategy.nextDelay()
            Log.d(TAG, "Reconnecting in ${delayMs}ms...")
            delay(delayMs)
            if (_connectionState.value != ConnectionState.AUTH_FAILED) {
                doConnect()
            }
        }
    }
}
