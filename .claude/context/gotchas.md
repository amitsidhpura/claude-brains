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

- The task lifecycle is NOT sub-agent-only. `task_started`/`task_progress` hang off ordinary tool
  uses too — the 2.1.226 emit site sits among `runningSubagents`, `isBackgrounded`/`is_backgrounded`
  and `local_workflow`/`workflowName` — so a plain Bash can produce a frame whose `description` is
  the string its tool line already shows. `taskLine` suppresses that on an exact match only, since
  for a real sub-agent the description is running commentary that changes.
- `system/init` carries `cwd` (`subtype:"init",cwd:e.cwd,session_id:…`), but ONLY at the first turn
  of a session — a resumed transcript renders before it arrives. That is why the project root is
  pushed by ChatPanel as `__project` and init's cwd is only a refresh.
- The CLI appends session sidecar records with `open(O_WRONLY|O_APPEND)` per write (`vRn` in the
  binary). That is why APPENDING to a live transcript is safe — a rename's `custom-title` record
  survives the CLI's next write — while DELETING one is not. Two different halves of the same fact.
- **A record that is appended when something HAPPENS can be anywhere in the file, so no window scan
  can find it.** `custom-title` and `ai-title` land at whatever offset the thread had reached — a
  rename on a 10,458-line transcript sat on the last four lines and a 400-line head scan showed the
  derived title forever, so the rename looked like it had done nothing (0.5.0 shipped this). A tail
  window is no safer: it just moves the blind spot to "renamed, then kept working". Full scan with a
  substring pre-filter is the only correct shape — the cost is what `tokensOf` already pays. Ask of
  any new transcript reader: is this record written at a FIXED position (session head, last turn) or
  at an arbitrary one?

## Build / toolchain
- **`pluginVerification { ides { recommended() } }` can take the WHOLE project offline.** It
  resolves the Android Studio releases list from `jb.gg/android-studio-releases-list.xml` →
  `teamcity.jetbrains.com` at CONFIGURATION time, so if that one host is unreachable, EVERY task
  fails — `test`, `runIde`, `probe` — after ~23s with a bare "Connection timed out: connect" that
  names neither the URL nor the verifier. `--offline` does NOT skip it (the value source ignores
  offline mode). A stored configuration-cache entry masks it until something invalidates the cache,
  which is why it appears to strike at random. Cost a whole afternoon on 2026-08-12.
  Diagnosis order that worked: `curl` the three JetBrains hosts with `-w "%{time_connect}"` — the
  IDE repo and `data.services` were fine and only the `jb.gg` redirect target hung. Do NOT chase
  IPv6 first (it was ruled out with a 6-line Java program: 1.1s either way).
  **Fixed 2026-08-12: `-PskipVerifierIdes` empties the list so the rest of the project builds
  through the outage.** `verifyPlugin` REFUSES to run under it (an empty list verifies nothing and
  reports success — a rubber stamp on the one run meant to catch incompatibility), and
  docs/release.md says never to release with it.
  Escape hatch if the flag is ever unavailable: `SessionStore` is platform-free, so compile it with the cached
  `kotlin-compiler-embeddable` (`java -cp <compiler>;<stdlib>+coroutines+reflect+annotations+trove4j
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler …`) and run the assertions as a `main`. Needs `cygpath
  -w` for every path and `;` separators, and the jar globs must exclude `-sources`.
- Build JVM must be **Java 21**: Gradle 8.10.2 refuses to run above JDK 23, and recent PhpStorm's
  bundled JBR is JDK 25. On the current Windows machine `java` IS on PATH and `JAVA_HOME` is set
  (`~/.jdks/ms-21.0.12`), so `./gradlew` just works — on a machine where it isn't, prefix
  `JAVA_HOME=<jdk-21>`. Run every `./gradlew` from `plugin/`, not the repo root. `runIde` itself runs on the JBR inside the
  downloaded `phpstorm("2024.2")` dependency (JBR 21 `-jcef` with `libcef.so`) — that's what
  makes the webview work. First run downloads ~1 GB.
  (`instrumentCode`/`buildSearchableOptions` being off is explained in build.gradle.kts itself.)
- `org.gradle.configuration-cache=true` is ON (plugin/gradle.properties): a task action (`doFirst`,
  `doLast`) that reads a script-level `val` — or `project` — captures the whole build-script object,
  and the build dies with "cannot serialize Gradle script object references". Evaluate what the
  action needs into LOCALS inside the configuration block and let the lambda close over those.
  The failure names the task, not the capture, so it reads like a plugin bug.
- The Marketplace runs the Plugin Verifier on every upload itself (0.5.1: Compatible on 2024.2.6 →
  2026.2.1, verifier 1.408, visible on the plugin's Versions page). So a local `./gradlew
  verifyPlugin` earns its keep only when a change touches platform API and you want the answer
  BEFORE spending a version number — it is not a per-release ritual. Worth knowing before paying for
  seven IDE downloads on a fresh machine.
- GitHub Actions: a `workflow_dispatch` trigger only appears once the workflow file is on the
  DEFAULT branch — `gh workflow run` on an unpushed workflow just says it doesn't exist. So the
  first dry run of `.github/workflows/marketplace-upload.yml` can only happen after a push to main,
  and pushing it also arms the automatic upload for the very next `gh release create`.
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
- A desktop-launched IDE has the GUI session's environment, NOT the shell's — nvm/pyenv PATH
  layers are missing, so CLI-spawned `npx …` MCP servers die under the IDE while identical
  config works in a terminal (found by the 0.4.0 smoke test, minutes after the new MCP-failure
  banner first shipped; 0.3.3 had the same failure SILENTLY). The platform's fix,
  `EnvironmentUtil.getEnvironmentMap()`, loads the shell env ONLY on macOS —
  `shouldLoadShellEnv()` opens `if (!isMac) return false` (242 AND 262 bytecode; an
  API existing is not the API working — the first fix attempt overlaid a no-op on Linux).
  Linux is covered by our own `ShellEnv` (`$SHELL -l -i -c "command env -0"`, watchdogged,
  warmed at service init, empty-map degradation); its capture+parse was proven against this
  machine before shipping (93 entries, nvm npx on PATH). Windows needs neither: GUI processes
  get the full registry environment.
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
- **A scrollbar is painted at the bottom of its container's PADDING box.** So the element that
  carries the padding must ALSO be the one that scrolls, or the bar floats mid-box: `.io` had
  border on `.io`, padding on `.io-row` and `overflow-x` on `.io-v`, which inset the bar 42px from
  the left and left a 6px gap under it. `.diff` puts all three on one element, which is the whole
  reason it looks right. Same element for both is now a written contract in chat.css.
- **`overflow: hidden` from `.fold:not(.open)` crops only the element it lands on.** Fold the
  inner value while the outer row scrolls and a COLLAPSED block keeps its scrollbar. Give
  `foldBlock` the scroll box itself and "crops folded, scrolls expanded" costs nothing.
- **`position: sticky` is clamped to its containing block, and that beats your offsets.** The
  idiom "padding on the parent + equal negative margin on the child, so the sticky box reaches the
  padding edge" does NOT work: the child cannot move left of the parent's CONTENT box, so it gets
  shoved right by exactly the padding and silently eats the flex `gap` after it — `OUT` ended up
  jammed against its value. Put the padding on the sticky element instead and give the parent none
  on that side.
- **Sticky cannot rescue a `flex: 0 0 100%` item**: it fills its containing block, so there is no
  slack to shift and it drifts with the scroll anyway. Worse, `100% + margin-left` is WIDER than
  the container, so every row carrying one gained phantom scroll (32px) even with short content.
  Anything that must not scroll belongs outside the scroll container, full stop.
- **`flex-wrap: wrap` + a content-sized child = the child on its own line.** `.io-row` wrapped only
  because the cut marker needed a second line; once `.io-v` became `flex: none`, a wide value
  dropped below its label. Removing the wrap was only possible after the marker moved out.
- **`box-sizing: border-box` is global here, so padding counts against `min-width`.** Adding 10px
  of padding to `.io-k` silently dropped its 22px content floor to 12px, and IN/OUT rows stopped
  agreeing on where the value starts. Floors on padded boxes must be written as content + padding.
- **The mockup links `chat.css` with no cache-buster**, so a `?v=N` on the HTML re-fetches the page
  and serves the STALE stylesheet — a CSS fix measured that way reads as "no effect". Swap the
  `<link>` for a cache-busted one from JS before measuring, or you will debug a fix that already
  worked.
- **A bubbling document listener cannot see a click on any control that `stopPropagation`s.** The
  rename outside-click dismissal was built in the existing handler at `chat.html`'s
  `document.addEventListener('click', …)` and failed on `#histBtn`, the model/mode chips and the
  effort dots — every control BESIDE the title, i.e. the likeliest next click. `tg(id, e)`,
  `histBtn.onclick` and the effort dots all stop propagation so they don't self-close. Capture
  phase (`…, true`) runs before any target handler and is immune. Check what stops propagation
  before adding a dismissal to the shared handler.
- **`grep` silently returns NOTHING for transcript files — they trip binary detection.** A search
  for `task-notification` across `~/.claude/projects/*/*.jsonl` printed zero hits and nearly killed
  a correct hypothesis; `grep -a` found three files instantly. Same family as the NUL byte that
  made ripgrep call `journal.md` binary. **Always `-a` on transcripts**, and treat "no hits" in a
  jsonl search as unproven until re-run with it.
- **The transcript and the live wire disagree about what exists.** A background task's completion
  notification is persisted as a `user` record (visible in the jsonl), but the live stream-json
  emits NO user frame for it at all — measured across two captures. A hypothesis built from
  transcripts alone named a hook (`onUserEvent`) that can never fire live. Transcripts prove what
  the CLI RECORDS; only a stream capture proves what it SENDS.
- **Reproducing a live-path bug without the IDE**: spawn `claude` with `ClaudeCli.kt`'s own flags
  (`--input-format stream-json --output-format stream-json --include-partial-messages --verbose`),
  keep stdin OPEN so the session outlives the turn — the interesting frames arrive after it — and
  record stdout with timestamps. Then replay those real lines into `window.onClaudeEvent` on the
  spliced chat.html (below) and assert on DOM state. Real frames + real renderer, no IDE, and the
  capture doubles as fixture provenance. Captures kept in `_local/wire*.jsonl`.
- **Background-task bugs are ORDERING-dependent, so one repro is not enough.** The same code gave
  opposite symptoms: a short-lived background process (roster empties BEFORE the turn's `result`)
  left the button on Send while a CLI-started turn printed; a long-lived one (roster still full at
  the `result`) stuck it on Stop for the whole process. Each masked the other. When a defect
  involves an async task, vary its DURATION relative to the turn.
- **A synthetic fixture whose turns are 3 blocks long cannot catch a turn-boundary bug.** The
  windowed-replay eviction scanned a bounded number of blocks ahead for a `user` block; the test
  fixture emitted user+assistant+summary, so a boundary was never more than 2 blocks away and it
  passed. Real transcripts are ~28 blocks a turn (9 user messages in 253 retained blocks of
  `metrobuildsuppliers`), the scan gave up constantly, and the panel opened mid-turn on an
  assistant reply with no question above it. Shape the fixture like the real data, or the green
  run means only that the fixture agrees with the code.
- **`#fade-top` sits above ordinary content but below the sticky `.msg-user` (z 6).** So anything
  that is normally FIRST in the log is invisibly protected by that stickiness, and any NEW kind of
  first block (the `truncated` marker) lands under a 48px wash and is unreadable. It is now
  switched off at `scrollTop <= 1`, where it was describing content that does not exist anyway.
  Check the top-of-log state whenever a new block type can be first.
- **CSS layout claims must be measured on REAL JCEF, not headless.** Beyond the rAF/RO caveats
  below, headless disagreed on *rendered output*: it showed a flex child ellipsised where JCEF
  measured it whole (70/70), and two headless runs of the same page disagreed with each other
  once a probe's own `<pre>` widened the container. Iterating on the ring/path work only stopped
  going in circles after switching to `tools/cdp.py` against the live panel. Headless is fine for
  click/class logic; for anything about WIDTH, ask the browser it ships in.
- **A column flex container stretches its children.** `.tool-imgs` had no `align-items`, so a
  64x64 result image rendered 474x320 — full panel width, the picture letterboxed into a corner by
  `object-position`. `align-items: flex-start` is what makes an image size to itself. Any
  column-flex box holding intrinsically-sized content has this.
- **Making a row `display: flex` lets it outgrow its container.** Turning `.tool-line` into a flex
  row with nowrap children gave it a min-content width of label + filename; inside an ancestor that
  sizes to content, the ROW got wider than the panel and the panel clipped the filename — one line,
  but the wrong content lost. `max-width: 100%` + `min-width: 0` on the row is what points the
  shrink at the intended child.
- **`textContent = …` deletes sibling controls.** Three surfaces now hold [icon, text]: the context
  chip (ring + digits), the header title (`.t-txt` + pencil), the queue row. Writing `textContent`
  on the wrapper removes the icon on the first refresh and never brings it back — write the text
  node / `.t-txt`, not the parent. `renderContext` and `setTitle` both do.
- **Rendering a list and OPENING it are different acts.** `renderHistory` ended with an
  unconditional `histPanel.classList.add('show')`, safe only while a `sessions` frame could arrive
  for one reason. The moment Kotlin re-pushed the list after a rename, saving popped the panel
  open. Guard: a `histWanted` flag set by the button, plus "already open" so a delete under an open
  panel doesn't close it. Any future unsolicited push has the same shape.
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
  never the property or the declaration you wrote. That trap BIT AGAIN 2026-08-09 (#queue): any
  hidden-toggled element whose ID rule sets `display` needs its own `[hidden]{display:none}`
  re-assert (`.chip-btn[hidden]` / `#queue[hidden]`), or even an empty element keeps painting
  its margins — the composer carried 6px of permanent phantom spacing that way.
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
