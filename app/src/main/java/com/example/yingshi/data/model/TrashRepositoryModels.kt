package com.example.yingshi.data.model

data class RemoteTrashDetail(
    val item: RemoteTrashItem,
    val canRestore: Boolean = true,
    val canMoveOutOfTrash: Boolean = true,
    val pendingCleanup: RemotePendingCleanup? = null,
)

data class RemotePendingCleanup(
    val trashItemId: String,
    val removedAtMillis: Long,
    val undoDeadlineMillis: Long,
    val item: RemoteTrashItem,
)
