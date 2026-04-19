# Yingshi Android Repo Instructions

## First read
Before any task, read:
- docs/product/album-prd-v2.md
- docs/design/ui-design-v2.md
- docs/implementation/non-negotiables.md
- docs/implementation/current-task.md

## Project goal
This repo is the Android client for Yingshi, a two-person long-term shared memory app centered on photos, posts, viewer, comments, and a separate system media tool area.

## Core rules
- Photo page is a global media stream, not a post feed.
- System media is a separate tool area, not part of the main content stream.
- Post comments and media comments must stay separated.
- Prefer fake repositories and local UI state in early stages.
- Do not introduce backend integration unless the current task explicitly requires it.

## Code rules
- Kotlin + Jetpack Compose + Material 3
- ViewModel produces state; Composables render state and emit events
- Keep feature-based package structure
- Add Preview where practical
- Avoid unrelated refactors

## Completion rules
Before finishing a task:
1. build the project
2. fix compile errors caused by your changes
3. summarize changed files
4. list known risks or TODOs