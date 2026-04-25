# Current Task - Stage 8.3 System Media Actions

## Goal
Upgrade system media actions from placeholders to local fake flows and add delete confirmation for photo feed media deletion.

## Scope
- photo feed delete confirmation
- system media multi-select action bar
- create fake post from selected system media
- add selected system media to existing post
- add-to-post picker uses album -> post two-step selection
- local simulated move to system trash
- single media viewer actions
- posted marker update
- filters update
- keep system-media performance follow-up noted without deep optimization
- minimal doc updates

## Product intent
- Photo feed media deletion is global app-content deletion and must ask confirmation.
- System media is a tool area.
- System media does not enter app content unless sent as a post or added to a post.
- System media trash is not the app trash.
- This stage is local fake flow only.

## Do not do
- no real upload
- no backend
- no real Android system delete
- no MediaStore trash request
- no Room / Retrofit
- no system media comments
- no deep performance optimization

## Known follow-up
- System media scrolling performance needs optimization.
- Re-entering system media should preserve/cache query results.
- These are planned for a later performance/state-retention pass.

## Done when
- Photo feed delete asks confirmation
- Multi-select system media actions work locally
- Create-post flow creates local fake post and app media
- Add-to-post flow updates target post and app media
- Simulated system trash hides media from system tool area
- Posted marker and filters update
- App builds and runs
