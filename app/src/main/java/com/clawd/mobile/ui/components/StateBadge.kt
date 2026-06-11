package com.clawd.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clawd.mobile.data.model.SessionState
import com.clawd.mobile.ui.theme.ClawdBlueBg
import com.clawd.mobile.ui.theme.ClawdGreenBg
import com.clawd.mobile.ui.theme.ClawdOrangeBg
import com.clawd.mobile.ui.theme.ClawdRedBg

/**
 * Colored state badge like the PWA's .state-badge span.
 */
@Composable
fun StateBadge(state: SessionState, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = remember(state) {
        when (state) {
            SessionState.ERROR -> Pair(ClawdRedBg, Color(0xFFEF4444))
            SessionState.ATTENTION -> Pair(ClawdOrangeBg, Color(0xFFB45309))
            SessionState.WORKING, SessionState.JUGGLING -> Pair(ClawdGreenBg, Color(0xFF22C55E))
            SessionState.THINKING -> Pair(ClawdBlueBg, Color(0xFF3B82F6))
            SessionState.NOTIFICATION -> Pair(ClawdOrangeBg, Color(0xFFD97757))
            else -> Pair(Color(0x0FFFFFFF), Color(0xFF71717A))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = state.label,
            color = textColor,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}
