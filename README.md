<p align="center">
  <img src="plugin/src/main/resources/META-INF/pluginIcon.svg" width="64" alt="">
</p>

<h1 align="center">Claude Brains</h1>

<p align="center">
  <b>Claude Code for JetBrains IDEs — unofficial.</b><br>
  A right-side tool window that runs Anthropic's official <code>claude</code> CLI inside PhpStorm
  and other IntelliJ-platform IDEs, wired to the editor through the same IDE-MCP bridge the
  VS Code extension uses.
</p>

> **Not affiliated with Anthropic or JetBrains.** This is a personal interop project. It bundles
> no Anthropic code — you bring your own `claude` CLI, already installed and logged in. Anthropic
> ships an official JetBrains plugin; if you want a supported product, use that one.

---

## What it does

The plugin drives the CLI in headless stream-json mode and renders the conversation natively in
a JCEF webview, so the IDE gets the full agent loop rather than a terminal in a panel:

- **Streaming chat** with markdown, syntax highlighting, and live thinking blocks
- **Permission cards** with real diffs — approve or reject each edit before it lands
- **Plan mode**, `AskUserQuestion` cards, and a permission-mode switcher
  (Manual / Edit automatically / Plan / Auto)
- **Model picker** with custom models, plus an effort slider
- **Sessions** — history, resume, replay of any past conversation from `~/.claude/projects`
- **Editor integration** — clickable file references, `@`-file mentions, diagnostics, selection,
  and diff tabs via the IDE-MCP bridge
- **Slash commands**, image paste/drop, stop and retry, a context gauge, and per-request
  completion summaries

The tool-window header shows the conversation title, with buttons to reload the current thread,
open past conversations, and start a new one.

## Requirements

- The [`claude` CLI](https://docs.anthropic.com/en/docs/claude-code), installed and **already
  logged in from a terminal** — the plugin has no login flow of its own.
- An IntelliJ-platform IDE, build **2024.2 or newer** (PhpStorm, IntelliJ IDEA, WebStorm, …).

The CLI is resolved from `-Dclaude.executable=<path>`, then `PATH`, then the binary inside an
installed VS Code Claude Code extension.

## Install

The plugin is distributed through a custom plugin repository rather than the JetBrains
Marketplace. Add this URL under **Settings → Plugins → ⚙ → Manage Plugin Repositories**:

```
https://raw.githubusercontent.com/amitsidhpura/claude-brains/main/updatePlugins.xml
```

Then find **Claude Brains** in the Marketplace tab and install — it auto-updates from there like
any marketplace plugin.

Or grab the zip from the [latest release](https://github.com/amitsidhpura/claude-brains/releases)
and use **Settings → Plugins → ⚙ → Install Plugin from Disk**.

## Build from source

Requires **Java 21** (Gradle 8.10.2 refuses anything newer — don't use the JBR from a recent
PhpStorm, which ships JDK 25).

```bash
cd plugin
./gradlew runIde          # sandbox IDE with the plugin loaded
./gradlew buildPlugin     # installable zip in build/distributions/
./gradlew test            # JUnit 5 over the transcript parser
./gradlew compileKotlin   # fast type-check
```

The first `runIde` downloads ~1 GB of IDE dependencies; later builds take seconds. The sandbox
runs on the downloaded IDE's bundled JBR (which has JCEF), not on your build JVM.

## How it works

```
┌─ IDE ──────────────────────────────────────────────┐
│  ChatPanel (JCEF webview)  ←──→  ClaudeSessionService
│         │  chat.html/.css               │          │
│         │                               ▼          │
│         │                        ClaudeCli ──spawn──→ claude --input-format stream-json
│         │                               ▲              --permission-prompt-tool stdio
│         ▼                               │          │            │
│  SessionStore ← ~/.claude/projects/*.jsonl          │            │ CLAUDE_CODE_SSE_PORT
│                                                     │            ▼
│  IdeMcpServer (WebSocket, 127.0.0.1) ←──────────────────── MCP over ws
│    openFile · openDiff · getDiagnostics · selection │
└─────────────────────────────────────────────────────┘
```

The bridge picks a free port, writes `~/.claude/ide/<port>.lock` with an auth token, and serves
MCP over WebSocket so the CLI can drive the editor. Conversations are read back from the CLI's
own JSONL transcripts, so resume replays exactly what happened — diffs, plan cards, refusals and
all. Replay is windowed: only the newest turns are shipped up front, and earlier history loads
as you scroll.

## Repo layout

| Path | What |
|---|---|
| `plugin/` | Kotlin source and Gradle build — IntelliJ Platform Gradle Plugin 2.x, JCEF webview UI |
| `design/` | the icon source and `mockup.html`, a static fixture mirroring the shipped renderer |
| `docs/` | protocol reference, feature checklist, limits, release process |

## Docs

- [`docs/ide-mcp-protocol.md`](docs/ide-mcp-protocol.md) — reverse-engineered protocol reference
- [`docs/feature-checklist.md`](docs/feature-checklist.md) — feature parity status
- [`docs/slash-commands.md`](docs/slash-commands.md) — which slash commands work headless, and why
- [`docs/limits.md`](docs/limits.md) — every size cap and where it is set
- [`docs/release.md`](docs/release.md) — how a release is cut
- [`CLAUDE.md`](CLAUDE.md) — architecture notes and hard-won protocol facts

## Status

Working end to end and used daily. Deliberately deferred: a settings page, non-terminal login,
conversation tabs, usage/token display, auto-include selection, and voice input.

Personal project — issues and PRs aren't actively solicited, but you're welcome to fork it.
