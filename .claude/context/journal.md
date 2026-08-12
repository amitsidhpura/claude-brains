# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

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

## 2026-08-09 (seventh) — 0.4.0 release prep; smoke test catches the MCP shell-env gap
- User verified spacing + delete-current in runIde; 3.1+9.10 explicitly scoped OUT of 0.4.0.
- Release prep done to the approval gate: version 0.4.0, zip built + content-verified,
  updatePlugins.xml feed bumped (all uncommitted per release.md step 7).
- Plugin Verifier: clean "Compatible", zero warnings, all seven IDEs 242→262 — run three
  times as APIs landed (baseline, +EnvironmentUtil, +ShellEnv final).
- The zip smoke test earned its keep immediately: playwright MCP "failed to start" —
  first-ever real firing of the banner shipped 2026-08-06. Root cause: GUI-session PATH
  lacks nvm; 0.3.3 failed identically but SILENTLY (env code diff v0.3.3..pre-fix is empty —
  measured, not assumed). First fix (EnvironmentUtil overlay) was a NO-OP on Linux — its
  shell loading is mac-only (bytecode). Real fix: ShellEnv.kt captures `$SHELL -l -i -c
  "command env -0"` once per IDE run (watchdogged, warmed at service init); capture+parse
  proven on this machine (93 entries, npx found) before the user's re-test confirmed
  playwright connects. Gotcha recorded; full story also in the ShellEnv KDoc.
- A literal NUL byte snuck into ShellEnv.kt via a heredoc-written char literal ('\0'
  must be the ESCAPE in source) — strict XML/Kotlin tooling both objected; patched by byte
  replace.

## 2026-08-09 (sixth) — composer phantom spacing + delete-current-conversation
- Queue spacing (user screenshot, user's own diagnosis was right): `#queue { display:flex;
  margin-bottom:6px }` beats the UA's `[hidden]{display:none}` (ID outranks attribute), so
  the EMPTY hidden queue still rendered its margin — permanent 6px dead space above the
  composer's first row. Fix: `#queue[hidden]{display:none}` re-assert, the `.chip-btn[hidden]`
  idiom. Audited the other hidden-toggled composer elements — only #queue was affected.
- Delete current conversation (long-standing user annoyance): the refusal existed because the
  CLI reopens the transcript per write — a live delete truncates instead of removing. Shipped
  leave-first delete: history rows now ALL carry the trash control; for the current row
  ChatPanel does the "new"-action reset then `deleteCurrentSession` restarts fresh, waits
  bounded (new `ClaudeCli.awaitExit(5s)` — `stop()` only SENDS the signal, a dying CLI can
  still flush a resurrecting write) for the OLD process to die off-EDT, then deletes the file
  and re-pushes the list. `deleteSession`'s live refusal stays as the backstop. Mid-turn
  delete = mid-turn "New conversation", deliberately consistent. mockup.html current-row
  sample mirrored (it had documented "no delete control" as a design fact).
- Both changes compile/test green; NOT yet runIde-verified and NOT committed.

## 2026-08-09 (fifth) — editor accept/reject v2 buttons half: balloon → under-diff bar
- Shipped the v2 buttons half through four user-driven iterations, each measured against
  242 AND 262 bytecode before coding: (1) toolbar CONTEXT_ACTIONS icons — worked but user
  rejected on sight, unidentifiable among diff toolbar icons; (2) NOTIFICATION_PROVIDERS
  top banner with prose + info tint — wrong position, too loud; (3) plain bar UNDER the
  editor via `FileEditorManager.addBottomComponent` on the editors `openFile` returns —
  accepted; (4) polish to full card parity: centered, card colours (.ok green / .no
  neutral via "JButton.*" client properties — LAF ignores setBackground), card's own
  Lucide SVGs bundled as /icons/*.svg (platform tick lookalikes don't match; Actions.
  Commit isn't even a tick in the new UI).
- Third button added: COMBINED suggestion grant (user's spec — no dropdown in the editor).
  One button grants every allow-suggestion whole ("Always allow" if rules present, else
  "Accept all edits"), echoing original indices; new "FILE_SAVED_ALL" verdict is a
  permission-flow-only extension — bridge verdicts stay the reference set.
- Two sandbox errors triaged: GlobalMenuLinux <clinit> SEVERE is 2024.2 platform noise at
  every launch (log-proven, gotchas'd); the real one was MINE — a literal `--` (CSS var
  name) inside an SVG comment is illegal XML, the strict loader killed reject.svg with
  "String '--' not allowed in comment" → iconless button + IDE error balloon.
- Text-in-toolbar impossibility recorded: displayTextInToolbar() @Deprecated(forRemoval)
  on 262, replacement SHOW_TEXT_IN_TOOLBAR key absent on 242 — no warning-free path.
- Next: 3.1+9.10 pairing (user wants together), VFS refresh, Plugin Verifier re-run
  (new APIs: addBottomComponent, IconLoader.getIcon(path, Class), JButton client props).

## 2026-08-09 (fourth) — 9.1 + 10.5 + editor accept/reject + 10.1/10.3: register 6 → 2 open
- Committed and pushed as `f001e0b` (20 files, +864/−90), tests green going in.
- 9.1 live half: the api_retry `error` is c_r()'s five-code ENUM (read from the binary — network
  failures are the literal "unknown"), and the stream translator double-emits each retry (raw
  api_error falls through `else yield` before the api_retry twin). RETRY_REASONS + last-key
  dedupe in chat.html; fixture 09 (8 pre-fix FAILs). User verified with a real nmcli storm.
- 9.1 replay half (user's replay screenshot): the CLI WRITES the concluding error record before
  flushing the buffered retries — file order lies, timestamps/parent chain don't. SessionStore
  inserts younger-than-the-error retries before it; probe on the real storm session confirmed;
  Kotlin test fails with the insertion disabled.
- 10.5 premise CORRECTED by measuring both reference halves: the IDE never writes on accept —
  both VS Code panes are temp docs and the CLI does the disk write from the returned verdict.
  DiffReview rewritten to the three-verdict contract (TAB_CLOSED added, final-pane-text accept,
  dead-caller/close_tab resolution, balloon dies with the future). All three verdicts
  wire-verified + user-clicked. Contract documented in docs/ide-mcp-protocol.md § 4.
- Built the roadmap-head feature on top: dual-surface edit permissions (card + editor diff,
  first answer wins, balloon v1 — user picked both via AskUserQuestion). EditProposals rebuilds
  the post-edit content from tool input (8 JUnit tests); __perm_answered retires the card
  (fixture 11); pendingPermissions map arbitrates.
- Stale-diff-tab bug (user screenshot): `FileEditorManager.openFiles` does NOT report diff
  editors — every find-then-close ever written here closed nothing. Now the diff opens as our
  own ChainDiffVirtualFile and the held handle closes exactly that tab on resolution.
- 10.1/10.3 re-scoped after measuring: the model-facing allowlist (getDiagnostics+executeCode
  only) is byte-identical across 2.1.222–226 — upstream policy, not a regression. Server-rename
  dodge rejected: the CLI finds its IDE client by the literal name "ide".
- New backlog: VFS refresh after CLI writes (user needed "Reload from disk"); stale ide
  lockfiles surviving hot-reload. Left open: 3.1+9.10 pairing (user wants together), 5.14
  scroll feel, 8.2/8.7 tail-error replay eyeball.

## 2026-08-09 (third) — 7.4 and 8.2+8.7 fixed: register 9 → 6 open; hardware sweep cleared
- 7.4 both halves: neither payload exists in any transcript (sub-agent frames are live-only),
  so both were read VERBATIM out of the CLI binary (`strings` on
  `~/.local/share/claude/versions/2.1.226`) — which also revealed the harness envelope is
  longer than descMax, so it wasn't just ugly, it crowded the real summary off the finished
  line. `isInternalResult()` (content-keyed — the same tool's COMPLETED result is worth
  reading, so RESULT_SKIP's name-keying couldn't express it) + leading-`[harness:]` strip in
  `stripPlumbing()` (safe at position 0 only: the CLI escapes line-initial forgeries first).
- 8.2 measured first: the transcript persists the SAME top-level `error` enum live keys off —
  replay just never read it. Fix: `AUTH_BLOCKED_CODES` in RenderLimits, `icon:"auth"` status
  items resolving through chat.html's AUTH_BLOCKED map (wording stated once), and `reqError`
  suppressing the phantom summary. That phantom turned out to BE 8.7's root cause: the done
  item was the actual tail, so `tail.role === 'error'` never matched and Retry never seeded —
  8.7 closed with zero 8.7-specific code.
- Fixtures 07 + 08 committed, each proven to pin the DEFECT by running against pre-fix
  chat.html (5 and 3 failures respectively). Negative controls on every new Kotlin test.
- New trap (in fixtures + gotchas): assert on `#log`, never `document.body` — body.textContent
  includes chat.html's own script source, which now contains the very literals under test.
- User cleared the hardware re-verification sweep on real JCEF (2.9, 2.14, Escape menus, 4.4,
  6.4) and 7.4(a) live; only 5.14's scroll FEEL and an 8.2/8.7 error-tail eyeball remain.
- Next: 10.5 openDiff accept-save, then 3.1 custom commands.

## 2026-08-09 (later) — fixing round two: register 13 → 9 open, both rounds committed
- 4.4 live edit diffs: in acceptEdits (or under a saved rule, or a pre-authorized path) the CLI
  never sends `can_use_tool` at all, and the permission card was the ONLY live diff producer —
  so live was strictly poorer than replay. Fixed optimistically from the tool_use INPUT the page
  already had: stash at `content_block_stop`, ask Kotlin for the gutter line over a new
  `lineStart` bridge round-trip, mount replay's own "Applied" card at `tool_result` unless a
  permission card superseded it. MultiEdit gained an `edits[]` branch everywhere, which also
  fixed its EMPTY permission-card preview — multi-hunk edits were being approved blind.
- 5.9 plumbing strip (`RenderLimits.PLUMBING_TAGS`, shared via LIMITS) and 5.14 scroll pin
  (keys off scroll DIRECTION now — the old at-bottom test lost the pin to any mid-turn reflow).
- 5.13 addendum, and a diagnosis the user corrected: I read the duplicated-checklist screenshots
  as "collapse consecutive lists"; the user pointed out replay was the CORRECT one and live was
  missing the status titles. Real fix was relocating each live checklist under the tool line
  that asked (tool_use_id echoed on the `__tasks` frame). Lesson: when live and replay disagree,
  establish WHICH is right before designing the fix.
- 6.4 split-button caret: parts were built one-per-suggestion, but a compound command is ONE
  suggestion carrying several rules — wire-probed first, and the CLI does persist exactly the
  picked subset when the echoed suggestion is narrowed. Then two defects only live testing could
  find: the menu was clipped by `content-visibility` containment (which also defeats
  synthetic-click assertions, so the harness was structurally blind), and it resized on hover.
- User rejected my first sizing fix (always reserve 32px) — "lets do like conversations drop
  down". The conversations list uses the same hover-only gutter and is stable only because
  `#histPanel` is a FIXED width; copying that (310px + `min-width:0` to beat `.popup`'s 330px
  base) was the right answer. Copy the working idiom whole, don't reinvent half of it.
- Committed both rounds: `4a64433` (six issues) and `fe620ef` (four). Tests green; pushed.
- Next: 10.5 openDiff accept-save — Kotlin-side, so runIde + direct MCP-over-WS, not the
  headless harness. Plus the accumulated hardware re-verification sweep (state.md).

## 2026-08-09 — the fixing session: register 19 → 13 open
- Removed all three webview keyboard chords (Ctrl+N / Ctrl+Alt+G / F12) instead of re-homing
  them as IDE actions — the plugin now binds NO shortcuts (decision logged). Every capability
  kept a route: New button / `/clear`, `window.__gallery()`, the DevTools Find Action.
- Re-tested 1.7 against the installed IDE: the Escape-reopen glitch is a SANDBOX artifact
  (CDP proved Escape and click-toggle leave identical DOM). Found + fixed two real Escape
  defects instead: slash-menu re-assert (`slashEscaped`) and card menus invisible to the
  Escape chain (`closeCardMenus()` rung). Register vocabulary unified: ISSUE = open,
  RESOLVED (date) — how = closed.
- 2.8 @-mentions: the menu opened INVISIBLY all along — `#mention`/`.mi` had zero CSS, so
  `position: static` ignored `openMenu()`'s viewport coords. CSS-only fix, verified by
  injecting into the live panel. Then built out the full dismissal contract from user
  reports: outside-click close, popup exclusivity both directions, soft-reopen on
  focus/click-return (Escape stays hard) — and gave the slash menu the identical contract
  via the `slashAuto()` extraction.
- New standing item 2.14: JCEF-Linux Delete key inserts keyChar 0x7F as a tofu char.
  Two-layer workaround: manual forward-delete on keydown + capture-phase control-char strip.
- 7.3 bg chip: `.chip-btn[hidden]{display:none}` (specificity defeat) + textContent clear;
  fixture 04 gained a computed-display assertion (the old `hidden`-property check was
  structurally blind to this bug). Verified END-TO-END with a real background task watched
  over CDP: appear "1 task" → full vanish when the sleep ended.
- 2.9 drag-drop: JCEF never delivers OS file drags to the DOM — added the AWT `DropTarget`
  delivery layer (`installFileDrop`, 25 MB cap) feeding the page's `__dropFiles`; page JS
  was proven correct all along. Hardware drag still needs the next runIde.
- Big technique win, now in gotchas: the spliced-chat.html headless harness (chat.css +
  live-captured `window.LIMITS` + `__bridge` stub, events via `onClaudeEvent`, assert via
  document.title) — used for every JS fix today; and `system/commands_changed` seeds the
  slash roster for future 3.1 testing.
- Next runIde carries the accumulated re-verification list: hardware drag, hardware Delete,
  Escape flags, mention menu under real JCEF. Then 10.5 openDiff accept-save (queue head).

## Digest
One line per digested session; lessons were promoted to gotchas/decisions/conventions first.

- **2026-08-07/08** — the full 92/92 manual-test pass, 19 ISSUE notes logged. Hard-to-trigger states
  were manufactured, not skipped (network cut, auth failure, exit-2 hook, broken `.mcp.json`, CDP
  fixture injection); stitched synthetic sessions from real donor records exercised replay depth.
  Technique lives in gotchas; the two corrected beliefs (image chips, gauge on model switch) too.
- **2026-08-07** — `.claude/context/` initialized as the project's portable memory; the root
  `CLAUDE.md` (427 lines) migrated into it and deleted per the no-CLAUDE.md policy (last in git at
  `ee7e9fc`), global auto-memory folded into conventions.md, `.gitignore` un-ignoring `context/`.
