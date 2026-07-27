# Claude Code for PhpStorm (Syncroze, personal build)

Runs Anthropic's official `claude` CLI inside PhpStorm, wired to the editor via the same
**IDE-MCP bridge** the VS Code extension uses. Opens in a **right-side tool window** (sidebar).

Protocol reference: [`../docs/ide-mcp-protocol.md`](../docs/ide-mcp-protocol.md).

> ⚠️ **Status: unbuilt scaffold.** These files were written but have **not** been compiled or
> run — no JDK/Gradle was available in the authoring environment. Expect to fix a few
> imports/signatures on first build. Treat it as a reviewed starting point, not a finished plugin.

> ⚠️ **Personal use only.** Do not bundle or redistribute Anthropic's `extension.js`, `webview/`,
> or `claude.exe`. This plugin only *interoperates* with the CLI you already installed.

## Architecture

```
PhpStorm plugin (this project)
├─ bridge/
│   ├─ PortFinder      pick a free 127.0.0.1 port (10000–65535)
│   ├─ IdeLockFile     write ~/.claude/ide/<port>.lock  { …, authToken }
│   ├─ IdeMcpServer    WebSocket MCP server; checks x-claude-code-ide-authorization
│   └─ IdeTools        IntelliJ-backed tools (openFile, getSelection, diagnostics…)
├─ cli/ClaudeCli       spawns `claude` with CLAUDE_CODE_SSE_PORT, streams stream-json
├─ ClaudeSessionService  one session per project (bridge + CLI lifecycle)
└─ ui/
    ├─ ClaudeToolWindowFactory   right-anchored sidebar
    ├─ ChatPanel                 JCEF host + JS⇄Kotlin bridge
    └─ resources/webview/chat.html   native chat UI (driven by stream-json)
```

The UI is **swappable**: today it loads our own `chat.html` (native tier). To move to the
**pixel-identical** tier later, point `ChatPanel.loadUi()` at Anthropic's `webview/index.html`
and translate messages in `pushEvent` / the JS-query handler — no bridge changes needed.

## Build & run

Prerequisites: **JDK 17** and either the Gradle wrapper jar or the IDE's bundled Gradle.

1. **Open** `phpstorm-plugin/` in IntelliJ IDEA (or PhpStorm with the Gradle plugin). The IDE
   will provision Gradle and generate the wrapper. (Or run `gradle wrapper` once if you have Gradle.)
2. Adjust `build.gradle.kts` → `phpstorm("2024.2")` to a version you have, or use
   `local("C:/Program Files/JetBrains/PhpStorm <ver>")`.
3. Run the **`runIde`** Gradle task → launches a sandbox PhpStorm with the plugin.
4. Open the **Claude Code** tool window on the right; type a prompt.

`claude` is resolved from `-Dclaude.executable=…`, else PATH (`claude`/`claude.exe`).

## Known gaps (next tasks)

- `openDiff`, `getDiagnostics`, `close_tab`, `closeAllDiffTabs`, `executeCode` are **stubbed** in
  `IdeTools` — wire to `DiffManager` / `DaemonCodeAnalyzer`.
- Auth: the CLI uses your existing Claude login; no in-plugin login UI yet.
- stream-json rendering in `chat.html` handles text, tool_use chips, and result; richer blocks
  (diffs, plan cards, thinking) are TODO.
- No persistence of past conversations / `/resume`.
