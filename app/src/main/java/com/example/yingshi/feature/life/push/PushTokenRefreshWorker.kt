package com.example.yingshi.feature.life.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes the FCM token registration with the backend.
 *
 * Why: onNewToken is not guaranteed to fire when the token rotates while the app process
 * is dead. onResume only triggers when the user opens the app. This worker ensures the
 * backend's lastSeenAtMillis stays fresh (daily) and re-registers if the token changed.
 *
 * De-dup: skips the network call if the token is unchanged AND was reported within 24h.
 */
class PushTokenRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // 1. Guard: must be logged in and in REAL mode
        if (AuthSessionManager.peekTokens()?.accessToken.isNullOrBlank()) {
            return@withContext Result.success() // not a failure — just nothing to do
        }

        // 2. Read current FCM token
        val token = try {
            Tasks.await(FirebaseMessaging.getInstance().token)
        } catch (e: Exception) {
            return@withContext Result.retry() // FCM unavailable, try again later
        }

        // 3. De-dup: skip if token unchanged AND reported within 24h
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastToken = prefs.getString(KEY_LAST_TOKEN, null)
        val lastReportAt = prefs.getLong(KEY_LAST_REPORT_AT, 0L)
        val now = System.currentTimeMillis()
        if (token == lastToken && now - lastReportAt < MAX_REPORT_INTERVAL_MS) {
            return@withContext Result.success()
        }

        // 4. Register with backend
        val result = RepositoryProvider.lifeConsoleRepository.registerPushToken(
            platform = PLATFORM_ANDROID,
            token = token,
        )
        if (result is ApiResult.Success) {
            prefs.edit()
                .putString(KEY_LAST_TOKEN, token)
                .putLong(KEY_LAST_REPORT_AT, System.currentTimeMillis())
                .apply()
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val PREFS_NAME = "push_token_refresh"
        private const val KEY_LAST_TOKEN = "last_token"
        private const val KEY_LAST_REPORT_AT = "last_report_at"
        private const val PLATFORM_ANDROID = "android"
        private const val MAX_REPORT_INTERVAL_MS = 24L * 60 * 60 * 1000 // 24h
        private const val PERIODIC_WORK_NAME = "push-token-refresh-periodic"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PushTokenRefreshWorker>(
                1, TimeUnit.DAYS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
