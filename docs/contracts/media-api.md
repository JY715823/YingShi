# Media API Contract

## Status
- unified with current `yingshi-server` code
- local-dev usable

## Base Rules
- base path: `/api/media`
- bearer auth required for all endpoints
- media feed is deduplicated by media body, not repeated per post
- there is no `GET /api/media/{mediaId}` JSON detail endpoint in current backend

## Media DTO

```json
{
  "mediaId": "media_001",
  "mediaType": "image",
  "type": "image",
  "url": "/api/media/files/media_001",
  "mediaUrl": "/api/media/files/media_001",
  "previewUrl": "/api/media/files/media_001",
  "thumbnailUrl": "/api/media/files/media_001",
  "originalUrl": "/api/media/files/media_001",
  "videoUrl": null,
  "coverUrl": null,
  "mimeType": "image/jpeg",
  "sizeBytes": 3145728,
  "width": 1440,
  "height": 1920,
  "aspectRatio": 0.75,
  "durationMillis": null,
  "displayTimeMillis": 1777412800000,
  "postIds": ["post_001", "post_002"]
}
```

Android REAL compatibility notes:
- Android accepts either `mediaType` or `type` as the media kind hint.
- Android accepts either `url` or `mediaUrl` as the canonical media URL.
- Android accepts either `previewUrl` or `thumbnailUrl` as the thumbnail or poster URL.
- `durationMillis` remains the preferred duration field; Android also tolerates `duration` during this stabilization pass.
- App-content Viewer image preview priority is `thumbnailUrl -> mediaUrl -> originalUrl`.
- App-content Viewer original-load priority is `originalUrl -> mediaUrl`, and original loading is triggered only by the Viewer original action.
- Viewer original loading state is client-local and keyed by `mediaId`; it is not a server DTO field.

Example meaning:
- one shared media can belong to multiple posts
- REAL seed examples now use Chinese albums/posts such as `日常`, `旅行`, `春日散步`

## Endpoints

### `GET /api/media/feed`

Response data:
- array of `MediaDto`

### `GET /api/media/files/{mediaId}`

Response:
- binary file stream
- local dev currently serves stored files directly from server-managed local storage

### `DELETE /api/media/{mediaId}`

Behavior:
- system delete one media globally in the current space
- hides it from feed and all posts
- creates one trash item with `itemType = mediaSystemDeleted`

Response:
- returns one `TrashItemDto`

## Notes
- `url` is the canonical file URL
- Android treats `previewUrl` or `thumbnailUrl` as the first thumbnail choice, `coverUrl` as the preferred video poster fallback, and `url` or `mediaUrl` as the last image fallback.
- If the backend returns a relative path such as `/api/media/files/media_001`, Android joins it against the configured diagnostics `baseUrl`.
- If the backend returns an absolute URL such as `http://host:8080/api/media/files/media_001`, Android uses it directly and does not prepend `baseUrl` again.
- Empty or invalid thumbnail fields must be tolerated; Android will show a safe placeholder instead of crashing.
- Video media should provide `thumbnailUrl`, `previewUrl`, or `coverUrl` whenever possible. Without a poster URL, Android now falls back to a video placeholder instead of trying to decode the video file as a normal image.
- App-content Viewer video playback now resolves the playable source with `videoUrl -> mediaUrl -> originalUrl`.
- Missing playable video URLs must be tolerated; Android shows a Chinese safe placeholder instead of attempting playback.
- App-content Viewer video state such as loading, error, progress, and playing remains client-local and keyed by `mediaId`; it is not a server DTO field.
- Stage 12.4 Android cleanup centralizes URL normalization, preview/original request building, auth-header injection, and media-type fallback inside the shared media support layer rather than page-local helper code.
- Stage 12.4 Viewer cleanup centralizes image failure labels and video playback labels/retry reset into shared Viewer-state helpers; these are client-side UI rules, not DTO fields.
- Stage 12.5 Viewer original-image rule: Android treats `originalUrl` as a meaningful “load original” target only when it is non-empty and different from the current preview candidate. If `thumbnailUrl -> mediaUrl -> originalUrl` all collapse to the same URL, the Viewer hides the original-image action.
- Stage 12.5 related-post rule: Android may open post detail from `postIds` alone, so `postIds` must stay stable for active relationships even when the feed DTO does not embed post titles.
- Stage 12.5 video-first-open rule: if no poster field is available, Android shows a unified video placeholder instead of assuming the video file can act as a normal image thumbnail.
- `postIds` only includes active posts
- system-deleted media stays restorable through trash

## Stage 12.2 Refresh Notes
- `GET /api/media/feed` 仍然是照片流主刷新源；媒体上传、加入帖子、系统删除、回收站恢复后，Android 应重新拉取该列表或按统一 mutation event 触发刷新。
- `DELETE /api/media/{mediaId}` 成功后，Android 不应继续在照片流、帖子详情媒体区、Viewer 或 Gear Edit 媒体管理中展示该媒体。
- 如果媒体评论计数依赖服务端 DTO，相关页面应在媒体评论 mutation 后重新拉取受影响的 `mediaId` 所在列表或详情。

## Error Codes
- `MEDIA_NOT_FOUND`
- `MEDIA_ALREADY_DELETED`
- `TRASH_ITEM_NOT_FOUND`
- `AUTH_UNAUTHORIZED`

## Stage 12.7 Performance Notes

- Feed, album cards, and media-management grids are expected to use preview-sized media only; they must not depend on original-size assets for first paint.
- Android now reuses the same preview URL cache path across feed, post cards, post detail, and Viewer whenever possible. Stable preview URLs materially reduce open / back / reopen latency.
- Comment-only mutations no longer imply a required full media-feed refresh contract. Backends should keep comment operations lightweight and let clients refresh only the affected comment surfaces.

## Post-12.7 Import / Original Notes

- App media can exist without a post relationship. Import-only media should appear in the app photo feed with an empty `postIds` / relationship set.
- System media actions now have three app-content outcomes: `导入app` creates app media only, `发成新帖子` creates app media plus a new post relationship, and `加入已有帖子` creates app media plus an existing post relationship.
- Viewer original actions are media-level, not post-level. Images use `originalUrl` when it is distinct and usable; videos may use `originalUrl`, `videoUrl`, or `mediaUrl` as the original media resource and must fail safely without closing Viewer.
- Post-12.7 targeted fix: Android now treats `mediaUrl` as a usable image original fallback when a separate `originalUrl` is missing or equivalent, and treats `videoUrl -> mediaUrl -> originalUrl` as valid video original/probe candidates.
- Trash detail may request `/api/media/files/{mediaId}` for deleted current-space media ids referenced by trash `sourceMediaId` / `relatedMediaIds`; this is read-only preview behavior and does not mean deleted media returns to active feed.

## Stage 12.3 约定补充

- REAL `/api/media/feed` 只返回已经关联到至少一个有效帖子的媒体。
- 目录删只移除当前帖子和媒体的关系；系统删移除全局关系并进入 app 回收站。
- 删除会导致帖子变成空帖时，服务端返回冲突，客户端应展示中文空帖保护提示。
