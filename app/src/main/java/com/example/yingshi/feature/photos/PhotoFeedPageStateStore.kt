package com.example.yingshi.feature.photos

object PhotoFeedPageStateStore {
    var pendingScrollTargetMediaId: String? = null
    var pendingScrollAnchorOriginalIndex: Int = -1
    var pendingHighlightNonce: Int = 0
    var pendingLocateSuccessMessage: String? = null
    var pendingLocateFailureMessage: String? = null
    var pendingNewImportedMediaIds: Set<String> = emptySet()
    var pendingNewImportedNonce: Int = 0
    var pendingImportHasRetryableItems: Boolean = false
    var savedFirstVisibleItemIndex: Int = 0
    var savedFirstVisibleItemScrollOffset: Int = 0
    var savedFirstVisibleMediaId: String? = null
}
