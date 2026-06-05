package com.example.yingshi.feature.photos

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.yingshi.data.model.RemoteCurrentUser
import com.example.yingshi.data.model.RemotePartnerProfile
import com.example.yingshi.data.repository.fakeAuthCurrentProfile
import com.example.yingshi.data.repository.fakeAuthLoginProfile

private const val DefaultFakeCollaboratorAccount = "demo.a@yingshi.local"

@Immutable
data class CollaboratorIdentityUiModel(
    val userId: String,
    val account: String? = null,
    val displayName: String,
    val avatarUrl: String?,
    val isCurrentUser: Boolean,
    val shortLabel: String,
)

data class CollaboratorDirectorySnapshot(
    val currentUser: CollaboratorIdentityUiModel?,
    val partner: CollaboratorIdentityUiModel?,
) {
    val all: List<CollaboratorIdentityUiModel>
        get() = listOfNotNull(currentUser, partner)

    fun resolve(userId: String?): CollaboratorIdentityUiModel? {
        if (userId.isNullOrBlank()) return null
        return all.firstOrNull { identity ->
            identity.userId == userId ||
                identity.account?.equals(userId, ignoreCase = true) == true
        }
    }

    fun resolveOrdered(
        userIds: Iterable<String?>,
        limit: Int = all.size,
    ): List<CollaboratorIdentityUiModel> {
        val resolved = linkedMapOf<String, CollaboratorIdentityUiModel>()
        userIds.forEach { userId ->
            resolve(userId)?.let { identity ->
                resolved.putIfAbsent(identity.userId, identity)
            }
        }
        return resolved.values
            .sortedWith(compareByDescending<CollaboratorIdentityUiModel> { it.isCurrentUser }.thenBy { it.shortLabel })
            .take(limit.coerceAtLeast(0))
    }
}

object CollaboratorDirectoryStore {
    var currentUser by mutableStateOf<RemoteCurrentUser?>(null)
        private set

    fun update(user: RemoteCurrentUser?) {
        currentUser = user
    }

    fun snapshot(fallbackToFakeProfile: Boolean = true): CollaboratorDirectorySnapshot {
        val resolvedUser = currentUser ?: resolveCollaboratorFallbackProfile(fallbackToFakeProfile)
        return CollaboratorDirectorySnapshot(
            currentUser = resolvedUser?.toCollaboratorIdentity(isCurrentUser = true),
            partner = resolvedUser?.partner?.toCollaboratorIdentity(),
        )
    }
}

fun collaboratorDirectorySnapshot(
    currentUser: RemoteCurrentUser?,
    fallbackToFakeProfile: Boolean = true,
): CollaboratorDirectorySnapshot {
    val resolvedUser = currentUser ?: resolveCollaboratorFallbackProfile(fallbackToFakeProfile)
    return CollaboratorDirectorySnapshot(
        currentUser = resolvedUser?.toCollaboratorIdentity(isCurrentUser = true),
        partner = resolvedUser?.partner?.toCollaboratorIdentity(),
    )
}

fun defaultSelectedCollaboratorUserIds(
    currentUser: RemoteCurrentUser?,
    fallbackToFakeProfile: Boolean = true,
): Set<String> {
    return collaboratorDirectorySnapshot(
        currentUser = currentUser,
        fallbackToFakeProfile = fallbackToFakeProfile,
    ).all.mapTo(linkedSetOf()) { it.userId }
}

fun toggleCollaboratorSelection(
    currentSelection: Set<String>,
    toggledUserId: String,
    allUserIds: Set<String>,
): Set<String> {
    if (toggledUserId !in allUserIds) return currentSelection.ifEmpty { allUserIds }
    val effectiveSelection = currentSelection.ifEmpty { allUserIds }
    return when {
        toggledUserId !in effectiveSelection -> effectiveSelection + toggledUserId
        effectiveSelection.size <= 1 -> allUserIds
        else -> effectiveSelection - toggledUserId
    }
}

fun toggleCollaboratorSelectionKeepingEmpty(
    currentSelection: Set<String>,
    toggledUserId: String,
    allUserIds: Set<String>,
): Set<String> {
    if (toggledUserId !in allUserIds) {
        return currentSelection.filterTo(linkedSetOf()) { it in allUserIds }
    }
    val effectiveSelection = currentSelection.filterTo(linkedSetOf()) { it in allUserIds }
    return if (toggledUserId in effectiveSelection) {
        effectiveSelection - toggledUserId
    } else {
        effectiveSelection + toggledUserId
    }
}

fun defaultCollaboratorSelection(
    allUserIds: Set<String>,
): Set<String> {
    return allUserIds.filterTo(linkedSetOf()) { it.isNotBlank() }
}

fun normalizeCollaboratorSelectionKeepingEmpty(
    selectedUserIds: Set<String>,
    allUserIds: Set<String>,
): Set<String> {
    if (allUserIds.isEmpty()) return emptySet()
    return selectedUserIds.filterTo(linkedSetOf()) { it in allUserIds }
}

fun normalizedCollaboratorSelection(
    selectedUserIds: Set<String>,
    allUserIds: Set<String>,
): Set<String> {
    if (allUserIds.isEmpty()) return emptySet()
    val normalized = selectedUserIds.filterTo(linkedSetOf()) { it in allUserIds }
    return if (normalized.isEmpty()) allUserIds else normalized
}

fun isAllCollaboratorsSelected(
    selectedUserIds: Set<String>,
    allUserIds: Set<String>,
): Boolean {
    if (allUserIds.isEmpty()) return false
    val effectiveSelection = normalizeCollaboratorSelectionKeepingEmpty(selectedUserIds, allUserIds)
    return effectiveSelection.containsAll(allUserIds) && effectiveSelection.size == allUserIds.size
}

fun initialOrNormalizedCollaboratorSelection(
    selectedUserIds: Set<String>,
    allUserIds: Set<String>,
    initialized: Boolean,
): Set<String> {
    return if (!initialized) {
        defaultCollaboratorSelection(allUserIds)
    } else {
        normalizeCollaboratorSelectionKeepingEmpty(
            selectedUserIds = selectedUserIds,
            allUserIds = allUserIds,
        )
    }
}

private fun resolveCollaboratorFallbackProfile(
    fallbackToFakeProfile: Boolean,
): RemoteCurrentUser? {
    if (!fallbackToFakeProfile) return null
    return fakeAuthCurrentProfile() ?: fakeAuthLoginProfile(DefaultFakeCollaboratorAccount)
}

private fun RemoteCurrentUser.toCollaboratorIdentity(
    isCurrentUser: Boolean,
): CollaboratorIdentityUiModel {
    return CollaboratorIdentityUiModel(
        userId = userId,
        account = account,
        displayName = displayName.ifBlank { account.substringBefore('@').ifBlank { "我" } },
        avatarUrl = avatarUrl,
        isCurrentUser = isCurrentUser,
        shortLabel = "我",
    )
}

private fun RemotePartnerProfile.toCollaboratorIdentity(): CollaboratorIdentityUiModel {
    return CollaboratorIdentityUiModel(
        userId = userId,
        account = account,
        displayName = displayName.ifBlank { account.substringBefore('@').ifBlank { "对方" } },
        avatarUrl = avatarUrl,
        isCurrentUser = false,
        shortLabel = "对方",
    )
}
