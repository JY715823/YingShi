# Yingshi Android Repo Instructions

## First read

Before doing any work, read these files in order:

1. `docs/product/album-prd-v2.md`
2. `docs/design/ui-design-v2.md`
3. `docs/implementation/non-negotiables.md`
4. `docs/implementation/current-task.md`

## Project

This repo is the Android client for Yingshi, a two-person shared memory app centered on:

- photos
- posts
- viewer
- comments
- a separate system media tool area

## Current phase

We are in the early local-first UI shell stage.

Stage 0 priorities:

- keep the repository easy for phased AI collaboration
- keep the Android app runnable
- prefer placeholder UI and fake state
- establish docs and feature boundaries before complex implementation

Do not introduce real backend integration unless the current task explicitly asks for it.

## Non-negotiable product rules

- Photo page is a global media stream, not a post feed.
- System media is a separate tool area, not part of the main content stream.
- Post comments and media comments must stay separated.
- Viewer uses an immersive edge-overlay structure.
- Prefer fake data and placeholder repositories in early stages.

## Stage 0 collaboration rules

- Keep edits small, isolated, and easy to continue in later stages.
- Prefer feature-oriented packages over one giant UI file.
- Update docs when stage boundaries or acceptance criteria change.
- Preserve a working shell even when only adding placeholders.
- Do not add real data, sync, upload, notification, or media-store logic in Stage 0.

## Suggested code structure

- `app/.../app`
  app shell and root navigation
- `app/.../feature/home`
  home placeholders
- `app/.../feature/photos`
  photo module placeholders and top nav shell
- `app/.../feature/life`
  life placeholders
- `app/.../ui/theme`
  minimal design tokens and theme baseline

## Code rules

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel produces state when real feature state appears
- Composables render state and emit events
- Keep feature-oriented package structure
- Avoid unrelated refactors
- Add Preview where practical

## Explicitly out of scope in Stage 0

- real backend
- Room / Retrofit / MediaStore
- real comment flows
- real Viewer interaction logic
- real system media integration
- upload/download pipelines
- notifications implementation

## Completion checklist

Before finishing any task:

1. Build the project if possible
2. Fix compile errors caused by your changes
3. Summarize changed files
4. State what is done
5. State what is not done
6. State known risks / TODOs
