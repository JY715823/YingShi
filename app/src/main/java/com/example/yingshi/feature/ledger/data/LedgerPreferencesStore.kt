package com.example.yingshi.feature.ledger.data

import android.content.Context
import android.content.SharedPreferences

class LedgerPreferencesStore(
    context: Context,
) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDefaultBookId(): String? = preferences.getString(KEY_DEFAULT_BOOK_ID, null)

    fun setDefaultBookId(bookId: String?) {
        preferences.edit().apply {
            if (bookId.isNullOrBlank()) {
                remove(KEY_DEFAULT_BOOK_ID)
            } else {
                putString(KEY_DEFAULT_BOOK_ID, bookId)
            }
        }.apply()
    }

    fun getDefaultAccountId(bookId: String): String? =
        preferences.getString(defaultAccountKey(bookId), null)

    fun setDefaultAccountId(bookId: String, accountId: String?) {
        preferences.edit().apply {
            if (accountId.isNullOrBlank()) {
                remove(defaultAccountKey(bookId))
            } else {
                putString(defaultAccountKey(bookId), accountId)
            }
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "ledger_preferences"
        private const val KEY_DEFAULT_BOOK_ID = "default_book_id"

        fun resolveDefaultBookId(
            storedBookId: String?,
            visibleBookIds: List<String>,
        ): String? {
            if (visibleBookIds.isEmpty()) return null
            return storedBookId?.takeIf { it in visibleBookIds } ?: visibleBookIds.first()
        }

        fun resolveDefaultAccountId(
            storedAccountId: String?,
            visibleAccountIds: List<String>,
        ): String? {
            if (visibleAccountIds.isEmpty()) return null
            return storedAccountId?.takeIf { it in visibleAccountIds } ?: visibleAccountIds.first()
        }

        private fun defaultAccountKey(bookId: String): String = "default_account_$bookId"
    }
}
