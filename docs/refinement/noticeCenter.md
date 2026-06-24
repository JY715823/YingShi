# Notice Center

> One module only. Keep this brief current so future turns can resume from here.

- Module key: `noticeCenter`
- Status: `device_qa`
- Last updated: `2026-06-23`
- Primary surfaces: `android | server | shared`
- Linked server brief: `none`

## Module Goal
- User value: make notification center a reliable shared-space history and reminder surface, with grouped media tasks, clear filters, and system push notifications.
- Business or product intent: notifications should explain what happened, who did it, and let the user jump directly to the relevant media or life record.
- Success criteria: no `Unexpected server error`; list is newest-first with date sections; default actor filter shows partner only; self events are visible as read history when selected; media thumbnails route individually; push settings and visible system notifications work.

## Current State
- Existing `/api/notifications` materializes events from comments, small albums, trash, and upload tasks at read time.
- Existing Android notification center has real list/detail/read/read-all, route snapshots, local cache, and thumbnail resolution, but only single-target DTO fields.
- Existing FCM path registers device tokens and refreshes life widgets with data-only `life_console.changed`; it does not show status-bar notifications.

## Your Current Ideas
- Use transfer-center style task cards: one operation per entry, multiple thumbnails, collapsible, date sections.
- Put module categories on the same row as the module switch, behind a right-side hamburger menu.
- Photo categories: `全部、内容更新、评论、删除、系统`.
- Show both users' history, but default to partner avatar only.
- Implement push notifications and settings defaults this round.

## Scope Boundaries
### In scope
- Android and server notification contract upgrade, grouped notification cards, actor filters, push settings, visible FCM notifications, and deep links.

### Out of scope
- Rewriting every historical app action into a durable event table. This round keeps materialized events but hardens their payload and grouping.

### Non-negotiables
- The feed must not fail entirely because one source row has missing/old data.
- Self events are history only by default: read and not pushed.

## Frontend and Backend Contracts
### Server endpoints and payloads
- Extend existing notification DTO with module/category/actor/group/media fields while preserving old fields.
- Add push preference read/update endpoints under `/api/push/preferences`.

### Shared rules
- Default actor filter: partner checked, self unchecked.
- Default push preferences: photos content update/comment enabled, photos delete/system disabled; life trace enabled, life ledger/chat/system disabled.

## Implementation Notes
### Client
- Notification center renders newest-first date sections, module/category filtering, partner/self actor filtering, grouped media strips, and per-media thumbnail routing.
- Media thumbnail taps now mark the notification read optimistically, persist the read/cache state, and route to the selected media in the photo feed instead of opening Viewer.
- Cached notifications now preserve `mediaItems`, so reopened notification center/offline cache keeps thumbnails and per-media targets instead of falling back to title-only rows.
- Settings now reads push preferences from `/api/push/preferences` and writes switch changes back with optimistic local state plus rollback on remote failure.
- Android push data handling was expanded to show visible status-bar notifications and route photo pushes to photo feed / life pushes to life module entry points.
- Photo push deep links now mark the target media as notification-highlighted in the photo feed, with a longer-lived glow and `通知` badge so cold-start navigation does not lose the visual cue.
- Photo small-album push deep links now parse `photos:small-album:<postId>` before the generic `photos` route, carry `targetRoute/category` through foreground notification intents, and open the matching small-album detail; comment pushes set `autoOpenComment`.
- Media-comment push deep links now keep the media target but also carry `autoOpenViewer/autoOpenComment`, so tapping a `photos:media:<mediaId>` comment push opens the media in Viewer and expands the comment panel instead of stopping at photo-feed highlight.
- Cross-device sync now has a dedicated `NOTIFICATIONS` module instead of letting notification center clear photo/albums/trash/life stale flags; small-album detail also listens for album/comment staleness so comments and edits do not require an app restart.
- Photo feed's stale refresh now clears `PHOTO_FEED` only after the remote refresh succeeds, so a failed or cancelled refresh keeps the prompt visible instead of hiding stale cached content.
- FCM sends are high-priority `notification + data` hybrid messages on the new `yingshi_shared_updates_v2` channel, so killed/background Android processes can still let the system tray show a notification while foreground delivery keeps the app-rendered presenter path.
- Android now has a second reliability layer: sync version polling keeps running at a slower cadence in background, checks unread partner notifications when the notification version advances, and posts a local status-bar notification if FCM did not surface one.
- The fallback also runs during the first sync baseline after app start/resume, so a missed push is not silently swallowed just because the client had no previous local notification version.
- Push reliability follow-up found the unstable-looking behavior was mostly observability and targeting drift: local `.env` had `PUSH_SELF_FALLBACK_ENABLED=false` again, so single-device verification had no eligible target after the actor's own device was excluded; service-side delivery decisions were only in logs, so no-token, preference-disabled, FCM-failed, and OS-permission cases all looked like "push disappeared."
- Settings now shows a "最近推送诊断" row backed by `/api/push/diagnostics`, so the app can display the last delivery status/reason, target counts, FCM success counts, and whether self fallback was used.
- Module filter chips now show unread counts as top-right badges on the photo/life module controls, and the badge count respects the current actor filter instead of counting hidden self/partner entries.
- Small-album comment push deep links now force-refresh post comments when opening the comment sheet, then perform a short delayed refresh to avoid showing the previous comment list during push-to-open races.

### Server
- `/api/notifications` now returns enriched DTO fields for module, category, actor, group/operation, media thumbnails, target route, and self-actor read state.
- Upload-task notifications are grouped by operation id and include all completed media items available from persisted upload/media rows.
- Push preferences are persisted in `push_preferences` with defaults matching the module decision set.
- `/api/push/preferences` read/update endpoints are implemented and used by Android settings.
- Photo comment, content update, and delete flows now call `PushNotificationService.notifyPhotoChanged`, filtered by recipient preference and excluding the actor's own devices.
- `/api/sync/versions` now returns `notificationVersion`; comments and upload task history contribute to notification freshness, and comments also contribute to photo/albums freshness.
- Life console push still uses the existing `life_console.changed` event, now preference-filtered with visible notification title/body/route data.
- Upload completion push is operation-scoped: one multi-media import operation sends one `photos/content_update` push summarizing image/video counts, instead of one push per media item.
- Photo comment pushes and small-album content-update pushes are now registered after the surrounding database transaction commits, so a user tapping the push should not arrive before the new comment/edit is readable.
- Photo/life push side effects now run asynchronously after transaction commit for comments, small-album content updates, trash deletes, and life-console changes, so slow FCM calls no longer hold the user-facing request open.
- `/api/sync/versions` now includes deleted media/small-album/album row update timestamps in photo/albums freshness, so moving media to trash advances both `photoFeedVersion` and `trashVersion`.
- Push delivery attempts are now persisted in `push_delivery_audits` with module/category/route, enabled/partner/target device counts, attempted/successful/invalid FCM counts, status, reason, and self-fallback usage. This makes the root cause visible after the fact instead of relying on container logs.
- Local server `.env` enables `PUSH_SELF_FALLBACK_ENABLED=true` again for one-device verification, but the code only falls back when there is no partner device token; if the partner has a token but disabled that category, diagnostics reports `partner_preference_disabled` instead of sending to self.

### Design
- Notification module/category controls stay on the same top surface; category options are behind the right-side menu.
- Device-QA polish pass split the top surface into two readable rows: title/count/user selector on the first row, module chips/category/menu/bulk actions on the second row. The leading `模块` label was removed, and clear is now a trash icon.
- Photo categories are ordered as `全部 / 内容更新 / 评论 / 删除`; life categories are `全部 / 今日痕迹 / 记账 / 聊天导入`, with the old sync-reminder and system wording removed from the visible category set.
- Date headers are larger and the list uses task-style cards with compact grouped thumbnails.
- Follow-up polish removed the framed top control container entirely and split controls into title/bulk actions, actor selector, then module/category menu rows. Notification task cards now reduce repeated explanatory text, make the time more prominent, and place multi-media expand/collapse at the card's bottom-right.
- Photo/life module unread state is now marked where the user expects it: on the module chips themselves, as compact top-right count badges instead of inline text that competes with the module label.

### Interaction Feedback
- Default actor filter is partner only; self events can be included as read history.
- Self-origin materialized notifications are treated as read and are not pushed to the actor.
- Push setting switches are responsive immediately but revert if the server update fails.
- Clicking notification media thumbnails locates the chosen media in the photo feed, preserving the "do not open Viewer first" rule.
- Tapping a photo push notification locates the target media and keeps a visible notification highlight long enough for the user to recognize the destination.
- B-device push diagnostics on 2026-06-19 showed B has an enabled token and the server was sending to it successfully; the later instability was traced to split delivery assumptions and a sync fallback gap: app-rendered notifications only covered foreground/data callbacks, while sync fallback skipped the first baseline poll and immediate polls could sleep behind the background interval. The 2026-06-23 deep fix keeps system-rendered FCM for killed/background reliability and adds first-baseline/background polling fallback.
- Tapping a small-album comment push opens the comment sheet, switches it into a fresh load, and retries once shortly after open so the new partner comment is less likely to be hidden behind stale state.
- Background small-album push click actions now use `OPEN_SMALL_ALBUM` and the manifest declares that action, keeping system-rendered hybrid FCM notifications aligned with foreground app-created notifications.

## Post-implement Self-check
- Validation run: passed `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"` on 2026-06-19; passed `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -DskipTests compile"` on 2026-06-19. Server package was rebuilt, copied into `yingshi-server`, restarted, and `/api/health` returned `UP`. WSL-local Java remains unavailable, so Windows wrappers were used.
- Validation run: passed `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"` on 2026-06-20 after module unread-badge and comment deep-link refresh changes. Passed `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -DskipTests compile"` and `mvnw.cmd -q -DskipTests package` after after-commit push changes; server jar was copied into `yingshi-server`, container restarted, and `/api/health` returned `UP`.
- New behavior sanity: notification grouping/filtering/cache/media-routing and push preference sync are implemented inside this module scope.
- New behavior sanity: latest pass keeps the change inside notification center / push deep links; no unrelated navigation contract was expanded.
- Contract sanity: Android DTO/model/repository contracts now match server notification enrichment and push preference endpoints.
- Contract sanity: no DTO shape changed in the latest pass; the server-only timing change keeps the existing FCM payload contract and only moves send execution to `afterCommit`.
- Test plan quality: includes local compile checks, linked-module regression areas, and real-device push/deep-link scenarios.
- Known gaps: push quick-view directly into a notification-only lightweight media viewer is not fully implemented; photo push currently routes into photo feed and life push routes into life surfaces. The newest comment deep-link fix still needs real two-device confirmation because the race depends on FCM tap timing and Android app state. Repo-wide `git diff --check` remains noisy because many unrelated existing files have CRLF/trailing-whitespace churn; compile checks passed for this round.
- Validation limitation on 2026-06-23: current Codex sandbox cannot run Docker (`/var/run/docker.sock` permission denied), Windows wrapper compile fails with `UtilBindVsockAnyPort`, and WSL has no `java`; this pass was code-inspected and targeted `rg`-checked, but real server migration/build and device FCM delivery still need the normal Windows/Docker environment.
- Validation update on 2026-06-23: targeted `git diff --check` passed for the touched Android push/sync/manifest/script/doc files and the touched server FCM sender. Compile/device validation is still blocked in this sandbox because WSL has no Java, `gradlew` has a Windows line-ending shebang, and Windows executable bridging is unavailable here.

## New Coupling Recheck
- Module: photos / comments / small albums / trash
  - What was rechecked: notification materialization fields and push triggers for comments, content update, and delete.
  - Result: compile passed; comment and small-album content-update pushes now run after transaction commit. Real-device tap-to-comment behavior still needs end-to-end confirmation.
- Module: settings / push / FCM
  - What was rechecked: preference defaults, read/update API, Android setting switches, and visible notification routing.
  - Result: compile passed; requires device token/notification permission testing on device.
- Module: upload / transfer history
  - What was rechecked: grouped upload notification DTO shape and media thumbnail preservation in cache.
  - Result: compile passed; upload push trigger is now operation-scoped and deployed to the running server; needs real multi-select upload push check.
- Module: system media / quick add import
  - What was rechecked: bottom picker import and system media import-status matching now share metadata fingerprints while preserving old MediaStore fingerprint lookup; system media cache displays first, then refreshes import status in the background.
  - Result: Android compile passed; needs real-device check for duplicate skip prompt and `已导入` badges after bottom-plus imports.

## Implement Test Plan
### Locally validated
- Check: Android compile.
- Result: `:app:compileDebugKotlin` passed via Windows Gradle wrapper; only pre-existing deprecated fullscreen/status-bar warnings in `PhotoViewerScreen.kt`.
- Check: Server compile.
- Result: `mvnw.cmd -q -DskipTests compile` passed via Windows Maven wrapper.

### Linked-module regression checks
- Module: photo feed navigation
  - What to recheck: tap notification media thumbnail, return to notification center, then re-enter photo feed.
  - Why it can regress: notification now uses photo-feed locating route rather than Viewer/detail routing.
- Module: comments / small album edits / trash
  - What to recheck: partner receives notification center entry and push only when their preference is enabled.
  - Why it can regress: these flows now trigger push service calls.
- Module: settings
  - What to recheck: toggles load from backend after app restart and revert on failed update.
  - Why it can regress: settings now has backend coupling.

### Real-device checks for the user
- Scenario: open notification center with existing history.
  - Steps: enter notification center after comments/uploads/deletes exist.
  - Expected result: no `Unexpected server error`; list is time-desc with date sections, partner-only by default, and category menu on the module row.
- Scenario: grouped upload notification.
  - Steps: import/upload multiple media in one operation, then open notification center.
  - Expected result: one task-style entry with multiple thumbnails; tapping a specific thumbnail locates that media in photo feed.
- Scenario: grouped upload push.
  - Steps: from A, import 3 photos in one picker operation while B has a registered push token.
  - Expected result: B receives one visible push summarizing 3 photos, not three separate pushes.
- Scenario: quick-add duplicate import.
  - Steps: import a system media item from the bottom plus picker, then open System Media and select the same item again.
  - Expected result: the media is marked imported after refresh; repeated import is skipped with a duplicate prompt instead of creating another media row.
- Scenario: push preferences.
  - Steps: toggle photo comment/content/life trace push settings, restart app, and reopen settings.
  - Expected result: toggles keep backend state; disabled categories do not produce status-bar pushes.
- Scenario: visible push deep link.
  - Steps: with notification permission granted, perform partner comment/content update/life trace action.
  - Expected result: system notification appears; tapping photo push enters photo feed target, tapping life trace enters life module.
- Scenario: photo push highlight.
  - Steps: tap a photo/content push that targets `photos:media:<id>`.
  - Expected result: app opens the photo feed, scrolls to that media, and shows a `通知` badge/glow on the target item.
- Scenario: B-device background push.
  - Steps: install the latest build on B, open the app once to create the notification channel and register token, then lock/background B and trigger a photo content update from A.
  - Expected result: server log shows `successful=1` with `targetUsers=[user_demo_b]`, and B receives a status-bar notification even while the app is not foregrounded.

### Still unverified
- Risk: push diagnostic migration and endpoint runtime verification.
  - Why it remains open: the current sandbox cannot access Docker and cannot launch Windows build wrappers; WSL also has no Java runtime.
  - Best next verification path: rebuild/restart `yingshi-server`, confirm Flyway applies `V19__push_delivery_audits.sql`, call authenticated `GET /api/push/diagnostics`, then trigger one comment/content update and verify the latest row shows `sent/fcm_accepted` or an explicit no-target reason.
- Risk: FCM delivery and Android notification permission behavior.
  - Why it remains open: local server config is now valid, but true acceptance still requires two logged-in devices, registered FCM tokens, background/foreground app states, and OS notification permission.
  - Best next verification path: install the current Android build on two devices, grant notification permission from Settings, trigger enabled partner photo/life actions, and verify disabled categories stay silent.
- Risk: direct lightweight media quick-view from push.
  - Why it remains open: current implementation routes media pushes into photo feed instead of a standalone quick-view activity.
  - Best next verification path: decide whether this belongs in noticeCenter follow-up or a dedicated push/deep-link polish pass.

## Device QA Findings
### Issue 2026-06-19-01
- Date: 2026-06-19
- Build version: local debug build, post noticeCenter implementation
- Device / OS: user real-device report; exact OS not provided
- Module key: `noticeCenter`
- Test environment: Android app against local Docker server on port 8080
- Repro steps:
  1. Open notification center.
  2. Observe the top control area.
- Expected result: user selector, module switch, category menu, one-key read, and clear action should be visually separated and easy to scan.
- Actual result: module, category, user selector, read-all, and clear controls were crowded into the same area; a leading `模块` label wasted space; clear used text instead of a trash icon.
- Evidence: user report in verify round.
- Severity: `polish`
- Server-related: `no`
- Reproducibility: `always`
- Suspected area: `NotificationCenterTopBar`
- Next action: completed in this pass; top surface now uses title/count/user row plus module/category/action row, and clear uses a trash icon with disabled state.

### Issue 2026-06-19-02
- Date: 2026-06-19
- Build version: local debug build, post noticeCenter implementation
- Device / OS: user real-device report; exact OS not provided
- Module key: `noticeCenter`
- Test environment: Android app against local Docker server on port 8080
- Repro steps:
  1. Open notification center with existing trash/upload history.
  2. Let the app request `GET /api/notifications`.
- Expected result: notification list renders, even with older trash history rows.
- Actual result: Android showed `Unexpected server error`.
- Evidence: Docker logs showed `No enum constant com.yingshi.server.domain.TrashItemType.LARGE_ALBUM_DELETED` from `NotificationService.collectNotificationEvents`.
- Severity: `blocker`
- Server-related: `yes`
- Reproducibility: `always` when the stale server image handled a database containing `LARGE_ALBUM_DELETED`.
- Suspected area: stale Docker server image plus historical trash enum compatibility.
- Next action: completed in this pass. The running jar now contains `LARGE_ALBUM_DELETED` and `V17__push_preferences.sql`; database schema is at v17; authenticated `GET /api/notifications?limit=5` returned 200 with enriched media/cover payloads.

### Issue 2026-06-19-03
- Date: 2026-06-19
- Build version: local server container, post noticeCenter implementation
- Device / OS: local server/runtime inspection; push arrival still needs real devices
- Module key: `noticeCenter`
- Test environment: Docker Compose server, Firebase Cloud Messaging path
- Repro steps:
  1. Inspect server container environment and startup logs.
  2. Try enabling FCM env from `.env`.
- Expected result: FCM config should enter the container only when credentials are usable; missing credentials should not take down the whole server.
- Actual result: fixed. `.env` now uses `FCM_SERVICE_ACCOUNT_HOST_PATH=E:/Secrets/yingshi-73941-firebase-adminsdk-fbsvc-054ea925da.json`, Compose mounts it into `/run/secrets/firebase-service-account.json`, `FCM_SERVICE_ACCOUNT_JSON_BASE64` is empty, and the running container has `FCM_ENABLED=true`.
- Evidence: `docker exec yingshi-server` showed `FCM_ENABLED=true`, `FCM_DRY_RUN=false`, `FCM_PROJECT_ID=yingshi-73941`, `FCM_SERVICE_ACCOUNT_PATH=/run/secrets/firebase-service-account.json`, base64 length `0`, and `firebase-secret-mounted`; `GET /api/health` returned 200.
- Severity: `major`
- Server-related: `yes`
- Reproducibility: `always` until valid credentials are provided in a container-readable form.
- Suspected area: Docker Compose FCM environment/secret wiring.
- Next action: completed for local runtime config. Real push delivery still needs two-device acceptance with notification permission granted.

### Issue 2026-06-19-05
- Date: 2026-06-19
- Build version: local server and Android repo cleanup pass
- Device / OS: local configuration inspection
- Module key: `noticeCenter`
- Test environment: Docker Compose server plus Android debug backend config
- Repro steps:
  1. Inspect local server `.env`, Compose files, start/stop scripts, and running containers.
  2. Search Android debug backend fallbacks and server docs/tests for Cloudflare tunnel dependencies.
- Expected result: local development should no longer depend on Cloudflare tunnel config or runtime containers.
- Actual result: fixed. The retired tunnel token line is absent from `.env`, the tunnel Compose overlay and tunnel doc were removed, start/stop scripts use only `docker-compose.yml`, the old tunnel container is not running, and Android debug backend fallback no longer points at a tunnel URL.
- Evidence: `docker ps` shows only `yingshi-server`, `yingshi-minio`, and `yingshi-postgres`; repo search after cleanup has no runtime/config tunnel references.
- Severity: `major`
- Server-related: `yes`
- Reproducibility: `always` before cleanup
- Suspected area: retired local tunnel deployment path
- Next action: completed. Future external access should be added as a separate deployment path if needed, not as the default local server path.

### Issue 2026-06-19-06
- Date: 2026-06-19
- Build version: local server after push verification pass
- Device / OS: user real-device report; exact Android version not provided
- Module key: `noticeCenter`
- Test environment: local Docker server with one registered Android FCM token
- Repro steps:
  1. Trigger notification-worthy behavior from the currently logged-in user.
  2. Expect a system notification to appear on the same phone during local testing.
- Expected result: local development should have a way to verify visible FCM notification delivery even before a second device is logged in.
- Actual result: fixed. The database had only `user_demo_a` registered in `push_device_tokens`, while server push logic intentionally excludes the actor's own device. Local `.env` now enables `PUSH_SELF_FALLBACK_ENABLED=true`; when no partner token exists, server sends to the actor's own registered device for local verification. Upload completion now also triggers `photos/content_update` push, so importing media can produce a visible notification.
- Evidence: `push_device_tokens` contained one enabled Android token for `user_demo_a`; runtime env shows `PUSH_SELF_FALLBACK_ENABLED=true`, `FCM_ENABLED=true`, and `FCM_DRY_RUN=false`; server compile/package and health check passed after the upload push trigger.
- Severity: `major`
- Server-related: `yes`
- Reproducibility: `always` when testing push with only one logged-in device
- Suspected area: push target selection plus upload-success push trigger
- Next action: user should retry an upload or life trace action with Android notification permission granted; server logs now emit info lines for self-fallback, no-target, and sent push counts.

### Issue 2026-06-19-07
- Date: 2026-06-19
- Build version: local Android and server after B-device push investigation
- Device / OS: two user devices; exact models and OS versions not provided
- Module key: `noticeCenter`
- Test environment: local Docker server, FCM project `yingshi-73941`
- Repro steps:
  1. Use A account and B account on separate phones.
  2. Upload media and expect the opposite phone to receive a system notification.
- Expected result: A actions should push to B's registered device, and B actions should push to A's registered device.
- Actual result: partially fixed and diagnosed. A can receive pushes, which confirms Firebase project/service-account sending is usable. The server database currently contains only A's FCM token and no B token, so A actions have no B target. B actions can push to A because A has a registered token.
- Evidence: `push_device_tokens` had one enabled Android token for `user_demo_a / 1085060329@qq.com` and no row for `user_demo_b / 2926315047@qq.com`; logs showed `No target device token` for A-origin photo pushes and `Sent photo push to 1 device(s)` when a push had A as target. `google-services.json` package/project match `com.example.yingshi / yingshi-73941`.
- Severity: `major`
- Server-related: `partly`
- Reproducibility: `always` until B device registers a token
- Suspected area: B device FCM token acquisition or client token-registration path
- Next action: completed in code for observability. Android now retries push-token registration on every `MainActivity.onResume`, and Settings now shows a `推送设备注册` diagnostic row with token acquisition / backend registration status and a manual retry action.

### Issue 2026-06-19-04
- Date: 2026-06-19
- Build version: local debug build, post noticeCenter verification
- Device / OS: user real-device report; Android version not provided
- Module key: `noticeCenter`
- Test environment: Settings notification permission row
- Repro steps:
  1. Open app after notification push work.
  2. Expect Android notification permission prompt.
- Expected result: Android 13+ should provide a clear way to grant `POST_NOTIFICATIONS`.
- Actual result: app only declared the permission and displayed current state; it did not actively call the runtime permission request.
- Evidence: code inspection found `POST_NOTIFICATIONS` in Manifest and status display in Settings, but no `ActivityResultContracts.RequestPermission` path for notification permission.
- Severity: `major`
- Server-related: `no`
- Reproducibility: `always` before the fix on Android 13+ if the permission was not previously granted.
- Suspected area: Settings permission UX.
- Next action: completed in this pass. The Settings `通知权限` row now requests `POST_NOTIFICATIONS` when missing, and opens the app notification settings when already granted or managed by system settings.

### Issue 2026-06-19-08
- Date: 2026-06-19
- Build version: local debug build after push deep-link patch
- Device / OS: user real-device report; exact Android version not provided
- Module key: `noticeCenter`
- Test environment: system notification click from photo small-album create/edit/comment pushes
- Repro steps:
  1. From one user, create or edit a small album, or add a comment.
  2. Tap the status-bar push on the other device.
- Expected result: app opens the target small-album detail; comment pushes open the same target with the comment entry behavior.
- Actual result: fixed in code. `photos:small-album:<postId>` was previously swallowed by the generic `photos` route and only opened the photo feed. Android now handles small-album routes before generic photo routes, and foreground notifications include the full `targetRoute/category` extras.
- Evidence: Android `:app:compileDebugKotlin` passed after the route patch; diff check passed for touched Android files.
- Severity: `major`
- Server-related: `no`
- Reproducibility: `always` before the route-order fix for small-album pushes
- Suspected area: `MainActivity.handleLaunchIntent`, foreground push `contentIntent`, and app-level deep-link consumption
- Next action: user should test create/edit/comment push taps on device; server rebuild is not required for this Android-only route fix.

### Issue 2026-06-19-09
- Date: 2026-06-19
- Build version: local Android + server after sync split patch
- Device / OS: user real-device report; exact Android version not provided
- Module key: `noticeCenter`
- Test environment: two-device album edit/comment, notification center, and app foreground/background sync
- Repro steps:
  1. From one user, edit a small album or add a comment.
  2. Keep the other device in the app without restarting.
  3. Check album/detail refresh, notification center refresh, and status-bar push arrival.
- Expected result: push should arrive when enabled; notification center should refresh without restarting; album/detail surfaces should see edits/comments through sync polling.
- Actual result: fixed in code and deployed to local server. Notification center was clearing `PHOTO_FEED/ALBUMS/TRASH/LIFE_CONSOLE` stale flags after only refreshing its own list, which could prevent album/detail surfaces from refreshing. Comments were also missing from server sync version calculation, so comment changes could remain invisible until full reload.
- Evidence: server logs before this pass showed photo pushes were being sent successfully for `photos:small-album:<id>` and `category=comment`; Android and server compiles passed after the sync split; server jar was redeployed, health returned `UP`, and Flyway migrated to v18.
- Severity: `major`
- Server-related: `yes`
- Reproducibility: likely `always` for comments before sync version fix; timing-dependent for album edits when notification center consumed shared stale flags first
- Suspected area: `SyncVersionTracker`, `NotificationCenterScreen`, `SyncService`
- Next action: user should install the new Android build, keep both devices open, edit/comment a small album, and confirm notification center plus album/detail update without restart.

### Issue 2026-06-20-01
- Date: 2026-06-20
- Build version: local Android after noticeCenter unread-badge polish
- Device / OS: user real-device report; exact Android version not provided
- Module key: `noticeCenter`
- Test environment: notification center top controls with photo/life module filters
- Repro steps:
  1. Open notification center with unread photo or life notifications.
  2. Look at the two module controls.
- Expected result: photo and life module controls should visibly show unread state at their top-right, without adding more crowded inline text.
- Actual result: fixed in code. Module filter chips now render a compact top-right unread badge, capped at `99+`, and the count follows the active actor filter so hidden self/partner entries do not inflate the visible module badge.
- Evidence: Android `:app:compileDebugKotlin` passed on 2026-06-20 after the UI change.
- Severity: `polish`
- Server-related: `no`
- Reproducibility: `always` before the UI polish when unread module counts existed
- Suspected area: `NotificationCenterTopBar`, `NotificationFilterChip`
- Next action: user should visually confirm badge placement on the actual device width.

### Issue 2026-06-20-02
- Date: 2026-06-20
- Build version: local Android + server after comment deep-link race fix
- Device / OS: user real-device report; exact Android version not provided
- Module key: `noticeCenter`
- Test environment: status-bar push tap for small-album comments
- Repro steps:
  1. From A, add a small-album comment.
  2. On B, tap the comment push.
  3. Observe the comment sheet content immediately after open.
- Expected result: app opens the correct small album, opens the comment sheet, and shows the newly added comment rather than an older cached list.
- Actual result: fixed in code and deployed to the local server. Server now sends comment pushes only after the comment transaction commits; Android forces a fresh post-comment load when auto-opening the sheet and retries once after a short delay.
- Evidence: Android `:app:compileDebugKotlin` passed; server compile/package passed; `docker restart yingshi-server` completed and `/api/health` returned `UP`.
- Severity: `major`
- Server-related: `yes`
- Reproducibility: timing-dependent before the fix; more likely when the user tapped the push immediately
- Suspected area: `CommentService` push timing, `PostDetailScreen` auto-open comment refresh
- Next action: user should retry A -> B small-album comment push while B is backgrounded and foregrounded.

### Issue 2026-06-22-01
- Date: 2026-06-22
- Build version: local Android + server after delete-sync/push hardening
- Device / OS: user real-device report; exact Android version not provided
- Module key: `noticeCenter`
- Test environment: two-device photo feed, trash, notification/push pipeline
- Repro steps:
  1. From A, delete a media item to trash.
  2. Keep B in the app and switch between photo feed and trash without restarting.
  3. Observe whether B's trash and photo feed both become stale.
- Expected result: B should not need an app restart; trash and photo feed should both get a refresh prompt/version bump, and refreshing photo feed should remove the deleted item.
- Actual result: fixed in code and deployed to the local server. `photoFeedVersion` previously ignored deleted media/small-album rows because it only looked at active rows, so a delete could advance `trashVersion` while leaving B's photo feed stale state unchanged. The version query now includes deleted-row update timestamps, and the photo feed stale banner is only cleared after refresh success.
- Evidence: Android `:app:compileDebugKotlin` passed; server compile/package passed; new jar was copied into `yingshi-server`; `/api/health` returned `UP`; a temporary media deletion through `DELETE /api/media/{mediaId}` advanced `photoFeedVersion`, `trashVersion`, and `notificationVersion`, then the temp data was cleaned up.
- Severity: `blocker`
- Server-related: `yes`
- Reproducibility: likely `always` when the deleted row was the newest media/small-album update
- Suspected area: `SyncService`, `RealPhotoFeedViewModel`, `RealPhotoFeedPage`
- Next action: user should retry A deletes media while B stays open; B should see `有新内容，点击刷新` on photo feed and the deleted item should disappear after refresh.

### Issue 2026-06-22-02
- Date: 2026-06-22
- Build version: local server after push async dispatch
- Device / OS: user real-device report; exact Android version not provided
- Module key: `noticeCenter`
- Test environment: upload/media import, small-album operations, trash deletes, life-console push side effects
- Repro steps:
  1. Trigger upload completion, create/edit/add-to small album, comment, delete, or life-console update.
  2. Watch whether the request appears stuck while waiting for push.
- Expected result: the primary operation should finish as soon as its database transaction commits; push sending should continue in the background and log failures without blocking the operation.
- Actual result: fixed in code and deployed to the local server. Comment/content/trash/life push dispatch now runs through async after-commit support. Delete push still follows the `照片删除推送` preference; runtime logs showed B has a token but delete push had no target because delete push is disabled by default.
- Evidence: server compile/package passed; `/api/health` returned `UP`; runtime logs show async push work on a background worker and B token registration for `user_demo_b`.
- Severity: `major`
- Server-related: `yes`
- Reproducibility: timing-dependent before the fix, especially when FCM was slow or blocked
- Suspected area: `TrashService`, `PostService`, `CommentService`, `LifeConsoleService`, `PushNotificationService`
- Next action: user should retry upload/new small album/edit/comment on two devices; for delete status-bar push specifically, enable Settings -> `照片删除推送` on the receiving phone.

### Issue 2026-06-23-01
- Date: 2026-06-23
- Build version: local Android after media-comment push routing fix
- Device / OS: user real-device report; exact Android version not provided
- Module key: `noticeCenter`
- Test environment: media comment status-bar push tap
- Repro steps:
  1. From A, add a comment directly on a media item.
  2. On B, tap the media-comment push.
  3. Observe the landing surface.
- Expected result: the app should open the target media in Viewer and open the media comment panel automatically.
- Actual result: fixed in code. `photos:media:<mediaId>` push routes now preserve `autoOpenViewer/autoOpenComment` when the category is `comment`; `YingShiApp` writes those flags into `GlobalPhotoFeedPageStateStore`, and the existing photo-feed locate flow opens Viewer and lets Viewer expand the comment panel.
- Evidence: `git diff --check` passed for `MainActivity.kt`, `AppNavigationRequests.kt`, and `YingShiApp.kt`. Kotlin compile could not run in this shell because Windows process launch failed with `UtilBindVsockAnyPort` and Linux-side Java is unavailable.
- Severity: `major`
- Server-related: `no`
- Reproducibility: `always` before the fix for media-comment push routes
- Suspected area: `MainActivity`, `AppNavigationRequests`, `YingShiApp`, `PhotoFeedPageStateStore`
- Next action: user should tap a media-comment push on device and confirm it opens Viewer with comments, not just the photo-feed highlight.

## Validation Snapshot
### Verified
- Case: Android compile after top-bar changes.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"` passed on 2026-06-19.
- Case: Android compile after notification permission request entry.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"` passed on 2026-06-19.
- Case: Server compile/package after noticeCenter server changes.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -DskipTests compile"` and `mvnw.cmd -q -DskipTests package` passed on 2026-06-19.
- Case: Server runtime health after restart.
- Result: `GET /api/health` returned 200; Flyway validated 17 migrations and schema is at v17.
- Case: Notification list API with existing trash history.
- Result: authenticated `GET /api/notifications?limit=5` returned 200; response included grouped media items and video cover URLs.
- Case: FCM local runtime configuration.
- Result: `.env` points to the E-drive Firebase Admin SDK file via `FCM_SERVICE_ACCOUNT_HOST_PATH`; the running container has `FCM_ENABLED=true`, `FCM_DRY_RUN=false`, an empty base64 override, and a non-empty mounted `/run/secrets/firebase-service-account.json`; health endpoint returned 200.
- Case: Cloudflare local-runtime removal.
- Result: Retired tunnel token/config overlay/start-stop wiring/Android tunnel fallback were removed; no old tunnel container is running.
- Case: Server config cleanup regression tests.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -Dtest=ObjectKeyPolicyTests,MediaStorageFieldServiceTests,ContentMapperCdnSigningTest,LocalObjectStorageServiceTests test"` passed on 2026-06-19.
- Case: Single-device local push fallback and upload push trigger.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -DskipTests compile"` and package/restart passed on 2026-06-19; runtime has `PUSH_SELF_FALLBACK_ENABLED=true`, `FCM_ENABLED=true`, `FCM_DRY_RUN=false`, and health returned 200.
- Case: B-device token diagnosis.
- Result: server inspection showed no B token; Android `:app:compileDebugKotlin` passed after adding push-token retry on resume and Settings push-device registration diagnostics.
- Case: Small-album push deep-link compile and formatting.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"` passed on 2026-06-19 after adding `photos:small-album:<postId>` routing; `git diff --check -- <touched Android files>` passed.
- Case: Notification/albums sync split.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"` passed on 2026-06-19; `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -DskipTests compile"` and package passed; `docker restart yingshi-server` completed and `/api/health` returned `UP`.
- Case: Ledger migration needed by current server code.
- Result: added `V18__ledger_snapshot_last_modified_by.sql`; Flyway applied v18 and `ledger_snapshots.last_modified_by` exists.
- Case: Module unread badge and comment deep-link refresh.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"` passed on 2026-06-20.
- Case: After-commit comment/content push timing.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -DskipTests compile"` and `mvnw.cmd -q -DskipTests package` passed on 2026-06-20; jar was copied into `yingshi-server`, the container restarted, Flyway reported schema v18 up to date, and `/api/health` returned `UP`.
- Case: Delete sync version regression.
- Result: temporary media deletion through authenticated `DELETE /api/media/{mediaId}` advanced `photoFeedVersion` from `1782140592426` to `1782140592836`, `trashVersion` from `1782139586336` to `1782140592831`, and `notificationVersion` from `1782140592426` to `1782140592836`; the temporary media and trash rows were removed after the check.
- Case: Push async dispatch and latest build deployment.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi-Server && mvnw.cmd -q -DskipTests compile"` and `mvnw.cmd -q -DskipTests package` passed on 2026-06-22; new jar was copied into `yingshi-server`, the container restarted, Flyway reported schema v18 up to date, and `/api/health` returned `UP`.
- Case: Android photo-feed stale refresh.
- Result: `cmd.exe /c "cd /d E:\Study\App\YingShi && gradlew.bat :app:compileDebugKotlin"` passed on 2026-06-22 after making `PHOTO_FEED` stale clearing wait for a successful refresh.
- Case: Media-comment push routing static check.
- Result: `git diff --check -- app/src/main/java/com/example/yingshi/app/AppNavigationRequests.kt app/src/main/java/com/example/yingshi/MainActivity.kt app/src/main/java/com/example/yingshi/app/YingShiApp.kt` passed on 2026-06-23. Compile was blocked by environment: `cmd.exe` failed with `UtilBindVsockAnyPort`, `./gradlew`/`bash gradlew` hit CRLF wrapper issues, and Linux-side `java` was not installed.

### Pending
- Case: Visual QA of the new notification top bar on the user's device.
- What still needs checking: spacing on the actual target screen width, especially the module row with category badge/read-all/trash/menu actions.
- Case: Real-device notification permission prompt and Android status-bar notification display.
- What still needs checking: permission grant flow, background delivery, tap route, and disabled-category behavior on two logged-in devices.
- Case: Two-device push acceptance.
- What still needs checking: token registration for both users, partner-only targeting, category preference filtering, status-bar arrival, and tap routing.
- Case: Single-device visible notification after this fix.
- What still needs checking: user retries upload/comment/life trace on the current phone and confirms a status-bar notification appears.
- Case: B device push-token registration.
- What still needs checking: install the new Android build on B phone, log in as `2926315047@qq.com`, open Settings -> `推送设备注册`, and confirm it shows `已注册` rather than `无 token` or `注册失败`.
- Case: Small-album push tap route.
- What still needs checking: tap create/edit/comment status-bar pushes and confirm they open the exact small-album detail rather than only landing on the photo feed.
- Case: Album edit/comment live sync.
- What still needs checking: without restarting either app, edit a small album and add a small-album comment from A, then confirm B's album/detail and notification center update through polling/push.
- Case: Comment push opens fresh comment content.
- What still needs checking: tap a small-album comment push immediately after it appears and confirm the opened comment sheet includes the new comment.
- Case: Module unread badge visual QA.
- What still needs checking: confirm the new photo/life unread badges sit in the top-right of the module chips without covering module text on the target phone.
- Case: A-delete/B-photo-feed live acceptance.
- What still needs checking: keep B in photo feed, delete media from A, confirm B sees `有新内容，点击刷新`, then tap refresh and confirm the media disappears without app restart.
- Case: Content-update push after async dispatch.
- What still needs checking: create/edit a small album or import media from A while B is backgrounded, confirm B receives a status-bar push and the operation on A no longer waits on push delivery.
- Case: Media-comment push Viewer landing.
- What still needs checking: tap B's media-comment push and confirm the target media opens in Viewer with the comment panel expanded.

### Blocked
- Blocker: none for local FCM configuration after this pass.
- Owner or dependency: physical-device push acceptance still requires two logged-in devices with notification permission granted.

## Next Fix Queue
1. `Comment push fresh-content acceptance` - add a small-album comment from A, tap B's push immediately, and confirm the comment sheet includes the new comment.
2. `Module unread badge visual QA` - check the photo/life module chip badges on the actual phone; adjust size/offset only if they cover text.
3. `Single-device visible notification recheck` - retry upload/comment/life trace now that upload pushes and self-fallback are enabled.
4. `Two-device push acceptance` - after a second logged-in device exists, verify partner-only targeting, category preferences, status-bar arrival, and tap routing.
5. `Small-album push deep-link acceptance` - create/edit/comment a small album from the partner account, tap the push, and confirm the app opens that specific small album.
6. `Album live-sync acceptance` - keep both devices open, perform small-album edit/comment, and confirm no restart is needed for album/detail/notification-center refresh.
7. `A-delete/B-photo-feed acceptance` - delete on A while B stays open, then confirm B photo feed and trash both refresh without restart.
8. `Media-comment push Viewer acceptance` - add a media comment from A, tap B's push, and confirm B opens Viewer plus comments instead of only highlighting the feed item.
