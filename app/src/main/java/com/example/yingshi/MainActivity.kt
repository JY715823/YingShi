package com.example.yingshi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.yingshi.app.AppNavigationRequests
import com.example.yingshi.app.YingShiApp
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.ui.theme.YingShiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)
        handleLaunchIntent(intent)
        enableEdgeToEdge()
        setContent {
            YingShiTheme {
                YingShiApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        when (intent?.action) {
            AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE -> AppNavigationRequests.requestLifeConsole(
                slotKey = intent.getStringExtra(AppNavigationRequests.EXTRA_LIFE_CONSOLE_SLOT_KEY),
                mediaId = intent.getStringExtra(AppNavigationRequests.EXTRA_LIFE_CONSOLE_MEDIA_ID),
            )
            AppNavigationRequests.ACTION_OPEN_LEDGER -> AppNavigationRequests.requestLedger()
            AppNavigationRequests.ACTION_OPEN_LEDGER_ADD -> AppNavigationRequests.requestLedgerAdd()
        }
    }
}
