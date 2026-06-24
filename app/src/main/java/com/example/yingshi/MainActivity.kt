package com.example.yingshi

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.yingshi.app.AppNavigationRequests
import com.example.yingshi.app.YingShiApp
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.feature.life.push.PushTokenRegistrar
import com.example.yingshi.ui.theme.YingShiTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthSessionManager.init(applicationContext)
        BackendDebugConfig.init(applicationContext)
        Log.d(TAG, "onCreate: intent action=${intent?.action}, extras=${intent?.extras?.keySet()?.toList()}")
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
        Log.d(TAG, "onNewIntent: action=${intent.action}, extras=${intent.extras?.keySet()?.toList()}")
        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        PushTokenRegistrar.registerCurrentTokenIfPossible(applicationContext)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val targetRoute = intent?.getStringExtra("targetRoute").orEmpty()
        val category = intent?.getStringExtra("category").orEmpty()
        val shouldAutoOpenComment = category.equals("comment", ignoreCase = true) ||
            intent?.getBooleanExtra(AppNavigationRequests.EXTRA_AUTO_OPEN_COMMENT, false) == true
        Log.d(TAG, "handleLaunchIntent: targetRoute=$targetRoute, category=$category, shouldAutoOpenComment=$shouldAutoOpenComment, action=${intent?.action}")
        if (targetRoute.isNotBlank()) {
            when {
                targetRoute.startsWith("photos:media:", ignoreCase = true) -> {
                    val mediaId = targetRoute.substringAfter("photos:media:").takeIf { it.isNotBlank() }
                    Log.d(TAG, "handleLaunchIntent: opening photo feed with media=$mediaId, autoOpenViewer=$shouldAutoOpenComment, autoOpenComment=$shouldAutoOpenComment")
                    AppNavigationRequests.requestPhotoFeed(
                        mediaId = mediaId,
                        autoOpenViewer = shouldAutoOpenComment,
                        autoOpenComment = shouldAutoOpenComment,
                    )
                    return
                }
                targetRoute.startsWith("photos:small-album:", ignoreCase = true) -> {
                    val postId = targetRoute.substringAfter("photos:small-album:").takeIf { it.isNotBlank() }
                    Log.d(TAG, "handleLaunchIntent: opening small album=$postId, autoOpenComment=$shouldAutoOpenComment")
                    if (postId != null) {
                        AppNavigationRequests.requestSmallAlbum(
                            postId = postId,
                            autoOpenComment = shouldAutoOpenComment,
                        )
                    } else {
                        AppNavigationRequests.requestPhotoFeed()
                    }
                    return
                }
                targetRoute.startsWith("photos", ignoreCase = true) -> {
                    Log.d(TAG, "handleLaunchIntent: opening photo feed (general)")
                    AppNavigationRequests.requestPhotoFeed()
                    return
                }
                targetRoute == "life:bowel" || targetRoute == "life:trace" -> {
                    Log.d(TAG, "handleLaunchIntent: opening life console")
                    AppNavigationRequests.requestLifeConsole()
                    return
                }
            }
        }
        when (intent?.action) {
            AppNavigationRequests.ACTION_OPEN_LIFE_CONSOLE -> {
                Log.d(TAG, "handleLaunchIntent: action OPEN_LIFE_CONSOLE")
                AppNavigationRequests.requestLifeConsole(
                    slotKey = intent.getStringExtra(AppNavigationRequests.EXTRA_LIFE_CONSOLE_SLOT_KEY),
                    mediaId = intent.getStringExtra(AppNavigationRequests.EXTRA_LIFE_CONSOLE_MEDIA_ID),
                )
            }
            AppNavigationRequests.ACTION_OPEN_LEDGER -> {
                Log.d(TAG, "handleLaunchIntent: action OPEN_LEDGER")
                AppNavigationRequests.requestLedger()
            }
            AppNavigationRequests.ACTION_OPEN_LEDGER_ADD -> {
                Log.d(TAG, "handleLaunchIntent: action OPEN_LEDGER_ADD")
                AppNavigationRequests.requestLedgerAdd()
            }
            AppNavigationRequests.ACTION_OPEN_PHOTO_FEED -> {
                val mediaId = intent.getStringExtra(AppNavigationRequests.EXTRA_PHOTO_FEED_MEDIA_ID)
                    ?: intent.mediaIdFromTargetRoute()
                Log.d(TAG, "handleLaunchIntent: action OPEN_PHOTO_FEED, mediaId=$mediaId, autoOpenComment=$shouldAutoOpenComment")
                AppNavigationRequests.requestPhotoFeed(
                    mediaId = mediaId,
                    autoOpenViewer = shouldAutoOpenComment,
                    autoOpenComment = shouldAutoOpenComment,
                )
            }
            AppNavigationRequests.ACTION_OPEN_SMALL_ALBUM -> {
                val postId = intent.getStringExtra(AppNavigationRequests.EXTRA_SMALL_ALBUM_ID)
                    ?: intent.smallAlbumIdFromTargetRoute()
                val autoOpenComment = intent.getBooleanExtra(AppNavigationRequests.EXTRA_AUTO_OPEN_COMMENT, false)
                Log.d(TAG, "handleLaunchIntent: action OPEN_SMALL_ALBUM, postId=$postId, autoOpenComment=$autoOpenComment")
                if (!postId.isNullOrBlank()) {
                    AppNavigationRequests.requestSmallAlbum(
                        postId = postId,
                        autoOpenComment = autoOpenComment,
                    )
                } else {
                    AppNavigationRequests.requestPhotoFeed()
                }
            }
            else -> {
                Log.d(TAG, "handleLaunchIntent: no matching route or action, ignoring")
            }
        }
    }

    private fun Intent.mediaIdFromTargetRoute(): String? {
        return getStringExtra("targetRoute")
            ?.substringAfter("photos:media:", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun Intent.smallAlbumIdFromTargetRoute(): String? {
        return getStringExtra("targetRoute")
            ?.substringAfter("photos:small-album:", missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
    }
}
