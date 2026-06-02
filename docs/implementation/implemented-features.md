# Implemented Features

Updated: 2026-05-25

This document records the current truth of the Android codebase. If older PRDs or task notes disagree with the code, trust this file and the repository.

## 1. App Shell And Navigation

- the primary tabs are `Home / Photos / Life / Me`
- the photos area has stable secondary entry points for feed, albums, and trash
- full-screen routes such as viewer, post detail, upload-related pages, notifications, settings, and diagnostics hide the bottom shell when needed
- repository mode switching is centralized so `FAKE / REAL` changes rebuild the app data graph cleanly

## 2. Auth, Me, And Settings

- real auth endpoints are wired for login, refresh-token, current-user, logout, profile update, and avatar upload
- auth session data is persisted locally and restored on app launch
- protected requests now have centralized automatic refresh-token handling and retry at the OkHttp layer
- switching backend `Base URL` clears the old auth state
- the `Me` area shows current user, shared-library, partner, backend mode, and backend address
- profile view and edit flows are usable, including backend avatar display and avatar upload
- settings exposes preferences, cache management, and backend diagnostics

## 3. Photos Main Flow

- both `FAKE` and `REAL` photo flows are supported
- `REAL` mode already loads media feed data from the backend
- feed grouping, density switching, multi-select, and target actions are implemented
- selected media can be turned into a new post, added to an existing post, or moved into trash flows

## 4. Viewer

- viewer can open from feed, post detail, system media, and upload-related flows
- mixed image and video browsing is supported
- real backend media delivery is used through backend file endpoints
- image preview, original loading, video cover, video playback, and media comments are handled in the viewer path
- media can open its owning post or trigger delete/trash actions

## 5. Albums, Posts, Comments, And Editing

- album list and album-post list are connected
- generic post list is connected through `GET /api/posts`
- post detail shows media and comments
- post comments and media comments support create, edit, and delete
- create-post and edit-post flows support title, summary, display time, album, cover, media order, add-media, remove-media, and delete-post

## 6. Notifications

- the photos top-bar bell is wired
- notification list and notification detail now consume the real backend notification API in `REAL` mode
- notification read-state and mark-all-read both write back to the backend
- notification routing currently supports:
  - post-related notifications -> post detail
  - trash notifications -> trash or trash detail
  - upload/system notifications -> transfer center or detail fallback
- `FAKE` mode still keeps the local notification seed data for preview and offline shell behavior

## 7. System Media, Uploads, And Transfer Center

- Android `MediaStore` querying is integrated for images and videos
- system media supports filtering, grouping, multi-select, viewer, import, create-post, and add-to-post flows
- real uploads are connected through:
  - `POST /api/uploads/token`
  - `POST /api/uploads/{uploadId}/file`
  - `GET /api/uploads/{uploadId}`
  - `POST /api/uploads/{uploadId}/confirm`
  - `POST /api/uploads/{uploadId}/cancel`
- upload tasks are surfaced in the transfer-center flow

## 8. Trash

- the app handles `postDeleted`, `mediaRemoved`, and `mediaSystemDeleted`
- list, detail, restore, remove, purge, undo-remove, and pending-cleanup flows are connected
- deleted-post detail and deleted-media preview routes are available in both fake and real flows

## 9. Life Module

- the life page is no longer a blank placeholder
- the current entries are:
  - ledger
  - chat viewer
- the old anniversary entry is intentionally removed
- ledger data uses local Room as the on-device cache and syncs with the backend snapshot API in REAL mode
- chat viewer supports importing `QCE ZIP` and browsing conversations offline
- imported chat snapshots hydrate from and sync back to `/api/chat/imported/snapshot` in `REAL` mode

## 10. Real Repository Coverage

- `RealAuthRepository`: login, refreshToken, logout, getCurrentUser, updateCurrentUserProfile, uploadCurrentUserAvatar
- `RealAlbumRepository`: getAlbums, getAlbumPosts
- `RealMediaRepository`: getMediaFeed, getMediaFeedPage, deleteMediaFromPost, systemDeleteMedia
- `RealPostRepository`: getPosts, getPostDetail, createPost, updatePostBasicInfo, setPostCover, updatePostMediaOrder, addMediaToPost, deletePost
- `RealCommentRepository`: post/media comment list plus create/edit/delete
- `RealNotificationRepository`: list, detail, mark-read, mark-all-read
- `RealTrashRepository`: list, detail, restore, remove, purge, undo-remove, pending-cleanup
- `RealUploadRepository`: upload token, multipart upload, getUploadTask, confirmUpload, cancelUpload
- `LedgerRepository` + `LedgerSyncBridge`: shared-library snapshot pull/push
- `ImportedChatRepository` + `ChatSyncBridge`: imported-chat snapshot pull/push

## 11. Intentional Gaps

- ledger uses local Room as cache and syncs through `/api/ledger/snapshot` in `REAL` mode
- large-scale offline sync and conflict handling are outside the current stage
