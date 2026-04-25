# Current Task - Stage 8.2 System Media Query

## Goal
Connect the system media tool area to local device media query.

## Scope
- simplify permission flow as granted
- system media repository / data source
- query local images and videos
- display real local media grid
- show minimal empty / error fallback instead of permission guide shell
- keep filters
- keep posted marker placeholder
- open system media viewer with real uri
- keep multi-select shell
- minimal doc updates

## Product intent
- System media is a tool area.
- It is separate from app content media feed.
- Permission guide is skipped for now; treat media access as granted.
- System media viewer does not show comments, original-load state, or related posts.

## Do not do
- no permission guide
- no real system delete
- no real post creation
- no upload
- no backend / Room / Retrofit

## Done when
- System media page shows local media or empty state
- Filters work
- Media opens in system media viewer
- Multi-select still works
- App builds and runs
