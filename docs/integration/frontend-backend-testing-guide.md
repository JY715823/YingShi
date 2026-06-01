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

- `demo.a@yingshi.local / demo123456`
- `demo.b@yingshi.local / demo123456`

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
- apply emulator or loopback preset
- save and relogin with the seeded demo account
- clear local auth cache
- switch `FAKE / REAL`
- run a minimal health check
- show the latest result

## Current `REAL` Verification Targets

After login and health check, switch to `REAL` and reopen the target pages:

- `Me`: current user, shared-library, partner information, avatar display, and avatar upload from edit-profile
- `Photos`: real feed data and viewer loading
- `Albums`: album list and post cards
- `Post detail`: media and comments
- `Notifications`: bell badge, notification list, notification detail, and read-state changes
- `Trash`: list, detail, restore/remove/purge/undo-remove
- system media and upload flows

## Known Intentional Gaps

- ledger remains local Room data for now because the user plans a major redesign and data-model changes
- life page contains ledger and chat viewer only; anniversary is intentionally gone

## Common Problems

`REAL` pages still ask you to log in:

- the page was opened before `REAL` login completed
- `Base URL` changed and the old session was cleared
- the backend restarted and the old token is no longer valid

Emulator cannot connect:

- `localhost` was used instead of `10.0.2.2`
- the backend is not running on `8080`

Phone cannot connect:

- `127.0.0.1` was used instead of the PC LAN IP
- phone and PC are not on the same Wi-Fi
- Windows Firewall is blocking the port
