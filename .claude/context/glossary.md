# Glossary — distinctions a fresh session gets subtly wrong

- **Live vs replay** — the two render paths: live = wire events through `onClaudeEvent`;
  replay = transcript JSONL through `SessionStore.readTranscript`. Same visuals required, often
  DIFFERENT field spellings on the wire (see gotchas.md). `docs/renderer-parity.md` audits
  live-vs-replay-vs-mockup (internal); `docs/client-parity.md` audits us-vs-TUI-vs-VS-Code
  (external).
- **By design / Declined / Deferred** — three different "we don't have it"s: never will
  (terminal's half), decided against on 2026-08-06 (cost/usage display), wanted-but-later
  (tabs, voice). Release prose must not blur them.
- **Fold vs cut-marker** — the fold (`foldBlock`) hides content the DOM still holds; the marker
  (`.io-cut`/`.cmd-cut`) reports content that is GONE (dropped by a cap). Never nest the marker
  inside the foldable region.
- **Level vs edge signal** — `background_tasks_changed` describes the present set (level) →
  rendered as a chip, replace-assigned; timeline entries are for edge events that happened.
- **Bridge** — the plugin's MCP-over-WebSocket server the CLI connects to for `mcp__ide__*`
  tools (lockfile + `--mcp-config`).
- **Windowed replay** — Kotlin parses the whole transcript but ships only the newest ~250
  blocks cut at a turn boundary (`alignedStart`); earlier chunks stream in on upward scroll.
- **Gallery** — `window.__gallery()` from DevTools or over CDP (the Ctrl+Alt+G chord was
  removed 2026-08-09); renders every transient UI state in the live webview without driving
  the CLI.
- **Probe** — `./gradlew probe`; dumps replay blocks for a session with no IDE, splitting
  parser bugs from renderer bugs.
- **Project root, two sources** — `__project` is OURS (ChatPanel pushes `project.basePath` when the
  panel opens); `system/init`'s `cwd` is the CLI's, and only arrives at the first turn. The webview
  takes the IDE's as primary and init's as a refresh. Neither is "the" root on its own.
- **`.p-head` / `.p-tail`** — the two halves of a tool-line path. Head is the shrinkable prefix and
  wears the ellipsis; tail never shrinks and holds the filename (plus its parent when they fit
  `PATH_TAIL_MAX`). What is IN the tail is decided in JS; CSS only decides what gives way.
- **LIMITS splice** — `RenderLimits.kt` values injected into chat.html as `window.LIMITS` at
  the `LIMITS` marker (same idiom as `<!--CSS-->`); the single source for caps/formats shared
  by both render paths.
