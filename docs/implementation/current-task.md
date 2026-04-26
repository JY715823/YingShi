# Current Task - Stage 8.4 System Media Performance and State Retention

## Goal
Improve basic performance and state retention for the system media tool area.

## Scope
- cache system media query result
- avoid repeated query on re-entry
- preserve filter state
- preserve grid scroll position
- keep viewer return on the same system-media session without dropping back to photos
- add a lightweight manual refresh entry
- preserve posted marker and local hidden state
- improve LazyVerticalGrid keys/contentType
- reduce unnecessary recomposition / recalculation
- optimize thumbnail loading basics
- minimal doc updates

## Product intent
- System media should feel closer to a tool-like gallery.
- Re-entering the page should not reload everything unnecessarily.
- Scrolling should be acceptable before deeper performance polish.

## Do not do
- no real system delete
- no real upload
- no backend / Room / Retrofit
- no large Paging rewrite unless very controlled
- no unrelated feature work

## Done when
- Re-entering system media page uses cached data when possible
- Scroll position and filter are preserved
- Grid scrolling is smoother than before
- Existing system media actions still work
- App builds and runs
