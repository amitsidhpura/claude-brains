# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

## 2026-08-15 — the / menu learns custom commands; the register hits zero
- **Shipped 3.1 + 9.10 together** (the last 2 open register items → **0 open / 25 resolved**):
  custom commands, skills and MCP prompts auto-enable in the `/` menu with muted source badges
  (`.pi-src`), `/reload-skills` enabled, and `cleanInjected` fixed for the second command-wrapper
  shape (arg-less custom = message→name, NO args tag) that was leaking raw XML into a session
  title. All user-verified in a 9-test manual pass, incl. required-arg insert-and-wait and
  $ARGUMENTS expansion.
- **The approved plan died in Phase 0, correctly.** The plan was a Kotlin disk scan; the wire
  probes found the CLI marks every custom entry with a " (project)"/" (user)" description suffix
  (zero false positives across 107 built-ins), and the user approved the pivot to a webview-only
  suffix parse — no Kotlin, no rescan round-trip, plugin-sourced commands covered for free.
- **Measured twice, still wrong once:** a headless probe concluded "nothing fires on a bare file
  drop; /reload-skills is the lever" — the user's manual pass showed the menu updating WITHOUT
  it. Re-probe with a 45s quiet wait: the CLI watches the PROJECT commands dir (drop ≈2.5s,
  delete ≈1s pushes commands_changed); the first probe had sent /reload-skills inside the
  watcher's debounce window and conflated the two pushes. `~/.claude/commands` is NOT watched.
- Negative controls both RUN: fixture 46's storage-only build failed exactly its 8 discriminating
  assertions; the SessionStore wrapper test was written first and failed pre-fix.
- docs/slash-commands.md rewritten around the mechanism + roster re-captured from 2.1.228
  (50 built-ins, new `aliases` field, 13 commands new since the old capture, /review now an
  alias of /code-review).
- Verified: live harness **242/242**, `./gradlew test` **101 green**, runIde end-to-end on the
  testing repo driven over CDP + the user's own manual pass.
- Next: release the next version (changeNotesHtml gate), then backlog order.

## 2026-08-14 (second) — 0.6.0 goes out
- **Released 0.6.0** (`fa26d57`, tag `v0.6.0`): the in-flight gutter dot and the VFS refresh after
  CLI writes. Full `docs/release.md` run; the approval gate at step 6 was held for the user.
- `verifyPlugin` WITHOUT `-PskipVerifierIdes`: Compatible on all seven PhpStorm branches 242→262.
  Asset HTTP 200 and `cmp`-identical to the local zip; feed advertises 0.6.0; upload workflow green
  in 14s; Marketplace **Approved** as version id 1138398 with four green verification rows.
- **Marketplace approval lags the upload.** At release time the API still listed 0.5.3 as newest,
  which reads like a failure and is not one — `api/plugins/33274/updates` carries `approve` per
  version and is the way to check. Recorded in state.md.
- **Two stale premises fell out of the release run**, both the "a doc outlives its decision" pattern
  conventions.md already names: `updatePlugins.xml` still opened with "Path B — no JetBrains
  Marketplace" two weeks after both channels went live, and `overview.md`'s listing URL used the
  xmlId form, which 404s — the numeric `/plugin/33274` is the real one.
- Next: unchanged — 3.1 custom commands + 9.10 together, then the rest of backlog.md.

## 2026-08-14 — the IDE stops showing yesterday's files
- **Shipped:** `CliFileSync` + `Vfs.refreshFromDisk`, so an edit lands in an open editor and a new
  file appears in the tree without "Reload from disk". Two mechanisms: pair a write tool's `tool_use`
  (which has the path) with its `tool_result` (which says the write finished) and refresh that path;
  then sweep the project root at every `result`. Reasoning in decisions.md.
- **The second mechanism exists because the first was measurably not enough.** The backlog scoped
  this to Write/Edit/MultiEdit. The first real test turn asked the CLI to create one file and
  overwrite another, and it used a SINGLE Bash call for both — the scoped fix caught nothing. Bash
  names no path, so the turn-end sweep is what covers it. Gotcha recorded: Bash writes are the
  common case, not the edge one.
- **Verified through the plugin's own MCP bridge, which turns out to be an ideal probe.** `openFile`
  calls `findVFile` and refreshes nothing, so it reports exactly what the VFS knows: `error: file not
  found` for a file created behind the IDE's back, `opened:` once a turn has refreshed it. The EDIT
  half was proven with `getDiagnostics` on a JSON file open in the editor — clean while valid, then
  reporting syntax errors after the CLI made it invalid out of band.
- **Nearly called a pass a failure.** The first diagnostics read came back empty and looked like the
  document had not reloaded; it had, and the analyzer simply had not caught up. Only interpretable
  after proving the probe could see a fault at all, by opening an already-broken file. Both that and
  "several IDEs may hold the same workspace open — filter the lockfile by ideName or you hit VS
  Code's bridge" are in gotchas.
- Verified: 100 Kotlin tests (13 new, negative control RUN — inverting "refresh at tool_use" and
  "Read refreshes" both fail as they must), live harness 217/217. User confirmed it in their own IDE.
- Next: unchanged — 3.1 custom commands + 9.10 together.

## 2026-08-13 (sixth) — the in-flight dot, and five real-panel bugs a green harness could not see
- **Shipped:** the timeline gutter dot now says what is happening — white and pulsing in flight,
  green on success, red on failure — plus a `--dot-c` refactor (four duplicate `::before` rules to
  one), `--pulse-period`, and the panel's first `prefers-reduced-motion` block. Reasoning in
  decisions.md; `design/dot-pulse-probe.html` holds the four compared motions with A marked picked.
- **Method that worked, twice over:** ship the boring option FIRST (`bg-pulse`, already the chip's
  idiom), then build the probe against a rule that is genuinely live, so the pick is a one-token
  edit. And sequence the fix so the negative control is free — landing the class-add WITHOUT the
  settle made fixture 45 fail 15/31 on exactly the assertions that matter.
- **Five things the user found in screenshots that the harness could not.** In order: the ring was
  clipped by paint containment; a sub-agent went green 1.8s in (its tool_result is a launch ack); a
  failing agent stayed green (the CLI reports it `completed`); all three agents went red (each had a
  `<tool_use_error>` block it worked around); then all three went green (the failing command had run
  in the background, where nothing records an exit status). Each was measured against real
  transcripts or the CLI binary before touching code — three of the five ended in NOT shipping
  something. Traps in gotchas.md.
- **The root cause of the blind spot, now fixed:** nothing on the WIRE creates a `.turn-body` — the
  panel makes one in `addUserMessage` — so every fixture was testing blocks sitting bare in `#log`,
  outside the containment and stacking context real blocks live in. `tools/live_harness.py` gained a
  per-step `setup` hook; fixture 45 uses it and is now 31 steps / 63 assertions.
- **Withdrawn the same day:** the halo, the containment lift it forced, and a sub-task status dot
  with its Kotlin round trip (reverted byte-exact). Recorded rather than deleted — each looks right
  until measured.
- Verified: live harness **217/217** in real JCEF, `./gradlew test` **87 green**, ten negative
  controls RUN (not just written), incl. injecting `animation:none` to prove the CSS assertions
  discriminate from the class ones.
- Env trap: a cold Gradle configuration cache turns `runIde` into a network build that can die on
  `teamcity.jetbrains.com` TLS — `-PskipVerifierIdes` is the way past it (gotchas).
- Next: unchanged — 3.1 custom commands + 9.10 together, then VFS refresh.

## 2026-08-13 (fifth) — 0.5.3 released; the docs stop describing a project that no longer exists
- **0.5.3 released and Marketplace-Approved** (`f751503`, tag `v0.5.3`): the header-title fix and
  the attached-block spacing. Full release.md run, gate held at step 6 for the user. Asset HTTP 200
  and `cmp`-identical to the local zip; feed advertises 0.5.3; the upload workflow went green in 8s
  and the Marketplace accepted it as version id 1137360. Approved with four green verification rows
  (IDE run + verifier 1.408 on 2026.2.1 / 2026.1.5 / 2025.3.6.1).
- `verifyPlugin` run WITHOUT `-PskipVerifierIdes`: **Compatible on all seven PhpStorm branches
  242→262, zero warnings, zero compatibility problems**. First run downloads the ladder (~25 min);
  cached after.
- **Nearly shipped a stale artifact.** A `chat.css` edit landed AFTER `buildPlugin`, so the zip on
  disk was behind. Rather than assume either way, extracted the CSS from the zip (it had the new
  rule) and then read exact mtimes: css 17:30:31 → zip 17:30:50 → verdicts 17:47:40. `verifyPlugin`
  had rebuilt it, so the verified bytes were the shipped bytes. Gotcha recorded.
- **Docs truth pass**, prompted by finding the README's Install section false: the plugin has been
  on the Marketplace since 2026-07-31 while README and `docs/release.md`'s title both still said
  "not the Marketplace". Also six dead `CLAUDE.md` references (migrated 2026-08-07, one of them a
  broken link in the README's own Docs list), README listing usage/token display as "deferred" when
  it was DECLINED on 2026-08-06, one "FIXED same day" left in the retired register vocabulary, and
  `feature-checklist.md` describing the header behaviour this very release changed.
- `#log` was the last place hardcoding the 18px block gap — on `--block-gap` now, `limits.md` updated.
- **One doc I called stale was not:** `verifier-matrix.md` says `verifyPlugin` checks PhpStorm only,
  and that is still true — `recommended()` resolves to the DECLARED platform's branches, so it is
  PhpStorm's ladder, not a cross-product matrix. Read the doc before "correcting" it.
- Next: unchanged — 3.1 custom commands + 9.10 together, then VFS refresh.

## 2026-08-13 (fourth) — one gap for everything that hangs off a line
- User asked why `.io` sits at `-6px` while `.tool-imgs` and `.todos` sit at `+2px`. The margins were
  never the answer: `.turn-body` is a flex column with `gap: 18px`, so the gap you SEE is 18 + the
  margin. Measured: **12 / 16 / 20 / 20 / 20px** — and three of the five sat FARTHER from their own
  tool line than an unrelated block at 18px, i.e. attached content read as detached.
- Two tokens now, `--block-gap` (independent blocks) and `--attach-gap` (a line and what hangs off
  it), with one rule for the five tool-line followers written as
  `calc(var(--attach-gap) - var(--block-gap))` so what is stated is the TARGET gap, not a nudge.
- **8px, picked by the user from `design/tool-gap-probe.html`** — 12/8/6/4 rendered side by side,
  every column driven by the real rule rather than a copy. 8px is what `.card-h` already put between
  a card's header and its body, so the panel's two "this belongs to that" idioms now agree.
- Extended on the user's ask to `.compact-sum` (was 4px), `.card-h → .diff/.cmd/.blk` (was 8px
  hardcoded) and `.think summary → .body` (was 4px).
- **Two container traps, both now written at the site.** `.compact-sum` cannot join the shared
  selector list — its parent is an ordinary block with no flex gap to cancel, so the subtraction
  would pull it 10px INTO the status line. And `.card-h`'s `margin-bottom` COLLAPSES with
  `.card .blk`'s `margin-top` rather than adding, so both ends had to name the token or the larger
  would silently win the next time the value changes.
- Verified in real JCEF through `window.__gallery()`: every attached pair exactly 8px, every
  unattached pair exactly 18px, 29 pairs, zero deviations. **Negative control RUN** by injecting
  `git show HEAD:`'s stylesheet into the same live page — the old 12/20/20 spread came straight
  back. Harness 154/154, `./gradlew test` 87 green.
- Surveyed every gap tighter than 18px so the rest is on record: action rows at 10px, list rhythm
  (0/2/3/6), label→sub-line at 1px. Left alone. One odd value outstanding — `.ask-panel → .ask-b` at
  4px where every other body→buttons gap is 10px (backlog).
- Next: unchanged — 3.1 custom commands + 9.10 together, then VFS refresh.

## 2026-08-13 (third) — a header that lagged a whole turn, and a control that nearly lied
- User screenshot, explicitly NOT reproducible for them: header "New conversation" beside a history
  row marked `current` that HAD a title. Diagnosed from the real transcript before touching code —
  `D--sites-accesshealth/ccafeb52-….jsonl` put the first prompt at 04:39:10Z and the next at
  05:40:30Z, so the screenshot's "10:14 AM · 430 KB" was five minutes into a first turn that ran an
  HOUR, and the file has no ai-title/custom-title/summary at all. Not a race, not a one-off: the
  header is pushed and only at `result`, the history list re-reads disk on every open, and both go
  through the same `titleOf`. They disagree for exactly one turn, every time.
- Fixed in two parts (decisions.md): a once-per-turn probe at `message_start` while the thread is
  unnamed, and `seedUi()` re-seeding the page on EVERY load instead of once inside `startSession()`.
- **Measured which frame can carry it**, rather than picking the obvious one: `_local/title_timing.py`
  spawns the CLI as ClaudeCli does and reports, per frame, whether the transcript exists. At
  `system/init` it is MISSING; by `message_start` it is 15 KB with the first user record. The
  precise-looking hook (the session id arriving) would have been too early.
- **The negative control nearly ran against the fixed build.** TaskStop killed Gradle but NOT the
  forked sandbox IDE, so `prepareSandbox` failed on a mapped jar — and CDP still found a live panel,
  which would have "passed" the control on the code it was meant to refute. The build log caught it.
  Now in gotchas, with the graceful `taskkill` (no `/F`) that keeps the sandbox layout.
- Controls, once run properly, reproduced the screenshot on demand: pre-fix mid-turn, header
  "New conversation" / row "Run the bash command: sleep 300" / `match:false`, 0 result frames, no
  `__title` after `message_start`; after `location.reload()`, `slashCommands` 0 and `projectRoot` "".
  Post-fix: `match:true`, 39 commands, real root. 87 tests green, live harness 154/154 in real JCEF.
- Register: 8.16 added and RESOLVED → **2 open ISSUE / 23 RESOLVED**. It landed AFTER 0.5.2 shipped,
  so it rides the next release.
- Next: unchanged — 3.1 custom commands + 9.10 together, then VFS refresh.

## 2026-08-13 (second) — 0.5.2 lands on the Marketplace; a load on the other machine
- Pulled `89f1714..a1523dc` on the **Windows** box (`D:\sites\claude-brains`): the two 08-13
  commits plus the 0.5.2 release commit and its tag. Clean fast-forward, tree clean, no build.
- **0.5.2 is released and Marketplace-Approved.** Confirmed on the listing (user screenshot):
  uploaded 13 Aug, 2.6 MB, compatibility 242.0+, both verification rows green — IDE run "no issues
  occurred", verifier 1.408 "Compatible" on IntelliJ IDEA 2026.2.1. The GitHub release carries
  `claude-brains-0.5.2.zip`, so `release: published` → `marketplace-upload.yml` worked end to end
  with no human at an upload form. That is the automation from 2026-08-12 proving itself.
- **The release session was never journaled.** `state.md` still read "no version bump, 0.5.1
  remains the released version" while `build.gradle.kts`, `updatePlugins.xml`, the tag and the
  listing all said 0.5.2. Caught on `/context load` by diffing git against the newest journal entry
  — that verification step earns its place; the files were confidently wrong, not vague.
- **Machine drift caught again, the other direction.** The files described Linux, this is Windows.
  Second consecutive load where the recorded machine was wrong, so state.md now leads with "check
  which machine" rather than declaring one.
- Consequence worth having found before starting work: the 3.1/9.10 fixture is NOT on this box.
  `D:\sites\claude-brains-test` exists but holds only `.idea` — no `.claude/commands/dummy-cmd.md`.
  It lives in the Linux test repo, is not in git, and does not travel. One file to recreate.
- No code changed this session; context files reconciled instead.
- Next: unchanged — 3.1 custom commands + 9.10 together, then VFS refresh after CLI writes.

## 2026-08-13 — the runIde sweep closes; a fixture that was only half a test
- User verified 8.13 (rename outside-click) and 8.14 (MCP tool lines) in `runIde` and ticked both.
- **Live harness in real JCEF: 154/154.** That is the run that mattered for the io-box
  restructure — moving the cut/note markers out of `.io-row` and changing which element `foldBlock`
  receives regressed nothing, with fixture 04's background chip, 01b and 40's tool-line paths all
  still green.
- Fixture 44 passed 17/17 first time, which is also what a vacuous fixture looks like — so it was
  replayed against 89f1714 (pre-fix). It failed on the stuck-busy defect and **passed** on the
  CLI-initiated-turn one: the first bug leaves `busy` already true, so "a turn we did not start
  sets busy" read as satisfied when nothing had set it. The two defects mask each other inside the
  fixture exactly as they do in the panel. A forced `setBusy(false)` step now separates the halves,
  and the fixture fails on BOTH against 89f1714 (5 assertions). Lesson in conventions.md.
- **8.15 closed against a real CLI**, driven entirely over CDP: compose + `submit()` to send a real
  turn, with `window.onClaudeEvent` wrapped to timestamp every frame against `busy` and the button.
  The lifecycle came out exactly as the wire capture predicted — 3.5s roster gains `local_bash`,
  4.8s `result` finalizes while the chip still shows the task, 33.6s shell exits and the CLI opens
  a NEW request on its own, 35.8s `message_start` flips busy back to true. No `user` frame anywhere
  between, which independently confirms `message_start` is the only available hook. Decisive
  number: **0 content deltas rendered while the button read Send**.
- That technique is now in gotchas: cdp.py can ACT as well as observe, which turns a "watch the
  panel and hope you catch it" item into a replayable timeline with a number to assert on.
- Register: 2 open ISSUE / 22 RESOLVED, and **zero unticked checklist items** — the sweep that had
  been accumulating since 2026-08-12 is done.
- Next: 3.1 custom commands + 9.10, worked together, now top of the queue.

## 2026-08-12 (fifth) — the IN/OUT box learns the diff's geometry; a contract falls out
- One user request — "make IN/OUT same as the write permission panel, scrollbar to the border and
  spacing inside" — that took four rounds because each fix exposed the next thing the old layout
  had been hiding. Every round was caught by MEASURING, never by looking.
- The difference was structural: `.diff` puts padding AND `overflow-x` on one element, so the
  scrollbar lands at the bottom of its padding box (flush, full width). `.io` split them across
  three, insetting the bar 42px. Moving the scroll to `.io-row` fixed that and broke three things
  the markers had been papering over — see gotchas, all four are recorded there.
- Round 2 (user's DevTools screenshots): `OUT` jammed against the value. Sticky clamps to its
  containing block, so `padding on the row + negative margin on the label` shoved the label 10px
  right and ate the column gap. Fixed by moving the row's left padding onto the label itself.
- Round 3 (user): folded rows kept their scrollbar, where the Accept/Reject diff shows one only
  when expanded. Cause: `overflow: hidden` crops only the element it lands on, and I had folded
  `.io-v` while scrolling `.io-row`. Now `foldBlock(row)` — same element for both, like `.diff`.
- Round 4 (user generalised it): every foldable block obeys one contract now, written into
  chat.css above the fold rules. Audited all five surfaces; only `.io-row` deviated (6px→8px).
- **The audit lied the first time and I nearly shipped it.** It reported "no failures" while
  having skipped rule 2 entirely: I read `overflow-x` after restoring the fold class, so folded
  elements reported `hidden` → "not scrollable" → the assertion never ran. Re-measured in the
  expanded state, then force-folded the two surfaces the gallery never folds.
- **User confirmed in runIde: "showing perfect".** That clears the real-JCEF question for this
  work specifically — the scrollbar geometry was exactly the class of thing headless gets wrong.
- Next: the register items 8.13/8.14/8.15 are still formally unticked, and fixture 44 has still
  never been executed.

## Digest
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
