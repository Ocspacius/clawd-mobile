package com.clawd.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.clawd.mobile.data.model.ConnectionState
import com.clawd.mobile.ui.theme.ClawdAmber
import com.clawd.mobile.ui.theme.ClawdFaint
import com.clawd.mobile.ui.theme.ClawdGreen
import com.clawd.mobile.ui.theme.ClawdRed

/**
 * Animated status dot matching the PWA connection indicator.
 * Green pulsing = connected, Amber = connecting, Red = error/auth_failed, Grey = disconnected.
 */
@Composable
fun ConnectionDot(state: ConnectionState, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    val color = when (state) {
        ConnectionState.CONNECTED -> ClawdGreen
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> ClawdAmber
        ConnectionState.AUTH_FAILED -> ClawdRed
        ConnectionState.DISCONNECTED -> ClawdFaint
    }

    val pulse = state == ConnectionState.CONNECTED || state == ConnectionState.RECONNECTING

    val alpha: Float = if (pulse) {
        val transition = rememberInfiniteTransition(label = "dot_pulse")
        val animatedAlpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_pulse_alpha"
        )
        animatedAlpha
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}
