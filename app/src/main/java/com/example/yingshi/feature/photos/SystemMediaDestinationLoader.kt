package com.example.yingshi.feature.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import com.example.yingshi.data.repository.RepositoryMode
import com.example.yingshi.data.repository.RepositoryProvider
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.result.ApiResult

data class SystemMediaDestinationUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val albums: List<AlbumSummaryUiModel> = emptyList(),
    val posts: List<AlbumPostCardUiModel> = emptyList(),
)

@Composable
fun rememberSystemMediaDestinationUiState(): State<SystemMediaDestinationUiState> {
    val mode = RepositoryProvider.currentMode
    val backendMutationVersion = RealBackendMutationBus.version.collectAsState().value
    return produceState(
        initialValue = initialDestinationState(mode),
        key1 = mode,
        key2 = AuthSessionManager.isLoggedIn,
        key3 = backendMutationVersion,
    ) {
        if (mode == RepositoryMode.FAKE) {
            value = SystemMediaDestinationUiState(
                albums = FakeAlbumRepository.getAlbums(),
                posts = FakeAlbumRepository.getPosts(),
            )
            return@produceState
        }

        if (!AuthSessionManager.isLoggedIn) {
            value = SystemMediaDestinationUiState(
                errorMessage = "REAL 模式需要先登录，才能选择后端帖子作为导入目标。",
            )
            return@produceState
        }

        value = SystemMediaDestinationUiState(isLoading = true)
        val albumRepository = RepositoryProvider.albumRepository
        when (val albumsResult = albumRepository.getAlbums()) {
            is ApiResult.Success -> {
                val albumSummaries = albumsResult.data.map { it.toAlbumSummaryUiModel() }
                val postsById = linkedMapOf<String, AlbumPostCardUiModel>()
                var firstError: String? = null
                albumsResult.data.forEach { album ->
                    when (val postsResult = albumRepository.getAlbumPosts(album.albumId)) {
                        is ApiResult.Success -> {
                            postsResult.data.forEach { post ->
                                postsById.putIfAbsent(
                                    post.postId,
                                    post.toAlbumPostCardUiModel(album.albumId),
                                )
                            }
                        }
                        is ApiResult.Error -> {
                            if (firstError == null) {
                                firstError = postsResult.toBackendUiMessage("读取相册帖子失败。")
                            }
                        }
                        ApiResult.Loading -> Unit
                    }
                }
                value = SystemMediaDestinationUiState(
                    albums = albumSummaries,
                    posts = postsById.values.sortedByDescending { it.postDisplayTimeMillis },
                    errorMessage = firstError,
                )
            }
            is ApiResult.Error -> {
                value = SystemMediaDestinationUiState(
                    errorMessage = albumsResult.toBackendUiMessage("读取后端相册失败。"),
                )
            }
            ApiResult.Loading -> Unit
        }
    }
}

private fun initialDestinationState(mode: RepositoryMode): SystemMediaDestinationUiState {
    return if (mode == RepositoryMode.FAKE) {
        SystemMediaDestinationUiState(
            albums = FakeAlbumRepository.getAlbums(),
            posts = FakeAlbumRepository.getPosts(),
        )
    } else {
        SystemMediaDestinationUiState(isLoading = true)
    }
}
