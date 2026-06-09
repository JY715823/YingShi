# Frontend Backend Testing Guide

Updated: 2026-05-25

## Scope

- Android repo: `E:\Study\App\YingShi`
- paired backend repo: `E:\Study\App\YingShi-Server`

## Recommended Backend Shape

Preferred daily integration mode:

- backend profile: `docker-local`
- database: PostgreSQL in Docker
- storage: MinIO in Docker
- backend process: IDEA on Windows

Quick bootstrap mode still exists:

- backend profile: default `dev`
- database: H2
- storage: `local-storage`

## Seed Accounts

- `1085060329@qq.com / 123456`
- `2926315047@qq.com / 123456`
- 登录需要读取 QQ 邮箱验证码；若 smoke 脚本未传 `-LoginCode`，会在控制台提示手动输入

## Backend Start

For recommended cloudlike mode, from the backend repo:

```powershell
Copy-Item .env.example .env
docker compose up -d postgres minio minio-init
```

Then run the backend from IDEA with profile:

```text
docker-local
```

Health URL:

```text
http://localhost:8080/api/health
```

## Backend Smoke

Before Android verification, it is safer to run backend smoke first:

```powershell
.\scripts\integration-smoke.ps1
.\scripts\stage16-cloudlike-smoke.ps1 -BaseUrl http://127.0.0.1:8080
```

## Android Base URL Rules

Emulator:

```text
http://10.0.2.2:8080/
```

Physical phone on the same Wi-Fi:

```text
http://<your-pc-ip>:8080/
```

Do not use `127.0.0.1` on a real phone.

## Diagnostics Entry

Inside the app:

1. Open `My`
2. Open `Settings`
3. Open `Backend Debug Diagnostics`

Current diagnostics responsibilities:

- edit `Base URL`
- 保存地址并清除旧会话
- 检查当前会话是否仍有效
- clear local auth cache
- run a minimal health check
- show the latest result

## Current Verification Targets

After login and health check, reopen the target pages:

- `Me`: current user, shared-library, partner information, avatar display, and avatar upload from edit-profile
- `Photos`: real feed data, viewer loading, and media comments
- `Albums`: large-album list, create-large-album dialog, and small-album cards under the selected large album
- `Small album detail`: top info area, photo-feed style media grid, comment panel, and media viewer progress bar
- `Notifications`: bell badge, notification list, notification detail, target jump, and read-state changes
- `Trash`: list, detail, restore/remove/purge/undo-remove
- system media import, upload queue, create-small-album, and add-to-small-album flows
- `Life / Ledger`: local Room cache hydration plus shared-library snapshot push/pull through `/api/ledger/snapshot`
- `Life / Chat viewer`: imported-chat list hydration plus shared-library snapshot push/pull through `/api/chat/imported/snapshot`

## Suggested Manual Pass

Run this short pass after backend smoke:

1. Log in with `1085060329@qq.com`, complete the QQ email verification, and confirm `Me` shows the shared library and partner info.
2. Open `Photos -> Albums`, create a large album, then create a small album under it and reopen the detail page.
3. Enter that small album, open the top-right comment icon, send a comment, then open one media item and confirm the in-album viewer still shows the bottom progress bar.
4. From `Photos`, use system media or existing App media to create another small album and verify the created result can reopen.
5. Open `Notifications`, mark one item as read, enter its detail, and verify the target jump still opens the corresponding page.
6. Open `Life -> Ledger`, make a small change, kill and reopen the app, then verify the change hydrates back in `REAL`.
7. Open `Life -> Chat viewer`, import one chat package, relaunch, and verify the imported sessions hydrate back in `REAL`.

## Known Intentional Gaps

- life widget display state is still local UI/cache state layered on top of backend-backed life data
- life page contains ledger and chat viewer only; anniversary is intentionally gone

## Common Problems

页面仍然要求重新登录:

- the page was opened before email verification completed
- `Base URL` changed and the old session was cleared
- the backend restarted and the old token is no longer valid
- the login code expired or was entered too many times

Emulator cannot connect:

- `localhost` was used instead of `10.0.2.2`
- the backend is not running on `8080`

Phone cannot connect:

- `127.0.0.1` was used instead of the PC LAN IP
- phone and PC are not on the same Wi-Fi
- Windows Firewall is blocking the port
