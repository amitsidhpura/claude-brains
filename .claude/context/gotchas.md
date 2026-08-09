# Gotchas — hard-won, don't rediscover

## Protocol / wire
**Payload shapes live in `docs/ide-mcp-protocol.md` (§9 wire vocabulary, §10 measured facts) —
this section keeps only the standing RULES that defeat assumptions.** Re-read the doc before
trusting memory here.
- A stream-json CLI NEVER auto-connects from `CLAUDE_CODE_SSE_PORT` — that discovery is TUI-only;
  the bridge rides `--mcp-config`. Env var + lockfile stay for terminal-launched TUI sessions.
- `system/init` arrives only after the first user turn — send control_request `initialize` at
  startup or the panel opens blind. Its `commands` payload has NO type field, so the slash
  allowlist is the only lever for hiding commands.
- Permission gate ONLY works with `--permission-prompt-tool stdio`. `acceptEdits` covers EDITS
  ONLY (Bash still asks — correct). `auto` is safety-checked, NOT `bypassPermissions`. Malformed
  `updatedPermissions` are silently DROPPED (no error). Refused control requests answer
  `subtype:"error"` → `__ctl_error`.
- `blocked_path` (sandbox-escape) re-asks no matter what is granted → strip suggestion buttons
  there. It is BASH-ONLY (write outside cwd / network): a Write/Edit targeting an
  out-of-workspace or root-owned path is a NORMAL ask WITH suggestions — mis-reading that as a
  broken 6.6 cost real time on the pass.
- **No card ⇒ no live diff.** Whenever a tool is pre-approved — acceptEdits/auto mode, a saved
  rule, a pre-authorized path — the CLI sends NO `can_use_tool` at all, and the live wire never
  carries `toolUseResult`/`structuredPatch`. Anything the permission card is the sole producer
  of needs a second source built from the tool_use INPUT (4.4's fix; see decisions.md).
- A COMPOUND command (`a && b`) yields ONE addRules suggestion carrying several `rules[]` —
  build per-rule UI off `rules`, not off the suggestion list. Echoing a suggestion back with a
  NARROWED `rules` subset is accepted and persisted exactly (wire-probed 2026-08-09).
- Hooks NEVER reach us as control requests: a hook denying a tool arrives as a plain
  `tool_result` with `is_error`; a hook blocking a NON-tool event emits `system/informational`
  with `prevent_continuation` and NOTHING else — unhandled it reads as a dead panel.
  `informational`'s optional `tool_use_id` DEDUPES rather than stacks, and it is persisted, so
  both paths must render it. Hooks are re-read from settings PER PROMPT, not cached at CLI
  spawn — removing a hook file takes effect on the next message of a LIVE session.
- Live wire and transcript disagree in SPELLING and in ORDER for the same events: field names
  differ (even field TYPES — worked example in the doc §10), and the file order of late-flushed
  records lies while timestamps + the `parentUuid` chain stay chronological. Accept both
  spellings on both paths; trust timestamps over file positions.
- The `assistant` event's `uuid` (and timestamp) is the SAME one the CLI writes into the
  transcript record — the only handle tying a live render to its replayed twin. Don't assume
  live-only state can't be persisted without checking for a shared uuid.
- Model-facing IDE tools are an upstream ALLOWLIST (getDiagnostics + executeCode only),
  byte-identical across CLI 2.1.222–226 — stable policy, not a regression to wait out
  (measured mechanism in the register's 10.1 note). Do NOT dodge it by renaming the bridge
  server: the CLI finds its IDE client by the literal name `"ide"` for its own IDE features
  (TUI diff-in-IDE among them). Verify bridge health by speaking MCP-over-WS directly (lockfile
  `authToken` in header `x-claude-code-ide-authorization`, subprotocol "mcp", `tools/call`).
- Two behaviours that LOOK like bugs and are not: image attachments persist as the bare API
  block (recompressed, no filename) → replay chips reading "file.jpg <smaller>" are correct;
  and a persisted non-default model writes a "/model <x>" audit record on every spawn, which
  untitled sessions then derive as their title.
- Synthetic transcript fixtures: stitch complete turns from REAL donor sessions (slice at turn
  starts, remap uuid/parentUuid per copy, rewrite sessionId) and verify with `./gradlew probe`
  before opening in the IDE. Inflated tail usage dies at the first live turn (newest request
  wins), so gauge tests need real bulk or a non-1M window.
- If per-turn rewind ever returns (removed 2026-07-30, see decisions.md): `rewind_files` needs
  `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1` — which also bloats EVERY transcript with
  file-history snapshots — plus a git repo and client-supplied uuids; dry_run first.

## Build / toolchain
- Build JVM must be **Java 21**: Gradle 8.10.2 refuses to run above JDK 23, and recent PhpStorm's
  bundled JBR is JDK 25. There is no `java` on PATH here — prefix
  `JAVA_HOME=~/.jdks/jdk-21.0.12+8` (the .zshrc export doesn't reach tool-run shells), and run
  every `./gradlew` from `plugin/`, not the repo root. `runIde` itself runs on the JBR inside the
  downloaded `phpstorm("2024.2")` dependency (JBR 21 `-jcef` with `libcef.so`) — that's what
  makes the webview work. First run downloads ~1 GB.
  (`instrumentCode`/`buildSearchableOptions` being off is explained in build.gradle.kts itself.)
- Do NOT re-add `testFramework(TestFrameworkType.Platform)` without platform tests: it registers
  a JUnit `LauncherSessionListener` that can't instantiate outside an IDE fixture and kills the
  test JVM. `SessionStore.claudeHome` is `internal var` so tests use a temp tree.
- REAL-IDE compat: 2025.x+ moved JCEF into a bundled plugin (`com.intellij.modules.jcef`);
  without the optional `<depends>`, ChatPanel dies with `NoClassDefFoundError: JBCefBrowser` on
  PhpStorm 2026.2 while the 2024.2 sandbox works fine. The sandbox CANNOT catch this class —
  smoke-test the zip in the real IDE.
- Verifier hygiene: `ReadAction.compute` AND Kotlin `runReadAction {}` are deprecated on 2026.1 —
  `Application.runReadAction(Computable)` is the one clean blocking-read API (wrapped as
  `readLocked {}`). `FileSaverDescriptor` vararg ctor deprecated 2025.1+, replacement absent on
  242 → reflection is the only warning-free both-ways route. Deprecation is per-IDE-version: the
  same zip shows MORE warnings on NEWER IDEs, never the reverse.
- `FileEditorManager.openFiles` does NOT report diff editors (measured live 2026-08-09: a
  visible "Claude: …" diff tab alongside an empty openFiles) — find-then-close of a diff tab is
  a silent no-op. Open diffs as your own `ChainDiffVirtualFile` via `FileEditorManager.openFile`
  and HOLD the handle; `closeFile(handle)` is the only reliable close (DiffReview does this).
- Verdict affordances in a diff editor, the dead-end chain (2026-08-09): toolbar icons via
  `DiffUserDataKeys.CONTEXT_ACTIONS` render fine but are unidentifiable among the standard diff
  toolbar icons (user rejected on sight); promoting them to TEXT buttons is impossible
  warning-free across 242→262 (`displayTextInToolbar()` is `@Deprecated(forRemoval)` on 262,
  its replacement `ActionUtil.SHOW_TEXT_IN_TOOLBAR` key doesn't exist on 242); a
  `DiffUserDataKeys.NOTIFICATION_PROVIDERS` banner takes arbitrary Swing but renders only at
  the TOP of the viewer, and is re-created per viewer (side-by-side and unified are separate —
  always build a fresh component). The shipped surface is
  `FileEditorManager.addBottomComponent(editor, bar)` on the editors returned by
  `openFile(diffFile, true)`: a bar UNDER the diff, attached to the FileEditor so it survives
  viewer switches and dies with the tab — all of the above verified in 242+262 bytecode.
  Two Swing-side traps from styling that bar: the LAF IGNORES `JButton.setBackground()` — color
  buttons via the `"JButton.backgroundColor"` / `"JButton.textColor"` / `"JButton.borderColor"`
  client properties (honored by DarculaButtonUI/DarculaButtonPainter on 242 and 262, checked in
  bytecode); and platform icons don't match the webview's Lucide glyphs (`AllIcons.Actions.
  Commit` isn't even a checkmark in the new UI — it's the VCS -o- glyph). Final shape bundles
  the card's own SVG_CHECK/SVG_X from chat.html as `/icons/accept.svg` + `reject.svg` (stroke
  colour hardcoded — the IDE's SVG loader has no `currentColor` context), loaded via
  `IconLoader.getIcon(path, Class)` (clean on 242+262; the zero-arg-context overloads are the
  deprecated ones). And the loader is STRICT XML: a literal `--` inside an SVG comment (say,
  a CSS var name like `--fg`) kills the whole parse — "String '--' not allowed in comment" —
  and the icon silently renders as nothing plus an IDE error balloon. No CSS var names in
  SVG comments.
- The IDE's VFS never sees the CLI's out-of-band writes: `editLineStart` reads files FRESH from
  disk because cached buffers lie, and open editors show stale content until a refresh (the
  "Reload from disk" symptom — fix shape in backlog.md). TIMING is the other half of that trap:
  `old_string` is only findable BEFORE the edit applies, so gutter lookups must fire at
  `content_block_stop`; by `tool_result` they correctly degrade to no line numbers.

## JCEF is not a browser (Linux)
- Text fields: the Delete key INSERTS keyChar 0x7F as a tofu char instead of forward-deleting
  (backspace is fine). chat.html carries a two-layer document-level workaround (manual
  forward-delete on keydown + capture-phase control-char strip on input), so any NEW text input
  gets both for free — don't add per-field key handling that swallows keydown first.
- EVERY webview JS keydown chord (F12, Ctrl+N, Ctrl+Alt+G) was dead on this machine even with the
  composer focused — the handlers never fired. All three were REMOVED 2026-08-09 and the plugin
  now binds no shortcuts at all; don't re-add a webview chord expecting it to work.
- OS file drags never reach the DOM — drag-drop needs an AWT `DropTarget` delivery layer feeding
  the page (`installFileDrop` → `window.__dropFiles`).
- DevTools: the JCEF context-menu route is DEAD (OSR — the enabling Registry key is captured once
  into a final field) and Ctrl+Alt+D is a WM grab. What works: Find Action → "Claude Brains: Open
  DevTools", or `http://localhost:9222` (port set by the `runIde` JVM arg — but a port hand-set
  in a sandbox's Registry WINS over it). The panel appears in `/json/list` only once the tool
  window has opened, titled "Claude Brains — chat panel".
- Sandbox startup noise, NOT ours: `GlobalMenuLinux <clinit> requests Experiments instance …
  Class initialization must not depend on services` is the 2024.2 platform's own class-init
  hygiene assertion, thrown while IT builds the Linux global menu during frame creation — no
  plugin code in the stack, fires once at EVERY runIde launch (idea.log shows it at each start
  since long before any plugin change). Ignore it; nothing to fix on our side.
- The runIde sandbox INVENTS UI symptoms, it doesn't only hide them (learned on manual-test 1.7).
  It runs PhpStorm 2024.2 on a fresh config with the stock keymap, so IDE-level key handling
  differs from a real install: a defect that reproduces ONLY there is suspect — reproduce any
  interaction bug against the installed IDE before chasing it in the renderer. Matching CDP
  limit: synthetic events go straight into the page and NEVER pass through IntelliJ's key
  dispatcher, so CDP can prove the page's own state machine correct while saying nothing about
  the IDE stealing focus on a real keypress. Don't let a clean CDP result close a focus question.

## Webview / debugging
- Headless Chrome is a DIFFERENT browser: (1) don't copy `mockup.html` elsewhere to probe — its
  stylesheet link is relative and silently resolves to nothing, measuring an UNSTYLED page; write
  probe copies into `design/`. (2) no compositor → rAF fires ~2.5/s, so rAF-driven animation
  reads frozen — stub `requestAnimationFrame` onto `setTimeout`. (3) `ResizeObserver` exists but
  never reliably fires — give RO-synced code a second trigger and test that. (Also docs/limits.md.)
- **The spliced-chat harness** (the standing lane for webview JS fixes): splice `chat.css` at
  `<!--CSS-->` and `window.LIMITS` + a `window.__bridge` stub at `<!--LIMITS-->` (capture LIMITS
  live: `cdp.py "JSON.stringify(window.LIMITS)"`), feed events through `window.onClaudeEvent`,
  assert via `document.title` under headless `--dump-dom`. Seed the slash roster with a real
  `system/commands_changed` event. Click/class logic only — the rAF/RO caveats above still
  apply, and see the containment trap below for what it CANNOT see. Assert on `#log`, never
  `document.body` — body.textContent includes chat.html's OWN script source, which legitimately
  contains the very literals under test (cost a false FAIL on 7.4's probe).
- `window.LIMITS` splice: the webview THROWS on load if unspliced (a JS default would be a second
  copy). `RenderLimitsTest` fails the build on hardcoded literals, marker count ≠ 1, or unbalanced
  script tags (the latter two silently truncate the whole script block = "dead webview"). Keep a
  captured `limits.json` in step with new keys or harness runs test stale shapes.
- A payload that is NEVER persisted has a third measurement lane beyond transcripts and a live
  run: `strings -n 8` on the CLI binary itself (`~/.local/share/claude/versions/<ver>`) — and on
  the installed VS Code `extension.js` for the host half. That lane read 7.4's payloads, 9.1's
  retry enum and double-emission, 10.1's allowlist, and 10.5's verdict consumers verbatim.
- `.turn-body`'s `content-visibility` PAINT-CONTAINS: any popup absolutely positioned inside a
  turn is clipped at the turn's box (6.4's card menu opened as a sliver) — and containment blocks
  HIT-TESTING too, so clipped elements still pass querySelector/synthetic-click assertions. That
  is a harness BLIND SPOT, not a harness bug. Escape hatch:
  `.turn-body:has(.card-menu.show){content-visibility:visible}`; the regression pin for any
  in-turn overlay is `elementFromPoint` at its center, never a DOM query. New in-turn popups need
  the same `:has` lift.
- `.popup`'s base `min-width: 330px` silently beats a smaller `width` on a variant — pair every
  narrower popup with `min-width: 0`, and MEASURE (a probe read 330 while the rule said 310).
  Same family as the UA `[hidden]` rule losing to `display: inline-flex`: assert COMPUTED style,
  never the property or the declaration you wrote.
- The cut-marker is a SIBLING of `.io-v`, never a child (`foldBlock` would fold it away). Fold ≠
  marker: the fold hides content it still holds; the marker reports content that is GONE.
- Replayed ask cards are marked `.ask-done` — that class must NOT be `.done` (the
  completion-summary line), whose 22px dot-column indent silently shifts the whole card.
- `RenderLimits.DESC_KEYS` is a GLOBAL chain — collision-check any new key or it hijacks other
  tools' lines. `todos`/`plan` stay OUT (structures; stringified they're worse than blank).
  Deliberately blank lines: Bash (IN box has the command), AskUserQuestion, ExitPlanMode,
  TodoWrite.
- When live and replay disagree, establish WHICH is correct before designing the fix — the
  duplicated-checklist bug looked like live over-rendering and was actually live UNDER-rendering
  (missing per-tool placement and status titles). Replay is usually the reference.
- Windowed replay means DOM search (browser find) only sees loaded blocks.
