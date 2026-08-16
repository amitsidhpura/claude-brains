# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

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

## 2026-08-16 (second) — the plan field joins the ask family; the storefront reshot from the real renderer
- **Plan-feedback field restyled, mockup-first per convention:** three user iterations (#1b1b1b →
  "match the ask Other input" → #201c19 → #201c1a) landed as one `--warn-field` token shared by
  `.plan-fb` and `.ask-other input` — the panel's two type-your-answer-on-a-card surfaces now one
  control. A `.plan-sep` hairline (composer `#inputbar` idiom, its own div because an input cannot
  carry a pseudo-element) marks where the plan ends. Ported to chat.html; `done()` removes the
  separator WITH the input, because the replayed card draws plan + footer only and the two paths
  must not disagree.
- Fixture 48 +5 assertions (separator exists/positioned/actually-drawn via computed style, field
  bg = the token's exact rgb, separator gone after decision). Harness **308/308** — after finding
  the 9222 panel was the PREVIOUS session's sandbox still running the old build (killed
  gracefully, relaunched, build verified BY CONTENT before measuring).
- **Marketplace screenshots: five fresh 2400×1520 frames replace the 2026-07-31 trio.** The
  live-JCEF capture route is a dead end — under `Emulation.setDeviceMetricsOverride` the OSR
  surface TILES multiple paints into `captureScreenshot` (clip+scale doesn't help). The shots
  come from the spliced chat.html in headless Chrome (real CSS + LIMITS captured off the live
  panel), states driven through the same builders `__gallery()` uses, animations frozen at
  legible values, colours/geometry measured off the old PNGs. Pipeline in the session scratchpad
  (mkshots.py / mkshots2.py / compose.py), not committed.
- Traps for the file: popups force-shown via `classList.add('show')` skip `tg()`'s positioning
  (open via the chip's real click); `.popup` width is max-content, so a menu that fits the wide
  IDE overflows a 394px shot; and `\uXXXX`-escaped file content must be patched with the Edit
  tool — a python-heredoc string literal decodes the escape and silently misses.
- Next: version bump (changeNotesHtml now carries 2026-08-15's tranches + both 2026-08-16 rounds).

## 2026-08-16 — plan feedback ships; the user's sweep beats the harness twice
- **Shipped the plan-card feedback feature** (the user's ask after learning the terminal's
  "keep planning" is an input): a "Tell Claude what to change" field riding every decision, and
  the Yes variants folded behind a split Approve caret. Wire mechanisms all probed on 2.1.233
  BEFORE coding: deny message = verbatim tool_result; allow `feedback` field = silently
  schema-stripped; the winner for approve is appending the note to `updatedInput.plan` (the
  terminal's own ctrl+g field) so it arrives in the SAME message as the approval.
- **The first approve mechanism was wrong and only the user's manual test caught it.** stdin
  steering measured as "delivered before the first Write" in the probe — but it races the model
  call cycle, and in the user's run the note arrived after implementation started ("if I want
  two→three it might write two then update"). The probe's one green run was timing luck.
  Lesson: a race you measured passing once is still a race.
- **Guided six manual tests, one at a time; two more finds:** the mode chip stuck on Plan after
  approval (the CLI restores prePlanMode but broadcasts its INTERNAL name `default`, which the
  chip's unknown-mode guard dropped — one-line alias), and earlier the "2 background processes"
  chip that was truthfully reporting my own orphaned probe scripts (blocking readline(), the
  same waiter-loop family as 2026-08-15 — now always `timeout N` wrapped).
- Also fixed en route: an immediate post-approval set_permission_mode ALWAYS loses to the CLI's
  prePlanMode restore (mode rows now park in `pendingPlanMode` until the broadcast); mid-turn
  steered messages persist ONLY as `queued_command` attachment records, so replay silently lost
  them (SessionStore now maps mode=prompt ones to user bubbles, deduped against delivered
  copies).
- Sandbox vs real IDE on one debug port: the real IDE held 9222, so runIde gained
  `-PjcefDebugPort` and cdp.py `CLAUDE_BRAINS_CDP_PORT` — then the sandbox's own hand-set
  Registry value beat the property anyway. Panel identity is now verified BY CONTENT before any
  harness run.
- Verified: live harness **303** (fixture 48, 10 steps, negative control run), `./gradlew test`
  **107** (3 new SessionStore tests, stash-run controls failed 2 and 3 pre-fix), six-test manual
  sweep by the user, replay re-checked against the real e2e transcripts.
- Next: version bump (changeNotesHtml gate) — now carrying 2026-08-15's three tranches AND this.

## 2026-08-15 (third) — three user reports, three fixes, and one premise I had backwards
- **"The chip said 2 tasks but there were none."** It was RIGHT: the process tree showed two shells
  genuinely alive under the panel's `claude` pid — my own orphaned `until … sleep 30 … done` waiter
  loops from the sweep, 2h34m and 1h31m old (killing them fired exactly two task notifications).
  Checking the process tree BEFORE the code is what turned this from a phantom into a fact.
- The real defect underneath was the inverse of the report: the roster reset lived in `sendTurn`
  (wiping shells that were still running) while `clearLogUI` — the actual CLI-restart boundary —
  never cleared it. The CLI's own schema settles it: the level is per-process. `docs/client-parity.md`
  had CLAIMED clearLogUI did this since before the code ever did.
- **Stuck popup highlight.** The user's wording ("dark on hover, lighter after") was exact and both
  halves had separate causes: a `.bg-row` CSS opt-out that beats `:hover` but only TIES with `.sel`,
  plus a document-level `mouseover` painter with no exit counterpart. Fixed with a `nosel` marker;
  the roster takes no cursor at all. Verified with REAL CEF mouse events, not synthetic ones.
- **One path renderer everywhere.** The decision card printed the absolute path while the tool line
  above it printed the relative one. Both card surfaces now use `fillPath`; an audit then found the
  @-mention menu ellipsising at the WRONG END (eating filenames) and `notebook_path` missing from
  DESC_KEYS/PATH_KEYS entirely, so NotebookEdit showed no path at all. Cut extracted to `pathParts()`.
- **A test caught my own over-correction twice this session:** fixture 47's step-2 guard rejected a
  `message_stop` reset within the hour, and fixture 04's restart step passed VACUOUSLY at first
  because the previous step's `sendTurn` had already emptied the roster.
- Verified: live harness **273**, Kotlin **103**, register 0 open. Next: version bump.

## 2026-08-15 (second) — 16 commands enabled, then swept; two rendering bugs and a self-inflicted third
- **Enabled an IDE-development set of 16 built-ins** (user-picked: core dev + session workflow +
  orchestration), regrouped docs/slash-commands.md by relevance, and re-captured the roster against
  CLI 2.1.233 (identical 50 names to 2.1.228 — nothing to add).
- **The user ran `/context` and saw "Puttered for 1s" and nothing else.** A local built-in answers
  as a bare whole-message `assistant` frame with ZERO stream events, and rendering was entirely
  delta-driven. Fixed with `msgStreamed`; replay needed its own fix (`system/local_command`).
- **Then swept all 16 through the live panel** rather than trusting the headless smoke that had
  missed it. 15 rendered; `/security-review` produced a completed turn with NOTHING in it. The
  wire tape proved the CLI HAD sent the reason — a `user` frame whose content is a STRING carrying
  `<local-command-stderr>`, dropped at `!Array.isArray(content)`. Fixed live + replay.
- **The tape is the lesson.** DOM evidence alone cannot separate "the CLI sent nothing" from "the
  panel dropped it", and a throw inside `onClaudeEvent` is swallowed by JCEF with no trace. 3016
  frames taped, zero handler errors — worth measuring rather than assuming.
- **Closing the last caveat found a regression I had just introduced.** Verifying
  `/security-review`'s success path (gave the sandbox a real `origin/HEAD` + a deliberately
  vulnerable file) showed every message after the first rendered TWICE. Cause: the CLI emits an
  `assistant` frame PER CONTENT BLOCK, so a message that thinks first sends two and the first was
  consuming the flag. `msgStreamed` is now turn-level. **A `message_stop` reset was tried and
  rejected within the hour — fixture 47's own step-2 guard caught it double-rendering the other
  way.** The guard written hours earlier caught the over-correction.
- Also closed: `/batch`'s real fan-out (plan gate APPROVED this time) — two parallel worktree
  agents, branches pushed, "2 tasks" chip, `PR: none — no GitHub remote` as the graceful failure.
- Verified: live harness **256/256**, Kotlin **103**, register **0 open / 27 resolved**.
- Next: version bump (changeNotesHtml gate), then backlog order.

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

## Digest
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
