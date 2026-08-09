# Journal

Dated session log, newest first. One compact entry per session: what was done, what was
learned, what's next. Entries older than ~10 sessions get digested (lessons promoted first).

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

## 2026-08-07/08 — the full manual-test pass
- Ran the entire standing checklist (`docs/manual-test.md`): 92/92 ticked over two days,
  19 ISSUE notes logged inline (register summarized in state.md). Every hard-to-trigger item
  was manufactured rather than skipped: network cut for the retry storm, auth-failure for
  error/Retry, hook-block via a temporary exit-2 settings hook, broken `.mcp.json` for the
  MCP notice, TUI `/rename` for custom titles, CDP fixture injection for rate-limit and
  refusal-fallback (render path only, provenance noted in the ticks).
- Built stitched synthetic sessions from REAL donor transcript records (turn-boundary
  slices, uuid remapping, `./gradlew probe` as the verification gate) to exercise replay
  richness, the 250-block window, the 4 MB image budget, and the orange gauge. Technique
  worth reusing; fixtures left in the testing project (see state.md).
- Verified the bridge end-to-end by speaking MCP-over-WS to it directly (lockfile token +
  "mcp" subprotocol) — which is how the openDiff accept-path bug (FILE_SAVED without a
  write) and the CLI 2.1.226 model-facing tool restriction were isolated from each other.
- Gallery verified via `window.__gallery()` over CDP after discovering all webview keyboard
  chords are dead on this machine.
- Corrected two prior beliefs: replayed image chips saying "file.jpg" is EXPECTED (the
  transcript persists the bare API image block, no filename); and the gauge not moving on a
  model switch was the >window→1M promotion rule working, not a bug.
- Sandbox note: `runIde` in a background shell needs `JAVA_HOME=~/.jdks/jdk-21.0.12+8`
  prefixed (the .zshrc export doesn't reach it).

## 2026-08-07
- Initialized `.claude/context/` as the project's portable memory (`/context init`).
- Migrated the root `CLAUDE.md` (427 lines) into overview / conventions / gotchas / decisions /
  state / backlog / glossary, then deleted it per the no-CLAUDE.md policy (recoverable from git
  history, last at commit `ee7e9fc`).
- Migrated the global auto-memory ("commit only when asked") into conventions.md.
- Un-ignored `.claude/context/` in `.gitignore` (the rest of `.claude/` stays ignored).
- Deliberately skipped `runbook.md` — release procedure already lives in `docs/release.md`.
- Noted: `.claude/skills/` (the `/context` skill itself) is still git-ignored, so the workflow
  doesn't travel to a fresh clone — un-ignore it if portability is wanted (open next step).
- Nothing committed yet; the whole migration awaits one commit (user commits on request only).
