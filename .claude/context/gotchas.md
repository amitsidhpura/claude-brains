# Gotchas — hard-won, don't rediscover

Each bullet is a RULE plus the minimum evidence to trust it. Payload shapes live in
`docs/ide-mcp-protocol.md` (§9 vocabulary, §10 measured facts) and caps in `docs/limits.md` —
re-read those before trusting memory here.

## Protocol / wire
- **The control-response parser WHITELISTS fields; everything else dies silently.** Allow admits
  `behavior`/`updatedInput`/`updatedPermissions` (deny adds `message`); a `feedback` field probed on
  2.1.233 reached the model in ZERO frames, no warning. The TUI's `acceptFeedback` is an internal
  extra text block on the tool_result, not a wire field. If a capability exists in the TUI, check the
  response SCHEMA before assuming the protocol exposes it.
- **A `new Set([...control subtypes])` in the binary is a ROUTING WHITELIST, not the stdio accept
  list.** Typed schemas prove VOCABULARY, never acceptance — only sending the request headlessly and
  reading the response settles it (`stop_task` entered the set at 2.1.238 while stdio had accepted
  `side_question`/`apply_flag_settings` since ≤2.1.233). The schema helper's minified name changes per
  build (`Tt` 2.1.233, `Ht` 2.1.241): match `subtype:<ident>("initialize")` first or a version diff
  silently returns zero for one side.
- **A hint read out of the binary is NOT the hint the wire sends.** `strings` shows two `/goal`
  records, one with `argumentHint:"[<condition> | clear]"`; the roster sends an EMPTY hint. Probe the
  live roster: spawn the CLI with the panel's flags, write one `{"type":"control_request","request":
  {"subtype":"initialize"}}`, read to the `control_response`. No turn, nothing persisted.
- **The roster is exactly `{name, description, argumentHint, aliases?}`** — no `immediate` flag on the
  wire though the binary carries one (51 entries enumerated, 2.1.233). "Does this act on click" can
  only come from `argumentHint`.
- **A custom command's wire marker is a DESCRIPTION SUFFIX** — every custom entry's description ends
  " (project)"/" (user)" (2.1.228, zero false positives across 107 built-ins). On disk the wrapper has
  TWO shapes: built-ins persist name→message→args, an arg-less custom one message→name with NO
  `<command-args>`. Match name and args independently or one shape leaks raw XML.
- **The CLI watches the PROJECT commands dir only**, not `~/.claude/commands` (drop → `commands_changed`
  ≈2.5s, delete ≈1s, no turn between). User-level needs `/reload-skills`. Sequence such probes with a
  QUIET WAIT (45s, no turns) before concluding an event never fires on its own — a `/reload-skills`
  sent straight after a file drop lands in the watcher's debounce and conflates the two.
- **A user message written to stdin mid-turn is read at the NEXT model call.** It races the model call
  cycle — one probe folded it into the first Write, a real run delivered it after implementation
  started. Anything that must precede the model's next action rides the SAME message it will read (for
  ExitPlanMode: `updatedInput.plan`, echoed in the tool_result).
- **`set_permission_mode` right after a plan approval ALWAYS loses** — the CLI restores `prePlanMode`
  when the approved ExitPlanMode executes, after your request is processed. Park the wish and send it
  on the post-approval `permissionMode` broadcast (plan→X is always a change, so it always fires).
- **The plan-exit restore broadcasts the LITERAL `default`, not `manual`** — `default` is the internal
  name for the mode `manual` advertises. A UI keyed on advertised names drops it and lies. Alias them.
- **An `assistant` frame is emitted PER CONTENT BLOCK, not per message** (`message_start →
  content_block_start → assistant → content_block_stop`, repeating, straddling `message_stop` in BOTH
  directions). A message that thinks first sends two, with different uuids. Per-message state consumed
  by "the assistant frame" is consumed too early — whole-message state belongs at TURN boundaries.
- **A local built-in's output has THREE spellings and none stream.** Live success = a bare
  whole-message `assistant` frame, zero stream events. Live failure = a `user` frame whose
  `message.content` is a **STRING** carrying `<local-command-stderr>` (an `!Array.isArray(content)`
  guard drops it). On disk = `system/local_command`, or the same wrappers on a plain `user` record.
- **A message the CLI consumed mid-turn persists ONLY as an `attachment`** (`{type:"queued_command",
  commandMode:"prompt"}`) — no user record follows, so replay ignoring attachments loses text the model
  acted on. One QUEUED to the next turn writes BOTH. Map attachments to bubbles only when no user record
  matches the text. `mode=task-notification` is machinery.
- **An async sub-agent's `tool_result` is a LAUNCH ACK, not the end of its work** — measured 1.8s after
  the call, reading "Async agent launched successfully…", while the agent ran for minutes. Anything keyed
  on "the result arrived" is wrong for agents; `isInternalResult()` already recognises the ack. What
  finishes one is `task_notification` (has `tool_use_id`) or `task_updated` (**no** `tool_use_id` — stash
  the tool on the task when an earlier frame supplies it). Both are LIVE-ONLY: zero occurrences on disk.
- **"The agent failed" ≠ "the agent's WORK failed", and only the first is knowable.** Status vocabulary
  is `completed|failed|killed` (verbatim, 2.1.228) — match those named states, not "anything ≠
  completed" (`running`/`pending` also exist; painting a live line red is worse than an unknown state
  green). An agent asked to fail still reports `completed`. The work's outcome is one level down and
  **whether it is recorded depends on how the agent ran the command** — foreground failure → errored
  tool_result, the same command under `run_in_background` → nothing. Identical work reads red or green
  by sandbox guard, which is why the sub-task dot was built and removed.
- **`is_error` does NOT mean the work failed — check for the `<tool_use_error>` wrapper.** Wrapped =
  the harness REFUSING the call (blocked compound command, invalid input); the tool never ran. Unwrapped
  = the command ran and failed (`Exit code 1`). Read the wrapper through BOTH content shapes (bare string
  on one path, array of text blocks on the other).
- **A sub-agent's child events never reach the parent's wire** — the parent transcript has zero
  `parent_tool_use_id`, only `isSidechain`. Children live in `subagents/agent-<id>.jsonl`, which the
  notification's `<output-file>` is a SYMLINK to; that field, not a path guess, is the handle.
- **The task lifecycle is NOT sub-agent-only** — `task_started`/`task_progress` hang off ordinary tool
  uses, so a plain Bash can emit a frame whose `description` is the string its tool line already shows.
  Suppress on an exact match only; a real sub-agent's description is changing commentary.
- **`background_tasks_changed` is a PER-PROCESS level signal.** The CLI's own schema: nothing is emitted
  at startup, so reset to empty whenever the CLI process (re)starts and let the next membership change
  repopulate. It fires only on CHANGE — any reset on the panel's own initiative (e.g. per turn) leaves
  the roster wrong with nothing coming to correct it. A background shell outlives its turn by design.
- **A background chip that looks stale may simply be right** — "2 tasks but nothing is running" was two
  genuinely-live orphaned `until <cond>; do sleep 30; done` waiters. Check the process tree
  (`ps --ppid <claude pid>`) first; the answer changes which bug you are looking at.
- A stream-json CLI NEVER auto-connects from `CLAUDE_CODE_SSE_PORT` — TUI-only discovery; the bridge
  rides `--mcp-config`. Env var + lockfile stay for terminal-launched TUI sessions.
- `system/init` arrives only after the first user turn — send control_request `initialize` at startup or
  the panel opens blind. It carries `cwd`, but only on the first turn of a session, which is why the
  project root is pushed as `__project` and init's cwd is a refresh.
- Permission gate works ONLY with `--permission-prompt-tool stdio`. `acceptEdits` covers EDITS only
  (Bash still asks — correct). `auto` is safety-checked, NOT `bypassPermissions`. Malformed
  `updatedPermissions` are silently DROPPED. Refused control requests answer `subtype:"error"`.
- `blocked_path` (sandbox-escape) re-asks whatever is granted → strip suggestion buttons there. It is
  BASH-ONLY: a Write/Edit to an out-of-workspace or root-owned path is a NORMAL ask WITH suggestions.
- **No card ⇒ no live diff.** Pre-approved tools (acceptEdits/auto, saved rule, authorized path) send NO
  `can_use_tool`, and the live wire never carries `toolUseResult`/`structuredPatch`. Anything the card is
  the sole producer of needs a second source built from the tool_use INPUT.
- A COMPOUND command (`a && b`) yields ONE addRules suggestion carrying several `rules[]` — build
  per-rule UI off `rules`. Echoing back a NARROWED `rules` subset is accepted and persisted exactly.
- Hooks NEVER reach us as control requests: a hook denying a tool arrives as a plain `tool_result` with
  `is_error`; one blocking a NON-tool event emits `system/informational` with `prevent_continuation` and
  nothing else (unhandled it reads as a dead panel). `informational`'s optional `tool_use_id` DEDUPES
  rather than stacks, and it is persisted, so both paths must render it. Hooks are re-read PER PROMPT,
  so removing a hook file takes effect on the next message of a LIVE session.
- **`hook_callback` is a blocking control request** — the CLI waits to `hook_callback_timeout`. Every
  path must answer, exceptions included; reply AFTER the side effect (from the EDT for saves).
- Model-facing IDE tools are an upstream ALLOWLIST (getDiagnostics + executeCode), byte-identical across
  2.1.222–226 — stable policy, not a regression. Do NOT dodge it by renaming the bridge server: the CLI
  finds its IDE client by the literal name `"ide"` for its own IDE features.
- The `assistant` event's `uuid`/timestamp is the SAME one written to the transcript — the only handle
  tying a live render to its replayed twin. Don't assume live-only state can't be persisted.
- Two behaviours that LOOK like bugs: image attachments persist as the bare API block (no filename), so
  replay chips reading "file.jpg <smaller>" are correct; and a persisted non-default model writes a
  "/model <x>" audit record per spawn, which untitled sessions derive as their title.
- If per-turn rewind ever returns (removed 2026-07-30): `rewind_files` needs
  `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1` (which bloats EVERY transcript with file-history
  snapshots), a git repo and client-supplied uuids; dry_run first. **Never probe it on a live CLI** — it
  mutates workspace files.

## Replay / transcript
- **Live wire and transcript disagree in SPELLING and in ORDER for the same events.** Field names and
  even TYPES differ; the file order of late-flushed records lies while timestamps + the `parentUuid`
  chain stay chronological. Accept both spellings on both paths; trust timestamps over file position.
  Worst case measured: `/context`'s output is a bare whole-message `assistant` frame live but persists as
  `system/local_command` with a `<local-command-stdout>` wrapper — NEITHER path shares a spelling.
- **The transcript proves what the CLI RECORDS; only a stream capture proves what it SENDS.** A
  background task's completion notification is persisted as a `user` record but emits NO user frame live
  — a hypothesis built from transcripts alone named a hook that can never fire.
- **A dying CLI writes one more record, and replay races it.** Killed with a permission pending, it
  appends its auto-deny tool_result ("Tool permission request failed: AbortError…",
  `toolDenialKind:"permission-rule"`) within ms of stdio closing. Read the transcript before that write
  and the record shows up one reload LATE. `stopForReplay()` closes the race but waits ONLY when
  `pendingPermissions.isNotEmpty()` — an unconditional wait slowed every ordinary reload.
- **A two-way decided/denied branch treats "no record" as the happy path** — replayCard drew "✓ Approved"
  for a plan whose tool_use never got a tool_result. Any replayed decision needs a third neutral state for
  "the transcript does not say": absence of evidence is not consent (`undecided` flag, `.und-t` footer).
- **A resumed CLI does NOT re-emit a pending permission.** `--resume` on a transcript ending in a dangling
  ExitPlanMode produces no frames at all (probed with and without the `initialize` handshake) and writes
  nothing. The pending request dies with its process; only the transcript says it existed.
- **A record appended when something HAPPENS can be anywhere in the file, so no window scan finds it.**
  `custom-title`/`ai-title` land wherever the thread had reached — a rename on a 10,458-line transcript
  sat on the last four lines and a 400-line head scan showed the derived title forever. A tail window just
  moves the blind spot. Full scan with a substring pre-filter is the only correct shape. Ask of any new
  transcript reader: is this record at a FIXED position or an arbitrary one?
- **The transcript does not exist yet at `system/init`** — missing when init arrives, 15 KB by
  `message_start` (2.1.229). So `message_start` is the earliest frame anything derived FROM THE FILE can
  be read at, and a session id arriving is NOT evidence the file is there. `titleOf` returning null is
  normal for the first seconds.
- The CLI appends sidecar records with `open(O_WRONLY|O_APPEND)` per write. That is why APPENDING to a
  live transcript is safe (a rename's record survives) while DELETING one is not.
- **`grep` silently returns NOTHING for transcript files** — they trip binary detection. Always `-a` on
  `*.jsonl`, and treat "no hits" as unproven until re-run with it.
- Synthetic fixtures: stitch complete turns from REAL donor sessions (slice at turn starts, remap
  uuid/parentUuid, rewrite sessionId) and verify with `./gradlew probe` before opening in the IDE.
  Inflated tail usage dies at the first live turn, so gauge tests need real bulk or a non-1M window.
- **A synthetic fixture whose turns are 3 blocks long cannot catch a turn-boundary bug** — real
  transcripts run ~28 blocks a turn, so a bounded forward scan that passed the fixture gave up constantly
  on real data. Shape the fixture like the real data.
- When live and replay disagree, establish WHICH is correct before designing the fix — the
  duplicated-checklist bug looked like live over-rendering and was live UNDER-rendering. Replay is
  usually the reference. Windowed replay also means browser find only sees loaded blocks.

## IDE platform / VFS
- **`LocalFileSystem.findFileByPath` reads the VFS SNAPSHOT, never the disk.** A file written behind the
  IDE's back doesn't resolve until something refreshes its PARENT — measured ~2 minutes to self-heal at a
  project root, and never for a directory the IDE doesn't index (`_local/`). This is what made a `Read`
  path click report "File not found". Use `findVFileOnDisk` (Vfs.kt) on any "open this path" entry point.
- **But `refreshAndFindFileByPath` refreshes SYNCHRONOUSLY — never call it under a read lock** (deadlocks
  against the refresh session). Permanently splits the callers: "open this path" callers hold no lock and
  may refresh; everything inside `readLocked` keeps plain `findVFile`. Dirty-document callers keep it for
  a second reason — a file the VFS never saw has no unsaved document.
- **The two lookups make each other a free control:** probe `checkDocumentDirty` (snapshot-only) and
  `openFile` (disk-consulting) on the SAME path in one run to prove staleness without racing the heal
  window. Snapshot call must go FIRST — the refreshing one populates the VFS.
- **`openFile` on our own MCP bridge is a free "what does the VFS know" probe** — `error: file not found`
  vs `opened:`. Speak to the bridge as the CLI does (lockfile `authToken` in header
  `x-claude-code-ide-authorization`, subprotocol `mcp`, `tools/call`). **Pick the right lockfile:**
  several IDEs may hold one workspace — filter on `ideName` or you hit VS Code's bridge. Prefer read-only
  tools (`checkDocumentDirty`, `getOpenEditors`) against the USER's IDE; `openFile` opens tabs in it.
- **`getDiagnostics` needs the daemon to have run, so an empty result proves nothing** — confirm the probe
  can see a fault at all by opening an already-broken file first.
- **`FileEditorManager.openFiles` does NOT report diff editors** (measured: visible diff tab, empty
  openFiles), so find-then-close of a diff tab is a silent no-op. Open diffs as your own
  `ChainDiffVirtualFile` and HOLD the handle; `closeFile(handle)` is the only reliable close.
- **The IDE's VFS never sees the CLI's out-of-band writes**: `editLineStart` reads files FRESH from disk
  because cached buffers lie. TIMING is the other half — `old_string` is only findable BEFORE the edit
  applies, so gutter lookups fire at `content_block_stop`; by `tool_result` they degrade to no numbers.
- **The CLI writes files with Bash far more often than you'd guess** — asked to create one file and
  overwrite another, it used a single `Bash` call for both. Anything keyed on the writing TOOLS catches a
  fraction of real writes; Bash names no path, so pair the per-file hook with a sweep at `result`.
- **`~/.claude/ide/*.lock` accumulates dead locks** (16 seen) — a probe taking "the newest" can pick a
  corpse. Pick by `os.kill(pid, 0)` AND `workspaceFolders`, then confirm the connect.
- Verdict affordances in a diff editor, the dead-end chain: `DiffUserDataKeys.CONTEXT_ACTIONS` icons are
  unidentifiable among standard diff icons; TEXT buttons are impossible warning-free across 242→262
  (`displayTextInToolbar()` deprecated-for-removal on 262, its replacement absent on 242);
  `NOTIFICATION_PROVIDERS` renders only at the TOP and is re-created per viewer. Shipped surface is
  `addBottomComponent(editor, bar)` on the editors from `openFile(diffFile, true)`. Two Swing traps: the
  LAF IGNORES `JButton.setBackground()` (use the `"JButton.backgroundColor"`/`textColor`/`borderColor`
  client properties), and platform icons don't match the webview's Lucide glyphs. Bundled SVGs need a
  hardcoded stroke colour (the IDE's loader has no `currentColor` context) and the loader is STRICT XML —
  a literal `--` inside an SVG comment kills the parse and the icon silently renders as nothing.

## Build / toolchain / release
- **Kotlin block comments NEST.** A literal `/*` inside a KDoc (writing the glob `js/*.js` in prose) opens
  a comment that never closes; the compiler reports "Unclosed comment" at the file's LAST line. Say "files
  under js/" in comments; globs only in strings.
- Build JVM must be **Java 21** — Gradle 8.10.2 refuses above JDK 23 and recent PhpStorm's JBR is JDK 25.
  Run every `./gradlew` from `plugin/`. `runIde` uses the JBR inside the downloaded `phpstorm("2024.2.6")`
  dependency (JBR 21 `-jcef` with `libcef.so`), which is what makes the webview work.
- `org.gradle.configuration-cache=true` is ON: a task action (`doFirst`/`doLast`) reading a script-level
  `val` — or `project` — captures the whole build-script object and dies with "cannot serialize Gradle
  script object references". Evaluate into LOCALS in the configuration block. The failure names the task,
  not the capture, so it reads like a plugin bug.
- **`pluginVerification { ides { recommended() } }` can take the WHOLE project offline.** It resolves the
  Android Studio list from `jb.gg` → `teamcity.jetbrains.com` at CONFIGURATION time, so one unreachable
  host fails EVERY task after ~23s with a bare "Connection timed out" naming neither URL nor verifier.
  `--offline` does NOT skip it; a stored config-cache entry masks it until something evicts the cache, so
  it appears to strike at random. **`-PskipVerifierIdes` empties the list** so the rest builds through the
  outage; `verifyPlugin` REFUSES to run under it, and release.md says never to release with it. Same host
  chain can also fail on TLS (`SSLHandshakeException` for teamcity) while `curl` from the same machine is
  clean — the JVM truststore/SNI is what's failing, so "the network is fine" clears nothing.
- **`./gradlew verifyPlugin` is MANDATORY on every release** (standing instruction 2026-08-23,
  `docs/release.md` step 3b). The Marketplace runs its own verifier on upload, but that verdict lands
  AFTER the version number is spent — it confirms, it does not gate. Do not reason about whether a diff
  "touched platform API" and skip; that judgement is exactly the one not to trust (0.9.0 shipped without
  it and passed only by luck confirmed afterwards). **Read the VERDICT FILES, never the log tail:**
  `build/reports/pluginVerifier/PS-*/plugins/<id>/<ver>/verification-verdict.txt`, one line each. It
  outlives a 2-minute timeout (~30-40s warm, ladder resolution can exceed it) — give it 10 minutes.
- **Marketplace approval lags the upload, and the API lags the page.** Minutes after a green upload run,
  `api/plugins/33274/updates` still names the previous version — it reads like a failed upload and is not
  one. Check the plugin PAGE (numeric id; the xmlId form 404s). A failed upload does not burn the version;
  only an accepted-then-rejected moderation does.
- **The zip in `build/distributions/` can be OLDER than your last edit — check the bytes, not the clock.**
  Extract the file you changed (`unzip -p <zip> …/lib/<jar>` then `unzip -p <jar> webview/chat.css`) and
  look for the change; `stat -c %y`, not `ls`, whose minute granularity hides the ordering.
- GitHub Actions: a `workflow_dispatch` trigger only appears once the workflow file is on the DEFAULT
  branch, so the first dry run can only happen after a push to main — and pushing it also arms the
  automatic upload for the very next `gh release create`.
- Do NOT re-add `testFramework(TestFrameworkType.Platform)` without platform tests: it registers a JUnit
  `LauncherSessionListener` that can't instantiate outside an IDE fixture and kills the test JVM.
- REAL-IDE compat: 2025.x+ moved JCEF into a bundled plugin (`com.intellij.modules.jcef`); without the
  optional `<depends>`, ChatPanel dies with `NoClassDefFoundError: JBCefBrowser` on 2026.2 while the
  2024.2 sandbox works fine. The sandbox CANNOT catch this class — smoke-test the zip in a real IDE.
- Verifier hygiene: `ReadAction.compute` AND Kotlin `runReadAction {}` are deprecated on 2026.1 —
  `Application.runReadAction(Computable)` is the one clean blocking-read API (wrapped as `readLocked {}`).
  `FileSaverDescriptor`'s vararg ctor is deprecated 2025.1+ with its replacement absent on 242, so
  reflection is the only warning-free both-ways route. Deprecation is per-IDE-version: the same zip shows
  MORE warnings on NEWER IDEs, never the reverse.
- **Marketplace updates may prompt an IDE restart — that is the IDE, not us.** The no-restart install can
  die on a download-cache race and fall back to install-on-restart. plugin.xml forces nothing; unloading
  live JCEF + the WS server + the claude process would rarely pass the GC check anyway. Accepted
  2026-08-19 — do not engineer around it.

## JCEF is not a browser (Linux)
- **JCEF sizes a text flex item's base a hair under its max-content**, so a two-word label wraps with the
  row half empty ("Very High" needed 55px, got a 53px base). Desktop Chrome on the SAME markup+CSS keeps
  one line — mockup.html and headless can NEVER reproduce this class, only the live panel over CDP.
  `white-space: nowrap` where a label must not wrap.
- **CSS layout claims must be measured on REAL JCEF, not headless.** Headless disagreed on rendered
  output (a flex child ellipsised where JCEF measured it whole), and two headless runs of the same page
  disagreed with each other. Headless is fine for click/class logic; for anything about WIDTH, ask the
  browser it ships in.
- Text fields: the Delete key INSERTS keyChar 0x7F as a tofu char instead of forward-deleting. chat.html
  carries a two-layer document-level workaround (manual forward-delete on keydown + capture-phase
  control-char strip on input), so any NEW input gets both free — don't add per-field key handling that
  swallows keydown first.
- **JCEF-OSR fabricates key events and can loop them — but ONLY on pre-2024.2.2 IDE builds.**
  **IJPL-161111** (dups JBR-7536/7547), fixed upstream in 2024.2.2 / 2024.3; every sighting was sandbox
  2024.2.0, since bumped to 2024.2.6. Two waveforms taped: keydown-only Enter at 1-3ms for minutes, and a
  keyup-storm of Ctrl/Enter cycles at ~2,000/s, plus characters wearing wrong physical codes (`t` with
  `code:'ArrowDown'`); 52 platform-only `invalid keyCode` stacks in `JBCefEventUtils.convertCefKeyEvent`
  are the IDE-side proof. Three defences were tried and all removed (an `e.code` guard — a REAL sandbox
  Enter also has `code:''`; a <30ms cadence guard — killed real Enter; commit-on-keyup — the second
  waveform delivers keyups). If it EVER resurfaces on a post-fix build, re-instrument recording MODIFIER
  FLAGS (the one gap in the tape: the phantom fabricates `Control`, so a phantom Ctrl+Enter submit was
  never ruled out) and prefer a BUTTON as the primary commit path. Evidence kept in
  `_local/phantom-enter-tape-2026-08-23/`; the guards were never committed, so git history has nothing.
- EVERY webview JS keydown chord (F12, Ctrl+N, Ctrl+Alt+G) was dead here even with the composer focused.
  All three were REMOVED 2026-08-09 and the plugin binds no shortcuts — don't re-add one expecting it to
  work. OS file drags never reach the DOM either; drag-drop needs an AWT `DropTarget` feeding the page.
- DevTools: the JCEF context-menu route is DEAD (OSR) and Ctrl+Alt+D is a WM grab. Use Find Action →
  "Claude Brains: Open DevTools", or `http://localhost:9222`. The panel appears in `/json/list` only once
  the tool window has opened. **The `file:///jbcefbrowser/` prefix is NOT unique** — every JCEF page in the
  IDE shares it, and a fresh sandbox's "What's new" tab can sort first; `cdp.py` matches the panel's
  `<title>`. **A hand-set sandbox Registry port beats `-PjcefDebugPort`**, and a REAL IDE may serve 9222
  with its own identically-titled panel: verify BY CONTENT (turn count, distinctive text), never by port.
- **JCEF OSR cannot produce stills under an emulated viewport** — `setDeviceMetricsOverride` applies but
  `captureScreenshot` returns the OSR surface with multiple paints tiled into it. Pixel-exact captures
  come from the spliced chat.html in headless Chrome instead, with animations frozen by injected CSS.
- A desktop-launched IDE has the GUI session's environment, NOT the shell's — nvm/pyenv PATH layers are
  missing, so CLI-spawned `npx` MCP servers die under the IDE while identical config works in a terminal.
  The platform's `EnvironmentUtil.getEnvironmentMap()` loads shell env ONLY on macOS
  (`shouldLoadShellEnv()` opens `if (!isMac) return false`, 242 AND 262) — an API existing is not the API
  working. Linux is covered by our own `ShellEnv`; Windows needs neither.
- Sandbox startup noise, NOT ours: `GlobalMenuLinux <clinit> requests Experiments instance …` is the
  2024.2 platform's own class-init assertion, no plugin code in the stack, fires at every launch.
- **The runIde sandbox INVENTS UI symptoms, it doesn't only hide them.** Fresh config, stock keymap, so
  IDE-level key handling differs from a real install: a defect reproducing ONLY there is suspect —
  reproduce interaction bugs against the installed IDE. Matching CDP limit: synthetic events go straight
  into the page and NEVER pass through IntelliJ's key dispatcher, so a clean CDP result says nothing about
  the IDE stealing focus on a real keypress.

## Webview / CSS / layout
- **`.turn-body`'s paint containment eats anything drawn OUTWARD, and it has bitten twice** (card menu as
  a clipped sliver; the in-flight ring as a cut half-rectangle — ring left edge x=8 against a turn box
  starting at x=14). Containment blocks HIT-TESTING too, so clipped elements still pass querySelector and
  synthetic-click assertions — a harness BLIND SPOT, not a bug; pin in-turn overlays with `elementFromPoint`
  at their centre. Escape hatch is `.turn-body:has(…){content-visibility:visible}`, but **weigh the lift**:
  it costs the live turn a real rendering property and lets a leaked state keep an old turn uncontained AND
  painting. Prefer "draw inside your own box" (the in-flight signal became an opacity-only fade); lift only
  when the thing genuinely cannot be expressed inside the box, as a popup cannot.
- **A scrollbar is painted at the bottom of its container's PADDING box**, so the element carrying the
  padding must ALSO be the one that scrolls or the bar floats mid-box. And `overflow: hidden` from
  `.fold:not(.open)` crops only the element it lands on — give `foldBlock` the scroll box itself and
  "crops folded, scrolls expanded" costs nothing.
- **`position: sticky` is clamped to its containing block, and that beats your offsets.** "Padding on the
  parent + equal negative margin on the child" does NOT work — the child cannot move left of the parent's
  CONTENT box, gets shoved right by exactly the padding, and eats the flex gap after it. Put the padding on
  the sticky element. Sticky also cannot rescue a `flex: 0 0 100%` item (no slack to shift, and
  `100% + margin` is wider than the container → phantom scroll). Anything that must not scroll belongs
  outside the scroll container.
- **In a flex column the margin is NOT the gap, and neither is a collapsed margin in a block.** `.turn-body`
  is flex with `gap`, so a child's spacing is **gap + margin-top** (flex margins never collapse); `.card`,
  `.compact` and `.think` are ordinary blocks where adjacent margins **collapse**, so two 8px margins make
  one 8px gap and changing only one moves nothing until it becomes the larger. A block-parented element must
  NOT join the flex family's `calc(attach - block)` rule. Always MEASURE previous-sibling-bottom to
  this-element-top; never read the margin and believe it.
- `box-sizing: border-box` is global, so padding counts against `min-width` — floors on padded boxes must
  be written as content + padding. `flex-wrap: wrap` + a content-sized child puts the child on its own line.
  A column flex container STRETCHES its children (`align-items: flex-start` is what makes an image size to
  itself). Making a row `display: flex` lets it outgrow its container — `max-width: 100%` + `min-width: 0`
  points the shrink at the intended child.
- `.popup`'s base `min-width: 330px` silently beats a smaller `width` on a variant — pair every narrower
  popup with `min-width: 0`, and MEASURE. Same family as the UA `[hidden]` rule losing to
  `display: inline-flex`: **assert COMPUTED style, never the declaration you wrote.** Any hidden-toggled
  element whose ID rule sets `display` needs its own `[hidden]{display:none}` re-assert, or an empty element
  keeps painting its margins.
- **The global `.turn :focus-visible { outline: … }` out-specifies an element-level `outline: none`** (two
  classes beat class+element) — suppressing a ring inside the turn area needs ≥3-class specificity, and the
  container takes the ring via `:focus-within`.
- **An absolutely-positioned child needs ITS card as containing block** — `.plan-add` drifted to the top of
  the log because `.card.plan { position: relative }` was missing, and the mockup hid it behind an inline
  style. Positioning bugs need the real panel.
- **`color-mix(…)` computes to `color(srgb r g b / a)`, not `rgba(…)`** — an alpha assertion built on an
  rgba regex matched nothing, defaulted its fallback, and PASSED on the build it was meant to fail. Parse
  both forms and return the raw string on no-match so the assert fails loudly.
- **`#fade-top` sits above ordinary content but below the sticky `.msg-user`**, so any NEW kind of first
  block lands under a 48px wash and is unreadable. Now switched off at `scrollTop <= 1`. **Second bite:**
  the toggle lived only in the scroll handler, so a FRESH conversation (no scroll event ever) never hid it.
  A greyed line with a horizontal band through it at the top of the log = the fade, not a style.
- **`textContent = …` deletes sibling controls** — three surfaces hold [icon, text] (context chip, header
  title, queue row). Write the text node / `.t-txt`, never the parent.
- **A bubbling document listener cannot see a click on any control that `stopPropagation`s** — the rename
  outside-click dismissal failed on `#histBtn`, the chips and the effort dots, i.e. the likeliest next
  click. Capture phase runs before any target handler. Check what stops propagation before adding a
  dismissal to the shared handler.
- **`sel` is a keyboard CURSOR, not a hover style, and the painter has no exit counterpart.** A
  document-level `mouseover` moves it onto any hovered `.popup-item` and never clears it — right for a
  picker, wrong for a status readout. Non-actionable popups opt out with `nosel`. **A CSS opt-out that only
  neutralises `:hover` will not save you:** `.popup-item.bg-row` ties with `.popup-item.sel` on specificity
  and loses on declaration order, so the JS class repaints anyway (symptom: dark while hovering, lighter
  after).
- **Rendering a list and OPENING it are different acts** — `renderHistory` ended with an unconditional
  `show`, so a re-push after a rename popped the panel open. Guard with an explicit "wanted" flag.
- **`Range.surroundContents` splits the text node, and unwrapping leaves it split** — a second selection
  over the same text then spans multiple nodes and throws. Unwrap must `parent.normalize()`.
- **`splice(NaN, 1)` deletes element 0** — a shared `.c-x` handler read `+dataset.i`, and the composer's ✕
  has none. Scope collection-row handlers to `:not(.compose)` whenever one class serves two roles.
- **`onUserEvent` has TWO early returns, and the common tools go out through them** (`LIM.resultSkip`,
  `isInternalResult`). Anything that must happen for EVERY result goes above them or silently never runs
  for the most-used tools.
- **A live-only state must be set at the LIVE site, never in the shared builder** — `toolLine()` serves the
  live path, replay AND `__gallery()`, so a class added inside makes a resumed conversation pulse forever.
- **Any forever-running state needs a sweep at turn end, not just a clear on the happy path.** An
  interrupt, an API error or `__exit` leaves a `tool_use` with no `tool_result`. `setBusy(false)` is the
  chokepoint. This family has bitten three times; assume a fourth.
- **The webview is a VIEW — anything pushed once at startup is gone after a page reload.** `onLoadEnd`
  fires on EVERY load while the seeding sat behind a `started` guard, so a reload left the DOM at markup
  defaults with a live CLI attached. `seedUi()` now runs per load. What makes it permanent rather than
  transient is a change-detector on the PUSH side (`lastTitle` records what was last SENT, so after a
  reload the name has not "changed"): any such cache must be cleared when the view resets — it tracks
  intent, and `pushEvent` is fire-and-forget.
- **Push-driven surfaces lag disk-driven ones by a whole turn** — the header title is pushed, the history
  list re-reads disk on open. Ask of any panel value: what refreshes it, and what is the longest gap
  between two of those events?
- `RenderLimits.DESC_KEYS` is a GLOBAL chain — collision-check any new key or it hijacks other tools'
  lines. `todos`/`plan` stay OUT. Deliberately blank: Bash, AskUserQuestion, ExitPlanMode, TodoWrite.
- The cut-marker is a SIBLING of `.io-v`, never a child (`foldBlock` would fold it away): the fold hides
  content it still holds, the marker reports content that is GONE. Replayed ask cards are `.ask-done`,
  which must NOT be `.done` (whose 22px dot-column indent shifts the whole card).
- **The mockup links `chat.css` with no cache-buster**, so a `?v=N` on the HTML re-fetches the page and
  serves the STALE stylesheet — a CSS fix measured that way reads as "no effect".

## Testing, probes and sandboxes
- **`pkill`/`pgrep -f <pattern>` matches the shell that runs it** when the pattern appears in your own
  command line — including in a later `&&` branch. Symptoms: exit 144 with everything after the kill
  silently skipped, or "still running" three times running because the probe saw itself. Use a
  self-escaping pattern (`pkill -f 'run[I]de'`), never combine the kill and a relaunch naming the same
  string in one call, and read `ps -o cmd -p <pid>` before believing a match.
- **`pkill -f runIde` kills GRADLE, never the sandbox IDE.** The orphan then swallows every relaunch
  through single-instance forwarding — `runIde` prints BUILD SUCCESSFUL in seconds and no window appears.
  Kill by pid: `pgrep -f 'idea.system.pat[h]'`. (On Windows: `taskkill /PID <pid>` **without** `/F`, so
  the sandbox saves its window layout — a forced kill loses it and the tool window comes back CLOSED,
  which means no CEF and no CDP target.) A surviving IDE also holds the sandbox jar mapped, so the next
  `runIde` dies in `prepareSandbox` — while `cdp.py` cheerfully attaches to the OLD panel. **Before
  trusting any measurement after a restart, confirm BY CONTENT which build is running.**
- **A sandbox launched BEFORE a CSS/JS edit is a free pre-fix build** — resources are spliced at page
  load, so the running panel still serves the old bytes. Run a new fixture's negative control against it
  before asking for the restart. Only works for resource changes; Kotlin needs a rebuild.
- **`./gradlew runIde --args="<projectPath>"` opens the sandbox straight onto a project** — it is a plain
  JavaExec task, so no GUI clicking to get a bridge and a panel.
- **The running IDE's own MCP bridge is a remote debugger for platform-API questions** — read port +
  `authToken` from `~/.claude/ide/<port>.lock`, connect (subprotocol `mcp`), `initialize`, `tools/call`.
  That reaches `IdeTools` inside a LIVE IDE with no GUI automation and no rebuild.
- **Every negative control must RUN, against the build that actually LACKS the fix** — not "current minus
  my commits". Fixture 49's control ran against HEAD-minus-this-session, which still contained the fix
  from the session before, so every discriminating assertion passed and the fixture read as vacuous. Find
  the commit that introduced the behaviour and check out its parent (`git checkout <fix>~1 -- <file>`).
- **A "discriminator" that is a substring of the thing it tests passes for the wrong reason** — fixture
  52's `review → /code-review` was green pre-fix because `review` is IN `code-review`. Ask what the OLD
  code returns for that exact input; pick inputs the old path cannot reach.
- **An assertion that calls a function by name aborts the whole harness run on a build without it** — a
  ReferenceError becomes an AssertionError that kills the process, and the control reports nothing for
  every remaining assertion. Assert through a path BOTH builds have (the rendered attribute, the DOM).
- **A computed-style assertion on a TRANSITIONED property is a coin flip unless it outwaits the
  transition** — the same fixture failed in step 1 on one run and step 3 on the next with no code change.
  The harness evals with `awaitPromise`: return `new Promise(r => setTimeout(() => r(<read>), 250))`.
- **A class assertion is not a CSS assertion** — `classList.contains` passes even when the stylesheet
  never attached. Assert the resolved `getComputedStyle(el, '::before').animationName` too, and prove the
  pair discriminates by injecting `animation: none !important`.
- **A fixture that feeds only wire frames does NOT reproduce the panel's DOM** — nothing on the wire
  creates a `.turn-body` (the panel makes one in `addUserMessage → newTurn()`), so fixtures build blocks
  bare in `#log`, outside the containment and stacking context real blocks live in. Fixture 45 went 31/31
  green while the clipping was plainly visible. Use the per-step `"setup"` hook. **Ask of any green CSS
  assertion: is the element in the same ANCESTRY it has in production?**
- **A probe page that omits the real ANCESTOR CHAIN measures a different cascade** — measuring `.ef-label`
  in a minimal page gave 7px where the real panel had 4px, because `#inputbar svg {18px}` is an ID rule
  beating both declared sizes. Reproduce the ancestors or measure in the panel, and prefer a property the
  winning rule does not set (the fix works because `flex-basis` is untouched — no specificity fight).
- **Harness fixtures leave their state in the LIVE panel** — fixture 46 ends on an empty
  `commands_changed`, so the real slash menu is empty afterwards and looks like a regression. Reload
  (seedUi replays `lastInitMeta`) or hit ↻ before eyeballing the panel after a run.
- **Live-harness asserts that mutate state must be their own STEP** — adding a commit-and-cancel assert
  mid-step consumed the composer later asserts needed and produced 11 unrelated failures. `setup` can
  `__clear` first.
- **Background-task bugs are ORDERING-dependent, so one repro is not enough** — a short-lived process
  (roster empties BEFORE the turn's `result`) and a long-lived one gave opposite symptoms, each masking
  the other. Vary the task's DURATION relative to the turn.
- **A payload that is NEVER persisted has a third measurement lane**: `strings -n 8` on the CLI binary
  (`~/.local/share/claude/versions/<ver>`) and on the installed VS Code `extension.js`. That lane read
  7.4's payloads, 9.1's retry enum, 10.1's allowlist and 10.5's verdict consumers verbatim. **Old
  reference versions are one URL away** even after both dirs have dropped them — the marketplace
  vspackage endpoint (URL in runbook.md) serves any version as a gzip-WRAPPED vsix (gunzip before unzip),
  carrying `resources/native-binary/claude`.
- **Reproducing a live-path bug without the IDE**: spawn `claude` with ClaudeCli's own flags, keep stdin
  OPEN so the session outlives the turn (the interesting frames arrive after it), record stdout with
  timestamps, then replay those real lines into `window.onClaudeEvent` on the spliced chat.html. Real
  frames + real renderer, no IDE, and the capture doubles as fixture provenance.
- **`tools/cdp.py` can both ACT and OBSERVE** — set `#input`'s value and call `submit()` to send a real
  turn, having first wrapped `window.onClaudeEvent` to timestamp every frame alongside the state you care
  about. Assert on a NUMBER, not a screenshot ("0 content deltas rendered while the button read Send").
  Neutralise the recorder afterwards or it accumulates for the session.
- **The spliced-chat harness** (standing lane for webview JS fixes): splice `chat.css` at `<!--CSS-->` and
  `window.LIMITS` + a `window.__bridge` stub at `<!--LIMITS-->`, feed events through
  `window.onClaudeEvent`, assert via `document.title` under headless `--dump-dom`. **Assert on `#log`,
  never `document.body`** — body.textContent includes chat.html's OWN script source, which contains the
  very literals under test. `window.LIMITS` must be spliced (the webview THROWS unspliced);
  `RenderLimitsTest` fails the build on hardcoded literals, marker count ≠ 1, or unbalanced script tags.
- **Headless Chrome is a DIFFERENT browser**: don't copy `mockup.html` elsewhere to probe (its relative
  stylesheet link silently resolves to nothing — and over `file://` Chrome REFUSES a stylesheet not ending
  `.css`, so a `chat.css.bak` control measured an UNSTYLED page and reported perfect alignment). **Any
  before/after CSS measurement must first prove the sheet LOADED** by reading back a value only the real
  sheet sets. Also: no compositor → rAF fires ~2.5/s (stub it onto `setTimeout`), and `ResizeObserver`
  exists but never reliably fires.
- **`window.__gallery()` maps the whole panel's spacing language in one pass** — one sweep over `#log *`
  comparing each element to its previous sibling. For a negative control, inject `git show HEAD:` of the
  stylesheet as a trailing `<style>` in the SAME live page: equal specificity plus later source order means
  the old rules win, no rebuild or restart needed.
- **A popup force-shown with `classList.add('show')` skips `tg()`'s positioning** — open menus through
  their real chip handlers. `.popup` width is max-content with only a MIN-width, so a menu that fits the
  real panel overflows a screenshot viewport; clamp for captures.
- **A wire-probe with a bare blocking `readline()` outlives its deadline forever** — the CLI keeps stdout
  open after `result`, so the loop never re-checks the clock. Wrap every probe in `timeout N`. Same for
  grep-based watchers on a probe TAPE: the tape wraps frames as JSON strings, so the file holds escaped
  `\"type\": \"result\"` and an unescaped grep loops forever. Three orphaned waiters in one week, all
  caught by the background chip.
- **Testing anything "before Claude reads/writes" in runIde: PhpStorm saves on frame deactivation.**
  Alt-tabbing out to report back writes every dirty buffer, so disk and editor agree before the tool runs
  and the test proves nothing. Stay inside the sandbox, or untick "Save files when switching to a
  different application". Also PIN THE TOOL in the prompt — the model reaches for `Bash cat` over `Read`,
  and no PreToolUse file matcher can see a Bash command.
- **Something outside the session mangles `.claude/skills/context/SKILL.md` line 1** — twice on
  2026-08-24 the opening `---` gained stray backticks (`` ``--- ``, then `-``--`), which silently
  kills the YAML frontmatter, the skill's description AND its auto-triggers ("catch me up" etc.).
  Culprit unfound (editor plugin / sync suspected). After any external edit to a skill file, check
  `head -1` is exactly `---` — the failure is silent and the skill listing lags one read behind.
- **Patching a file that contains `\uXXXX` escapes must go through the Edit tool** — a python heredoc's
  string literal decodes the escapes, so `str.replace` silently misses the file's literal text and two
  patch rounds "apply" cleanly while changing nothing.
- **Two machines, and only the repo travels.** Any note naming a home dir, transcript folder, CLI location
  or the sibling test repo is machine-scoped — check which box you are on. The sharp edge is fixtures that
  live OUTSIDE the repo (the 3.1 `dummy-cmd.md` is not in git and is absent on whichever machine did not
  create it, while the context files cheerfully say it exists). Verify the file, don't read about it.
