# Current Task - Stage 11.3 Comment API Shell

## Goal
Build the first real-comment integration shell for post comments and media comments, while keeping the current fake comment flow as the default runnable path.

## Scope
- update `docs/contracts/comment-api.md`
- lock comment pagination draft to one placeholder style
- keep post comments and media comments as separate streams
- add comment request / response DTOs for list and mutation
- refine `CommentApi` Retrofit shell
- add DTO -> domain and domain -> UI comment mapping boundaries
- extend `CommentRepository` with list / create / update / delete methods
- keep `FakeCommentRepository` as the default local behavior source
- add `RealCommentRepository` placeholder methods for future backend wiring
- reserve loading / error / empty state structures without forcing a large UI rewrite
- keep current post comment and media comment interactions working

## Product intent
- Post comments and media comments do not mix.
- Latest comments appear first.
- The same `mediaId` still shares one media-comment state across photo-flow Viewer and in-post Viewer.
- UI must not depend on transport DTOs.
- Real API wiring should be able to replace fake data incrementally later.

## Do not do
- no live backend dependency
- no forced real repository switch
- no Room
- no real paging implementation
- no replies, thread nesting, or quote replies
- no large comment UI restructure

## Done when
- `comment-api.md` clearly defines list / create / update / delete drafts
- comment DTOs, request DTOs, and mapper boundaries exist
- `CommentApi` has explicit post-comment and media-comment endpoints
- Fake / Real `CommentRepository` boundaries are clear
- existing fake post comment and media comment add / edit / delete still work
- the app still builds and runs
