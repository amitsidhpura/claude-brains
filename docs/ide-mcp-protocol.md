# Claude Code IDE ↔ CLI protocol (reverse-engineered)

Source: `vscode/extension.js` from `anthropic.claude-code` **v2.1.220** (win32-x64), decompiled/minified.
§9 was later enumerated from the **v2.1.222** linux-x64 bundle (binary + both extension halves).
Purpose: document the "IDE integration" bridge so a PhpStorm/JetBrains plugin can implement the
IDE side and drive the **official `claude` CLI** unchanged. Symbol names below (e.g. `zG`, `UZt`)
are the minifier's; they're kept as breadcrumbs so you can re-locate the code after an update.

> This is an interop spec derived by reading a locally-installed, proprietary binary for
> **personal use**. Do not redistribute Anthropic's `extension.js`, `webview/`, or `claude.exe`.

---

## 1. Big picture

The extension is **not** an AI client. It is a thin host that:

1. **Starts a local MCP server** (WebSocket transport, `127.0.0.1` only).
2. **Writes a lock file** advertising the port + auth token to the CLI.
3. **Spawns / attaches the `claude` CLI**, passing the port via env var.
4. The CLI connects back over WebSocket and calls **IDE tools** (open file, show diff,
   read selection, diagnostics, …).
5. A separate **webview** (`webview/index.js`) renders the chat UI and talks to the host
   via `postMessage`.

```
┌────────────────────────┐         ws://127.0.0.1:<port>          ┌──────────────────┐
│  IDE plugin (host)      │  <───── MCP over WebSocket ─────────►  │  claude CLI       │
│  - MCP server (tools)   │         header: x-claude-code-         │  (claude.exe)     │
│  - writes lock file     │         ide-authorization: <token>     │                   │
│  - spawns CLI           │                                        │  reads lock file, │
│  - hosts webview UI     │  env: CLAUDE_CODE_SSE_PORT=<port>      │  sends token      │
└────────────────────────┘                                        └──────────────────┘
```

There are **two channels**, do not confuse them:
- **IDE-MCP (this doc):** CLI is the MCP *client*, IDE is the MCP *server* exposing editor tools.
- **Webview channel:** host ↔ chat UI, `postMessage`/`onDidReceiveMessage` (a VS Code-specific
  `acquireVsCodeApi` bridge). For JetBrains you'd replace this with JCEF ↔ Kotlin messaging.

---

## 2. The lock file (discovery + auth)

Written by `zG(port, authToken)`; directory resolved by `I0e()` → `es()/ide`.

- **Directory:** `${CLAUDE_CONFIG_DIR || ~/.claude}/ide/` (created `mode 0700`).
- **Filename:** `<port>.lock` (the chosen port number, e.g. `52140.lock`).
- **Permissions:** file written `mode 0600`.
- **Contents (JSON):**

```json
{
  "pid": 12345,                          // process.ppid (the CLI's parent = IDE)
  "workspaceFolders": ["D:/sites/foo"],  // absolute fs paths of open roots
  "ideName": "Visual Studio Code",       // vscode.env.appName
  "transport": "ws",
  "runningInWindows": true,              // process.platform === "win32"
  "authToken": "<random-uuid>"           // MUST equal the WS header the server checks
}
```

- On workspace-folder change the host rewrites the same file (`zG(x, f)`), keeping the token.
- On shutdown the file is deleted (`P0e(port)` → `unlink`, ignores `ENOENT`).

**Auth token:** a `crypto.randomUUID()` (symbol `f`, from `L0e.randomUUID()`). The **same value**
is written as `authToken` in the lock file and checked on every WS connection. (There is also a
helper `_s()` = `randomBytes(16).hex` used for message `requestId`s — *not* the auth token.)

---

## 3. Transport + handshake

Server setup lives in `U0e(...)` / the `.listen` closure.

1. **Port pick:** `LZt()` = `Math.floor(random()*55536)+10000` → range **10000–65535**.
   Availability probed by `NZt(port)` (tries to `listen`, closes, returns bool); retries **<50×**,
   else throws `"Failed to find an available port after multiple attempts"`.
2. **Bind:** `http.createServer()` + `new WebSocketServer({ server })`, then
   `server.listen(port, "127.0.0.1")` — **loopback only**.
3. **Write lock file** with the port + token (§2).
4. **Export env** `CLAUDE_CODE_SSE_PORT=<port>` into the CLI's environment (`$0e(...)`).
5. **On `connection`:** the server reads header
   `x-claude-code-ide-authorization`. If it ≠ the token → `ws.close(1008, "Unauthorized")`.
   Only **one** client is kept; a new valid connection disconnects the previous one
   (and unregisters its diagnostics subscription).
6. Messages are **JSON-RPC 2.0** MCP frames (`jsonrpc:"2.0"`). Standard MCP methods
   `initialize`, `tools/list`, `tools/call` are handled (`setRequestHandler`).

**MCP server name** (`UZt()`), by host app:
`"Claude Code VSCode MCP"` / `"Claude Code Cursor MCP"` / `"Claude Code Windsurf MCP"` /
`"Claude Code <appName> MCP"` (default). Internal id string seen: `claude-code-ide`.
Version = the extension's `package.json` version.

> The CLI discovers the IDE purely from `CLAUDE_CODE_SSE_PORT` (env) + `<port>.lock` (token) —
> **but only in the interactive TUI.** See §3b for how a stream-json host must connect instead.

### 3b. Stream-json mode does NOT auto-connect (probed 2.1.220, 2026-08-01)

Hard-won facts, established by bytecode tracing + live probes; don't rediscover:

- The env/lockfile auto-connect (`ASo()` discovery, "wait ≤30s for exactly one valid IDE") is
  reachable ONLY from two React hooks in the TUI, both guarded `if(isPrintMode||…)return`. A CLI
  spawned with `--input-format stream-json` **never** connects to the WS bridge on its own —
  `CLAUDE_CODE_SSE_PORT` set, lockfile valid, zero connection attempts (verified with `ss` against
  a live session). The model in such a session has NO `mcp__ide__*` tools.
- The official VS Code **webview** extension doesn't use the WS bridge for this either: it passes
  its tools as an SDK MCP server named `claude-vscode` via `--mcp-config {"mcpServers":{…}}`,
  tunneled over the stdio control protocol (`connectSdkMcpServer` / `sendMcpServerMessageToCli`).
  The WS bridge + lockfile remain for the terminal (Cmd+Esc TUI) flow.
- **What works for us:** pass the bridge explicitly at spawn:
  `--mcp-config {"mcpServers":{"ide":{"type":"ws","url":"ws://127.0.0.1:<port>","headers":{"x-claude-code-ide-authorization":"<token>"}}}}`.
  Config types `ws-ide`/`sse-ide` are FILTERED from user-supplied config (internal-only transport,
  silently dropped — the server never even shows in `mcp_servers[]`); plain `ws` with a headers map
  is accepted. Server name `ide` is allowed from `--mcp-config` and yields the canonical
  `mcp__ide__<tool>` names. Do NOT pass `--strict-mcp-config` (it would kill the user's own servers).
- **Subprotocol trap:** the CLI's WebSocket client requests `Sec-WebSocket-Protocol: mcp` and
  aborts (~20ms `ErrorEvent`) when the 101 response doesn't echo it. Java-WebSocket's default
  draft never echoes → construct the server with
  `Draft_6455(emptyList(), listOf(Protocol("mcp"), Protocol("")))` (IdeMcpServer does).
- Verified end-to-end 2026-08-01 against a replica server + real CLI: `mcp_servers:[{"name":"ide",
  "status":"connected"}]`, tool listed as `mcp__ide__getDiagnostics`, `tools/call` round-trip OK
  (client sends `_meta.claudecode/toolUseId`; protocolVersion `2025-11-25` requested, our
  `2024-11-05` answer accepted). Tool calls flow through `can_use_tool` like any other tool.

---

## 4. IDE tools exposed to the CLI

Registered via `a.tool(name, description, zodSchema, handler)` on the MCP server (`U0e`).
These are what the CLI calls to act on the editor:

| Tool | Description | Key inputs (from zod schema) |
|------|-------------|------------------------------|
| `openDiff` | Open a git diff for the file | `old_file_path`, `new_file_path`, `new_file_contents`, `tab_name` |
| `openFile` | Open a file, optionally select a text range | path + optional selection range/pattern |
| `getCurrentSelection` | Current selection in the active editor | — |
| `getLatestSelection` | Most recent selection (even if editor not active) | — |
| `getOpenEditors` | Info about open editors | — |
| `getWorkspaceFolders` | All workspace roots open in the IDE | — |
| `getDiagnostics` | Language diagnostics from the IDE | optional uri filter |
| `saveDocument` | Save a document with unsaved changes | file path |
| `checkDocumentDirty` | Whether a document has unsaved changes | file path |
| `closeAllDiffTabs` | Close all diff tabs; replies `CLOSED_<n>_DIFF_TABS` | — |
| `close_tab` | Close the one tab whose label equals `tab_name` (an unknown name is a no-op); replies `TAB_CLOSED` either way | `tab_name` |
| `executeCode` | Execute code (Jupyter controller path) | code/cell |

(`openDiff`'s two `*_file_path` args both describe "uses active editor if not provided"; the diff
tab is how "proposed changes" appear, gated behind the `acceptProposedDiff`/`rejectProposedDiff`
commands in `package.json`.)

**openDiff's verdict contract** (extension 2.1.222 + CLI 2.1.226 binary, measured 2026-08-09):
the call blocks until one of three verdicts, returned as text parts —
`["FILE_SAVED", <final right-pane text>]` on accept (the pane is editable; user tweaks travel
back), `["DIFF_REJECTED", <tab_name>]` on reject, `["TAB_CLOSED"]` when the diff closes without
a decision. **The IDE never writes the file**: both panes are temp documents in the reference,
and the CLI maps the verdict to `{oldContent, newContent}` (TAB_CLOSED → accept-as-proposed,
DIFF_REJECTED → old content) and does the disk write itself. FILE_SAVED is the accept token,
not a claim about disk — a caller-less probe that expects a write will wrongly conclude the
host is broken (manual-test 10.5 did).

---

## 5. Host→CLI notifications (webview/state channel)

The host also *pushes* messages (envelope `{type:"request", channelId, requestId:_s(), request:{…}}`),
observed subtypes: `auth_url`, `update_state`, `usage_update`, `session_states_update`,
`proactive_suggestions_update`. These drive the **chat UI**, not the editor tools — relevant only
if you reuse the webview; a minimal plugin can ignore them and rely on `useTerminal` mode.

---

## 5b. Permission control protocol (stdin/stdout, NOT the WS channel)

Edit approval is **not** done via `openDiff` — it's the SDK **control protocol** over the CLI's
stream-json stdio. Enabled by launching with `--permission-prompt-tool stdio` (+ `--permission-mode`).

- CLI → host (stdout line): `{"type":"control_request","request_id":"…","request":{"subtype":"can_use_tool","tool_name":"Edit","input":{…},"permission_suggestions":[…],"blocked_path":"…?"}}`
- host → CLI (stdin line): `{"type":"control_response","response":{"subtype":"success","request_id":"…","response":{"behavior":"allow","updatedInput":{…},"updatedPermissions":[…]?}}}`
  - reject: `response:{"behavior":"deny","message":"…"}`
  - transport error: `response:{"subtype":"error","request_id":"…","error":"…"}` — the CLI answers
    a refused host request this way too (e.g. `set_permission_mode` to bypass); surface it, don't drop it.
- Other control subtypes: `initialize` (host→CLI: declares `hooks`, `sdkMcpServers`, `jsonSchema`,
  `systemPrompt`), `set_permission_mode` (host→CLI: `{subtype,mode}`), `hook_callback`, `mcp_message`.
- **Host hooks over stream-json (measured 2026-08-17, CLI 2.1.233):** `initialize` takes
  `hooks: {<HookEvent>: [{matcher?, hookCallbackIds: [id…], timeout?}]}` (validated strictly); the
  CLI then blocks each matching call on `control_request{subtype:"hook_callback", callback_id,
  input:{hook_event_name, tool_name, tool_input, session_id…}, tool_use_id}` until the host answers
  `control_response{…, response:<hook JSON output>}` — `{continue:true}` to carry on. We register
  ONE: `PreToolUse Edit|Write|MultiEdit|Read → autosave` (`ClaudeCli.sendInitialize` / `Autosave.kt`),
  the VS Code host's `saveFileIfNeeded` equivalent.
- Permission modes (`--permission-mode`, re-probed 2026-08-03 on 2.1.220 — the advertised set has
  GROWN): `manual` (ask), `acceptEdits` (auto-approves **edits only** — Bash etc. still ask),
  `plan`, `auto`, `dontAsk`, `bypassPermissions`. `default` still parses but is no longer
  advertised; `manual` replaced it. `auto` is the one the official UIs call Auto — it approves
  actions that pass a safety check and pauses on anything risky (the binary ties it to
  `CLAUDE_CODE_AUTO_MODE_CLASSIFY_EDITS` / `classifyEditsModels`), which is NOT the same as
  `bypassPermissions`. `dontAsk` is a distinct branch in the permission flow; its semantics are
  unverified and no UI here exposes it. Without `--permission-prompt-tool stdio` the CLI applies
  edits without asking.

### Permission facts probed against 2.1.220 (2026-07-31) — don't rediscover

- **`bypassPermissions` cannot be *entered* at runtime** — and ONLY it. Re-probed 2026-08-03 by
  sending `set_permission_mode` to a live CLI: `auto`, `dontAsk` and `manual` all answer `success`;
  only bypass errors ("session was not launched with…"). It needs
  `--dangerously-skip-permissions`, and that flag makes EVERY mode a bypass (probed:
  `--permission-mode default` + the flag ran an out-of-sandbox write unprompted), so it stays
  unusable for a mode *switcher*. The plugin used to relaunch with `--resume` to enter Auto; now
  that Auto means `auto`, that relaunch is gone and every mode switch is a plain control request.
- **The CLI broadcasts the mode**: `system/init` and `system/status` events carry `permissionMode`,
  including changes the CLI makes itself — approving `ExitPlanMode` RESTORES `prePlanMode` (the
  mode from before plan mode was entered; 2.1.233, read from the binary), falling back to
  `default` only when none was recorded or the restored mode is gated (auto gate off,
  bypassPermissions unlaunched). Older CLIs always dropped to `default`. NOTE the restore of a
  `manual` pre-plan mode broadcasts the LITERAL `default` (measured 2026-08-16: manual → plan →
  approve) — treat the two as the same mode or a mode UI silently drops the broadcast. Drive any
  mode UI from these, not from what the host last requested.
- **Feedback on a permission answer** (probed 2026-08-16, 2.1.233): the deny `message` is
  delivered to the model VERBATIM as the tool_result — user-typed text there fires the CLI's own
  "the user said" branch and it revises rather than asking. On allow, a `feedback` field is
  silently dropped, and a stdin user message is steered in only at the NEXT model call — when
  the model implements immediately, the first tool call wins and the note arrives late (observed
  live). The working equivalent is appending the note to `updatedInput.plan` (the terminal's
  ctrl+g edit path): the ExitPlanMode tool_result echoes the approved plan, so the model reads
  the note in the same message as the approval — the terminal's own shift+tab pushes its
  acceptFeedback as an extra text block on that same tool_result (read from the 2.1.233 binary). The CLI also omits
  the `setMode acceptEdits` suggestion on a plan card when `prePlanMode` was already elevated
  (auto/acceptEdits/dontAsk).
- **`permission_suggestions`** on `can_use_tool` are ready-made "don't ask again" options
  (`setMode` / `addRules {toolName, ruleContent?}` / `addDirectories`, each with `behavior` and
  `destination`: `userSettings|projectSettings|localSettings|session|cliArg`). Echo the accepted one
  back in `updatedPermissions` on the allow response: `addRules`+`localSettings` writes
  `.claude/settings.local.json`; `setMode acceptEdits` silences further edit prompts. Malformed
  entries are **silently dropped** — verify by behaviour, not by lack of error.
  - **Compound-command shape (measured 2026-08-09, 2.1.226):** the rules for a multi-part Bash
    command arrive as **ONE `addRules` suggestion whose `rules[]` holds one rule per
    sub-command** (`factor 97 && mcookie ; openssl rand -hex 4 && base32 <<< hello` → a single
    suggestion with four rules) — NOT one suggestion per sub-command. Mixed payloads exist too:
    a command rule and a `Read //etc/**` path rule came as two separate one-rule suggestions.
  - **Subset echo is accepted (probed):** `updatedPermissions` may carry a suggestion whose
    `rules` was narrowed to a subset — the CLI persisted exactly `Bash(factor 97)` from a
    four-rule suggestion, nothing else. This is what makes per-sub-command grants possible.
- **The session scratchpad is pre-authorized.** Edit/Write into `/tmp/claude-1000/<enc-cwd>/
  <session>/scratchpad/` never emits `can_use_tool`, even in `default` mode — the CLI treats its
  own temp area as approved (probed: a scratchpad Write ran silently while the cwd Write in the
  same turn prompted). Not a broken permission gate; don't re-investigate.
- **Bash runs sandboxed.** In-workspace commands run without asking in every mode; a command that
  escapes (write outside cwd, network) asks with `blocked_path` set — and those escalations
  **re-ask every time regardless of persisted grants**. Probed useless: `Bash(prefix:*)` /
  exact-command allow rules (even pre-seeded in settings), `additionalDirectories` (settings or
  echoed suggestion), `acceptEdits`. Only a bare `Bash` allow-rule or `bypassPermissions` silences
  them — so don't offer "don't ask again" on a `blocked_path` card.

Our plugin: `ClaudeCli` splits `control_request` frames from conversation events, surfaces
`can_use_tool` as an Accept/Reject card (+ one button per usable permission suggestion; suppressed
when `blocked_path` is set), and replies with `control_response`.

## 6. Relevant environment variables (host → CLI)

Set by the host when launching `claude` (non-exhaustive; from the `CLAUDE_CODE_*` table):

- `CLAUDE_CODE_SSE_PORT` — **the** IDE handshake port (required for this bridge).
- `CLAUDE_CODE_ENTRYPOINT` — identifies the launcher.
- `CLAUDE_CODE_GIT_BASH_PATH` — Windows: path to `bash.exe` for the CLI's shell tool.
- Provider selection: `CLAUDE_CODE_USE_BEDROCK` / `_USE_VERTEX` / `_USE_FOUNDRY` / etc.
- Auth passthrough: `CLAUDE_CODE_OAUTH_TOKEN`, `CLAUDE_CODE_HOST_CREDS_FILE`,
  `CLAUDE_CODE_HOST_AUTH_ENV_VAR`, `CLAUDE_CODE_PROVIDER_MANAGED_BY_HOST`.
- User-configurable (from `package.json` settings): `claudeCode.environmentVariables`,
  `claudeCode.claudeProcessWrapper` (override the launched executable),
  `claudeCode.useTerminal` (run in a terminal instead of the native UI).

---

## 7. What a JetBrains plugin must implement (minimum viable bridge)

1. **MCP-over-WebSocket server** on `127.0.0.1:<random 10000–65535>`, single-client, checking
   the `x-claude-code-ide-authorization` header. (Java: e.g. Java-WebSocket or Ktor;
   MCP framing is JSON-RPC 2.0 — the official `io.modelcontextprotocol` Java SDK can serve it.)
2. **Lock file** at `~/.claude/ide/<port>.lock` with the exact JSON shape in §2
   (`ideName` = e.g. `"PhpStorm"`, `workspaceFolders` = open project roots), `authToken` = the
   same UUID your server checks. Delete on shutdown.
3. **Spawn `claude`** (bundled/user CLI) with `CLAUDE_CODE_SSE_PORT=<port>` in its env
   (+ `CLAUDE_CODE_GIT_BASH_PATH` on Windows).
4. **Implement the tools** in §4 against the IntelliJ Platform API:
   - `getWorkspaceFolders` → `ProjectRootManager` / project base paths
   - `getOpenEditors` / `getCurrentSelection` → `FileEditorManager`, `SelectionModel`
   - `openFile` → `OpenFileDescriptor` + `Editor` selection
   - `getDiagnostics` → `DaemonCodeAnalyzer` / `MarkupModel` highlights (map severities)
   - `openDiff` / `closeAllDiffTabs` / `close_tab` → `DiffManager` + a virtual-file diff request
   - `saveDocument` / `checkDocumentDirty` → `FileDocumentManager.isDocumentUnsaved/saveDocument`
   - `executeCode` → only needed for Jupyter; safe to stub initially
5. **UI:** simplest path is a tool window that just runs the CLI in an embedded terminal
   (`useTerminal` equivalent). A richer path hosts the CLI's streaming output in a **JCEF** panel.

Start with #1–#4 + a terminal UI; that already gives you IDE-aware Claude Code in PhpStorm.
The webview reuse (§5) is a later, higher-effort enhancement.

---

## 8. Re-extracting after a Claude Code update

1. New version installs to `~/.vscode/extensions/anthropic.claude-code-<ver>-<platform>/`.
2. Re-copy (excluding the ~266 MB `resources/native-binary/claude.exe`) into `vscode/`.
3. Re-run the greps used to build this doc (search `extension.js` for `x-claude-code-ide-authorization`,
   `.lock`, `a.tool(`, `CLAUDE_CODE_SSE_PORT`) and diff against §2–§4 to catch protocol changes.

---

## 9. Full wire vocabulary — 2.1.222 sweep (2026-08-06)

Raw reference, preserved from the full-bundle completeness sweep (see `docs/client-parity.md`
§ 32 for the triage — that doc says what we DO about each of these; this section only records
what EXISTS). Sources: the CLI binary's embedded zod wire schema (~1,160 `.describe()` strings,
byte offsets ≈ 281.9–285.2 M in `resources/native-binary/claude`), `extension.js` (host half,
bundled Agent SDK 0.3.222), and `webview/index.js`. Counts are occurrence counts in those
artifacts, not transcript counts. Re-derive after an update with `grep -oaE` over the binary
(it embeds JS, so `-a` text greps work directly; never dump raw matching lines — they can be
megabytes).

### 9a. Top-level stream-json frame types

`StdoutMessage` union (all carry `uuid` + `session_id`):

| type | notes |
|---|---|
| `assistant` | full message frames; carry `error`, `supersedes`, `is_meta`, `parent_tool_use_id` |
| `user` | echoes + tool results; variants carry `isReplay`, `isSynthetic`, `subagent_type`, `tool_use_result` |
| `system` | 44 subtypes — §9b |
| `result` | §9d |
| `stream_event` | wraps raw SSE `event` + `parent_tool_use_id`, `ttft_ms` |
| `rate_limit_event` | `rate_limit_info` — "emitted when rate limit info changes" |
| `control_request` / `control_response` / `control_cancel_request` | §9c |
| `tool_progress` | `{tool_use_id, tool_name, parent_tool_use_id, elapsed_time_seconds, task_id?, heartbeat?, subagent_type?, subagent_retry?{agent_id, attempt, max_retries, retry_delay_ms, error_status, error_category}}` |
| `keep_alive` | 30 s timer; ignore |
| `auth_status` | `{isAuthenticating, output[], error?}` — only with `--enable-auth-status` |
| `attachment` | @internal — at-mentioned files, IDE selections, pasted media, structured output |
| `tombstone` | @internal — "consumers that render or persist the stream should remove the referenced message" |
| `command_lifecycle` | `{command_uuid, state}` — pairs with stdin `bash_command` (CCR terminal UIs) |
| `active_goal` | `/goal` Stop-hook state; `value` null when cleared |
| `transcript_mirror` | @internal — only with `--session-mirror` |
| `prompt_suggestion` | predicted next prompt; only when `promptSuggestions` enabled at initialize |
| `tool_use_summary` | received-and-dropped by the VS Code webview; purpose undocumented |
| `conversation_reset` | "emitted by /clear, plan-mode exit, and fresh-session flows … mount a fresh transcript under new_conversation_id" |
| `notification` | "loop-side text notification — mirrors the interactive REPL notification queue (key/priority/timeout)" |

Stdin-only types: `user`, `bash_command` (@internal, CCR terminal UIs), `control_request`,
`control_response`, `control_cancel_request`, `keep_alive`, `update_environment_variables`.

Internal QueryEvent types with **no public schema and explicit no-op switch arms** (the CLI
consumes them itself; a client never sees them): `api_metrics`, `apply_flag_settings`,
`compact_progress`, `hint_clears`, `interruptible_tool_in_progress`, `open_message_selector`,
`os_notification`, `response_length`, `refusal_continuation` (`phase:"begin"|"end"`,
`salvage_text`), `set_expanded_view`, `set_in_progress_tool_use_ids`, `stream_mode`,
`thinking_progress`, `thinking_signature`, `hooks_start`, `content_block_start`, `start`, `end`,
`sdk_status`, `query_model_change` (no schema at all).

Does NOT exist: top-level `refusal_fallback` (0 occurrences in the binary — the VS Code webview
synthesizes a pseudo-message of that name internally); `checkpoint` as a stream or record type.

### 9b. `system` subtypes (runtime literals, count of emission sites)

```
informational 8 | model_refusal_fallback 7 | compact_boundary 7 | status 6 | thinking_tokens 5
model_fallback 5 | notification 4 | model_refusal_no_fallback 4 | model_consent_fallback 4
background_tasks_changed 3 | session_state_changed 2 | permission_denied 2 | api_retry 2
worker_shutting_down 1 | vcs_state_changed 1 | turn_starting 1 | turn_duration 1 | task_updated 1
task_summary 1 | task_started 1 | task_progress 1 | task_notification 1 | stop_hook_summary 1
scheduled_task_fire 1 | post_turn_summary 1 | plugin_install 1 | permission_retry 1 | mirror_error 1
memory_saved 1 | memory_recall 1 | local_command 1 | init 1 | hook_started 1 | hook_response 1
hook_progress 1 | file_snapshot 1 | elicitation_complete 1 | control_request_progress 1
commands_changed 1 | code_change_published 1 | bridge_status 1 | bridge_state 1 | away_summary 1
api_error 1 | agents_killed 1        (+ schema-only: files_persisted, local_command_output, thinking)
```

Field notes worth keeping (schema doc strings, abridged):
- `init` carries far more than we read: `agents, apiKeySource, betas, claude_code_version, cwd,
  tools, mcp_servers[{name,status}], model, permissionMode, slash_commands, output_style, skills,
  plugins, plugin_errors?, mcp_server_errors?, fast_mode_state?, capabilities?, memory_paths?`.
  `capabilities` is an open set for feature detection (e.g. `interrupt_receipt_v1`,
  `interrupt_cancel_queued_v1`) — "ignore unknown values".
- `status`: `status ∈ {"compacting","requesting",null}` + `permissionMode?` +
  `compact_result:"success"|"failed"?` + `compact_error?`.
- `api_retry` is the PUBLIC wire frame (`attempt, max_retries, retry_delay_ms, error_status,
  error`); `api_error` is "@internal … wire twin is SDKAPIRetryMessage ('api_retry')" and is what
  transcripts persist. The two have different field spellings.
- `compact_boundary`: `compact_metadata{trigger:"manual"|"auto", pre_tokens, post_tokens?,
  cumulative_dropped_tokens?}` + `logical_parent_uuid?`.
- `session_state_changed`: `state ∈ idle|running|requires_action` — "'idle' fires after
  heldBackResult flushes … authoritative turn-over signal".
- `permission_denied`: tool auto-denied with NO prompt (deny rule, auto-mode classifier) — "so
  SDK hosts can render the denial instead of only seeing an is_error tool_result". Hook denies
  are NOT covered by it.
- `task_updated.patch.status ∈ pending|running|completed|failed|killed|paused`; merge semantics.
- `commands_changed`: full roster push mid-session — "clients should REPLACE their cached
  command list".
- `hook_started` `{hook_id, hook_name, hook_event}` / `hook_progress` `{+stdout, stderr, output}`
  / `hook_response` `{+exit_code?, outcome:"success"|"error"|"cancelled"}` — all behind the
  `--include-hook-events` spawn flag (VS Code passes it; we don't).
- `stop_hook_summary` `{hook_count, hook_infos[{command, prompt_text?, duration_ms?}],
  hook_errors[], hook_additional_context?}`.
- `model_consent_fallback` `{choice:"consent"|"switch_default"|"cancelled"}` — the Fable 5
  usage-credit gate; "@internal … Not yet in the public SDKMessage union".
- `model_fallback` `{trigger, original_model, fallback_model, content}` — the generic
  (non-refusal) fallback; needs `--fallback-model`.
- `mirror_error` — SessionStore.append failed for a mirror batch, "the batch is then dropped;
  this surfaces the failure so consumers are not silent on data loss".
- `vcs_state_changed.kind ∈ commit|push|merge|rebase` (open set); `code_change_published.provider
  ∈ github|github-enterprise|gitlab|bitbucket` (open set); `memory_recall.mode ∈
  select|synthesize`; `plugin_install.status ∈ started|installed|failed|completed`;
  `worker_shutting_down.reason` e.g. `host_exit`, `remote_control_disabled`.
- `bridge_status` / `bridge_state` / `turn_starting` have runtime literals but NO zod schema.

### 9c. Control protocol

**Loop → client** (CLI sends, blocks on reply; exhaustive — the dispatcher throws on anything
else): `can_use_tool`, `hook_callback`, `mcp_message`, `elicitation` (default answer
`{action:"decline"}`), `request_user_dialog` (`{dialog_kind, payload, tool_use_id?}`; kinds seen:
`refusal_fallback_prompt`, `fable_overage_consent_prompt`; a client that declared no
`supportedDialogKinds` is never sent one — the CLI "stays silent so a capable client (or the
worker's park deadline) settles it"), `oauth_token_refresh`, `host_auth_token_refresh`.

`can_use_tool` request fields beyond what we render: `display_name`, `description`, `title`,
`requires_user_interaction`, `decision_reason`, `agent_id`,
`matched_ask_rule{source, tool_name, rule_content?}`. Response shape:
`{behavior:"allow", updatedInput?, updatedPermissions?, toolUseID?, decisionClassification?}` or
`{behavior:"deny", message, interrupt?, toolUseID?, decisionClassification?}` with
`decisionClassification ∈ user_temporary|user_permanent|user_reject`.

**Client → loop** (56 accepted at runtime): `interrupt`, `end_session`, `initialize`,
`set_permission_mode` (+`ultraplan:bool`), `set_model` (+optional `system_prompt`),
`set_max_thinking_tokens` (+`thinking_display:"summarized"|"omitted"|null`), `set_cwd`,
`set_color`, `mcp_status`, `get_binary_version`, `get_context_usage`, `list_models`,
`get_session_cost`, `get_usage`, `get_settings`, `get_plan`, `get_workspace_diff`, `mcp_message`,
`mcp_call`, `mcp_set_servers`, `mcp_reconnect`, `mcp_toggle`, `mcp_authenticate`,
`mcp_oauth_callback_url`, `mcp_clear_auth`, `set_mcp_permission_mode_override` (tighten-only),
`channel_enable`, `rewind_files`, `rewind_conversation`, `cancel_async_message`, `read_file`,
`stage_file`, `register_repo_root`, `add_directory`, `file_suggestions`, `seed_read_state`,
`reload_plugins`, `reload_skills`, `apply_flag_settings` ("merges the provided settings into the
flag settings layer"), `stop_task`, `background_tasks` (Ctrl+B semantics), `generate_session_title`,
`rename_session`, `submit_feedback`, `side_question`, `ultrareview_launch`, `message_rated`,
`remote_control`, `claude_authenticate`, `claude_oauth_callback`,
`claude_oauth_wait_for_completion`, `log_otel_event`, plus loopback arms for `hook_callback`,
`can_use_tool`, `request_user_dialog`, `elicitation`.

No `set_effort` / `set_output_style` / `list_sessions` subtypes exist. Effort is a spawn flag
(`--effort <v>`) and possibly `apply_flag_settings{effortLevel}` (unprobed).

Schema-less but accepted (undocumented): `end_session`, `add_directory`, `stage_file`,
`rewind_conversation`, `side_question`, `ultrareview_launch`, `remote_control`, `channel_enable`,
`generate_session_title`, and the `claude_*`/`mcp_*` auth family.

Accepted MID-TURN: `set_model`, `set_permission_mode`, `interrupt`, `set_max_thinking_tokens`,
`rename_session`, `set_color`, `mcp_authenticate`, `mcp_oauth_callback_url`, `mcp_reconnect`,
`apply_flag_settings`, `side_question`, `reload_plugins`.

Response envelope: `{subtype:"success", request_id, response?}` / `{subtype:"error", request_id,
error}` — both may carry `pending_permission_requests` / `pending_user_dialog_requests` on the
`initialize` response, "so a client joining an already-initialized session learns about in-flight
prompts".

### 9d. `result` event

Success arm: `{subtype:"success", duration_ms, duration_api_ms, ttft_ms?, num_turns, result,
stop_reason, total_cost_usd, usage, modelUsage, permission_denials[{tool_name, tool_use_id,
tool_input}], structured_output?, deferred_tool_use?, terminal_reason?, fast_mode_state?,
origin?, uuid, session_id}` (+ several @internal timing fields). Error arm: same minus
`result`/timings, plus `errors:string[]`, `subtype ∈ error_during_execution | error_max_turns |
error_max_budget_usd | error_max_structured_output_retries`.

`terminal_reason` (19 values): `blocking_limit, rapid_refill_breaker, prompt_too_long,
image_error, model_error, api_error, malformed_tool_use_exhausted, aborted_streaming,
aborted_tools, stop_hook_prevented, hook_stopped, tool_deferred, max_turns,
background_requested, completed, budget_exhausted, structured_output_retry_exhausted,
tool_deferred_unavailable, turn_setup_failed`.

`modelUsage` is a MAP keyed by raw model string: `{inputTokens, outputTokens,
cacheReadInputTokens, cacheCreationInputTokens, webSearchRequests, costUSD, contextWindow,
maxOutputTokens, canonicalModel?, provider?}`; `provider ∈ firstParty|bedrock|vertex|foundry|
anthropicAws|anthropicGoogleCloud|mantle|gateway`.

### 9e. Content blocks and SSE deltas

Block types by `==="X"` comparison count in the CLI's own stream handling: `text` 298 ·
`tool_use` 166 · `tool_result` 128 · `image` 65 · `thinking` 56 · `redacted_thinking` 22 ·
`document` 17 · `server_tool_use` 12 · `mcp_tool_use` 5 · `web_search_tool_result` 2 ·
**zero**: `web_fetch_tool_result`, `code_execution_tool_result`,
`bash_code_execution_tool_result`, `text_editor_code_execution_tool_result`, `mcp_tool_result`,
`container_upload`, `search_result` — this CLI cannot emit those despite the API types existing.

Delta types (VS Code's assembler, exhaustive): `text_delta`, `thinking_delta`,
`signature_delta`, `input_json_delta`, `citations_delta` (pushes onto `text.citations`),
`compaction_delta` (no-op even there). The CLI synthesizes `content_block_stop` /
`message_delta` / `message_stop` frames to close orphaned partial messages on retraction.

### 9f. Transcript record types (`~/.claude/projects/**/*.jsonl`)

Message records: `user`, `assistant`, `system`, `attachment`, `progress`, `summary`.
Sidecar/state records: `last-prompt`, `custom-title`, `ai-title`, `tag`, `relocated`,
`agent-name`, `agent-color`, `agent-setting`, `mode`, `permission-mode`, `isolation-latch`,
`worktree-state`, `pr-link`, `bridge-session`, `file-history-snapshot`, `file-history-delta`,
`queue-operation`, `attribution-snapshot`, `observer-ref`, `fork-context-ref`,
`marble-origami-snapshot`, `marble-origami-commit`, `marble-origami-reset`, `compact`,
`env_manager_log`, `teleport-skipped-branch`.

Per-record flags (binary occurrence counts): `isMeta` 188, `isSidechain` 19, `isReplay` 12,
`isCompactSummary` 7, `isApiErrorMessage` 7, `isSynthetic` 7, `isVisibleInTranscriptOnly` 6
("stored in the transcript but not rendered in the live UI" — locally coextensive with
`isCompactSummary`, all 26 records carry both), `isSnapshotUpdate` 2.

VS Code title precedence: `customTitle → aiTitle → lastPrompt → summary → derived first prompt`.
It prefilters lines with raw substring checks (`'"isSidechain":true'`) BEFORE JSON.parse for
speed, and reads only head/tail byte windows of each file when listing sessions.

### 9g. Hook events (30 names in the schema)

`PreToolUse, PostToolUse, PostToolUseFailure, PostToolBatch, PermissionRequest,
PermissionDenied, Notification, UserPromptSubmit, UserPromptExpansion, Stop, StopFailure,
SubagentStart, SubagentStop, SessionStart, SessionEnd, Setup, PreCompact, PostCompact,
ConfigChange, CwdChanged, DirectoryAdded, FileChanged, MessageDisplay, InstructionsLoaded,
Elicitation, ElicitationResult, TaskCreated, TaskCompleted, TeammateIdle, WorktreeCreate,
WorktreeRemove`. Hook-produced synthetic frames in the internal stream (no zod schema):
`hook_additional_context`, `hook_non_blocking_error`, `hooks_start`, `hook_cancelled`,
`hook_success`, `hook_error_during_execution`.

### 9h. VS Code extension — spawn flags, protocol and renderers (2.1.222)

**Spawn**: base `--output-format stream-json --verbose --input-format stream-json`; the host adds
`--permission-prompt-tool stdio` (when a canUseTool callback exists), `--include-partial-messages`
(disabled in remote/SSH windows), and extraArgs `--debug --debug-to-stderr --enable-auth-status
--no-chrome --replay-user-messages` on every native-UI launch. Other flags the SDK can emit:
`--thinking adaptive|disabled`, `--max-thinking-tokens`, `--thinking-display`, `--effort`,
`--max-turns`, `--max-budget-usd`, `--task-budget`, `--model`, `--agent`, `--betas`,
`--json-schema`, `--debug-file`, `--continue`, `--resume=<id>`, `--resume-session-at=<v>`,
`--fork-session`, `--session-id=<uuid>`, `--no-session-persistence`, `--channels`,
`--allowedTools`, `--disallowedTools`, `--tools`, `--mcp-config <inline json>`,
`--strict-mcp-config`, `--setting-sources=a,b`, `--permission-mode`,
`--allow-dangerously-skip-permissions`, `--fallback-model`, `--include-hook-events`,
`--session-mirror`, `--add-dir`, `--plugin-dir`, `--managed-settings`, `--settings`. No `--ide`
flag exists — IDE attachment is lockfile + env or `--mcp-config` (§3b). Env: sets
`MCP_CONNECTION_NONBLOCKING=true`, `CLAUDE_CODE_ENTRYPOINT=claude-vscode`,
`CLAUDE_CODE_ENABLE_TASKS=0`, `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING` (checkpointing always
on), deletes `CLAUDECODE`/`TRACEPARENT`/`TRACESTATE`/`NODE_OPTIONS`.

**Host-registered SDK hooks** (how their diff/diagnostics pipeline works without reading files
fresh): `PreToolUse Edit|Write|MultiEdit → captureBaseline`, `PreToolUse Edit|Write|Read →
autosave`, `PostToolUse Edit|Write|MultiEdit → findDiagnosticsProblems`.

**Webview ⟷ host**: envelope types `io_message, request, response, cancel_request,
close_channel, speech_audio_level, speech_to_text_message, file_updated, plan_comment` (+
`from-extension` DOM wrapper). Host→webview pushes: `update_state, visibility_changed,
font_configuration_changed, create_new_conversation, toggle_dictation, open_plugins_dialog,
insert_at_mention, selection_changed, session_states_update, proactive_suggestions_update,
usage_update, auth_url, tool_permission_request, user_dialog_request`. Webview→host: ~78 request
types covering sessions (list/get/delete/rename/fork/teleport), files/diffs
(open_file, open_diff, open_file_diffs, open_markdown_preview + plan comments, create_worktree),
model/mode (set_model, set_permission_mode, set_thinking_level, apply_settings), MCP
(get_mcp_servers, set_mcp_server_enabled, reconnect/authenticate/clear_auth/oauth_callback,
chrome/jupyter enable-disable), plugins/marketplaces (8), usage/auth
(get_usage, get_context_usage, login, submit_oauth_code), terminal
(exec, open_terminal, get_terminal_contents), speech (start/stop_speech_to_text), telemetry
(log_event, message_rated, submit_feedback).

**Webview per-tool renderers**: `Bash`/`PowerShell` (command block), `Read` (chip + range link),
`ReadCoalesced` (synthetic multi-Read "Explored" merge), `Write`, `Edit` (inline diff),
`NotebookEdit`, `Glob` (`pattern:`), `Grep` (`in <path>, glob:, type:`), `Search`, `WebFetch`
(hostname), `WebSearch` (query), `TodoWrite` (checklist), `ExitPlanMode` ("Claude's Plan"),
`Agent` (wire name `Task`; description + prompt IN row), `TaskOutput`, `AgentOutputTool`
(renders nothing), `Skill`, `ToolSearch` (hidden), `REPL`, `SandboxNetworkAccess`,
`AskUserQuestion`, `Artifact` (auto-opens published URL), `mcp__claude-in-chrome__*` (per-action
labels), generic `mcp__*` (server-name header), fallback (JSON IN/OUT dump). Message window cap:
600 messages, sliced to newest 500.

**Webview system subtypes handled** (9 — fewer than the CLI emits): `init, status,
compact_boundary, thinking_tokens, task_started, task_progress, task_notification,
model_refusal_fallback, model_consent_fallback`. It does NOT know `informational`, `api_error`/
`api_retry`, `model_refusal_no_fallback`, `task_updated`, or `background_tasks_changed` (it
builds its task map from the edge events instead).

**Notable dead code in the shipped build** (evidence that presence ≠ reachable): the
`refusal_fallback_prompt` dialog is disabled by a hardcoded `||!1||`; the debugger/Jupyter MCP
controllers are stubbed (`hasActiveSession: false`, `registerTool: ()=>{}`); `tool_use_summary`
produces no view-model; `claude-vscode.update` and `.createWorktree` commands are declared but
never registered.

---

## 10. Measured payload facts (promoted from gotchas.md, 2026-08-09)

Shape-level facts consulted only when touching their specific handler — moved here so the
per-session gotchas reload stays lean. Each was measured against real transcripts, a live
stream, or the CLI binary; none is inferred.

- **Live vs persisted spellings of one event differ** — the worked example behind gotchas.md's
  accept-both rule: live `system/api_retry` `{attempt, max_retries, retry_delay_ms,
  error_status, error:"<enum string>"}` vs persisted `system/api_error` `{retryAttempt,
  maxRetries, retryInMs, error:{message, formatted}}` — `error` even changes TYPE. Same family:
  `prevent_continuation` / `preventContinuation`. The api_retry `error` enum (five codes; a
  no-status network failure is the literal `unknown`) resolves through chat.html's
  `RETRY_REASONS` (9.1).
- **AskUserQuestion**: the `can_use_tool` reply must carry `updatedInput` with answers keyed by
  question TEXT; a plain allow silently returns "user did not answer".
- **Sub-agent progress** (`task_started` / `task_progress` / `task_notification`) is LIVE-ONLY,
  never persisted; `task_notification` omits `subagent_type` (remember it from `task_started`).
  Child `assistant`/`user` events (`parent_tool_use_id`) are deliberately ignored.
- **`background_tasks_changed`** has REPLACE semantics — assign the set, never merge, or
  finished tasks live forever (a level signal; see glossary "Level vs edge signal").
- **`result.modelUsage`** is a MAP that routinely includes side models the user never picked:
  match the raw key (it carries the `[1m]` tag) → `canonicalModel` → on no match change
  NOTHING. No denominator exists until the first turn ends, and the `[1m]` seed heuristic must
  check BOTH `resolvedModel` and `value` (fable differs). Usage above the known window PROMOTES
  to 1M — a big session showing the same % on a 200k and a 1M model is correct, not a stuck
  denominator.
- **`queue-operation` records** are the CLI's own pipeline bookkeeping (matched
  enqueue/dequeue pairs, one per turn) — there is no CLI queue to drive; message queueing is
  client-side.
- **No `set_effort`/`set_thinking_level` control request exists** (only
  `set_max_thinking_tokens`, a raw token count) — the effort slider rides a muted `/effort`
  turn (`effortMuted`, idle-gated).
- **Tool-returned images**: the discriminator is `toolUseResult.type=="image"` — NOT `isImage`,
  a Bash-result field that is always false. `dimensions` is
  `{originalWidth,originalHeight,displayWidth,displayHeight}`, not `{width,height}`.
- **Many transcript `thinking` blocks are empty** — signature only, no body (~2.1k of 6.6k
  local ones carry text); they replay as nothing, correctly.
- **Transcript format changed at 2.1.226**: one assistant record per message (blocks inline),
  plus record types that didn't exist before (`queue-operation`, `attachment` = tool-roster
  deltas NOT files, `ai-title`, `last-prompt`, `mode`, `custom-title`). OLDER files persist one
  record PER BLOCK, each repeating the same *cumulative* `message.usage` — summing
  `output_tokens` over those over-reports ~2.45x unless deduped by `message.id`. Live is
  immune. Never assume one transcript's shape generalizes.
- **Transcript file ORDER can lie**: the CLI writes a retry storm's concluding error record
  BEFORE flushing the buffered `api_error` records (measured on a real network-off session:
  retries at file positions after the error, timestamps before it). Timestamps and the
  `parentUuid` chain stay chronological; file order does not. SessionStore reorders exactly
  this pattern (9.1's replay half).
