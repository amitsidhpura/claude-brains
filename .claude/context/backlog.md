# Backlog

## Immediate — manual-test issue register (detail in docs/manual-test.md, order in state.md)
- 2 open issues from the 2026-08-08 pass, both in one pairing: 3.1 custom commands in the
  / menu + 9.10 re-test (user wants both together)
- Stale `~/.claude/ide/*.lock` files survive a plugin hot-reload (dispose skipped) — observed
  2026-08-09 with two dead locks; could misdirect a terminal TUI's IDE discovery. Check
  lockfile cleanup on unload if touched anyway.

## Next up
- VFS refresh after CLI writes: an accepted edit needs "Reload from disk" to show in an open
  editor — the CLI writes out-of-band and the plugin never refreshes the VirtualFile (verified
  2026-08-09: no refresh call anywhere; sandbox has no native file watcher, and frame-activation
  sync never fires when the user stays inside the IDE). Fix: async `vf.refresh(true, false)` at
  tool_result for Edit/Write/MultiEdit (`refreshAndFindFileByPath` for new files). Same root as
  the editLineStart reads-fresh-from-disk gotcha, surfaced editor-side.

## Roadmap (rough order)
- Editor accept/reject v2, remaining half: tweak-travel (pane edits ride updatedInput on
  accept). The buttons half SHIPPED 2026-08-09: Accept ✓ / Reject ✕ text buttons on a plain
  bar UNDER the diff editor (`FileEditorManager.addBottomComponent`), balloon removed. Two
  same-day iterations before the user accepted the shape: toolbar icons (unidentifiable),
  top banner with prose + info tint (wrong position, too loud) — chain in gotchas.md.
- @-symbol mentions
- Worktrees
- Extensibility status view

## Deferred (user's choice, do last)
- Conversation tabs
- Auto-include selection / Alt+K
- Voice input

## Housekeeping (one line each, do opportunistically)
- `docs/client-parity.md:1335` still says "**FIXED same day.**" — the register vocabulary is now
  ISSUE / RESOLVED; conform it if that file is touched anyway (offered 2026-08-09, not done).
- `.claude/skills/` is git-ignored (`.gitignore:22` ignores all of `.claude/*` bar `context/`),
  so the `/context` workflow itself does NOT travel to a fresh clone — un-ignore if wanted.

## Someday / conditional
- Same leak family as 7.4, found in the CLI binary but never observed live: a COMPLETED
  sub-agent result gets `agentId: … (use SendMessage …)` + `<usage>subagent_tokens…</usage>`
  appended (skipped for some agent types). Deliberately not fixed on an unverified premise —
  take it if it ever shows up in a real OUT box.
- TodoWrite checklist renderer (client-parity item 14) — until then TodoWrite tool lines stay blank by design
- Test sidechain/subagent replay ordering once local sessions contain `isSidechain` records
- Widen Plugin Verifier beyond PhpStorm — parked on the breadth decision; all groundwork +
  config sketch in `docs/verifier-matrix.md` (recommendation on file: edges variant)

## Not happening (decided 2026-08-07)
- Light theme / configurable colours — `docs/colors.md` and `design/colors.html` (the
  groundwork) deleted; recoverable from git history if this ever revives
