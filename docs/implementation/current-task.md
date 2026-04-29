# Current Task - Stage 11.5 Upload API Shell

## Goal
Prepare upload-token, upload-task, upload-state, and fake import-flow boundaries so system media can enter app content through upload placeholders rather than direct local insertion.

## Scope
- update `docs/contracts/upload-api.md`
- minimally sync `docs/contracts/media-api.md`
- refine upload DTOs
- extend `UploadApi` shell
- refine `UploadRepository` fake / real boundary
- add upload state models
- add fake upload-task progress simulation
- connect system-media "create new post" and "add to existing post" flows to upload placeholder tasks
- only insert media into app content after upload success

## Product intent
- upload remains a shell in this stage
- system media stays separate from app-content media until upload success
- upload progress should be visible but lightweight
- fake flow remains the default runnable path

## Do not do
- no real OSS upload
- no real file transfer
- no live backend dependency
- no WorkManager
- no full retry or resume system
- no hardcoded production server

## Done when
- upload contracts are updated
- upload DTOs and API shell exist
- upload repository fake / real boundary is clear
- system-media import uses upload placeholder tasks
- fake flow still runs without real backend
- app builds successfully
