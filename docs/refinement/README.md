# Module Refinement Workflow

This folder holds the durable briefs for one-module-at-a-time refinement.

## How to use it

Use the global Codex entry skill:

- `/module-refinement plan <module-key>`
- `/module-refinement implement <module-key>`
- `/module-refinement verify <module-key>`
- `/module-refinement close <module-key>`

The stage meanings are:

- `plan`: discovery, strong dependency and related-module analysis, Codex-led recommendations, deployment-minded completeness, a strong plan self-check, and a decision-complete plan
- `implement`: code changes plus a slimmer self-check for newly added behavior, any newly introduced coupling, and a concrete test plan before you test on device
- `verify`: structured intake for your real-device findings after the implement self-check is done
- `close`: final compression plus a lightweight closeout self-check

The slash-visible wrapper lives at `/mnt/c/Users/10850/.codex/skills/module-refinement`.

The real workflow package for this repository lives at `/mnt/e/Study/App/.claude/skills/module-refinement`.

## Brief location

Each module gets one main brief:

- `/mnt/e/Study/App/YingShi/docs/refinement/<module-key>.md`

That file is the source of truth from planning through closeout compression.

## Working rules

- One module at a time.
- Default to Android + Server linkage unless the brief proves the module is client-only.
- Before re-asking for background, consult the module brief and current code/docs first.
- `plan` should be Codex-led. Even if you give only a little input, it should still surface worthwhile recommendations that can make the module more useful, robust, and smooth.
- In `plan`, most of the substance should come from Codex's own recommendations and risk analysis, not from repeatedly unpacking the user's initial suggestions.
- The heaviest self-check and related-module analysis belongs in `plan`, not in `implement`.
- This workflow is for pre-deployment sweep and polish, so prefer finishing a module cleanly before moving to the next one.
- `implement` should mainly confirm the newly added behavior and any newly introduced coupling, then hand back a concrete test plan.
- After each round, write the latest decisions back into the brief so the next turn can resume cleanly.
- When a module closes, keep only carry-forward facts that later modules may need.

## Templates and examples

- Template source: `/mnt/e/Study/App/.claude/skills/module-refinement/references/brief-template.md`
- Scan checklist: `/mnt/e/Study/App/.claude/skills/module-refinement/references/dependency-scan.md`
- Device QA template: `/mnt/e/Study/App/.claude/skills/module-refinement/references/device-qa-template.md`
- Self-check rules: `/mnt/e/Study/App/.claude/skills/module-refinement/references/self-checks.md`
- Implement test plan template: `/mnt/e/Study/App/.claude/skills/module-refinement/references/test-plan-template.md`
- Path notes: `/mnt/e/Study/App/.claude/skills/module-refinement/references/path-notes.md`
- Closeout rules: `/mnt/e/Study/App/.claude/skills/module-refinement/references/closeout-rules.md`
- Example brief: `/mnt/e/Study/App/YingShi/docs/refinement/example-notifications-center.md`
