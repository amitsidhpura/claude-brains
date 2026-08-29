# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

## 2026-08-29 (fourth) — §15 closed, 8.8/8.10 deferred, 8.11 side question built and live-verified
- §15: 15.5 `ask_debugger_help` later (backlog "Debugger MCP tools" [LG]), 15.6 MCP toggles no (by
  design). §8: 8.8 later (follows tabs), 8.10 later ("not very important"). User parked 8.7 + §14 last.
- 8.11 measured BEFORE building: a live probe (panel flags, `initialize` then `side_question`) showed
  the roster has no `/btw`, the wire is `system/control_request_progress{started}` →
  `control_response{response, synthetic}`, `history` pairs work, and nothing hits the transcript dir.
- Built per convention: mockup + CSS first, headless render shown, yes, then wired. Kotlin gained
  request-id-keyed control callbacks (`ClaudeCli.pending`). Fixture 66 written defensively (null-safe
  expects) so the FREE pre-feature control could run: 21/23 failed; centring assert read -61 against
  the pre-centring CSS; right-edge assert added after the user spotted the mockup misalignment.
- Two slips caught by the harness: fixture 66 hard-coded row ids (`sq1`) and failed on its second
  run (`sq5`) — ids are page-lifetime monotonic by design, so the fixture now reads them from the
  tape; and `CMD_LOCAL` added a slash row, so four roster-count fixtures needed +1.
- User feedback applied: side input one line at rest (was 2 rows — dead space above Send);
  panel centred on `#inputcard` (`margin: 0 auto`, measured 435 vs 435 on JCEF) and copying the
  composer's scrollbar inset. Measured on the way: `syncGutter` lags a scrollbar that appears
  without a `#log` resize (pre-existing; offered, not asked).
- Red destructive hover (roster ✕ + history delete) asked about, shown deliberate, kept.
- End: harness **566/0** (fixtures to 66), `./gradlew test` **134/0**, sandbox up on the final
  build. Committed and pushed.

## 2026-08-29 (third) — section marks; goal set; §1 closed (1.21/1.23/1.24 built, 1.22/1.25 ➖)
- User asked for section-level marks: `## N. ✅|⬜ Title`, one glyph, no counts ("let's not complicate
  it"). Then set the goal: every section ✅ by 2026-08-30 EOD, working one section at a time.
- Measured before building: `tool_progress` is NOT on stream-json (12 s foreground Bash, 2.1.251, zero
  frames) → 1.22 ➖; `result.terminal_reason:"completed"` confirmed live → 1.24 buildable.
- Built 1.21 (redacted_thinking line, live + replay), 1.23 (decision_reason note on the permission
  card, Kotlin plumbing), 1.24 (turn-end-reason status line). Harness 533/0, tests 131/0.
- Fixture slip worth remembering: the `__transcript` frame's key is `items`, not `blocks` — and a
  null `.textContent` in an expect ABORTS the whole harness run (fixtures after it never run).
- CLI auto-updated to 2.1.251 mid-session; the checklist stays audited at 2.1.250.
- Tested the three builds properly: 1.23 triggered on demand (`echo $(whoami)` →
  decision_reason "Contains command_substitution"; rule / subcommandResults asks carry a TYPE but no
  text) and verified live end-to-end; spacing bug found by the user (note sat 8/0 on the preview) →
  `.card .card-h + .t-note`; 1.24 measured with `--max-turns 1` (result:null + errors[] — the error
  block now shows errors[] text); 1.21 unforceable (docs: safety-only, never on Fable/Mythos 5).
- §6, §9 and §12 closed by decision (9.7 = later + a `system/model_fallback` watch, fixture 65);
  §13 closed by building 13.2 as a SchemaStore-URL provider (nothing bundled) covering
  `settings.local.json`, which the IDE's own catalog misses — verifyPlugin Compatible ×7, user
  hand-checked in the sandbox (§17 MT-10.6). Sandbox restarted five times, each verified by content.
- End of session: harness **541/0** (fixtures to 65), `./gradlew test` **134/0**. Sections left:
  §8 (user's calls pending), §14 (all-➖ recommendation pending), §15. Committed and pushed.

## 2026-08-29 (second) — §11 closed: 11.5 decline ack, 11.6 declined, 🚫 mark retired
- Walked the user through 11.5 and 11.6 in plain language; they chose: 11.5 → answer `elicitation`
  with `{action:"decline"}` now, form deferred (backlog § Someday) until a server they use elicits;
  11.6 → declined. `ClaudeCli.handleControlRequest` gained an explicit `"elicitation"` branch;
  compileKotlin + `./gradlew test` green; harness not rerun (Kotlin-only, no webview frame).
- User asked the difference between ➖ and 🚫 → audit of all 27 closed rows found 7 drifting (9.6 /
  12.4 / 8.12 were decisions on ➖; 5.5 / 6.6 / 8.9 / 15.2 were deferrals on 🚫). User's call: ONE
  mark — ➖ "not implemented", the row body says terminal's-half / declined / deferred. Legend,
  [DECIDE] line, At a glance (`78 ✅ · 3 🟥 · 5 🟧 · 15 ⬜ · 27 ➖`), glossary and state updated.
  The By design / Declined / Deferred split still governs release PROSE.
- §11 Extensibility has no open row. One watch: 11.3's `ambient:true` filter is unmeasured —
  `window.__ambientSeen` in the console is the signal.
- Committed and pushed at the user's word at the end of the session.

## 2026-08-29 — 11.3 kill-a-task built and hand-tested; 11.4 measured and declined; §11 assessed
- 11.3 built in one pass: `stop_task{task_id}` (schema from the binary, field confirmed), hover-✕ on
  roster rows copying the `.hist-del` gutter idiom, `.stopping` dim, REPLACE-only removal. First
  CDP run: `sleep 240` on the roster → ✕ → roster empty in ~1s and the `sleep` pid gone.
- User hand-tested four scenarios (MT-7.8): shell; Explore sub-agent — the SUSPENDED turn resumed and
  finished (summary + Send back), the case that could have hung; one-of-two shells (survivor
  untouched); Escape + new conversation (the surviving shell died with the replaced CLI, no orphan).
  Findings: none. User declined a confirm step. Stopping-id memory pruned per roster frame after the
  test showed a killed id lingering.
- 2.1.250 roster schema grew `ambient` ("hosts should exclude from activity indicators") — filtered
  from roster + suspend count on the schema's word; user asked for measurement → first ambient task
  kept as `window.__ambientSeen` + console warning. Harness 490 → 513.
- 11.4 re-measured with a wire tape: an agent told to `exit 3` and reply FAILED ended
  `task_notification{completed}`. Lifecycle ≠ verdict; VS Code's `handleTaskNotification` only
  deletes from a map. Row → 🚫. Kill vocabulary drift found on the same tape (`task_updated{killed}`
  + `task_notification{stopped}`); `taskLine` now paints `stopped`; fixture 45 +1 step, negative
  control (`paused` must not paint) failed as required. Harness 514.
- Probe trap: the sub-agent's Bash raised the ordinary permission card on the parent and the probe
  waited 3 min for a notification that could not come until the card was answered (gotchas).
- 11.6 and 11.5 assessed (see state.md); user has not decided. 11.5's `{}` ack is schema-invalid.
- Tooling: `cdp.py` prints the `# target` line on stderr — piping through `tail -n +2` eats the
  first line of the JSON result (gotchas § Testing).

## 2026-08-28 (fourth) — 3.6 files-changed review built
- Probe first (`probe36.py`): `get_workspace_diff` answers a full git working-tree diff (stats +
  structuredPatch hunks per file) — but HEAD vs working tree, user edits included → documented in
  ide-mcp-protocol, not used. Baselines instead from the autosave PreToolUse hook (`Autosave.handle`
  gained a `snapshot` callback), settled at `result` in `CliFileSync.onTurnEnd` → `TurnChanges`.
- UI: `.files` line under `.done` (mockup + chat.css), whole line is the Review action live;
  replay draws it from `done.files` without the action. `DiffReview.openChain` = one
  `ChainDiffVirtualFile` with N read-only requests ("Before this turn"/"Now").
- Hand-verified with the user: two Edits in one turn (both pane-tweaked → both notes correct —
  I first misread the notes as a bug; the transcript showed the "v2" tweaks), the line read
  "2 files changed · README.md +1−0, test-code.js +1−0 · Review", the chain tab navigated
  README → test-code.js with the → arrow. Tests 130, harness 490 (fixture 60).
- Fixture 60's bridge tap failed only in the FULL run (a wrapper around whatever `__bridge` was at
  that moment); switched to fixture 48's tape-and-restore idiom with its own restore step.
- Replay fixture swapped to the full hand-test session cad0a74e (the 2-record fragment produced no
  done item, so `files` could not be pinned on it).
- Mockup caught up: tweak-travel card, files line, a still-running Bash (`.generating`) and two
  `.t-prog.run` examples. User asked how the card note's 6px was derived — it wasn't → `--attach-gap`
  (decision). Then "how many font sizes?" → 13/12/11/10 + three one-offs, all literals →
  `--fs-base/-sm/-xs/-2xs` tokens replace 63 literals (`.blk code .92em` → `--fs-sm`, 11.96→12px);
  computed sizes verified over CDP, harness 490 unchanged. Rule → conventions § Code & assets.

## 2026-08-28 (third) — 3.5 tweak-travel built; 3.6 analysed
- User asked for an analysis of 3.5/3.6, then "go ahead with the recommended way" (3.5 first).
- Measured VS Code 2.1.250: its tweak-travel is `rf(…,"single")` = a unified diff with 1e5 context →
  ONE whole-file hunk → `accept({old_string: whole file, new_string: whole pane})`; Write → content;
  MultiEdit goes to the card. Probed our stdio path headlessly (scratchpad `probe35.py`, session
  f63143c3): the CLI applied a whole-file updatedInput, one-line tool_result, transcript keeps the
  ORIGINAL tool_use, `toolUseResult` has what ran, `userModified:false` (= disk drift, not ours).
- Built: `EditProposals.tweakedInput/tweaked`, `DiffReview.open(current=)`, ChatPanel passes the
  final pane text as updatedInput + `__perm_answered{tweaked,oldStr,newStr}`, SessionStore item
  `tweaked`, `RenderLimits.TWEAK_NOTE` → `LIMITS.tweakNote`; JS: card redraw + note (85-cards),
  replay note under the structuredPatch card (55-replay). Tests 116→124 (EditProposalsTest ×6,
  SessionStoreTweakTest ×2 on the real transcript pair); harness 467→478 (fixture 59).
- Control run: stashed `webview/`, restarted, verified by content (`ev.tweaked` absent) → 7/4 with
  exactly the four discriminating asserts failing; pop, restart → 15/15. Trap: `LIM.tweakNote` is
  spliced from KOTLIN, so it is not a webview-build witness (first check passed on the stale
  page); also the `control.sh` runIde call BLOCKED on the gradle client (no detach from a script).
- 3.6 analysis: VS Code's session diffs come from a `file_updated` MCP notification to its
  in-process `claude-vscode` sdkMcpServer + a checkpoint store on load; `open_file_diffs` →
  `vscode.changes`. Ours would snapshot baselines in the Autosave PreToolUse hook and open a
  `SimpleDiffRequestChain`; resume case needs a `get_workspace_diff` probe. Not started.
- Hand test with the user, one step at a time: first attempt FAILED — "Failed to make diff.md
  writable": `DiffContentFactory.create(text)` is read-only; fixed with
  `DiffContentFactoryEx.createEditable`. Second attempt: pane editable, Accept → file had the
  pane text; card showed all 19 file rows (renderEditDiff on whole-file old/new) and could not
  fold (innerHTML swap kept foldBlock's stale verdict) → new `wholeFileHunk` (3-line context
  through patchRows), a NEW `.diff` element re-folded, `.card .t-note` caption margin. Third
  attempt: card correct; resume of the session drew the identical card. Harness 480.

## 2026-08-28 (second) — re-audit 2.1.246 → 2.1.250
- Both 2.1.250 builds were already installed (CLI under `~/.local/share/claude/versions/`, extension
  beside the still-present 2.1.246 dir). Method as before: `package.json` JSON-diff; `tool("…")`
  registrations for the IDE-MCP roster; `strings` diff of `subtype:"…"`; headless `initialize` on
  both binaries (scratchpad `probe.py`), field-by-field diff; string-literal diff of `webview/index.js`
  and `extension.js`; upstream CHANGELOG via WebFetch (2.1.249 has no heading; 2.1.250 = "bug fixes").
- Result: surfaces identical except roster +`/workflow-authoring` and four `@internal` `remote_*`
  cloud-worker subtypes. One [NEW] row, **1.25** grace banner — the CLI has emitted
  `rateLimitGraceActive`/`overageStatus` since ≤ 2.1.246; only the VS Code webview caught up
  (gate `tengu_lantern_sconce`). CLI 2.1.250 changed the `/model` confirmation copy to
  `Set model to …` — moot for us.
- `./gradlew test` now 116 (16.1 said 115). Row recount by mark matches At a glance (128).
- Gotcha: ugrep's `-oE '.{0,200}key.{0,200}'` fails "exceeds complexity limits" on the binary —
  use a python `re.finditer` window instead (gotchas § Testing).
- Changes left in the working tree; nothing committed.

## 2026-08-28 — four docs retired, the checklist reshaped, one detour reverted
- Doc audit → user deleted `docs/verifier-matrix.md` (parked), `docs/renderer-parity.md` (0 open),
  `docs/client-parity.md` (all 37 DONE/by-design) and `docs/manual-test.md` (self-contained,
  102/102 — the "92" in 16.5 undercounted). Knowledge promoted first: verifier DSL traps →
  gotchas § Build; eight deliberate live/replay divergences → gotchas § Replay; not-taken wire
  vocabulary → `ide-mcp-protocol.md` § 11; ~40 measured wire facts (agent-extracted from 1587
  lines, deduped by key against §9/§10/gotchas) → § 12; the manual-test items → checklist § 17
  (one `MT-n.m` line each, mapped to row ids, RESOLVED "how" + hard-to-trigger recipes kept).
  Every cross-reference re-pointed; each deleted doc has a `git show 9bd1683:docs/<name>.md` pointer.
- Learned: the "PhpStorm MCP" the CLI shows is OUR server (`bridge/IdeMcpServer.kt` names itself
  `"Claude Code <ideName> MCP"`); the CLI is the MCP client.
- **Detour**: a plan-approved bird's-eye restructure (section table + `<details>` headline per row)
  went through four one-symptom fixes (uniform ▶, indent, no duplicate bodies, body indent) and was
  reverted as "complete mess". Lesson → conventions § Docs: reformat = design task, show ONE
  section first. Also: marked needs a blank line after `</summary>` and around `</details>`.
- What the user DID like, plain markdown, sample-first: re-audit paragraphs each in its own
  `<details>` (title only in the summary); every §1–16 row in one shape (`**id** mark [effort]
  **Name** — gist; facts`, §1 approved before the other 103); an **At a glance** block (counts,
  🟥 Next up, [DECIDE] ids); §17 groups collapsed with counts; the six long ✅/🚫 rows (5.6, 9.2,
  9.4, 9.5, 9.9, 9.10) trimmed to name + behaviour with pointers to decisions/gotchas. 802 → 601
  lines, 229 ids in the same order.
- Committed and pushed at the user's request at session end.

## 2026-08-26 (third) — 0.11.1 goes out (saved 2026-08-27)
- **Released 0.11.1** (`979326c`, tag `v0.11.1`) — the effort slider's move into the model menu,
  the bare mode chip, the "Models" header. PATCH bump: a relocation adds no capability. Full
  `docs/release.md` run; gate held at step 6 with the complete notes and the 7-IDE verdict table.
- `verifyPlugin` Compatible ×7 (242.26775.23 → 262.10315.32), 0 warnings, verdict files read by
  path for 0.11.1; a mid-run peek at the log (verifier 1.410 "0.11.1 against PS-…") ruled out
  the 0.11.0 silent non-run before waiting on it.
- Asset 200 + `cmp`-identical; the raw feed served 0.11.1 with NO CDN lag; `marketplace-upload`
  green in ~5s, response `version "0.11.1"`, update id 1152867; API listed it after exactly
  300s (the standing ~5-min lag). User's dashboard screenshot: **Approved**, 242.0+, every
  compatibility check Success incl. the 2026.2.2 rc IDE-run check.
- Release notes used a **🧭 Changed** section instead of ✨ New / 🐛 Fixes (nothing was either)
  and surfaced the Fable thinking no-op under ⚠️ Notes — both offered at the gate, both accepted.
- Near-miss worth the gotcha: the feed-bump script asserted `count('0.11.0') == 2` when the
  string appears three times (version + twice in the URL) — it aborted AFTER build.gradle.kts
  was already written, leaving a half-applied bump that only the `git diff` review caught.
- Nothing unreleased on `main` after this save.

## 2026-08-26 (second) — the checklist meets 2.1.246, and nothing moved
- **`docs/feature-checklist.md` re-audited 2.1.241 → 2.1.246**, both references diffed rather than
  assumed: extension `package.json` contributions and the 12-tool IDE-MCP roster byte-identical
  (diffed against the `vscode/` 2.1.241 extraction — the old extension dir itself was already
  gone); CLI typed vocabulary +1 (`upload_device_hook_template`, @internal device-hooks); headless
  `initialize` on BOTH binaries: +`analytics_disabled`, 53→53 commands with no hint changes, same
  models/flags/agents/output styles/account keys.
- Header refs → 2.1.246; `[NEW]` legend → "added none"; new re-audit paragraph (newest first);
  §2 heading "unchanged through 2.1.246"; **new row 9.10 🚫** records the declined roster-flag gate
  in the register (126 → 127 rows, ids untouched).
- Side-finding worth acting on: the command roster is unchanged 2.1.233 → 2.1.246 across the two
  audits, so "sync `docs/slash-commands.md`" is a version-LABEL edit, not a re-verification.
- Two lessons promoted from the digested 2026-08-21 (second) entry into gotchas (compaction
  record order; gradle stream split + background cwd), plus the extension-dir-deleted trap.

## Digest
- **2026-08-26** — effort slider moved into `#modelFooter` (header "Models"); the level's chip suffix went bracket → middot → NOTHING (user: "better is to hide effort"; six candidates rendered as live `.chip-btn` nodes in `#inputbar`). Fixture 51 retargeted to `51-model-menu-effort-rail.json`, three complementary controls run. Article claim triaged: Haiku half REFUTED by the user's screenshot (no gating on capability flags), Fable thinking-switch INERT confirmed by probe — documented, not fixed. Harness 467, unit 116.
- **2026-08-25 (third)** — 0.11.0 released (`4c8899b`, tag `v0.11.0`): effort confirmation line, custom-model ✓/× fixes, /model-/effort resume parity. verifyPlugin ×7 — the FIRST attempt never ran (masked exit; verdict files still said 0.10.0 → the version in the verdict path is part of the check, gotchas § Build). Marketplace Approved incl. the 2026.2.2 EAP check.
- **2026-08-25 (second)** — model-menu polish: custom rows get the ✓ (fixture 57; `#inputbar` ID
  rules falsified two rounds of hand-derived offsets); chip `set_model` writes a `/model` trio to the
  transcript → hidden on resume (`cleanInjected`); `/effort` now shows the CLI's confirmation like a
  model change (fixture 58, supersedes the 2026-07-30 audit-trail acceptance). Harness 462, test 116.
- **2026-08-25** — 0.10.0 released (`v0.10.0`, `55fdcb1`): model-menu footer switches, 1M
  reconciliation, API-error single-render. `verifyPlugin` BEFORE the gate (7 IDEs Compatible) — the
  user interrupted to ask "was verify done?": keep the verdict table ready. Marketplace Approved after
  a few minutes' lag (same as 0.9.0).
- **2026-08-24 (second)** — model-menu footer shipped: 1M / fast / thinking switches (9.9/9.4/9.5),
  every protocol fact probed first (no 1M flag — `[1m]` is the marker; `set_model` never rejects;
  `apply_flag_settings{fastMode}` both ways; `set_max_thinking_tokens` 0/null live). User cut
  client-side validity logic and conversation markers. 1M switch reconciles from
  `result.modelUsage[].contextWindow`; API-error double-render deduped by exact text (hardened
  after "could this swallow a message?"). Fixtures 55/56 with four negative controls.
- **2026-08-24** — phantom Enter attributed to IJPL-161111 (fixed upstream; sandbox bumped to
  2024.2.6) — "it was not plugin but PHPStorm". Pending-plan replay honesty: `undecided` flag,
  abort-prefix filter, `stopForReplay()` gated on pending permissions after the unconditional
  wait slowed every reload. Context skill rebuilt around a briefing TIER (~41k → ~8k at load).
  0.9.0 released; `verifyPlugin` skipped then made mandatory (release.md 3b); an unasked
  commit reverted — authorization does not carry forward.
- **2026-08-23 (third)** — 5.6 polish committed (`92363ac`); live/replay anchor highlights share
  `highlightAnchors` (50-blocks.js) and carry an `occurrence` when a match is ambiguous. The
  phantom-Enter saga: three speculative guards on an unreproduced bug, one broke Enter, user
  ordered a full revert (lesson → conventions § Workflow; JCEF-OSR key-loop evidence and the
  `pkill -f runIde` orphan trap → gotchas).
- **2026-08-23 (second)** — plan-card comments shipped in the panel (`c0df900`): measured from
  VS Code screenshots + transcript (plans are files in `~/.claude/plans/`, comments ride the deny
  tool_result as `[Re: "anchor"] note` lines); user's call: full approve surface stays available
  with comments pending (VS Code collapses). Fixture 53 = 37 asserts over eight control runs;
  harness 361→398, gradle 109→113. Keyboard-only pill → backlog.
- **2026-08-23** — checklist re-audited 2.1.233 → 2.1.241: no new surfaces either side; what moved
  was measurement — `stop_task`, `side_question`, `apply_flag_settings{effortLevel,fastMode}`,
  `set_max_thinking_tokens`, `rename_session` all ANSWER over stdio (probes pre-paid for 8.11/9.4/
  9.5/11.3); `/clear` grew a `[name]` hint (7.6 decision opened); the missing 2.1.233 baseline was
  downloaded as a gzip-wrapped vsix carrying the CLI binary (runbook). Trap promoted: a binary's
  `new Set([...subtypes])` is the relay whitelist, not the stdio accept list (gotchas § Protocol).
- **2026-08-21 (second)** — replay drew "Conversation compacted" ABOVE its `/compact` bubble: the
  CLI writes boundary+summary at compaction END, physically before the command records — file order
  lies, timestamps don't. Fixed, then generalized as `DisplacedAnchor` (the retry-storm reorder
  refactored onto it, byte-identical by `probe --json` + cmp); +2 order tests with a neutered-guard
  control (107 → 109). Live-only "Distilled" footer / resume-only summary box KEPT as deliberate
  divergences (renderer-parity Audit 2). Lessons in gotchas (§ Replay, § Testing) and decisions.
- **2026-08-21** — "File not found" on a live path: `LocalFileSystem.findFileByPath` reads the VFS
  SNAPSHOT, not disk (not Windows-specific). Reproduced against the user's LIVE PhpStorm via its own
  MCP bridge using read-only `checkDocumentDirty`; fixed with `findVFileOnDisk` (refresh) on the two
  "open this path" callers only — read-locked callers must not refresh (deadlock). Lessons live in
  gotchas (VFS staleness, sandbox CDP port ≠ the port you asked for) and decisions 2026-08-21.
- **2026-08-19 (second)** — 0.8.0 released (`dce3600`: aliases, autosave hook, roster reload,
  close_tab, lock sweep; webview split rides unadvertised); verifier ×7 clean, Approved same day;
  plugin.xml description un-staled (web description stays hand-edited — user errand); Marketplace
  API lag seen a fourth time (the plugin PAGE is the truth); JetBrains' live IDE-run check debuts.
- **2026-08-19** — the webview split ships (`41f24f9`: chat.html → markup + 14 js files spliced by
  WebviewAssets, RenderLimitsTest asserts the ASSEMBLED page; controls run); "Very High" wrap fixed
  (JCEF-only flex base — nowrap on `.ef-label`); restart-on-update diagnosed as a download-cache
  race and ACCEPTED by the user; Kotlin nested-comment and Registry-port traps promoted to gotchas.
- **2026-08-17 (seventh)** — open-low mark 🟨 → ⬜ (user can't tell yellow from orange; their pick),
  lifting the register decision's own ⬜ ban with a decisions entry saying so. Load-time
  verification caught state.md citing 8.5/8.9/8.13 for rows that are 8.7/8.11/8.14 and "eight"
  [DECIDE] rows when there were nine → the re-derive-ids-from-the-register rule (conventions).
- **2026-08-17 (sixth)** — 7.7 aliases score like names in the / menu (+`canonicalCmd` before the
  allowlist gate) and 7.10 the roster survives reload (ChatPanel replays the newest raw
  `commands_changed` after the init seed). Negative control re-earned its keep: the first
  "discriminator" passed pre-fix (substring already ranked) and was re-expressed as /reset → /clear.
- **2026-08-17 (fifth)** — checklist 2.10/2.11: autosave moved onto the SDK hook lane
  (`PreToolUse Edit|Write|MultiEdit|Read`, the reference's own mechanism, no toggle) and stale
  `~/.claude/ide/*.lock` files are swept on every lock write, dead pid only — 15 corpses had
  accumulated because `delete()` only runs on an orderly dispose. Traps: `hook_callback` is a
  BLOCKING control request so every path must answer; PhpStorm saves on frame deactivation, which
  silently invalidates any "before Claude reads" test run by alt-tabbing out (both in gotchas).
- **2026-08-17 (fourth)** — the checklist re-audited against both reference clients on one version
  and 2.4 finished (`close_tab` closes ONE review by name, both close tools reply reference-exact).
  Ours had swept every diff, so with two proposals open, closing one resolved both.
- **2026-08-17 (third)** — **Released 0.7.2** (`40bc060`): / menu insert-vs-send rule + Effort
  label rail; patch bump, gate held at step 6, Approved same day.
- **2026-08-17 (second)** — the "7px that was never 7px": the probe page lacked the `#inputbar`
  ancestor so headless numbers were real for the probe page and wrong for the panel — align a
  probe page's ancestor chain with the real DOM (gotchas). Fixture 51 pins the ef-label rail.
- **2026-08-17** — slash-hint watch-item closed by measurement: bare `initialize` → 51 entries,
  keys `{name, description, argumentHint, aliases?}`, no `immediate` flag on the wire (the binary
  carries one — /goal has TWO records there, the wire sends the hintless one). state.md's 🟥 ids
  re-derived from the register after paraphrase drift (conventions).

- **2026-08-17** — fixtures 49+50 green (harness 337): all three findings came from RUNNING the
  negative controls (`3c86aa2~1` not HEAD-minus-session; assertions re-expressed through
  `data-takesarg` after a ReferenceError aborted the pre-fix run; per-step state reset after
  fixture 44's masking trap). Unasked 0.7.2 release prep reverted — the "release only when asked"
  convention entry. Port-9222-wins re-confirmed; builds verified by content.
- **2026-08-16 (fifth)** — effort label aligned to the mode rows' 20px icon rail (headless probe
  numbers later corrected: no `#inputbar` ancestor on the probe page); `chat.css.bak` control lied
  (file:// Chrome refuses non-.css) — renamed, showed the real −7. `cmdNeedsArg` → `cmdTakesArg`
  (any hint inserts) after `/context` ran bare on a click; skills ride the same roster as command
  files; composer sends on Ctrl+Enter.
- **2026-08-16 (fourth)** — `/model` on a NEW conversation looked struck through: `#fade-top`
  crossing the first block because `body.at-top` was only set by scroll/replay paths; fixed via
  `updateTopFade()` in `maybeScroll()` + `clearLogUI()`. **Released 0.7.1** (`91a6ba5`), Approved
  same day.
- **2026-08-16 (third)** — **Released 0.7.0** (`59d94fc`): plan feedback + split Approve, custom
  commands, 16 built-ins. Verifier Compatible ×7 without skip. Traps: read the verifier's
  verification-verdict.txt not the tail; quote Marketplace API URLs (zsh globs `?`).

- **2026-08-16 (second)** — plan-feedback field restyled mockup-first over three user iterations
  into one shared `--warn-field` token (`.plan-fb` + `.ask-other input`) plus a `.plan-sep`
  hairline removed with the input so live and replay agree; fixture 48 +5, harness 308. Five fresh
  2400×1520 Marketplace screenshots, rendered from the spliced chat.html in headless Chrome
  because JCEF OSR tiles its paints under an emulated viewport — that trap, the force-shown-popup
  positioning trap and the `\uXXXX`-escape patching trap all live in gotchas.

- **2026-08-16** — plan feedback ships: deny text = verbatim tool_result message; approve note
  appended under `PLAN_NOTES_MARKER` via `updatedInput.plan` (probed: a `feedback` field is
  schema-dropped, stdin steering races the model call); mode rows park in `pendingPlanMode` until
  the CLI's restore broadcast. The user's manual sweep beat the harness twice — the lesson lives in
  gotchas (plan-probe traps) and decisions (three 2026-08-16 entries).
- **2026-08-15 (third)** — three user reports: the bg chip was right (roster reset at the CLI
  boundary), the stuck popup highlight was not, and one file was being named two ways (card vs
  tool line) → paths project-relative + middle-ellipsised everywhere, absolute kept on
  `dataset.path`/`title`. `<local-command-stderr>` and mid-turn steered messages fixed the same day.
- **2026-08-15 (second)** — 16 built-ins enabled (user-picked set) and every one driven through the
  LIVE panel, not the headless smoke: `/context` rendered nothing (bare whole-message `assistant`
  frame, zero stream events → `msgStreamed`), `/security-review` dropped its `<local-command-stderr>`
  (string-content `user` frame). The fix then double-rendered thinking turns — the CLI emits an
  `assistant` frame PER CONTENT BLOCK — and fixture 47's own guard caught the over-correction within
  the hour. Lesson kept: tape the wire before blaming either side. Harness 256, Kotlin 103, register 0.
- **2026-08-15** — custom commands / skills / MCP prompts auto-enable in the / menu (3.1 + 9.10 →
  register 0 open). The approved Kotlin disk-scan plan died in Phase 0, correctly: the wire marks
  every custom entry with a " (project)"/" (user)" suffix, so a webview-only parse won. The CLI
  watches the PROJECT commands dir itself (~2.5s drop / ~1s delete); `~/.claude/commands` is not
  watched — a first probe conflated its push with `/reload-skills`' inside the debounce window.
- **2026-08-14 (second)** — 0.6.0 released + Approved (verifier Compatible ×7, asset identical,
  upload green in 14s). Two stale doc premises fell out of the run: `updatePlugins.xml` still said
  "no Marketplace", `overview.md` used the xmlId listing URL that 404s. Approval-lag lesson
  promoted to gotchas § Release.
- **2026-08-14** — `CliFileSync` + `Vfs.refreshFromDisk` shipped: edits land in open editors and
  new files appear without "Reload from disk". Scoping it to Write/Edit/MultiEdit was measurably
  not enough — the first real turn did both writes in ONE Bash call — so a turn-end root sweep
  covers what names no path. Verified through the plugin's own MCP bridge (`openFile` refreshes
  nothing, so it reports exactly what the VFS knows); 100 Kotlin tests with the negative control
  run, harness 217/217. Lessons in gotchas.
- **2026-08-13 (sixth)** — the in-flight gutter dot shipped (white/pulsing → green/red, `--dot-c`,
  `--pulse-period`, first `prefers-reduced-motion` block). Five real-panel bugs the green harness
  could not see, root cause: fixtures fed bare blocks in `#log` with no `.turn-body` — the harness
  gained a per-step `setup` hook. Halo, containment lift and the sub-task work-outcome dot all
  built and withdrawn the same day. Traps (launch ack, `<tool_use_error>`, containment) in gotchas.
- **2026-08-13 (fifth)** — 0.5.3 released + Approved; verifier Compatible on all seven branches
  242→262. Nearly shipped a stale zip (edit after `buildPlugin`) — settled by extracting the bytes
  and reading exact mtimes. Docs truth pass: README/release.md still said "not the Marketplace"
  two weeks after listing; six dead `CLAUDE.md` links. `verifier-matrix.md` was NOT stale — read
  a doc before "correcting" it.
- **2026-08-13 (fourth)** — `--block-gap` (18px) / `--attach-gap` (8px, user-picked from a
  side-by-side probe) replaced five drifted margins; the flex-gap-plus-margin vs collapsing-margin
  arithmetic is in gotchas. 29 pairs measured in real JCEF, negative control via `git show HEAD:`
  stylesheet injected into the live page.
- **2026-08-13** — the runIde manual sweep closed; the lesson that outlived it is that a fixture
  which never runs its own assertion is indistinguishable from a passing one (now in conventions).
- 2026-08-13 (third): title header lagged a whole turn — once-per-turn probe at message_start + seedUi() on every load; measured WHICH frame can carry the title (init too early); the negative control nearly ran against the fixed build (TaskStop left the sandbox alive) — lesson in gotchas.
- 2026-08-13 (second): 0.5.2 released + Marketplace-Approved via the automated workflow; the release session was never journaled and /context load's git-vs-journal diff caught it; machine drift caught again → state.md now leads with "check which machine".
- **2026-08-12 (fifth)** — the IN/OUT box took the diff's geometry: padding and overflow must sit on
  ONE element or the scrollbar insets; fixing that exposed three things the markers had papered
  over. All four traps are in gotchas.
- **2026-08-12 (fourth)** — three user reports; the third taught the method now in
  conventions ("do not fix what you cannot reproduce"): both busy-state defects reproduced by
  replaying REAL captured wire frames (kept at `_local/wire.jsonl` / `wire-short.jsonl`), which
  also killed the stated mechanism — the live stream sends NO user frame for a shell completion,
  so `message_start` is the only busy hook. Also: rename outside-click needed capture phase
  (every header control stopPropagations), and `"function"` moved DESC_KEYS→IN_KEYS.
- **2026-08-12 (third)** — the replay window kept the OLDEST blocks instead of the newest; fixed
  with an aligned cut at a turn boundary, plus the "N earlier blocks not loaded" top marker.
- **2026-08-12 (second)** — the reported rename bug did not exist (the CLI's own `ai-title` was
  overwriting the user's); the last manual release step died when marketplace-upload.yml went in.
- **2026-08-11/12** — UI-polish session: five user-reported defects fixed (tool-line path
  shortening, IN/OUT geometry, fold fade, history edge, permission card), then the plugin
  rename to `io.github.amitsidhpura.claude-brains`. Lessons promoted to gotchas/conventions.
One line per digested session; lessons were promoted to gotchas/decisions/conventions first.

- **2026-08-09 (seventh)** — 0.4.0 release prep, and the zip smoke test earning its keep on its
  first outing: playwright MCP "failed to start", the first real firing of the banner shipped three
  days earlier. Root cause was a GUI-session PATH without nvm — 0.3.3 had failed identically but
  SILENTLY (proven by an empty env-code diff, not assumed). The first fix, an `EnvironmentUtil`
  overlay, was a NO-OP on Linux because its shell loading is mac-only in the bytecode; the real one
  is `ShellEnv.kt` capturing `$SHELL -l -i -c "command env -0"` once per IDE run. Verifier clean on
  all seven IDEs, run three times as the APIs landed. Lessons in gotchas + the ShellEnv KDoc,
  including the literal NUL byte a heredoc put into a Kotlin char literal.

- **2026-08-09 (sixth)** — composer phantom spacing + delete-current-conversation. The 6px dead
  space above the composer was `#queue { display:flex }` outranking the UA's `[hidden]{display:none}`
  on specificity, so an EMPTY hidden queue still painted its margin; fixed by re-asserting
  `#queue[hidden]`, the `.chip-btn[hidden]` idiom (trap in gotchas). Delete-current-conversation
  shipped as leave-first: the refusal had existed because the CLI reopens the transcript per write,
  so ChatPanel now does the "new" reset, waits bounded off-EDT for the old process to actually die
  (`ClaudeCli.awaitExit(5s)` — `stop()` only SENDS the signal and a dying CLI can still flush a
  resurrecting write), then deletes and re-pushes the list.

- **2026-08-09 (fifth)** — the editor accept/reject v2 BUTTONS half, through four user-driven
  iterations each measured against 242 AND 262 bytecode before coding: toolbar icons (rejected on
  sight — unidentifiable), a top notification banner (wrong position, too loud), then the accepted
  shape, a plain bar UNDER the diff via `FileEditorManager.addBottomComponent`, polished to card
  parity (JButton client properties, because the LAF ignores setBackground; our own Lucide SVGs,
  because no platform icon is actually a tick). Added the COMBINED suggestion grant as a third
  button (`FILE_SAVED_ALL` — a permission-flow extension; the bridge verdict set stays the
  reference). Lessons all promoted: the GlobalMenuLinux launch noise, the `--`-in-an-SVG-comment
  XML failure that silently killed an icon, and `displayTextInToolbar()` having no warning-free
  path across 242→262.

- **2026-08-09 (fourth)** — 9.1 + 10.5 + editor accept/reject + 10.1/10.3, register 6 → 2 open,
  pushed as `f001e0b`. 9.1 needed both halves: live, the api_retry `error` is a five-code ENUM read
  out of the binary and the stream translator double-emits every retry (dedupe in chat.html); in
  replay, the CLI writes the concluding error record BEFORE flushing the buffered retries, so
  SessionStore reorders by timestamp. 10.5's premise was corrected by measuring the reference
  client — the IDE never writes on accept, the CALLER does — which produced the three-verdict
  DiffReview contract now in docs/ide-mcp-protocol.md § 4 and decisions.md, and the dual-surface
  edit permission (card + editor diff, first answer wins). 10.1/10.3 re-scoped to upstream policy,
  not a regression. Trap promoted to gotchas: `FileEditorManager.openFiles` does not report diff
  editors, so every find-then-close of a diff tab was a silent no-op.

- **2026-08-09 (third)** — 7.4 and 8.2+8.7 fixed, register 9 → 6 open. Both 7.4 payloads exist
  only live, so they were read VERBATIM out of the CLI binary — which also showed the harness
  envelope is longer than descMax, crowding the real summary off the line; fixed with a
  CONTENT-keyed `isInternalResult()` (RESULT_SKIP's name-keying could not express "this tool's
  COMPLETED result is still worth reading"). 8.2's phantom summary turned out to BE 8.7's root
  cause, so 8.7 closed with zero 8.7-specific code — measuring first is what found that. Fixtures
  07 + 08 each proven to pin the defect against pre-fix chat.html. Trap promoted to gotchas:
  assert on `#log`, never `document.body`, whose textContent includes chat.html's own script
  source and therefore the literals under test.

- **2026-08-09 (later)** — fixing round two, register 13 → 9 open, committed as `4a64433` +
  `fe620ef`. 4.4 live edit diffs built optimistically from the tool_use INPUT, because under
  acceptEdits the CLI never sends `can_use_tool` and the permission card had been the only live
  diff producer (MultiEdit's empty preview fell out of the same fix — multi-hunk edits were being
  approved blind); 5.9 plumbing strip and 5.14 scroll pin keyed off scroll DIRECTION; 6.4
  split-button caret wire-probed first, then hit two defects only live testing finds
  (`content-visibility` containment clipping the menu AND defeating synthetic-click assertions).
  Two lessons promoted: when live and replay disagree, establish WHICH is right before designing
  the fix; and copy a working idiom WHOLE (the conversations list's hover gutter is stable only
  because its panel is a fixed width).

- **2026-08-09 (first)** — the fixing session, register 19 → 13 open. All three webview keyboard
  chords removed (the plugin binds NO shortcuts); 1.7's Escape-reopen proved a SANDBOX artifact but
  two real Escape defects found instead; 2.8 @-mention menu had been opening INVISIBLY (zero CSS,
  so `position: static` ignored the viewport coords) and gained a full dismissal contract shared
  with the slash menu; 2.14 logged (JCEF-Linux Delete inserts 0x7F tofu); 7.3 bg chip fixed
  (`[hidden]` specificity defeat) and confirmed end-to-end against a real background task; 2.9
  drag-drop needed an AWT `DropTarget` layer because JCEF never delivers OS drags to the DOM. The
  spliced-chat.html headless harness was invented here — it is in gotchas and has been used in
  every session since.

- **2026-08-07/08** — the full 92/92 manual-test pass, 19 ISSUE notes logged. Hard-to-trigger states
  were manufactured, not skipped (network cut, auth failure, exit-2 hook, broken `.mcp.json`, CDP
  fixture injection); stitched synthetic sessions from real donor records exercised replay depth.
  Technique lives in gotchas; the two corrected beliefs (image chips, gauge on model switch) too.
- **2026-08-07** — `.claude/context/` initialized as the project's portable memory; the root
  `CLAUDE.md` (427 lines) migrated into it and deleted per the no-CLAUDE.md policy (last in git at
  `ee7e9fc`), global auto-memory folded into conventions.md, `.gitignore` un-ignoring `context/`.
