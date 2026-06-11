package com.clawd.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawd.mobile.data.model.SessionData
import com.clawd.mobile.ui.theme.ClawdCard
import com.clawd.mobile.ui.theme.ClawdCardBorder
import com.clawd.mobile.ui.theme.ClawdFaint
import com.clawd.mobile.ui.theme.ClawdMuted
import com.clawd.mobile.ui.theme.ClawdSubtle
import com.clawd.mobile.ui.theme.ClawdText
import kotlinx.coroutines.delay

/**
 * Session card composable matching the PWA's .session-card style.
 */
@Composable
fun SessionCard(
    session: SessionData,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ClawdCard)
            .border(0.5.dp, ClawdCardBorder, RoundedCornerShape(14.dp))
    ) {
        // Header: Agent name + State badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Agent dot
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(session.state.uiColor)
                )
                Text(
                    text = (session.agentId ?: "AGENT").uppercase(),
                    color = ClawdText,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }

            StateBadge(state = session.state)
        }

        // Title (if present)
        if (!session.title.isNullOrBlank()) {
            Text(
                text = session.title,
                color = ClawdText,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Meta row: project basename + relative time
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!session.basename.isNullOrBlank()) {
                Text(
                    text = "📁 ${session.basename}",
                    color = ClawdSubtle,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (session.updatedAt != null) {
                Text(
                    text = formatAgo(session.updatedAt, now),
                    color = ClawdMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 16.dp)
                .background(ClawdCardBorder)
        )

        // Footer: Recent events toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📋", fontSize = 13.sp)
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Text(
                    text = "最近事件",
                    color = ClawdMuted,
                    fontSize = 12.sp
                )
                if (session.recentEvents.isNotEmpty()) {
                    Text(
                        text = " ${session.recentEvents.size}",
                        color = ClawdFaint,
                        fontSize = 11.sp
                    )
                }
            }

            // Chevron
            Text(
                text = if (expanded) "▲" else "▼",
                color = ClawdFaint,
                fontSize = 10.sp
            )
        }

        // Expandable event timeline
        EventTimeline(
            events = session.recentEvents,
            visible = expanded
        )
    }
}
