package com.clawd.mobile.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawd.mobile.data.model.SessionEvent
import com.clawd.mobile.data.model.SessionState
import com.clawd.mobile.ui.theme.ClawdMuted
import com.clawd.mobile.ui.theme.ClawdSubtle
import com.clawd.mobile.ui.theme.ClawdText
import kotlinx.coroutines.delay

/**
 * Expandable event timeline for a session. Matches the PWA's .event-history layout.
 */
@Composable
fun EventTimeline(
    events: List<SessionEvent>,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        if (events.isEmpty()) return@AnimatedVisibility

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, bottom = 12.dp, end = 16.dp)
        ) {
            for (event in events) {
                EventRow(event)
            }
        }
    }
}

@Composable
private fun EventRow(event: SessionEvent) {
    val stateColor = remember(event.state) {
        val s = SessionState.fromString(event.state ?: "idle")
        s.uiColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colored dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(stateColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Event emoji icon
        Text(
            text = eventIcon(event.event.orEmpty()),
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Event label (Chinese)
        Text(
            text = eventLabel(event.event.orEmpty()),
            color = ClawdText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Relative time (auto-updating)
        if (event.at != null) {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000)
                    now = System.currentTimeMillis()
                }
            }

            Text(
                text = formatAgo(event.at, now),
                color = ClawdMuted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Chinese label for event types, matching PWA's EVENT_LABELS_CN.
 */
fun eventLabel(eventName: String): String = when (eventName) {
    "UserPromptSubmit" -> "用户输入"
    "PreToolUse" -> "工具启动"
    "PostToolUse" -> "工具完成"
    "PostToolUseFailure" -> "工具失败"
    "Stop" -> "已完成"
    "SessionStart" -> "会话开始"
    "SessionEnd" -> "会话结束"
    "PermissionRequest" -> "需要权限"
    "Notification" -> "通知"
    "SubagentStart" -> "子代理启动"
    "SubagentStop" -> "子代理停止"
    else -> eventName
}

/**
 * Emoji icon for event types, matching PWA's EVENT_ICONS.
 */
fun eventIcon(eventName: String): String = when (eventName) {
    "UserPromptSubmit" -> "💬"
    "PreToolUse" -> "⚙️"
    "PostToolUse" -> "✅"
    "PostToolUseFailure" -> "❌"
    "Stop" -> "🏁"
    "SessionStart" -> "▶️"
    "SessionEnd" -> "⏹️"
    "PermissionRequest" -> "🔒"
    "Notification" -> "🔔"
    "SubagentStart" -> "🔀"
    "SubagentStop" -> "🔀"
    "Elicitation" -> "❓"
    else -> "●"
}

/**
 * Relative time formatter, matching PWA's formatAgo().
 */
fun formatAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val sec = (now - timestamp) / 1000
    return when {
        sec < 5 -> "刚刚"
        sec < 60 -> "${sec}秒前"
        sec < 3600 -> "${sec / 60}分钟前"
        else -> "${sec / 3600}小时前"
    }
}
