package com.clawd.mobile.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clawd.mobile.data.local.ConnectionHistoryEntry
import com.clawd.mobile.data.model.ConnectionState
import com.clawd.mobile.ui.components.ConnectionDot
import com.clawd.mobile.ui.theme.ClawdAccent
import com.clawd.mobile.ui.theme.ClawdCard
import com.clawd.mobile.ui.theme.ClawdFaint
import com.clawd.mobile.ui.theme.ClawdGreen
import com.clawd.mobile.ui.theme.ClawdMuted
import com.clawd.mobile.ui.theme.ClawdRed
import com.clawd.mobile.ui.theme.ClawdSubtle

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isBatteryOptimized by viewModel.isBatteryOptimized.collectAsStateWithLifecycle()

    // Launcher for battery optimization request — refreshes status when user returns
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshBatteryStatus()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection status
        item {
            ConnectionStatusSection(state.connectionState)
        }

        // Connection form
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "连接设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = state.host,
                    onValueChange = viewModel::onHostChanged,
                    label = { Text("主机地址 (如 192.168.1.x)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.port,
                    onValueChange = viewModel::onPortChanged,
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkTextFieldColors()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.token,
                    onValueChange = viewModel::onTokenChanged,
                    label = { Text("Token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkTextFieldColors()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = viewModel::onConnect,
                        enabled = state.connectionState != ConnectionState.CONNECTED &&
                                state.connectionState != ConnectionState.CONNECTING,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ClawdAccent
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("连接")
                    }

                    OutlinedButton(
                        onClick = viewModel::onDisconnect,
                        enabled = state.connectionState == ConnectionState.CONNECTED,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("断开")
                    }
                }
            }
        }

        // Battery optimization — essential for background notifications on MIUI/ColorOS/etc.
        item {
            BatteryOptimizationSection(
                isOptimized = isBatteryOptimized,
                onRequestOptimization = {
                    viewModel.getBatteryOptimizationIntent()?.let { intent ->
                        batteryLauncher.launch(intent)
                    }
                }
            )
        }

        // Connection history
        if (state.history.isNotEmpty()) {
            item {
                Text(
                    text = "连接历史",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            itemsIndexed(
                items = state.history,
                key = { _, entry -> "${entry.host}:${entry.port}-${entry.timestamp}" }
            ) { index, entry ->
                HistoryItem(
                    entry = entry,
                    onClick = { viewModel.onHistoryEntryClick(entry) },
                    onDelete = { viewModel.onDeleteHistory(index) }
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusSection(state: ConnectionState) {
    val statusText = when (state) {
        ConnectionState.CONNECTED -> "已连接"
        ConnectionState.CONNECTING -> "连接中..."
        ConnectionState.RECONNECTING -> "重连中..."
        ConnectionState.AUTH_FAILED -> "认证失败"
        ConnectionState.DISCONNECTED -> "未连接"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConnectionDot(state = state, size = 10.dp)

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = statusText,
            color = when (state) {
                ConnectionState.CONNECTED -> ClawdAccent
                ConnectionState.AUTH_FAILED -> ClawdRed
                else -> ClawdMuted
            },
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HistoryItem(
    entry: ConnectionHistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${entry.host ?: "?"}:${entry.port}",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp
            )
            Text(
                text = "${entry.timestamp}", // TODO: format as relative time
                color = ClawdSubtle,
                fontSize = 11.sp
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "删除",
                tint = ClawdSubtle,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onBackground,
    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
    focusedBorderColor = ClawdAccent,
    unfocusedBorderColor = ClawdFaint,
    focusedLabelColor = ClawdAccent,
    unfocusedLabelColor = ClawdMuted,
    cursorColor = ClawdAccent
)

@Composable
private fun BatteryOptimizationSection(
    isOptimized: Boolean,
    onRequestOptimization: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "后台保活设置",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            ConnectionDot(
                state = if (isOptimized) ConnectionState.CONNECTED else ConnectionState.AUTH_FAILED,
                size = 8.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isOptimized) "已关闭电池优化 ✓" else "电池优化未关闭 — 后台可能被系统休眠",
                color = if (isOptimized) ClawdGreen else ClawdRed,
                fontSize = 13.sp
            )
        }

        // Explanation text
        Text(
            text = "小米/OPPO/vivo 等手机在熄屏后会冻结 App 进程，导致 WebSocket 断连、通知延迟。" +
                    "关闭电池优化可让 Clawd Mobile 在后台持续运行，像微信一样实时推送通知。",
            color = ClawdMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        if (!isOptimized) {
            Button(
                onClick = onRequestOptimization,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClawdAccent
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("关闭电池优化")
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "💡 小米用户额外操作：系统设置 → 应用设置 → Clawd Mobile → 省电策略 → 选择「无限制」",
                color = ClawdSubtle,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}
