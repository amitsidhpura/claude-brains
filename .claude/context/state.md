# State

## Current focus
**2026-08-29 (twentieth session): goal = every checklist section ✅ by 2026-08-30 EOD. Today: §1 built
(1.21/1.23/1.24) and tested, §6/§9/§12 closed by decision, §13 built (13.2). Committed and pushed.**
Sections still ⬜: **§8, §14, §15** — see Next steps. Previous session (nineteenth): §11 closed.
- **11.3**: roster rows in `webview/js/70-events.js` `renderBgTasks()` carry a hover-✕ (`.bg-x`,
  conversations-list gutter idiom, NO confirm step by user decision) → bridge `stopTask` →
  `ClaudeSessionService.stopTask` → `ClaudeCli.stopTask` = `stop_task{task_id}`. The row dims
  (`.stopping`, `stoppingBgTasks`, pruned per roster frame) and is NEVER removed optimistically — the
  CLI's next `background_tasks_changed` (REPLACE) is the only truth. `ambient:true` tasks (2.1.250
  schema) are dropped from the roster and the suspend count — UNMEASURED; the first one seen lands in
  `window.__ambientSeen` + a console warning. Fixture 61 (23 asserts). User hand-tested four
  scenarios (§17 MT-7.8): shell, Explore sub-agent (suspended turn resumed cleanly), one-of-two,
  Escape/new-conversation.
- **11.4** ➖ (declined): measured 2026-08-29 — an Explore agent whose Bash failed and which replied "FAILED: …"
  still ended `task_notification{status:"completed"}`; task status is lifecycle, never verdict; VS
  Code shows nothing either. Drift fixed on the way: a kill sends `task_updated{killed}` +
  `task_notification{stopped}` — `taskLine` in `50-blocks.js` now paints on `stopped` too (fixture
  45 gained a step, negative control run).
- **11.6** ➖ declined by the user 2026-08-29 (terminal's half; 11.2 covers the failure case;
  VS Code has none). Revivable as [SM] — see decisions.md.
- **11.5** DONE 2026-08-29: `ClaudeCli.handleControlRequest` answers
  `elicitation` with `{action:"decline"}` (bare `{}` was schema-invalid); row → ➖, form deferred
  until a local MCP server actually elicits (then a ~30-line stdio probe server first). Docs
  updated: checklist 11.5 + At a glance, `docs/ide-mcp-protocol.md` § elicitation. compileKotlin +
  `./gradlew test` green; harness not rerun (Kotlin-only change).
- Checklist current to **2.1.250** (128 rows; 78 ✅ · 3 🟥 · 5 🟧 · 15 ⬜ · 27 ➖ — 🚫 mark RETIRED 2026-08-29, everything not implemented is ➖ with the reason in the row).
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
  11.5 and 11.6 both settled 2026-08-29 (decline ack shipped; status view declined). §11 closed.
- `/clear [name]` decision still open (checklist 7.6).
- `docs/slash-commands.md` roster prose now records 2.1.233 → 2.1.250 as one change
  (`/workflow-authoring`); the 2.1.233 capture date in its header is history, not staleness.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **541** (fixtures to 65); `./gradlew test` **134**.
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
**GOAL (user, 2026-08-29): every checklist section ✅ by 2026-08-30 EOD** — ✅ = every row ✅ or ➖.
Section headings carry ✅/⬜ (rule in the checklist's Numbering note). Working **one section at a
time, first to last**: for each, present the open rows with a recommendation, get yes/➖ per row,
build the yes-rows, mark the rest ➖ with the reason, refresh the heading mark + At a glance.
- **§1 DONE 2026-08-29** (uncommitted): 1.21 redacted_thinking (`thinkBlock(text, secs, redacted)`
  in `50-blocks.js`; live branch in `75-retraction.js` content_block_start; `SessionStore` emits
  `redacted:true`; `55-replay.js` passes it), 1.23 `decision_reason` → `permission_request.reason`
  → ↳ note under `.card-h` (`85-cards.js`; Kotlin plumbing ClaudeCli → ClaudeSessionService →
  ChatPanel), 1.24 `turnEndReason()` in `80-gauge.js` (silent for completed / aborted_* /
  background_requested / while `stopping`). 1.22 ➖ (measured: no `tool_progress` on stream-json,
  2.1.251), 1.25 ➖ deferred by the user. Fixtures 62/63/64 (19 asserts, wrong-value controls run); 1.23 verified LIVE (trigger: any Bash with `$(…)` → "Contains command_substitution"); spacing fix `.card .card-h + .t-note` (8/8, was 8/0; mockup card added at `design/mockup.html` § permission cards); 1.21 cannot be triggered (docs: safety-only, never on Fable/Mythos 5); 1.24 `max_turns` measured via `--max-turns 1` (verbatim frame in fixture 64; error arm now prefers `errors[]` over the subtype token);
  SessionStoreTest gained the redacted replay test (pre-fix control run: 0 blocks).
- **§6 DONE** (6.4, 6.5 ➖ later → backlog; 6.7 ➖ no). **§12 DONE** (12.3 ➖ no; 12.6 ➖ later → backlog).
- **§13 DONE 2026-08-29**: 13.2 built — `ClaudeSettingsSchema.kt` (`JsonSchemaProviderFactory` →
  SchemaStore URL, nothing bundled; both `settings.json` and `settings.local.json` under `.claude`);
  plugin.xml: `JavaScript` ns extension + optional depends `com.intellij.modules.json`
  (`claude-brains-json.xml`, empty, JCEF pattern). `ClaudeSettingsSchemaTest` (3). `./gradlew test`
  134. verifyPlugin run 2026-08-29 (result in journal). Hand-checked by the user 2026-08-29 in the sandbox (§17 MT-10.6): schema named, completions, bogus key flagged.
- **§9 DONE 2026-08-29**: 9.7 ➖ later + WATCH — `system/model_fallback` → `window.__modelFallbackSeen` +
  one console warning (`70-events.js`), fixture 65 (pre-watch control run). Backlogged.
- **Remaining ⬜ sections: §8 (8.7, 8.8, 8.10, 8.11, 8.14 — recommended later/later/later/BUILD/later,
  awaiting the user) · §14 (14.1–14.4) · §15 (15.5, 15.6).**
- CLI auto-updated to **2.1.251** (2026-08-29); checklist/docs are audited at 2.1.250. Not re-audited.
- [ ] **§8** — user's yes/later/no on 8.7 (rec. later), 8.8 + 8.10 (rec. later, follow tabs),
      **8.11 (rec. BUILD — probe pre-paid, `side_question` control)**, 8.14 (rec. later).
- [ ] **§14** — recommendation given 2026-08-29, awaiting the user: 14.1 / 14.2 / 14.3 later as one
      "worktrees" backlog bundle, 14.4 no.
- [ ] **§15** — 15.5 `ask_debugger_help` [LG], 15.6 Chrome/Jupyter MCP toggles [SM]: explain plainly, get
      the call (both lean ➖: debugger hand-off is a VS Code debug-console feature; MCP toggles are the
      terminal's half).
- [ ] After every section: heading mark, At a glance counts (`awk` recount), [DECIDE] list, backlog.
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
- **Do not gate UI on roster capability flags** (checklist 9.10 ➖, declined). Only `supportsFastMode` is
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
