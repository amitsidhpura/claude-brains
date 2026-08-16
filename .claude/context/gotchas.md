# Gotchas — hard-won, don't rediscover

## Protocol / wire
- **The control-response parser WHITELISTS fields; everything else dies silently.** The allow
  response admits `behavior`/`updatedInput`/`updatedPermissions` (deny adds `message`) — a
  `feedback` field probed on 2.1.233 reached the model in ZERO frames, no warning anywhere. The
  TUI's own dialogs carry `acceptFeedback` internally (pushed as an extra text block on the
  ExitPlanMode tool_result), but no wire field maps to it. If a capability exists in the TUI,
  check the response SCHEMA before assuming the protocol exposes it. Watch-item: if a `feedback`
  field ever lands in the schema, ClaudeCli can switch to the TUI's exact shape in two lines.
- **A user message written to stdin mid-turn is read at the NEXT model call, not "immediately".**
  One probe showed it folded into the model's very first Write; the user's real run showed it
  arriving AFTER implementation started. Both are true — it races the model call cycle. A race
  that measured green once is still a race; anything that must precede the model's next action
  has to ride the SAME message it will read (for ExitPlanMode: `updatedInput.plan`, echoed in
  the tool_result).
- **`set_permission_mode` sent right after a plan approval ALWAYS loses.** The CLI restores
  `prePlanMode` when the approved ExitPlanMode executes — after your request was already
  processed — so the restore overwrites it, deterministically (chip ended Auto after picking
  auto-edit). Park the wish and send it on the post-approval `permissionMode` broadcast, which
  always fires (plan→X is always a change).
- **The plan-exit restore broadcasts the LITERAL `default`, not `manual`.** `default` is the
  CLI's internal name for the mode `manual` merely advertises. A mode UI keyed on advertised
  names drops the broadcast and lies (chip stuck on Plan — user report 2026-08-16). Alias them.
- **A message the CLI consumed mid-turn persists ONLY as an `attachment` record**
  ({type:"queued_command", commandMode:"prompt"}) — no user record ever follows, so replay that
  ignores attachments silently loses text the model acted on. One QUEUED to the next turn writes
  BOTH records (measured 3 vs 2 locally) — map attachments to bubbles only when no user record
  matches the text, or queued messages render twice. mode=task-notification is machinery.
- **A custom command's wire marker is a DESCRIPTION SUFFIX, and the wrapper it persists has two
  shapes.** The roster entry schema (`{name, description, argumentHint, aliases?}`) has no type
  field, but every custom entry's description ends " (project)"/" (user)" (measured 2.1.228,
  zero false positives across 107 built-ins). On disk, a built-in command turn persists
  name→message→args while an ARG-LESS custom one persists message→name with NO
  `<command-args>` tag — any single ordered regex over the wrapper misses one shape (it leaked
  raw XML into a session title the day custom commands became sendable). Match name and args
  independently.
- **The CLI watches the PROJECT commands dir; it does NOT watch `~/.claude/commands`.** A file
  drop pushes `commands_changed` ≈2.5s later, a deletion ≈1s, with NO turn in between — but only
  for `<cwd>/.claude/commands`; user-level changes need `/reload-skills`. And the trap that got
  this wrong the first time: a probe that sends `/reload-skills` right after dropping the file
  lands inside the watcher's debounce window and conflates the two pushes — sequence wire probes
  with a QUIET WAIT (45s, no turns) before concluding an event "never fires on its own".
- **`sel` is a keyboard CURSOR, not a hover style, and the painter has no exit counterpart.** A
  document-level `mouseover` moves `sel` onto whatever `.popup-item` you hover in any shown popup
  and never clears it — correct for a picker (hover moves the cursor so Enter acts on what you
  point at), wrong for a status readout, where it sticks to the last row the pointer crossed. A
  popup whose rows are not actionable must opt out (`nosel` on the popup, honoured at the open-seed,
  arrow/Enter and hover sites). **And a CSS opt-out that only neutralises `:hover` will not save
  you:** `.popup-item.bg-row` ties with `.popup-item.sel` on specificity and loses on declaration
  order, so the JS-applied class repaints the row anyway. The symptom reads as two different
  colours — dark while hovering (the opt-out winning against `:hover`), lighter after (the cursor
  winning on mouseout).
- **`background_tasks_changed` is a PER-PROCESS level signal, so the roster must be reset by the
  CLI's lifetime and by nothing else.** The CLI's own schema: *"nothing is emitted at startup, so
  consumers must reset to the empty set whenever the session's CLI process (re)starts and let the
  next membership change repopulate it."* It fires only when membership CHANGES — so any reset the
  panel does on its own initiative (e.g. per turn) leaves the roster wrong until the next start or
  exit, with nothing coming to correct it. A background shell outlives its turn by definition.
- **A background chip that looks stale may simply be right.** A user report of "2 tasks but nothing
  is running" turned out to be two genuinely-live orphaned shells — `until <cond>; do sleep 30;
  done` waiters whose condition never became true survive the entire session, invisible except
  through that chip. **Check the process tree** (`ps --ppid <the claude pid>`) before touching the
  roster code; the answer changes which bug you are looking at.
- **An `assistant` frame is emitted PER CONTENT BLOCK, not per message** (taped live 2026-08-15:
  `message_start → content_block_start → assistant → content_block_stop`, repeating, with the
  frames straddling `message_stop` in BOTH directions). So a message that thinks first sends two
  assistant frames with different uuids. Any per-message state consumed by "the assistant frame"
  is therefore consumed too early — that is what double-rendered every message after the first.
  Whole-message state belongs at TURN boundaries, not message ones.
- **A local built-in's output has THREE different spellings and none of them stream.** Live
  success = a bare whole-message `assistant` frame with ZERO stream events. Live failure = a
  `user` frame whose `message.content` is a **STRING** carrying
  `<local-command-stderr>…</local-command-stderr>` (an `!Array.isArray(content)` guard drops it
  before any rendering runs). On disk = `system/local_command`, or the same wrappers on a plain
  `user` record. Handle all of them or a command reports success while showing nothing.
- **An async sub-agent's `tool_result` is a LAUNCH ACK, not the end of its work.** Measured
  2026-08-13 (`-home-syncroze-Sites-claude-brains-testing/8a0b1939…`): an `Agent` tool_use at
  16:55:53.309Z got its tool_result 1.8s later reading *"Async agent launched successfully … The
  agent is working in the background."* — and the agent then ran for minutes. Anything keyed on "the
  result arrived" therefore fires almost immediately and is WRONG for agents: it turned three
  in-flight dots green while all three were still working. The launch ack is exactly what
  `isInternalResult()` already recognises (that is why it draws no OUT box), so use that same rule.
  What actually finishes a sub-agent is the task lifecycle — `task_notification` (has
  `tool_use_id`) or `task_updated` (**no** `tool_use_id`, so the tool must be stashed on the task
  when an earlier frame supplied it). Both are LIVE-ONLY: zero occurrences in any transcript, so
  they cannot be re-measured from disk.
- **"The agent failed" is not "the agent's WORK failed", and only the first is knowable.** The
  notification field is `<status>completed|failed|killed</status>` (verbatim from the 2.1.228 binary)
  — match those named states, not "anything != completed", since the binary also carries `running`
  and `pending` and painting a live line red is worse than leaving an unknown state green.
  It reports the AGENT: measured 2026-08-13, an agent explicitly asked to fail was still
  `<status>completed</status>`, because it did its job. The work's outcome lives one level down, and
  **whether it is recorded at all depends on how the agent ran the command** — session `ae4b9c80…`,
  the sandbox's sleep-chain guard blocked `sleep 30 && exit 1` in the foreground, the agent re-ran it
  via `run_in_background`, and nobody captured the exit status: four clean tool results, no error,
  while its own text said "TASK 3: FAILED". The same task ran in the foreground in an earlier session
  and DID produce an errored result. Any transcript scan is therefore red or green for identical work
  depending on a sandbox guard — which is why the sub-task dot was built and removed. If revisited,
  the only signal surviving both paths is the summary prose.
- **`is_error` on a tool_result does NOT mean the work failed — check for the `<tool_use_error>`
  wrapper.** A result wrapped in `<tool_use_error>…</tool_use_error>` is the harness REFUSING the
  call (a blocked compound command, an invalid input); the tool never ran, and an agent typically
  adapts and carries on. An unwrapped error is the command running and failing (`Exit code 1`).
  Measured 2026-08-13 after all three of a user's dummy agents came out red: each had its first call
  refused with `<tool_use_error>Blocked: sleep 30 followed by: echo …`, and only the one that ALSO
  had a plain `Exit code 1` had actually failed. Every errored result across the machine's sub-agent
  transcripts split the same way (3 wrapped, all `Blocked:`; 2 unwrapped, both `Exit code 1`).
  Read the wrapper through BOTH content shapes — a result is a bare string on one path and an array
  of text blocks on the other.
- **An async sub-agent's child events never reach the parent's wire.** Measured 2026-08-13 (session
  `96ebe694…`): the parent transcript has ZERO records carrying `parent_tool_use_id`, only
  `isSidechain`. The children live in `subagents/agent-<id>.jsonl`, which the notification's
  `<output-file>` is a SYMLINK to — so that field, not a path guess, is the handle if the sub-agent's
  own tool results are ever needed (e.g. to tell whether its WORK failed, which no wire field says).
  The synchronous-sub-agent probe that recorded `parent_tool_use_id` as existing does not generalise
  to backgrounded ones.
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
  spellings on both paths; trust timestamps over file positions. Extreme worked example
  (2026-08-15): a local built-in's output (`/context`) is a bare whole-message `assistant`
  frame with ZERO stream_events on the wire, but persists as `system/local_command` with a
  `<local-command-stdout>` wrapper — NEITHER path shares a spelling with the other, so both
  renderers were blind through two different mechanisms.
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
- **The transcript does not exist yet at `system/init`.** Measured against CLI 2.1.229
  (`_local/title_timing.py`, 2026-08-13): the file is MISSING when `system/init` arrives and is on
  disk by `message_start` — 15 KB already, first user record included. So `message_start` is the
  earliest frame anything derived FROM THE FILE (a title, a token count) can be read at, and the
  session id arriving is NOT evidence the file is there. `SessionStore.titleOf` returning null is
  the normal state for the first few seconds of a session, not an error.

- **The command roster is exactly `{name, description, argumentHint, aliases?}` — there is no
  `immediate` flag on the wire even though the CLI binary carries one.** Measured 2026-08-17 by
  sending a bare `initialize` control request to CLI 2.1.233 with the panel's own flags (51
  entries, keys enumerated across all of them). So "does this command act on click" can only be
  derived from `argumentHint`; there is no authoritative signal to read instead.
- **A hint read out of the binary is NOT the hint the wire sends.** `strings` shows TWO records
  for `/goal`, one carrying `argumentHint:"[<condition> | clear]"` and `immediate:!0`; the roster
  sends `/goal` an EMPTY hint. Only one record reaches the wire and the binary does not say which.
  Probe the live roster before believing a hint — this wrongly put /goal on the insert list and
  wrongly left /code-review, /simplify, /loop and /batch off it (2026-08-17). Recipe: spawn the
  CLI with the panel's flags, write one `{"type":"control_request","request":{"subtype":
  "initialize"}}` line, read until the `control_response`. No user turn, nothing persisted.

## Build / toolchain
- **A cold configuration cache turns `runIde` into a NETWORK build, and it can fail on TLS.**
  `build.gradle.kts` resolves the verifier's IDE ladder through
  `ProductReleasesValueSource`, which hits `teamcity.jetbrains.com`. While a config-cache entry
  exists this never runs, so `runIde` looks offline — but the moment the entry is evicted (running a
  different task graph, e.g. `test`, is enough) the next `runIde` recomputes it and can die with
  `SSLHandshakeException: No subject alternative DNS name matching teamcity.jetbrains.com`.
  Observed 2026-08-13 with `curl -I https://teamcity.jetbrains.com` returning a clean 401 from the
  same machine at the same moment — so "the network is fine" does not clear the JVM, whose truststore
  or SNI path is what is actually failing. `--offline` does NOT help (the value source still runs).
  **`./gradlew runIde -PskipVerifierIdes` does** — that flag is what skips the ladder resolution.
  Reach for it the instant a build fails on a jetbrains.com host for a task that has no business
  downloading anything.
- **Two machines, and only the repo travels.** Development happens on both a Linux box and a
  Windows one (paths in overview.md), so ANY note naming a home dir, a transcript folder, a CLI
  location or the sibling test repo is machine-scoped — check which box you are on before trusting
  it. Two consecutive `/context load`s opened with the recorded machine being the wrong one. The
  sharp edge is test fixtures that live OUTSIDE the repo: the 3.1 `dummy-cmd.md` sits in the
  sibling test repo, is not in git, and is therefore absent on whichever machine did not create it
  — while the context files cheerfully say it exists. Verify the file, don't read about it.
- **The zip in `build/distributions/` can be OLDER than your last edit — check the bytes, not the
  clock.** An edit after `buildPlugin` leaves a stale artifact that every downstream step (verify,
  release, upload) then treats as current. Settle it by extracting the file you changed
  (`unzip -p <zip> claude-brains/lib/<jar>` then `unzip -p <jar> webview/chat.css`) and looking for
  the change, THEN by exact mtimes (`stat -c %y`, not `ls`, whose minute granularity hides the
  ordering). Useful side effect found 2026-08-13: `verifyPlugin` depends on `buildPlugin`, so it
  refreshes the zip — the bytes it verified were the bytes that shipped, but only by luck of
  ordering. Rebuild before a release rather than relying on that.
- **Killing `./gradlew runIde` does NOT kill the sandbox IDE, and the survivor will fake a test
  result.** The IDE is a forked JVM: kill the Gradle process and it keeps running, holding
  `build/idea-sandbox/PS-*/plugins/claude-brains/lib/*.jar` mapped, so the next `runIde` dies in
  `prepareSandbox` with "cannot be performed on a file with a user-mapped section open" — while
  `tools/cdp.py` cheerfully attaches to the OLD panel. On 2026-08-13 that nearly passed a negative
  control against the very build it was meant to refute; the build LOG is what caught it. Close it
  with `taskkill /PID <pid>` **without** `/F` (graceful, so the sandbox saves its window layout — a
  forced kill loses it and the tool window comes back CLOSED, which means no CEF and no CDP target
  at all). Find it with `Get-CimInstance Win32_Process` filtered on `*idea-sandbox*`. Before
  trusting any live measurement after a restart, confirm the build you think you are testing is the
  one that is running.
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
- **The verifier's per-IDE verdicts are FILES, not the tail of the log.** A `tail -N` on the
  backgrounded `verifyPlugin` output shows only the last IDE or two; the authoritative list is
  `plugin/build/reports/pluginVerifier/PS-*/plugins/<pluginId>/<version>/verification-verdict.txt`
  (one line each — "Compatible" / warnings / problems). Read those before declaring the ladder
  green. Also: an unquoted `?` in a curl URL is a zsh glob ("no matches found") — quote the
  Marketplace API URLs.
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
- **Harness fixtures leave their state in the LIVE panel.** Fixture 46 ends on an empty
  `commands_changed`, so after a harness run the real panel's slash menu is empty and stays so —
  it looks like a regression and is fixture residue. A webview reload (seedUi replays
  `lastInitMeta`) or the header ↻ restores it. Reload before eyeballing the panel after any
  harness run.
- **The CLI writes files with Bash far more often than you would guess, so a Write/Edit-only hook
  misses them.** Measured 2026-08-14: asked to create one file and overwrite another, the CLI used a
  single `Bash` call for both. Anything keyed on the file-writing TOOLS (Write/Edit/MultiEdit/
  NotebookEdit) therefore catches a fraction of real writes. Bash's input names no path, so there is
  nothing to derive — pair the per-file hook with a sweep at `result`.
- **`openFile` on the plugin's own MCP bridge is a free "what does the VFS actually know" probe.**
  It calls `findVFile` and refreshes nothing, so it answers `error: file not found` for a file that
  exists on disk but has not been refreshed into the VFS, and `opened:` once it has. Speak to the
  bridge the way the CLI does (lockfile `authToken` in header `x-claude-code-ide-authorization`,
  subprotocol `mcp`, `tools/call`). **Pick the right lockfile:** several IDEs may hold the same
  workspace open — filter on `ideName`, or you will hit VS Code's bridge, whose `openFile` takes a
  different argument schema and fails validation.
- **`getDiagnostics` needs the daemon to have run, so an empty result proves nothing on its own.**
  Checking a file straight after an out-of-band edit returned no diagnostics and then reported them
  correctly moments later — the document HAD reloaded, the analyzer just had not caught up. Before
  reading "no diagnostics" as "no change", confirm the probe can see a fault at all by opening a file
  that is already broken.
- **`.turn-body`'s paint containment eats anything drawn OUTWARD, and it has now bitten twice.** The
  card menu opened as a clipped sliver; the in-flight ring rendered as a "cut half-rectangle" squared
  off against the turn's left and top edges — the exact shape chat.css's focus-ring comment predicted
  years-of-comments earlier. Measured: at full expansion the ring's left edge is x=8 while
  `.turn-body` starts at x=14. Focus rings dodged it by going INSET; anything that must grow outward
  cannot, so lift containment instead — `.turn-body:has(...)`, the idiom already there for the card
  menu. **But weigh that lift before taking it:** it costs the live turn a real rendering property and
  lets a leaked state keep an old turn uncontained AND painting. The in-flight signal was rebuilt as
  an opacity-only fade instead — no geometry, nothing to clip, no lift — and the halo was dropped.
  Prefer "draw inside your own box" over "un-contain the ancestor"; reach for the lift only when the
  thing genuinely cannot be expressed inside the box (the card menu is a popup, so it cannot).
- **A fixture that feeds only wire frames does NOT reproduce the panel's DOM.** Nothing on the wire
  creates a `.turn-body` — the real panel makes one in `addUserMessage` -> `newTurn()` when the user
  SENDS. So every live-harness fixture builds its blocks bare in `#log`, outside the containment,
  stacking and `content-visibility` context real blocks live in. Fixture 45 went 31/31 green while
  the clipping was plainly visible in the panel, for exactly this reason. The harness now takes a
  per-step `"setup"` JS hook: use it to build the turn the way the panel does before sending frames.
  **Ask of any green CSS assertion: is the element in the same ANCESTRY it has in production?**
- **`onUserEvent` has TWO early returns, and the common tools go out through them.** `LIM.resultSkip`
  (Edit/Write/TodoWrite/Task*) and `isInternalResult` both `return` partway down the tool_result
  handler. Anything that must happen for EVERY result — clearing an in-flight state, a bridge call —
  goes above them or it silently never runs for the most-used tools of all. The `tasks` bridge call
  already carries this warning in a comment; the in-flight `.run` removal is the second case, and
  fixture 45 step 4 exists only to pin it.
- **A live-only state must be set at the LIVE site, never in the shared builder.** `toolLine()` is
  called by the live path, the replay builder AND `__gallery()` — a replayed or demo tool is already
  finished, so a class added inside the helper makes a resumed conversation pulse forever. Same shape
  for anything else live-only: put it where the wire frame is handled.
- **Any forever-running state needs a sweep at turn end, not just a clear on the happy path.** An
  interrupt, an API error or `__exit` ends a turn with a `tool_use` that never gets a `tool_result`,
  so the per-result clear never fires. `setBusy(false)` is the chokepoint (six callers, all real ends
  of turn; the one intermediate `result` for a pending background task returns before it, correctly).
  This family has now bitten three times: `.t-prog.run`, the hidden bg chip that pulsed forever
  (manual-test 7.3), and this. Assume it will bite a fourth.
- **A class assertion is not a CSS assertion.** `el.classList.contains(...)` passes even when the
  stylesheet never attached; assert the resolved `getComputedStyle(el, '::before').animationName`
  too. Prove the pair discriminates by injecting `animation: none !important` and confirming ONLY
  the animation assertions fail (26 pass / 5 fail for fixture 45, 2026-08-13).
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
- **Driving a real end-to-end test from outside the IDE**: `tools/cdp.py` can both ACT and OBSERVE.
  Set `#input`'s value and call `submit()` to send a real turn to the real CLI, having first
  wrapped `window.onClaudeEvent` to timestamp every frame alongside the state you care about
  (`busy`, `send.className`, chip text). That turns a "watch the panel and hope you catch it" item
  like manual-test 8.15 — where the interesting moment lands ~35s after the prompt — into a
  replayable timeline. Assert on a NUMBER, not a screenshot: "0 content deltas rendered while the
  button read Send" is what actually closed it. Neutralise the recorder afterwards (swap the array
  for a no-op `push`) or it accumulates for the rest of the session; a panel Refresh clears the
  wrapper.
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
  Check the top-of-log state whenever a new block type can be first. **Second bite 2026-08-16:**
  the `at-top` toggle lived only in the scroll handler + replay paths, so a FRESH conversation
  (no scroll event ever) never hid the fade and `/model`'s stdout `.blk` was washed out — it read
  as strikethrough in the screenshot. Now refreshed from `maybeScroll()` and `clearLogUI()`. A
  greyed line with a horizontal band through it at the top of the log = the fade, not a style.
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
  probe copies into `design/`. Same family, cost a false PASS on 2026-08-16: over `file://`
  Chrome REFUSES a stylesheet whose filename does not end `.css` (a `chat.css.bak` copy kept as a
  pre-fix control), and the unstyled page happily reported the two elements under test as
  perfectly aligned. Any before/after CSS measurement must first prove the sheet LOADED — read
  back a value only the real sheet sets. (2) no compositor → rAF fires ~2.5/s, so rAF-driven animation
  reads frozen — stub `requestAnimationFrame` onto `setTimeout`. (3) `ResizeObserver` exists but
  never reliably fires — give RO-synced code a second trigger and test that. (Also docs/limits.md.)
- **A probe page that omits the real ANCESTOR CHAIN measures a different cascade.** Linking
  chat.css into a minimal page and measuring `.ef-label` gave a 7px misalignment; the real panel
  had 4px, because the popup lives inside `#inputbar` and `#inputbar svg { width:18px; height:18px }`
  is an ID rule that beats both `.ef-label svg {15px}` and `.pi-ic svg {17px}` — those declared
  sizes are DEAD in the composer and every icon there draws at 18px. Reproduce the ancestors (or
  measure in the panel) before quoting a number, and prefer a property the winning rule does not
  set: the alignment fix works because `flex-basis` is untouched by `#inputbar svg`, so no
  specificity fight was needed (2026-08-17, found by fixture 51's control).
- **A negative control must run against the build that actually LACKS the fix — not "current
  minus my commits".** Fixture 49's control was run against HEAD-minus-this-session, which still
  contained the fade fix (it shipped in 0.7.1 the session before), so every DISCRIMINATING
  assertion passed and the fixture read as vacuous. Find the commit that introduced the behaviour
  and check out its parent (`git checkout <fix>~1 -- <file>`), then prove BY CONTENT which build
  the panel is serving before believing a single result (2026-08-16).
- **An assertion that calls a function by name aborts the whole harness run on a build that has
  not got it.** `cmdTakesArg(...)` in a fixture threw ReferenceError on the pre-fix build, and
  `Panel.eval` turns that into an AssertionError that kills the process — the control reported
  nothing at all for the remaining 18 assertions. Assert through a path BOTH builds have (the
  rendered attribute, the DOM) and the control can actually complete.
- **A computed-style assertion on a TRANSITIONED property is a coin flip unless it outwaits the
  transition.** `#fade-top` transitions opacity over .12s, so an immediate `getComputedStyle`
  after the class toggle returns the PRE-transition value: the same fixture failed in step 1 on
  one run and in step 3 on the next, with no code change between. The harness evals with
  `awaitPromise`, so return `new Promise(r => setTimeout(() => r(<read>), 250))` and the reading
  is deterministic (2026-08-16).
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
- **In a flex column the margin is NOT the gap, and neither is a collapsed margin in a block.** Two
  different arithmetics sit side by side in this file and both defeat a DevTools reading:
  `.turn-body` is `display:flex` with `gap: var(--block-gap)`, so a child's spacing is **gap +
  margin-top** (flex margins never collapse) — `.io` at `-6px` was really 12px; while `.card`,
  `.compact` and `.think` are ordinary blocks where adjacent margins **collapse**, so `.card-h`'s
  `margin-bottom` and `.card .blk`'s `margin-top` produce one 8px gap, not 16px, and changing only
  one of them moves nothing until it becomes the larger. Consequences, both live: a block-parented
  element must NOT join the flex family's `calc(attach - block)` rule (it would be pulled 10px INTO
  the line above), and both ends of a collapsing pair must name the same token. Always MEASURE the
  gap — previous sibling's `bottom` to this element's `top` — never read the margin and believe it.
- **Cross-checking spacing takes one probe, not a rebuild.** `window.__gallery()` draws every
  transient state, so one pass over `#log *` comparing each element to its previous sibling maps the
  panel's whole spacing language in a single run (2026-08-13: 29 pairs, which is how the 10px action
  rows and 0/2/3/6px list rhythms got separated from the attached family). For the negative control,
  inject `git show HEAD:` of the stylesheet as a trailing `<style>` in the SAME live page — equal
  specificity plus later source order means the old rules win, and no rebuild or restart is needed.
- **Push-driven surfaces lag disk-driven ones by a whole turn.** The header title is pushed by
  Kotlin; the history list re-reads disk every time it opens. Both go through the same `titleOf`,
  so they can only ever disagree about WHEN — and `pushTitle` had one live-turn caller, the
  `result` line, so a new conversation showed "New conversation" beside a titled `current` row for
  the length of its first turn (a real one ran an hour: 2026-08-13, docs/manual-test.md 8.16). Ask
  of any panel value: what refreshes it, and what is the longest gap between two of those events?
- **The webview is a VIEW — anything pushed once at startup is gone after a page reload.** Every
  `__mode`/`__project`/`__models`/`__commands`/`__title` frame used to be sent inside
  `startSession()` behind its `started` guard, while `onLoadEnd` fires on EVERY load: a reload left
  the DOM at its markup defaults with a live CLI attached and nothing to correct it (proved on the
  pre-fix build — after `location.reload()`, `slashCommands` 0 and `projectRoot` ""). Now `seedUi()`
  runs per load. The trap that makes it permanent rather than transient is a change-detector on the
  push side: `lastTitle` records what was last SENT, so after a reload the name has not "changed"
  and is never re-sent. Any such cache must be cleared when the view resets — it tracks intent, and
  `pushEvent` is a fire-and-forget `executeJavaScript` that guarantees nothing about delivery.

## Session tooling (probes, sandboxes, background shells)
- **`pkill -f <pattern>` from a harness background shell kills ITSELF when the pattern appears
  in the shell's own eval'd command line** (exit 144, and whatever came after the pkill never
  runs — a runIde relaunch silently didn't happen twice on 2026-08-16). Run the kill and the
  relaunch as SEPARATE tool calls, or pattern on something not quoted in your own command.
- **A wire-probe script with a bare blocking `readline()` outlives its deadline forever** — the
  CLI child keeps stdout open after `result`, so the loop never re-checks the clock. Two probes
  sat 40+ minutes as exactly the orphaned-waiter family the background chip caught on
  2026-08-15. Wrap every probe in `timeout N`; the chip is telling the truth.
- **A grep-based watcher on a probe TAPE can never fire if it greps for unescaped JSON**: the
  tape wraps each frame as a JSON string, so the file contains `\"type\": \"result\"` (escaped,
  and without the spaces you typed) — `until grep '"type": "result"'` loops forever. Third
  orphaned waiter of 2026-08-16, all reported by the chip. Grep the tape for a marker YOU wrote
  into it, or match the escaped form — and give watchers their own `timeout` too.
- **The sandbox's hand-set Registry value beats `-PjcefDebugPort`** (Registry user property >
  system property), so the sandbox came up on 9222 anyway — meanwhile the REAL IDE can also be
  serving 9222 with its own "Claude Brains — chat panel" target. Before driving any panel over
  CDP, verify identity BY CONTENT (turn count, distinctive conversation text), never by port.
- **JCEF OSR cannot produce stills under an emulated viewport.** `Emulation.setDeviceMetricsOverride`
  applies (innerWidth/dpr change correctly) but `Page.captureScreenshot` returns the OSR surface
  with MULTIPLE paints of the viewport tiled/stacked into it — clip+scale just tiles at 4x. Found
  2026-08-16 making marketplace screenshots. Pixel-exact panel captures come from the spliced
  chat.html in headless Chrome instead (real CSS + LIMITS captured off the live panel via cdp.py);
  drive states through the builders `__gallery()` uses, and freeze animations with injected CSS —
  headless catches them at arbitrary phase (shimmer text can render transparent).
- **A popup force-shown with `classList.add('show')` skips `tg()`'s positioning** and can render
  shifted or overflowing — open menus through their real chip handlers (`modeChip.click()`,
  `bgChip.click()`). And `.popup` width is max-content with only a MIN-width: a menu that fits the
  wide real panel (mode menu's long descriptions) overflows a 394px screenshot viewport; clamp its
  width for captures.
- **Patching a file that contains `\uXXXX` escapes must go through the Edit tool.** A python
  heredoc's string literal decodes backslash-u escapes (backslash-u-2014 becomes the em-dash
  character), so `str.replace` silently
  misses the file's literal backslash-u text — two patch rounds "applied" cleanly while changing
  nothing (2026-08-16). The Edit tool passes the text through undecoded.
