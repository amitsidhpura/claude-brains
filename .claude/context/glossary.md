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
  The roster mixes two kinds: AGENT-type tasks suspend the turn (intermediate `result`, real one
  later); background SHELLS (`task_type "local_bash"`) do not — the CLI goes idle and their
  `result` is the true end. `pendingBgTasks` counts only the suspending kind (deny-list: unknown
  types suspend). And a shell's completion notification starts a turn with NO user frame on the
  wire — `message_start` is the only signal, which is why it sets busy.
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
- **Block gap vs attach gap** — the panel's two spacings, as `:root` tokens. `--block-gap` (18px)
  separates INDEPENDENT blocks: one tool line from the next, a card from an error. `--attach-gap`
  (8px) is the distance from a line to what HANGS OFF it: a tool line to its IN/OUT box, progress,
  caveat, images or checklist; a card header to its body; a status line to the compaction summary;
  a `.think` summary to its text. Neither is the same as a container's own internal rhythm (a
  `.card-b` action row at 10px, list items at 0-6px). Adding a new block under a line? It takes
  `--attach-gap` — see gotchas for WHICH form, since flex and block parents need different maths.
- **The gutter dot's three states** — the dot (`::before`) says what a block is AND, for a tool line,
  how it turned out: **white and pulsing** while in flight (no verdict yet), **green** on success,
  **red** on failure. The motion is `--pulse-name` (`bg-pulse`) at `--pulse-period`, opacity only —
  deliberately no geometry, because anything that grows outside the dot's box gets clipped by
  `.turn-body`'s paint containment (an outward halo was tried and pulled for exactly that). One geometry rule serves all four dots
  (`.blk`/`.think`/`.think-live`/`.tool-line`) and the colour is `--dot-c` declared on the element —
  which is what lets the ring read its own colour and cover `.fail`'s red without a second rule.
  `--pulse-period` (1.6s) is the panel's shared live heartbeat (ring, `.bg` chip, both text
  shimmers); `--pulse-name` is the ring's motion, and `design/dot-pulse-probe.html` drives that
  token. `.run` is LIVE-only: it is set where the wire frame is handled, never in `toolLine()`,
  because replay and `__gallery` draw finished tools through the same helper.
- **LIMITS splice** — `RenderLimits.kt` values injected into chat.html as `window.LIMITS` at
  the `LIMITS` marker (same idiom as `<!--CSS-->`); the single source for caps/formats shared
  by both render paths.
