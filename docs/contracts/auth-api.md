# Auth API Contract

Updated: 2026-06-09

## Status

- Base path: `/api/auth`
- Public routes: `POST /login/challenge`, `POST /login/challenge/resend`, `POST /login/verify`, `POST /login/remembered`, `POST /refresh-token`
- Bearer-auth routes: `GET /me`, `PATCH /me/profile`, `POST /logout`, `POST /me/avatar`, `GET /avatar/{userId}`
- Android login is now a two-step flow: `账号密码 -> 邮箱验证码 -> 建立会话`

## Seed Accounts

- `1085060329@qq.com / 123456`
- `2926315047@qq.com / 123456`

## Login Code Rules

- code length: `6`
- code TTL: `5 minutes`
- resend cooldown: `60 seconds`
- per-account rate limit: `30 minutes up to 5 sends`
- per-challenge max wrong attempts: `5`

## 1. `POST /api/auth/login/challenge`

Request:

```json
{
  "account": "1085060329@qq.com",
  "password": "123456"
}
```

Response:

```json
{
  "challengeId": "login_challenge_xxx",
  "maskedEmail": "108***29@qq.com",
  "expireAtMillis": 1780000000000,
  "resendAvailableAtMillis": 1780000060000
}
```

Notes:

- this step only validates account/password and sends the QQ email code
- no access token or refresh token is returned here

## 2. `POST /api/auth/login/challenge/resend`

Request:

```json
{
  "challengeId": "login_challenge_xxx"
}
```

Response shape matches `/api/auth/login/challenge`.

## 3. `POST /api/auth/login/verify`

Request:

```json
{
  "challengeId": "login_challenge_xxx",
  "code": "123456",
  "deviceId": "android-install-id"
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
- `rememberedLoginToken`
- `rememberedLoginExpireAtMillis`
- `accessToken`
- `refreshToken`
- `accessTokenExpireAtMillis`
- `refreshTokenExpireAtMillis`

Notes:

- session creation happens only after code verification succeeds
- Android persists the returned tokens, current-user snapshot, and the same-device remembered-login token at this step

## 4. `POST /api/auth/login/remembered`

Request:

```json
{
  "account": "1085060329@qq.com",
  "password": "123456",
  "deviceId": "android-install-id",
  "rememberedLoginToken": "opaque-remembered-login-token"
}
```

Response shape matches `/api/auth/login/verify`.

## 5. `POST /api/auth/refresh-token`

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

## 6. `GET /api/auth/me`

- core Android session-restore endpoint
- response fields match the current-user portion of the verify-login response

## 7. `PATCH /api/auth/me/profile`

Request:

```json
{
  "displayName": "映世小屋",
  "bio": "Updated profile bio"
}
```

Validation:

- `displayName` required, max `80`
- `bio` optional, max `280`

## 8. `POST /api/auth/logout`

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

## 9. `POST /api/auth/me/avatar`

Request:

- `multipart/form-data`
- field name: `file`

Response:

- updated current-user payload

## 10. `GET /api/auth/avatar/{userId}`

Response:

- `200 image/jpeg` when present
- `404` when the user has no avatar

## Android Mapping

- login page step 1 -> `POST /api/auth/login/challenge`
- login page resend -> `POST /api/auth/login/challenge/resend`
- login page step 2 -> `POST /api/auth/login/verify`
- same-device re-login -> `POST /api/auth/login/remembered`
- session restore -> `GET /api/auth/me`
- profile pages -> `GET /api/auth/me`
- edit profile -> `PATCH /api/auth/me/profile`
- logout -> `POST /api/auth/logout`
- refresh-token path -> `RealAuthRepository.refreshToken()`
- avatar upload -> `POST /api/auth/me/avatar`
- avatar display -> `GET /api/auth/avatar/{userId}`

## Session Behavior

- token refresh and retry are still centralized in `AuthRefreshCoordinator`
- if token is missing or expired, Android no longer silently replays stored account/password
- after a successful verified login, Android can keep a same-device remembered-login token for short-term re-login after manual logout
- when cached user data exists, the app may stay in cached read-only mode and prompt for re-verification

## Error Codes

- `AUTH_INVALID_CREDENTIALS`
- `AUTH_LOGIN_CHALLENGE_INVALID`
- `AUTH_LOGIN_CODE_EXPIRED`
- `AUTH_LOGIN_CODE_INVALID`
- `AUTH_LOGIN_CODE_RATE_LIMITED`
- `AUTH_LOGIN_CODE_RESEND_TOO_FAST`
- `AUTH_LOGIN_CODE_SEND_FAILED`
- `AUTH_REMEMBERED_LOGIN_EXPIRED`
- `AUTH_REMEMBERED_LOGIN_INVALID`
- `AUTH_TOKEN_EXPIRED`
- `AUTH_UNAUTHORIZED`
- `AUTH_SESSION_INVALID`
- `FORBIDDEN`
- `NOT_FOUND`
- `VALIDATION_ERROR`
- `SERVER_ERROR`
