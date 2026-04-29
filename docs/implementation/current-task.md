# Current Task - Stage 11.6 Trash / Delete-Restore API Shell

## Goal
Prepare API, DTO, mapper, and repository boundaries for future real delete, trash, restore, and 24h undo cleanup flows while keeping the current fake trash experience runnable by default.

## Scope
- update trash contract with list, detail, restore, remove-from-trash, undo-remove, and pending-cleanup drafts
- align post/media delete contract drafts with trash entry creation semantics
- add trash detail and pending-cleanup DTOs plus delete request DTOs
- expand TrashApi Retrofit shell
- expand trash DTO -> domain mapper boundary
- expand TrashRepository fake/real interface while keeping fake as the default path

## Do not do
- no real backend integration
- no real Android system delete
- no real 24h cleanup worker or background job
- no forced switch to real repositories
- no large trash UI refactor
- no hardcoded production server address

## Done when
- `docs/contracts/trash-api.md` is complete for Stage 11.6
- `docs/contracts/media-api.md` and `docs/contracts/post-api.md` are minimally aligned
- trash DTOs, mappers, and Retrofit shell methods exist
- fake trash list / restore / remove / undo behavior still works as the default flow
- app still builds and runs
