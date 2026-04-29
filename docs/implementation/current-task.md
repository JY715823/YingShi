# Current Task - Stage 10.2 Settings Preferences

## Goal
Move the settings page from shell-only into a basic usable tool page by connecting browsing preferences, viewer behavior preferences, cache cleanup, permission status, and about/diagnostics placeholders.

## Scope
- in-memory settings state model for the current app session
- default photo-page grid density preference
- default album-page column preference
- viewer preference group with minimal behavior wiring
- settings-page cache and storage actions
- permission status presentation
- about and diagnostics placeholder presentation
- minimal PRD / UI doc sync

## Product intent
- Settings should now help shape the app-content browsing experience instead of only listing future placeholders.
- Browsing defaults should influence first entry into photo and album pages, but should not keep fighting temporary per-page user adjustments.
- Viewer preferences can land in two layers for this stage: some can affect current viewer behavior now, and some can remain clearly labeled placeholders for later real player work.
- Cache cleanup stays local-first and fake, but settings should become the formal home for that management entry.

## Do not do
- no DataStore or restart persistence yet
- no real account system
- no real notification permission flow
- no real background task or download permission flow
- no real crash reporting or analytics service
- no backend / Room / Retrofit
- no large viewer or navigation refactor

## Done when
- settings groups are clear and usable
- default photo density can be changed to 2 / 3 / 4 / 8 / 16
- default album columns can be changed to 2 / 3 / 4
- viewer preference group exists with clear real-vs-placeholder behavior
- settings page shows fake cache summary and cleanup actions
- permission status shows system media access as fully granted
- about and diagnostics placeholders exist with clearer structure
- settings state survives within the current app session
- related docs are minimally updated
- app builds and runs
