package com.example.yingshi.data.cache

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val OfflineReadOnlyDefaultMessage = "服务器暂时不可用，已切换为缓存只读。"

data class OfflineAccessState(
    val isReadOnly: Boolean = false,
    val message: String? = null,
)

object OfflineAccessManager {
    var state by mutableStateOf(OfflineAccessState())
        private set

    fun enterReadOnly(message: String = OfflineReadOnlyDefaultMessage) {
        state = OfflineAccessState(
            isReadOnly = true,
            message = message,
        )
    }

    fun clear() {
        if (!state.isReadOnly && state.message == null) {
            return
        }
        state = OfflineAccessState()
    }
}
