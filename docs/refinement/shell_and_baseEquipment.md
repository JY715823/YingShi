# App Shell And Base Equipment

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `shell_and_baseEquipment`
- Status: `closed`
- Last updated: `2026-06-08`
- Primary surfaces: `android | shared`
- Linked server brief: `none`

## Module Goal
- User value: polish the release shell so navigation, connection behavior, cache fallback, and base visuals feel reliable enough for deployment.
- Business or product intent: remove obsolete debug-only runtime choices, reduce connection fragility, and make core entry surfaces usable when the backend is temporarily unavailable.
- Success criteria:
  - bottom navigation no longer crowds text and shell visuals feel more polished
  - app runtime no longer exposes FAKE / REAL mode switching in normal paths
  - core read entry points can render durable cached content when the server is unreachable
  - offline cached access is read-only and clearly bounded

## Current State
- What exists today:
  - shell visuals and bottom nav are already customized but still slightly crowd labels
  - repository mode infra still exists in runtime config and many code paths
  - media binary caches exist, but core business-data caches for feed, albums, notifications, trash, and profile snapshot are weak or memory-only
  - login and app bootstrap still gate too hard on live auth/session state and can block cached read access
- Known constraints:
  - release is near; this round should be decisive, not exploratory
  - repo worktree already contains many unrelated and overlapping user edits; do not revert them
  - legacy fake files may remain in repo, but runtime path should be REAL-only
- Relevant code or docs:
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/app/YingShiApp.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/ui/components/AppShell.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/data/remote/config/BackendDebugConfig.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/data/repository/RepositoryProvider.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/RealPhotoFeedViewModel.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/RealPhotoViewModels.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/NotificationCenterScreen.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/NotificationDetailScreen.kt`
  - `/mnt/e/Study/App/YingShi/app/src/main/java/com/example/yingshi/feature/photos/RealTrashViewModels.kt`

## Your Current Ideas
- Idea 1: move the bottom nav slightly downward so labels stop feeling blocked.
- Idea 2: remove FAKE / REAL repository mode from frontend and backend runtime flow unless there is a strong reason to keep it.
- Idea 3: improve motion, shadow, and polish details.
- Idea 4: strengthen cache strategy and prefer cached loading over immediate connection failure.

## Codex Recommendations
### Recommended to finish in this module
- Enforce REAL-only runtime while keeping legacy fake source files as compile-time leftovers if needed.
  - Why it is worth considering: it removes release ambiguity without forcing a risky repository-wide fake-code purge right before deployment.
  - Impact on usability, robustness, or smoothness: fewer hidden mode mismatches and less settings clutter.
- Add a shared persistent read-cache layer for the five agreed entry surfaces: Me, photo feed, album directory, notifications, trash.
  - Why it is worth considering: current fallback quality is weakest exactly where users reopen the app after connection loss.
  - Impact on usability, robustness, or smoothness: the app opens into something useful instead of an auth or network wall.
- Allow cached read-only entry when the backend is unreachable or refresh fails for network reasons, while still clearing protected state on explicit logout or confirmed 401 invalidation.
  - Why it is worth considering: this matches the agreed release behavior and avoids fake “logged out” moments during transient outages.
  - Impact on usability, robustness, or smoothness: much calmer resilience story for release.
- Keep connection editing available in Settings and a lightweight connection entry at login, but remove the current heavy debug feel.
  - Why it is worth considering: release still needs support for base URL correction without surfacing a debug lab.
  - Impact on usability, robustness, or smoothness: lower friction and less intimidating connection troubleshooting.
- Do a medium polish pass on shell surfaces, elevation, and motion instead of isolated one-off tweaks.
  - Why it is worth considering: shell quality is multiplicative because every module sits inside it.
  - Impact on usability, robustness, or smoothness: better visual coherence and perceived finish.

### Defer only with explicit acceptance
- Item: add stale-age hints and last-sync timestamps to cached entry surfaces.
  - Why it would otherwise belong in this module: they help users judge whether cached read-only content is safe to trust.
  - Why it might still be deferred: release can still proceed if cache entry and offline rules are otherwise very clear.
- Item: add a small offline operations queue for retryable writes after release if product later wants it.
  - Why it would otherwise belong in this module: it completes the offline story more fully.
  - Why it might still be deferred: today’s scope intentionally keeps offline writes disabled to reduce risk.

## Key Questions
- [x] Should runtime become REAL-only even if legacy fake files remain in repo?
- [x] Which surfaces must support offline cached entry in this round?
- [x] Should offline mode be read-only or support queued writes?

## Scope Boundaries
### In scope
- app shell spacing and shared visual polish
- REAL-only runtime path cleanup in active app flow
- persistent read-cache storage and offline read-only fallback for agreed entry surfaces
- login/bootstrap/settings adjustments needed to support the new connection and offline behavior

### Out of scope
- deleting every fake implementation file from the repository
- adding full offline write queueing
- broad server contract redesign unless implementation proves a small coupling change is required

### Non-negotiables
- keep runtime path REAL-only
- preserve settings-based base URL editing for release
- allow cached read-only access when previously authenticated but temporarily offline
- clear protected cached access on explicit logout or confirmed auth invalidation

### Failure and fallback expectations
- Failure states to support:
  - server unreachable on launch
  - token refresh fails due to network
  - empty cache with no connection
  - write action attempted during offline read-only mode
- Rollback or fallback behavior:
  - load durable cache when available
  - show read-only / offline restrictions instead of silently attempting writes
  - route to login only when there is no usable protected cache or auth is explicitly invalid

## Related Modules
- Module: `auth`
  - Relationship: bootstrap, refresh, logout, and cached user snapshot rules determine offline entry.
  - Recheck before ship: token/network failure distinction and cache clearing paths.
- Module: `settings`
  - Relationship: base URL editing and cache management surface live here.
  - Recheck before ship: release-safe copy and discoverability.
- Module: `notifications`
  - Relationship: read state and detail hydration now depend on persistent cache and offline restrictions.
  - Recheck before ship: write actions disabled offline.
- Module: `viewer / feed / albums / trash`
  - Relationship: main read surfaces consume new cache layer.
  - Recheck before ship: empty/error/offline state consistency.

## Frontend and Backend Contracts
### Client state and entry points
- Screens, routes, ViewModels, repositories:
  - `YingShiApp`, `LoginScreen`, `SettingsScreen`, `BackendDiagnosticsScreen`
  - `RealPhotoFeedViewModel`, album ViewModels, notification screens, trash ViewModels
  - shared auth/session config and any new read-cache store

### Server endpoints and payloads
- Controllers, services, DTOs, contracts:
  - existing real endpoints for current user, media feed, albums, notifications, trash
  - no planned server contract expansion; cache should store existing payload shapes

### Shared rules
- Auth, permissions, identity, ordering, time, copy:
  - cached protected content is keyed by connection + identity
  - explicit logout and confirmed 401 invalidation clear protected cached access
  - offline path is read-only
  - ordering should follow latest successful server payload

## UI and Visual Details
- Layout or information hierarchy: bottom nav should breathe more; shell chrome should feel lighter and more intentional.
- Components and states: shell, tabs, floating actions, settings connection entry, cache/offline messaging.
- Motion or transitions: smoother shared transitions and less abrupt surface/elevation treatment.
- Copy notes: connection and offline copy should sound release-ready, not like debug tooling.

## Interaction Feedback
- Loading: prefer skeleton/progress while also allowing stale cache to appear when available.
- Empty: distinguish empty data from empty cache.
- Error: avoid dead-end server error walls when cache can be shown.
- Success: live refresh should replace stale cache cleanly.
- Permission denial: preserve existing auth/permission messaging where relevant.
- Offline or retry: allow manual retry and clearly mark read-only restrictions.

## Hidden Impact Checklist
- Notifications: persistent list/detail cache and offline action disablement required.
- Auth: bootstrap/login/logout/refresh rules are directly affected.
- Upload: new offline read-only state should block upload-style actions from shell entry points.
- Comments: not targeted this round; ensure no accidental dependency drift.
- Viewer: verify feed/album read cache does not break viewer entry.
- Settings: connection and cache management surfaces need updated copy/behavior.
- Analytics or logging: no new analytics planned; preserve existing diagnostics where practical.
- Cache or offline: main focus of the round.
- Permissions: no new OS permissions planned.
- Copy and empty states: release wording should replace debug flavor.

## Plan Self-check
- Recommendation quality: previously completed in planning discussion; this brief carries forward the approved recommendations for implementation.
- Scope pressure test: limited to shell/base infra plus the five read surfaces and supporting auth/settings coupling.
- Contract and dependency pressure test: no server expansion planned; primary risk is client state consistency across auth and cached content.
- UX state pressure test: this round must keep loading, empty, error, success, and offline states distinct on shared entry flows.
- Risks to watch in implement: overlapping user edits, cache invalidation mistakes, and accidental fake-mode compile regressions.

## Implementation Notes
### Client
- Added `AppReadCacheStore` and `OfflineAccessManager` as shared shell-level infra for protected read-only fallback.
- Persistent cache is stored as JSON files under app private storage `files/read-cache`, keyed by base URL plus user identity, and currently covers:
  - current user snapshot
  - photo feed
  - album directory
  - notification list/detail
  - trash list/detail
- `YingShiApp` bootstrap now prefers cached current-user entry when live auth/profile refresh fails for network or server availability reasons, and only forces login when there is no usable protected cache or auth is explicitly invalid.
- Explicit logout and confirmed `401` invalidation clear protected cached access; transient network/server failure enters read-only cached mode instead.
- Active runtime path is now REAL-only in config/provider flow; repository mode is no longer surfaced through build config or settings for normal app usage.
- Feed, album directory, notifications, and trash flows now persist successful reads and fall back to cached read-only content inside the agreed five global entry surfaces.
- Offline read-only mode blocks write-style actions in these shell-adjacent surfaces instead of trying and failing blindly.
- Added app-level network recovery monitoring so the shell can detect connectivity restoration without requiring page switches.
- On connectivity restoration, shell auth/profile recovery now attempts to re-establish the live session and refresh current-user data when the app is sitting in cached read-only or failed-auth-refresh state.
- Added shared reconnect-triggered silent refresh hooks to the five cached read surfaces already in scope:
  - photo feed
  - album directory
  - notification list
  - notification detail
  - trash list/detail
- Adjusted album-page loading behavior so reconnect refresh keeps already visible content on screen and uses lighter inline feedback instead of dropping back to a full-page loading card.
- Follow-up hardening for the current device-QA round:
  - `NetworkConnectivityMonitor` now rechecks the current default network on callback edges and bumps `changeVersion` on real network transitions even when the final connectivity boolean does not change.
  - login now keeps a queued request alive across connectivity flaps and auto-retries if a request fails after the network changed during the same spinner window.
  - viewer original-image loading now keeps pending requests alive across disconnects and immediately retries once connectivity has changed back instead of surfacing a stale failure result.
  - notification list and notification detail now use cancellable refresh jobs, stale-result protection, disconnect-time cached fallback, and reconnect auto-refresh without requiring page switches.
  - severe page-entry ANR follow-up moved remaining notification and trash cache reads off the main thread, and also moved photo-feed plus album-directory cache writes and album offline cache lookups to `Dispatchers.IO` so tab switches no longer compete with JSON disk IO on the UI thread.
  - closeout hardening moved remaining notification and trash cache writes to `Dispatchers.IO`, so both page-entry hydration and post-refresh persistence avoid main-thread disk pressure.

### Server
- No server code change was required for this round.
- Rechecked `YingShi-Server` for FAKE / REAL runtime toggles relevant to this shell module and did not find a server-side mode branch that needed matching cleanup.

### Design
- Bottom navigation spacing and shell chrome were adjusted so labels breathe more comfortably near the bottom edge.
- Shared motion and surface treatment were polished with slightly stronger elevation/shadow and a gentler reveal feel.
- Login, settings, diagnostics, and cache surfaces were rewritten toward release connection tooling instead of debug-looking controls.

### Interaction Feedback
- If live data refresh fails but protected cache exists, the app now shows cached content with explicit read-only messaging instead of dropping straight to a connection failure wall.
- If no cache exists, existing error/login paths still surface clearly so empty cache and offline cache are not conflated.
- Shell-level offline state is shared, so feed, albums, notifications, trash, and Me/profile copy stay aligned about cached read-only behavior.

## Post-implement Self-check
- Validation run:
  - `cmd.exe /c gradlew.bat :app:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain` passed on `2026-06-08` after adding connectivity restoration monitoring and reconnect-driven silent refresh.
  - `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain"` passed again on `2026-06-08` after the follow-up login, viewer-original, notification, and connectivity-monitor self-healing fixes.
  - `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain"` passed again on `2026-06-08` after the ANR stopgap that removed remaining main-thread cache IO from page-entry photo/notification/trash flows.
  - `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin --no-daemon -Pkotlin.incremental=false --console=plain"` passed again on `2026-06-08` after moving the remaining notification and trash cache writes off the main thread during closeout hardening.
  - No new manifest permission was required because `ACCESS_NETWORK_STATE` was already declared.
- New behavior sanity:
  - reconnect recovery is now shared instead of page-switch-driven
  - cached read-only surfaces only auto-refresh when connectivity restoration is relevant to their current state
  - existing content is preserved during reconnect refresh where possible instead of forcing useless full-screen loading
- Contract sanity:
  - no server payload contract changed
  - reconnect recovery reuses existing auth, feed, album, notification, and trash repository requests rather than introducing parallel DTO or repository shapes
  - current-user snapshot persistence still maps partner data into the existing `RemotePartnerProfile` shape consistently
- Test plan quality:
  - local compile validation is complete
  - real-device retest now focuses tightly on the reconnect behavior plus the install-build page-entry ANR path that changed in this round
- Known gaps:
  - reconnect recovery still needs fresh real-device confirmation on the user’s actual network conditions
  - legacy fake source files and some internal `RepositoryMode` conditionals still exist in the codebase, but runtime no longer exposes a user-switchable mode
  - this round does not add offline write queueing or stale-age timestamps
  - life-module cache coverage was explicitly left out of this follow-up round per user direction

## New Coupling Recheck
- Module: `auth`
  - What was rechecked: bootstrap entry, auto-login fallback, cached current-user persistence, logout clearing, and unauthorized clearing paths.
  - Result: network/server failure can stay in protected cached read-only mode; explicit logout and confirmed `401` still clear protected cache access.
- Module: `settings / diagnostics / cache management`
  - What was rechecked: base URL editing entry points, release-safe connection copy, and cache visibility/clear affordances.
  - Result: settings remains the formal connection editor, login keeps a lighter entry, and cache management can summarize/clear the new read-cache store.
- Module: `feed / albums / notifications / trash`
  - What was rechecked: successful-read persistence plus offline write disablement across the agreed entry surfaces.
  - Result: these surfaces now share the same read-cache fallback model and read-only restrictions without changing their server contracts.
- Module: `platform connectivity`
  - What was rechecked: existing network-state permission and application-lifetime initialization path for connectivity monitoring.
  - Result: `ACCESS_NETWORK_STATE` was already present, so reconnect monitoring could be added without a new permission or server dependency.

## Implement Test Plan
### Locally validated
- Check: app-level connectivity monitor, shell reconnect recovery, and reconnect-triggered refresh hooks compile with the Android app module.
- Result: `:app:compileDebugKotlin` passed on `2026-06-08`.

### Linked-module regression checks
- Module: `auth`
  - What to recheck: cached read-only entry, reconnect auto-login recovery, explicit logout, and confirmed unauthorized clearing.
  - Why it can regress: this round adds a new app-level reconnect trigger on top of the existing auth bootstrap flow.
- Module: `feed / albums / notifications / trash`
  - What to recheck: reconnect while already showing cached content, reconnect while showing an error state, and reconnect while a write action is disabled offline.
  - Why it can regress: these surfaces now auto-refresh on a shared connectivity signal instead of relying on manual navigation or retry only.

### Real-device checks for the user
- Scenario: fresh-install tab entry no longer ANRs
  - Steps:
    1. Install the latest build fresh or clear app data.
    2. Open the app and tap `照片`、`生活`、`我的` in normal succession.
    3. Repeat once more after the initial data/cache load settles.
  - Expected result: each tab should open without freezing the system UI, showing `映世无响应`, or getting the process killed.
- Scenario: login spinner reconnects in place
  - Steps:
    1. Disconnect network on the device.
    2. Tap login and keep the login screen open while the spinner is running.
    3. Restore network before the first spinner finishes.
  - Expected result: the same spinner should transition into auto-retry and complete login without a second tap.
- Scenario: viewer original-image spinner reconnects in place
  - Steps:
    1. Disconnect network and open a photo in the viewer.
    2. Tap `加载原图` and keep the viewer on the same screen while the spinner is running.
    3. Restore network before the first original-image attempt finishes.
  - Expected result: the original-image spinner should continue onto the restored connection and finish without a second manual trigger.
- Scenario: cached read-only feed recovers in place
  - Steps:
    1. Disconnect network and open a feed/album/notification/trash screen that has cached content.
    2. Confirm the page enters cached read-only mode.
    3. Restore network without leaving the page.
  - Expected result: the page keeps current content visible, quickly retries in the background, then exits read-only state as soon as live data returns.
- Scenario: no-cache error state recovers in place
  - Steps:
    1. Disconnect network and open a screen that does not yet have usable cached data.
    2. Let it enter the error/loading failure state.
    3. Restore network without leaving the page.
  - Expected result: the page retries automatically and becomes usable without requiring a tab switch or back-and-forth navigation.
- Scenario: profile shell recovery
  - Steps:
    1. Enter the app with cached user data while offline or with a broken connection.
    2. Stay on shell or profile-related surfaces.
    3. Restore network.
  - Expected result: shell auth/profile state recovers automatically, cached-only messaging clears, and disabled shell actions return when live session refresh succeeds.
- Scenario: notification surfaces clear stale reminders in place
  - Steps:
    1. Open notification list or notification detail with cached content available.
    2. Disconnect network, let the page enter cached read-only or failed-loading state, then restore network without leaving the page.
    3. Observe whether old reminders disappear after live refresh returns.
  - Expected result: notification list/detail should clear stale reminder text and return to normal live state without page switches.

### Still unverified
- Risk:
  - Why it remains open: local compile cannot prove device-specific timing around airplane mode, Wi-Fi toggles, or captive-network transitions.
  - Best next verification path: real-device QA on the user’s normal phone with repeated offline -> online transitions across the five scoped cached entry surfaces.

## Real-device Issue Log
### Issue 2026-06-08-01
- Date: `2026-06-08`
- Build version: `not provided`
- Device / OS: `not provided`
- Module key: `shell_and_baseEquipment`
- Test environment: `real device`, network disconnected first, then network restored while the app remained on the same screen with disabled or loading UI states.
- Repro steps:
  1. Enter a cached read-only or failed-loading state in a shell-adjacent surface while the device has no network.
  2. Keep the app on the same screen and restore network connectivity.
  3. Observe whether disabled controls, gray states, or loading states recover automatically without manual navigation or returning to the page.
- Expected result: once connectivity is restored, the app should quickly detect it, clear stale loading or disabled state, immediately retry the relevant request, and update the UI as soon as a live response arrives.
- Actual result: some surfaces remain gray, disabled, or stuck in loading/failure state after network restoration, and may require leaving the page and coming back before they recover.
- Evidence: `user report only`; no screenshot, screen recording, or logcat attached yet.
- Severity: `major`
- Server-related: `no`
- Reproducibility: `intermittent`
- Suspected area: missing shared connectivity-restored observer and missing per-surface auto-refresh orchestration after `OfflineAccessManager` enters read-only mode.
- Next action: resolved in the closing device-QA pass; user confirmed same-screen reconnect recovery is now acceptable.

### Issue 2026-06-08-02
- Date: `2026-06-08`
- Build version: `not provided`
- Device / OS: `not provided`
- Module key: `shell_and_baseEquipment`
- Test environment: `real device`, login triggered while the device had no network, then network was restored before the first spinner finished.
- Repro steps:
  1. Disconnect network on the device.
  2. Tap login and let the screen enter the loading spinner state.
  3. Restore network before the first login attempt finishes.
- Expected result: the same spinner should detect restored connectivity, immediately retry, and complete login without a second tap.
- Actual result: the first spinner eventually ends in a no-network failure, and a second manual tap is required before login succeeds.
- Evidence: `user report only`
- Severity: `major`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area: in-flight login request was not being treated as stale after a connectivity flap, so the old failure could still win.
- Next action: resolved in the closing device-QA pass; user confirmed login-side reconnect self-healing is now acceptable.

### Issue 2026-06-08-03
- Date: `2026-06-08`
- Build version: `not provided`
- Device / OS: `not provided`
- Module key: `shell_and_baseEquipment`
- Test environment: `real device`, photo viewer opened offline, original-image loading triggered, then network restored before the first spinner finished.
- Repro steps:
  1. Disconnect network and open the photo viewer.
  2. Tap `加载原图` and let the viewer enter the original-image loading spinner state.
  3. Restore network before the first original-image request finishes.
- Expected result: the same loading state should continue onto the restored connection and finish successfully without manual retry.
- Actual result: the first loading attempt still ends in failure, and the user has to trigger original loading again manually.
- Evidence: `user report only`
- Severity: `major`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area: original-image requests were still able to publish a stale failure after the network changed during the same request window.
- Next action: resolved in the closing device-QA pass as part of the broader reconnect/self-healing confirmation.

### Issue 2026-06-08-04
- Date: `2026-06-08`
- Build version: `not provided`
- Device / OS: `not provided`
- Module key: `shell_and_baseEquipment`
- Test environment: `real device`, photo feed and album pages with network disconnected first, then network restored while staying on the same page.
- Repro steps:
  1. Enter photo feed or album while offline or after a failed live request.
  2. Restore network without leaving the page.
  3. Observe whether the old inline failure reminder clears once live refresh succeeds.
- Expected result: once the reconnect refresh succeeds, any old failure banner should clear automatically and the page should return to a normal live state.
- Actual result: feed and album could keep showing an old inline failure reminder even after other functions had already recovered.
- Evidence: `user report only`
- Severity: `major`
- Server-related: `no`
- Reproducibility: `intermittent`
- Suspected area: reconnect refresh needed stronger stale-result protection and faster disconnect-time cancellation for in-flight requests.
- Next action: resolved in the closing device-QA pass; user confirmed reconnect cleanup is now acceptable on the tested device.

### Issue 2026-06-08-05
- Date: `2026-06-08`
- Build version: `not provided`
- Device / OS: `not provided`
- Module key: `shell_and_baseEquipment`
- Test environment: `real device`, Me/profile surfaces entered through cached read-only mode, then network restored while the app stayed on the same shell flow.
- Repro steps:
  1. Enter Me/profile while the app is in cached read-only mode.
  2. Restore network without restarting the screen.
  3. Observe whether the cached read-only badge and profile refresh state return to normal automatically.
- Expected result: Me/profile should clear the cached read-only state automatically after live session/profile refresh succeeds.
- Actual result: the page could remain in cached read-only state even after reconnect, including after a simple page switch.
- Evidence: `user report only`
- Severity: `major`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area: shell reconnect recovery was not consistently completing the live current-user refresh path after reconnect.
- Next action: resolved in the closing device-QA pass; user confirmed Me/profile recovery now behaves acceptably.

### Issue 2026-06-08-06
- Date: `2026-06-08`
- Build version: `not provided`
- Device / OS: `not provided`
- Module key: `shell_and_baseEquipment`
- Test environment: `real device`, repeated offline -> online toggles while keeping the app on the same screen.
- Repro steps:
  1. Disconnect network and stay on login, viewer, feed, album, or Me/profile surfaces.
  2. Observe how long it takes before the UI reflects the loss of connectivity.
  3. Restore network and observe how quickly the UI flips back and starts retrying real requests.
- Expected result: both disconnect and reconnect should be detected quickly enough that the UI stops useless loading early and restarts useful requests immediately.
- Actual result: both offline detection and online recovery feel too slow, and some UI states do not update promptly.
- Evidence: `user report only`
- Severity: `major`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area: connectivity callback state changes were not propagating strongly enough across shell and page-level auto-recovery logic.
- Next action: resolved in the closing device-QA pass; user confirmed the scoped detection and recovery latency is now acceptable.

## Validation Snapshot
### Verified
- Planning decisions are explicit in the brief.
- Code implementation for the scoped shell/base infra round is in place.
- Local Kotlin compilation passed for the Android app module.
- Real-device testing now confirms the previously reported install-build page-entry ANR no longer reproduces when opening `照片`、`生活`、`我的`.
- Real-device testing now confirms the scoped offline -> online self-healing behavior is acceptable on the user’s device.
- Local follow-up implementation for Issue `2026-06-08-01` is in place and compiles.
- Local follow-up implementation for Issues `2026-06-08-02` through `2026-06-08-06` is in place and compiles.
- Closeout hardening removed the remaining scoped notification/trash main-thread cache writes and compiles.

### Pending
- None at close.

### Blocked
- None.

## Next Fix Queue
- None at close.

## Closeout Summary
- What shipped:
  - REAL-only runtime path for release usage, lighter shell visuals, adjusted bottom navigation spacing, and release-safe connection tooling.
  - Persistent protected read-cache support for Me/current user, photo feed, album directory, notifications, and trash with shared read-only offline behavior.
  - Shared reconnect detection and auto-recovery across shell auth/profile, login, viewer-original loading, feed, albums, notifications, and trash.
  - Final hardening against main-thread cache IO on the page-entry and post-refresh paths most likely to cause ANR.
- What was validated:
  - repeated local `:app:compileDebugKotlin` passes across the implementation, verify, ANR stopgap, and closeout-hardening rounds
  - user real-device confirmation that page-entry freezing is gone
  - user real-device confirmation that network self-healing is working acceptably
- What remains risky or intentionally deferred:
  - legacy fake source files and some internal `RepositoryMode` branches still exist as non-user-facing leftovers, even though runtime is no longer switchable
  - stale-age timestamps and offline write queueing remain intentionally deferred
  - life-module business caches were explicitly left out of this module scope and may need their own later refinement if offline behavior is expanded there

## Carry-forward Notes
- Fact future modules must remember: this round establishes offline read-only as the release default for cached entry surfaces.
- Adjacent module to revisit later: uploader/write-heavy flows may need a stronger offline policy after release because this module deliberately stops at read-only fallback.
- Adjacent module uncovered during device QA: `LifeConsoleScreen` currently refreshes on launch, timed refresh, or `ON_RESUME`, but does not participate in the shared shell read-cache recovery model.
- Future regression watch: if photos, notifications, trash, or auth flows gain new persistent-cache reads or writes, keep them off the main thread by default.

## Closeout Self-check
- Brief completeness: final shipped behavior, device-QA outcome, and accepted defers are now reflected; no known module-scope issue remains undocumented.
- Remaining risk clarity: remaining risk is limited to accepted defers and adjacent-module follow-up, not unresolved shell/base blockers.
- Carry-forward quality: future-relevant facts are short, explicit, and focused on the offline/read-cache model plus main-thread-IO guardrails.
