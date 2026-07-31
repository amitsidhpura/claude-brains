# Claude Brains — Claude Code for JetBrains IDEs (unofficial)

Runs Anthropic's official `claude` CLI inside PhpStorm (and any IntelliJ-platform IDE), wired
to the editor via the same **IDE-MCP bridge** the VS Code extension uses. Opens in a
**right-side tool window**: streaming chat, permission cards with diffs, plan mode, slash
commands, @-file mentions, image paste, session history/resume, and more — see
[`../docs/feature-checklist.md`](../docs/feature-checklist.md) for the full status.

> ⚠️ **Unofficial, personal project.** Not affiliated with Anthropic or JetBrains. It does not
> bundle or redistribute any Anthropic code — it only *interoperates* with the `claude` CLI you
> already installed. Protocol reference: [`../docs/ide-mcp-protocol.md`](../docs/ide-mcp-protocol.md).

## Requirements

- The [`claude` CLI](https://docs.anthropic.com/en/docs/claude-code) installed and **already
  logged in via a terminal** (the plugin has no login flow yet).
- The CLI is resolved from `-Dclaude.executable=<path>` → `PATH` → the installed VS Code
  extension's binary.
- IDE build 2024.2+ (`since-build 242`).

## Install

From the custom plugin repository (gets auto-updates):

1. Settings → Plugins → ⚙ → **Manage Plugin Repositories**
2. Add `https://raw.githubusercontent.com/amitsidhpura/claude-brains/main/updatePlugins.xml`
3. Search for **Claude Brains** in the Marketplace tab → Install → restart.

Or install a downloaded zip directly: Settings → Plugins → ⚙ → **Install Plugin from Disk**.

## Build from source

```
./gradlew buildPlugin        # installable zip in build/distributions/
./gradlew runIde             # sandbox IDE with the plugin loaded
./gradlew test               # plain JUnit 5 over SessionStore
./gradlew compileKotlin      # fast type-check
```

Build JVM must be **Java 21** (Gradle 8.10.2 refuses JDK >23). See the repo root `CLAUDE.md`
for the full build notes, architecture detail, and protocol facts.

## Architecture

```
Claude Brains (this plugin)
├─ bridge/    IDE-MCP server: free port, ~/.claude/ide/<port>.lock, WS + auth header,
│             IDE tools (openFile / openDiff / getDiagnostics / selection / …)
├─ cli/       spawns `claude --input-format stream-json --output-format stream-json
│             --permission-prompt-tool stdio`, routes control protocol vs conversation
├─ ui/        JCEF webview (webview/chat.html + chat.css), JS↔Kotlin bridge
└─ session/   reads ~/.claude/projects/<enc-cwd>/*.jsonl, resume + transcript replay
```
