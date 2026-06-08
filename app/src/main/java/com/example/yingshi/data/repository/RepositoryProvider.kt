package com.example.yingshi.data.repository

import com.example.yingshi.data.remote.config.RemoteServiceFactory

enum class RepositoryMode {
    FAKE,
    REAL,
}

object RepositoryProvider {
    val currentMode: RepositoryMode
        get() = RepositoryMode.REAL

    val mediaRepository: MediaRepository
        get() = RealMediaRepository(RemoteServiceFactory.mediaApi)

    val postRepository: PostRepository
        get() = RealPostRepository(RemoteServiceFactory.postApi)

    val albumRepository: AlbumRepository
        get() = RealAlbumRepository(RemoteServiceFactory.albumApi)

    val commentRepository: CommentRepository
        get() = RealCommentRepository(RemoteServiceFactory.commentApi)

    val notificationRepository: NotificationRepository
        get() = RealNotificationRepository(RemoteServiceFactory.notificationApi)

    val trashRepository: TrashRepository
        get() = RealTrashRepository(RemoteServiceFactory.trashApi)

    val uploadRepository: UploadRepository
        get() = RealUploadRepository(RemoteServiceFactory.uploadApi)

    val authRepository: AuthRepository
        get() = RealAuthRepository(RemoteServiceFactory.authApi)

    val lifeConsoleRepository: LifeConsoleRepository
        get() = RealLifeConsoleRepository(RemoteServiceFactory.lifeConsoleApi)
}
