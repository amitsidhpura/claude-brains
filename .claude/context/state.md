# State

## Current focus
**2026-08-29 (eighteenth session, started 2026-08-28 evening): 11.3 kill-background-task BUILT and
hand-verified; 11.4 re-measured and DECLINED; §11 assessed row by row — all in the working tree,
committed and pushed at the end of the session (2026-08-29).**
- **11.3**: roster rows in `webview/js/70-events.js` `renderBgTasks()` carry a hover-✕ (`.bg-x`,
  conversations-list gutter idiom, NO confirm step by user decision) → bridge `stopTask` →
  `ClaudeSessionService.stopTask` → `ClaudeCli.stopTask` = `stop_task{task_id}`. The row dims
  (`.stopping`, `stoppingBgTasks`, pruned per roster frame) and is NEVER removed optimistically — the
  CLI's next `background_tasks_changed` (REPLACE) is the only truth. `ambient:true` tasks (2.1.250
  schema) are dropped from the roster and the suspend count — UNMEASURED; the first one seen lands in
  `window.__ambientSeen` + a console warning. Fixture 61 (23 asserts). User hand-tested four
  scenarios (§17 MT-7.8): shell, Explore sub-agent (suspended turn resumed cleanly), one-of-two,
  Escape/new-conversation.
- **11.4** 🚫: measured 2026-08-29 — an Explore agent whose Bash failed and which replied "FAILED: …"
  still ended `task_notification{status:"completed"}`; task status is lifecycle, never verdict; VS
  Code shows nothing either. Drift fixed on the way: a kill sends `task_updated{killed}` +
  `task_notification{stopped}` — `taskLine` in `50-blocks.js` now paints on `stopped` too (fixture
  45 gained a step, negative control run).
- **11.6** assessed, awaiting the user's word: recommend 🚫 (init frame already carries
  `mcp_servers/agents/skills/plugins`; 11.2's failure notice is the actionable half; VS Code has no
  such view; SM if wanted).
- **11.5** assessed: our catch-all answers `elicitation` with `{}` — schema-invalid (the enum wants
  `action:accept|decline|cancel`); recommend `{action:"decline"}` now and a 30-line stdio MCP probe
  server before ever building a form. VS Code passes NO `onElicitation` handler. Not done yet.
- Checklist current to **2.1.250** (128 rows; 78 ✅ · 3 🟥 · 6 🟧 · 16 ⬜ · 18 ➖ · 7 🚫).
- **Checklist rules still in force**: `**id** mark [effort] **Name** — gist; facts`; the **At a
  glance** block is hand-maintained — refresh counts and id lists at every change; `<details>` only
  in the two re-audit paragraphs and the eleven §17 groups.
- Effort slider: **no chip shows the effort level** — do not re-propose a chip suffix (2026-08-26).

## Released — 0.11.1 (2026-08-26)
**0.11.1 is the shipped version** (tag `v0.11.1`, commit `979326c`): effort slider in the model
menu, bare mode chip, "Models" header. PATCH bump — no new capability. `verifyPlugin` Compatible
×7, 0 warnings, run BEFORE the gate. GitHub release + feed + Marketplace all green; asset
byte-identical; Marketplace **Approved** (user's dashboard 2026-08-26: 242.0+, every check
Success incl. the 2026.2.2 rc IDE-run). Next release is a fresh bump when work lands.

## Open work — ids verified against `docs/feature-checklist.md`
- 🟥 high rows left: **8.7** rewind/fork [LG] · **8.11** side question [MD, probe pre-paid] ·
  **8.14** reloaded-webview log replay [LG]. 11.3 shipped 2026-08-29; 3.6 shipped 2026-08-28 (live
  turns; resumed-session Review parked in backlog).
- **Seven [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2 — plus a
  yes / later / no on **1.25** (grace banner; probe by injecting through `onClaudeEvent`), on
  **11.6** (recommended 🚫) and on the two 11.5 steps (decline ack; MCP probe server).
- `/clear [name]` decision still open (checklist 7.6).
- `docs/slash-commands.md` roster prose now records 2.1.233 → 2.1.250 as one change
  (`/workflow-authoring`); the 2.1.233 capture date in its header is history, not staleness.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **514**; `./gradlew test` **130**.
- Fixture 51 = `51-model-menu-effort-rail.json`; three negative controls in its provenance — read
  before changing any assert.
- Sandbox **PhpStorm 2024.2.6**; start (must run from `plugin/`; background tasks start in the
  REPO ROOT): `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` DETACHES** — gradle's exit code says
  nothing about the IDE; `pgrep -f 'idea.system.pat[h]'`; kill by pid, wait for CDP to vanish.
- **ALWAYS verify the running build BY CONTENT over CDP before trusting a fixture run** (a relaunch
  served the PREVIOUS build's bytes — gotchas § Testing). Control builds restore the WHOLE
  `plugin/src/main/resources/webview/` directory.
- Release prep: check every precondition before the first write, assert on exact tokens, and diff
  before the gate (gotchas § Build — the 0.11.1 half-applied bump).

## Next steps
- [ ] Get yes / later / no on the **seven [DECIDE]** rows (8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2), on **1.25**, on **11.6** (→ 🚫?) and on 11.5's two steps.
- [ ] If 11.5 step 1 is a yes: `ClaudeCli.handleControlRequest` catch-all answers `elicitation` with `{action:"decline"}` (keep `{}` for the rest).
- [ ] Decide `/clear [name]` (checklist 7.6).
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts → reloaded-webview
      log replay (**8.14**).
- [ ] Sync the **Marketplace web description** (hand-edited; uploads don't refresh it).
- [ ] `conventions.md` is ~50% over its briefing-tier cap — move the longest war stories to
      gotchas, leave pointers (offered 2026-08-26, not yet asked for).
- [ ] **User errands**: re-extract `vscode/` to **2.1.250** NOW (2.1.246 and 2.1.250 dirs both on disk
      2026-08-28; the older one vanishes on the next auto-update — gotchas § Testing); gist push of the context skill (`gh gist edit
      b2d033439ba4ca5bcd018f4fe5eef773 -f SKILL.md .claude/skills/context/SKILL.md`, check line 1
      is exactly `---`); Windows `./gradlew test` + VFS click check.

## Known gaps (deliberately left)
- **The user's own hand-test sessions (2026-08-28 evening) are in NO `~/.claude/projects` dir** —
  only CDP-driven ones are; unexplained, harmless (gotchas § Testing).
- **The Thinking switch is INERT on Fable** — measured 2026-08-26, "document only" by user
  decision, stated in the 0.11.1 release notes; do not gate it.
- **Do not gate UI on roster capability flags** (checklist 9.10 🚫). Only `supportsFastMode` is
  gated, and only because delivery was probed.
- Effort/model conversation MARKERS dropped 2026-08-24 (partly superseded 2026-08-25: /effort
  shows the CLI's confirmation line). Confirm-card path wrapping declined — do not re-propose.
- Typing `/model` in the composer is refused (`cmdKind` → 'tui'); the chip is the only model surface.
- 1M switch carries NO client-side validity logic, by decision. Fast-mode "· fast" marker parked.
- 5.6 keyboard-only comment pill; plan-card keyboard shortcuts deferred 2026-08-16;
  `DiffReview.open` snapshot-only lookup parked; `/batch` verified at N=2 only.

## Which machine — check FIRST, both are real
The 2026-08-26, 2026-08-28 and 2026-08-29 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The Windows box still owes the CRLF splice check.
