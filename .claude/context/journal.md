# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

## 2026-08-19 (second) — 0.8.0 goes out
- **Released 0.8.0** (`dce3600`, tag `v0.8.0`): aliases (7.7), autosave hook (2.10), roster
  across reload (7.10), per-review close_tab (2.4), lock sweep (2.11), effort-label fix — plus
  the webview split riding along unadvertised (internal, per release.md). Minor bump: features.
- Full `docs/release.md` run, gate held at step 6 until the user read the complete notes.
  Verifier Compatible ×7 (242 → 262), 0 warnings; zip carried all 14 `webview/js/` files and the
  baked 0.8.0 notes; asset 200 + `cmp`-identical; feed serving 0.8.0 within a minute; upload run
  32276262660 success (11s); **Approved the same day** (sixth in a row).
- `plugin.xml` description un-staled in the same release: "sixteen built-ins … plus auto-enabled
  custom commands, skills and MCP prompts" replaces "/compact + /clear only". The Marketplace
  WEB description still carries the old wording — hand-edited Markdown, uploads don't touch it;
  left with the user (now in state.md next steps).
- Marketplace API lag showed a fourth time: `updates?size=2` still named 0.7.2 newest after the
  successful upload; the plugin page showed 0.8.0 Approved. The page is the truth.
- New on the version page: alongside the verifier ladder, JetBrains ran a live "IDE run with the
  plugin installed" check on 2026.2.1 — "No issues occurred". The split webview loaded clean in
  their environment too.

## 2026-08-19 — chat.html becomes fourteen files, and "Very High" gets its line back
- **The webview split shipped** (`41f24f9`): chat.html (4542 lines, one 4400-line `<script>`) is
  markup-only at 131 lines; the JS lives in `webview/js/` as 14 numbered files cut at the existing
  section banners, spliced back into ONE script by new `ui/WebviewAssets.kt` at a `<!--JS-->`
  marker. Assembled page verified byte-identical (banners aside) — no JS was hand-edited, a
  python script did the cuts at exact line numbers. `RenderLimitsTest` now asserts over the
  ASSEMBLED page (raw chat.html would have made every content check vacuous) plus manifest ⇄
  directory equality; negative control RUN (broken marker + dropped manifest entry → 4
  discriminating failures, incl. AUTH_BLOCKED as a bonus). gradle test 107, harness 356, then a
  7-step manual ladder by the user in the sandbox touched every file, replay path included.
- Kotlin trap found: block comments NEST, so the literal glob `js/*.js` inside a KDoc opened a
  comment that never closed ("Unclosed comment" at file end). In gotchas now.
- **Effort-label wrap fixed** (`e06add1`): user screenshot showed "Very High" on two lines in the
  mode-menu footer with the row half empty. Measured over CDP: JCEF gives a text flex item a base
  a hair under max-content (53px vs 55) — desktop Chrome doesn't, so design/mockup.html could
  never reproduce it. One declaration (`white-space: nowrap` on `.ef-label`); fixture 51 gained a
  step; the user's still-running sandbox predated the CSS edit, so it WAS the pre-fix build and
  the negative control ran against it honestly (bH 37 fail → restart → 19, 12/12). Baseline 361.
- **"Why does PhpStorm ask for a restart on update?"** answered from the real IDE's idea.log
  (2026.2.1 snap): the 0.7.1 no-restart install crashed on `NoSuchFileException` for
  `~/.cache/JetBrains/PhpStorm2026.2/plugins/claude-brains.zip` at unpack (a download-cache race —
  two of our updates 39 min apart that day) and fell back to install-on-restart. Not plugin.xml
  (no require-restart, all EPs dynamic); and a hot-swap would have to unload live JCEF + the WS
  server + the claude process anyway. User: "I am ok with it" — accepted, not to be engineered around.
- Sandbox port gotcha reconfirmed: `-PjcefDebugPort=9223` was on the JVM cmdline, but the
  hand-set Registry value kept CDP on 9222 — found by probing both ports by content.
- Next: unchanged — the nine [DECIDE] rows; 9.4 fast-mode toggle is the cheapest 🟥. Plus a
  one-off `./gradlew test` on the Windows box to settle the CRLF splice path.

## 2026-08-17 (seventh) — the low tier stops looking like the medium one
- **Session was a `/context load` briefing plus one mark change.** `docs/feature-checklist.md`'s
  open-low mark went 🟨 → 🟦 → ⬜ (the user could not tell yellow from orange; blue was my pick,
  ⬜ was theirs). 16 rows, the status-mark key, and the scope-rule line. Mark set now
  ✅ / 🟡 partial / 🟥 / 🟧 / ⬜ / ➖ / 🚫.
- The 2026-08-17 register decision had explicitly banned ⬜ ("reads as not started"); the new
  decisions entry lifts that ban for the low tier and says so, so a later session does not
  "correct" it back. 🟨 left untouched in this journal and in the superseded decisions entry —
  history stays as written.
- **Load-time verification caught four drifts in `state.md`**, all fixed in this save: the 🟥 list
  cited 8.5 / 8.9 / 8.13 for rewind-fork, side-question and reload-log-replay, but those ids are
  **8.7 / 8.11 / 8.14** (8.5 is ✅ Delete conversation, 8.9 is 🚫 tabs, 8.13 is ➖
  `generate_session_title`); and the [DECIDE] rows number **nine**, not eight. The checklist was
  right every time — `state.md`'s summary had been written from memory.
- Standing lesson, cheap here and expensive later: when `state.md` paraphrases a numbered
  register, re-derive the ids from the register at load, never copy them forward.
- Context files are well over the retention targets (`decisions.md` 761 lines, `gotchas.md` 729,
  targets ~100). Not addressed; flagged for a consolidation pass.
- Next: unchanged — the nine [DECIDE] rows, then 9.4 fast-mode toggle [SM], the cheapest 🟥.

## 2026-08-17 (sixth) — aliases become names, and the roster survives a reload
- **7.7 shipped**: aliases score like names in the / menu filter, ride the row muted, and a typed
  alias is canonicalised before the allowlist gate (`canonicalCmd`). Motivation from the roster
  itself: `/review`, `/peers`, `/reset`, `/new` are what the CLI advertises, and a typed `/review`
  was being refused as "not available in the IDE".
- **The negative control earned its keep again**: the fixture's first "discriminator" — typing
  `review` puts /code-review first — PASSED on the pre-fix build, because `review` is a substring
  of the name and already ranked 2 above compact's description-only 3. Re-expressed as `/reset →
  /clear` (in neither name nor description pre-fix). One expression also threw on null on the
  pre-fix build and aborted the run — every row is null-safe now (the 2026-08-17 lesson, relearned
  cheaply). Final control 7 fail / 5 pass; fixed build 12/12; whole harness 356/356.
- **7.10 shipped**: ChatPanel caches the newest raw `commands_changed` frame and replays it after
  the init seed on every page load. Verified live without a fixture, over CDP: drop
  `reload-probe.md` into the testing repo's `.claude/commands/` (the CLI watcher pushed within
  ~6s), assert roster 54 + badge, `location.reload()`, assert again. Pre-fix the reload replayed the
  53-entry initialize roster.
- Control build via `git stash push -- chat.html chat.css` → runIde → harness → `stash pop` →
  runIde: two IDE launches, cheaper than building a pinned commit, and honest because ChatPanel's
  Kotlin change is orthogonal to what fixture 52 asserts.
- Trap noted: `pgrep -f 'PhpStorm-2024.2/jbr/bin/java'` matches the pgrep-launching shell itself
  (its command line carries the pattern) — read the pid's cmd before concluding "still running".
- Next: the eight [DECIDE] rows; 9.4 fast-mode toggle is the cheapest 🟥.

## 2026-08-17 (fifth) — 2.10 and 2.11: the hook lane opens, and the locks get swept
- **Autosave-before-read/write shipped as a host-registered SDK hook.** Read out of extension.js
  2.1.233 first: VS Code's `claudeCode.autosave` (default on) is `saveFileIfNeeded`, a `PreToolUse
  Edit|Write|Read` callback that saves a dirty document. Our stream-json CLI takes the same thing:
  `initialize` accepts `hooks:{Event:[{matcher,hookCallbackIds}]}` (schema pulled from the CLI
  binary; validated strictly) and then sends `control_request{subtype:hook_callback}` per matching
  call. Until now that arm fell into the generic empty-`{}` ack. New `Autosave.kt`; the reply goes
  out from the EDT after `saveDocument`, `{continue:true}` on every path.
- **Measured before believing, twice.** Headless probe (`probe_hook.py`, scratchpad): initialize
  with hooks → success; `hook_callback id=autosave event=PreToolUse tool=Read path=…` arrived
  before the Read ran. Then live in `runIde`: unsaved `ZEBRA-43` buffer, `Read` returned ZEBRA-43,
  `idea.log` shows `autosaved before Read` at 22:01:07.320, file mtime 22:01:07.313.
- **Two contaminated runs before the clean one, both instructive:** (1) PhpStorm's own
  "save on frame deactivation" wrote the buffer the moment the user alt-tabbed to report back, so
  disk and buffer agreed before any tool ran — the test must stay inside the sandbox until the
  answer lands; (2) the model chose `Bash cat` over the `Read` tool, which no PreToolUse
  file-matcher can see — pin the tool in the prompt ("Use the Read tool — not Bash"). Neither is a
  defect; both are in gotchas.
- **Stale-lock sweep** (`IdeLockFile.sweepStale`, on every write): the CLI's own rule (dead pid →
  unlink) exists in the binary but only runs when it ENUMERATES IDEs, which `--mcp-config` never
  triggers — so nothing had ever cleaned up. 17 locks → 2 on the first sandbox start.
- `runIde` hit the known `teamcity.jetbrains.com` config-cache eviction (2m20s "Connection timed
  out") — `-PskipVerifierIdes` cleared it, exactly as gotchas § Build says.
- Next: the eight remaining [DECIDE] rows; 9.4 fast-mode toggle is the cheapest 🟥.

## 2026-08-17 (fourth) — the checklist re-audited, and 2.4 finished
- **`docs/feature-checklist.md` rewritten** against VS Code 2.1.233 + CLI 2.1.233 (installed here;
  the file had been on 2.1.220/222). Sources: `package.json` contributions, the `case"…"` host-
  message vocabulary grepped out of `extension.js`, the `name:"…",description:"…"` command records
  out of the CLI binary, and our own docs/context. Stale rows found: editor accept/reject listed ⬜
  (shipped 2026-08-09), bypass-by-relaunch (removed 2026-08-03), no plan-feedback/split-approve, no
  effort slider / gauge / rename / delete; whole families we built (queue, bg roster, todo,
  compaction, retries, hooks, CliFileSync, ShellEnv…) absent. IDE-MCP tool set unchanged at 12.
- New in 2.1.233 worth a row: `side_question`, session groups + a sessions sidebar view,
  `set_thinking_level`, `set_focus_view`, `primaryEditor.open`, `open_file_diffs`, git host actions,
  `ask_debugger_help`, `get_terminal_contents`, speech-to-text. Each carries **[NEW]** and a take.
- The user iterated the FORMAT six times, each a one-line ask: no `[x]` beside the emoji (redundant);
  fold the two summary lists into rows as tags; move the key to the top; uncluttered header (two
  small tables); ⬜ → 🟥🟧🟨 by importance; `[XS/SM/MD/LG]` effort after the mark; `section.row`
  numbers. Ended at 124 rows, header-only meta, ids declared stable.
- **2.4 `close_tab` finished**: it had swept every diff tab (`closeAllDiffTabs()` under the hood).
  Now per-review by the `tab_name` given at open; both close tools reply with the reference's
  exact strings (read from extension.js: `tab.label === tab_name` → `TAB_CLOSED`;
  ``CLOSED_${n}_DIFF_TABS``). Verified live in `runIde` over MCP-over-WS (websockets, subprotocol
  `mcp`, auth header from the LIVE lock — 16 stale locks were sitting in `~/.claude/ide/`, picked
  by `os.kill(pid,0)` + workspaceFolders): close_tab ALPHA left BETA pending until Accept;
  closeAllDiffTabs resolved both + `CLOSED_2_DIFF_TABS`. Probe recipe: two `tools/call openDiff`
  with distinct `tab_name`, sleep, one close call, print verdicts as they land.
- Tooling notes: `./gradlew runIde` returned "BUILD SUCCESSFUL in 7s" while the sandbox JVM kept
  running (verify by `pgrep` + jar contents, not by the Gradle exit); a heredoc python one-liner
  over the 15KB `package.json` timed out at 120s once for no reason found — writing the script to
  the scratchpad and running it under `timeout` worked first time.
- Next: the ten **[DECIDE]** rows; then backlog order.

## 2026-08-17 (third) — 0.7.2 goes out
- **Released 0.7.2** (`40bc060`, tag `v0.7.2`): the / menu insert-vs-send rule and the Effort
  label rail. Patch bump — two fixes, no features. Full `docs/release.md` run with the gate held
  at step 6 until the user had read the complete notes.
- 106 tests, verifier Compatible ×7 (242→262, no warning files), zip clean, notes baked into the
  shipped plugin.xml, jar resources confirmed to carry both fixes. Asset 200 + `cmp`-identical,
  feed serving 0.7.2, upload run 31967154720 success, **Approved the same day**.
- The Marketplace API lag showed again: `updates?size=3` still named 0.7.1 as newest-approved
  minutes after a successful upload, while the plugin page already showed 0.7.2 Approved. Third
  time this has been observed (recorded 2026-08-14) — check the page, not the API.
- Notes followed the UPDATE-release shape (theme line, two punchy lines, one paragraph, 🐛 Fixes /
  📥 Install / ⚠️ Notes). Ctrl+Enter is mentioned explicitly because the insert leaves the caret in
  the composer and the change only reads end-to-end with the send key named.
- Tooling note: `./gradlew verifyPlugin` needs a Bash timeout above the 120s default — the first
  attempt was cut off mid-run at exactly 2 minutes.
- Next: backlog order — plan-card keyboard shortcuts, reloaded-webview log replay,
  kill-background-process from the panel.

## 2026-08-17 (second) — the alignment fixture, and the 7px that was never 7px
- **Fixture 51 `mode-menu-effort-rail`** (7 assertions) closes the last uncovered thing: the
  Effort label's rail. Opens the menu by CLICKING THE CHIP (a force-shown popup skips tg()'s
  positioning) and closes it in the same setup. Harness **344/344**.
- **Its control corrected the fix's own story.** The real pre-fix misalignment was **4px**, not
  the 7px reported on 2026-08-16: `#inputbar svg { width:18px; height:18px }` is an ID rule that
  beats `.ef-label svg {15px}` AND `.pi-ic svg {17px}`, so composer icons are all 18px and the
  headless probe — a page without the `#inputbar` ancestor — measured a cascade that does not
  exist in the panel. The fix is still right, and works for a reason worth knowing: `flex-basis`
  is the one sizing property that ID rule does not set. Promoted to gotchas; chat.css comment and
  state.md corrected.
- **The slash-hint watch-item is closed by measurement.** A bare `initialize` control request to
  CLI 2.1.233 returns 51 entries keyed exactly `{name, description, argumentHint, aliases?}` —
  no `immediate` flag on the wire though the binary carries one. Of the 15 enabled commands six
  insert (`/compact` `/context` `/code-review` `/simplify` `/loop` `/batch`), nine act on click.
  This overturned the earlier `strings` reading: /goal has TWO records in the binary and the wire
  sends the hintless one. Table now in docs/slash-commands.md; fixture 50 re-cited to the wire
  probe and its control re-run after the edit.
- Release still not cut, by the user's call: 0.7.1 remains what users have.

## 2026-08-17 — the controls earn their keep (continues the 2026-08-16 fifth session)
- **Fixtures 49 + 50 written and green**: `49-top-fade-at-top` (10 assertions, 3 steps) pins the
  fade fix released in 0.7.1; `50-slash-pick-insert-vs-send` (19 assertions, 4 steps) pins
  `cmdTakesArg`. Full live harness now **337/337** (was 308).
- **Both negative controls RUN, and all three findings came from running them**, not from writing
  them: (1) the first control used HEAD-minus-this-session, which still had the fade fix — the
  real control is `3c86aa2~1`; (2) three assertions called `cmdTakesArg` directly and the
  ReferenceError aborted the entire run on the pre-fix build, so they were re-expressed through the
  rendered `data-takesarg` attribute plus two new roster entries; (3) fixture 50 masked its own
  second half — the pre-fix build left `busy` true after step 2's send, so step 3's click was
  QUEUED and two guards failed for an unrelated reason (fixture 44's trap, now reset per step).
  All three promoted to gotchas.
- An opacity assertion on `#fade-top` was flaky until it outwaited the .12s transition — it failed
  in a different step on each run. 250ms promise; three consecutive 10/10 runs after.
- Re-confirmed the port trap: `runIde -PjcefDebugPort=9223` lost to the sandbox's own Registry
  value and the panel came up on 9222. Every build swap this session was verified BY CONTENT
  (`typeof cmdTakesArg`, `maybeScroll.toString().includes('updateTopFade')`, `.ef-label` gap)
  before being driven.
- **Release prep was started and then reverted, unasked** — version bump, changeNotesHtml and
  `updatePlugins.xml` all went to 0.7.2 before the user pointed out they had never asked for a
  release. Reverted to 0.7.1, and the 0.7.2 build artifacts were deleted at the user's request.
- `docs/slash-commands.md` still described the OLD pick contract ("runs it immediately … to pass
  an argument, type the command by hand") — corrected. Sole occurrence; the code comment was fine.
- Next: both fixes are on main but UNRELEASED — 0.7.1 is still what users have.

## 2026-08-16 (fifth) — two small alignments: one of pixels, one of intent
- **Effort label aligned with the mode titles.** A mode row puts its title at 14px padding + a
  20px `.pi-ic` slot + a 10px gap = 44; `.ef-label` was 14 + a bare 15px svg + 8 = 37. Fixed in
  chat.css by giving the svg the same rail: `gap: 10px` + `flex: 0 0 20px` while `width: 15px`
  keeps the glyph its size, so it centres on the mode icons' axis. Measured headless: delta
  −7 → 0, icon slots both `[15→35]`. CSS-only, so `design/mockup.html` (which links the same
  sheet) needed no mirroring. **CORRECTED 2026-08-17:** those headless numbers are real for that
  probe page but NOT for the panel — it had no `#inputbar` ancestor, so `#inputbar svg {18px}`
  never applied. In the real panel the gap was 4px and the icons are 18px, not 15 (see the
  2026-08-17 second entry and gotchas).
- **The negative control lied first.** The pre-fix sheet was copied to `chat.css.bak`, which
  `file://` Chrome refuses to load as CSS — the unstyled page measured a perfect 0 delta. Renamed
  to `.css`, the control showed the real −7. Promoted to gotchas.
- **Slash-menu picks now insert whenever a command takes an argument** (user report: clicking
  `/context` ran it bare instead of letting them type `save`). `cmdNeedsArg` → `cmdTakesArg`;
  rationale and blast radius in decisions.md. Probed the CLI binary for the built-ins' hints —
  and learned there is a built-in `/context` (`[all]`) that this repo's project skill shadows.
- Answered two questions along the way: skills ride the SAME roster as command files (no type
  field on the wire — only the `" (project)"/" (user)"` description suffix tells them apart, which
  is why the skill wears a `project` badge), and this composer sends on **Ctrl+Enter**, not
  Shift+Enter.
- Both fixes verified by the user in `runIde`. Nothing released — 0.7.1 remains the shipped
  version; these ride the next one.

## 2026-08-16 (fourth) — the fade that never learned it was at the top
- User report (screenshot): `/model` on a NEW conversation rendered "Set model to …" greyed and
  apparently struck through. Not a text style — `#fade-top`'s 18px-solid → transparent band
  crossing the first block. `body.at-top` (which hides the fade at `scrollTop <= 1`) was set only
  by the scroll handler and the two replay paths; a fresh empty log fires no scroll event and has no
  sticky `.msg-user` to ride above the fade when the first block is a local command's stdout.
- Fix: `updateTopFade()` from `maybeScroll()` (every render) and `clearLogUI()`. Verified by
  `node --check` and by reading the three prior call sites; NOT driven live — the real IDE's 9222
  had no chat-panel target for `tools/cdp.py`. Live check + harness assertion still owed.
- **Released 0.7.1** the same session (`91a6ba5`, tag `v0.7.1`) — patch bump, one-line notes.
  Full `docs/release.md` run held at the approval gate; verifier Compatible ×7, asset identical,
  feed live, Marketplace upload workflow green (run 31933586034), **Approved** the same day.
- Also wrote up the full fade model for the user: bottom `#fade` is never toggled (composer-sized,
  hidden at the end purely by `#log` padding-bottom); top `#fade-top` is the only toggled one.

## 2026-08-16 (third) — 0.7.0 goes out, quietly
- **Released 0.7.0** (`59d94fc`, tag `v0.7.0`): plan-card feedback + split Approve, custom
  commands in the / menu, 16 built-ins, and the 2026-08-15 fix tranche. Full `docs/release.md`
  run with the approval gate held at step 6; version chosen as a MINOR bump (features, not fixes,
  per the 0.5.x→0.6.0 pattern).
- `verifyPlugin` WITHOUT `-PskipVerifierIdes`: Compatible on all seven PhpStorm branches
  242→262, zero warnings, 40s wall-clock on the cached ladder. Asset 200 + `cmp`-identical, feed
  advertising 0.7.0, upload workflow green in 17s, **Approved** the same day (IDE-run row + verifier
  1.408 on 2026.2.1 / 2026.1.5). The user uploaded the five new listing screenshots by hand.
- Two small tool traps, neither costly: `tail -30` on the backgrounded verifier truncated the
  per-IDE verdict list — read `build/reports/pluginVerifier/PS-*/plugins/<id>/<ver>/
  verification-verdict.txt` instead of the log; and zsh globs an unquoted `?` in a curl URL
  ("no matches found") — quote Marketplace API URLs.
- Release-time API check again showed 0.6.0 as newest-approved for a few minutes; the plugin
  page had 0.7.0 Approved by the time the user looked. Known lag, recorded 2026-08-14.
- Next: backlog order — plan-card shortcuts, reloaded-webview log replay, kill-bg-process.

## Digest
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
