# Frontend Backend Testing Guide

## Scope
- Android repo: `YingShi`
- paired backend repo: `yingshi-server`

## Stage 12.5 Viewer Checks

1. 在 `REAL` 图片 Viewer 中确认预览图优先级为 `thumbnailUrl -> mediaUrl -> originalUrl`。
2. 当 `originalUrl` 缺失，或与当前预览图地址相同时，确认“加载原图”按钮不出现。
3. 当 `originalUrl` 有意义时，确认“加载原图”会经历 `loading -> success / failure`，失败后仍保留预览图。
4. 左右切换图片 / 视频时，确认前一张媒体的原图状态、视频 loading、error、playing 不串到下一张。
5. 在照片流 Viewer 中点击“所属帖子”，确认单帖子直接进入帖子详情，多帖子可从 sheet 中选择。
6. 从帖子详情进入 Viewer，再点击“所属帖子”，确认仍能稳定回到对应帖子，不会停留在占位提示。
7. 系统媒体 Viewer 中确认没有评论、加载原图、所属帖子等帖子专属入口。
8. 首次打开视频时，确认有稳定封面或统一视频占位，不直接黑屏；失败时有中文状态和重试入口。
9. 帖子详情媒体区确认仍为矩形画布，图片按比例居中最大化，长图不会被错误裁剪。
- Android diagnostics page: `Photos -> Notifications -> Settings -> Backend integration diagnostics`
- backend smoke script: `../yingshi-server/scripts/integration-smoke.ps1`

## Seed Account
- account: `demo.a@yingshi.local`
- password: `demo123456`
- alternate account: `demo.b@yingshi.local`
- alternate password: `demo123456`

## 1. Start the Backend

From the `yingshi-server` root:

```powershell
.\mvnw.cmd spring-boot:run
```

Recommended first:

```powershell
.\mvnw.cmd test
```

Health URL:

```text
http://localhost:8080/api/health
```

## 2. Run the Backend Smoke Script

From the `yingshi-server` root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\integration-smoke.ps1
```

The script verifies:
- health
- login token
- me
- albums
- album posts
- post detail
- post update
- media feed
- post comments
- media comments
- upload token
- local upload
- trash list, detail, and restore

If the script finishes with:

```text
Integration smoke completed with 0 failures.
```

the backend is ready for Android smoke work.

## 3. Android Base URL Setup

Current debug default:

```text
http://10.0.2.2:8080/
```

This is correct for Android Emulator.

For a physical phone:
- the diagnostics page supports changing the value
- `127.0.0.1` is available as a manual preset
- for same-Wi-Fi testing, use the computer LAN IP instead, for example `http://192.168.1.100:8080/`

Why:
- `127.0.0.1` on the phone normally points back to the phone itself
- same-Wi-Fi testing requires the PC LAN IP

## 4. Debug Cleartext HTTP

Current Android implementation:
- debug build allows cleartext HTTP only
- release security policy is untouched

Files:
- debug manifest overlay: `app/src/debug/AndroidManifest.xml`
- debug network security config: `app/src/debug/res/xml/network_security_config.xml`

If you still see a cleartext failure:
- confirm you are installing a debug build
- confirm the base URL starts with `http://`
- rebuild with `.\gradlew.bat --no-daemon assembleDebug`

## 5. Fake Real Switching

Current behavior:
- default mode stays `FAKE`
- diagnostics page can switch `FAKE` / `REAL`
- the switch is stored in debug runtime settings
- changing `Repository mode` rebuilds the REAL page session so old fake/real view-model caches do not mix
- changing `Base URL` clears the current token and rebuilds Retrofit immediately

Files:
- config state: `app/src/main/java/com/example/yingshi/data/remote/config/BackendDebugConfig.kt`
- Retrofit entry: `app/src/main/java/com/example/yingshi/data/remote/config/RemoteServiceFactory.kt`
- repository switch point: `app/src/main/java/com/example/yingshi/data/repository/RepositoryProvider.kt`

Recommendation:
- keep UI work on `FAKE`
- switch to `REAL` only when you are explicitly checking backend integration
- switch back to `FAKE` after the pass

## 6. Diagnostics Entry

Current path in the app:
1. Open Photos.
2. Open Notifications.
3. Open Settings.
4. Open `Backend integration diagnostics`.

What this page supports:
- view and edit `baseUrl`
- apply emulator and `127.0.0.1` presets
- switch `FAKE` / `REAL`
- login with the dev seed account
- test health
- test albums and post detail
- test media and comments
- test trash
- run one combined smoke pass

## 7. Exact Physical Device Acceptance Steps

Use this exact checklist:

1. Connect the phone and computer to the same Wi-Fi.
2. Start the backend with `.\mvnw.cmd spring-boot:run`.
3. Find the computer LAN IP with `ipconfig`.
4. Confirm Windows Firewall allows inbound `8080`.
5. Build Android with `.\gradlew.bat --no-daemon assembleDebug`.
6. Install the debug app on the phone.
7. Open `Photos -> Notifications -> Settings -> Backend integration diagnostics`.
8. Replace the base URL with `http://<your-pc-ip>:8080/`.
9. Tap `Save Base URL`.
10. Verify the `Active base URL` row updates.
11. Tap `Health`.
12. Confirm `Last result` shows `[health] success`.
13. Tap `Login and verify /me`.
14. Confirm `Token state` becomes `Logged in`.
15. If you later change `Base URL`, log in again because the app now clears the old token on base-url change.
16. Tap `Albums and post detail`.
17. Confirm `Last result` includes `albums=` and `post=`.
18. Tap `Media and comments`.
19. Confirm `Last result` includes `media=`, `postComments=`, and `mediaComments=`.
20. Tap `Trash`.
21. Confirm `Last result` includes `trash=` without a failure message.
22. Tap `Run all smoke actions`.
23. Confirm the page lists each smoke item as `success` or `failed`, and the summary contains `health=UP`, `upload=success`, and `trash=`.
24. If you want to verify future real repository wiring, switch mode to `REAL`, then reopen the target screen so it picks up the new repository session.
25. Open `照片` and confirm the feed shows real thumbnails or safe placeholders instead of flat fake gradients.
26. Open `相册` and confirm post cards show real cover thumbnails. If some cards are briefly plain then recover after load, that is the current per-post detail enrichment path.
27. Open one post detail page and confirm the media area shows real thumbnails, while missing URLs and failed image requests stay on a safe placeholder.
28. Open `Gear Edit -> 媒体管理` and confirm the grid shows the same real thumbnails or safe placeholders without crashing.
29. Open one image from the photo feed Viewer and confirm it first shows the preview image on a dark immersive background.
30. Tap the original action and confirm the Viewer shows an original-loading state, then either the original image or a safe retry/failure state while keeping the preview available when possible.
31. Swipe between several Viewer media items and confirm original loading / failed / loaded states do not leak between different `mediaId` values.
32. Open an in-post Viewer from a post detail media item and repeat the preview, original-load, and dark-background checks.
33. Open one REAL video from the photo-flow Viewer and confirm it can load, play, pause, and stop when you swipe away to another media item.
34. Open one REAL video from an in-post Viewer and confirm play / pause works there too, without carrying the previous media item's loading, error, or progress state.
35. Open one system-media video Viewer and confirm the video itself can zoom or pan while the playback control bar stays fixed near the lower-left area inside the media canvas.
36. In any video Viewer, verify that missing URLs or failed playback show Chinese fallback copy and do not crash the page; use the retry entry if the request can be retried.
37. Switch mode back to `FAKE` when finished.

## 8. Common Problems

Emulator cannot connect:
- base URL still uses `localhost`
- backend is not running on port `8080`

Phone cannot connect:
- phone and computer are not on the same Wi-Fi
- base URL uses `127.0.0.1` instead of the PC LAN IP
- Windows Firewall blocks inbound `8080`

Login fails:
- backend was restarted and the old token became invalid
- base URL points to the wrong machine
- the app is still on an older debug build

Health passes but later requests fail:
- login was not run yet
- token is stale after a backend restart
- repository mode changed but the target screen was not reopened after the switch

Thumbnails still do not appear in REAL:
- backend returned only relative paths but Android `baseUrl` points to the wrong host
- backend returned video items without `thumbnailUrl`, `previewUrl`, or `coverUrl`, so Android can only show a video placeholder
- album cards currently resolve real covers through extra `post detail` requests, so a post-detail failure can leave that one card on a safe placeholder

Viewer image does not show the expected REAL photo:
- confirm the app is in `REAL` mode and the target screen was reopened after switching modes
- confirm the media DTO contains at least one preview URL candidate: `thumbnailUrl`, `mediaUrl`, or `originalUrl`
- confirm the original action has an original URL candidate: `originalUrl` or `mediaUrl`
- if preview works but original fails, the Viewer should keep the preview and move only that media item into the retry/failure state
- if dark letterbox areas show colored demo backgrounds, reinstall the latest debug build because Stage 12.1 second-round Viewer uses the shared immersive background

Viewer video does not play as expected:
- confirm the media DTO contains a playable candidate URL in `videoUrl`, `mediaUrl`, or `originalUrl`
- confirm poster display comes from `thumbnailUrl`, `previewUrl`, or `coverUrl`; without a poster URL the app should still stay safe and show a placeholder
- if playback fails on one media item, verify the error state does not leak to the next item after swiping
- if system-media controls appear to move with the zoomed video, reinstall the latest debug build because Stage 12.1 third-round Viewer separates the transformed video layer from the fixed control layer

Repeated smoke runs change media count:
- upload smoke adds media while the dev server stays up
- restart the backend for a fresh H2 state
- clean `local-storage` manually only when the backend is stopped

## 9. Stage 12.2 State Consistency Checks

1. In `REAL`, edit one post title in `Gear Edit`, return to the album page, and confirm the card title changes without switching mode.
2. Open the same post detail page again and confirm title / summary / album chips are updated.
3. From system media, add one image into an existing post and confirm photo feed, post detail media area, and `Gear Edit -> 媒体管理` all refresh to the new media set.
4. Delete one media from `Gear Edit -> 媒体管理` and confirm the removed item disappears from photo feed, post detail, Viewer, and media management after returning.
5. Delete one whole post and confirm album list, photo feed, and trash all reflect the change after the mutation completes.
6. Restore one post or one media from trash and confirm the restored content reappears in the relevant list without mode leakage.
7. Add, edit, and delete one media comment, then confirm the active comment thread, comment bubble, and visible count all refresh together.
8. In system media, move one item to the Android system trash and confirm both the system media list and system media Viewer stop showing the deleted item.
9. Turn `REAL` -> `FAKE` -> `REAL` and confirm previous REAL error / loading / selection state does not pollute FAKE pages, and vice versa.
10. If any refresh request fails, confirm the page stays alive and exposes Chinese retry copy instead of crashing.

## 10. Stage 12.4 Cleanup Checks

1. In `REAL`, confirm feed thumbnails, Viewer preview/original loading, and video poster fallback still work after the shared media helper cleanup.
2. Switch between image and video Viewer items and confirm loading/error/progress labels still stay isolated per media item after the shared Viewer-state cleanup.
3. Edit a post, delete media, restore from trash, and add a media comment; confirm the affected pages still refresh correctly after mutation helpers replace page-local scope construction.
4. Reopen `照片流` and `相册` and confirm REAL empty/error/retry cards still behave normally after shared backend state-card reuse.
## Stage 12.3 回归重点

1. 系统媒体选择多张媒体后执行“发成新帖子”，先看到“已加入上传队列”，全部上传并整理成功后：
   - 照片流刷新
   - 相册 / 帖子列表刷新
   - 自动进入新帖子详情
2. 系统媒体执行“加入已有帖子”，成功后帖子详情媒体区和 Gear Edit 媒体管理同步刷新。
3. 故意制造上传后整理帖子失败时，主照片流不能出现未挂帖媒体。
4. 在 REAL 模式删除帖子最后一张媒体时，应返回中文冲突提示，不允许留下空帖。
5. 系统删后媒体应从照片流、帖子详情、Viewer、媒体管理中消失，并进入 app 回收站；恢复后重新出现。


## 11. Stage 12.6 Add-Post / Add-Media Checks

1. ???? Quick Add ?? `????`?????????????????????????????????????
2. ? Quick Add ?? `????`?????????????????????????
3. ?????????????? `?????`?????????????????? REAL???????????????????????????
4. REAL ?????????????????????????????????????????????????????
5. ????? Viewer ???????? `?????`??????????????????
6. ??????? Viewer ??? `??????`?????????????????????? Gear Edit ????????
7. ??????????????????????????????? `??`?????????????
8. ????????????????????? `?????`????????????????????
