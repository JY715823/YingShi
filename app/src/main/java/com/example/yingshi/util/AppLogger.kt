package com.example.yingshi.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Unified logging utility that wraps Android Log and Firebase Crashlytics.
 *
 * In debug builds, logs to Logcat. In release builds with Firebase initialized,
 * also records non-fatal exceptions and custom log messages to Crashlytics.
 *
 * Usage:
 *   AppLogger.e(TAG, "error message", exception)
 *   AppLogger.w(TAG, "warning message")
 *   AppLogger.i(TAG, "info message")
 *   AppLogger.d(TAG, "debug message")
 *   AppLogger.recordException(exception)
 */
object AppLogger {

    private fun isCrashlyticsEnabled(): Boolean {
        return try {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled
        } catch (_: Exception) {
            false
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        if (isCrashlyticsEnabled()) {
            try {
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.log("E/$tag: $message")
                if (throwable != null) {
                    crashlytics.recordException(throwable)
                }
            } catch (_: Exception) {
                // Crashlytics not initialized, silently ignore
            }
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        if (isCrashlyticsEnabled()) {
            try {
                FirebaseCrashlytics.getInstance().log("W/$tag: $message")
            } catch (_: Exception) { }
        }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        if (isCrashlyticsEnabled()) {
            try {
                FirebaseCrashlytics.getInstance().log("I/$tag: $message")
            } catch (_: Exception) { }
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        // Debug messages don't go to Crashlytics
    }

    fun v(tag: String, message: String) {
        Log.v(tag, message)
    }

    /**
     * Record a non-fatal exception to Crashlytics without logging to Logcat.
     * Useful for caught exceptions that should be tracked but aren't errors.
     */
    fun recordException(throwable: Throwable, tag: String? = null) {
        if (isCrashlyticsEnabled()) {
            try {
                val crashlytics = FirebaseCrashlytics.getInstance()
                if (tag != null) {
                    crashlytics.log("Exception in $tag: ${throwable.message}")
                }
                crashlytics.recordException(throwable)
            } catch (_: Exception) { }
        }
    }

    /**
     * Set a custom key-value pair in Crashlytics for crash context.
     */
    fun setCustomKey(key: String, value: String) {
        if (isCrashlyticsEnabled()) {
            try {
                FirebaseCrashlytics.getInstance().setCustomKey(key, value)
            } catch (_: Exception) { }
        }
    }

    /**
     * Set the user identifier in Crashlytics for crash context.
     */
    fun setUserId(userId: String) {
        if (isCrashlyticsEnabled()) {
            try {
                FirebaseCrashlytics.getInstance().setUserId(userId)
            } catch (_: Exception) { }
        }
    }
}
