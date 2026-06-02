package com.example.yingshi.feature.life.push

import android.content.Context
import android.util.Log
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PushTokenRegistrar {
    private const val TAG = "PushTokenRegistrar"
    private const val PLATFORM_ANDROID = "android"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerCurrentTokenIfPossible(context: Context) {
        val appContext = context.applicationContext
        if (!shouldRegister(appContext)) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> registerToken(appContext, token) }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Unable to read Firebase Messaging token.", throwable)
            }
    }

    fun registerToken(context: Context, token: String) {
        val appContext = context.applicationContext
        val trimmedToken = token.trim()
        if (trimmedToken.isBlank() || !shouldRegister(appContext)) return

        scope.launch {
            when (
                val result = RepositoryProvider.lifeConsoleRepository.registerPushToken(
                    platform = PLATFORM_ANDROID,
                    token = trimmedToken,
                )
            ) {
                is ApiResult.Success -> Log.d(TAG, "Registered Firebase Messaging token.")
                is ApiResult.Error -> Log.w(TAG, "Register push token failed: ${result.message}", result.throwable)
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun shouldRegister(context: Context): Boolean {
        if (RepositoryProvider.currentMode != RepositoryMode.REAL) return false
        if (AuthSessionManager.peekTokens()?.accessToken.isNullOrBlank()) return false
        return runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }
}
