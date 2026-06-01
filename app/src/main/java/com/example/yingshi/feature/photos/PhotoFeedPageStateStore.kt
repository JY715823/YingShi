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
    var pendingImportHasRetryableItems by mutableStateOf(false)
    var savedFirstVisibleItemIndex by mutableIntStateOf(0)
    var savedFirstVisibleItemScrollOffset by mutableIntStateOf(0)
    var savedFirstVisibleMediaId by mutableStateOf<String?>(null)
}

val GlobalPhotoFeedPageStateStore = PhotoFeedPageStateStore()
