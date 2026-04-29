# Current Task - Stage 11.4 Post and Album API Shell

## Goal
Prepare post and album backend integration boundaries with clearer contracts, DTOs, mappers, and Fake/Real repository shells, while keeping the current fake-first app flow unchanged.

## Scope
- update `docs/contracts/post-api.md`
- create `docs/contracts/album-api.md`
- split post and album API responsibilities
- refine post and album DTOs
- add post detail / post summary / post media transport models
- add request DTOs for post create, edit, cover update, media-order update, and album assignment update
- add DTO -> domain mapper boundaries
- split `PostRepository` and `AlbumRepository`
- keep fake repositories as the default runnable path
- keep real repositories as API-call shells only

## Product intent
- posts and albums remain separate but related entities
- post detail keeps its own media ordering and cover semantics
- album APIs focus on album directory and post-to-album relationships
- UI must not depend on Retrofit DTOs
- existing fake pages must keep running without switching to real APIs

## Do not do
- no live backend dependency
- no forced real repository switch
- no full fake-data migration
- no large UI refactor
- no hardcoded production server address

## Done when
- post and album contract docs exist
- `PostApi` and `AlbumApi` shells exist
- post / album DTOs and request DTOs exist
- mappers exist
- fake / real repository boundaries are clear
- fake app flow remains runnable
- app builds successfully
