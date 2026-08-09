# Gotchas — hard-won, don't rediscover

## Protocol / wire (full reference: docs/ide-mcp-protocol.md)
- A stream-json CLI NEVER auto-connects from `CLAUDE_CODE_SSE_PORT` — that discovery is
  TUI-only (§3b, learned 2026-08-01). ClaudeCli passes the bridge via `--mcp-config` (server
  "ide", type "ws"; "ws-ide" is filtered from user config). Env var + lockfile stay for
  terminal-launched TUI sessions.
- `system/init` arrives only after the first user turn; send control_request
  `{subtype:"initialize"}` at startup to get `{commands, models, account}` immediately.
  The `commands` payload is `{name,description,argumentHint}` only — NO type field, so the
  slash allowlist is the only lever.
- AskUserQuestion: answer via `can_use_tool` reply with
  `updatedInput={questions, answers:{"<question>": "<label(s)>"}}` — plain allow returns
  "user did not answer".
- Permission gate ONLY works with `--permission-prompt-tool stdio`. `acceptEdits` covers EDITS
  ONLY (Bash still asks — correct). `auto` is a safety-checked mode, NOT `bypassPermissions`.
  `permissionMode` on `system/init`/`system/status` drives the chip (plan approval drops to
  `default`). Malformed `updatedPermissions` are silently dropped. `blocked_path`
  (sandbox-escape) prompts re-ask no matter what is granted — strip suggestion buttons there.
  Refused control requests answer `subtype:"error"` → surfaced as `__ctl_error`. (§5b)
- Hooks NEVER reach us as control requests (probed 2.1.222): a hook denying a tool arrives as a
  plain `tool_result` with `is_error`; a hook blocking a NON-tool event emits
  `system/informational` with `prevent_continuation` and NOTHING else — unhandled it reads as a
  dead panel. `informational`'s optional `tool_use_id` DEDUPES rather than stacks; it is
  persisted, so both paths must render it.
- Live wire vs transcript SPELLINGS differ for the same event: live `system/api_retry`
  `{attempt, max_retries, retry_delay_ms, error_status, error:"<string>"}` vs persisted
  `system/api_error` `{retryAttempt, maxRetries, error:{message, formatted}}` — `error` even
  changes TYPE. Same family: `prevent_continuation`/`preventContinuation`. Never trust the
  transcript spelling for a live handler; accept both on both paths.
- Transcripts persist ONE record PER CONTENT BLOCK, each repeating the same *cumulative*
  `message.usage` — summing `output_tokens` over records over-reports ~2.45x unless deduped by
  `message.id` (first record is authoritative; 2546 split messages checked). Live is immune —
  `message_delta.usage` fires once per message.
- The `assistant` event's `uuid` is the SAME uuid the CLI writes into the transcript record
  (timestamp too) — the only handle tying a live render to its replayed twin (the
  completion-summary verb uses it). Don't assume live-only state can't be persisted without
  checking for a shared uuid.
- Sub-agent progress (`task_started`/`task_progress`/`task_notification`) is LIVE-ONLY, never
  persisted; `task_notification` omits `subagent_type` (remember it from `task_started`).
  Child `assistant`/`user` events (`parent_tool_use_id`) are deliberately ignored.
- `background_tasks_changed` has REPLACE semantics — assign the set, never merge, or finished
  tasks live forever. It's a LEVEL signal → a chip reflecting the present, not timeline entries.
- `result.modelUsage` is a MAP that routinely includes side models the user never picked.
  Match the raw key (carries the `[1m]` tag, like `currentModel`) → then `canonicalModel` → on
  no match change NOTHING. It rides the `result` event, so no denominator until the first turn
  ends; the seed heuristic: `[1m]` tag sits on `resolvedModel` for default/opus but on `value`
  for fable — check both. Usage above the known window still promotes to 1M.
- `queue-operation` records are the CLI's own pipeline bookkeeping (matched enqueue/dequeue
  pairs, one per turn) — there is no CLI queue to drive; message queueing is client-side.
- No `set_effort`/`set_thinking_level` control request exists (probed — only
  `set_max_thinking_tokens`, a token count) — the effort slider rides a muted `/effort` turn
  (`effortMuted`, idle-gated).
- Tool-returned images: discriminator is `toolUseResult.type=="image"` — NOT `isImage`, a
  Bash-result field that is always false. `dimensions` is
  `{originalWidth,originalHeight,displayWidth,displayHeight}`, not `{width,height}`.
- Many transcript `thinking` blocks have an EMPTY body and only a `signature` (~2.1k of 6.6k
  local ones carry text) — those replay as nothing, correctly.
- CLI 2.1.226 exposes ONLY `mcp__ide__getDiagnostics` to the MODEL — openFile / openDiff /
  getCurrentSelection are refused ("No such tool available") even though the bridge advertises
  them and the CLI connects fine. Verify bridge health by speaking MCP-over-WS directly
  (lockfile `authToken` in header `x-claude-code-ide-authorization`, subprotocol "mcp",
  `tools/call`) — that path proved openFile/selection/openDiff all work plugin-side.
- 2.1.226 transcript format: ONE assistant record per message (blocks inline — the old
  per-block cumulative-usage gotcha above applies to OLD files only), plus new record types
  (`queue-operation`, `attachment` [tool-roster deltas, NOT files], `ai-title`, `last-prompt`,
  `mode`, `custom-title` from TUI `/rename`). Image attachments persist as the bare API block —
  recompressed, NO filename — so replay chips saying "file.jpg <smaller size>" are CORRECT.
- With a non-default model persisted, every CLI spawn writes a "/model <x>" audit record as the
  transcript's first user record (panel sends `set_model` at startup) — same accepted
  audit-trail family as `/effort`; untitled sessions derive it as their title (accepted).
- Hooks are re-read from settings PER PROMPT, not cached at CLI spawn — removing a hook file
  takes effect on the very next message of a LIVE session (probed 2026-08-08 via a
  UserPromptSubmit exit-2 hook).
- `blocked_path` is BASH-ONLY (write outside cwd / network). A Write/Edit tool targeting an
  out-of-workspace or root-owned path is a NORMAL permission ask WITH suggestions — don't
  mis-read that card as a broken 6.6 (cost real time on the pass).
- The context gauge's >window→1M promotion means a big session shows the SAME % under a 200k
  and a 1M model — correct, not a stuck denominator; hover the gauge for used/window truth.
- Synthetic transcript tricks that WORK for fixtures: stitch complete turns from real donor
  sessions (slice at turn starts, remap uuid/parentUuid per copy, rewrite sessionId), verify
  with `./gradlew probe` before opening in the IDE. Inflated tail usage dies at the first live
  turn (newest request wins), so gauge tests need real bulk or a non-1M window.
- If per-turn rewind ever returns: `rewind_files` needs
  `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1`, a git repo, and a client-supplied `uuid` on
  sent user messages; success is `{canRewind, skippedLinks}` (no filesChanged) → dry_run first.
  The env var also makes the CLI write file-history snapshots into every transcript.

- JCEF-on-Linux text fields: the Delete key INSERTS keyChar 0x7F as a tofu char instead of
  forward-deleting (backspace is fine). chat.html carries a two-layer workaround (manual
  forward-delete on keydown + capture-phase control-char strip on input, document-level).
  Any NEW text input added to the page gets both for free — don't add per-field key handling
  that swallows keydown before the document handler sees it. Same event-conversion family as
  the dead F12/Ctrl+N chords: JCEF's keyboard path is not a browser's.

## Build / toolchain
- Build JVM must be **Java 21**: Gradle 8.10.2 refuses to run above JDK 23, and recent
  PhpStorm's bundled JBR is JDK 25. `runIde` itself runs on the JBR inside the downloaded
  `phpstorm("2024.2")` dependency (JBR 21 `-jcef` with `libcef.so`) — that's what makes the
  webview work. First run downloads ~1 GB.
- `instrumentCode = false` is required (crashes on MS JDK 21); `buildSearchableOptions = false`
  (no settings UI — by design, so this stays off).
- Do NOT re-add `testFramework(TestFrameworkType.Platform)` without platform tests: it
  registers a JUnit `LauncherSessionListener` that can't instantiate outside an IDE fixture and
  kills the test JVM. `SessionStore.claudeHome` is `internal var` so tests use a temp tree.
- REAL-IDE compat: 2025.x+ moved JCEF into a bundled plugin (`com.intellij.modules.jcef`);
  without the optional `<depends>`, ChatPanel dies with `NoClassDefFoundError: JBCefBrowser` on
  PhpStorm 2026.2 while the 2024.2 sandbox works fine. The sandbox CANNOT catch this class —
  smoke-test the zip in the real IDE.
- Verifier hygiene: `ReadAction.compute` AND Kotlin `runReadAction {}` are deprecated on
  2026.1 — `Application.runReadAction(Computable)` is the one clean blocking-read API (wrapped
  as `readLocked {}`). `FileSaverDescriptor` vararg ctor deprecated 2025.1+, replacement absent
  on 242 → reflection is the only warning-free both-ways route. Deprecation is per-IDE-version:
  the same zip shows MORE warnings on NEWER IDEs, never the reverse.

## Webview / debugging
- Headless Chrome is a DIFFERENT browser: (1) don't copy `mockup.html` elsewhere to probe — its
  stylesheet link is relative and silently resolves to nothing, measuring an UNSTYLED page;
  write probe copies into `design/`. (2) no compositor → rAF fires ~2.5/s, so rAF-driven
  animation reads frozen — stub `requestAnimationFrame` onto `setTimeout`. (3) `ResizeObserver`
  exists but never reliably fires — give RO-synced code a second trigger and test that.
  (Also docs/limits.md.)
- Webview DevTools on Linux (probed 2026-08-07, re-probed 2026-08-08): JCEF context-menu route
  is DEAD (OSR; `ide.browser.jcef.contextMenu.devTools.enabled` is captured once into a final
  field); Ctrl+Alt+D is a WM grab. What works: Find Action → "Claude Brains: Open DevTools" or
  `http://localhost:9222`. EVERY webview JS keydown chord (F12, Ctrl+N, Ctrl+Alt+G) was dead on
  this machine even with the composer focused — the handlers never fired — so all three were
  REMOVED on 2026-08-09 and the plugin now binds no shortcuts at all; don't re-add a webview
  chord expecting it to work. The debug port is set by the BUILD (`runIde` JVM arg
  `-Dide.browser.jcef.debug.port=9222`) — a system property legitimately sets any Registry key
  read at early startup — but a port hand-set in a sandbox's Registry still WINS over it.
  The panel appears in `/json/list` only once the tool window has opened (CEF starts with the
  first JBCefBrowser), listed by its `<title>` "Claude Brains — chat panel".
- The runIde sandbox INVENTS UI symptoms, it doesn't only hide them (learned 2026-08-09 on
  manual-test 1.7). It runs PhpStorm 2024.2 on a fresh config with the stock keymap, so
  IDE-level key handling differs from a real install: a defect that reproduces ONLY there is
  suspect. Reproduce any interaction bug against the installed IDE before chasing it in the
  renderer. The matching CDP limit: synthetic events dispatched over `tools/cdp.py` go straight
  into the page and NEVER pass through IntelliJ's key dispatcher — CDP can prove the page's own
  state machine correct (Escape and click-toggle leaving identical DOM), but it can say nothing
  about the IDE stealing focus on the real keypress. Page logic and IDE key routing need
  different instruments; don't let a clean CDP result close an IDE-focus question.
- `window.LIMITS` splice: the webview THROWS on load if unspliced (a JS default would be a
  second copy). `RenderLimitsTest` fails the build on hardcoded literals, marker count ≠ 1, or
  unbalanced script tags (the latter two silently truncate the whole script block = "dead
  webview"). Anything loading chat.html outside the IDE must splice LIMITS too — and that is
  cheap: splice chat.css at `<!--CSS-->`, `window.LIMITS` (capture from the live panel:
  `cdp.py "JSON.stringify(window.LIMITS)"`) + a `window.__bridge` stub at `<!--LIMITS-->`,
  feed events through `window.onClaudeEvent`, assert via `document.title` under headless
  `--dump-dom`. Proved out 2026-08-09 on the @-menu dismissal tests; click/class logic only
  (the rAF/RO headless caveats above still apply). The cut-marker
  is a SIBLING of `.io-v`, never a child (`foldBlock` would fold it away). Fold ≠ marker: the
  fold hides content it still holds; the marker reports content that is GONE.
- Replayed ask cards are marked `.ask-done` — that class must NOT be `.done` (the
  completion-summary line), whose 22px dot-column indent silently shifts the whole card.
- Edit-diff gutter numbers (`ChatPanel.editLineStart`) read the file FRESH from disk — the CLI
  edits out-of-band, so cached buffers lie.
- `RenderLimits.DESC_KEYS` is a GLOBAL chain — collision-check any new key or it hijacks other
  tools' lines. `todos`/`plan` stay OUT (structures; stringified they're worse than blank).
  Deliberately blank lines: Bash (IN box has the command), AskUserQuestion, ExitPlanMode,
  TodoWrite.
- Windowed replay means DOM search (browser find) only sees loaded blocks.
- `.turn-body`'s `content-visibility` PAINT-CONTAINS: any popup absolutely positioned inside a
  turn is clipped at the turn's box (6.4's card menu opened as a sliver) — and containment
  blocks HIT-TESTING too, so clipped elements still pass querySelector/synthetic-click harness
  assertions. Escape hatch: `.turn-body:has(.card-menu.show){content-visibility:visible}`;
  regression pin for any in-turn overlay is `elementFromPoint` at the element's center, never
  a DOM query. New in-turn popups need the same `:has` lift.
