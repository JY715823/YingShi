# Current Task - Stage 10.3 Notification and Settings Polish

## Goal
Close the Stage 10 notification and settings shell by adding notification category filters, unread badges, click-through placeholders, and a stability pass on current in-memory settings state.

## Scope
- notification category filter row
- per-category unread badges
- bell unread badge in the photos module top bar
- single-item read and mark-all-read updates
- notification click-through placeholder detail
- notification-detail standalone page shell
- settings state stability check and minimal UI polish
- minimal PRD / UI doc sync

## Product intent
- Notification center should now feel like a usable local-first inbox instead of only a flat fake list.
- Read state should stay visually consistent across the bell entry, category filters, and list rows within the current app session.
- Notification click handling should already express future destination intent, even though real push, deep links, and backend routing are still deferred.
- Settings remains session-scoped and local-first in this stage; the main requirement is that current values stay stable and clearly explain placeholder behavior where needed.

## Do not do
- no real push notification
- no real notification permission
- no DataStore or restart persistence
- no backend / Room / Retrofit
- no complex deep-link router
- no large navigation refactor

## Done when
- notification filters work for all categories
- category unread badges update correctly
- the bell badge reflects current unread count
- clicking a notification marks it read
- mark-all-read clears list, filter, and bell unread indicators
- clicking a notification opens a jump placeholder or detail placeholder
- settings page remains stable within the current app session
- docs record fake notifications, placeholder jump behavior, and session-only settings state
- app builds and runs
