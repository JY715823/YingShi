# YingShi Android

Updated: 2026-05-25

This repository hosts the YingShi Android client. The app has moved beyond placeholder-shell status and now has a usable `REAL` integration path for auth, photos, posts, comments, uploads, and trash.

## Current App State

- `debug`, `profile`, and `optimizedDebug` default to `REAL`
- runtime still supports switching `FAKE / REAL` inside the app
- auth session data is persisted locally and restored on app launch
- changing backend `Base URL` clears the old session and rebuilds the network graph
- fake repositories are intentionally retained for UI-only iteration and isolation

## Latest Progress

- The primary shell is stable as `Home / Photos / Life / Me`.
- The photos module already covers feed, albums, posts, viewer, comments, uploads, and trash.
- Real auth is connected for login, refresh-token, logout, current-user, and profile update.
- The `Me` section, settings, cache management, and backend diagnostics are usable.
- The life page now keeps only ledger and chat-viewer entries.
- The old anniversary entry is intentionally removed from the life page.
- Real repositories are aligned with backend support for posts list, refresh-token, upload task status/confirm/cancel, and trash pending-cleanup flows.

## Current Frontend Backend Alignment

Already consumed in Android `REAL` mode:

- auth: `login / refresh-token / me / logout / me/profile`
- albums: list and album-posts
- posts: list, detail, create, update, cover, media-order, add-media, delete
- media: feed, backend file delivery, delete-from-post, system delete
- comments: post comment and media comment create/edit/delete
- trash: list, detail, restore, remove, purge, undo-remove, pending-cleanup
- upload: token, multipart upload, task status, confirm, cancel

Backend-ready but not fully exposed in Android UI:

- avatar upload / avatar display
- notifications API
- centralized automatic refresh-and-retry networking behavior

## Docs

- [Implemented Features](E:/Study/App/YingShi/docs/implementation/implemented-features.md)
- [Light Color System V1](E:/Study/App/YingShi/docs/design/light-color-system-v1.md)
- [Current Task](E:/Study/App/YingShi/docs/implementation/current-task.md)
- [Frontend Backend Testing Guide](E:/Study/App/YingShi/docs/integration/frontend-backend-testing-guide.md)
- [API Overview](E:/Study/App/YingShi/docs/contracts/api-overview.md)
- [Auth Contract](E:/Study/App/YingShi/docs/contracts/auth-api.md)
- [Upload Contract](E:/Study/App/YingShi/docs/contracts/upload-api.md)
- [Notification Contract](E:/Study/App/YingShi/docs/contracts/notification-api.md)
- [Trash Contract](E:/Study/App/YingShi/docs/contracts/trash-api.md)
- [Collaboration Notes](E:/Study/App/YingShi/AGENTS.md)

## Build

Build debug:

```powershell
cd E:\Study\App\YingShi
.\gradlew.bat assembleDebug
```

Kotlin compile only:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

## Backend Pairing

Recommended backend pairing for daily integration:

- backend repo: `E:\Study\App\YingShi-Server`
- backend profile: `docker-local`
- database: PostgreSQL in Docker
- storage: MinIO in Docker

Inside the app:

1. Open `My -> Settings -> Backend Debug Diagnostics`
2. Confirm or edit `Base URL`
3. Tap save-and-relogin
4. Run the health check
5. Switch to `REAL`
6. Reopen the target page to reload with the active repository session
