package com.clawd.mobile.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawd.mobile.data.model.ConnectionState
import com.clawd.mobile.data.model.SessionData
import com.clawd.mobile.data.repository.ConnectionRepository
import com.clawd.mobile.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SessionsViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    val sessions: StateFlow<List<SessionData>> = sessionRepository.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionState: StateFlow<ConnectionState> = connectionRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    fun onRefresh() {
        connectionRepository.reconnect()
    }
}
