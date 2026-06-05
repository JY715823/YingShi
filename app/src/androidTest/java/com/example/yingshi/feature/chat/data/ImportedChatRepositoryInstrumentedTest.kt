package com.example.yingshi.feature.chat.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ImportedChatRepositoryInstrumentedTest {
    private lateinit var appContext: Context
    private lateinit var database: ChatImportDatabase
    private lateinit var dao: ChatImportDao
    private lateinit var repository: ImportedChatRepository
    private lateinit var sandboxDir: File
    private lateinit var importsBaseDir: File
    private lateinit var importsTempDir: File

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ChatImportDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.chatImportDao()
        sandboxDir = appContext.cacheDir.resolve("chat-import-test-${UUID.randomUUID()}").apply { mkdirs() }
        importsBaseDir = sandboxDir.resolve("imports").apply { mkdirs() }
        importsTempDir = sandboxDir.resolve("temp").apply { mkdirs() }
        repository = ImportedChatRepository(
            appContext = appContext,
            database = database,
            dao = dao,
            syncBridge = NoOpChatSyncBridge,
            baseDir = importsBaseDir,
            tempDir = importsTempDir,
        )
    }

    @After
    fun tearDown() {
        database.close()
        sandboxDir.deleteRecursively()
    }

    @Test
    fun importFromZip_handlesNestedRootAndMissingResourcesGracefully() = runBlocking {
        val zipFile = sandboxDir.resolve(
            "friend_TestChat_u_peer123_20260603_161527_chunked_jsonl.zip",
        )
        createSampleQceZip(zipFile)

        val result = repository.importFromZip(Uri.fromFile(zipFile))

        assertTrue(repository.hasImportedChat(result.chatId))
        assertEquals(2, result.importedMessageCount)
        assertEquals(0, result.mergedMessageCount)
        assertEquals(1, result.copiedResourceCount)
        assertEquals(1, result.copiedAvatarCount)
        assertEquals(1, result.missingResourceCount)
        assertEquals(0, result.failedAvatarCount)
        assertTrue(result.warnings.any { it.contains("[resource_missing]") })

        val chats = dao.getAllChats()
        assertEquals(1, chats.size)
        val chat = chats.single()
        assertEquals("Test Chat", chat.displayName)
        assertEquals(2, chat.messageCount)
        assertEquals(1, chat.lastImportResourceCount)
        assertEquals(1, chat.lastImportAvatarCount)
        assertEquals(zipFile.name, chat.sourceFileName)

        val participants = dao.getAllParticipants()
        assertEquals(1, participants.size)
        val participant = participants.single()
        assertTrue(File(requireNotNull(participant.avatarLocalPath)).exists())

        val messages = dao.getAllMessages()
        assertEquals(2, messages.size)

        val resources = dao.getAllResources()
        assertEquals(1, resources.size)
        val chatStorageDir = requireNotNull(importsBaseDir.listFiles()?.singleOrNull { it.isDirectory })
        val storedResourceFile = chatStorageDir.resolve(resources.single().storedRelativePath)
        assertTrue(storedResourceFile.exists())
        assertEquals("resources/images/existing.png", resources.single().originalRelativePath)

        val leftoverTempEntries = importsTempDir.listFiles().orEmpty().toList()
        assertTrue(leftoverTempEntries.isEmpty())
    }

    private fun createSampleQceZip(zipFile: File) {
        val imageBytes = Base64.decode(TINY_PNG_BASE64, Base64.DEFAULT)
        val avatarJson = """{"20001":"data:image/png;base64,$TINY_PNG_BASE64"}"""
        val manifestJson = """
            {
              "chatInfo": {
                "name": "Test Chat",
                "type": "friend",
                "selfUid": "self-001",
                "selfUin": "10000",
                "selfName": "Self User"
              },
              "statistics": {
                "totalMessages": 2
              },
              "chunked": {
                "chunks": [
                  {
                    "relativePath": "chunks/chunk_0001.jsonl",
                    "fileName": "chunk_0001.jsonl",
                    "count": 2
                  }
                ]
              },
              "avatars": {
                "file": "avatars.json"
              }
            }
        """.trimIndent()
        val chunkJsonl = buildString {
            appendLine(
                """
                {"id":"msg-image-1","seq":"1","timestamp":1717402527000,"time":"2026-06-03T16:15:27+08:00","type":"image","sender":{"uid":"peer123","uin":"20001","name":"Peer User"},"content":{"text":"[image]","resources":[{"type":"image","url":"images/existing.png","filename":"existing.png","mimeType":"image/png","md5":"img001","size":${imageBytes.size},"width":1,"height":1}]}}
                """.trimIndent(),
            )
            appendLine(
                """
                {"id":"msg-audio-2","seq":"2","timestamp":1717402528000,"time":"2026-06-03T16:15:28+08:00","type":"audio","sender":{"uid":"peer123","uin":"20001","name":"Peer User"},"content":{"text":"[audio]","resources":[{"type":"audio","localPath":"audios/missing.amr","filename":"missing.amr","mimeType":"audio/amr","md5":"aud001","size":12,"duration":3}]}}
                """.trimIndent(),
            )
        }

        ZipOutputStream(FileOutputStream(zipFile)).use { output ->
            writeZipEntry(output, "nested-root/manifest.json", manifestJson.toByteArray(Charsets.UTF_8))
            writeZipEntry(output, "nested-root/avatars.json", avatarJson.toByteArray(Charsets.UTF_8))
            writeZipEntry(output, "nested-root/chunks/chunk_0001.jsonl", chunkJsonl.toByteArray(Charsets.UTF_8))
            writeZipEntry(output, "nested-root/resources/images/existing.png", imageBytes)
        }
    }

    private fun writeZipEntry(
        output: ZipOutputStream,
        path: String,
        bytes: ByteArray,
    ) {
        output.putNextEntry(ZipEntry(path))
        output.write(bytes)
        output.closeEntry()
    }

    companion object {
        private const val TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS9QAAAAASUVORK5CYII="
    }
}
