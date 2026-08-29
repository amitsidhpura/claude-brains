# Backlog

## Immediate
- **Manual-test register closed at 0 open (2026-08-15); `docs/manual-test.md` deleted 2026-08-28**
  (self-contained by design; `git show 9bd1683:docs/manual-test.md`). Re-test fixtures
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
- The two remaining **[DECIDE]** rows in `docs/feature-checklist.md` (8.7, 14.2)
  need a yes / later / no (12.3 / 12.6 declined-deferred and 13.2 built 2026-08-29).
- Per-turn fast-mode proof in the conversation: append "· fast" to the turn summary when
  `result.usage.speed === "fast"` — offered 2026-08-24, user hasn't asked; small.
- Plan-card keyboard shortcuts, deferred by the user 2026-08-16: Enter in the feedback input =
  keep planning with text, Shift+Tab = approve with text — both slot into the existing `done()`
  paths in chat.html.
- Effort slider: switch from the muted `/effort` turn to `apply_flag_settings
  {settings:{effortLevel}}` — measured working over stdio 2026-08-23 (checklist 9.2); small
  change, removes the audit-trail turn from transcripts.
- Watch-item: if the CLI's control-response schema ever admits a `feedback` field on allow,
  switch ClaudeCli.respondPermission to the TUI's exact shape (extra text block on the
  tool_result) instead of the `updatedInput.plan` append — two-line change.

- **8.14** Replay the conversation into a RELOADED webview (the *log* half; the roster half
  shipped as 7.10 on 2026-08-17). `seedUi()` (2026-08-13) restores the chrome on
  every page load, but the log itself is still lost — the transcript would have to be pushed WITHOUT
  restarting the CLI (unlike `refresh`, which restarts it) and reconciled against frames still
  arriving mid-turn. Deliberately deferred: no reload has ever been observed in the wild, and the
  chrome was the part that could never heal.
- **`DiffReview.open` VFS staleness** (parked 2026-08-21): its left pane resolves `oldPath`
  through snapshot-only `findVFile`, so a file the VFS has not caught up on renders as NEW. Fix is
  `findVFileOnDisk` (it holds no read lock at that point — the `readLocked` block is inside the
  later `invokeLater`), but the failure is cosmetic and `refreshFromDisk` covers its paths, so it
  was left out of the 2026-08-21 open-path fix rather than widening that change's blast radius.

- **Phantom single-character comment commit** — ROOT CAUSE FOUND 2026-08-23 (second pass):
  IJPL-161111, a JCEF-OSR Linux keyboard bug fixed upstream in 2024.2.2+; every sighting was
  on the pre-fix sandbox 2024.2.0, since bumped to 2024.2.6 (build.gradle.kts). No YouTrack
  filing needed (already fixed). Watch-item only: if a phantom EVER appears on the new sandbox
  or a real IDE, re-instrument WITH modifier flags — the phantom fabricates Control events, so
  a phantom Ctrl+Enter submit of a half-typed draft was never ruled out (tape in
  `_local/phantom-enter-tape-2026-08-23/`).
- **Confirm-card path at narrow widths** — the permission card header's path wraps to a second
  line AND middle-ellipsises, so two lines are spent and the middle is still hidden. A CSS-only
  fix (wrap, show whole) was planned and DECLINED by the user 2026-08-24; parked, do not
  re-propose unprompted.
- **5.6 leftovers** (feature shipped 2026-08-23): keyboard-only selection cannot trigger the
  comment pill (mouse-up only); an INTERRUPTED ExitPlanMode's footer quotes the CLI's stock
  "The user doesn't want to proceed…" boilerplate via fbQuote (pre-existing, seen in probe) —
  filter it like REJECT_MESSAGE if it ever grates.

## Roadmap (rough order)
- **3.6 Review on RESUMED sessions**: the replayed "N files changed" line is informational
  (baselines die with the session). Options: `get_workspace_diff` hunks filtered to the turn's
  paths (git-only, includes user edits — say so in the tab title), or persist TurnChanges pairs
  under the session id. Parked 2026-08-28 at the v1 cut.
- ~~Editor accept/reject v2, remaining half: tweak-travel~~ — SHIPPED 2026-08-28 (checklist 3.5).
  The buttons half SHIPPED 2026-08-09: Accept ✓ / Reject ✕ text buttons on a plain
  bar UNDER the diff editor (`FileEditorManager.addBottomComponent`), balloon removed. Two
  same-day iterations before the user accepted the shape: toolbar icons (unidentifiable),
  top banner with prose + info tint (wrong position, too loud) — chain in gotchas.md.
- @-symbol mentions
- Worktrees
- Extensibility status view
- **15.5 Debugger MCP tools** [LG] (deferred 2026-08-29): expose the live PhpStorm debug session (`XDebuggerManager` → frames, variables, breakpoints) as bridge tools in `IdeTools.kt`, mirroring VS Code's `claude-vscode-extension` MCP server + `ask_debugger_help` hand-off; needs an Xdebug session in the sandbox to test

## Deferred (user's choice, do last)
- Conversation tabs (+ 8.8 reopen-closed-session and 8.10 session groups/sidebar, both deferred 2026-08-29 — only worth it with tabs or worktrees)
- Auto-include selection / Alt+K insert-mention action (checklist 6.6 / 6.5, 6.5 deferred 2026-08-29)
- @-mention symbols from the IDE index (checklist 6.4 [MD], deferred 2026-08-29)
- Focus view — prompts + responses only, tool noise hidden (checklist 12.6 [MD], deferred 2026-08-29; mockup first)
- Fable overage consent card + chip update on fallback (checklist 9.7 [MD], deferred 2026-08-29) — build only after `window.__modelFallbackSeen` has captured a real frame
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
- **11.5 Elicitation form** (deferred 2026-08-29; the decline ack shipped that day): only if a
  server the user actually uses elicits. First a ~30-line stdio MCP probe server in
  `~/Sites/claude-brains-testing/.mcp.json` whose one tool calls `elicitation/create` (form mode,
  two-field schema) — tape whether the CLI forwards it over stdio at all; then a form (askTabs
  idiom). VS Code passes no `onElicitation` handler.
- Real ES modules for `webview/js/` (CefResourceHandler or file:// base) — only if per-file
  scope ever earns the resource-serving layer + deferred-load rewiring; the 2026-08-19 concat
  splice is the stepping stone.
- Author-signing the plugin (`signPlugin` + a generated key/chain, key in a GitHub secret). Today the
  zip goes up UNSIGNED and the Marketplace signs its own copy — five releases accepted that way, so
  this buys only the "installed from disk" trust path. Take it if that ever matters.
- Same leak family as 7.4, found in the CLI binary but never observed live: a COMPLETED
  sub-agent result gets `agentId: … (use SendMessage …)` + `<usage>subagent_tokens…</usage>`
  appended (skipped for some agent types). Deliberately not fixed on an unverified premise —
  take it if it ever shows up in a real OUT box.
- Test sidechain/subagent replay ordering once local sessions contain `isSidechain` records
- Widen Plugin Verifier beyond PhpStorm — parked on the breadth decision. Recommendation:
  **edges** (keep `recommended()` for the PhpStorm ladder + `select {}` the other products at
  sinceBuild 242 and the current branch only; ~20 GB first run). Full matrix (~80 GB, 2–4 h/run)
  rejected as redundant. `docs/verifier-matrix.md` deleted 2026-08-28 — its four DSL traps are in
  gotchas.md § Build; config sketch via `git show 9bd1683:docs/verifier-matrix.md`.

## Not happening (decided 2026-08-07)
- Light theme / configurable colours — `docs/colors.md` and `design/colors.html` (the
  groundwork) deleted; recoverable from git history if this ever revives
