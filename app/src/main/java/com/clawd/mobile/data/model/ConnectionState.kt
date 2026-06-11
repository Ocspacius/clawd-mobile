package com.clawd.mobile.data.model

/**
 * Connection lifecycle states for UI display.
 */
enum class ConnectionState {
    CONNECTED,
    CONNECTING,
    RECONNECTING,
    DISCONNECTED,
    AUTH_FAILED
}
