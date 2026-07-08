# Yingshi Android Repo Instructions

## First read

Before doing any work, read these files in order:

1. `PRODUCT.md` — product vision, brand personality, non-negotiable rules
2. `docs/implementation/roadmap.md` — Stage 0–15 implementation history
3. `docs/refinement/项目深度调研报告-20260703.md` — comprehensive audit (2026-07-03)
4. `docs/refinement/精修1/需求文档.md` — current refinement requirements

## Project

Yingshi (映世) is a two-person private photo album and life-records app. Android client + Spring Boot backend (`YingShi-Server`) + PostgreSQL/MinIO storage.

## Current phase

**Stage 12+ — refinement and release preparation.**

The app is fully integrated with a real backend. All 11 Repository implementations walk `REAL` mode. 14 server Controllers with ~60 endpoints are implemented. Flyway V1–V25 migration chain is complete.

Do **not** use placeholder UI or fake state unless explicitly asked. Real API integration, Docker deployment, and true-device QA are the norm.

## Build & run

```bash
# Debug build
gradlew.bat assembleDebug

# Release build (requires keystore.properties + YINGSHI_RELEASE_API_BASE_URL)
YINGSHI_RELEASE_API_BASE_URL=https://your-domain/ gradlew.bat assembleRelease

# Server (in YingShi-Server/)
mvnw.cmd compile
docker compose up -d          # postgres + minio + server
```

- 4 build types: `debug`, `profile`, `optimizedDebug`, `release`
- Release enforces HTTPS URL via `YINGSHI_RELEASE_API_BASE_URL` (GradleException if missing)
- Release enables R8 minification + resource shrinking + APK signing
- `google-services.json` is conditionally applied (Firebase Crashlytics/FCM)
- `keystore.properties` is gitignored; copy `keystore.properties.example` as template

## Tech stack

- **AGP** 9.1.1 / **Kotlin** 2.2.10 / **Compose BOM** 2024.09.00 / **Material3**
- **minSdk** 24 / **targetSdk** 36 / **compileSdk** 36 / **Java** 11
- **Retrofit** 2.11 / **Room** 2.8.2 / **WorkManager** 2.11.1 / **Media3** 1.10
- **Coil** 2.7 / **Firebase BOM** 34.7 / **SilkDecoder** (jitpack, WeChat silk audio)
- **Not used**: Hilt, Navigation-Compose, DataStore

## Code structure

```
app/.../app            YingShiApp.kt (root composable ~1935 lines), YingShiApplication.kt
app/.../data/cache     OfflineAccessManager, AppReadCacheStore
app/.../data/model     DTOs and domain models
app/.../data/remote    13 Retrofit interfaces, AuthInterceptor, AuthRefreshCoordinator, AuthSessionManager
app/.../data/repository 11 Repository interfaces + Real/Fake/Provider
app/.../feature/auth   LoginScreen
app/.../feature/home   HomeScreen
app/.../feature/photos Photo feed, albums, viewer, system media, trash, notifications, settings, diagnostics
app/.../feature/life   Life console, push (FCM), widgets, chat import
app/.../feature/ledger Ledger (Room v4 + server sync)
app/.../feature/chat   Imported chat viewer (Room v3 + server sync, 10 split files)
app/.../feature/sync   SyncVersionTracker (foreground polling + FCM trigger)
app/.../feature/me     Profile, edit profile, personal profile
app/.../ui/components  AppShell, YingShiSurfaces, YingShiMotion, AuroraBackdrop
app/.../ui/theme       Color/Shape/Theme/Tokens/Type design tokens
```

## Architecture patterns

- **Repository pattern**: 11 interfaces with `Real*Repository` (API) + `Fake*Repository` (demo) implementations. `RepositoryProvider.currentMode` hardcoded to `REAL`.
- **ViewModel**: `MutableStateFlow` + `asStateFlow()` + `viewModelScope.launch`. Composables render state, emit events.
- **Auth**: `AuthSessionManager` (object singleton) manages JWT sessions. `AuthInterceptor` + `AuthRefreshCoordinator` (OkHttp authenticator) handle token refresh.
- **Offline**: `OfflineAccessManager.state` gates write operations. `shouldFallbackToReadCache()` unifies offline logic. `AppReadCacheStore` caches by `baseUrl + userId`.
- **Sync**: `SyncVersionTracker` polls `/api/sync/versions` + reacts to FCM. Per-module staleness tracking (PHOTO_FEED, ALBUMS, TRASH, NOTIFICATIONS, LIFE_CONSOLE).
- **Push**: `YingShiFirebaseMessagingService` → `PushNotificationPresenter` (actor self-filter, `PushNotificationDeduper`). Channel `PushNotificationChannels` + fallback `NotificationFallbackNotifier`.
- **Room**: `yingshi-ledger.db` v4 (10 entities, 3 migrations), `yingshi-chat-imports.db` v3 (5 entities, 2 migrations).
- **Error handling**: `ApiResult.Error` with code/message/throwable. Consistent offline-only semantics.

## Non-negotiable product rules

- Photo page is a global media stream, not a post feed.
- System media is a separate tool area, not part of the main content stream.
- Post comments and media comments must stay separated.
- Viewer uses an immersive edge-overlay structure.
- Brand: quiet, intimate, trustworthy. "Milk-fog sky-blue air shell + warm-white cards + light-green/champagne-gold accents."
- No global deep-blue dark theme, no social-feed patterns, no saturated neon blue-purple.

## Collaboration rules

- Keep edits focused and feature-oriented.
- Feature packages over monolithic UI files (YingShiApp.kt is a known tech-debt item).
- Update docs when module status changes (open/closed in `docs/refinement/*.md`).
- Always verify with `gradlew.bat assembleDebug` before finishing.
- Cross-module changes require checking coupling (photo_stream ↔ picker, ledger sync ↔ chat sync, push after-commit ↔ comment/trash/upload/life).
- Docker container image must match source code (Flyway version drift has caused issues before).
- Windows environment: use `cmd.exe` wrappers, PowerShell, or Python scripts (WSL has no Java).

## Known tech debt

| Item | Status | Impact |
|------|--------|--------|
| `YingShiApp.kt` ~1935 lines | Known, partially split | Maintainability |
| Global `object` singletons (5+) | Accepted | Testability |
| Fake repository体系 (~1400 lines each) | Retained as fallback | Dead code if REAL-only |
| Deprecated Window APIs | @Suppress in PhotoViewerScreen | Future AGP upgrade risk |
| Test coverage ~0% | Android only (server has 28+ tests) | Regression risk |
| `allowBackup="false"` | Done (Round 1) | Security |

## Completion checklist

Before finishing any task:

1. `gradlew.bat assembleDebug` passes (zero errors)
2. Fix compile errors and deprecation warnings caused by your changes
3. Summarize changed files with line-level description
4. State what is done and what is not done
5. State known risks / TODOs
6. Update relevant `docs/refinement/*.md` brief if module status changes
