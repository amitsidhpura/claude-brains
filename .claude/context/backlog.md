# Backlog

## Immediate — manual-test issue register (detail in docs/manual-test.md, order in state.md)
- **Register is at 0 open / 25 resolved (2026-08-15)** — 3.1 + 9.10 shipped. Re-test fixtures
  `dummy-cmd.md` + `sub/nested-cmd.md` kept under `~/Sites/claude-brains-testing/.claude/commands/`
  (Linux only, not in git; recreate on Windows before any re-test there).
- A filename longer than the whole tool line is hard-clipped with no ellipsis (the tail never
  shrinks by design). Rare — needs ~45+ chars at a narrow panel. Offered 2026-08-12, not taken.
- ~~Permission cards wrap long paths, deliberately NOT clamped~~ — **SUPERSEDED 2026-08-15 by the
  user:** paths are project-relative and middle-ellipsised everywhere, cards included, because the
  card and the tool line beside it were naming one file two different ways. The concern that made
  the original call (you are approving a write to a SPECIFIC file) is answered instead by keeping
  the absolute path on `dataset.path` and `title` — the click target and the hover tooltip are
  unchanged, only the rendering is shortened.

## Next up
- The eight remaining **[DECIDE]** rows in `docs/feature-checklist.md` need a yes / later / no;
  9.4 fast-mode toggle [SM] is the cheapest 🟥 left.
- Plan-card keyboard shortcuts, deferred by the user 2026-08-16: Enter in the feedback input =
  keep planning with text, Shift+Tab = approve with text — both slot into the existing `done()`
  paths in chat.html.
- Watch-item: if the CLI's control-response schema ever admits a `feedback` field on allow,
  switch ClaudeCli.respondPermission to the TUI's exact shape (extra text block on the
  tool_result) instead of the `updatedInput.plan` append — two-line change.

- Replay the conversation into a RELOADED webview. `seedUi()` (2026-08-13) restores the chrome on
  every page load, but the log itself is still lost — the transcript would have to be pushed WITHOUT
  restarting the CLI (unlike `refresh`, which restarts it) and reconciled against frames still
  arriving mid-turn. Deliberately deferred: no reload has ever been observed in the wild, and the
  chrome was the part that could never heal.
- Kill a background process from the panel: the roster rows (`renderBgTasks`) are display-only and
  `interrupt()` only stops the in-flight response — the CLI kills shells via the `TaskStop` tool,
  which only the model can call. Needs a bridge verb + an action on the roster row (conversations-
  list hover-gutter idiom). Found 2026-08-12 while fixing the busy-state defects; the CLI has no
  host-side control request for this today, so check the protocol first.

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
- Marketplace-screenshot pipeline (mkshots.py/mkshots2.py/compose.py) lives only in the
  2026-08-16 session scratchpad — commit under `tools/` if refreshing the listing becomes routine.
- **Does the OS's reduce-motion setting reach JCEF?** chat.css got a `@media
  (prefers-reduced-motion: reduce)` block on 2026-08-13 covering all four animated surfaces
  (in-flight ring, `.bg::before`, `.shimmer`, `.generating .verb`). Half of this is now SETTLED:
  under `Emulation.setEmulatedMedia` the block fires correctly in real JCEF — ring goes
  `display:none` / `animation:none` and reverts cleanly — so it is live CSS, not dead. What is still
  unverified is the other half: whether the LINUX DESKTOP's setting propagates into an
  offscreen-rendered CEF at all (it read `matches:false` with the setting untested). Answer it by
  actually turning on reduce-motion in the OS and re-reading `matchMedia(...).matches` in the panel.
- `.ask-panel → .ask-b` sits at 4px where every other body→buttons gap (`→ .card-b`,
  `.ask-b → .ask-foot`) is 10px. Found in the 2026-08-13 spacing survey, offered, not taken.
- `.claude/skills/` is git-ignored (`.gitignore:22` ignores all of `.claude/*` bar `context/`),
  so the `/context` workflow itself does NOT travel to a fresh clone — un-ignore if wanted.

## Someday / conditional
- Author-signing the plugin (`signPlugin` + a generated key/chain, key in a GitHub secret). Today the
  zip goes up UNSIGNED and the Marketplace signs its own copy — five releases accepted that way, so
  this buys only the "installed from disk" trust path. Take it if that ever matters.
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
