# Current Task: Android Real-Mode Polish And Change-Safe Cleanup

Updated: 2026-05-25

## Background

The Android app already consumes the main backend flows for auth, photos, uploads, comments, trash, and notifications. The remaining work is now about polishing the real-mode experience, exposing a few backend-ready surfaces in UI, and avoiding wasteful backend work in areas that are about to be redesigned.

## Goals

1. Keep the current `REAL` baseline stable for auth, photos, notifications, uploads, and trash.
2. Keep avatar upload/display and auth refresh behavior stable after landing them.
4. Keep the life page intentionally focused on ledger and chat viewer only.
5. Avoid premature ledger backend work before the new UI and data model are settled.

## Scope

- Android repository and networking layer polish
- notification-center verification and docs sync
- `Me` / profile UI verification for avatar support
- docs that describe current frontend-backend alignment
- ledger sequencing guidance so later UI redesign does not force unnecessary rework

## Non Goals

- no reintroduction of the anniversary feature
- no direct Android-to-MinIO or Android-to-OSS flow
- no full ledger backend schema or API before the redesigned UI and data fields are agreed

## Acceptance

1. Android documentation clearly reflects that notifications are already real in `REAL` mode.
2. The repo does not claim the removed anniversary entry still exists.
3. Remaining Android/backend gaps are narrowed mainly to ledger design sequencing rather than vague backend uncertainty.
