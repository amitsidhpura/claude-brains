# State

## Current focus
The standing manual-test pass (`docs/manual-test.md`) is COMPLETE as of 2026-08-08: all 92
items ticked. The defect register now stands at **15 open ISSUE notes** after the 2026-08-09
fixing session: three keyboard-chord issues closed by removing the chords; the 1.7
Escape-reopen glitch retired as a sandbox artifact and two real Escape defects fixed in its
place (`slashEscaped` flag, `closeCardMenus()` rung in the Escape chain — verified by
headless-Chrome probe of the mockup mirror); 2.8 @-mention fixed — the menu opened INVISIBLY
(`#mention` had zero CSS, so `position: static` ignored `openMenu()`'s viewport coords) and
the fix is CSS-only, verified by injecting the rules into the live installed-IDE panel over
CDP (filter/nav/pick/Escape all exercised, screenshot taken); mention menu also got the
slash menu's Escape-stick contract (`mentionEscaped`) plus, after a user-reported follow-up,
the full dismissal contract (outside-click close; mutual exclusivity with composer popups in
both directions; caret returning to an @-token reopens the menu — outside-click is a soft
dismissal, Escape a hard one; the slash menu shares the identical contract via `slashAuto()`). The JS pieces are verified by running the REAL spliced chat.html headless
(CSS + live-captured LIMITS, files fed via `onClaudeEvent`) — a reusable technique; only
real-JCEF confirmation remains, next runIde. Also fixed same-day (new standing item 2.14):
the JCEF-Linux Delete key inserting 0x7F as a tofu char — manual forward-delete on keydown
plus a capture-phase control-char strip, verified 7/7 on the headless harness, hardware-key
check pending next runIde. Checklist notes carry all detail. The pass
itself was committed as `3278aac` (2026-08-08); all 2026-08-09 work is uncommitted.

## Issue register highlights (full detail lives in docs/manual-test.md)
- **Real bugs**: openDiff accept reports FILE_SAVED but never writes (`DiffReview.kt:56`);
  custom commands filtered out of the `/` menu (`cmdKind` — no custom detection).
- **Cosmetic**: raw `<tool_use_error>`/`<system-reminder>` wrappers in OUT boxes; sub-agent
  async-launch "internal metadata" OUT box + harness annotations in the progress line;
  "unknown — retrying (n/10)" + duplicated first attempt; 6.4 split-button arrow never
  appeared; replay drops the auth status line and adds phantom summaries on error turns;
  resumed tail error has no Retry link.
- **Upstream (CLI 2.1.226)**: only `mcp__ide__getDiagnostics` is model-facing — openFile /
  openDiff / selection refused to the model. Bridge itself verified healthy by direct WS
  calls. Re-scope manual-test 10.1/10.3/10.4/10.5 if the restriction sticks.

## Next steps
- [x] Commit the pass results + context updates (done: `3278aac`, 2026-08-08).
- [ ] Fix issues, suggested order: 10.5 openDiff accept-save (roadmap-relevant) →
      3.1 custom commands (then re-test 9.10; fixture
      `claude-brains-testing/.claude/commands/dummy-cmd.md` is in place; the harness's
      `commands_changed` seeding trick applies)
      → 7.4 async-metadata suppression → replay fidelity (8.2 / 8.7).
      (5.9 wrapper strip: done 2026-08-09 — `RenderLimits.PLUMBING_TAGS`/`stripPlumbing`,
      shared via LIMITS.plumbingTags. 5.14 scroll pin: done 2026-08-09 — direction-based
      unpin + `__tasks` maybeScroll; feel-check the heavy turn next runIde. 5.13 addendum:
      Task* checklists now attach under their own tool line via a tool_use_id echoed on the
      `__tasks` frame — live matches replay; was the detached-duplicate stack the user
      screenshotted. 6.4 split caret: done 2026-08-09 — parts per RULE not per suggestion
      (measured: compound = ONE addRules suggestion with rules[]); "N.R" grant tokens narrow
      the echoed suggestion, subset-echo wire-probed as accepted; protocol doc updated.)
      (Done 2026-08-09: 7.3 chip CSS, 2.9 drag-drop, 4.4 live diffs for auto-approved edits
      — optimistic-with-supersede via `fillAppliedCard`/`supersedeEdit`/`lineStart` bridge
      round-trip; MultiEdit preview fixed as a rider. Next runIde re-verifies: hardware drag,
      hardware Delete, Escape flags, 4.4 visuals in acceptEdits mode.)
- [ ] Decide whether to re-scope section 10 of the checklist against the CLI 2.1.226
      model-facing tool restriction, or wait to see if upstream reverts it.
- [ ] Roadmap after fixes: editor-title accept/reject → @-symbol mentions → worktrees →
      extensibility status view (backlog.md).

## Test fixtures left in place (deliberate)
- `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/` holds stitched synthetic
  sessions: `c7a2bf37…` ("Update Shopify theme", 2.2 MB, every record type) and `b16da214…`
  (13 MB, 20 images — exceeds the 4 MB replay image budget so both degrade states show).
  Built from real donor records only; delete freely once the fixed issues are re-verified.
- `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` (sibling repo, NOT inside this
  one) — 3.1/9.10 re-test fixture.

## Known gaps (deliberately left)
- Sidechain/subagent replay ordering untested — still no `isSidechain` records locally.
- Windowed replay: DOM search only sees loaded blocks.
