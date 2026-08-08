# State

## Current focus
The standing manual-test pass (`docs/manual-test.md`) is COMPLETE as of 2026-08-08: all 92
items ticked, with 19 inline **ISSUE** notes forming the defect register. The checklist file
carries all detail (symptom + root cause + code pointers per issue). Uncommitted:
`docs/manual-test.md` (all ticks/issues) and `.claude/context/*` — commit when the user asks.

## Issue register highlights (full detail lives in docs/manual-test.md)
- **Real bugs**: openDiff accept reports FILE_SAVED but never writes (`DiffReview.kt:56`);
  background-task chip never hides (`.chip-btn{display:inline-flex}` defeats `[hidden]` —
  one-line CSS fix + clear textContent in renderBgTasks); custom commands filtered out of the
  `/` menu (`cmdKind`, chat.html:2034 — no custom detection); auto-approved edits render no
  diff live (only the permission card ever draws one; replay is richer); all three webview
  keyboard chords dead on this setup (Ctrl+N / Ctrl+Alt+G / F12 — JS keydown handlers never
  fire; likely need IDE-level action shortcuts); Escape-reopen popup glitch; `@` file-mention
  menu not opening; drag-drop attach dead.
- **Cosmetic**: raw `<tool_use_error>`/`<system-reminder>` wrappers in OUT boxes; sub-agent
  async-launch "internal metadata" OUT box + harness annotations in the progress line;
  "unknown — retrying (n/10)" + duplicated first attempt; 6.4 split-button arrow never
  appeared; replay drops the auth status line and adds phantom summaries on error turns;
  resumed tail error has no Retry link.
- **Upstream (CLI 2.1.226)**: only `mcp__ide__getDiagnostics` is model-facing — openFile /
  openDiff / selection refused to the model. Bridge itself verified healthy by direct WS
  calls. Re-scope manual-test 10.1/10.3/10.4/10.5 if the restriction sticks.

## Next steps
- [ ] Commit the pass results + context updates (one commit, when the user asks).
- [ ] Fix issues, suggested order: 7.3 chip CSS (one-liner) → 10.5 openDiff accept-save
      (roadmap-relevant) → 3.1 custom commands (then re-test 9.10; fixture
      `claude-brains-testing/.claude/commands/dummy-cmd.md` is in place) → 4.4 live diff for
      auto-approved edits → keyboard chords as IDE actions → composer trio (1.7 / 2.8 / 2.9)
      → plumbing-leak strips → replay fidelity (8.2 / 8.7).
- [ ] Decide whether to re-scope section 10 of the checklist against the CLI 2.1.226
      model-facing tool restriction, or wait to see if upstream reverts it.
- [ ] Roadmap after fixes: editor-title accept/reject → @-symbol mentions → worktrees →
      extensibility status view (backlog.md).

## Test fixtures left in place (deliberate)
- `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/` holds stitched synthetic
  sessions: `c7a2bf37…` ("Update Shopify theme", 2.2 MB, every record type) and `b16da214…`
  (13 MB, 20 images — exceeds the 4 MB replay image budget so both degrade states show).
  Built from real donor records only; delete freely once the fixed issues are re-verified.
- `claude-brains-testing/.claude/commands/dummy-cmd.md` — 3.1/9.10 re-test fixture.

## Known gaps (deliberately left)
- Sidechain/subagent replay ordering untested — still no `isSidechain` records locally.
- Windowed replay: DOM search only sees loaded blocks.
