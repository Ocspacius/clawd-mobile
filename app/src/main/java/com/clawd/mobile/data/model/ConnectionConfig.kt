package com.clawd.mobile.data.model

/**
 * Connection configuration entered by the user or loaded from history.
 */
data class ConnectionConfig(
    val host: String,
    val port: Int,
    val token: String
) {
    val websocketUrl: String
        get() = "ws://$host:$port/ws?token=$token"
}
