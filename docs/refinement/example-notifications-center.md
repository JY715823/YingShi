# Example: Notifications Center

> Format example only. This file demonstrates how to structure a module brief. It is not the authoritative current state of the app.

- Module key: `notifications-center`
- Status: `planning`
- Last updated: `2026-06-08`
- Primary surfaces: `android | server | shared`
- Linked server brief: `none`

## Module Goal
- User value: let the user see, understand, and act on collaboration updates without hunting across multiple screens.
- Business or product intent: keep notifications trustworthy enough that unread count, list ordering, and deep links feel consistent.
- Success criteria: unread badge, list content, and destination screen stay aligned after refresh, push entry, and manual revisit.

## Current State
- What exists today: notification contracts and push-related docs already exist on both client and server sides.
- Known constraints: the module likely intersects auth, deep link routing, and any badge surfaces that summarize unread state.
- Relevant code or docs:
  - `/mnt/e/Study/App/YingShi/docs/contracts/notification-api.md`
  - `/mnt/e/Study/App/YingShi-Server/docs/contracts/notification-api.md`
  - `/mnt/e/Study/App/YingShi-Server/docs/contracts/push-fcm.md`

## Your Current Ideas
- Idea 1: treat the notifications center as the single readable history, with push acting as an entry point instead of a separate truth source.
- Idea 2: make unread state explicit and reversible so QA can verify it across refresh, backgrounding, and deep links.
- Open preference: whether badge state should update optimistically on the client or only after server confirmation.

## Codex Recommendations
### Recommended for this round
- Recommendation: add explicit destination-failure handling when a notification points to deleted or permission-gated content.
  - Why it is worth considering: this failure mode is easy to miss in planning but very visible in real use.
  - Impact on usability, robustness, or smoothness: improves resilience and reduces confusing dead-end taps.
- Recommendation: define one clear unread-sync rule across push entry, manual refresh, and back-navigation.
  - Why it is worth considering: unread drift is one of the fastest trust-breakers in a notification surface.
  - Impact on usability, robustness, or smoothness: improves consistency and lowers regression risk across related modules.

### Good follow-up ideas
- Refinement: consider grouped copy or lightweight filters if notification volume grows.
  - Why it may help: keeps the center readable without immediately adding heavyweight IA changes.

## Key Questions
- [ ] What counts as read, opened, or consumed for each notification type?
- [ ] Which notification types deep link into existing modules versus staying inside the center?
- [ ] What should happen when the destination content is gone, deleted, or permission-gated?

## Scope Boundaries
### In scope
- notification list behavior
- unread count behavior
- notification item states and deep links

### Out of scope
- redesigning unrelated destination screens
- broad push infrastructure rewrites unless they block the module

### Non-negotiables
- unread indicators must not contradict the list
- destination failures need user-facing handling

### Failure and fallback expectations
- Failure states to support: expired auth, missing target content, empty list, slow network, duplicate delivery.
- Rollback or fallback behavior: if deep link resolution fails, keep the user in notifications center with an actionable message.

## Related Modules
- Module: `auth-session`
  - Relationship: expired auth can break list fetches and deep-link resolution.
  - Recheck before ship: relogin path and session-expiry messaging.
- Module: `viewer`
  - Relationship: some notifications may land in media or immersive views.
  - Recheck before ship: return path and unread-state updates after open.
- Module: `post-detail`
  - Relationship: comment or reaction notifications may target post detail.
  - Recheck before ship: destination renders the expected highlighted context.

## Frontend and Backend Contracts
### Client state and entry points
- Screens, routes, ViewModels, repositories: notifications tab or screen, unread badge source, push-entry routing, and any destination handoff state.

### Server endpoints and payloads
- Controllers, services, DTOs, contracts: notification list/read APIs, unread count shape, push payload shape, and destination identifiers.

### Shared rules
- Auth, permissions, identity, ordering, time, copy: partner identity labels, relative or absolute time copy, read-state ordering, deleted-target fallback copy.

## UI and Visual Details
- Layout or information hierarchy: newest-first list, clear unread affordance, compact metadata, and explicit empty state.
- Components and states: badge, grouped list item shell, read/unread state, loading skeleton, empty state, error state.
- Motion or transitions: no decorative motion, only clear press and state transitions.
- Copy notes: unread labels and fallback copy should be direct and unambiguous.

## Interaction Feedback
- Loading: skeleton or lightweight placeholder for first load and refresh.
- Empty: say there is nothing new, not that data failed.
- Error: preserve the current list when possible and offer retry.
- Success: unread count and row state should update together.
- Permission denial: explain why the destination cannot be opened.
- Offline or retry: surface retry without pretending a read-state sync succeeded.

## Hidden Impact Checklist
- Notifications: direct impact, this is the primary module.
- Auth: direct impact through session expiry and identity-sensitive copy.
- Upload: probably no direct impact, but upload-complete notifications may reference it.
- Comments: likely direct impact through comment-related notification items.
- Viewer: possible deep link target.
- Settings: notification preferences or badge toggles may exist later.
- Analytics or logging: important for unread-sync and deep-link failure diagnosis.
- Cache or offline: list staleness and badge drift need a clear rule.
- Permissions: destination access and push permission messaging.
- Copy and empty states: high visibility, must be explicit.

## Plan Self-check
- Recommendation quality: top recommendations focus on trust-breaking failure points instead of broad brainstorming.
- Scope pressure test: unread-sync rule and destination-failure handling are suitable for the first refinement round; grouping or filters stay as follow-up.
- Contract and dependency pressure test: unread count, read-state mutation, push payload shape, auth expiry, and deep-link destinations all need explicit rechecks.
- UX state pressure test: loading, empty, error, success, permission, offline, and return-path consistency all matter here.
- Risks to watch in implement: unread drift and deep-link fallback can regress adjacent modules quickly.

## Implementation Notes
### Client
- Example: list route, unread badge source, and deep-link resolution are kept together for easier regression checks.

### Server
- Example: unread count contract and read-state mutation contract are documented before UI polish.

### Design
- Example: unread vs read hierarchy is visible without depending on a single color cue.

### Interaction Feedback
- Example: destination failure returns the user to the center with an explanation instead of silent no-op behavior.

## Post-implement Self-check
- Validation run: example placeholder, targeted build or sanity checks would be listed here before device QA starts.
- New behavior sanity: example placeholder, this round's unread-sync and destination-fallback behavior would be checked here.
- Contract sanity: example placeholder, unread count shape and read-state mutation contract would be rechecked here.
- Known gaps: example placeholder, anything unverified before device QA would be called out here.

## New Coupling Recheck
- Module: `auth-session`
  - What was rechecked: expiry handling around notification fetch and deep-link entry.
  - Result: example placeholder.
- Module: `viewer`
  - What was rechecked: destination handoff and return path.
  - Result: example placeholder.

## Real-device Issue Log
- None yet. Add entries using `/mnt/e/Study/App/.claude/skills/module-refinement/references/device-qa-template.md`.

## Validation Snapshot
### Verified
- None yet.

### Pending
- unread badge after background resume
- push tap to destination and back-navigation behavior
- session expiry while opening a notification destination

### Blocked
- none

## Closeout Summary
- What shipped: not filled in this example.
- What remains risky: not filled in this example.
- What was intentionally deferred: not filled in this example.

## Carry-forward Notes
- Any future module that can generate a notification should recheck unread count and deep-link consistency.
- If destination-copy rules change elsewhere, recheck notification fallback messages too.

## Closeout Self-check
- Brief completeness: example placeholder.
- Remaining risk clarity: example placeholder.
- Carry-forward quality: example placeholder.
