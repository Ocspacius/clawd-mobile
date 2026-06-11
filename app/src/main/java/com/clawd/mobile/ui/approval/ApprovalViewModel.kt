package com.clawd.mobile.ui.approval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clawd.mobile.data.model.ServerMessage
import com.clawd.mobile.data.repository.ApprovalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ApprovalViewModel @Inject constructor(
    private val approvalRepository: ApprovalRepository
) : ViewModel() {

    val pendingApproval: StateFlow<ServerMessage.ApprovalRequest?> =
        approvalRepository.pendingApproval
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val receivedAtMs: StateFlow<Long> =
        approvalRepository.receivedAtMs
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun approve() {
        val request = pendingApproval.value ?: return
        approvalRepository.sendResponse(request.requestId, "allow")
    }

    fun deny(reason: String? = null) {
        val request = pendingApproval.value ?: return
        approvalRepository.sendResponse(request.requestId, "deny", reason)
    }

    fun dismiss() {
        approvalRepository.clearPending()
    }
}
