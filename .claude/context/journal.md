# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

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

## 2026-08-12 (fourth) — three user reports; the third one taught the method
- **Machine drift caught on load**: the context files said Windows `D:\sites\…`, the session was on
  Linux `/home/syncroze/Sites/…`. Both machines are real — state.md now says to check first. The
  3.1 fixture the files called "dead with the old machine" exists here as
  `~/Sites/claude-brains-testing/.claude/commands/dummy-cmd.md` (note `-testing`, not `-test`).
- **Rename outside-click.** Built it in the existing bubbling dismiss handler, tested, and it FAILED
  on `#histBtn` / model chip / mode chip / effort dots — every control beside the title
  `stopPropagation`s, so their clicks never reach that listener. Moved to capture phase. The plan
  was approved with the wrong hook; only running the matrix found it.
- **MCP tool lines.** `"function"` had been in `DESC_KEYS` since 2026-08-05 to fix 15 blank
  `browser_evaluate` lines — on the assumption a JS body reads like prose. It runs 230-2965 chars
  over 9-59 lines. Moved to `IN_KEYS` (the Bash shape: blank line, body in the box). The test that
  let it through used `() => document.title`, 21 chars, one line.
- **The third report, and the correction that mattered.** "Submit button not disabled while a
  background process runs." I diagnosed it from the CLI binary and a 7-week-old transcript and
  proposed a fix. The user rejected the plan twice and said: *don't fix what you can't reproduce.*
  They were right — six `setBusy(false)` sites produce that symptom and I had ruled out none.
- Reproduced it properly: spawned `claude` exactly as `ClaudeCli.kt` does, captured the wire for a
  real background-bash turn, then replayed those REAL frames through the REAL `chat.html`
  headlessly. Both defects appeared, and they are ordering-dependent — a short-lived process gives
  the reported Send-mode bug, a long-lived one gives the opposite (stuck on Stop). The captures are
  kept at `_local/wire.jsonl` / `_local/wire-short.jsonl`.
- The capture also KILLED my stated mechanism: I had said the notification arrives as a `user`
  frame that `onUserEvent` ignores. The transcript persists one; **the live stream sends none**. So
  `message_start` is not one option for the hook, it is the only one.
- A model switch to Fable mid-session re-checked the finding from the raw captures; it held, with
  that mechanism correction.
- Next: none of the three fixes has been through `runIde`, and fixture 44 has never run.

## 2026-08-12 (third) — the replay window kept the wrong end
- User screenshot: a live session resumed showing work from SIX DAYS earlier, "Resumed" drawn under
  a stale `Edit`. Cause: `readTranscript` capped by `break`ing out of the read, so it kept the
  OLDEST 4,000 blocks and dropped everything newer. Their session parses to 6,034.
- Measuring first paid again — a node scan of the real transcript put the cut at record ~7,668
  (2026-08-06 10:32), which is exactly the `search.php` Edit in the screenshot. Diagnosis confirmed
  before a line was changed.
- Rewritten as a newest-N window: evict from the FRONT, cap 4,000 → 20,000. Then THREE follow-on
  defects, each found by the user in the panel, not by me:
  1. Top of the window was silent — indistinguishable from the conversation's start. Added the
     `truncated` head block + `.status` marker (candidate D of four, rendered side by side in
     `design/history-edge-probe.html` and picked by the user).
  2. Window opened MID-TURN — an assistant reply with no question above it. My eviction scanned
     only one chunk ahead for a turn boundary; real turns are ~28 blocks (9 user messages in 253),
     so it gave up constantly. Now an unbounded forward scan to the first `user` block.
  3. The marker was unreadable under `#fade-top`. Sticky `.msg-user` (z 6) rides above that fade,
     which is why no one had ever seen it; ordinary content does not. Fade now switches off at
     `scrollTop <= 1`.
- **The test fixture was the real bug in (2).** It used 3-block turns, where a bounded scan cannot
  miss a boundary. Rebuilt at 14 blocks/turn; the negative control then failed exactly right
  (`expected: <user> but was: <assistant>`). A fixture that cannot express the failure is not a test.
- Also fixed, same session: the `.t-prog` line repeated a Bash command the IN box already showed,
  cut to 140 chars. This was the SECOND LANE of a bug the user reported earlier — the existing guard
  compared against `.t-desc`, which is blank for Bash by design, so it never fired. Fixture `01b`
  extended; live harness 137/137 in real JCEF.
- Lost most of the afternoon to `pluginVerification { ides { recommended() } }`: it resolves the
  Android Studio releases list from `jb.gg` → `teamcity.jetbrains.com` at CONFIGURATION time, and
  that host was unreachable, so EVERY Gradle task died with a bare "Connection timed out" naming
  neither the URL nor the verifier. `--offline` does not skip it. Worked around it by compiling
  `SessionStore` with the cached `kotlin-compiler-embeddable` and running the assertions as a
  `main`; re-ran everything under Gradle once the host returned. Then fixed the cause rather than
  leaving it: `-PskipVerifierIdes`, guarded so `verifyPlugin` refuses to run under it — an empty
  IDE list passes vacuously, which would be a rubber stamp on the one run that matters.
- Closed the loop on the harness too: 137/137 re-run AFTER the `#fade-top` change, in the sandbox
  that was still open. Both backlog items from this session are done.
- Next: nothing in flight.

## 2026-08-12 (second) — the rename that wasn't broken, and the last manual release step dies
- First session on **Windows** (`D:\sites\claude-brains`); every path in the context files was from
  the old Linux box, and `state.md` still said 0.5.0 was unreleased when git showed it tagged and
  shipped. Reconciled both here.
- User reported "rename not working" with a screenshot. Measuring the transcript FIRST (the standing
  rule) inverted the diagnosis in one command: `grep` found four correct `custom-title` records, so
  the write half was fine and the READ half was the bug — `computeTitle`'s 400-line head scan versus
  records at lines 10455-10458. Had the renderer been opened first, this would have been a long hunt
  in the wrong file.
- Fix is a full-file scan with a substring pre-filter past the head, which costs what `tokensOf`
  already pays (521 ms cold / 0 ms cached on the real 35 MB file, measured through a throwaway test
  pointed at the user's own transcript, then deleted). A tail window was considered and rejected —
  it would have made the bug intermittent instead of fixing it.
- Marketplace upload automated as a GitHub Actions workflow (repo's first). Deliberately UPLOAD-only:
  it re-posts the published GitHub asset rather than building in CI, so the zip users get is still
  the one smoke-tested locally. User picked "fully automatic" over a local `./gradlew publishPlugin`.
- Two things fell out of reading the release path: `changeNotes` has been stale since 0.3.3 (so 0.4.0
  and 0.5.0 both published the wrong notes) — now step 1b of the checklist, because automating the
  upload removes the human who might have noticed; and `workflow_dispatch` needs the file on main
  before it exists at all, so the dry run had to wait for the push.
- Negative controls run, not just written: the rename regression test failed against the pre-fix
  reader, and the workflow's asset-name assertion was exercised locally across right/wrong tags.
- `journal.md` itself held a literal NUL byte — inside the 2026-08-09 bullet describing a literal NUL
  byte — which made ripgrep call the whole memory file binary and silently return nothing for every
  search. Patched by byte replace to `\0`. Grep failing on a file is not always the pattern's fault.
- **0.5.1 cut and released the same day**, and the automation carried it: `gh release create` → the
  workflow uploaded on its own in 8s → Marketplace update id 1135613 on Stable. JetBrains then ran
  THEIR verifier on the upload, Compatible on every build 2024.2.6 → 2026.2.1, which is the run that
  was skipped locally (no verifier IDE cache on this machine, and the change touched no platform API).
- The user pushed back on `changeNotes` being only a checklist line — rightly, since a checklist is
  what had already failed twice. It is now enforced: `buildPlugin` refuses a zip whose notes carry no
  entry for its own version. First attempt broke the CONFIGURATION CACHE (a task action that reads a
  script-level `val` captures the script object); fixed by evaluating into locals in the config
  block. Both directions run — stale notes go red with the explanatory message, correct notes build.

## 2026-08-11/12 — a UI-polish session: five user-reported defects, then rename
- All five started as a user screenshot, and each was MEASURED before touching the renderer.
  Queue rows restyled to the attachment chip (border/background/hover-revealed `.rm`, divider
  below); tool-image sizing (see gotchas: a column flex box stretched a 64x64 icon to 474x320);
  todo strike → currentColor; tool-line paths project-relative and clamped to one line; context
  gauge ring; header rename.
- The duplicate line under a Bash box was `.t-prog`, the SUB-AGENT progress line: the CLI's task
  lifecycle is not sub-agent-only (`runningSubagents`/`isBackgrounded`/`local_workflow` in the
  2.1.226 binary), so an ordinary tool use hangs a task frame off its tool line whose
  `description` the tool line already shows. Suppressed on exact match only.
- Path shortening needed a NEW wire field: ChatPanel pushes `__project` (project.basePath),
  refreshed by `system/init`'s `cwd` (`subtype:"init",cwd:e.cwd` — read from the binary). The
  IDE is primary because init only arrives at the first TURN, so a resumed transcript would
  render absolute until the user spoke.
- Rename shipped end to end. `SessionStore.rename` appends the CLI's own record, read verbatim:
  `{type:"custom-title", customTitle: title.trim(), sessionId}` + "\n", O_APPEND. Header title
  hover-pencil → the header becomes the editor. `rename_session` exists as a control subtype but
  answers "onRenameSession callback not registered" — an SDK-embedder callback, unreachable over
  stream-json, so writing the record ourselves is the route.
- Tooling grew: fixtures 01b, 40, 41, 42 (live harness 132 assertions, 0 failed) and two Kotlin
  test classes (SessionStorePathTest, SessionStoreRenameTest). Every new suite got a negative
  control run — reorder the JSON fields, assert a wrong value — and each failed as it should.
- Two self-inflicted bugs found by measuring rather than looking: making `.tool-line` a flex row
  let it grow past the panel (clipping filenames at the edge), and `pushSessions()` after a rename
  popped the conversations panel open because `renderHistory` treated rendering and showing as one
  act. Both now pinned by fixtures.

## Digest
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
