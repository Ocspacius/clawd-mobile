package com.clawd.mobile.data.websocket

import android.util.Log
import com.clawd.mobile.data.model.ServerMessage
import com.clawd.mobile.data.model.SessionData
import com.clawd.mobile.data.model.SessionEvent
import com.clawd.mobile.data.model.SessionState
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Parses raw JSON strings from the Clawd on Desk WebSocket into typed [ServerMessage] objects.
 * Supports protocol v1 and v2. Validates version field and routes by type field.
 */
object MessageParser {

    private const val TAG = "ClawdMsgParser"
    private val SUPPORTED_VERSIONS = setOf("v1", "v2")
    private val gson = Gson()

    /**
     * Parse a raw JSON message. Returns null for unknown/unparseable messages.
     */
    fun parse(raw: String): ServerMessage? {
        return try {
            val root = JsonParser.parseString(raw).asJsonObject
            val version = root.get("version")?.asString ?: return null
            if (version !in SUPPORTED_VERSIONS) {
                Log.w(TAG, "Unsupported version: $version")
                return null
            }

            val type = root.get("type")?.asString ?: return null
            if (type != "state") {  // Don't log state messages (too noisy)
                Log.d(TAG, "Parsing message: version=$version type=$type")
            }
            when (type) {
                "snapshot" -> parseSnapshot(root)
                "state" -> parseState(root)
                "session_deleted" -> parseSessionDeleted(root)
                "approval_request" -> parseApprovalRequest(root)
                else -> {
                    Log.w(TAG, "Unknown message type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            null
        }
    }

    private fun parseSnapshot(root: JsonObject): ServerMessage.Snapshot {
        val sessionsObj = root.getAsJsonObject("sessions") ?: JsonObject()
        val sessions = mutableMapOf<String, SessionData>()
        for ((sid, dataObj) in sessionsObj.entrySet()) {
            if (dataObj.isJsonObject) {
                parseSessionData(dataObj.asJsonObject)?.let { sessions[sid] = it }
            }
        }
        return ServerMessage.Snapshot(sessions)
    }

    private fun parseState(root: JsonObject): ServerMessage.State {
        val sessionId = root.get("sessionId")?.asString ?: ""
        val dataObj = root.getAsJsonObject("data")
        val data = if (dataObj != null) {
            parseSessionData(dataObj) ?: SessionData(sessionId = sessionId)
        } else {
            SessionData(sessionId = sessionId)
        }
        return ServerMessage.State(sessionId, data)
    }

    private fun parseSessionDeleted(root: JsonObject): ServerMessage.SessionDeleted {
        val sessionId = root.get("sessionId")?.asString ?: ""
        return ServerMessage.SessionDeleted(sessionId)
    }

    private fun parseApprovalRequest(root: JsonObject): ServerMessage.ApprovalRequest {
        val requestId = root.get("requestId")?.asString ?: ""
        val toolName = root.get("toolName")?.asString ?: "Unknown"
        Log.i(TAG, "★★★★ Parsing approval_request: requestId=$requestId toolName=$toolName")
        return ServerMessage.ApprovalRequest(
            requestId = requestId,
            sessionId = root.get("sessionId")?.asString ?: "",
            agentId = root.get("agentId")?.asString ?: "claude-code",
            toolName = toolName,
            toolInput = root.get("toolInput")?.asString ?: "",
            description = root.get("description")?.asString ?: "",
            timestamp = root.get("timestamp")?.asLong ?: System.currentTimeMillis()
        )
    }

    private fun parseSessionData(obj: JsonObject): SessionData? {
        return try {
            val stateStr = obj.get("state")?.asString
            val eventsArray = obj.getAsJsonArray("recentEvents")
            val events = if (eventsArray != null) {
                gson.fromJson(eventsArray, Array<SessionEvent>::class.java).toList()
            } else emptyList()

            SessionData(
                sessionId = obj.get("sessionId")?.asString ?: "",
                agentId = obj.get("agentId")?.asString,
                title = obj.get("title")?.asString,
                basename = obj.get("basename")?.asString,
                state = if (stateStr != null) SessionState.fromString(stateStr) else SessionState.UNKNOWN,
                updatedAt = obj.get("updatedAt")?.asLong,
                recentEvents = events
            )
        } catch (_: Exception) {
            null
        }
    }
}
