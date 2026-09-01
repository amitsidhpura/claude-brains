# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

## 2026-09-01 — the giant-yellow-note misfire found, fixed, hardened twice
- User's 2026-08-30 "yellow text with large output" (no screenshot) diagnosed by measurement:
  the `.t-note` caveat line — `resultNote`'s end-anchored regex captured from any literal
  `(note:` in tool output to a final `)`. All amber emitters enumerated; not the CLI 2.1.252
  "task notification" fix (that's API-payload-side; the panel never renders that content).
- Two guards, both renderers, each test-first with the control watched failing: `NOTE_MAX = 400`
  (over → DROP, not truncate — a slice of misread output is still misread) and position-0
  (every real note template in the 2.1.252 binary is APPENDED after text — three ` (note:`,
  Edit's escape-swap `\n(note:`). Second guard came from the user's own probe: a 3-line awk
  whose whole result was `(note: …)` ducked under the size bound.
- Real-wire before/after driven via `sendTurn(...)` over CDP through the live CLI: pre-fix build
  drew the ~1300-char amber wall, fixed build none; user's exact small repro also clean.
- Suites: `./gradlew test` 134, harness **592** (fixture 69, 6 asserts, provenance carries both
  control runs + the real-wire reproduction). `docs/limits.md` gained the caveat row with the
  full suppress-conditions list.
- Learned: sandbox-panel CLI sessions DO persist under `…-claude-brains-testing/` — the raw
  tool_result there confirmed the misfire shape byte-for-byte before any fix was trusted.
- Committed and pushed on the user's ask (this save included).

## 2026-08-30 (sixth) — 0.12.3 released
- User's "lets release it" → steps 1–5 proactively (bump, notes now 0.12.3/0.12.2/0.12.1, feed),
  `test buildPlugin` 134/0, zip audited, `verifyPlugin` 7/7 Compatible from the verdict files;
  STOPPED at the approval gate with the full notes; released on "Go ahead".
- `1277ad7`, tag `v0.12.3`, asset `cmp`-identical, feed served 0.12.3 immediately,
  `marketplace-upload` run 33302608925 green → Marketplace update 1157030, **Approved** within the
  hour (verifier Success on 2025.3.6.1 / 2026.1.5 / 2026.2.2 rc, IDE run no issues).
- Notes said plainly that 0.12.2's fix had made the flash happen on every open — the honest line
  the user approved unchanged.
- User tested the released update in the real IDE: working fine. Nothing unreleased on `main`.
- Stray `plugin/verify.log` from redirecting `verifyPlugin` output — deleted, never commit it
  (redirect into the scratchpad next time).

## 2026-08-30 (fifth) — first-paint flash fixed for real (0.12.2's fix had made it deterministic)
- User's real-IDE screenshot: the ~300×180 squashed frame STILL showing, now on every open. Root
  cause read straight from `4b62367`: `browser.component.isVisible = false` → an invisible
  `BorderLayout` child gets no bounds → CEF keeps its default surface → page loads small → shown
  and resized at `onLoadEnd`. The deferral was right; hiding the child broke it.
- Fix in `ui/ChatPanel.kt`: browser stays visible, `loadUi()` on the BROWSER component's first
  non-empty `componentResized`, `setPageBackgroundColor("#1a1a1a")` (API present in 2024.2.6's
  `JBCefBrowserBase`). Then, on the user's "is it optimized?", the `JPanel` wrapper + hide/show +
  `Color` mirror were removed — the diff vs pre-0.12.2 is one listener and one call.
- Verified each round: compileKotlin, `./gradlew test` 134/0, sandbox viewport 730×871 with body bg
  `rgb(26,26,26)` over CDP, harness 586/0; the first native frame itself cannot be seen over CDP —
  the user tested BOTH zips in the real IDE ("working fine"). Zip kept the 0.12.2 name (no bump).
- Process: the user wanted a zip to test BEFORE any release — no bump/notes/feed touched. Commit +
  push authorized with this save. Unreleased on `main` after it: this one Kotlin fix.

## 2026-08-30 (fourth) — 0.12.2 released
- User's "let's release it" → steps 1–5 done proactively (bump, notes now 0.12.2/0.12.1/0.12.0,
  feed), `test buildPlugin` 134/0, zip audited, `verifyPlugin` 7/7 Compatible from the verdict files;
  STOPPED at the approval gate with the full notes; released on the user's "go ahead".
- `50ac66c`, tag `v0.12.2`, asset `cmp`-identical, feed served 0.12.2 immediately,
  `marketplace-upload` run 33298655547 green → Marketplace update 1157011 (`approve:false` on
  upload, **Approved** within the hour — the public `updates` API lists approved versions only, so
  its silence right after upload is normal; read the run log's JSON instead).
- Notes carried a ⚠️ line for the behaviour change (CLI starts on first show of the panel).

## 2026-08-30 (third) — first-paint flash + dumb-mode placeholder fixed
- User's screenshot: on project open the panel paints for a fraction of a second at ~300×180
  (header + composer squashed top-left) then snaps to width. Cause: `ChatPanel` called `loadHTML`
  in its constructor, before `ClaudeToolWindowFactory` added the component to the tool window, so
  CEF laid the page out against its default surface (gotchas § JCEF).
- Fix in `ui/ChatPanel.kt`: `component` is a `JPanel(BorderLayout)` wrapper painted `PAGE_BG`
  (`#1a1a1a`, mirrors `--bg`), JCEF child starts hidden, `loadUi()` runs on the wrapper's first
  non-empty `componentResized`, the child is shown at `onLoadEnd` (invokeLater + revalidate).
  Side effect: the CLI spawns when the panel is first SHOWN, not at project open.
- Second screenshot: "This view is not available until indexes are built" — the platform's
  dumb-mode placeholder for a non-`DumbAware` factory; pre-existing, the deferred load just made it
  visible. `ClaudeToolWindowFactory` now implements `DumbAware` (gotchas § IDE platform).
- Verified: compile, viewport at load 730×871 over CDP (not the default surface), harness 586/0,
  `./gradlew test` 134/0, and the user's hands-on run: both the flash and the placeholder gone.
- Unreleased on `main` after this commit: these two Kotlin-only fixes.

## 2026-08-30 (later) — Marketplace screenshots 04/05 regenerated
- `design/marketplace/05-sessions-models.png` (effort pill slider replaced the five dots) and
  `04-commands-agents.png` (new side-question placeholder) re-rendered with
  `python3 tools/marketplace_shots.py 4 5` — no scene edits needed; the script composes from the live
  webview sources, so a UI change only needs a rerun.
- Observed, left as-is: at the 394 px panel width the side-question placeholder clips after "Enter"
  (`chat.html:51`); faithful to the IDE. Shorten the hint if the listing should read clean.
- Committed and pushed on the user's "commit and push".

## 2026-08-30 — re-audit 2.1.251 (early, user's ask); 9.11 built + hand-tested; SchemaStore lag
- Audit per runbook: 2.1.250 base downloaded from the Marketplace vsix (step 3 works); extension flat
  (contributions, case labels, 12 tools, gates); webview gained only a Remote Control pill; CLI: one
  @internal host-rejected subtype, `initialize.remote_control_available`, agents `model:"inherit"`,
  roster unchanged. Two `set_model` findings, both probed live: a `PreModelSwitch` hook (`source:"sdk"`)
  can REJECT it (→ 9.11), and the `Set model to …` echo is absent before the first turn.
- 9.11 built the same day: `ClaudeCli.setModel(model, onResponse)`, `ClaudeSessionService.revertModel`
  → `__model_rejected` → webview `showModel` (display half split out of `setModel`) + error block.
  Fixture 68 (control 6/6 discriminating fails on the free pre-change sandbox; live end-to-end with a
  deny hook via `--settings` headless and via the testing repo's `settings.local.json` in the
  sandbox). Harness 575→586, tests 134.
- User walked four hands-on steps (§17 MT-9.11): refusal + revert, accepted switch, refused restart
  re-apply (chip → roster head, error line survives into the new conversation), no-hook regression.
  Their screenshot CORRECTED the audit: the echo still draws after a turn — docs fixed.
- Traps: `compileKotlin` failed in 2 s (`json.encodeToString` in a class without a `json` — use
  `buildJsonObject{}.toString()`) and a port-poll waiter hid it: waiters must also grep `BUILD FAILED`.
  Fixture asserted `.blk` for what `errorBlock` emits (`.error`); running the harness in the same batch
  as a CDP injection `__clear`ed the evidence (known rule, re-learned).
- Settings-schema warning on `PreModelSwitch`: nothing bundled (13.2); SchemaStore is at 2.1.220.
  Options laid out (local VS Code copy / wait / bundle); user chose wait. URLs in state.md.
- Committed and pushed on the user's "commit and push" with the first save; then the user asked for
  the release: **0.12.1** cut per `docs/release.md` (verifier 7/7, asset byte-identical, feed live,
  upload run 33295856635 green, Marketplace Approved within the hour). Release notes drafted while the
  verifier ran; approval gate held. Post-release context save on its own ask (this one).

## 2026-08-29 (eighth) — Copilot Chat 0.63 audited and dropped; `vscode/` → `reference/anthropic-claude-code/`
- Landscape survey (web) → user asked for a Copilot extraction like `vscode/`. `github.copilot` is
  deprecated; Copilot Chat is BUILT IN to VS Code (`/usr/share/code/resources/app/extensions/copilot`,
  0.63.0); its OSS repo `microsoft/vscode-copilot-chat` stops at 0.44 (May 2026) and its bundled
  changelog at 0.41 — the shipped manifest is the only truth.
- Rearranged: `vscode/` moved under `reference/` as `anthropic-claude-code/` (2.1.251); `.gitignore`
  `/vscode/` line dropped (`/reference/` already covers it); eight path-only doc/context edits.
  Copilot folders were created, audited by an Explore agent (38 tools, 191+~330 settings, local
  Claude Code harness REMOVED between 0.44 and 0.63, reads `.claude/skills` + `.claude/settings.json`
  hooks), then deleted at the user's request. Report stayed in the scratchpad.
- User verdict: Copilot is bloat; of ten features offered only "terminal last command/output as
  context" survives → backlog. Everything else (worktree shape, model aliasing, per-phase models,
  OTel, etc.) not to be re-proposed on Copilot's evidence.
- Trap: `git mv -k` on an UNTRACKED path exits 0 without moving, so `|| mv` never fires — use plain
  `mv` for gitignored dirs (gotchas § Build).
- Checklist References block reworded: audited = 2.1.250; the extraction is "newest installed, the
  diff base for the NEXT audit" — the 2.1.251 number was read as an audit claim.
- Committed and pushed on the user's "commit and push" with this save.

## 2026-08-29 (seventh) — links → system browser; effort pill slider; side hint
- Side-question placeholder matched to the composer's ("Ctrl+Enter to send, Enter for newline") —
  the Enter behaviour was already identical, only the hint differed.
- Effort selector restyled as a pill slider from the user's screenshot (`.tgl` idiom, five stops).
  Four rounds: label stays "Effort **High**" + accent token (asked); knob inset 3→2px; fill = knob
  right + 2 so orange shows past the knob in EVERY state; stops made fixed 12px slots (tick = 4px
  `::before`) because a 12px knob among 4px flex ticks re-spaced the centres. Geometry proven in
  headless Chrome per round (sandbox was down), then in JCEF. Dead `background: transparent` cut.
- **Blank PhpStorm windows on link clicks** (user, "many times"): `target=_blank` on an OSR JCEF
  browser → CEF popup with no surface. Fix in three layers (JS delegate → `browse` frame,
  `onBeforePopup`, and — after the user's middle-click navigated the panel to jetbrains.com —
  `onBeforeBrowse` cancelling any main-frame http(s) load). Bare URLs autolink now.
  Recovery of the navigated panel: raw CDP `Runtime.evaluate history.back()` on the page target
  (cdp.py can't attach — it filters by the chat-panel title). Page reloads → log lost (8.14).
- Fixture 67: control 1 (JS stashed, Kotlin kept): 2/6 → all discriminating asserts failed;
  control 2 free against the running pre-auxclick page: exactly the two new asserts failed.
  Harness 566 → 575, tests 134/0. User hand-tested all three click routes.
- Sandbox now started/killed by Claude (user's go); `runIde` background task reports exit 1 when
  the IDE is killed by pid — expected, not a failure.
- Committed and pushed on the user's "commit and push" with this save.

## 2026-08-29 (sixth) — /clear removed (7.6 ➖); 8.7 + §14 decided; EVERY SECTION ✅
- GOAL REACHED a day early: `docs/feature-checklist.md` is 82 ✅ · 46 ➖ (128 rows), all 17
  section headings ✅, no 🟥/🟧/⬜, no [DECIDE]. Remaining wants live in backlog (worktrees bundle,
  tabs, debugger tools).
- 8.7 / §14: explained the relation (theme = undo vs isolation; TUI `/branch` fuses fork + worktree;
  rewind dry-run has a diff shape). User's principle: undo/branching depend on GIT (+ Local History),
  not Claude checkpoints → 8.7 no, 14.2/14.4 no (by design), 14.1/14.3 later as a worktrees bundle.
- 7.6 explained (A pass-name / B pin-pick-runs / C remove); user picked C. `65-slash.js`
  `CMD_NATIVE` → `{'btw'}`, `60-composer.js` native `/clear` branch deleted; typed `/clear`,
  `/new`, `/reset` now refused like `/model`. Docs: slash-commands row → Hidden, checklist 7.6 ➖,
  At a glance 82 ✅ · 1 🟥 · 2 🟧 · 2 ⬜ · 41 ➖, limits.md / ide-mcp-protocol.md / mockup `SLASH_ON` touched.
- Fixtures 46 and 52 re-pointed (row counts −1; 52's alias assertions moved from /clear to
  /code-review, `cmdKind('new')` → 'tui'); 36 and 50 unaffected (no /clear in their rosters).
- Verified: `./gradlew test` 134/0; sandbox restarted, build confirmed by content over CDP
  (`cmdKind('clear')` = 'tui', `CMD_NATIVE.size` = 1); harness **566/0**; live typed `/clear` → 0
  bridge sends + refusal status line.
- Gotcha hit again: a `cd` that fails inside a compound command leaves the shell cwd wherever the
  previous call left it — the first JS edit silently applied nothing; use absolute paths.
- Three optional items taken up: (1) `syncGutter` "lag" did NOT reproduce — catches up by the
  next rAF; recorded in gotchas § Testing, no code change. (2) `conventions.md` trimmed 161 → ~95
  lines, rules + pointers, stories stay in gotchas. (3) Marketplace text: plugin.xml description
  fixed (`/clear` → `/btw`, stale "Diffs live in the chat" bullet dropped — 2.2/3.5/3.6 open real
  diff tabs). A paste-ready Markdown copy was added then REMOVED: the user had set the Marketplace
  to take the description from the plugin for future releases, so release.md's "hand-synced" note
  was the stale premise; corrected, verify on the next upload.
- **Marketplace screenshots reshot** (user: "outdated"): new `tools/marketplace_shots.py` (kept in the
  repo — the 2026-08-16 script died with its scratchpad) splices chat.html exactly as `loadUi` does,
  drives five two-panel scenes through the real builders in headless Chrome at 2400×1520. Traps hit:
  manifest parse truncated at a `)` inside a comment; the pinned user message hides overflow under
  it (thinking block, ask tabs) — trim scenes to fit; popups need a fixed clamp at 394px. Files:
  01-conversation, 02-plan-and-questions, 03-control, 04-commands-agents, 05-sessions-models
  (02-plan-approval / 05-sessions-modes removed). Upload is the user's manual step.
- User: screenshots go up with the next release. Committed and pushed (`d886879`, `a0cec7b`).
- Later: `vscode/` re-extracted to 2.1.251 (re-audit deferred a few versions by the user); gist
  verified identical to SKILL.md; change notes trimmed to the LAST THREE versions + GitHub releases
  link (user's call — the block had grown to 14).
- **Released 0.12.0** on the user's go: bump + notes, tests 134/0, `buildPlugin`, zip audited,
  `verifyPlugin` 7/7 Compatible (verdict files read), approval gate with the full notes, commit
  `0e1af47` + tag, GitHub release with byte-identical asset, feed live, `marketplace-upload` green.
  Marketplace moderation pending at save time. User's manual steps: screenshots + description check.

## Digest
- **2026-08-29 (fifth)** — 8.14 (page-reload transcript heal) declined by the user: `refresh`/reopen covers the renderer-crash case; checklist 8.14 ➖, Next up narrowed to 8.7.
- **2026-08-29 (fourth)** — §15 closed (15.5 debugger tools → backlog [LG], 15.6 by design), 8.8/8.10 deferred; 8.11 side question measured FIRST (live probe: no `/btw` in roster, `control_request_progress{started}` → `control_response{response, synthetic}`, nothing persisted) then built mockup-first; fixture 66's free pre-feature control ran 21/23 red; side input one line at rest, panel centred on `#inputcard`. Harness 566, tests 134.
- **2026-08-29 (third)** — section marks (`## N. ✅|⬜`, one glyph, no counts) + the 2026-08-30 all-✅ goal; 1.21/1.23/1.24 built, 1.22 ➖ (`tool_progress` measured absent on stream-json), 1.25 ➖; §6/§9/§12 closed by decision, §13 via the SchemaStore-URL provider (nothing bundled) hand-checked; 1.23's 8/0 spacing bug → `.card .card-h + .t-note`. Harness 541, tests 134.
- **2026-08-29 (second)** — §11 closed: 11.5 `elicitation` answered `{action:"decline"}` (form deferred, backlog § Someday), 11.6 declined. 🚫 mark retired after 7 of 27 closed rows drifted between ➖/🚫 — ONE mark ➖, the row body says terminal's-half / declined / deferred; the By design / Declined / Deferred split still governs release prose.
- **2026-08-29** — 11.3 kill-a-task built (`stop_task{task_id}`, hover-✕ via the `.hist-del` idiom, REPLACE-only removal) and hand-tested ×4 incl. a suspended Explore sub-agent resuming; stopping-id memory pruned per roster frame. 2.1.250 roster `ambient` filtered, `window.__ambientSeen` as the measurement hook. 11.4 declined on a wire tape (`task_notification{completed}` for a FAILED agent — lifecycle ≠ verdict); kill vocabulary drift (`killed`/`stopped`) fixed, fixture 45. Traps: a sub-agent's Bash raises the parent's permission card and a probe waits on it; `cdp.py` prints `# target` on stderr so `tail -n +2` eats the first JSON line. Harness 514.
- **2026-08-28 (fourth)** — 3.6 files-changed review built: baselines from the autosave PreToolUse hook (`Autosave.handle` `snapshot` callback) → `TurnChanges` at `result`; `get_workspace_diff` probed and documented but NOT used (HEAD vs working tree, user edits included). `.files` line + `DiffReview.openChain`. Fixture 60 tap uses fixture 48's tape-and-restore idiom (a wrapper around whatever `__bridge` was at that moment fails in the FULL run). Font-size literals (63) → `--fs-*` tokens, card note 6px → `--attach-gap` (conventions § Code & assets). Tests 130, harness 490.
- **2026-08-28 (third)** — 3.5 tweak-travel built (VS Code = whole-file `accept({old_string, new_string})`; probed our stdio path: CLI applies a whole-file updatedInput, transcript keeps the ORIGINAL tool_use, `userModified:false`). `EditProposals.tweakedInput`, `DiffReview.open(current=)`, `RenderLimits.TWEAK_NOTE`. Hand test failed first on read-only `DiffContentFactory.create` → `DiffContentFactoryEx.createEditable`; whole-file card could not fold → `wholeFileHunk`. Trap: `LIM.tweakNote` is spliced from KOTLIN so it is no webview-build witness; `control.sh`'s runIde BLOCKED on the gradle client. Tests 124, harness 480.
- 2026-08-28 (second) — re-audit 2.1.246→2.1.250: only new row 1.25 (usage-limit grace banner, later deferred); `/workflow-authoring` joined the roster; four @internal cloud-worker subtypes ignored; runbook's re-audit procedure extended.
- **2026-08-28** — four docs retired (`verifier-matrix`, `renderer-parity`, `client-parity`, `manual-test`; `git show 9bd1683:docs/<name>.md`), knowledge promoted first (gotchas § Build/§ Replay, protocol § 11/§ 12, checklist § 17). Bird's-eye checklist restructure reverted as "complete mess" → conventions § Docs (reformat = show ONE section first); the accepted shape: `**id** mark [effort] **Name** — gist`, At a glance block, re-audit paragraphs in `<details>`. 802 → 601 lines.
- **2026-08-26 (third)** — 0.11.1 released (`979326c`): effort slider into the model menu, PATCH bump. verifyPlugin ×7 read by path; feed had no CDN lag; Marketplace Approved. Notes used a 🧭 Changed section. Near-miss: the feed-bump script asserted after writing build.gradle.kts (gotchas § Build).
- **2026-08-26 (second)** — checklist re-audited 2.1.241 → 2.1.246: contributions and 12-tool roster identical, CLI +`upload_device_hook_template`, `initialize` +`analytics_disabled`; row 9.10 🚫 added. Roster unchanged 2.1.233 → 2.1.246, so "sync slash-commands.md" is a label edit.
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
