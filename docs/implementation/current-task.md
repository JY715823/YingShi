# Current Task - Stage 10.1 Notification Center and Settings Shell

## Goal
Add independent notification-center and settings-shell pages for the app.

## Scope
- Connect the bell button in the photos-module top bar to an independent notification-center page.
- Build the notification-center page as a standalone route with no global bottom navigation and no photos secondary navigation.
- Use fake notification data with at least these categories:
  - comments
  - content updates
  - delete / restore
  - system
- Show notification type, title, short body, time, and read / unread state.
- Support marking one notification as read.
- Support marking all notifications as read.
- Add a reachable settings entry if none exists yet.
- Build the settings page as a standalone route with no global bottom navigation and no photos secondary navigation.
- Organize settings into grouped placeholder sections.
- Connect the settings cache / storage section to the existing Stage 9.4 global cache-management placeholder.
- Sync the minimum required PRD / UI / current-task docs together with code.

## Product intent
- Notification center is a focused low-noise list page, not a feed mixed into photos or posts.
- Settings is a clear tool page, not a backend-style control panel.
- Both pages are independent shells that prepare room for later real permissions, browsing preferences, cache policies, and notification categories.
- Stage 10.1 stays local-first and fake-safe: it defines routes, grouping, and page boundaries before real push or account systems exist.

## Do not do
- no real push notification
- no account system
- no real permission request
- no real diagnostics / crash reporting
- no backend / Room / Retrofit
- no large main-navigation refactor
- no regression to photos / albums / viewer / system-media flows

## Acceptance
- The bell button opens notification center.
- The notification list is visible.
- Notifications can be marked read.
- Mark-all-read works.
- Settings page is reachable.
- Settings groups are clear.
- The cache cleanup entry opens the existing Stage 9.4 global cache-management placeholder.
- Related docs are minimally updated in the same change.
- The project builds and remains runnable.

## Follow-up after Stage 10.1
- Real push / notification channel strategy
- Real notification filtering, grouping, and jump targets
- Real account and shared-space settings
- Real browsing preferences persistence
- Real permission status / request wiring
- Real diagnostics and export entries
