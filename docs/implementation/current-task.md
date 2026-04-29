# Current Task - Stage 11.1 API Contract and Retrofit Shell

## Goal
Prepare future backend integration by defining API contracts, DTO boundaries, Retrofit service shells, result wrappers, and fake/real repository switching structure without changing the current fake-first app behavior.

## Scope
- contract docs under `docs/contracts`
- media / post / comment / trash / upload API drafts
- remote DTO layer
- Retrofit API interfaces
- placeholder remote config and service factory
- DTO -> domain mapper boundary
- fake / real repository interfaces and default fake selection
- simple `ApiResult` / `NetworkResult` style wrapper
- minimal PRD / UI doc sync

## Product intent
- Stage 11.1 is a boundary-building pass, not a live backend pass.
- UI should keep running on the current fake repositories by default.
- DTOs must stay outside UI-facing models so later backend iteration does not leak transport details into screens.
- Upload only needs contract and token shape now; real transfer, OSS wiring, and auth remain follow-up work.

## Do not do
- no real Spring Boot backend
- no real login or token flow
- no real upload implementation
- no full fake-data migration
- no large UI refactor
- no hardcoded production server URL

## Done when
- `docs/contracts` contains the API draft docs
- remote DTOs exist
- Retrofit service shells exist
- mapper boundaries exist
- fake / real repository interfaces exist with fake as the default path
- app still starts on the fake path
- docs are minimally updated
- app builds and runs
