# Claude Code IDE ↔ CLI protocol (reverse-engineered)

Source: `vscode/extension.js` from `anthropic.claude-code` **v2.1.220** (win32-x64), decompiled/minified.
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
| `closeAllDiffTabs` | Close all diff tabs | — |
| `close_tab` | Close a named tab | `tab_name` |
| `executeCode` | Execute code (Jupyter controller path) | code/cell |

(`openDiff`'s two `*_file_path` args both describe "uses active editor if not provided"; the diff
tab is how "proposed changes" appear, gated behind the `acceptProposedDiff`/`rejectProposedDiff`
commands in `package.json`.)

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
  including changes the CLI makes itself — approving `ExitPlanMode` drops it to `default`. Drive any
  mode UI from these, not from what the host last requested.
- **`permission_suggestions`** on `can_use_tool` are ready-made "don't ask again" options
  (`setMode` / `addRules {toolName, ruleContent?}` / `addDirectories`, each with `behavior` and
  `destination`: `userSettings|projectSettings|localSettings|session|cliArg`). Echo the accepted one
  back verbatim in `updatedPermissions` on the allow response: `addRules`+`localSettings` writes
  `.claude/settings.local.json`; `setMode acceptEdits` silences further edit prompts. Malformed
  entries are **silently dropped** — verify by behaviour, not by lack of error.
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
