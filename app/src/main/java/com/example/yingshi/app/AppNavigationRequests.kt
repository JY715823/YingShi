package com.example.yingshi.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object AppNavigationRequests {
    const val ACTION_OPEN_LIFE_CONSOLE = "com.example.yingshi.action.OPEN_LIFE_CONSOLE"
    const val ACTION_OPEN_LEDGER = "com.example.yingshi.action.OPEN_LEDGER"
    const val ACTION_OPEN_LEDGER_ADD = "com.example.yingshi.action.OPEN_LEDGER_ADD"
    const val EXTRA_LIFE_CONSOLE_SLOT_KEY = "life_console_slot_key"
    const val EXTRA_LIFE_CONSOLE_MEDIA_ID = "life_console_media_id"

    var openLifeConsoleNonce by mutableIntStateOf(0)
        private set
    var openLedgerNonce by mutableIntStateOf(0)
        private set
    var openLedgerAddNonce by mutableIntStateOf(0)
        private set
    var lifeConsoleSlotKey: String? = null
        private set
    var lifeConsoleMediaId: String? = null
        private set

    fun requestLifeConsole(slotKey: String? = null, mediaId: String? = null) {
        lifeConsoleSlotKey = slotKey
        lifeConsoleMediaId = mediaId
        openLifeConsoleNonce += 1
    }

    fun requestLedger() {
        openLedgerNonce += 1
    }

    fun requestLedgerAdd() {
        openLedgerAddNonce += 1
    }
}
