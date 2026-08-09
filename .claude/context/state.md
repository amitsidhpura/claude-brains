# State

## Current focus
**v0.4.0 RELEASED 2026-08-09** (user-approved notes; full gate history in journal seventh
entry): the editor-review release — dual-surface edit permissions with the card-identical
verdict bar, delete-current-conversation, the ShellEnv MCP fix (smoke-test find), the
composer spacing fix, and the whole 92-item register wave. Verifier clean ×3, smoke-tested
on the user's real PhpStorm. The defect register in `docs/manual-test.md` stands at
**2 open ISSUE notes / 22 RESOLVED**; the open pair 3.1+9.10 (custom commands in the `/`
menu) was explicitly scoped OUT of 0.4.0 and is the next work, worked TOGETHER per the
user. Per-issue detail lives under each checklist item in `docs/manual-test.md` — that
file is the register, not this one.

## Issue register highlights (full detail in docs/manual-test.md)
- **Still open, next up, worked TOGETHER per user**: 3.1 custom commands missing from the
  `/` menu (`cmdKind` has no custom detection) + 9.10 commands_changed re-test (unobservable
  until 3.1 is fixed).
- **10.1/10.3 re-scoped 2026-08-09**: the model-facing restriction is a hardcoded allowlist,
  byte-identical across CLI 2.1.222–226 — upstream by design, matching VS Code. Items now mean
  their bridge halves (verified). Server-rename dodge measured and REJECTED (gotchas.md).
- **10.5 premise correction worth remembering**: the IDE never writes on openDiff accept —
  FILE_SAVED is the accept TOKEN and the CLI does the disk write itself (contract now in
  docs/ide-mcp-protocol.md § 4).

## Next steps
- [ ] **3.1 custom commands + 9.10 together** (user's explicit pairing): fixture
      `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` is in place, and the
      harness's `system/commands_changed` seeding trick supplies the roster.
- [ ] VFS refresh after CLI writes (backlog.md "Next up") — accepted edits currently need
      "Reload from disk" in open editors; fix shape already worked out.
- [x] Plugin Verifier re-run DONE 2026-08-09 (after the verdict-bar + delete-current work,
      run against the working tree): plain "Compatible", zero warnings, on all seven
      recommended IDEs 242 → 262 (reports in plugin/build/reports/pluginVerifier/). The
      release-blocking gate is cleared; re-run only if new platform API usage lands before
      the cut.
- [ ] When convenient: 5.14's heavy-turn scroll FEEL on real JCEF (logic proven 8/8 headless);
      and the 8.2/8.7 tail-error replay eyeball — needs a session whose TAIL is an API error
      (the 2026-08-09 storm session `afe39ca0…` recovered, so its error is mid-history; stitch
      from the `"error":"rate_limit"` donor records in
      `~/.claude/projects/-home-syncroze-Sites-peers-woocommerce/b4589d75…jsonl`).
- [ ] Roadmap after that: editor accept/reject v2 remaining half (tweak-travel so pane edits
      ride updatedInput; buttons half shipped 2026-08-09) → @-symbol mentions → worktrees →
      extensibility status view (backlog.md).

## Test fixtures left in place (deliberate)
- `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/` holds stitched synthetic
  sessions: `c7a2bf37…` ("Update Shopify theme", 2.2 MB, every record type) and `b16da214…`
  (13 MB, 20 images — exceeds the 4 MB replay image budget so both degrade states show).
  Built from real donor records only; delete freely once the fixed issues are re-verified.
  The real network-off storm session `afe39ca0…` also lives there — the 9.1 replay-reorder
  measurement donor.
- `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` (sibling repo, NOT inside this
  one) — 3.1/9.10 re-test fixture.

## Known gaps (deliberately left)
- Sidechain/subagent replay ordering untested — still no `isSidechain` records locally.
- Windowed replay: DOM search only sees loaded blocks.
- Editor permission diff: Accept / Accept all edits / Reject are real text buttons on a bar
  under the diff editor (v2 buttons half, 2026-08-09; toolbar-icon and top-banner cuts
  rejected — gotchas.md). The middle button is COMBINED by design (user's spec): one button
  grants every allow-suggestion whole — "Always allow" when rules are among them, "Accept
  all edits" when mode-only — echoing each suggestion's original index; no split/partial
  dropdown in the editor (the panel card keeps that). Wire: "FILE_SAVED_ALL" verdict, a
  permission-flow-only extension DiffReview documents — bridge verdicts stay the reference
  set. Panes still read-only (no tweak-travel) — remaining v2 scope in backlog.
