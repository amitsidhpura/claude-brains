# Claude Brains — Claude Code for JetBrains IDEs (unofficial)

Personal project: a JetBrains/PhpStorm plugin that drives the official `claude` CLI,
replicating the VS Code Claude Code extension. **Personal use only — never bundle or
redistribute Anthropic's extension.js / webview / claude.exe.**
Renamed from "Claude Code (Syncroze)" 2026-07-31: plugin id `io.github.amitsidhpura.claude-brains`,
packages `io.github.amitsidhpura.claudebrains.*`, no syncroze references in the plugin.
Distribution: custom plugin repo on `github.com/amitsidhpura/claude-brains` (Path B) AND the
JetBrains Marketplace (vendor `amitsidhpura`, submitted 2026-07-31 — same plugin id, IDEs take
the higher version from either source); process in `docs/release.md`.

## Philosophy — "Develop in the IDE. Configure in the Terminal."
The slogan, and the scope rule. The plugin COMPLEMENTS the terminal. Ask of any feature:
*is this reached for many times an hour while writing code?*
- **Yes → the panel.** Asking, editing, diffs, approvals, model/mode/effort, session resume,
  file references — one keystroke from the editor.
- **No → the terminal.** Login, MCP servers, hooks, permission rules, agents, everything under
  `~/.claude`. Rebuilding those in the IDE buys a second implementation that drifts.
See conventions.md for the vocabulary rules this imposes; decisions.md for what it has declined.

## Repo layout
- `plugin/` — Kotlin source + Gradle root (IntelliJ Platform Gradle Plugin 2.x, JCEF webview UI);
  every `./gradlew` command runs from here
- `.github/workflows/marketplace-upload.yml` — the only CI: on `release: published` it re-posts the
  published GitHub asset to the JetBrains Marketplace. Nothing is built in CI
- `docs/ide-mcp-protocol.md` — reverse-engineered protocol reference (READ FIRST)
- `docs/feature-checklist.md` — feature register vs VS Code + TUI (re-audited against 2.1.250 on
  2026-08-28): 128 rows with STABLE
  `section.row` ids, colour tiers, effort tags — refer to features by id (e.g. 2.4)
- `docs/limits.md` — every size cap (folded/scrolled/truncated/volume) and where it is set
- `docs/slash-commands.md` — the slash-command allowlist and per-command verification status
- `docs/release.md` — release process (Path B custom repo)
- `design/mockup.html` — static UI mockup for design iteration in a browser; approved changes
  get ported into `plugin/src/main/resources/webview/chat.html`
- `tools/cdp.py` — run JS in the live webview over CDP; `tools/live_harness.py` — live-path
  regression suite (replays recorded wire frames through `onClaudeEvent`); `tools/fixtures/`
- `vscode/` — NOT in git. Extracted official VS Code extension (from
  `~/.vscode/extensions/anthropic.claude-code-<ver>/`, minus the binary). Re-extract locally;
  used to reverse-engineer protocols and styles.
- `_local/` — personal scratch, not in git.

## Architecture (all verified working end-to-end)
- **Bridge** (`bridge/`): picks a free port (10000–65535), writes `~/.claude/ide/<port>.lock`
  `{pid, workspaceFolders, ideName, transport:"ws", runningInWindows, authToken}`, runs an
  MCP-over-WebSocket server on 127.0.0.1 checking header `x-claude-code-ide-authorization`,
  advertising WebSocket subprotocol "mcp" (the CLI's WS client aborts without the echo).
  IDE tools in `IdeTools.kt` (openFile/openDiff/getDiagnostics/selection/etc.). The CLI is
  handed the bridge via `--mcp-config` (server "ide", type "ws"), NOT env discovery — see
  gotchas.md.
- **CLI** (`cli/ClaudeCli.kt`): spawns `claude --input-format stream-json --output-format
  stream-json --include-partial-messages --verbose --permission-prompt-tool stdio
  --permission-mode <mode>` with env `CLAUDE_CODE_SSE_PORT=<port>`. Routes control protocol
  (`can_use_tool`, `initialize`, `set_model`, `set_permission_mode`, `interrupt`) separately
  from conversation events. Declares ONE host hook on `initialize` (`PreToolUse
  Edit|Write|MultiEdit|Read → autosave`, `Autosave.kt`) and answers its `hook_callback`s.
- **UI** (`ui/ChatPanel.kt` + `resources/webview/`): JCEF panel in a right-anchored tool window.
  Single JS<->Kotlin channel: `window.__bridge(json)` up, `window.onClaudeEvent(line)` down.
  `chat.html` is markup only; the JS is 14 numbered files in `webview/js/`, spliced back into the
  page's ONE `<script>` block by `ui/WebviewAssets.kt` at `<!--JS-->` (manifest `JS_FILES` is the
  only copy of the order — one shared script scope, no modules). Styles ONLY in `webview/chat.css`
  (spliced at `<!--CSS-->`; the mockup links the same file).
- **Sessions** (`session/SessionStore.kt`): reads `~/.claude/projects/<enc-cwd>/*.jsonl`
  (enc = cwd with non-alphanumerics → `-`); resume via `--resume <id>`; transcript replayed
  into the UI through rich `JsonObject` blocks so replay matches live rendering.
- Shared caps/formats (tool-desc chain, IN/OUT caps, cut rule, search-result format) live once
  in `RenderLimits.kt`, spliced into chat.html as `window.LIMITS` — see gotchas.md.

## Build / run
`cd plugin && ./gradlew runIde` (sandbox PhpStorm 2024.2.6 — bumped from 2024.2.0 on 2026-08-23 for the IJPL-161111 JCEF keyboard fix). `./gradlew compileKotlin` for a fast
type-check; `buildPlugin` → installable zip in `build/distributions/`; `./gradlew test` (plain
JUnit 5 over SessionStore/RenderLimits); `./gradlew probe --args="<projectPath> <sessionId>"`
dumps replay blocks without the IDE. Resource-only changes (chat.html, webview/js/, chat.css) need only a `runIde`
restart. `claude` resolved from `-Dclaude.executable` → PATH → installed VS Code extension binary.
Sandbox JCEF debug port: `-PjcefDebugPort=<n>` on runIde + `CLAUDE_BRAINS_CDP_PORT` for
tools/cdp.py — but a hand-set sandbox Registry value still wins (gotchas).
Toolchain requirements (Java 21, Gradle 8.10.2, instrumentCode off) are load-bearing — gotchas.md.

### Which debug route — pick by symptom, not by habit
| Symptom | Route |
|---|---|
| "Does the CLI even send this?" | grep `~/.claude/projects/*/*.jsonl` **by key**, or run `claude --output-format stream-json` in a terminal |
| Payload NEVER persisted (task frames, launch results) | `strings -n 8 ~/.local/share/claude/versions/<ver>` — read it verbatim from the binary |
| Wrong blocks after resume | `./gradlew probe` — splits a PARSER bug from a RENDERER bug, no IDE |
| A transient state renders wrong | `window.__gallery()` — every state, without driving the CLI |
| Live render misbehaving mid-session | `python tools/cdp.py` — the only view of real JCEF |
| Kotlin logic: bridge, permissions, parsing | `./gradlew runIde --debug-jvm` (suspends, port 5005) + Remote JVM Debug |
| CSS / layout iteration | `design/mockup.html` + headless Chrome (compositor traps — gotchas.md) |
| Won't load in a REAL IDE | install the zip in the real PhpStorm — the sandbox cannot catch this class |
| Reproduces ONLY in the sandbox | suspect the sandbox (2024.2.6, stock keymap) — it invents symptoms too |
| Poking around by hand | DevTools window (Find Action → "Claude Brains: Open DevTools") |

**Start at the top row** — cheapest and most skipped. Check by KEY, not substring. The live
panel is for AFTER you know the data exists.

## External references
- Official extension for reverse-engineering: `~/.vscode/extensions/anthropic.claude-code-<ver>/`
- Release repo: `github.com/amitsidhpura/claude-brains` (`updatePlugins.xml` at repo root)
- Marketplace listing: `plugins.jetbrains.com/plugin/33274` — the NUMERIC id is the working URL; the
  xmlId form 404s. Versions via the API: `plugins.jetbrains.com/api/plugins/33274/updates`. Vendor
  `amitsidhpura`. Upload token: created in the **My Tokens** tab of
  `plugins.jetbrains.com/author/me/tokens`, stored ONLY as the GitHub repo secret
  `JETBRAINS_MARKETPLACE_TOKEN` (set 2026-08-12). Never in the repo, never in a file. The
  `release: published` → `marketplace-upload.yml` path is routine now: 0.5.2 and 0.5.3 both went up
  with no manual step and were **Approved** the same day. The Marketplace runs its own verifier on
  every upload and signs its own copy.
- **TWO machines, both real — check which one you are on** (corrected 2026-08-12; an earlier save
  wrongly declared the Linux box "gone"):
  - **Linux**: repo `/home/syncroze/Sites/claude-brains`, home `/home/syncroze`, transcripts
    `~/.claude/projects/-home-syncroze-Sites-<project>/`, CLI `~/.local/bin/claude` (versions under
    `~/.local/share/claude/versions/`), Java 21.0.12 Temurin on PATH. Sibling test repo
    `~/Sites/claude-brains-testing` (**-testing**, not -test).
  - **Windows 11**: repo `D:\sites\claude-brains`, home `C:\Users\Supple-7`, transcripts
    `C:\Users\Supple-7\.claude\projects\D--sites-<project>\`, JDK 21 at `~/.jdks/ms-21.0.12`.
    Sibling test repo `D:\sites\claude-brains-test`.
  Gradle via the committed wrapper (8.10.2) on both.
