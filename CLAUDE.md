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
Open `phpstorm-plugin/` in IntelliJ IDEA → Gradle task `runIde` (launches sandbox PhpStorm).
- JDK auto-provisioned via Foojay resolver; platform needs Java 21 (`jvmToolchain(21)`).
- `instrumentCode = false` is required (task crashes on MS JDK 21; we don't need it).
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

Sessions/resume: `SessionStore.readTranscript` now strips the CLI's injected bookkeeping so a
resumed thread stays clean — `cleanInjected()` drops `<local-command-caveat>` (isMeta) /
`<local-command-stdout>` / `<task-notification>` user messages and collapses a
`<command-name>/x</command-name>…<command-args>y</command-args>` block to a compact `/x y`.

NEXT (user's active focus): **session polish — many known issues in resume/history**. Resume
replay is still lossy vs. live rendering (readTranscript only reproduces user text + assistant
text + bare tool-name lines; no diffs/cards/thinking/IN-OUT/attachments/images, no ordering guarantees
for interleaved sidechains, titles/summary heuristics rough). Audit `SessionStore.kt` +
`renderTranscript()` together and decide how faithfully to rebuild each block on resume.
Then Phase 3: verify the ported UI in a runIde sandbox against real streaming (token meta from
message_delta.usage, undo placement, ask Other/multiSelect, fail dots) — the Ctrl+Alt+G gallery
renders every transient state without driving the CLI. Then: editor-title accept/reject,
@-symbol mentions, conversation-level rewind, worktrees, extensibility status view.
