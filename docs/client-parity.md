# Client parity — what the official clients show that we don't

Everything the `claude` CLI emits (or the official clients render) that the plugin currently drops
on the floor, scored against both official clients. Written 2026-08-04 against plugin `0.3.3` /
CLI+extension `2.1.220`.

> Not to be confused with `docs/renderer-parity.md`, which is an INTERNAL audit — our own three
> render paths (live / resume / mockup) against each other. This doc is EXTERNAL: us against the
> terminal TUI and the VS Code extension.

**How each column was established**

- **Us** — read out of this repo: `plugin/src/main/resources/webview/chat.html` (live renderer),
  `session/SessionStore.kt` (replay parser), `cli/ClaudeCli.kt` (stream routing),
  `ui/ChatPanel.kt` (init payload). Every claim carries a `file:line`.
- **VS Code** — grepped out of the installed bundle at
  `~/.vscode/extensions/anthropic.claude-code-2.1.220-win32-x64/webview/index.js`. Minified, so the
  evidence is a symbol or a literal string, quoted per item. This is authoritative, not recalled.
- **Terminal** — observed TUI behaviour of the same build; the CLI ships as a 265 MB native binary,
  so there is no source to cite. Items I could not confirm are marked `?` rather than guessed.
  Where a claim was checkable against strings in the binary it is noted.

**Legend** `✅` shows it · `⚠️` shows part of it · `❌` doesn't show it · `?` unverified · `—` n/a

**Take** is my recommendation, not a decision: `P1` do next · `P2` worth doing · `P3` skip, or
already deliberate.

---

## Summary

| # | Item | Terminal | VS Code | Us | Take |
|---|------|:--------:|:-------:|:--:|------|
| 1 | Sub-agent internals (nested tool calls) | ✅ | ✅ | ❌ | P2 |
| 2 | Sub-agent final report | ✅ | ⚠️ | ❌ | P1 |
| 3 | Sub-agent prompt (what it was asked) | ✅ | ✅ | ❌ | P1 |
| 4 | Background task roster | ✅ | ✅ | ❌ | P2 |
| 5 | Blank Task/Skill tool lines | ✅ | ✅ | ❌ | P1 |
| 6 | Non-Bash tool result summaries | ✅ | ✅ | ❌ | P1 |
| 7 | Tool input beyond one key | ✅ | ✅ | ⚠️ | P1 |
| 8 | Tool-returned images | ⚠️ | ✅ | ❌ | P2 |
| 9 | Bash failure detail (stderr / interrupted) | ✅ | ✅ | ⚠️ | P1 |
| 10 | Truncated-output marker | ✅ | ✅ | ❌ | P1 |
| 11 | "File was modified by the user" | ? | ✅ | ❌ | P2 |
| 12 | Server-side tools (web search blocks) | ✅ | ✅ | ❌ | P2 |
| 13 | MCP server status | ✅ | ✅ | ❌ | P1 |
| 14 | Todo / task checklist | ✅ | ✅ | ❌ | P1 |
| 15 | Compaction boundary | ✅ | ✅ | ❌ | P1 |
| 16 | Rate-limit warnings | ✅ | ✅ | ❌ | P1 |
| 17 | Cost | ✅ | ✅ | ❌ | P3 |
| 18 | Token breakdown / usage panel | ✅ | ✅ | ⚠️ | P3 |
| 19 | Real thinking-token count | ✅ | ✅ | ⚠️ | P2 |
| 20 | Account / plan / auth | ✅ | ✅ | ❌ | P3 |
| 21 | Model refusal fallback | ? | ✅ | ❌ | P2 |
| 22 | Hook activity | ✅ | ? | ❌ | P2 |
| 23 | CLI stderr | — | ? | ❌ | P2 |
| 24 | Queued messages | ✅ | ❌ | ❌ | P3 |
| 25 | Bookkeeping records on replay | ❌ | ❌ | ❌ | P3 |
| 26 | Per-record metadata (effort/branch/model) | ⚠️ | ⚠️ | ❌ | P3 |
| 27 | Injected IDE context | ❌ | ❌ | ❌ | P3 |
| 28 | Off-window history | ✅ | ✅ | ⚠️ | P3 |
| 29 | Non-Bash tool output on replay | ✅ | ✅ | ❌ | P1 |
| 30 | Silent `/effort` turns | — | — | ❌ | P3 |

---

## A. Sub-agents & background work

### 1. Sub-agent internals
The tool calls, thinking and text a `Task`/`Agent` sub-agent produces while it runs.

- **Terminal:** ✅ nested lines under the `Task(...)` header, collapsible, with a
  "N tool uses · M tokens" tail.
- **VS Code:** ✅ `system/tool_progress` events carrying `parent_tool_use_id` + `repl_call` are fed
  to the parent tool card via `addProgress({innerToolUseId, toolName, toolInput, phase})`; stream
  events are routed with their `parent_tool_use_id` into the assembler.
- **Us:** ❌ nothing reads `parent_tool_use_id` or `isSidechain` anywhere in the plugin, and
  `tool_progress` isn't in the `switch` at `chat.html:1658`.
- **Take: P2.** Real work — needs a nesting model in the DOM and a matching replay path. Do it after
  the cheap items below, and note the latent hazard: because nothing *filters* on
  `parent_tool_use_id` either, if the CLI ever streams child text to us it would interleave into the
  main transcript undistinguished. A one-line guard is worth adding even before the feature.

### 2. Sub-agent final report
The summary the sub-agent hands back.

- **Terminal:** ✅ rendered, expandable.
- **VS Code:** ⚠️ the `Agent` renderer returns `renderOutput(){return null}` — it shows the prompt,
  not the report (the report arrives as parent text anyway).
- **Us:** ❌ parsed and thrown away by `chat.html:1819` — `if (t.name !== 'Bash') return;`.
- **Take: P1.** Falls out of item 6 for free.

### 3. Sub-agent prompt
What the sub-agent was actually asked to do.

- **Terminal:** ✅ shown on expand.
- **VS Code:** ✅ `class tme … name="Agent"` renders header `Agent: <description>` plus an `IN` row
  containing the full `prompt`, click-to-open in an editor tab.
- **Us:** ❌ only `description` reaches the tool line; `prompt` is dropped.
- **Take: P1.** We already have the `IN` box idiom from Bash (`ioRow('IN', …)`); this is one branch
  in `content_block_stop`.

### 4. Background task roster
Which background tasks are running while the turn is suspended.

- **Terminal:** ✅ live list; `/bashes` for shells.
- **VS Code:** ✅ `subagentTasks` map driven by `system/task_started`, `task_progress`,
  `task_notification`.
- **Us:** ❌ `background_tasks_changed` sets a *count only* (`chat.html:1692`) so `onResult` can tell
  a suspend from the true end of a request. No names, no progress, no output.
- **Take: P2.** Ships naturally with item 1; a "2 tasks running" chip is a cheap interim.

### 5. Blank Task/Skill tool lines
`TaskUpdate`, `TaskOutput`, `TaskStop`, `Skill` have none of `RenderLimits.DESC_KEYS`, so they draw
an empty description — a tool line with nothing on it.

- **Terminal:** ✅ named and described.
- **VS Code:** ✅ bespoke renderers for `TaskOutput` (`task: "<task_id>"`) and `Skill`.
- **Us:** ❌ blank. `TaskUpdate` is the most frequent offender in local transcripts.
- **Take: P1.** Cheapest fix in the document: add `activeForm`, `skill`, `task_id` to
  `RenderLimits.DESC_KEYS` and both paths get it at once.

## B. Tool results

### 6. Non-Bash tool result summaries
Read / Grep / Glob / Search / WebFetch / WebSearch / MCP calls all render a bare tool line for us.

- **Terminal:** ✅ "Read 120 lines", "Found 17 files", first lines + `… +N lines (ctrl+o to expand)`.
- **VS Code:** ✅ per-tool bodies — `Glob` → `Found N files` / `No files found`; `Grep` →
  `N lines of output`, click opens the full output in an editor tab; `WebFetch` → `Fetched from
  <url>`; `Read` → header only, deliberately (`body(){return null}`).
- **Us:** ❌ `chat.html:1819` again.
- **Take: P1, do this first.** One `return` guards it. Match VS Code's shape — a one-line count with
  click-to-open beats dumping content into the conversation, and we already have `kind:"open"`
  plumbing for file references.

### 7. Tool input beyond one key
`RenderLimits.DESC_KEYS` picks exactly one field for the whole line.

- **Terminal:** ✅ pattern + path + flags.
- **VS Code:** ✅ `Grep` header is `Grep "pattern" (in <path>, glob: <glob>, type: <type>)`;
  `Read` appends `(lines 12-40)` from `offset`/`limit` and links the filename to that exact range.
- **Us:** ⚠️ a Grep shows its pattern but not path/glob/type; a Read shows the path but not the
  line range (`chat.html:1780`, `SessionStore.kt:519`).
- **Take: P1.** Same edit as item 6, and it stays honest across live + replay because both walk
  `RenderLimits`. Needs per-tool suffixes rather than the generic chain.

### 8. Tool-returned images
Playwright screenshots, `Read` on a PNG — `toolUseResult.isImage`.

- **Terminal:** ⚠️ can't render images inline.
- **VS Code:** ✅ image results render.
- **Us:** ❌ dropped entirely.
- **Take: P2.** We already decode base64 images for user attachments, so the renderer exists; this
  is wiring, not new UI. Watch the transcript budget (`IMAGE_BUDGET`) if it also lands in replay.

### 9. Bash failure detail
We show the concatenated `tool_result` text and colour the dot.

- **Terminal:** ✅ error output distinguished, exit status implied.
- **VS Code:** ✅ error styling plus `stderr` handling.
- **Us:** ⚠️ `stderr`, `interrupted`, `returnCodeInterpretation` and `noOutputExpected` are present
  in `toolUseResult` and read for nothing (`SessionStore.kt:558` uses stdout/stderr only as a
  fallback). A command killed by a timeout reads like a quiet success.
- **Take: P1.** `interrupted` especially — a hung-then-killed command currently looks fine.

### 10. Truncated-output marker
We cut at `RenderLimits.OUT_MAX` (2000 chars) with no indication.

- **Terminal:** ✅ `… +N lines (ctrl+o to expand)`.
- **VS Code:** ✅ click-to-open shows the whole thing.
- **Us:** ❌ silent cut; `persistedOutputPath`/`persistedOutputSize` (the CLI spilling a big result
  to a file) is ignored too.
- **Take: P1.** A silent truncation is a correctness problem, not a cosmetic one — the user can't
  tell a short result from a clipped one. `docs/limits.md` already treats "no silent caps" as the
  house rule.

### 11. "File was modified by the user"
`toolUseResult.userModified`.

- **Terminal:** ? not confirmed.
- **VS Code:** ✅ present in the result model.
- **Us:** ❌ read for nothing.
- **Take: P2.** Cheap once item 6 opens the non-Bash result path.

### 12. Server-side tools
`server_tool_use` content blocks (web search executed API-side).

- **Terminal:** ✅ shown as a search line with result counts.
- **VS Code:** ✅ the stream assembler completes `server_tool_use` blocks alongside `tool_use`.
- **Us:** ❌ `content_block_start` (`chat.html:1726`) branches on `tool_use` and `thinking` only, so
  a server-side search renders literally nothing — a silent gap in the conversation.
- **Take: P2.** Small, and it removes an unexplained hole in the timeline.

## C. Session & account state

### 13. MCP server status
Which MCP servers connected, which failed.

- **Terminal:** ✅ `/mcp`, plus a startup warning on failure.
- **VS Code:** ✅ `get_mcp_servers` / `setMcpServerEnabled` requests behind a servers UI, and MCP
  servers appear in the usage-attribution panel.
- **Us:** ❌ the `initialize` response is destructured at `ChatPanel.kt:339` for `commands` and
  `models` only; everything else is discarded.
- **Take: P1.** We already *receive* this. A server that failed to connect is currently invisible,
  which reads as "the model ignored my tools" — the worst kind of bug report.

### 14. Todo / task checklist
`TodoWrite`-style planning state.

- **Terminal:** ✅ checkbox list, re-rendered as items complete.
- **VS Code:** ✅ `class ime … name="TodoWrite"` renders header `Update Todos` + a real checklist
  (`todoListContainer`/`todoItem`/`completed` styles), and mirrors `input.todos` into a panel-level
  `todos` signal.
- **Us:** ❌ would render as a blank tool line (no matching desc key).
- **Take: P1.** High visible value for a modest renderer; the checklist is how the user follows a
  long turn.

### 15. Compaction boundary
Where the context got compacted, why, and how big it was before.

- **Terminal:** ✅ a boundary marker + the summary.
- **VS Code:** ✅ `system/compact_boundary` → a compact message built from
  `compact_metadata.trigger` + `pre_tokens`, then the synthetic user summary is attached to it;
  `totalTokens` resets to 0.
- **Us:** ❌ `/compact` is forwarded and runs, but the subtype isn't handled (`chat.html:1682`
  handles `init` + `background_tasks_changed`), so the conversation silently loses its history with
  no marker — and our context gauge doesn't reset either.
- **Take: P1.** The gauge going stale after a compact is a live wrongness, not just a missing
  feature.

### 16. Rate-limit warnings
Approaching / hit a usage limit, and when it resets.

- **Terminal:** ✅ warning line with reset time.
- **VS Code:** ✅ a top-level `rate_limit_event` type → `rateLimitWarning`, keyed by
  `status:rateLimitType` and dismissible.
- **Us:** ❌ the event type isn't in our `switch` at all; we only see the failure once it becomes an
  API error or an `is_error` result.
- **Take: P1.** Cheap (one status line through the existing `statusLine()`), and it turns a
  confusing hard failure into an expected one.

### 17. Cost
`result.total_cost_usd`, `duration_api_ms`, `num_turns`, `modelUsage`.

- **Terminal:** ✅ `/cost` and an exit summary.
- **VS Code:** ✅ `total_cost_usd` → `usageData.totalCost`, with `modelUsage[model]` supplying the
  context window and max output tokens.
- **Us:** ❌ `onResult` (`chat.html:2000`) uses wall-clock + output tokens only.
- **Take: P3.** "usage/tokens display" is already on the DEFERRED list in `CLAUDE.md` by your call.
  Worth noting we could take `modelUsage[].contextWindow` for free — it would replace the
  `[1m]`-tag sniffing the context gauge currently relies on.

### 18. Token breakdown / usage panel
Input vs. cache-read vs. cache-creation vs. output, and what's eating the window.

- **Terminal:** ✅ `/context` breakdown.
- **VS Code:** ✅ a usage panel attributing consumption to agents, skills, plugins and MCP servers.
- **Us:** ⚠️ those numbers feed the context gauge denominator and nothing else.
- **Take: P3.** Same deferral as 17.

### 19. Real thinking-token count
- **Terminal:** ✅ live token count while thinking.
- **VS Code:** ✅ `system/thinking_tokens` → `estimated_tokens`, formatted `1.2k tokens`.
- **Us:** ⚠️ we show a number, but it's our own `chars/4` guess (`chat.html:1761`) — the CLI is
  telling us the real one and we ignore it.
- **Take: P2.** Strictly an accuracy upgrade; swap the estimate for the event, keep chars/4 as the
  fallback for when the event doesn't arrive.

### 20. Account / plan / auth
- **Terminal:** ✅ `/status`, `/login`.
- **VS Code:** ✅ shows login state; handles `assistant` events with `error === "authentication_failed"`
  by opening the login flow.
- **Us:** ❌ `account` from the `initialize` response is dropped at `ChatPanel.kt:339`.
- **Take: P3** for the display (settings/non-terminal login is deferred), but the
  `authentication_failed` branch is worth stealing — right now an auth failure is an opaque error
  block with no route out of it.

### 21. Model refusal fallback
- **Terminal:** ? not confirmed.
- **VS Code:** ✅ `system/model_refusal_fallback` + `refusal_fallback` message type, with
  `retracted_message_uuids` retracting the already-rendered blocks.
- **Us:** ❌ unhandled — retracted content would stay on screen.
- **Take: P2.** Rare, but leaving retracted text visible is actively misleading.

### 22. Hook activity
- **Terminal:** ✅ hook output and blocked-tool reasons.
- **VS Code:** ? no `hook_` markers in the webview bundle; likely handled extension-side.
- **Us:** ❌ `ClaudeCli.kt:216` acks *every* non-`can_use_tool` control request with an empty
  response. A hook that fires, blocks, or errors is invisible.
- **Take: P2.** Note this isn't purely a display gap — the empty ack may be answering
  `hook_callback` with something the CLI reads as "no opinion". Worth probing before building UI.

### 23. CLI stderr
- **Terminal:** — it *is* the terminal.
- **VS Code:** ? extension-side.
- **Us:** ❌ `ClaudeCli.kt:138` sends it to the IDE log only.
- **Take: P2.** At minimum, surface stderr alongside the existing `__exit` status line on a non-zero
  exit — today a CLI crash reads as "claude process exited (1)" with the actual reason buried in
  idea.log.

### 24. Queued messages
Messages typed while a turn is in flight.

- **Terminal:** ✅ queued and displayed; the CLI writes `queue-operation` records.
- **VS Code:** ❌ no queue UI in the bundle (every `queue` hit is React internals).
- **Us:** ❌ no queue at all.
- **Take: P3.** VS Code doesn't do it either; not parity-critical.

## D. Replay-only gaps

### 25. Bookkeeping records
`SessionStore.readTranscript` handles `user` and `assistant` only. Never replayed: `attachment`
records (`task_reminder`, `deferred_tools_delta`, `agent_listing_delta`, `skill_listing`,
`command_permissions`), `mode`, `queue-operation`, `last-prompt`.

- **Terminal / VS Code:** ❌ both hide these too.
- **Take: P3.** Correct as-is. Listed so nobody "discovers" them later and assumes they're a bug.

### 26. Per-record metadata
`effort`, `attributionSkill`, `permissionMode`, `gitBranch`, `version`, `cwd` sit on nearly every
record.

- **Terminal:** ⚠️ model + branch in the status line.
- **VS Code:** ⚠️ model shown; the rest not.
- **Us:** ❌ nothing — a resumed session can't tell you which model, effort or branch a turn ran on.
- **Take: P3** as chrome, but `effort`/model per turn would be genuinely useful on long resumed
  sessions. Park it.

### 27. Injected IDE context
`<ide_opened_file>`, `<local-command-stdout>`, `<task-notification>` — stripped by
`cleanInjected()` (`SessionStore.kt:641`).

- **Terminal / VS Code:** ❌ hidden there too.
- **Take: P3.** Keep stripping.

### 28. Off-window history
Windowed replay ships the newest ~250 blocks; earlier turns aren't in the DOM until you scroll up,
so browser find can't see them.

- **Terminal / VS Code:** ✅ full scrollback.
- **Us:** ⚠️ deliberate — it's what makes a big session open fast (`docs/limits.md`).
- **Take: P3.** Keep. Revisit only if we add find-in-conversation, which would need to drive the
  chunk loader.

### 29. Non-Bash tool output on replay
Replay keeps `structuredPatch` (edits) and `answers` (ask cards); `stdout` is kept for Bash alone
(`SessionStore.kt:558`).

- **Take: P1.** Whatever item 6 shows live has to be mirrored here or the two paths drift — which
  `RenderLimits` and `docs/renderer-parity.md` exist specifically to prevent.

## E. Deliberate mutes

### 30. Silent `/effort` turns
The stream, echo and summary of an effort change are suppressed live via `effortMuted`
(`chat.html:1654`); they reappear on resume because the transcript records them.

- **Take: P3.** Already decided and documented in `CLAUDE.md` — kept as an honest audit trail.

---

## Suggested order

1. **One edit, five items:** open the non-Bash tool-result path at `chat.html:1819` and mirror it in
   `SessionStore.applyToolResult` → items 2, 6, 29, and the hooks for 8/11. Add the truncation
   marker (10) in the same pass.
2. **`RenderLimits` additions:** `activeForm` / `skill` / `task_id` for the blank lines (5), plus
   per-tool input suffixes (7).
3. **Free from data we already receive:** MCP server status (13), compaction boundary + gauge reset
   (15), rate-limit warnings (16), real thinking tokens (19).
4. **Then the todo checklist (14)**, which is a new renderer but a self-contained one.
5. **Then sub-agent nesting (1, 3, 4)** — the only item here that needs a design pass.
