# Current Task - Stage 5.4 Comment Selection and Copy Polish

## Goal
Polish comment item actions so long-press shows a lightweight inline menu and text selection behaves closer to a real app.

## Scope
This stage covers:
- lightweight inline comment action menu
- anchored popup-style menu that does not affect comment list layout
- menu options:
  - 复制
  - 选择
  - 编辑
  - 删除
- copy full comment text to system clipboard
- enter text selection mode from 选择
- default full-text selection where possible
- selected-text copy
- mutually exclusive action-menu state and text-selection state
- local edit/delete preservation
- minimal related doc updates

## Product intent
- Long-press should not open a large full-screen or heavy menu.
- The action menu should appear near the comment item.
- Copy should directly copy the whole comment.
- Select should enter text selection mode, not normal multi-select mode.
- After selecting text, only the copy action should appear.
- Post comments and media comments must remain separate.

## Applies to
- post comments in post detail
- media comments in photo-flow viewer detail panel
- media comments in in-post viewer detail panel

## Do not do in this stage
- no real backend
- no Room / Retrofit
- no replies
- no nested comments
- no quoted replies
- no notification system
- no heavy rich text editor
- no large unrelated refactor

## Done when
- Long-press comment shows a small inline menu
- Menu only has 复制 / 选择 / 编辑 / 删除
- Copy copies full text
- Select enters text selection mode
- Selected-text copy works at a basic level
- Edit/delete still work locally
- Post and media comments do not mix
- App builds and runs
- Docs are minimally synchronized
