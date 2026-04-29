package com.example.yingshi.data.repository

import com.example.yingshi.data.remote.config.RemoteServiceFactory

enum class RepositoryMode {
    FAKE,
    REAL,
}

object RepositoryProvider {
    private const val defaultMode = "FAKE"

    val currentMode: RepositoryMode = RepositoryMode.valueOf(defaultMode)

    val mediaRepository: MediaRepository by lazy {
        when (currentMode) {
            RepositoryMode.FAKE -> FakeMediaRepositoryShell()
            RepositoryMode.REAL -> RealMediaRepository(RemoteServiceFactory.mediaApi)
        }
    }

    val postRepository: PostRepository by lazy {
        when (currentMode) {
            RepositoryMode.FAKE -> FakePostRepositoryShell()
            RepositoryMode.REAL -> RealPostRepository(RemoteServiceFactory.postApi)
        }
    }

    val commentRepository: CommentRepository by lazy {
        when (currentMode) {
            RepositoryMode.FAKE -> FakeCommentRepositoryShell()
            RepositoryMode.REAL -> RealCommentRepository(RemoteServiceFactory.commentApi)
        }
    }

    val trashRepository: TrashRepository by lazy {
        when (currentMode) {
            RepositoryMode.FAKE -> FakeTrashRepositoryShell()
            RepositoryMode.REAL -> RealTrashRepository(RemoteServiceFactory.trashApi)
        }
    }

    val uploadRepository: UploadRepository by lazy {
        when (currentMode) {
            RepositoryMode.FAKE -> FakeUploadRepositoryShell()
            RepositoryMode.REAL -> RealUploadRepository(RemoteServiceFactory.uploadApi)
        }
    }
}
