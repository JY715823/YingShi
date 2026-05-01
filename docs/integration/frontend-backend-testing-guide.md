# Frontend Backend Testing Guide

## Scope
- Android repo: `YingShi`
- paired backend repo: `yingshi-server`
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
25. Switch mode back to `FAKE` when finished.

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

Repeated smoke runs change media count:
- upload smoke adds media while the dev server stays up
- restart the backend for a fresh H2 state
- clean `local-storage` manually only when the backend is stopped
