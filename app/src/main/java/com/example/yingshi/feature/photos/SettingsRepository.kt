package com.example.yingshi.feature.photos

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
data class SettingsUiState(
    val defaultPhotoFeedDensity: PhotoFeedDensity = PhotoFeedDensity.COMFORT_3,
    val defaultAlbumGridDensity: AlbumGridDensity = AlbumGridDensity.COZY_2,
    val defaultSystemMediaDensity: PhotoFeedDensity = PhotoFeedDensity.COMFORT_3,
    val viewerPreferences: ViewerPreferenceState = ViewerPreferenceState(),
    val sharePreferences: SharePreferenceState = SharePreferenceState(),
    val mediaTimePreference: MediaTimePreference = MediaTimePreference.CAPTURED_FIRST,
    val interactionPreferences: InteractionPreferenceState = InteractionPreferenceState(),
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
        )
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? {
    val normalized = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { enumValueOf<T>(normalized) }.getOrNull()
}
