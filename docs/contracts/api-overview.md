# API Overview Draft

## Status
- Stage 11.1 draft only
- no live backend required
- no final auth or pagination behavior locked yet

## Purpose
This document defines the first client-side API boundary for future backend integration.
The goal is to keep UI models separate from transport models while preserving the current fake-first app flow.

## Stage 12.5 Viewer Notes
- Viewer 原图入口由客户端根据 `thumbnailUrl / mediaUrl / originalUrl` 优先级和差异性决定，不等于“只要 DTO 带了 `originalUrl` 字段就一定显示按钮”。
- `postIds` 继续是 Viewer “所属帖子”跳转的最小契约；即使没有帖子标题，客户端也会用 `postId` 构造稳定跳转 route。
- 系统媒体 Viewer 不消费帖子专属字段；评论、所属帖子、原图按钮只属于 app 内容 Viewer。

## Base URL
- placeholder only: `https://api-placeholder.yingshi.local/`
- must be configurable
- must not hardcode a production server

## Auth
- bearer token placeholder
- request header draft:
  - `Authorization: Bearer <token>`
- auth contract details now live in `auth-api.md`
- token acquisition and refresh remain placeholder-only in Stage 11.2

## Envelope Draft

Successful response draft:

```json
{
  "requestId": "req_123",
  "data": {},
  "page": {
    "page": 1,
    "pageSize": 20,
    "nextCursor": null,
    "hasMore": false
  }
}
```

Error response draft:

```json
{
  "requestId": "req_123",
  "error": {
    "code": "NOT_IMPLEMENTED",
    "message": "Placeholder error",
    "details": null
  }
}
```

## Naming Rules
- JSON fields use `camelCase`
- IDs use string form such as `mediaId`, `postId`, `commentId`
- timestamps use UTC milliseconds or ISO-8601 string
- booleans use explicit names such as `isDeleted`, `isRead`, `hasMore`

## Pagination Draft
- page-number and cursor styles are both reserved
- list endpoints in Stage 11.1 expose placeholder params:
  - `page`
  - `pageSize`
  - `cursor`
- final backend can choose one style later, but the contract docs should mention the placeholder path now

## Error Code Placeholders
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `RATE_LIMITED`
- `SERVER_ERROR`
- `NOT_IMPLEMENTED`

## Stage 11.1 Draft-Only APIs
- upload token issue and upload completion
- delete / restore mutations
- comment create / update / delete
- notification APIs are not part of this contract pass yet

## Stage 12.4 Client Cleanup Note
- Android Stage 12.4 does not change the server API shape, but it assumes transport DTOs continue to be mapped into client UI models through dedicated mappers and shared media helper entrypoints instead of being used directly in Compose UI.
