package com.example.yingshi.feature.photos

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.data.model.RemotePushPreference
import com.example.yingshi.data.model.RemotePushDiagnostics
import com.example.yingshi.data.remote.result.ApiResult
import com.example.yingshi.data.repository.RepositoryProvider

@Immutable
data class ViewerPreferenceState(
    val hideOverlaysWhenZoomed: Boolean = true,
    val autoPauseVideoOnMediaSwitch: Boolean = true,
    val autoLongImageReading: Boolean = true,
)

enum class ShareDeliveryPreference(
    val label: String,
) {
    SMART("智能默认"),
    DIRECT_FILES("直接分享"),
    ZIP_PACKAGE("压缩包"),
}

enum class ShareImageQualityPreference(
    val label: String,
) {
    ORIGINAL("原图"),
    PREVIEW("缩略图"),
}

@Immutable
data class SharePreferenceState(
    val deliveryPreference: ShareDeliveryPreference = ShareDeliveryPreference.SMART,
    val imageQualityPreference: ShareImageQualityPreference = ShareImageQualityPreference.ORIGINAL,
    val zipThreshold: Int = 10,
    val includeVideos: Boolean = true,
) {
    fun shouldZip(itemCount: Int): Boolean {
        return when (deliveryPreference) {
            ShareDeliveryPreference.SMART -> itemCount >= zipThreshold
            ShareDeliveryPreference.DIRECT_FILES -> false
            ShareDeliveryPreference.ZIP_PACKAGE -> true
        }
    }
}

enum class MediaTimePreference(
    val label: String,
) {
    CAPTURED_FIRST("拍摄优先"),
    IMPORTED_FIRST("导入优先"),
}

@Immutable
data class InteractionPreferenceState(
    val hapticEnabled: Boolean = true,
    val followSystemReducedMotion: Boolean = true,
)

@Immutable
data class PushPreferenceState(
    val photosContentUpdate: Boolean = true,
    val photosComment: Boolean = true,
    val photosDelete: Boolean = false,
    val photosSystem: Boolean = false,
    val lifeTrace: Boolean = true,
    val lifeLedger: Boolean = false,
    val lifeChat: Boolean = false,
    val lifeSystem: Boolean = false,
)

@Immutable
data class PushDiagnosticsState(
    val status: String = "待检查",
    val detail: String = "进入设置或点击重查后，会读取服务端最近的推送投递记录。",
)

@Immutable
data class SettingsUiState(
    val defaultPhotoFeedDensity: PhotoFeedDensity = PhotoFeedDensity.COMFORT_3,
    val defaultAlbumGridDensity: AlbumGridDensity = AlbumGridDensity.COZY_2,
    val defaultSystemMediaDensity: PhotoFeedDensity = PhotoFeedDensity.COMFORT_3,
    val viewerPreferences: ViewerPreferenceState = ViewerPreferenceState(),
    val sharePreferences: SharePreferenceState = SharePreferenceState(),
    val mediaTimePreference: MediaTimePreference = MediaTimePreference.CAPTURED_FIRST,
    val interactionPreferences: InteractionPreferenceState = InteractionPreferenceState(),
    val pushPreferences: PushPreferenceState = PushPreferenceState(),
    val pushDiagnostics: PushDiagnosticsState = PushDiagnosticsState(),
)

object SettingsRepository {
    private const val PrefsName = "yingshi_settings"
    private const val KeyPhotoFeedDensity = "photo_feed_density"
    private const val KeyAlbumGridDensity = "album_grid_density"
    private const val KeySystemMediaDensity = "system_media_density"
    private const val KeyHideOverlaysWhenZoomed = "hide_overlays_when_zoomed"
    private const val KeyAutoPauseVideoOnSwitch = "auto_pause_video_on_switch"
    private const val KeyAutoLongImageReading = "auto_long_image_reading"
    private const val KeyShareDeliveryPreference = "share_delivery_preference"
    private const val KeyShareImageQualityPreference = "share_image_quality_preference"
    private const val KeyShareZipThreshold = "share_zip_threshold"
    private const val KeyShareIncludeVideos = "share_include_videos"
    private const val KeyMediaTimePreference = "media_time_preference"
    private const val KeyHapticEnabled = "haptic_enabled"
    private const val KeyFollowSystemReducedMotion = "follow_system_reduced_motion"
    private const val KeyPushPhotosContentUpdate = "push_photos_content_update"
    private const val KeyPushPhotosComment = "push_photos_comment"
    private const val KeyPushPhotosDelete = "push_photos_delete"
    private const val KeyPushPhotosSystem = "push_photos_system"
    private const val KeyPushLifeTrace = "push_life_trace"
    private const val KeyPushLifeLedger = "push_life_ledger"
    private const val KeyPushLifeChat = "push_life_chat"
    private const val KeyPushLifeSystem = "push_life_system"

    private var preferences: SharedPreferences? = null
    private var settingsUiState by mutableStateOf(SettingsUiState())

    fun init(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        preferences = prefs
        settingsUiState = prefs.readSettingsState()
    }

    fun getSettingsState(): SettingsUiState = settingsUiState

    fun updateDefaultPhotoFeedDensity(density: PhotoFeedDensity) {
        updateState { it.copy(defaultPhotoFeedDensity = density) }
    }

    fun updateDefaultAlbumGridDensity(density: AlbumGridDensity) {
        updateState { it.copy(defaultAlbumGridDensity = density) }
    }

    fun updateDefaultSystemMediaDensity(density: PhotoFeedDensity) {
        updateState { it.copy(defaultSystemMediaDensity = density) }
    }

    fun updateHideViewerOverlaysWhenZoomed(enabled: Boolean) {
        updateState {
            it.copy(
                viewerPreferences = it.viewerPreferences.copy(
                    hideOverlaysWhenZoomed = enabled,
                ),
            )
        }
    }

    fun updateAutoPauseVideoOnMediaSwitch(enabled: Boolean) {
        updateState {
            it.copy(
                viewerPreferences = it.viewerPreferences.copy(
                    autoPauseVideoOnMediaSwitch = enabled,
                ),
            )
        }
    }

    fun updateAutoLongImageReading(enabled: Boolean) {
        updateState {
            it.copy(
                viewerPreferences = it.viewerPreferences.copy(
                    autoLongImageReading = enabled,
                ),
            )
        }
    }

    fun updateShareDeliveryPreference(preference: ShareDeliveryPreference) {
        updateState {
            it.copy(
                sharePreferences = it.sharePreferences.copy(
                    deliveryPreference = preference,
                ),
            )
        }
    }

    fun updateShareImageQualityPreference(preference: ShareImageQualityPreference) {
        updateState {
            it.copy(
                sharePreferences = it.sharePreferences.copy(
                    imageQualityPreference = preference,
                ),
            )
        }
    }

    fun updateShareZipThreshold(threshold: Int) {
        updateState {
            it.copy(
                sharePreferences = it.sharePreferences.copy(
                    zipThreshold = threshold.coerceAtLeast(2),
                ),
            )
        }
    }

    fun updateShareIncludeVideos(enabled: Boolean) {
        updateState {
            it.copy(
                sharePreferences = it.sharePreferences.copy(
                    includeVideos = enabled,
                ),
            )
        }
    }

    fun updateMediaTimePreference(preference: MediaTimePreference) {
        updateState { it.copy(mediaTimePreference = preference) }
    }

    fun updateHapticEnabled(enabled: Boolean) {
        updateState {
            it.copy(
                interactionPreferences = it.interactionPreferences.copy(
                    hapticEnabled = enabled,
                ),
            )
        }
    }

    fun updateFollowSystemReducedMotion(enabled: Boolean) {
        updateState {
            it.copy(
                interactionPreferences = it.interactionPreferences.copy(
                    followSystemReducedMotion = enabled,
                ),
            )
        }
    }

    fun updatePushPreferences(transform: (PushPreferenceState) -> PushPreferenceState) {
        updateState { it.copy(pushPreferences = transform(it.pushPreferences)) }
    }

    fun isPushEnabled(module: String?, category: String?): Boolean {
        return settingsUiState.pushPreferences.isEnabled(module, category)
    }

    suspend fun refreshPushPreferencesFromRemote(): Boolean {
        return when (val result = RepositoryProvider.pushPreferenceRepository.getPushPreferences()) {
            is ApiResult.Success -> {
                updateState { state ->
                    state.copy(pushPreferences = state.pushPreferences.applyRemote(result.data))
                }
                refreshPushDiagnosticsFromRemote()
                true
            }
            is ApiResult.Error -> false
            ApiResult.Loading -> true
        }
    }

    suspend fun refreshPushDiagnosticsFromRemote(): Boolean {
        return when (val result = RepositoryProvider.pushPreferenceRepository.getPushDiagnostics()) {
            is ApiResult.Success -> {
                updateState { state ->
                    state.copy(pushDiagnostics = result.data.toPushDiagnosticsState())
                }
                true
            }
            is ApiResult.Error -> {
                updateState { state ->
                    state.copy(
                        pushDiagnostics = PushDiagnosticsState(
                            status = "读取失败",
                            detail = result.message,
                        ),
                    )
                }
                false
            }
            ApiResult.Loading -> true
        }
    }

    suspend fun updatePushPreference(
        module: String,
        category: String,
        enabled: Boolean,
        transform: (PushPreferenceState) -> PushPreferenceState,
    ): Boolean {
        val previous = settingsUiState.pushPreferences
        updatePushPreferences(transform)
        return when (
            val result = RepositoryProvider.pushPreferenceRepository.updatePushPreference(
                module = module,
                category = category,
                enabled = enabled,
            )
        ) {
            is ApiResult.Success -> {
                updateState { state ->
                    state.copy(pushPreferences = state.pushPreferences.applyRemote(result.data))
                }
                refreshPushDiagnosticsFromRemote()
                true
            }
            is ApiResult.Error -> {
                updateState { it.copy(pushPreferences = previous) }
                false
            }
            ApiResult.Loading -> true
        }
    }

    private fun updateState(transform: (SettingsUiState) -> SettingsUiState) {
        val nextState = transform(settingsUiState)
        settingsUiState = nextState
        preferences?.edit()?.apply {
            putString(KeyPhotoFeedDensity, nextState.defaultPhotoFeedDensity.name)
            putString(KeyAlbumGridDensity, nextState.defaultAlbumGridDensity.name)
            putString(KeySystemMediaDensity, nextState.defaultSystemMediaDensity.name)
            putBoolean(
                KeyHideOverlaysWhenZoomed,
                nextState.viewerPreferences.hideOverlaysWhenZoomed,
            )
            putBoolean(
                KeyAutoPauseVideoOnSwitch,
                nextState.viewerPreferences.autoPauseVideoOnMediaSwitch,
            )
            putBoolean(
                KeyAutoLongImageReading,
                nextState.viewerPreferences.autoLongImageReading,
            )
            putString(
                KeyShareDeliveryPreference,
                nextState.sharePreferences.deliveryPreference.name,
            )
            putString(
                KeyShareImageQualityPreference,
                nextState.sharePreferences.imageQualityPreference.name,
            )
            putInt(KeyShareZipThreshold, nextState.sharePreferences.zipThreshold)
            putBoolean(KeyShareIncludeVideos, nextState.sharePreferences.includeVideos)
            putString(KeyMediaTimePreference, nextState.mediaTimePreference.name)
            putBoolean(
                KeyHapticEnabled,
                nextState.interactionPreferences.hapticEnabled,
            )
            putBoolean(
                KeyFollowSystemReducedMotion,
                nextState.interactionPreferences.followSystemReducedMotion,
            )
            putBoolean(KeyPushPhotosContentUpdate, nextState.pushPreferences.photosContentUpdate)
            putBoolean(KeyPushPhotosComment, nextState.pushPreferences.photosComment)
            putBoolean(KeyPushPhotosDelete, nextState.pushPreferences.photosDelete)
            putBoolean(KeyPushPhotosSystem, nextState.pushPreferences.photosSystem)
            putBoolean(KeyPushLifeTrace, nextState.pushPreferences.lifeTrace)
            putBoolean(KeyPushLifeLedger, nextState.pushPreferences.lifeLedger)
            putBoolean(KeyPushLifeChat, nextState.pushPreferences.lifeChat)
            putBoolean(KeyPushLifeSystem, nextState.pushPreferences.lifeSystem)
            apply()
        }
    }

    private fun SharedPreferences.readSettingsState(): SettingsUiState {
        val photoFeedDensity = getString(KeyPhotoFeedDensity, null)
            .toEnumOrNull<PhotoFeedDensity>()
            ?: PhotoFeedDensity.COMFORT_3
        return SettingsUiState(
            defaultPhotoFeedDensity = photoFeedDensity,
            defaultAlbumGridDensity = getString(KeyAlbumGridDensity, null)
                .toEnumOrNull<AlbumGridDensity>()
                ?: AlbumGridDensity.COZY_2,
            defaultSystemMediaDensity = getString(KeySystemMediaDensity, null)
                .toEnumOrNull<PhotoFeedDensity>()
                ?: photoFeedDensity,
            viewerPreferences = ViewerPreferenceState(
                hideOverlaysWhenZoomed = getBoolean(KeyHideOverlaysWhenZoomed, true),
                autoPauseVideoOnMediaSwitch = getBoolean(KeyAutoPauseVideoOnSwitch, true),
                autoLongImageReading = getBoolean(KeyAutoLongImageReading, true),
            ),
            sharePreferences = SharePreferenceState(
                deliveryPreference = getString(KeyShareDeliveryPreference, null)
                    .toEnumOrNull<ShareDeliveryPreference>()
                    ?: ShareDeliveryPreference.SMART,
                imageQualityPreference = getString(KeyShareImageQualityPreference, null)
                    .toEnumOrNull<ShareImageQualityPreference>()
                    ?: ShareImageQualityPreference.ORIGINAL,
                zipThreshold = getInt(KeyShareZipThreshold, 10).coerceAtLeast(2),
                includeVideos = getBoolean(KeyShareIncludeVideos, true),
            ),
            mediaTimePreference = getString(KeyMediaTimePreference, null)
                .toEnumOrNull<MediaTimePreference>()
                ?: MediaTimePreference.CAPTURED_FIRST,
            interactionPreferences = InteractionPreferenceState(
                hapticEnabled = getBoolean(KeyHapticEnabled, true),
                followSystemReducedMotion = getBoolean(
                    KeyFollowSystemReducedMotion,
                    true,
                ),
            ),
            pushPreferences = PushPreferenceState(
                photosContentUpdate = getBoolean(KeyPushPhotosContentUpdate, true),
                photosComment = getBoolean(KeyPushPhotosComment, true),
                photosDelete = getBoolean(KeyPushPhotosDelete, false),
                photosSystem = getBoolean(KeyPushPhotosSystem, false),
                lifeTrace = getBoolean(KeyPushLifeTrace, true),
                lifeLedger = getBoolean(KeyPushLifeLedger, false),
                lifeChat = getBoolean(KeyPushLifeChat, false),
                lifeSystem = getBoolean(KeyPushLifeSystem, false),
            ),
        )
    }
}

fun PushPreferenceState.isEnabled(module: String?, category: String?): Boolean {
    return when ("${module.orEmpty()}:${category.orEmpty()}".lowercase()) {
        "photos:content_update" -> photosContentUpdate
        "photos:comment" -> photosComment
        "photos:delete" -> photosDelete
        "photos:system" -> photosSystem
        "life:trace" -> lifeTrace
        "life:ledger" -> lifeLedger
        "life:chat" -> lifeChat
        "life:system" -> lifeSystem
        else -> true
    }
}

private fun PushPreferenceState.applyRemote(
    preferences: List<RemotePushPreference>,
): PushPreferenceState {
    return preferences.fold(this) { state, preference ->
        when ("${preference.module}:${preference.category}".lowercase()) {
            "photos:content_update" -> state.copy(photosContentUpdate = preference.enabled)
            "photos:comment" -> state.copy(photosComment = preference.enabled)
            "photos:delete" -> state.copy(photosDelete = preference.enabled)
            "photos:system" -> state.copy(photosSystem = preference.enabled)
            "life:trace" -> state.copy(lifeTrace = preference.enabled)
            "life:ledger" -> state.copy(lifeLedger = preference.enabled)
            "life:chat" -> state.copy(lifeChat = preference.enabled)
            "life:system" -> state.copy(lifeSystem = preference.enabled)
            else -> state
        }
    }
}

private fun RemotePushDiagnostics.toPushDiagnosticsState(): PushDiagnosticsState {
    val latest = recentDeliveries.firstOrNull()
    if (latest == null) {
        return PushDiagnosticsState(
            status = "暂无记录",
            detail = "当前账号本机已注册 $currentUserEnabledDeviceCount 台设备；共享空间已启用 $libraryEnabledDeviceCount 台设备；self fallback ${if (selfFallbackEnabled) "已开" else "已关"}。",
        )
    }
    return PushDiagnosticsState(
        status = latest.status.toPushStatusLabel(),
        detail = buildString {
            append("${latest.module}/${latest.category}：${latest.reason.toPushReasonLabel()}。")
            append(" 目标 ${latest.targetDeviceCount}，FCM 成功 ${latest.successfulCount}/${latest.attemptedCount}。")
            if (latest.usedSelfFallback) {
                append(" 本次走了本机兜底。")
            }
            append(" 共享空间设备 $libraryEnabledDeviceCount，本机 $currentUserEnabledDeviceCount。")
        },
    )
}

private fun String.toPushStatusLabel(): String {
    return when (this) {
        "sent" -> "已发送"
        "no_target" -> "无目标"
        "skipped" -> "已跳过"
        "failed" -> "发送失败"
        else -> this
    }
}

private fun String.toPushReasonLabel(): String {
    return when (this) {
        "fcm_accepted" -> "FCM 已接受"
        "sender_skipped" -> "发送器未启用或跳过"
        "invalid_token" -> "token 已失效"
        "fcm_failed" -> "FCM 返回失败"
        "no_enabled_device_token" -> "没有已启用设备 token"
        "no_partner_token_self_fallback_disabled" -> "没有对方设备，且本机兜底关闭"
        "no_partner_token" -> "没有对方设备 token"
        "partner_preference_disabled" -> "对方关闭了该类推送"
        "no_eligible_token" -> "没有符合条件的 token"
        else -> this
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? {
    val normalized = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { enumValueOf<T>(normalized) }.getOrNull()
}
