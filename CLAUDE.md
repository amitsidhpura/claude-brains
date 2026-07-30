# Claude Code for PhpStorm

Personal project: a JetBrains/PhpStorm plugin that drives the official `claude` CLI,
replicating the VS Code Claude Code extension. **Personal use only — never bundle or
redistribute Anthropic's extension.js / webview / claude.exe.**

## Repo layout
- `phpstorm-plugin/` — the plugin (Kotlin, Gradle IntelliJ Platform 2.x, JCEF webview UI)
- `docs/ide-mcp-protocol.md` — reverse-engineered protocol reference (READ FIRST)
- `docs/feature-checklist.md` — feature parity checklist + status (the working TODO list)
- `docs/limits.md` — every size cap (clamped / scrolled / truncated / volume) and where it is set
- `design/mockup.html` — static UI mockup for design iteration in a browser; approved
  changes get ported into `phpstorm-plugin/src/main/resources/webview/chat.html`
- `vscode/` — NOT in git. Extracted official VS Code extension (from
  `~/.vscode/extensions/anthropic.claude-code-<ver>/`, minus `resources/native-binary/claude.exe`).
  Re-extract locally for reference; used to reverse-engineer protocols and styles.

## Architecture (all verified working end-to-end)
- **Bridge** (`bridge/`): picks a free port (10000–65535), writes `~/.claude/ide/<port>.lock`
  `{pid, workspaceFolders, ideName, transport:"ws", runningInWindows, authToken}`, runs an
  MCP-over-WebSocket server on 127.0.0.1 checking header `x-claude-code-ide-authorization`.
  IDE tools implemented in `IdeTools.kt` (openFile/openDiff/getDiagnostics/selection/etc.).
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
`cd phpstorm-plugin && ./gradlew runIde` (launches a sandbox PhpStorm), or the same task from
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
  relative (`../phpstorm-plugin/…`) and silently resolves to nothing, so you measure an UNSTYLED
  page; write the probe copy into `design/`. (2) headless has no compositor, so
  `requestAnimationFrame` fires ~once per 400ms and rAF-driven animations (scroll glide, auto-scroll
  re-assert) read as frozen — stub `requestAnimationFrame` onto `setTimeout` to test them.
- `claude` resolved from `-Dclaude.executable` → PATH → installed VS Code extension binary.
- Resource-only changes (chat.html) need only a `runIde` restart, no Gradle sync.

## Status (see docs/feature-checklist.md for detail)
DONE: bridge+tools, streaming chat, permission cards with diffs, plan card, AskUserQuestion,
mode switcher (Manual/Edit automatically/Plan/Auto), model selector (persisted), slash-command
menu with descriptions, @-file-mentions, image paste/drop, markdown+syntax highlight, thinking,
stop/interrupt, retry, sessions (new/history/resume+replay),
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
Clickable file references (`.t-desc.path`, `.card-h code`) open in the editor via `kind:"open"`,
line-numbered Edit/Write diffs with trimmed context (unchanged anchor lines shown as context, not
±), ↑/↓ composer message history, animated scroll-to-bottom (button + on submit).

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
  earlier history in silently, no viewport jump; a long (12+ line) user message PINS while scrolling
  (`.msg-user` sticky holds over `.clip`); the scrollbar reads honest (`contain-intrinsic-size:
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
- **Renderer parity** (docs/renderer-parity.md): 27 fixed / 9 accepted / 0 open. Fixed along the
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
