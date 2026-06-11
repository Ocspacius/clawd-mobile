package com.clawd.mobile.ui.settings

import android.app.Application
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawd.mobile.data.local.ConnectionHistoryEntry
import com.clawd.mobile.data.local.ConnectionPrefs
import com.clawd.mobile.data.model.ConnectionConfig
import com.clawd.mobile.data.model.ConnectionState
import com.clawd.mobile.data.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val host: String = "",
    val port: String = "",
    val token: String = "",
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val history: List<ConnectionHistoryEntry> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val connectionRepository: ConnectionRepository,
    private val connectionPrefs: ConnectionPrefs
) : ViewModel() {

    private val _host = MutableStateFlow("")
    private val _port = MutableStateFlow("23334")
    private val _token = MutableStateFlow("")

    val connectionState: StateFlow<ConnectionState> = connectionRepository.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionState.DISCONNECTED)

    val history: StateFlow<List<ConnectionHistoryEntry>> = connectionPrefs.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Reflects whether the app is exempt from battery optimization. */
    val isBatteryOptimized: StateFlow<Boolean> = MutableStateFlow(checkBatteryOptimized())

    val uiState: StateFlow<SettingsUiState> = kotlinx.coroutines.flow.combine(
        _host, _port, _token, connectionState, history
    ) { host, port, token, state, hist ->
        SettingsUiState(
            host = host,
            port = port,
            token = token,
            connectionState = state,
            history = hist
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun onHostChanged(value: String) { _host.value = value }
    fun onPortChanged(value: String) { _port.value = value }
    fun onTokenChanged(value: String) { _token.value = value }

    fun onConnect() {
        val host = _host.value.trim()
        val port = _port.value.trim().toIntOrNull() ?: return
        val token = _token.value.trim()
        if (host.isBlank() || token.isBlank()) return

        connectionRepository.connect(ConnectionConfig(host, port, token))
    }

    fun onDisconnect() {
        connectionRepository.disconnect()
    }

    fun onHistoryEntryClick(entry: ConnectionHistoryEntry) {
        // Defensive: Gson can bypass Kotlin null-safety via reflection,
        // so verify fields are non-null before use.
        val host = entry.host
        val port = entry.port
        val token = entry.token
        if (host.isNullOrBlank() || token.isNullOrBlank()) return

        _host.value = host
        _port.value = port.toString()
        _token.value = token
        connectionRepository.connect(ConnectionConfig(host, port, token))
    }

    fun onDeleteHistory(index: Int) {
        viewModelScope.launch {
            connectionPrefs.deleteHistory(index)
        }
    }

    /** Re-check battery optimization status (call after returning from system settings). */
    fun refreshBatteryStatus() {
        (isBatteryOptimized as MutableStateFlow).value = checkBatteryOptimized()
    }

    /** Open the system dialog to request battery optimization exemption. */
    fun getBatteryOptimizationIntent(): Intent? = if (!checkBatteryOptimized()) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${app.packageName}")
        }
    } else null

    private fun checkBatteryOptimized(): Boolean {
        val pm = app.getSystemService(Application.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(app.packageName)
    }
}
