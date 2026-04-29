package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Immutable
data class ViewerPreferenceState(
    val hideOverlaysWhenZoomed: Boolean = true,
    val autoPauseVideoOnMediaSwitch: Boolean = true,
)

@Immutable
data class SettingsUiState(
    val defaultPhotoFeedDensity: PhotoFeedDensity = PhotoFeedDensity.COMFORT_3,
    val defaultAlbumGridDensity: AlbumGridDensity = AlbumGridDensity.COZY_2,
    val viewerPreferences: ViewerPreferenceState = ViewerPreferenceState(),
)

object FakeSettingsRepository {
    private var settingsUiState by mutableStateOf(SettingsUiState())

    fun getSettingsState(): SettingsUiState = settingsUiState

    fun updateDefaultPhotoFeedDensity(density: PhotoFeedDensity) {
        settingsUiState = settingsUiState.copy(defaultPhotoFeedDensity = density)
    }

    fun updateDefaultAlbumGridDensity(density: AlbumGridDensity) {
        settingsUiState = settingsUiState.copy(defaultAlbumGridDensity = density)
    }

    fun updateHideViewerOverlaysWhenZoomed(enabled: Boolean) {
        settingsUiState = settingsUiState.copy(
            viewerPreferences = settingsUiState.viewerPreferences.copy(
                hideOverlaysWhenZoomed = enabled,
            ),
        )
    }

    fun updateAutoPauseVideoOnMediaSwitch(enabled: Boolean) {
        settingsUiState = settingsUiState.copy(
            viewerPreferences = settingsUiState.viewerPreferences.copy(
                autoPauseVideoOnMediaSwitch = enabled,
            ),
        )
    }
}
