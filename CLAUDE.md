# Claude Code for PhpStorm

Personal project: a JetBrains/PhpStorm plugin that drives the official `claude` CLI,
replicating the VS Code Claude Code extension. **Personal use only — never bundle or
redistribute Anthropic's extension.js / webview / claude.exe.**

## Repo layout
- `phpstorm-plugin/` — the plugin (Kotlin, Gradle IntelliJ Platform 2.x, JCEF webview UI)
- `docs/ide-mcp-protocol.md` — reverse-engineered protocol reference (READ FIRST)
- `docs/feature-checklist.md` — feature parity checklist + status (the working TODO list)
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
  --permission-mode <mode>` with env `CLAUDE_CODE_SSE_PORT=<port>` and
  `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1`. Routes control protocol
  (`can_use_tool`, `initialize`, `set_model`, `set_permission_mode`, `interrupt`,
  `rewind_files`) separately from conversation events.
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
- `rewind_files` requires `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1`, a git repo, and a
  client-supplied `uuid` on sent user messages; success response is `{canRewind, skippedLinks}`
  (no filesChanged) — use dry_run first to decide whether to show Undo.
- Tool inputs stream via `input_json_delta`; tool results arrive as `user` events with
  `tool_result` blocks (used for Bash IN/OUT boxes).

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
- `claude` resolved from `-Dclaude.executable` → PATH → installed VS Code extension binary.
- Resource-only changes (chat.html) need only a `runIde` restart, no Gradle sync.

## Status (see docs/feature-checklist.md for detail)
DONE: bridge+tools, streaming chat, permission cards with diffs, plan card, AskUserQuestion,
mode switcher (Manual/Edit automatically/Plan/Auto), model selector (persisted), slash-command
menu with descriptions, @-file-mentions, image paste/drop, markdown+syntax highlight, thinking,
stop/interrupt, retry, per-turn file rewind (dry-run gated), sessions (new/history/resume+replay),
Ctrl+N, VS Code-style document UI (user boxes, dot blocks, tool lines with IN/OUT, composer,
mode/model popups, top+bottom scroll fades), tool-window icon (grey/white on selection), per-request
completion summary (✻ Baked for Ns · ↓ tokens; background-task suspend/resume aware),
dev gallery (Ctrl+Alt+G renders every transient state in the live webview),
line-numbered Edit/Write diffs with trimmed context (unchanged anchor lines shown as context, not
±), ↑/↓ composer message history, animated scroll-to-bottom (button + on submit).

DEFERRED (user's choice, do last): settings page, non-terminal login, conversation tabs,
usage/tokens display, auto-include selection / Alt+K, voice input.

UI: chat.html is fully ported to the mockup design (Phase 1 chrome + Phase 2 renderer, see
docs/port-plan.md). Styles live ONLY in webview/chat.css (spliced at `<!--CSS-->`; mockup links
the same file). Turn model: `.turn` > sticky `.msg-user` (in-box undo) + `.blk` dot blocks +
`.tool-line` (green/red dot) with `.io` IN/OUT + canon `.card`/`.ask` cards (plan card shares the
`--warn` feedback surface). Live thinking (chevron collapse, live Ns timer, hide-0s) + flower-spinner
working line (live chars/4 token estimate; hide-0) + `.done` completion summary at request end. Status
lines (⏹ Stopped / ↩ Reverted) hang their glyph (`.s-ic`, SVG or emoji) in the 22px dot column via
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

NEXT: **Phase 3 — verify in a runIde sandbox** (nothing since the resume rewrite has been run;
this laptop has no JDK/Gradle, so it was validated by parsing real JSONL + `node --check` only).
Check: resumed threads (thinking/diffs/IN-OUT/images/ask cards render, big sessions stay
responsive — a 5.7 MB session yields ~876 blocks), history titles now read as ai-titles, then the
live path against real streaming (token meta from `message_delta.usage`, undo placement, ask
Other/multiSelect, fail dots). Ctrl+Alt+G renders every transient state *including* a replayed
transcript sample, without driving the CLI.
Replay also reconstructs, from fields the parser previously ignored: plan cards (`ExitPlanMode`
`input.plan`), `⏹ Stopped` status lines (`interruptedByShutdown`, replacing the bogus
"[Request interrupted by user]" user box), refusal state (`toolDenialKind` → `✗ Rejected` /
`✗ Kept planning` / `✗ Cancelled` instead of a false `✓ Applied`), the `✻ … for Ns · ↓ N tokens`
summary (per-request `message.usage.output_tokens` + timestamp span, skipped when tokens are 0),
and thinking durations (record-to-record wall time — an approximation, live times the stream).
NB: many sessions store `thinking` blocks with an EMPTY body and only a `signature`, so those
replay as nothing at all; ~2.1k of 6.6k local thinking blocks carry text.
Known gaps deliberately left:
sidechain/subagent ordering untested (no `isSidechain` records in local sessions yet).
Then: editor-title accept/reject, @-symbol mentions, conversation-level rewind, worktrees,
extensibility status view.
