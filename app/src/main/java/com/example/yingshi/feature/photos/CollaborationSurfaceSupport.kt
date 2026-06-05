package com.example.yingshi.feature.photos

fun resolveCollaboratorIdentities(
    directory: CollaboratorDirectorySnapshot,
    userIds: Iterable<String?>,
    limit: Int = directory.all.size,
): List<CollaboratorIdentityUiModel> {
    return directory.resolveOrdered(userIds = userIds, limit = limit)
}

fun filterTrashEntriesByCollaborator(
    entries: List<TrashEntryUiModel>,
    directory: CollaboratorDirectorySnapshot,
    selectedUserIds: Set<String>,
    allUserIds: Set<String>,
): List<TrashEntryUiModel> {
    if (entries.isEmpty() || allUserIds.isEmpty()) return entries
    val effectiveSelection = normalizeCollaboratorSelectionKeepingEmpty(
        selectedUserIds = selectedUserIds,
        allUserIds = allUserIds,
    )
    if (effectiveSelection.isEmpty()) return emptyList()
    val currentUserId = directory.currentUser?.userId
    val showUnknownActors = isAllCollaboratorsSelected(
        selectedUserIds = effectiveSelection,
        allUserIds = allUserIds,
    )
    return entries.filter { entry ->
        val actorUserId = TrashActorHintStore.resolve(entry)
        if (actorUserId.isNullOrBlank()) {
            showUnknownActors || (
                effectiveSelection.size == 1 &&
                    !currentUserId.isNullOrBlank() &&
                    currentUserId in effectiveSelection
                )
        } else {
            val resolvedActorUserId = directory.resolve(actorUserId)?.userId ?: actorUserId
            resolvedActorUserId in effectiveSelection
        }
    }
}

fun resolveTrashActorIdentity(
    entry: TrashEntryUiModel,
    directory: CollaboratorDirectorySnapshot,
): CollaboratorIdentityUiModel? {
    return directory.resolve(TrashActorHintStore.resolve(entry))
        ?: directory.currentUser.takeIf { entry.actorUserId.isNullOrBlank() }
}
