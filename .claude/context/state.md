# State

## Current focus
Working the standing defect register in `docs/manual-test.md`. The 92-item pass itself is
COMPLETE (2026-08-08, commit `3278aac`); what remains is fixing what it found. Register stands
at **6 open ISSUE notes / 18 RESOLVED**: rounds `4a64433` + `fe620ef` (committed, pushed), then
UNCOMMITTED on 2026-08-09: 7.4 (internal-metadata suppression + harness-envelope strip, fixture
07, 7.4(a) user-verified on real JCEF) and 8.2+8.7 (auth status line on replay via
AUTH_BLOCKED_CODES + `icon:"auth"` items, phantom summary suppressed via `reqError`, which also
un-blocked the tail Retry — fixture 08; not yet eyeballed on real JCEF). Per-issue detail lives
under each checklist item in `docs/manual-test.md` — that file is the register, not this one.

## Issue register highlights (full detail in docs/manual-test.md)
- **Real bugs, still open**: openDiff accept reports FILE_SAVED but never writes
  (`DiffReview.kt:56`); custom commands filtered out of the `/` menu (`cmdKind` has no custom
  detection).
- **Cosmetic, still open**: "unknown — retrying (n/10)" + duplicated first attempt.
- **Upstream (CLI 2.1.226)**: only `mcp__ide__getDiagnostics` is model-facing — openFile /
  openDiff / getCurrentSelection refused TO THE MODEL. Bridge itself verified healthy by direct
  MCP-over-WS calls. Re-scope manual-test 10.1/10.3/10.4/10.5 if the restriction sticks.

## Next steps
- [x] **runIde re-verification sweep** — user-confirmed on real hardware/JCEF 2026-08-09:
      hardware file drag (2.9), hardware Delete key (2.14), Escape in the slash/mention menus,
      4.4's applied-edit cards in acceptEdits, and 6.4's split-button menu. Plus 7.4(a) verified
      live the same session (background Explore launch: no internal-metadata OUT box, clean
      finished line). Still outstanding from the old list: **5.14's heavy-turn scroll FEEL only**
      (headless proved the logic 8/8; real-JCEF rAF cadence is the production case — a
      when-next-convenient check, not a blocker).
- [ ] Fix the remaining register issues, suggested order: **10.5 openDiff accept-save**
      (roadmap-relevant; Kotlin-side, so verification is runIde + direct MCP-over-WS, NOT the
      headless harness) → **3.1 custom commands** in the `/` menu (then re-test 9.10; fixture
      `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` is in place, and the
      harness's `system/commands_changed` seeding trick supplies the roster) → the retry-storm
      cosmetics ("unknown — retrying" + duplicated first attempt).
- [ ] Eyeball 8.2/8.7 on real JCEF when convenient: resume a session whose tail is an API
      error (stitch one from the real `"error":"rate_limit"` donor records in
      `~/.claude/projects/-home-syncroze-Sites-peers-woocommerce/b4589d75…jsonl`, or re-run
      the auth-failure manufacture) and check status line + no phantom summary + Retry.
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
