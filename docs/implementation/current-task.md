# Current Task - Stage 4.3 Post Detail Media Area and In-post Viewer Placeholder

Stage 4.2 is complete. The project is now in Stage 4.3.

## Goal
Refine the post detail media area and add an in-post viewer placeholder route while tightening the photo module top bar and doing lightweight smoothness improvements.

## Scope
This stage covers:
- further tightening the photo module top bar
- lightweight smoothness checks for density switching and secondary root-page swiping
- post detail media area refinement
- media info row refinement
- media comment entry placeholder
- in-post viewer placeholder route
- minimal related doc updates

Confirmed experience notes:
- The photo module top bar still sits too far from the system status bar and should be tightened again without violating safe area.
- Photo density switching, album density switching, and secondary root-page swiping currently have some stutter; Stage 4.3 only does lightweight checks and obvious fixes.
- Do not do broad performance rewrites in this stage.
- Later polish stages should continue improving smoothness, motion, and gesture details.

## Product intent
- The photo module top bar should sit close to the system status bar while respecting safe area.
- Density switching and secondary root-page swiping should feel acceptable now and remain polish targets later.
- Post detail is a context page, not a global photo feed and not the immersive photo viewer.
- Media comments and post comments must stay separated.
- The in-post viewer context is different from the photo-page global media viewer context.

## Do not do in this stage
- no real backend
- no Room / Retrofit / MediaStore
- no real export / save
- no real Gear Edit
- no full comment system
- no final in-post viewer implementation
- no large performance rewrite

## Done when
- Photo module top bar is closer to the system status bar
- Obvious unnecessary stutter in density switching / secondary root-page swiping is lightly improved or documented
- Post detail media area is clearer
- Media info row is established
- Media comment entry placeholder exists
- In-post viewer placeholder opens from post detail and returns to post detail
- App builds and runs
- Docs are minimally synchronized
