package com.clawd.mobile.ui.sessions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clawd.mobile.data.model.SessionData
import com.clawd.mobile.ui.components.EmptyState
import com.clawd.mobile.ui.components.SessionCard
import com.clawd.mobile.ui.theme.ClawdMuted

@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    if (sessions.isEmpty()) {
        EmptyState(modifier = modifier.fillMaxSize())
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "活跃会话 · ${sessions.size}",
                    color = ClawdMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            items(sessions, key = { it.sessionId }) { session ->
                SessionCard(session = session)
            }
        }
    }
}
