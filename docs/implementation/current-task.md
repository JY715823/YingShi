# Current Task - Stage 8.1 System Media Shell

## Goal
Build the first shell of the system media tool area.

## Scope
- fix trash detail / pending cleanup back navigation and keep trash tab state
- system media route
- permission guide shell
- authorized / unauthorized / partial placeholder states
- fake system media grid
- filters
- posted marker
- multi-select shell
- system media viewer placeholder
- minimal doc updates

## Product intent
- System media is a separate tool area.
- It is not part of the app content feed.
- It does not show comments, original-load state, or related-post navigation.
- It should feel more tool-like and closer to a system gallery.

## Do not do
- no real MediaStore query
- no real system delete
- no real post creation
- no real permission flow completion
- no backend / Room / Retrofit

## Done when
- System button opens independent system media page
- Permission shell exists
- Fake media grid and filters work
- Multi-select shell exists
- Viewer placeholder exists
- Trash back navigation returns to trash page
- App builds and runs
