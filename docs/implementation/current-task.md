# Current Task - Stage 11.2 Auth Contract and Token Shell

## Goal
Prepare future auth integration by defining login/auth contracts, auth DTOs, token management placeholders, bearer-token injection, and fake/real auth repository boundaries without forcing the current app into a real login flow.

## Scope
- `docs/contracts/auth-api.md`
- login / refresh / logout / current-user contract draft
- auth DTO layer
- `AuthApi` Retrofit shell
- token store / token provider / session manager placeholder
- `AuthInterceptor` shell
- auth repository fake / real boundary
- settings login-status placeholder
- minimal PRD / UI doc sync

## Product intent
- Stage 11.2 is still contract-first and shell-first.
- Fake data remains the default runtime path.
- Token handling should become a clear boundary now so later backend auth can slot into the existing remote layer cleanly.
- No screen should be blocked behind login yet.

## Do not do
- no real backend
- no real login screen
- no DataStore or persistent token storage
- no OAuth / phone code / third-party login
- no full fake-data migration
- no hardcoded production URL

## Done when
- `auth-api.md` exists
- auth DTOs exist
- `AuthApi` exists
- token management placeholder exists
- `AuthInterceptor` exists
- auth repository fake / real boundary exists
- settings can show login-status placeholder without forcing login
- app still starts and uses fake data by default
- docs are updated
- app builds and runs
