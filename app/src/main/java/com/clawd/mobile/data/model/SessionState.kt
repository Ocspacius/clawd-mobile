package com.clawd.mobile.data.model

import androidx.compose.ui.graphics.Color

/**
 * Session state enum matching Clawd on Desk protocol v1.
 * Each state has a Compose Color, sort priority, and Chinese label.
 * Colors match the PWA CSS exactly.
 */
enum class SessionState(
    val uiColor: Color,
    val priority: Int,
    val label: String
) {
    ERROR(Color(0xFFEF4444), 0, "错误"),
    ATTENTION(Color(0xFFB45309), 1, "需要关注"),
    WORKING(Color(0xFF22C55E), 2, "工作中"),
    JUGGLING(Color(0xFF22C55E), 2, "多任务"),
    THINKING(Color(0xFF3B82F6), 3, "思考中"),
    NOTIFICATION(Color(0xFFD97757), 4, "通知"),
    SWEEPING(Color(0xFF71717A), 5, "清理中"),
    CARRYING(Color(0xFF71717A), 5, "搬运中"),
    IDLE(Color(0xFF71717A), 6, "空闲"),
    SLEEPING(Color(0xFFA1A1AA), 7, "休眠"),
    UNKNOWN(Color(0xFF71717A), 8, "未知");

    companion object {
        fun fromString(s: String): SessionState =
            entries.find { it.name.equals(s, ignoreCase = true) } ?: UNKNOWN
    }
}
