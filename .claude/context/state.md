# State

## Current focus
**2026-08-28 (seventeenth session): 3.5 tweak-travel + 3.6 files-changed review BUILT, both
hand-verified; plus the 2.1.250 re-audit, the mockup catch-up and the `--fs-*` type-scale tokens —
committed and pushed at the end of the session (2026-08-28).** 3.6:
`TurnChanges.kt` (baselines from the `Autosave` PreToolUse hook, settled by `CliFileSync`'s
turn end into a `__files_changed` frame), `filesLine()` in `webview/js/75-retraction.js` under
the ✻ summary, `review` bridge verb → `DiffReview.openChain` (one tab, N requests). Replay: the
done item carries `files` (paths) → informational line. Fixture 60; `TurnChangesTest`. Tweak-travel: the permission diff's right pane is editable
(`DiffReview.open(current=…)`, no longer readOnly); an edited pane → `EditProposals.tweakedInput`
(whole-file Edit / Write content / MultiEdit edits[], the shape VS Code sends) → `updatedInput` via
`ClaudeSessionService.respondPermission(updatedInput=)`; the card redraws from
`__perm_answered{tweaked,oldStr,newStr}` with the `LIMITS.tweakNote` line; replay flags
`tweaked` in SessionStore via `EditProposals.tweaked` (input vs `toolUseResult`). Probe + real
transcript in `plugin/src/test/resources/fixtures/tweak-travel.jsonl`. Tests 124, harness 478
(fixture 59, control run). **Hand-verified by the user 2026-08-28**, live and on resume (session
cad0a74e in the testing repo): pane editable (after the `createEditable` fix), file on disk has
the pane text, card shows the 3-line-context hunk + note, replay identical.
Re-audit findings: extension contributions + 12-tool roster identical; CLI roster +1 bundled skill
`/workflow-authoring` (Hidden/unverified); four `@internal` cloud-worker control subtypes; one new
row **1.25** ⬜ [SM] usage-limit grace banner (VS Code webview renders `rateLimitGraceActive` +
`overageStatus`; our `rate_limit_event` handler in `webview/js/70-events.js` is silent on
`status:"allowed"`). `./gradlew test` = **116**, folded into 16.1. Probe script + `initialize`
JSON for both binaries in the 2026-08-28 session scratchpad (`probe.py`, `init-2.1.2*.json`).
- Sixteenth session (same day, committed `682028e`): four completed docs deleted (`docs/verifier-matrix.md`, `docs/renderer-parity.md`,
`docs/client-parity.md`, `docs/manual-test.md`); their knowledge lives in gotchas § Build (verifier
DSL traps), gotchas § Replay (deliberate live/replay divergences), `docs/ide-mcp-protocol.md` § 11
(not-taken wire vocabulary) + § 12 (measured wire facts), and `docs/feature-checklist.md` § 17 (the
102-item manual-test record). `docs/` now holds five docs: ide-mcp-protocol, feature-checklist,
limits, release, slash-commands.
- **Checklist rules now in force**: every row in §1–16 is `**id** mark [effort] **Name** — gist;
  facts` (one bold = the name); the **At a glance** block after References is hand-maintained —
  refresh its counts and the 🟥 / [DECIDE] id lists at every audit; `<details>` only where the
  user asked (two re-audit paragraphs, eleven §17 groups) — no other HTML in the docs.
- Effort slider is the last row of `#modelFooter` (`plugin/src/main/resources/webview/chat.html`);
  **no chip shows the effort level** — three suffix forms rejected (decisions 2026-08-26). **Do not
  re-propose a chip suffix.** `.ef-row` is deliberately NOT `.tgl-row` (fixture 55 counts three).
- Checklist current to **2.1.250** (128 rows; 3.5 ✅ 2026-08-28; 9.10 🚫 = declined roster-flag gate).

## Released — 0.11.1 (2026-08-26)
**0.11.1 is the shipped version** (tag `v0.11.1`, commit `979326c`): effort slider in the model
menu, bare mode chip, "Models" header. PATCH bump — no new capability. `verifyPlugin` Compatible
×7, 0 warnings, run BEFORE the gate. GitHub release + feed + Marketplace all green; asset
byte-identical; Marketplace **Approved** (user's dashboard 2026-08-26: 242.0+, every check
Success incl. the 2026.2.2 rc IDE-run). Next release is a fresh bump when work lands.

## Open work — ids verified against `docs/feature-checklist.md`
- 🟥 high rows left: **8.7** rewind/fork [LG] · **8.11** side question [MD, probe pre-paid] ·
  **8.14** reloaded-webview log replay [LG] · **11.3** kill-background-process [MD, `stop_task`
  accepted]. 3.6 shipped 2026-08-28 (live turns; resumed-session Review parked in backlog).
- **Seven [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2 — plus a
  yes / later / no on the new **1.25** (grace banner; probe by injecting through `onClaudeEvent`).
- `/clear [name]` decision still open (checklist 7.6).
- `docs/slash-commands.md` roster prose now records 2.1.233 → 2.1.250 as one change
  (`/workflow-authoring`); the 2.1.233 capture date in its header is history, not staleness.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **490**; `./gradlew test` **130**.
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
- [ ] Get yes / later / no on the **seven [DECIDE]** rows (8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2) and on **1.25**.
- [ ] Decide `/clear [name]` (checklist 7.6).
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts → reloaded-webview
      log replay (**8.14**) → kill-background-process (**11.3**).
- [ ] Sync the **Marketplace web description** (hand-edited; uploads don't refresh it).
- [ ] `conventions.md` is ~50% over its briefing-tier cap — move the longest war stories to
      gotchas, leave pointers (offered 2026-08-26, not yet asked for).
- [ ] **User errands**: re-extract `vscode/` to **2.1.250** NOW (2.1.246 and 2.1.250 dirs both on disk
      2026-08-28; the older one vanishes on the next auto-update — gotchas § Testing); gist push of the context skill (`gh gist edit
      b2d033439ba4ca5bcd018f4fe5eef773 -f SKILL.md .claude/skills/context/SKILL.md`, check line 1
      is exactly `---`); Windows `./gradlew test` + VFS click check.

## Known gaps (deliberately left)
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
The 2026-08-26 and both 2026-08-28 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The Windows box still owes the CRLF splice check.
