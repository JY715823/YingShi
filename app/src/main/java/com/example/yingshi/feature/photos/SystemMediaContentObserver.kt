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
            delay(500L)
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
