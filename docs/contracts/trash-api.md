# Trash API Draft

## Purpose
Represent app-content trash items, restore actions, and permanent delete placeholders.

## Endpoints

### `GET /v1/trash/items`
- use case: trash list
- query draft:
  - `type` optional
  - `page`
  - `pageSize`
  - `cursor`

Response item draft:

```json
{
  "trashItemId": "trash_001",
  "itemType": "postDeleted",
  "sourcePostId": "post_001",
  "sourceMediaId": null,
  "title": "Night Walk",
  "previewInfo": "Deleted at 2026-04-29 10:00",
  "deletedAtMillis": 1777412800000,
  "relatedPostIds": ["post_001"],
  "relatedMediaIds": ["media_001", "media_002"]
}
```

### `POST /v1/trash/items/{trashItemId}/restore`
- use case: restore a trash item

### `DELETE /v1/trash/items/{trashItemId}`
- use case: permanent delete placeholder

## Field Notes
- `itemType` draft values:
  - `postDeleted`
  - `mediaRemoved`
  - `mediaSystemDeleted`

## Error Code Placeholders
- `TRASH_ITEM_NOT_FOUND`
- `RESTORE_CONFLICT`
- `DELETE_NOT_ALLOWED`
- `NOT_IMPLEMENTED`

## Pagination Placeholder
- latest delete time descending

## Stage 11.1 Draft-Only Notes
- 24h undo / pending cleanup semantics are still draft-only
- final restore conflict strategy with shared media remains to be defined with backend
