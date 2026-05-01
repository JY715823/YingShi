# Current Task - Stage 11B-5 Follow-up Interaction Restructure

## Goal
Correct the navigation structure, entry placement, viewer actions, cache entry location, and system-media interaction model before entering the next stage.

## Scope
- bottom navigation becomes `首页 / 照片 / 生活 / 我的`
- add a lightweight `+` placeholder entry under the photos-module secondary navigation
- add a standalone `我的` page with `设置` and `缓存管理` entries
- remove settings / cache shortcuts from notification center, photo-flow viewer, and post detail
- add media-comment entry from the viewer preview area even when comment count is zero
- change the photo-flow viewer top-right action to delete-with-confirm
- improve viewer zoom / pan expectations for image, long image, and video
- keep video controls inside the media canvas
- restructure system-media viewer into a cleaner viewer + bottom action menu model
- upgrade `移到系统回收站` from local simulation to Android system trash request
- minimally sync PRD / UI / implementation docs

## Product intent
- FAKE remains the default safe path.
- REAL mode integration from earlier tasks must keep working.
- system media stays a separate tool area and must not leak into app trash semantics
- cache management becomes a calmer tool entry under `我的`, not a scattered action hidden across content viewers
- this pass fixes information architecture and interaction correctness, not final visual polish

## Do not do
- no removal of fake repositories
- no forced REAL default
- no real OSS
- no real notification implementation
- no broad visual redesign
- no large unrelated refactor
- no backend expansion unless docs / contracts truly need a tiny sync

## Done when
- bottom navigation is `首页 / 照片 / 生活 / 我的`
- `我的` page exposes settings and cache management
- the bell only opens notification center
- the photos module has a `+` placeholder entry
- viewer comment preview has an add-comment action
- photo-flow viewer deletes from a trash icon with confirmation
- viewer and post detail no longer expose cache-management shortcuts
- system-media viewer uses a top-right menu and can request real Android system trash
- Android builds
