# State

## Current focus
Working the standing defect register in `docs/manual-test.md`. The 92-item pass itself is
COMPLETE (2026-08-08, commit `3278aac`); what remains is fixing what it found. Register stands
at **2 open ISSUE notes / 22 RESOLVED** (the last two are the 3.1+9.10 pairing). UNCOMMITTED on 2026-08-09 (second session): 9.1 both
halves (RETRY_REASONS enum translation + twin-emission dedupe in chat.html, fixture 09;
late-flushed api_error reorder in SessionStore + Kotlin test — user-verified live AND on
replay with a real network-off storm) and 10.5 (DiffReview rewritten to the reference openDiff
contract — TAB_CLOSED verdict, final-pane-text accept, close-tool + dead-caller resolution;
all three verdicts wire-verified, user confirmed no stale balloons). Per-issue detail lives
under each checklist item in `docs/manual-test.md` — that file is the register, not this one.

## Issue register highlights (full detail in docs/manual-test.md)
- **Still open, user wants them worked TOGETHER later**: 3.1 custom commands missing from the
  `/` menu (`cmdKind` has no custom detection) + 9.10 commands_changed re-test (unobservable
  until 3.1 is fixed).
- **10.1/10.3 re-scoped 2026-08-09**: the model-facing restriction is a hardcoded allowlist,
  byte-identical across CLI 2.1.222–226 — upstream by design, matching VS Code. Items now mean
  their bridge halves (verified). Server-rename dodge measured and REJECTED (gotchas.md).
- **10.5 premise correction worth remembering**: the IDE never writes on openDiff accept —
  FILE_SAVED is the accept TOKEN and the CLI does the disk write itself (contract now in
  docs/ide-mcp-protocol.md § 4).

## Next steps
- [x] **runIde re-verification sweep** — user-confirmed on real hardware/JCEF 2026-08-09:
      hardware file drag (2.9), hardware Delete key (2.14), Escape in the slash/mention menus,
      4.4's applied-edit cards in acceptEdits, and 6.4's split-button menu. Plus 7.4(a) verified
      live the same session (background Explore launch: no internal-metadata OUT box, clean
      finished line). Still outstanding from the old list: **5.14's heavy-turn scroll FEEL only**
      (headless proved the logic 8/8; real-JCEF rAF cadence is the production case — a
      when-next-convenient check, not a blocker).
- [x] ~~10.5 openDiff~~ — resolved 2026-08-09 (premise corrected + real fixes; see register).
- [x] ~~9.1 retry storm~~ — resolved 2026-08-09, live and replay halves, user-verified both.
- [ ] **3.1 custom commands + 9.10 together** (user's explicit pairing, deferred to later):
      fixture `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` is in place, and
      the harness's `system/commands_changed` seeding trick supplies the roster.
- [ ] Eyeball 8.2/8.7 on real JCEF when convenient: resume a session whose TAIL is an API
      error (the 2026-08-09 storm session `afe39ca0…` recovered, so its error is mid-history —
      stitch a tail-error one from the `"error":"rate_limit"` donor records in
      `~/.claude/projects/-home-syncroze-Sites-peers-woocommerce/b4589d75…jsonl`, or re-run
      the auth-failure manufacture) and check status line + no phantom summary + Retry.
- [x] ~~Section 10 re-scope decision~~ — decided 2026-08-09: restriction measured as stable
      upstream policy (identical 2.1.222–226); 10.1/10.3 re-scoped to their bridge halves.
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
