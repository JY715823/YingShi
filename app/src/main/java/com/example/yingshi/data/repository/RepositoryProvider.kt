package com.example.yingshi.data.repository

import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.config.RemoteServiceFactory

enum class RepositoryMode {
    FAKE,
    REAL,
}

object RepositoryProvider {
    val currentMode: RepositoryMode
        get() = BackendDebugConfig.settings.repositoryMode

    val mediaRepository: MediaRepository
        get() = when (currentMode) {
            RepositoryMode.FAKE -> FakeMediaRepositoryShell()
            RepositoryMode.REAL -> RealMediaRepository(RemoteServiceFactory.mediaApi)
        }

    val postRepository: PostRepository
        get() = when (currentMode) {
            RepositoryMode.FAKE -> FakePostRepositoryShell()
            RepositoryMode.REAL -> RealPostRepository(RemoteServiceFactory.postApi)
        }

    val albumRepository: AlbumRepository
        get() = when (currentMode) {
            RepositoryMode.FAKE -> FakeAlbumRepositoryShell()
            RepositoryMode.REAL -> RealAlbumRepository(RemoteServiceFactory.albumApi)
        }

    val commentRepository: CommentRepository
        get() = when (currentMode) {
            RepositoryMode.FAKE -> FakeCommentRepositoryShell()
            RepositoryMode.REAL -> RealCommentRepository(RemoteServiceFactory.commentApi)
        }

    val trashRepository: TrashRepository
        get() = when (currentMode) {
            RepositoryMode.FAKE -> FakeTrashRepositoryShell()
            RepositoryMode.REAL -> RealTrashRepository(RemoteServiceFactory.trashApi)
        }

    val uploadRepository: UploadRepository
        get() = when (currentMode) {
            RepositoryMode.FAKE -> FakeUploadRepositoryShell()
            RepositoryMode.REAL -> RealUploadRepository(RemoteServiceFactory.uploadApi)
        }

    val authRepository: AuthRepository
        get() = when (currentMode) {
            RepositoryMode.FAKE -> FakeAuthRepositoryShell()
            RepositoryMode.REAL -> RealAuthRepository(RemoteServiceFactory.authApi)
        }
}
