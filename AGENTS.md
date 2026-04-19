# Yingshi Android Repo Instructions

## First read
Before doing any work, read these files in order:
1. docs/product/album-prd-v2.md
2. docs/design/ui-design-v2.md
3. docs/implementation/non-negotiables.md
4. docs/implementation/current-task.md

## Project
This repo is the Android client for Yingshi, a two-person shared memory app centered on:
- photos
- posts
- viewer
- comments
- a separate system media tool area

## Current phase
We are in the early local-first UI shell stage.
Do not introduce real backend integration unless the current task explicitly asks for it.

## Core product rules
- Photo page is a global media stream, not a post feed.
- System media is a separate tool area, not part of the main content stream.
- Post comments and media comments must stay separated.
- Viewer uses an immersive edge-overlay structure.
- Prefer fake data and placeholder repositories in early stages.

## Code rules
- Kotlin
- Jetpack Compose
- Material 3
- ViewModel produces state
- Composables render state and emit events
- Keep feature-oriented package structure
- Avoid unrelated refactors
- Add Preview where practical

## Completion checklist
Before finishing any task:
1. Build the project if possible
2. Fix compile errors caused by your changes
3. Summarize changed files
4. State what is done
5. State what is not done
6. State known risks / TODOs