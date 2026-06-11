package com.clawd.mobile.ui.approval

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clawd.mobile.data.model.ServerMessage

/**
 * AlertDialog overlay for remote permission approval.
 *
 * Displays tool info and approve/deny buttons. The dialog is shown
 * when there's a pending [ServerMessage.ApprovalRequest].
 */
@Composable
fun ApprovalDialog(
    request: ServerMessage.ApprovalRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "权限审批请求",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${request.agentId} · ${request.toolName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Description
                if (request.description.isNotBlank()) {
                    SectionLabel("描述")
                    Text(
                        text = request.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Tool input (scrollable, monospace, truncated)
                if (request.toolInput.isNotBlank()) {
                    SectionLabel("工具输入")
                    Text(
                        text = request.toolInput,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Session info
                if (request.sessionId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionLabel("会话")
                    Text(
                        text = request.sessionId.takeLast(8),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) {
                Text("批准 (Approve)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("拒绝 (Deny)")
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}
