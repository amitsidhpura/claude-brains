# Claude Brains — Claude Code for JetBrains IDEs (unofficial)

Personal project: a JetBrains/PhpStorm plugin that drives the official `claude` CLI,
replicating the VS Code Claude Code extension. **Personal use only — never bundle or
redistribute Anthropic's extension.js / webview / claude.exe.**
Renamed from "Claude Code (Syncroze)" 2026-07-31: plugin id `io.github.amitsidhpura.claude-brains`,
packages `io.github.amitsidhpura.claudebrains.*`, no syncroze references in the plugin.
Distribution is Path B — custom plugin repo on `github.com/amitsidhpura/claude-brains`
(NOT the JetBrains Marketplace); process in `docs/release.md`.

## Repo layout
- `plugin/` — Kotlin source + Gradle root (IntelliJ Platform Gradle Plugin 2.x, JCEF
  webview UI); every `./gradlew` command runs from here
- `docs/ide-mcp-protocol.md` — reverse-engineered protocol reference (READ FIRST)
- `docs/feature-checklist.md` — feature parity checklist + status (the working TODO list)
- `docs/limits.md` — every size cap (folded / scrolled / truncated / volume) and where it is set
- `design/mockup.html` — static UI mockup for design iteration in a browser; approved
  changes get ported into `plugin/src/main/resources/webview/chat.html`
- `vscode/` — NOT in git. Extracted official VS Code extension (from
  `~/.vscode/extensions/anthropic.claude-code-<ver>/`, minus `resources/native-binary/claude.exe`).
  Re-extract locally for reference; used to reverse-engineer protocols and styles.

## Architecture (all verified working end-to-end)
- **Bridge** (`bridge/`): picks a free port (10000–65535), writes `~/.claude/ide/<port>.lock`
  `{pid, workspaceFolders, ideName, transport:"ws", runningInWindows, authToken}`, runs an
  MCP-over-WebSocket server on 127.0.0.1 checking header `x-claude-code-ide-authorization`,
  advertising WebSocket subprotocol "mcp" (the CLI's WS client aborts without the echo).
  IDE tools implemented in `IdeTools.kt` (openFile/openDiff/getDiagnostics/selection/etc.).
  CRITICAL (learned 2026-08-01, §3b of the protocol doc): a stream-json CLI NEVER auto-connects
  from `CLAUDE_CODE_SSE_PORT` — that discovery is TUI-only. ClaudeCli passes the bridge via
  `--mcp-config` (server "ide", type "ws" — "ws-ide" is filtered from user config) so the model
  gets `mcp__ide__*` tools; env var + lockfile stay for terminal-launched TUI sessions.
- **CLI** (`cli/ClaudeCli.kt`): spawns `claude --input-format stream-json --output-format
  stream-json --include-partial-messages --verbose --permission-prompt-tool stdio
  --permission-mode <mode>` with env `CLAUDE_CODE_SSE_PORT=<port>`. Routes control protocol
  (`can_use_tool`, `initialize`, `set_model`, `set_permission_mode`, `interrupt`)
  separately from conversation events.
- **UI** (`ui/ChatPanel.kt` + `resources/webview/chat.html`): JCEF panel in a right-anchored
  tool window. Single JS<->Kotlin channel: `window.__bridge(json)` up, `window.onClaudeEvent(line)` down.
- **Sessions** (`session/SessionStore.kt`): reads `~/.claude/projects/<enc-cwd>/*.jsonl`
  (enc = cwd with non-alphanumerics -> `-`); resume via `--resume <id>`; transcript replayed into UI.

## Key protocol facts (hard-won, don't rediscover)
- CLI emits `system/init` only after the first user turn; send control_request
  `{subtype:"initialize"}` at startup to get `{commands, models, account}` immediately.
- AskUserQuestion: answer by replying to its `can_use_tool` with
  `updatedInput={questions, answers:{"<question>": "<label(s)>"}}` — plain allow returns
  "user did not answer".
- Permission gate ONLY works with `--permission-prompt-tool stdio`.
- Permission modes & suggestions (probed 2.1.220, full findings in docs/ide-mcp-protocol.md §5b):
  `acceptEdits` covers EDITS ONLY (Bash still asks — correct, not a bug); `bypassPermissions` can't
  be entered via `set_permission_mode` (refused unless launched `--dangerously-skip-permissions`,
  which guts every other mode) → entering Auto relaunches with `--permission-mode bypassPermissions`
  + `--resume`; `system/init`+`system/status` broadcast `permissionMode` (plan approval drops to
  `default`) — the chip follows those, not the last request; `can_use_tool` carries
  `permission_suggestions` → echo the accepted one as `updatedPermissions` (malformed = silently
  dropped); sandbox-escape prompts (`blocked_path`) re-ask no matter what is granted, so suggestion
  buttons are stripped there. Refused control requests answer `subtype:"error"` — surfaced as
  `__ctl_error`, never swallowed.
- Per-turn file rewind was REMOVED (2026-07-30). If it ever returns: `rewind_files` needs
  `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1`, a git repo and a client-supplied `uuid` on sent
  user messages; success is `{canRewind, skippedLinks}` (no filesChanged), so dry_run first.
  Dropping the env var also stops the CLI writing file-history snapshots into every transcript.
- Tool inputs stream via `input_json_delta`; tool results arrive as `user` events with
  `tool_result` blocks (used for Bash IN/OUT boxes).
- Every stream line carries a `uuid`, and the `assistant` event's uuid is the SAME uuid the CLI writes
  into the transcript record (timestamp too) — verified by diffing a live run against its own JSONL.
  That is the only handle for tying a live render to its replayed twin; the completion-summary verb
  uses it. Don't assume "live-only state can't be persisted" without checking for a shared uuid.
- One API message is persisted as one record PER CONTENT BLOCK (`['thinking']`, `['tool_use']`,
  `['text']`…), and every one of those records repeats the same *cumulative* `message.usage`. Anything
  summing `output_tokens` over records must dedupe by `message.id` or it over-reports by the block
  count (measured 2.45x across local sessions). Live is immune — `message_delta.usage` fires once per
  message. 2546 split messages checked: none disagreed on usage, so the first record is authoritative.

## Build / run
`cd plugin && ./gradlew runIde` (launches a sandbox PhpStorm), or the same task from
IntelliJ. `./gradlew compileKotlin` for a fast type-check; `buildPlugin` produces the installable
zip in `build/distributions/`.
- Build JVM must be **Java 21**. Do NOT use the JBR bundled with a recent PhpStorm — 2026.x ships
  JDK 25 and Gradle 8.10.2 refuses to run on anything above 23. Linux dev box uses Temurin 21 at
  `~/.jdks/jdk-21.0.12+8` (also IntelliJ's auto-detect dir); `~/.zshrc` exports `JAVA_HOME`.
- Gradle 8.10.2 at `~/.local/opt/gradle-8.10.2`, matching `gradle-wrapper.properties`. The wrapper
  (`gradlew` + `gradle-wrapper.jar`) IS committed — without the jar a clean clone can't bootstrap.
- `runIde` runs on the JBR bundled inside the *downloaded* `phpstorm("2024.2")` dependency
  (JBR 21 `-jcef`, has `libcef.so`), NOT on the build JVM — that's what makes the JCEF webview work.
  First run downloads ~1 GB into `~/.gradle/caches`; after that builds are seconds.
- `instrumentCode = false` is required (task crashes on MS JDK 21; we don't need it).
- `buildSearchableOptions = false` — no settings UI yet, so it only cost a headless IDE launch
  per build. Re-enable when the deferred settings page lands.
- `./gradlew test` — plain JUnit 5 over `SessionStore` (no IntelliJ deps), against a fixture of
  real trimmed CLI records in `src/test/resources/fixtures/`. Do NOT re-add
  `testFramework(TestFrameworkType.Platform)` without platform tests: it registers a JUnit
  `LauncherSessionListener` that can't instantiate outside an IDE fixture and kills the test JVM.
  `SessionStore.claudeHome` is `internal var` so tests point at a temp tree, not the real `~/.claude`.
- `./gradlew probe --args="<projectPath> <sessionId>"` — dump the replay blocks for any real
  session without launching the IDE. Fastest way to tell a parser bug from a renderer bug.
- Measuring layout in headless Chrome (`--dump-dom` + a probe script) settles CSS questions fast,
  with two traps: (1) don't copy `mockup.html` elsewhere to inject a probe — its stylesheet link is
  relative (`../plugin/…`) and silently resolves to nothing, so you measure an UNSTYLED
  page; write the probe copy into `design/`. (2) headless has no compositor, so
  `requestAnimationFrame` fires ~once per 400ms and rAF-driven animations (scroll glide, auto-scroll
  re-assert) read as frozen — stub `requestAnimationFrame` onto `setTimeout` to test them.
  (3) `ResizeObserver` EXISTS but never (reliably) fires headless — same missing-compositor cause.
  Code that keeps a measurement in sync via RO can't be validated there; give it a second trigger
  (e.g. re-measure on the interaction) and test that instead, or it reads as a false "stale" bug.
- `claude` resolved from `-Dclaude.executable` → PATH → installed VS Code extension binary.
- REAL-IDE compat: 2025.x+ moved JCEF out of the platform core into a bundled plugin
  (`com.intellij.modules.jcef`) — without the optional `<depends>` on it, ChatPanel dies with
  `NoClassDefFoundError: JBCefBrowser` on PhpStorm 2026.2 while the 2024.2 `runIde` sandbox
  works fine (JCEF still in core there). Optional because the module id doesn't exist on 242.
  The sandbox can't catch this class of bug; smoke-test installs in the real snap IDE.
- Resource-only changes (chat.html) need only a `runIde` restart, no Gradle sync.
- Plugin Verifier hygiene (0 warnings on 242→262, achieved post-0.2.0): blocking reads go
  through `readLocked {}` (Threads.kt) — `ReadAction.compute` AND Kotlin's `runReadAction {}`
  are both deprecated on 2026.1, `Application.runReadAction(Computable)` is the one blocking-read
  API clean everywhere (checked in platform bytecode via javap on the Gradle-cached IDEs).
  Diagnostics come from `DocumentMarkupModel` + `HighlightInfo.fromRangeHighlighter` (public),
  not `DaemonCodeAnalyzerImpl` (internal). `FileSaverDescriptor` is built via reflection
  (ChatPanel.saveDescriptor): 2025.1+ deprecates the vararg ctor, its replacement doesn't exist
  on 242 — reflection is the only warning-free way to keep 2024.x support. Deprecation is
  per-IDE-version; the same zip shows MORE warnings on NEWER IDEs, never the reverse.

## Status (see docs/feature-checklist.md for detail)
DONE: bridge+tools, streaming chat, permission cards with diffs, plan card, AskUserQuestion,
mode switcher (Manual/Edit automatically/Plan/Auto), model selector (persisted), slash-command
menu with descriptions, @-file-mentions, image paste/drop, markdown+syntax highlight, thinking,
stop/interrupt, retry, sessions (new/history/resume+replay),
header conversation title + Refresh (re-resume) button,
Ctrl+N, VS Code-style document UI (user boxes, dot blocks, tool lines with IN/OUT, composer,
mode/model popups, top+bottom scroll fades), tool-window icon (grey/white on selection), per-request
completion summary (✻ Baked for Ns · ↓ tokens; background-task suspend/resume aware),
dev gallery (Ctrl+Alt+G renders every transient state in the live webview),
context gauge in the composer (share of the window the next request carries — latest request's
input+cache_read+cache_creation, NOT a sum; orange ≥50%; click sends /compact via `sendTurn`).
The window is not a number in the initialize payload: the 1M variants are tagged `[1m]`, on
`resolvedModel` for default/opus but on `value` for fable — check both. Usage above the known
window still promotes to 1M, so an untagged future model can't pin a wrong denominator.
Long-session performance (see docs/limits.md for the measurements and the traps): off-screen
`.turn-body`s skip layout/paint via `content-visibility` (2000 turns: 151ms→12ms initial, 84ms→13ms
per reflow), and replay is WINDOWED — Kotlin parses the whole file but ships only the newest ~250
blocks cut at a turn boundary (`alignedStart`), 89–95% smaller frames; earlier chunks load silently
as you scroll within 600px of the top, prepended viewport-anchored so nothing jumps.
Permission mode is PERSISTED (like the model) and every CLI (re)start launches with it; the chip is
CLI-driven (`__mode` seed + `permissionMode` on system events), Auto = relaunch-with-resume
(`__modeRestarted` ends a hanging busy state), refused control requests render as `__ctl_error`
blocks, and permission cards grow buttons from the CLI's `permission_suggestions` (accept-all-edits /
always-allow / allow-directory; stripped on `blocked_path` cards where no grant stops the re-ask).
ALL `addRules` suggestions merge into ONE "Always allow" button that echoes every rule index at
once (compound commands arrive as one suggestion per sub-command — granting half would re-prompt;
the wire `sugg` field is a comma-separated index list, done-text names the granted rules).
Clickable file references (`.t-desc.path`, `.card-h code`) open in the editor via `kind:"open"`,
line-numbered Edit/Write diffs with trimmed context (unchanged anchor lines shown as context, not
±), ↑/↓ composer message history, animated scroll-to-bottom (button + on submit).

Slash commands (finalized 2026-07-30, tracked in docs/slash-commands.md): the menu is an
ALLOWLIST, not the full CLI roster. Over `--input-format stream-json` there is no interactive
terminal, so only turn-producing commands actually work; unconfirmed ones are HIDDEN (and refused
if typed). `cmdKind()` buckets each: `CMD_NATIVE` (handled in the IDE) / `CMD_ALLOWED` (forwarded
as a turn) / else `tui` (hidden). Enabled today: `/compact` (→ CLI) + `/clear` (native → `kind:'new'`,
identical to the New button: wipe view, fresh empty session, old one on disk & resumable). `/model`
and `/effort` are deliberately hidden — the composer already has the model chip+dropdown (search /
custom) and the effort slider. The init `commands` payload is only `{name,description,argumentHint}`
— NO type field — so the allowlist is the only lever; enable one command at a time as each is
verified in `runIde`, then tick it in the doc. Menu pick RUNS the command immediately unless its
`argumentHint` marks a required `<arg>` (`cmdNeedsArg`: `[opt]` / `<optional …>` / empty = no-arg →
run; a bare `<x>` → insert `/cmd ` and wait). Custom models parse as `value : Display Name :
Description` (mirrors the CLI shape) and render through the same `chipName` as built-ins.
Mode chip carries the effort as a plain-text suffix ("Ask before edits (High)") via `syncModeUI`.
Effort changes are SILENT like the mode picker: no `set_effort`/`set_thinking_level` control request
exists (probed — only `set_max_thinking_tokens`, a token count), so the slider rides a `/effort` turn
whose stream/echo/summary is muted in `onClaudeEvent` via `effortMuted` (idle-gated so a mid-turn
tweak can't eat a real turn). Both verified in `runIde` 2026-07-30: `/clear` from the menu works,
and the effort mute holds live; resumed sessions DO show the `/effort` turn (transcript records it,
replay doesn't filter) — accepted as an honest audit trail, no filter planned.

DEFERRED (user's choice, do last): settings page, non-terminal login, conversation tabs,
usage/tokens display, auto-include selection / Alt+K, voice input.

UI: chat.html is fully ported to the mockup design (Phase 1 chrome + Phase 2 renderer, see
docs/port-plan.md). Styles live ONLY in webview/chat.css (spliced at `<!--CSS-->`; mockup links
the same file). Turn model: `.turn` > sticky `.msg-user` + `.blk` dot blocks +
`.tool-line` (green/red dot) with `.io` IN/OUT + canon `.card`/`.ask` cards (plan card shares the
`--warn` feedback surface). Live thinking (chevron collapse, live Ns timer, hide-0s) + flower-spinner
working line (live chars/4 token estimate; hide-0) + `.done` completion summary at request end. Status
lines (⏹ Stopped) hang their glyph (`.s-ic`, SVG or emoji) in the 22px dot column via
`statusLine()`; timeline lines are 13px. Diff gutter line numbers come from `ChatPanel.editLineStart`
(finds old_string in the file, reading FRESH from disk since the CLI edits out-of-band). Auto-scroll
pinning re-asserts on rAF so empty-then-innerHTML blocks land fully at bottom. Editing chat.html
markup? mirror it in the mockup fixture too.

Sessions/resume: `SessionStore.readTranscript` returns rich `JsonObject` blocks (not flat
role+text) so replay matches live rendering — roles `user` (text + base64 images, 4 MB budget then
name-only chips) / `thinking` / `assistant` / `tool` (desc + `.path`, Bash IN/OUT, `isError`) /
`ask`. Tool items are indexed by `tool_use_id` and patched when their result record is read.
Diffs come from `toolUseResult.structuredPatch` — authoritative hunks with real line numbers, so
replay uses `patchRows()` instead of the live path's prefix/suffix heuristic (`renderEditDiff`
stays the fallback). Replayed cards are resolved, non-interactive: edits show `✓ Applied`
("Applied", not "Accepted" — the transcript can't tell a manual approval from an auto mode), ask
cards mirror `renderAsk`'s structure exactly — `.ask-h` tab header (clickable), one `.ask-panel`
per question, checkbox vs radio glyphs by `multiSelect`, free text as the `Other` row + disabled
field — marked `.ask-done` (hover/cursor suppressed). That class must NOT be `.done`: that's the
completion-summary line and its 22px dot-column indent silently shifted the whole card right.
Geometry verified equal to live in headless Chrome. `cleanInjected()` still
drops `<local-command-caveat>` (isMeta) / `<local-command-stdout>` / `<task-notification>` and
collapses `<command-name>/x</command-name>…<command-args>y</command-args>` to `/x y`.
Titles: the CLI writes `ai-title` records (no `summary` in current sessions) — `titleOf()` prefers
summary → last ai-title → first user message.

Phase 3 — sandbox verification COMPLETE (2026-07-30, confirmed live in `runIde`, not just headless
Chrome). Verified working:
- the performance batch: a BIG session opens fast and lands at the BOTTOM (tail pre-render →
  `scrollIntoView` on the last element → settle loop with late re-checks); scrolling up streams
  earlier history in silently, no viewport jump; a long user message PINS while scrolling
  (`.msg-user` sticky holds over the collapsed `.fold` state); the scrollbar reads honest (`contain-intrinsic-size:
  auto 250px`); "New conversation" from a windowed session doesn't pull the old session's chunks.
- images past the 4 MB budget fall back to name-only chips; multi-question ask card tab switching
  works; thinking with real text (via `syncroze-core`); live token meta from `message_delta.usage`,
  ask Other/multiSelect, fail dots.
- streaming rAF throttle holds under real JCEF frame timing: text_delta marks dirty and `flushMd()`
  renders once per frame (was O(n²) per token via `renderMd`), flushed synchronously wherever
  `curBubble` is finalized.
Ctrl+Alt+G renders every transient state without driving the CLI; `./gradlew probe` dumps the
replay blocks for any session, which is the fastest way to split a parser bug from a renderer bug.
Replay also reconstructs, from fields the parser previously ignored: plan cards (`ExitPlanMode`
`input.plan`), `⏹ Stopped` status lines (`interruptedByShutdown`, replacing the bogus
"[Request interrupted by user]" user box), refusal state (`toolDenialKind` → `✗ Rejected` /
`✗ Kept planning` / `✗ Cancelled` instead of a false `✓ Applied`), the `✻ … for Ns · ↓ N tokens`
summary (per-request `message.usage.output_tokens` + timestamp span, skipped when tokens are 0),
and thinking durations (record-to-record wall time — an approximation, live times the stream).
NB: many sessions store `thinking` blocks with an EMPTY body and only a `signature`, so those
replay as nothing at all; ~2.1k of 6.6k local thinking blocks carry text.
Known gaps deliberately left:
- sidechain/subagent ordering untested (no `isSidechain` records in local sessions yet)
- tool lines still blank for tools whose input has none of the desc-chain keys
  (`description`/path/`pattern`/`query`/`url`/`element`/`filename`/`target` — the last three are
  MCP/Playwright, added 2026-07-30): `TaskUpdate` (has `activeForm`, ~308 local occurrences — the
  biggest), `Skill` (`skill`), `TaskOutput`/`TaskStop` (`task_id`). Each needs a bespoke key, so
  the generic chain won't do it. Bash and AskUserQuestion are blank BY DESIGN.
- windowed replay: DOM search (browser find, any future find-in-conversation) only sees loaded
  blocks.

Audits (2026-07-30, all closed — findings live in the docs):
- **Renderer parity** (docs/renderer-parity.md): 27 fixed / 10 accepted / 0 open (silent `/effort`
  turns reappearing on resume was the last one — user accepted it as an audit trail). Fixed along the
  way: API-error records replay as the live `.error` block, Retry after a resumed tail error
  (replay seeds `lastUser`), IMAGE_BUDGET spent newest-first so the visible tail keeps its bytes.
- **Spacing/radius**: three radius tiers (12px panels / 6px `--radius` / 3px micro — no 4px left),
  one 12px text edge inside the ask card, history panel wears the popup-family metrics
  (`10px 14px 6px` header + divider on all titled popups), `--accent-rgb` so tints can't desync,
  both dropdown lists cap at exactly 5×54px rows (docs/limits.md).
- **Code optimization** (all tranches shipped): rAF-throttled streaming render (see NEXT),
  `FileStats` (mtime,size) cache — history opens on unchanged files do zero file reads (was ≤3
  reads/file/click); `clearLogUI` resets `lastUser`/`toolsById`/`openTool` (cross-session Retry +
  detached-DOM leaks); swallowed exceptions now log; `vf.charset` in DiffReview; dead code gone
  (`uuid()`, `sendToClaude`, `sendUserText` pair). Dedup: shared block builders in chat.html
  (`ioRow/ioBox/toolLine/errorBlock/thinkBlock/planCardHtml/writeDiffHtml/askTabsHtml/wireAskTabs/
  resolveAsk`) — live and replay draw through the SAME functions now, so they cannot drift;
  Kotlin `textParts()`/`pushFrame()`/`chunkItems()`/`findVFile()` (Vfs.kt, the one front door for
  path lookups). Browser-verified: gallery + synthetic streams + ask interactions all green.

Then: editor-title accept/reject, @-symbol mentions, worktrees,
extensibility status view.
