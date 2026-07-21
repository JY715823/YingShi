package com.example.yingshi.feature.life

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.example.yingshi.data.model.RemoteLifeConsoleToday
import com.example.yingshi.data.model.RemoteMedia
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.feature.life.widget.LifeConsoleWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Transparent dispatcher that resolves a push targetRoute into the latest life-console
 * media and opens [LifeMediaQuickViewerActivity]. Finishes immediately after handing off.
 */
class LifePushDispatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)

        val route = intent.getStringExtra(EXTRA_TARGET_ROUTE).orEmpty()
        val category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
        Log.d(TAG, "Dispatching push: route=$route, category=$category")

        if (route.isBlank()) {
            Log.w(TAG, "No targetRoute, falling back to main app.")
            finish()
            return
        }

        LifeConsoleWidgetProvider.refreshAll(applicationContext)

        dispatchScope.launch {
            val snapshot = fetchTodaySnapshot()
            val media = resolveMedia(snapshot, route)
            runOnUiThread {
                if (media != null) {
                    val viewerIntent = LifeMediaQuickViewerActivity.widgetIntent(applicationContext, media).apply {
                        putExtra(EXTRA_LAUNCHED_FROM_PUSH, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(viewerIntent)
                } else {
                    Log.d(TAG, "No media resolved for route=$route, falling back to main app.")
                }
                finish()
            }
        }
    }

    private suspend fun fetchTodaySnapshot(): RemoteLifeConsoleToday? {
        return when (val result = RepositoryProvider.lifeConsoleRepository.getToday(date = todayDate())) {
            is ApiResult.Success -> result.data
            else -> null
        }
    }

    private fun resolveMedia(snapshot: RemoteLifeConsoleToday?, route: String): RemoteMedia? {
        if (snapshot == null) return null
        return when {
            route == "life:trace" -> {
                firstAvailableMedia(
                    snapshot.personSelf.mediaItems,
                    snapshot.mealSelf.mediaItems,
                    snapshot.personPartner.mediaItems,
                    snapshot.mealPartner.mediaItems,
                )
            }
            route == "life:bowel" -> {
                firstAvailableMedia(
                    snapshot.personSelf.mediaItems,
                    snapshot.mealSelf.mediaItems,
                )
            }
            else -> null
        }
    }

    private fun firstAvailableMedia(vararg slots: List<RemoteMedia>): RemoteMedia? {
        for (items in slots) {
            val first = items.firstOrNull()
            if (first != null) return first
        }
        return null
    }

    private fun todayDate(zoneId: String = LIFE_CONSOLE_ZONE_ID): String {
        return LocalDate.now(ZoneId.of(zoneId)).toString()
    }

    companion object {
        private const val TAG = "LifePushDispatch"
        const val EXTRA_TARGET_ROUTE = "life_push_target_route"
        const val EXTRA_CATEGORY = "life_push_category"
        const val EXTRA_LAUNCHED_FROM_PUSH = "life_push_launched"
        const val ACTION_DISPATCH = "com.example.yingshi.LIFE_PUSH_DISPATCH"

        private val dispatchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun intent(context: Context, route: String, category: String): Intent {
            return Intent(context, LifePushDispatchActivity::class.java).apply {
                action = ACTION_DISPATCH
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_ROUTE, route)
                putExtra(EXTRA_CATEGORY, category)
            }
        }
    }
}
