# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

## 2026-08-24 — the phantom was PhpStorm's, and a plan card that lied twice
- **Phantom Enter closed by attribution, not by code.** A fresh check found the real ticket:
  **IJPL-161111** "JCEF: the keyboard on linux is broken" (dups JBR-7536/7547) — Linux+OSR,
  repeated key events on the JS side, wrong codes — **fixed upstream in 2024.2.2 / 2024.3**.
  The sandbox pinned 2024.2.0. The old JBR-5348/5115 attribution was stale (2023 fixes, already
  in our JBR). Bumped to `phpstorm("2024.2.6")`; user re-tested many times, never reproduced:
  "it was not plugin but PHPStorm". Three reverted guards had been defending a dev-only bug.
- Guard archaeology found **nothing in git** — the guards were never committed, so gotchas'
  "recover from git history" was false. The only evidence was the sandbox idea.log; preserved
  to `_local/phantom-enter-tape-2026-08-23/` before the bump created a new sandbox dir. That log
  also held 52 platform-only `invalid keyCode` stacks in `JBCefEventUtils.convertCefKeyEvent`.
- **Then the real bug**, from user screenshots: a plan card pending at reload replayed
  "✓ Approved", and on the next reload "✗ Kept planning" quoting the CLI's own AbortError.
  Probes (headless, killing the CLI mid-permission) measured the mechanism: a dying CLI flushes
  its auto-deny tool_result within ms, so the panel read the transcript BEFORE that write — the
  record arrived one reload late, and the two-way footer branch invented "Approved" from its
  absence. The user's own session file grew 18→22 records mid-investigation, on camera.
- Fixed in three places: `undecided` flag (no tool_result → "◌ Interrupted — no decision
  recorded", neutral `.und-t`), `PERMISSION_ABORT_PREFIX` filtered from planFeedback, and
  `stopForReplay()` killing the CLI before reading. Fixture 54 + a parser test; control run
  406/5 before applying.
- **The user caught the regression I shipped with it**: the unconditional `awaitExit(1_500)`
  made every reload slow. Now gated on `pendingPermissions.isNotEmpty()`. Lesson: a correctness
  wait that fires on the common path is a performance bug — scope it to the state it exists for.
- Also fixed: `tools/cdp.py` picked the first `jbcefbrowser` page, and the fresh 2024.2.6
  sandbox opened a "What's new" tab that hijacked it — now matches the panel's `<title>` first.
- Relearned the hard way (twice in two days): `pkill -f runIde` kills gradle, not the sandbox
  IDE; the orphan then swallows relaunches silently.
- Declined by the user: wrapping the confirm-card path to show it whole at narrow widths.
- **Released 0.9.0** at the end of the session (tag `v0.9.0`, commit `f53a10c`): plan comments,
  the pending-plan replay honesty, the VFS open-path fix and the compaction replay-order fix.
  GitHub release, custom feed and the automatic Marketplace upload all green; the published
  asset `cmp`s identical to the local zip.

## 2026-08-23 (third) — plan comments finish, and a phantom Enter that beat us
- Four more polish rounds on 5.6, all committed (`92363ac`): the ✕/⏎ buttons wear the `.rm`
  plate (`--warn-border` 15% over `--warn-bg`) and `.rm` itself LOST its left-edge fade on the
  attachment chips + queue rows; decided cards now KEEP their anchor highlights.
- **Live/replay highlight parity** (user ask): `highlightAnchors` in 50-blocks.js re-highlights
  by text search and is run by BOTH the live decide (after unwrapping its precise selection
  marks) and `replayCard` — one function, so the two cannot drift. Then the user caught it
  picking the FIRST occurrence: capture now records WHICH match (shared `planFlat`/
  `anchorMatches`), the wire carries `[Re: "x" (2nd occurrence)]` only when ambiguous, and
  `RenderLimits.PlanComment` gained an `occurrence` field. Substring hits ("one" inside
  "standalone") count identically on both ends, which is what makes it land right.
- **The phantom-Enter saga, and its retreat.** User: typing a second comment sometimes commits
  after one character. Three fixes, three failures: an `e.code` guard (phantom passes with
  `code:''`), a <30ms cadence guard (killed REAL Enter — "Enter completely not working"), and
  moving the commit to keyup (user's screenshot showed a 1-char commit anyway, so that variant
  delivers keyups too). **The user called it and asked for a full revert; all three guards, the
  key tape and the Kotlin `diag` bridge verb are gone.** The composer is back to plain
  Enter-commits/Escape-cancels.
- What the investigation DID establish (kept in gotchas): a webview `diag` verb writing to
  `idea.log` is the only way to keep evidence past a window close, and it taped a genuine
  JCEF-OSR defect — keydown-only Enter loops at ~1-3ms for seconds, plus physical codes
  scrambled wholesale (a 't' arriving with `code:'ArrowDown'`). Ours registers no key listeners,
  so the fault is in JBR's AWT→CEF translation (JBR-5348/5115 family). Not our bug to fix.
- Lesson the hard way: **three speculative fixes shipped on an unreproduced bug** — exactly what
  conventions.md warns against. The trusted-CDP repro attempt failed early and that should have
  stopped the fixing, not licensed guessing.
- Also relearned: `pkill -f runIde` kills gradle but NOT the sandbox IDE, whose orphan then
  swallows every relaunch via single-instance forwarding (runIde exits ~700ms "successfully").

## 2026-08-23 (second) — plan comments: from VS Code screenshots to a shipped feature
- The user walked VS Code's plan-preview commenting live (screenshots) and we measured the whole
  mechanism: plans are files in `~/.claude/plans/<slug>.md` written by an ordinary Write;
  `get_plan` returns `{exists, content, path}`; comments force keep-planning THERE; the wire is
  plain text on the deny tool_result — `[Re: "anchor"] note` lines under "Comments on the plan:"
  (grepped byte-for-byte from the claude-vscode transcript). Checklist 5.6 [LG]→[MD] on that.
- Then "Lets implement it" — built IN THE PANEL (`c0df900`): select text on the plan card →
  floating pill → anchored comment rows between the separator and the feedback input. Deny sends
  the reference client's exact format; approve rides `PLAN_NOTES_MARKER`; SessionStore parses
  both back (`parsePlanComments` in RenderLimits, format strings in `window.LIMITS`); replay
  draws the same rows via one shared builder (`planCommentRows`). Kotlin untouched on the wire.
- **User's design call, diverging from the reference:** the FULL approve surface stays available
  with comments pending (VS Code collapses to keep-planning-only). Mockup-first with candidates;
  seven polish rounds followed, each from a real-IDE or mockup pass: pill containing block
  (`.card.plan{position:relative}` was missing), commit-on-decision-click, two-line composer with
  ✕/⏎ (later one centered `.c-btns` stack), rows below the separator at the decision surface's
  10px rhythm, size parity with `.plan-fb`, container `:focus-within` ring, mark geometry matched
  to `.blk code`, mark mix 40→60%.
- **The round-4 bug had two heads**: deleting a row mid-compose dropped the composer AND stranded
  `composing` (pill locked out); plus the generic ✕ loop's `splice(NaN,1)` ate element 0 once the
  composer got its own ✕. Both pinned in fixture 53's bug-repro step.
- Proof discipline held all day: fixture 53 grew to 37 asserts across EIGHT control runs (14 fail
  pre-feature, then 3/4/2/9/2/1/1 per round — every new assert seen failing first); full harness
  361 → **398**; gradle test 109 → **113** (parser + replay fixtures incl. the byte-exact VS Code
  deny message); `probe` on the real VS Code transcript replays the comment row.
- Traps for gotchas: `pkill -f` self-matching the Bash cmdline (twice), `color-mix` computing to
  `color(srgb …/a)` making an rgba-regex assert VACUOUS (caught because the control run passed
  when it should fail), `surroundContents` leaving split text nodes (unwrap now `normalize()`s),
  the global `.turn :focus-visible` out-specifying an element `outline:none`.
- Not done: keyboard-only selection can't trigger the pill (backlog); still NO release — 0.8.0
  ships, `main` now carries VFS fix + compaction fix + 2.1.241 audit + this feature.

## 2026-08-23 — the checklist meets 2.1.241, and the wire answers back
- `docs/feature-checklist.md` re-audited 2.1.233 → 2.1.241 (user ask; both sides installed at
  241). Same method as 2026-08-17 — package.json contributions, `case"…"` vocab, typed control
  subtypes — plus LIVE stdio probes this time. Verdict: **no new feature surfaces on either
  side**; extension contributions and the 12-tool IDE-MCP set identical, typed control vocab +1
  @internal (`register_device_hooks`).
- Baseline problem (2.1.233 gone from disk) solved by downloading the old vsix from the
  marketplace vspackage URL — gzip-wrapped, and it carries `resources/native-binary/claude`, a
  true CLI binary baseline. `vscode/` re-extracted at 2.1.241. Procedure now in runbook.md.
- What moved was measurement, not features: `stop_task` accepted over stdio (11.3 unblocked;
  relay whitelist added it at 2.1.238), `side_question` answers headlessly with a real model
  reply (8.11), `apply_flag_settings{effortLevel}` takes effect (9.2 watch-item resolved) and
  `{fastMode:true}` clears `sdk_opt_in_required` (9.4), `set_max_thinking_tokens` accepted (9.5);
  `rename_session`/`get_settings`/`list_models`/`get_context_usage` all answer. New row 14.4
  (`get_workspace_diff`). §16 counts unstaled (tests 109, harness 361, releases → 0.8.0).
- **`/clear` grew a `[name]` hint** → a panel menu pick now INSERTS `/clear ` instead of running
  (`cmdTakesArg` keys on the hint) and the native branch drops a typed name; 7.6 records the
  pending decision (pass the name through as the title, or pin pick-runs).
- Trap: a `new Set([...subtypes])` in the binary is the remote-relay whitelist, NOT the stdio
  accept list — schemas for side_question/apply_flag_settings existed at 2.1.233 while the
  checklist said "no host-side control". Only a live probe settles acceptance (gotchas).
- Follow-ups: `docs/slash-commands.md` still says 2.1.233 and misses the /clear hint; the nine
  [DECIDE] rows unchanged but 8.11/9.4/9.5/11.3 now have their probes pre-paid.

## 2026-08-21 (second) — the /compact bubble finds its place, and the trick gets a name
- User screenshots (live vs resumed): replay drew "Conversation compacted" ABOVE the /compact
  bubble that caused it. Measured on a real shopify transcript: the CLI writes boundary + summary
  at compaction END, physically BEFORE the command records, which keep typed-at timestamps
  (boundary 13:20:19 @ file pos 295; its /compact 13:18:11 @ 298). File order lies; live was right.
- Fixed in SessionStore, then — user asked for a common solution — generalized: `DisplacedAnchor`
  (arm index+ts, insert-before when a later record's ts proves it earlier, shift/forget on
  eviction). The 2026-08-09 retry-storm reorder refactored onto the same class. Global timestamp
  sort rejected: ts-less records, per-block same-ts messages, the streaming eviction window, and
  "timestamps always win" is an unmeasured premise (decisions.md).
- Proof chain: probe on the real session pre/post fix (inversion → corrected); refactor
  byte-identical (`probe --json` + cmp); +2 order tests (compact, late-flushed retries — the old
  retry test pinned SPELLINGS only, the reorder was previously unpinned); negative control by
  neutering the shared guard → exactly the 2 order tests fail. gradle test 107 → 109.
- User confirmed in their real IDE ("Showing perfect now"), then asked about the leftover
  "Distilled for 41s" footer: decided to KEEP both divergences (footer live-only, summary box
  resume-only) — recorded as deliberate in docs/renderer-parity.md Audit 2.
- jb.gg/teamcity config-time "Connection timed out" struck again mid-session; `-PskipVerifierIdes`
  cleared it (the 2026-08-12 gotcha, working as documented). Host answered fine minutes later.
- Trap relearned: `2>&1 > file` splits gradle's streams (stderr keeps the terminal); use
  `> file 2>&1`. And background Bash tasks start in the repo root, not the shell's cwd.
- Next: unchanged backlog; both 2026-08-21 fixes committed together, still unreleased.

## 2026-08-21 — "File not found" on a path that was right there
- User report (Windows, PhpStorm, project `D:\sites\metrobuildsuppliers`): clicking the path on a
  `Read` line raised the plugin's "File not found" balloon for a screenshot in `_local/`.
- The balloon itself cleared the wire: `notifyMissing` prints the RAW path it received, and it
  showed the full correct absolute path — so the click, the `dataset.path` round-trip and the
  bridge were all fine, and only the lookup had failed. Diagnosing from the error text saved
  chasing the webview.
- Root cause: `findVFile` → `LocalFileSystem.findFileByPath` reads the VFS snapshot, not the disk.
  Not Windows-specific at all; the user just hit the window there.
- **Reproduced on Linux against the USER'S LIVE PhpStorm** by talking to its own MCP bridge
  (lock file → WebSocket → `tools/call`), using read-only `checkDocumentDirty` so nothing opened
  in their IDE: a file created at the project root seconds earlier answered "not found" while
  pre-existing `index.php` resolved. Re-probed ~2 min later: both resolved — a staleness window.
- Fix: `findVFileOnDisk` (snapshot → `refreshAndFindFileByPath`) on the two "open this path"
  callers only; read-locked callers must not refresh (deadlock). See decisions + gotchas.
- Verified in a sandbox (`runIde --args=<testRepo>`, which skips all GUI clicking) with a
  three-call discriminating run at one instant: snapshot lookup "not found" / fixed lookup
  "opened" / absent path still "not found". Then drove the user's ACTUAL path — `__bridge`
  `{kind:'open'}` over CDP — and `getOpenEditors` confirmed the file opened; ghost path opened
  nothing. gradle test 107, compile clean.
- Cross-checked by a second model (Fable) at the user's request: threading triage re-derived from
  the code (`Threads.kt` `readLocked` = `runReadAction`; the `open` branch takes no lock), full
  caller sweep, verdict ship. It also noted the failure path now attempts two refreshes on a
  missing absolute path (harmless, pre-existing fallback shape) — left alone.
- Sandbox CDP port gotcha AGAIN: `-PjcefDebugPort=9223` on the command line, CDP served on 9222.
  Identify the panel by content (`/json/list` title), never by the port you asked for.
- Next: unchanged backlog; the fix is uncommitted-then-committed this session and NOT released.

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

## Digest
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
