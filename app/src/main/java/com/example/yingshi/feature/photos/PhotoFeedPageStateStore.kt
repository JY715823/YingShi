package com.example.yingshi.feature.photos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class PhotoFeedPageStateStore {
    var pendingScrollTargetMediaId by mutableStateOf<String?>(null)
    var pendingScrollAnchorOriginalIndex by mutableIntStateOf(-1)
    var pendingHighlightNonce by mutableIntStateOf(0)
    var pendingLocateSuccessMessage by mutableStateOf<String?>(null)
    var pendingLocateFailureMessage by mutableStateOf<String?>(null)
    var pendingNewImportedMediaIds by mutableStateOf<Set<String>>(emptySet())
    var pendingNewImportedNonce by mutableIntStateOf(0)
    var pendingRestoredMediaIds by mutableStateOf<Set<String>>(emptySet())
    var pendingRestoredNonce by mutableIntStateOf(0)
    var pendingNotificationMediaIds by mutableStateOf<Set<String>>(emptySet())
    var pendingNotificationNonce by mutableIntStateOf(0)
    var pendingImportHasRetryableItems by mutableStateOf(false)
    var pendingAutoOpenViewer by mutableStateOf(false)
    var pendingAutoOpenComment by mutableStateOf(false)
    var pendingAutoOpenCommentMediaId by mutableStateOf<String?>(null)
    var savedFirstVisibleItemIndex by mutableIntStateOf(0)
    var savedFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    var savedFirstVisibleMediaId by mutableStateOf<String?>(null)
    var savedDensityName by mutableStateOf<String?>(null)
    var collaboratorSelectionInitialized by mutableStateOf(false)
    var selectedCollaboratorUserIds by mutableStateOf<Set<String>>(emptySet())
    var timeBucketHours by mutableIntStateOf(24)
    var visibleMediaIds by mutableStateOf<Set<String>>(emptySet())
}

val GlobalPhotoFeedPageStateStore = PhotoFeedPageStateStore()
