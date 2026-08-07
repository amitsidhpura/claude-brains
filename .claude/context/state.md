# State

## Current focus
This session (2026-08-07) migrated all project context from the root `CLAUDE.md` into
`.claude/context/` — the migration is complete but UNCOMMITTED (`M .gitignore`, `D CLAUDE.md`,
new `.claude/context/`). On the plugin itself, the last work (through commit `ee7e9fc`) was
verification-infrastructure hardening: live-only branches made reproducible
(`tools/live_harness.py` + `tools/fixtures/`), the standing manual-test checklist
(`docs/manual-test.md`), debug-route documentation, and JCEF DevTools access
(build-set port 9222, DevToolsAction/F12).

## Where things stand
- Feature status detail lives in `docs/feature-checklist.md`; parity remainders in
  `docs/client-parity.md` (external) and `docs/renderer-parity.md` (internal, 0 open).
- All 2026-07-30 audits closed (renderer parity 27 fixed / 10 accepted; spacing/radius; code
  optimization). Phase 3 sandbox verification complete.
- Recent commits (through `ee7e9fc`) closed the client-parity sweep rows: retry-event dual
  spelling, mid-turn Stop replay, compaction status, renames/roster/ide_selection, and
  converted blind-verified parity items to harness-confirmed.

## Next steps
- [ ] Commit the context migration (new `.claude/context/`, edited `.gitignore`, deleted
      `CLAUDE.md`) — one commit, when the user asks.
- [ ] Decide whether to also un-ignore `.claude/skills/` so the `/context` skill travels with
      the repo (currently machine-local).
- [ ] Run the standing manual-test pass (`docs/manual-test.md`) against a real IDE install
      when the next release candidate builds.
- [ ] Roadmap order per CLAUDE.md's closing line: editor-title accept/reject → @-symbol
      mentions → worktrees → extensibility status view (also in backlog.md).

## Known gaps (deliberately left)
- Sidechain/subagent ordering untested — no `isSidechain` records in local sessions yet.
- Windowed replay: DOM search only sees loaded blocks.
