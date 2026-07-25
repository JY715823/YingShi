package com.example.yingshi.feature.photos

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class SystemMediaContentObserver(
    private val handler: Handler,
    private val contentResolver: ContentResolver,
    private val onChangeCallback: () -> Unit,
) : ContentObserver(handler) {

    private val debounceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var debounceJob: Job? = null

    override fun onChange(selfChange: Boolean) {
        onChange(selfChange, null)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        debounceJob?.cancel()
        debounceJob = debounceScope.launch {
            // P2-1: debounce 500ms → 2000ms, 对标小米相册策略.
            // 此前 500ms 在相机连拍/插卡导入场景会触发多次全量刷新,
            // 2000ms 可合并连拍雪崩, 等待 MediaStore 稳定后再触发一次刷新.
            delay(2000L)
            onChangeCallback()
        }
    }

    fun unregister() {
        debounceJob?.cancel()
        debounceScope.cancel()
        runCatching { contentResolver.unregisterContentObserver(this) }
    }

    companion object {
        fun create(context: Context, onChange: () -> Unit): SystemMediaContentObserver? {
            val appContext = context.applicationContext
            val contentResolver = appContext.contentResolver
            val handler = Handler(Looper.getMainLooper())
            val observer = SystemMediaContentObserver(handler, contentResolver, onChange)
            val uri = MediaStore.Files.getContentUri("external")
            return try {
                contentResolver.registerContentObserver(uri, true, observer)
                observer
            } catch (e: SecurityException) {
                null
            }
        }
    }
}
