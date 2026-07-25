package com.example.yingshi.feature.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import com.example.yingshi.data.remote.auth.AuthSessionManager
import com.example.yingshi.data.remote.config.BackendDebugConfig
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider

data class SystemMediaDestinationUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val albums: List<AlbumSummaryUiModel> = emptyList(),
    val posts: List<AlbumPostCardUiModel> = emptyList(),
)

@Composable
fun rememberSystemMediaDestinationUiState(): State<SystemMediaDestinationUiState> {
    val backendMutationEvent = RealBackendMutationBus.latestEvent.collectAsState().value
    return produceState(
        initialValue = initialDestinationState(),
        AuthSessionManager.isLoggedIn,
        backendMutationEvent.version.takeIf { backendMutationEvent.affectsSystemMediaDestinations() } ?: 0,
        BackendDebugConfig.sessionVersion,
    ) {
        if (!AuthSessionManager.isLoggedIn) {
            value = SystemMediaDestinationUiState(
                errorMessage = "请先连接服务，才能选择在线小相册作为导入目标。",
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
                                firstError = postsResult.toBackendUiMessage("读取相册下的小相册失败。")
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
                    errorMessage = albumsResult.toBackendUiMessage("读取相册失败。"),
                )
            }
            ApiResult.Loading -> Unit
        }
    }
}

private fun initialDestinationState(): SystemMediaDestinationUiState {
    return SystemMediaDestinationUiState(isLoading = true)
}
