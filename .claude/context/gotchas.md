# Gotchas — hard-won, don't rediscover

## Protocol / wire
**Payload shapes live in `docs/ide-mcp-protocol.md` — this section keeps only what that doc
can't tell you: which assumptions are WRONG.** Re-read the doc before trusting memory here.
- A stream-json CLI NEVER auto-connects from `CLAUDE_CODE_SSE_PORT` — that discovery is TUI-only;
  the bridge rides `--mcp-config`. Env var + lockfile stay for terminal-launched TUI sessions.
- `system/init` arrives only after the first user turn — send control_request `initialize` at
  startup or the panel opens blind. Its `commands` payload has NO type field, so the slash
  allowlist is the only lever for hiding commands.
- AskUserQuestion needs a `can_use_tool` reply carrying `updatedInput` with answers keyed by
  question TEXT; a plain allow silently returns "user did not answer".
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
  of must have a second source built from the tool_use INPUT (that is 4.4's fix; see
  decisions.md).
- A COMPOUND command (`a && b`) yields ONE addRules suggestion carrying several `rules[]`, not
  one suggestion per rule — build per-rule UI off `rules`, not off the suggestion list. Echoing
  a suggestion back with a NARROWED `rules` subset is accepted, and the CLI persists exactly the
  picked rules (wire-probed 2026-08-09).
- Hooks NEVER reach us as control requests: a hook denying a tool arrives as a plain
  `tool_result` with `is_error`; a hook blocking a NON-tool event emits `system/informational`
  with `prevent_continuation` and NOTHING else — unhandled it reads as a dead panel.
  `informational`'s optional `tool_use_id` DEDUPES rather than stacks, and it is persisted, so
  both paths must render it. Hooks are re-read from settings PER PROMPT, not cached at CLI
  spawn — removing a hook file takes effect on the next message of a LIVE session.
- Live wire vs transcript SPELLINGS differ for the same event: live `system/api_retry`
  `{attempt, max_retries, retry_delay_ms, error_status, error:"<string>"}` vs persisted
  `system/api_error` `{retryAttempt, maxRetries, error:{message, formatted}}` — `error` even
  changes TYPE. Same family: `prevent_continuation`/`preventContinuation`. Never trust the
  transcript spelling for a live handler; accept both on both paths.
- The `assistant` event's `uuid` is the SAME uuid the CLI writes into the transcript record
  (timestamp too) — the only handle tying a live render to its replayed twin. Don't assume
  live-only state can't be persisted without checking for a shared uuid.
- Sub-agent progress (`task_started`/`task_progress`/`task_notification`) is LIVE-ONLY, never
  persisted; `task_notification` omits `subagent_type` (remember it from `task_started`). Child
  `assistant`/`user` events (`parent_tool_use_id`) are deliberately ignored.
- `background_tasks_changed` has REPLACE semantics — assign the set, never merge, or finished
  tasks live forever. It's a LEVEL signal → a chip reflecting the present, not timeline entries.
- `result.modelUsage` is a MAP that routinely includes side models the user never picked: match
  the raw key (it carries the `[1m]` tag) → `canonicalModel` → on no match change NOTHING. No
  denominator exists until the first turn ends, and the `[1m]` seed heuristic must check BOTH
  `resolvedModel` and `value` (fable differs). Usage above the known window PROMOTES to 1M, so a
  big session shows the same % on a 200k and a 1M model — correct, not a stuck denominator.
- `queue-operation` records are the CLI's own pipeline bookkeeping (matched enqueue/dequeue
  pairs, one per turn) — there is no CLI queue to drive; message queueing is client-side.
- No `set_effort`/`set_thinking_level` control request exists (only `set_max_thinking_tokens`, a
  token count) — the effort slider rides a muted `/effort` turn (`effortMuted`, idle-gated).
- Tool-returned images: discriminator is `toolUseResult.type=="image"` — NOT `isImage`, a
  Bash-result field that is always false. `dimensions` is
  `{originalWidth,originalHeight,displayWidth,displayHeight}`, not `{width,height}`.
- Many transcript `thinking` blocks have an EMPTY body and only a `signature` (~2.1k of 6.6k
  local ones carry text) — those replay as nothing, correctly.
- CLI 2.1.226 exposes ONLY `mcp__ide__getDiagnostics` to the MODEL — openFile / openDiff /
  getCurrentSelection are refused ("No such tool available") even though the bridge advertises
  them and the CLI connects fine. Verify bridge health by speaking MCP-over-WS directly (lockfile
  `authToken` in header `x-claude-code-ide-authorization`, subprotocol "mcp", `tools/call`) —
  that path proved openFile/selection/openDiff all work plugin-side.
- Transcript format CHANGED at 2.1.226: one assistant record per message (blocks inline), plus
  record types that didn't exist before (`queue-operation`, `attachment` = tool-roster deltas NOT
  files, `ai-title`, `last-prompt`, `mode`, `custom-title`). OLDER files persist one record PER
  BLOCK, each repeating the same *cumulative* `message.usage` — summing `output_tokens` over
  those over-reports ~2.45x unless deduped by `message.id`. Live is immune. Never assume one
  transcript's shape generalizes.
- Two behaviours that LOOK like bugs and are not: image attachments persist as the bare API block
  (recompressed, no filename), so replay chips reading "file.jpg <smaller size>" are correct; and
  a persisted non-default model makes every spawn write a "/model <x>" audit record as the first
  user record, which untitled sessions then derive as their title.
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
  `system/commands_changed` event. Click/class logic only — the rAF/RO caveats above still apply,
  and see the containment trap below for what it CANNOT see.
- `window.LIMITS` splice: the webview THROWS on load if unspliced (a JS default would be a second
  copy). `RenderLimitsTest` fails the build on hardcoded literals, marker count ≠ 1, or unbalanced
  script tags (the latter two silently truncate the whole script block = "dead webview"). Keep a
  captured `limits.json` in step with new keys or harness runs test stale shapes.
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
- Edit-diff gutter numbers (`ChatPanel.editLineStart`) read the file FRESH from disk — the CLI
  edits out-of-band, so cached buffers lie. TIMING is the trap: `old_string` is only findable
  BEFORE the edit applies, so the request must fire at `content_block_stop`; by `tool_result` the
  lookup fails and the diff must degrade to no gutter numbers rather than block.
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
