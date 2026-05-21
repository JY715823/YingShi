package com.example.yingshi.feature.chat.data

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ImportedChatEntity::class,
        ImportedParticipantEntity::class,
        ImportedMessageEntity::class,
        ImportedResourceEntity::class,
        ImportedMessageSearchEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class ChatImportDatabase : RoomDatabase() {
    abstract fun chatImportDao(): ChatImportDao

    companion object {
        @Volatile
        private var instance: ChatImportDatabase? = null

        fun getInstance(context: Context): ChatImportDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatImportDatabase::class.java,
                    "yingshi-chat-imports.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE imported_messages
                    ADD COLUMN replyReferenceMessageId TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE imported_messages
                    ADD COLUMN replyReferenceSenderUin TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE imported_messages
                    ADD COLUMN replyReferenceSenderName TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE imported_messages
                    ADD COLUMN replyReferenceTimestampSeconds INTEGER
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE imported_messages
                    ADD COLUMN replyReferenceContent TEXT
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE imported_resources
                    ADD COLUMN renderKind TEXT NOT NULL DEFAULT 'UNKNOWN'
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE imported_chats
                    ADD COLUMN lastImportAddedMessageCount INTEGER NOT NULL DEFAULT 0
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE imported_chats
                    ADD COLUMN lastImportMergedMessageCount INTEGER NOT NULL DEFAULT 0
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE imported_chats
                    ADD COLUMN lastImportResourceCount INTEGER NOT NULL DEFAULT 0
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    ALTER TABLE imported_chats
                    ADD COLUMN lastImportAvatarCount INTEGER NOT NULL DEFAULT 0
                    """.trimIndent(),
                )
            }
        }
    }
}
