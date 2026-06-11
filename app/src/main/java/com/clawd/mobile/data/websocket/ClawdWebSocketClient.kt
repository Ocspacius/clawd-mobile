package com.clawd.mobile.data.websocket

import android.util.Log
import com.clawd.mobile.data.model.ConnectionConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around OkHttp WebSocket for connecting to Clawd on Desk's
 * LAN mobile preview server. Converts OkHttp callbacks into a clean
 * [ClawdEvent] sealed class emitted via a [Flow].
 */
@Singleton
class ClawdWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val eventChannel = Channel<ClawdEvent>(Channel.BUFFERED)
    val events: Flow<ClawdEvent> = eventChannel.receiveAsFlow()

    @Volatile
    private var currentSocket: WebSocket? = null

    fun connect(config: ConnectionConfig) {
        disconnect()

        val request = Request.Builder()
            .url(config.websocketUrl)
            .build()

        currentSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to ${config.host}:${config.port}")
                eventChannel.trySend(ClawdEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                eventChannel.trySend(ClawdEvent.MessageReceived(text))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Server closing (code: $code, reason: $reason)")
                webSocket.close(1000, null)
                if (code == AUTH_FAILED_CODE) {
                    eventChannel.trySend(ClawdEvent.AuthFailed)
                } else {
                    eventChannel.trySend(ClawdEvent.Closed(code, reason))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Connection closed (code: $code)")
                eventChannel.trySend(ClawdEvent.Closed(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                eventChannel.trySend(ClawdEvent.Failure(t))
            }
        })
    }

    fun disconnect() {
        currentSocket?.let { socket ->
            try {
                socket.close(1000, "Client disconnecting")
            } catch (_: Exception) {}
        }
        currentSocket = null
    }

    /**
     * Send a JSON message to the server. M2: used for approval_response.
     * Returns true if the message was queued for sending, false if not connected.
     */
    fun send(json: String): Boolean {
        val socket = currentSocket ?: return false
        return try {
            socket.send(json)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isConnected(): Boolean = currentSocket != null

    companion object {
        private const val TAG = "ClawdWS"
        const val AUTH_FAILED_CODE = 1008
    }
}

/**
 * Typed events from the WebSocket client, replacing raw OkHttp callbacks.
 */
sealed class ClawdEvent {
    data object Connected : ClawdEvent()
    data class MessageReceived(val text: String) : ClawdEvent()
    data class Closed(val code: Int, val reason: String) : ClawdEvent()
    data class Failure(val throwable: Throwable) : ClawdEvent()
    data object AuthFailed : ClawdEvent()
}
