# Auth API Contract

Updated: 2026-05-25

## Status

- This document is aligned with the current `YingShi-Server` code.
- Base path: `/api/auth`
- `POST /login` and `POST /refresh-token` are public.
- `GET /me`, `PATCH /me/profile`, `POST /logout`, `POST /me/avatar`, and `GET /avatar/{userId}` require bearer auth.
- Android `REAL` mode already consumes login, refresh-token, current-user, logout, profile update, avatar upload, and avatar read/display.

## Token Rules

- login returns access token plus refresh token
- refresh rotates both tokens
- auth sessions are persisted on the server
- reusing an old refresh token returns `AUTH_SESSION_INVALID`
- logout revokes the current session on the server

## 1. `POST /api/auth/login`

Request:

```json
{
  "account": "demo.a@yingshi.local",
  "password": "demo123456"
}
```

Response `data` contains:

- `userId`
- `account`
- `displayName`
- `avatarUrl`
- `bio`
- `libraryId`
- `libraryDisplayName`
- `partner`
- `createdAtMillis`
- `updatedAtMillis`
- `accessToken`
- `refreshToken`
- `accessTokenExpireAtMillis`
- `refreshTokenExpireAtMillis`

## 2. `POST /api/auth/refresh-token`

Request:

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

Response:

- new access token
- new refresh token
- new expiry timestamps

Android note:

- `AuthRefreshCoordinator` now centralizes refresh-token exchange and request retry for protected backend calls

## 3. `GET /api/auth/me`

This is the core Android session-restore endpoint.

Response fields match the current-user portion of the login response.

## 4. `PATCH /api/auth/me/profile`

Request:

```json
{
  "displayName": "Demo A",
  "bio": "Updated profile bio"
}
```

Validation:

- `displayName` required, max `80`
- `bio` optional, max `280`

Android note:

- the edit-profile page already uses this endpoint

## 5. `POST /api/auth/logout`

Body may be empty or may include:

```json
{
  "refreshToken": "jwt-refresh-token"
}
```

Response:

```json
{
  "success": true
}
```

Current behavior:

- server-side session revocation is enabled
- the current access token becomes unusable after logout

## 6. `POST /api/auth/me/avatar`

Request:

- `multipart/form-data`
- field name: `file`

Response:

- updated current-user payload

Current Android state:

- edit-profile already supports selecting and uploading the current user's avatar
- `My` and profile pages already display backend avatars

## 7. `GET /api/auth/avatar/{userId}`

Response:

- `200 image/jpeg` when present
- `404` when the user has no avatar

## Current Android Mapping

- login page -> `POST /api/auth/login`
- session restore -> `GET /api/auth/me`
- profile pages -> `GET /api/auth/me`
- edit profile -> `PATCH /api/auth/me/profile`
- logout -> `POST /api/auth/logout`
- refresh-token path -> `RealAuthRepository.refreshToken()`
- automatic retry path -> `AuthRefreshCoordinator` + OkHttp `Authenticator`
- avatar upload -> `POST /api/auth/me/avatar`
- avatar display -> `GET /api/auth/avatar/{userId}`

## Seed Accounts

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

## Error Codes

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `SERVER_ERROR`
