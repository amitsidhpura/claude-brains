# Decisions

Format: `## YYYY-MM-DD — <decision>`, newest first, with *why* and *alternatives rejected*.
Never delete entries; mark superseded ones.

## 2026-08-16 — Card text fields share one dress: `--warn-field` + the ask-input style
`.plan-fb` and `.ask-other input` both render transparent-frame-on-warn-card style with
`background: var(--warn-field)` (#201c1a, a sunken step below `--warn-bg`), and the plan card
gains `.plan-sep` — a full-bleed hairline (`margin: 10px -12px 0; border-top: 1px solid
var(--border)`, the composer `#inputbar` idiom) between plan body and decision surface. The
separator is a div of its own (inputs cannot carry pseudo-elements) and `done()` removes it with
the input so a decided card matches the replayed one (plan + footer only).
**Why:** the panel's two type-your-answer-on-a-card surfaces must read as one control (the same
argument `fillPath` made for paths), and the user reported the plan/field boundary was hard to
see. Final colour user-picked over three mockup iterations, per the mockup-first convention.
**Rejected en route:** `#1b1b1b` on `--panel` styling (first draft), fully transparent field
(matched ask exactly but the user wanted the well back) — both superseded the same day.

## 2026-08-16 — Approve-with-notes rides `updatedInput.plan`, not a steered message
The plan card's typed note, on ANY approve path, is appended to the approved plan under
`RenderLimits.PLAN_NOTES_MARKER` and sent via `updatedInput`; SessionStore parses it back out of
`toolUseResult.plan` for the replay footer.
**Why:** the ExitPlanMode tool_result echoes the approved plan, so the model reads the note in
the SAME message as the approval — before its first implementation call, deterministically. This
is timing-equivalent to the terminal's shift+tab (which pushes acceptFeedback as an extra text
block on that same tool_result — an internal path the control-response schema does not expose).
Bonus: the note is recorded durably in the plan file, which the terminal's version is not.
**Rejected:** a `feedback` field on the allow response (probed: schema-whitelisted away,
silently); stdin steering (probed working ONCE, then observed racing the model call cycle in the
user's manual test — the note arrived after implementation started); a queued user turn
(delivered only after the whole implementation turn — the original design error).

## 2026-08-16 — Plan-card mode rows park their switch until the CLI's restore broadcast
Picking "Approve, auto-edit"/"Approve, auto mode" does not bridge `set_permission_mode`
immediately; the wish parks in `pendingPlanMode` and is sent when the post-approval
`permissionMode` broadcast arrives (cleared on sendTurn/clearLogUI).
**Why:** the CLI restores `prePlanMode` when the approved ExitPlanMode EXECUTES — always after
our immediate request is processed, so the restore overwrote the user's pick every time
(measured live: chip ended Auto after choosing auto-edit). Plan→X is always a change, so the
broadcast always fires and the parked switch always releases — after the restore, before the
first implementation edit.
**Rejected:** immediate bridge (deterministic loss, not a race); sending at turn end (too late —
the implementation itself is what the user wanted covered).

## 2026-08-16 — The chip reads the broadcast `default` as Manual
`applyCliMode` aliases `default` → `manual` before the unknown-mode guard.
**Why:** measured — restoring a `manual` pre-plan mode broadcasts the LITERAL `default` (the
CLI's internal name; `manual` merely replaced it as the advertised one). The guard dropped it
and the chip stayed on Plan while the real mode was manual — the user's report, and the exact
lie the guard exists to prevent.

## 2026-08-16 — Mid-turn steered messages replay as user bubbles, deduped by text
SessionStore maps `attachment` records ({type:"queued_command", commandMode:"prompt"}) to user
blocks unless a plain user record in the same file carries the same text.
**Why:** measured across local transcripts — a message the CLI consumed MID-TURN persists ONLY
as the attachment record (3 cases), while one queued to the next turn persists as attachment AND
user record (2 cases). Without the mapping, replay silently lost text the model demonstrably
acted on; without the dedupe, queued messages would render twice. task-notification-mode
attachments are machinery and never render.

## 2026-08-15 — One path renderer for every surface, cards included
Both permission-card headers (live `renderPermission`, replay `fillAppliedCard`) now fill their
`<code>` through `fillPath()`, the same helper the tool line uses: project-relative, split into an
ellipsised `.p-head` and a fixed `.p-tail`, with the ABSOLUTE path on `dataset.path` + `title`.
**Why:** the decision surface and the timeline sat one above the other naming the same file two
different ways — the card spelled out `/home/syncroze/Sites/claude-brains/plugin/src/…` while the
tool line showed `plugin/src/…`. A second renderer is also a second thing to drift.
**Supersedes** the backlog note that card paths were deliberately unclamped "since that surface asks
you to approve a write to a specific file". That concern is met without the full string: the click
handler already reads `dataset.path` first, and `title` still reveals the whole path on hover —
so what you approve is unchanged, only what you read is shorter.
**Rejected:** a card-only shortener (the drift this is meant to remove), and clamping via CSS alone
(measured on the tool line already — flex shrinks proportionally, so any factor that saves the
filename on a long path nibbles the parent folder on a short one).

## 2026-08-15 — `msgStreamed` is a TURN-level fact, never cleared mid-turn
Set at `message_start`, cleared only at `result` / `sendTurn` / `clearLogUI`. The whole-message
`assistant` handler reads it and never writes it.
**Why:** the CLI emits an `assistant` frame per CONTENT BLOCK, not per message (taped live). A
message that thinks first therefore sends two, and clearing the flag on the first let the second
re-draw text the deltas had already rendered — every message after the first appeared twice in a
`/security-review` run, both copies carrying one uuid because `stampMessage` stamps all pending
blocks. A local command's turn never streams at all, which is exactly what leaves the flag false
and lets its whole message draw itself.
**Rejected:** clearing at `message_stop` (tried and reverted the same hour — assistant frames
straddle the stop in BOTH directions, so fixture 47's step-2 guard immediately caught it
double-rendering the other way); and a rendered-uuid set, which cannot help because the duplicate
carries the uuid of the frame that drew it.

## 2026-08-15 — A failed local command is surfaced, not swallowed
`onUserEvent` handles a STRING `content`: `<local-command-stderr>` → error block,
`<local-command-stdout>` → answer block, every other wrapper still invisible. Replay routes the
same two shapes on a `user` record through the same mapping.
**Why:** measured — `/security-review` in a repo with no `origin/HEAD` reports only through a
`user` frame whose content is a string, which the `!Array.isArray(content)` guard dropped before
any rendering logic ran. The panel showed a completed turn with nothing in it, and the CLI's own
reason ("fatal: ambiguous argument 'origin/HEAD...'") was lost.
**Rejected:** extending `cleanInjected`'s drop list, which was already wrong in both directions —
it swallowed the stdout spelling outright and let stderr through as raw XML in a blue user box.
Dropping an error blob violates the project's own no-silent-drops rule.

## 2026-08-15 — Custom commands are detected by the description SUFFIX, not a disk scan
`markCustom()` in chat.html parses `^([\s\S]*) \((project|user)\)$` off every roster entry's
description (initialize AND commands_changed), strips it for display, and records the source on
the entry + in the `customCmds` map; `cmdKind` returns 'text' for map hits and `mcp__`-prefixed
names, after the native/allowlist checks so a custom `clear.md` cannot shadow the IDE's /clear.
**Why:** the entry schema has no type field, but the suffix is a measured wire marker — present
for every `.claude/commands/**.md` and `.claude/skills/*/SKILL.md` entry in either base, absent
from all 107 built-ins across two captures (CLI 2.1.228). It makes the whole feature webview-only
and covers plugin-sourced commands for free.
**Rejected:** the PLAN-APPROVED Kotlin disk scan (`CustomCommands.scan` + a `rescanCommands`
bridge verb) — more moving parts, a rescan round-trip on every `commands_changed`, blind to
sources not on the two scanned paths, and it duplicates knowledge the CLI already sends. The
user approved the pivot mid-plan after the Phase-0 probes. Accepted risk: a future CLI respelling
of the suffix hides custom commands (fail-closed, one-line fix).

## 2026-08-15 — cleanInjected matches command name and args INDEPENDENTLY
`CMD_NAME_RE` + `CMD_ARGS_RE` replace the single name-then-args `CMD_RE` in SessionStore.
**Why:** two wrapper shapes exist on disk (both measured, 2.1.228): built-ins write
name→message→args, but an arg-less CUSTOM command writes message→name with NO `<command-args>`
tag — the one-pattern regex missed it and a session titled by such a turn showed raw XML. Found
by the verification screenshot the same day custom commands became sendable.
**Rejected:** ordering variants in one regex (there are already two orders; independent matching
is immune to a third).

## 2026-08-14 — The IDE is kept in step with the CLI's writes, per file and per turn
`CliFileSync` sits on the session's event stream (`ClaudeSessionService.startCli`, before the UI
callback) and does two things: pairs a write tool's `tool_use` with its `tool_result` and refreshes
that one path, then sweeps the project root once at every `result`. `refreshFromDisk` (Vfs.kt) does
an ASYNC refresh; when the path is unknown to the VFS — a newly CREATED file — it walks up to the
nearest directory the VFS does know, recursively if it had to climb past directories the CLI also
created, because the VFS only discovers a new child when its parent is refreshed.
**Why:** the plugin never writes files itself; the CLI does, out of band, and nothing refreshed
afterwards — an accepted edit needed "Reload from disk" and a created file never appeared in the
tree.
**Why two mechanisms, and this is the part that was measured rather than designed:** the per-file
half covers Write/Edit/MultiEdit/NotebookEdit, which is what the backlog scoped. Driving a real turn
showed the CLI answering "create one file and overwrite another" with a SINGLE `Bash` call — no
Write, no Edit — so the scoped fix caught nothing at all. Bash's input names no file, so there is
nothing to refresh from; the turn-end sweep is what covers it, at roughly the cost IntelliJ already
pays whenever the window regains focus.
**Rejected:** deriving paths from the Bash command (guesswork over an open-ended shell string);
sweeping per Bash call rather than per turn (same cost, many more times); refreshing at `tool_use`
(the file is not written yet, so it would read the file as it was); and keying off the permission
card, which a pre-approved tool never produces.
**Verified end to end in the sandbox, not just by unit test:** the plugin's own MCP bridge answers
`openFile`, which uses `findVFile` with no refresh, so it reports exactly what the VFS knows. A file
created behind the IDE's back reads `error: file not found`; after a real CLI turn the same probe
reads `opened:`. The edit half was proven with `getDiagnostics` on a JSON file open in the editor —
clean while valid, reporting the syntax errors after the CLI made it invalid out of band, i.e. the
open document really did reload.

## 2026-08-13 — A running tool's dot is white and breathing; the colour IS the verdict
`.tool-line.run { --dot-c: var(--fg) }` (declared before `.fail`), plus
`.tool-line.run::before, .think-live::before { animation: var(--pulse-name) var(--pulse-period) ease-in-out infinite }`
with `--pulse-name: bg-pulse`. `.run` is set at the LIVE tool_use site only — replay and `__gallery`
draw finished tools through the same `toolLine()` helper — cleared at the result, in
`serverToolResult`, and swept in `setBusy(false)`.
**Why:** a two-minute `Bash` and one that had already returned rendered identically, and the dot went
green from its first frame, asserting success before anything came back. White says "no verdict yet".
`bg-pulse` was picked by the user from `design/dot-pulse-probe.html` (four motions, each column
driving the real rule); it is what the background-task chip already wears, so the panel says "still
going" one way throughout. Opacity only, deliberately — see the superseded entry below for what
geometry costs.
**Rejected:** a new `:root` token for the running colour (`--fg` is already the panel's near-white);
capping the pulse to the newest in-flight line (during a fan-out several tools genuinely ARE running,
and showing one misreports state — the sweep bounds it instead).

## 2026-08-13 — A sub-agent settles when its TASK ends, not when it was launched
`onUserEvent` clears `.run` only when `b.is_error || !isInternalResult(resultRaw)`; `taskLine` stashes
`st.tool` and clears it on any `done` frame, and `task_updated` clears it through that stash.
**Why:** measured — an `Agent` tool_use got its tool_result 1.8s later reading "Async agent launched
successfully … working in the background", then ran for minutes. "The result arrived" is not "the work
finished" for this tool. `isInternalResult` already recognised that ack (it is why no OUT box is
drawn for it), so both uses now share one rule instead of a second heuristic.
**Rejected:** keying on the tool NAME — the task lifecycle is explicitly not sub-agent-only, and a
name list drifts. Also rejected: leaving it to the end-of-turn sweep, which is correct but far too
coarse (every agent's dot would stay white until the last one finished). The sweep remains as the
safety net for a `task_notification` that never arrives.

## 2026-08-13 — A sub-agent goes red on a failed/killed TASK, never on failed WORK
`taskLine`'s done branch and `task_updated` add `.fail` for status `failed` or `killed` only.
The panel says nothing about whether the agent's work succeeded.
**Why:** the status vocabulary is `<status>completed|failed|killed</status>`, read verbatim from CLI
2.1.228 — and a sub-agent's tool_result is only ever the launch ack, so without this an agent that
genuinely died settled GREEN. Named states only, not "anything ≠ completed": the binary also carries
`running`/`pending`, and painting a live line red is worse than leaving an unknown state green.
**Why not the work outcome — this was built, then removed the same day.** An agent asked to run a
failing command is reported `completed`; the failure is one level down. Reading it needs the
sub-agent's own transcript, and whether that transcript records the failure AT ALL depends on how the
agent ran the command (gotchas: foreground → errored tool_result, `run_in_background` → nothing). The
same task rendered red one run and green the next, which is worse than either consistent answer.
**Rejected:** colouring from the summary PROSE — the only signal surviving both paths, and the one
the CLI hands the model, but a text heuristic over an open-ended sentence. Offered; user chose removal.

## 2026-08-13 — The gutter dot's colour is a `--dot-c` property on the element
The four byte-identical `::before` rules (`.blk`, `.think`, `.think-live`, `.tool-line`) collapse to
one geometry rule reading `background: var(--dot-c)`, each element declaring its own colour. `:root`
also gains `--pulse-period: 1.6s`, replacing four hand-written copies.
**Why:** it is what lets a STATE recolour the dot in a single declaration — `.run`'s white and
`.fail`'s red are one custom-property line each instead of another copy of the geometry. Same
argument `--block-gap`/`--attach-gap` already make in that file: values used in more than one place
drift apart when nothing names them, and these four had no way to stay identical. (It arrived with
the halo, which needed a colour-agnostic rule; it earned its place independently and stayed.)

## 2026-08-13 — The panel honours prefers-reduced-motion on every animated surface at once
One `@media (prefers-reduced-motion: reduce)` block covers the in-flight dot, `.bg::before`,
`.think-live .shimmer` and `.generating .verb`.
**Why:** the panel had no such block while four surfaces animated infinitely; covering only the newest
would have been incoherent. `.shimmer`/`.verb` also get `color: var(--muted)` back — they set
`color: transparent` and rely on a MOVING gradient to be legible, so stopping the animation alone
leaves invisible text rather than still text. The in-flight dot keeps its WHITE (colour, not
animation), so a running tool is still distinguishable with motion off.
**Verified, and its limit:** under `Emulation.setEmulatedMedia` the block fires in real JCEF and
reverts cleanly. Whether the Linux desktop's own setting reaches an offscreen-rendered CEF is
untested — in backlog.

## 2026-08-13 — [SUPERSEDED, all removed the same day] Three things built and withdrawn
Kept as a record of what was tried, because each looks obviously right until measured. Details of the
deleted code are not worth the reload budget; the lessons live in gotchas.md.
1. **An outward halo** (`@keyframes dot-ping` on a `::after` ring). Shipped, seen in the panel, pulled:
   `.turn-body`'s paint containment shaved it into a "cut half-rectangle" — the exact shape the
   focus-ring comment in chat.css already warned about. Measured: ring left edge x=8 against a turn
   box starting at x=14.
2. **Lifting `content-visibility` for a turn with work in flight**, the fix for (1). Reverted with it:
   it cost the live turn a real rendering property for decoration, and a leaked `.run` would have left
   an old turn uncontained AND painting. A fade changes no geometry, so nothing needs lifting.
3. **A status dot on the sub-task (`.t-prog`) line, red when the agent's work failed** — with an
   `agent_outcome` bridge verb, `SessionStore.agentWorkFailed`, and an `__agent_outcome` frame. Removed
   with all of it (Kotlin reverted byte-exact) once the work outcome proved unknowable; `.t-prog`'s
   colour-only `run` class predates this and stays. A `<tool_use_error>` exclusion was added on the way
   out and is worth remembering on its own — see gotchas.

## 2026-08-13 — One gap for every block that hangs off the line above it
`:root` gains `--block-gap: 18px` (independent blocks) and `--attach-gap: 8px` (a line and what
belongs to it). One rule covers the five tool-line followers —
`.io, .t-prog, .t-note, .tool-imgs, .todos { margin: calc(var(--attach-gap) - var(--block-gap)) 0 0 22px }`
— and `.compact-sum`, `.card-h` (+ `.card .blk`) and `.think .body` name `--attach-gap` directly.
**Why:** the five had drifted to −6/−2/+2/+2/+2px, which against `.turn-body`'s 18px flex gap is
12/16/20/20/20px — so `.tool-imgs`, `.todos` and `.t-note` sat FARTHER from their own tool line than
an unrelated block, reading as detached exactly where they should read as attached. Writing
`calc(target - block)` rather than the nudge is what makes the intent legible and stops the family
drifting again. 8px was picked by the user from `design/tool-gap-probe.html` (12/8/6/4 side by side,
each column driven by the real rule) because it is what `.card-h` already put between a card's
header and its body — so the panel's two "this belongs to that" idioms agree rather than compete.
**Rejected:** matching `.io`'s old 12px (only 6px tighter than an unrelated block — too weak a
signal), and 4px/`.compact-sum`'s value (crowds the todo list and image chips). Also rejected:
putting `.compact-sum` in the shared selector list — its parent `.compact` is an ordinary block, so
there is no flex gap to cancel and the subtraction would pull it 10px INTO the status line; it takes
the token directly instead. Left alone deliberately, as a different relationship: action rows
(`→ .card-b`, `.ask-b → .ask-foot`) at 10px, list rhythm (`.io-row` 0, `.ask-opt` 2, `.todo` 3,
`.ti` 6) and label→sub-line at 1px.

## 2026-08-13 — An unnamed thread re-reads its name once per turn, at `message_start`
`ChatPanel`'s onEvent keeps its per-`result` `pushTitle`, and adds: while `lastTitle` is blank and
this turn has not probed yet, a line containing `"message_start"` triggers one more read (guarded by
`titleProbed`, reset at `result`).
**Why:** `pushTitle` had exactly ONE live-turn caller — the `result` line — while the history list
calls the same `titleOf` off disk every time it opens. So a new conversation showed "New
conversation" beside a titled `current` row for the length of its first turn. Measured, not
inferred: a user's transcript (`D--sites-accesshealth/ccafeb52-…`) has its first prompt at
04:39:10Z and its next at 05:40:30Z, and no ai-title/custom-title/summary at all — an hour of
disagreement, with the first-user-message fallback as the final answer the whole time. `message_start`
is the EARLIEST frame that can work: measured against CLI 2.1.229 (`_local/title_timing.py`), the
transcript is MISSING at `system/init` and 15 KB by `message_start`. Once per turn matters because
`titleOf` scans the whole file and its `(mtime,size)` cache misses on every append of a live session.
**Rejected:** an `onSessionId` callback out of `ClaudeCli` — the most precise-sounding trigger, and
the measurement shows it fires before the file exists, on top of needing new plumbing through two
classes. Also rejected: deriving a provisional title in JS from the rendered first message — a
second derivation of a value that conventions.md requires to have one source.

## 2026-08-13 — The webview is seeded on EVERY load, and the title cache is cleared when it is
`startSession()` split: the `session.start(...)` wiring stays once (`started`), while a new
`seedUi()` — `__mode`, `__project`, the stored init payload, the title, the file list — runs at every
main-frame `onLoadEnd`. `lastTitle` is nulled first. `ClaudeSessionService.start` also rebinds its
callbacks BEFORE the `server != null` guard.
**Why:** `onLoadEnd` always fired on every load; everything it seeded sat behind `started`, so a
reloaded page kept a live CLI with a DOM back at its markup defaults. Proved on the pre-fix build:
after `location.reload()`, `slashCommands` 0 and `projectRoot` "" — an empty slash menu and absolute
paths, permanently. The title was the one that could never heal, because `pushTitle` only pushes on
a CHANGE and the name had not changed: a change-detector on the push side records what was last
SENT, and `pushEvent` is a fire-and-forget `executeJavaScript` that guarantees nothing about
delivery. The callback rebind is the same failure family — a ChatPanel recreated against a live
service returned before wiring itself to the CLI.
**Rejected:** replaying the conversation into a reloaded page. A reload empties the log too, but
recovering it means pushing the transcript WITHOUT restarting the CLI (unlike `refresh`, which does)
and reconciling a replay against frames still arriving mid-turn — and no reload has ever been
observed in the wild. Parked in backlog.md; this change makes the chrome honest, not the log.

## 2026-08-12 — One contract for every foldable block, stated in chat.css
Two rules, written above the `.fold` rules so they cannot drift: (1) `8px 10px` padding, and
whichever element carries it is ALSO the one that scrolls; (2) foldable AND scrollable ⇒ the
scrollbar appears only when EXPANDED. Holders: `.diff`, `.card .cmd`, `.codeblock pre`, `.io-row`,
`.compact-sum` (not scrollable — it wraps).
**Why:** rule 2 is not extra work, it falls out of rule 1 — a scrollbar is painted at the bottom of
its container's padding box, and `.fold:not(.open)`'s `overflow: hidden` crops the element it lands
on. One element for both and the behaviour is free; split across two and you get a bar floating
mid-box that survives collapsing, which is exactly what `.io` did. `.io-row` was the only surface
that deviated (6px vertical, and the fold on `.io-v` while the row scrolled). `foldBlock` is now
given the ROW.
**Rejected:** `:has()` to reach the row from the folded value — one line, but it makes the
behaviour depend on a selector feature we cannot test outside real JCEF, and it would have left
the two surfaces structurally different for no gain. Also rejected: keeping `.io-row` at 6px
because it is denser — the point of the change was that these read as one family.
**Exception, deliberate:** `.io-row`'s left 10px lives on its sticky `.io-k`, not on the row.
Sticky clamps to the containing block, so padding on the row makes the label unable to reach the
padding edge; it gets pushed right and eats the column gap. The 10px is still there, just owned by
the label — recorded at both sites.

## 2026-08-12 — Busy is a fact about the STREAM, not about who sent the turn
`message_start` sets busy when it is false, resetting the request-scoped counters (`turnTokens`,
`reqTokens`, `reqSeed`, `retrySeen`) exactly as `sendTurn` does. Separately, `pendingBgTasks`
counts only tasks whose `task_type` is NOT `local_bash`.
**Why:** `setBusy(true)` had exactly one call site — inside `sendTurn` — so a turn the CLI started
on its own streamed with the button on Send, and Stop (the only interrupt control) was unreachable.
That happens on every background-shell completion: the CLI injects its notification prompt and
starts a turn. **Measured, not inferred** — a real stream-json capture (CLI 2.1.228, kept at
`_local/wire-short.jsonl`) shows the notification turn arriving with NO user frame on the wire,
which is what makes `message_start` the only possible hook; the transcript persists a `user`
record, the live stream does not. The counter reset is required because it IS a new request (the
CLI brackets it with `system/init` + `status:"requesting"`); without it the done line billed the
previous request's tokens (142 vs its own 12, measured). The shell half is the same premise from
the other side: the CLI's own busy set excludes `local_bash`, so a shell's `result` is the true end
and counting it parked busy for the life of the process.
**Rejected:** the CLI's allow-list form for the filter — an unknown `task_type` must count as
SUSPENDING, because finalizing a live request early corrupts its accounting and drains the queue
into it, while failing to un-suspend is a visible spinner that heals when the roster empties.
Also rejected: hooking `system/status:"requesting"` (fires ~3x per ordinary turn) or a `user` frame
(does not exist on the wire). `ClaudeCli.route` gained `if (!stopped) onEvent(line)` as a
consequence — a stale buffered `message_start` after a restart would now set busy with no result
coming.

## 2026-08-12 — A value that IS the work goes in the IN box, not the description
`"function"` moved from `RenderLimits.DESC_KEYS` to `IN_KEYS`, so `mcp__playwright__browser_evaluate`
renders like Bash: blank tool line, JS body in the IN box. `.t-desc` also gained a one-line clamp
(`nowrap` + ellipsis) with the SHOWN text mirrored onto `title`.
**Why:** `function` joined DESC_KEYS on 2026-08-05 to close 15 blank lines, assuming its value reads
like prose. Measured across the nine real calls in local transcripts it is 230-2965 characters of
multi-line JS — `DESC_MAX` produced a mid-token slice, wrapped across the line. IN_KEYS' own rule
already covered it: "whose value is the instruction ITSELF". The IN box is `white-space: pre`,
scrolls sideways, folds to three lines and caps at `CMD_MAX`, which no real call reaches. Neither
reference client puts code on the line either. The tooltip carries the post-`DESC_MAX` string
deliberately: it reveals what the CLAMP hid, never what the CAP dropped.
**Rejected:** keeping it in both lists (the duplicate-description bug fixed the day before), and
clamping the description without moving it (the code stays unreachable). `toolLabel()` left alone —
changing `PlaywrightBrowserEvaluate` to VS Code's `Playwright [browser_evaluate]` would reopen a
settled 2026-07-30 decision and add the first MCP branch to a deliberately universal rule.

## 2026-08-12 — The rename editor dismisses on CAPTURE, the one place the popup idiom does not fit
An outside click discards the rename (like Escape and ✕, never commits), via a document listener
registered with `capture: true` — unlike every other dismissal in chat.html, which bubbles.
**Why:** built first in the existing bubbling handler, per plan, and it failed on exactly the
clicks most likely to follow: `#histBtn`, the model/mode chips and the effort dots all
`stopPropagation` in their own handlers, so their clicks never reach a bubbling document listener.
Capture runs before any target handler. Discard rather than commit because a stray click would
append a `custom-title` record with no undo. Excluding `#convTitle` as well as `#convEdit` keeps
the opening click from closing the editor it just opened.
**Rejected:** a `blur`/focus-loss dismissal — there is ZERO `blur` handling in the webview, and
every popup stays open when the panel loses focus; rename must not become the only surface that
does otherwise. Also rejected: adding blur to every surface (wider blast radius, no request).

## 2026-08-12 — The replay window keeps the NEWEST blocks, and says so at its top edge
`readTranscript` reads the whole file and evicts from the FRONT once it holds more than
`MAX_BLOCKS` (20,000, was 4,000). The cut is the first `user` block at or after the minimum that
must go — unbounded forward scan — and eviction triggers at `max + chunk` so each cut removes at
least a chunk. When anything is dropped the parser prepends a `truncated` block carrying the COUNT,
which chat.html draws as a `.status` line ("12,400 earlier blocks not loaded").
**Why:** the old cap `break`ed out of the read, keeping the OLDEST blocks — a session resumed on
2026-08-12 replayed as though it had ended on 2026-08-06 (user screenshot; 6,034 blocks against a
4,000 cap). The tail is what is on screen, so the tail is what must survive. The count rides at the
HEAD of the list rather than on a wire field because `more` reaching 0 is already exactly the moment
it should appear — no new frame, no ChatPanel change. Wording lives in chat.html, the same split
`auth` uses. 20,000 is affordable because per-block cost is bounded by `RenderLimits` and
`IMAGE_BUDGET`; it is a runaway guard, not a paging window.
**Rejected:** a bidirectional paging window that re-parses on scroll (the user's first proposal) —
a mid-file range cannot be parsed in isolation (results patch earlier blocks by `tool_use_id`, tasks
rebuild from increments, compact summaries link by `parentUuid`), so every page-up would cost a full
file scan to save memory that `RenderLimits` already bounds. Also rejected: a BOUNDED forward scan
for the turn boundary — shipped, and it failed on the first real transcript (below).

## 2026-08-12 — A cap that trims history must not do it silently, at EITHER end
Two edges, same rule. The window announces what it dropped (above), and `#fade-top` now switches
off at `scrollTop <= 1` so the first block is legible.
**Why:** the fade exists to dissolve content scrolling under the header, but at the true top there
is nothing under it — it was describing content that does not exist while washing out the marker.
Sticky `.msg-user` (z 6) rides above the fade, which is why this never showed in five months of
use; ordinary content at z-auto does not.
**Rejected:** raising the marker's z-index instead — it would leave one crisp line hanging over
content that is correctly fading beneath it. Also rejected: padding the marker clear of the 48px
band, which trades an unreadable line for a hole at the top of every windowed conversation.

## 2026-08-12 — The task-progress line dedupes against the IN box, not just the tool line
`taskLine` suppresses a `task_started`/`task_progress` description that exactly matches EITHER the
tool line's `.t-desc` OR its IN box row.
**Why:** the `.t-desc` half shipped earlier from a user screenshot, but Bash's `.t-desc` is blank
BY DESIGN — `command` is in `IN_KEYS`, not `DESC_KEYS` — so a Bash called without a `description`
had nothing to compare against, the CLI's frame fell back to the command itself, and the command
printed twice: in full in the box and again beneath it cut to `descMax`. Exact-match only, both
surfaces, so a real sub-agent's running commentary still draws.
**Rejected:** a prefix or fuzzy match (would eat commentary that legitimately quotes the command),
and locating the IN row by position (a box can hold OUT alone — it is found by its `IN` key).

## 2026-08-12 — Release notes are enforced by the build, not by the checklist
`buildPlugin` fails when `changeNotesHtml` (`plugin/build.gradle.kts`) carries no `<b>X.Y.Z</b>`
entry for the version being built, with a message pointing at `docs/release.md` step 1b. Older
versions stay listed in the notes — they are what users are updating FROM.
**Why:** the checklist line added earlier the same day was the same class of guard that had already
failed twice (0.4.0 and 0.5.0 both published 0.3.3's notes), and automating the upload removed the
last human who might have noticed. This fires at step 2, before the tag and before a version number
can be spent on a bad upload. The user asked for it after reading the checklist-only version.
**Rejected:** a configuration-time check (would fail `runIde` mid-feature, which teaches people to
ignore it) and validating the built plugin.xml instead of the source string (more machinery, same
answer). Trap hit on the way: with `org.gradle.configuration-cache=true`, a task action that reads a
script-level `val` captures the script object and the cache refuses to serialize it — evaluate into
locals in the configuration block. See gotchas.md.

## 2026-08-12 — The Marketplace upload is automated, but only the UPLOAD
`.github/workflows/marketplace-upload.yml` (the repo's first workflow) fires on `release: published`,
downloads the zip `gh release create` just attached, and POSTs it to
`https://plugins.jetbrains.com/api/updates/upload` with `xmlId` + `file` and a bearer token from the
`JETBRAINS_MARKETPLACE_TOKEN` repo secret. Steps 1-9 of `docs/release.md` stay manual and local.
**Why:** the web-form upload was the one release step with nothing enforcing it, and the step that
decides whether Marketplace users see the release at all. Re-posting the PUBLISHED asset rather than
building in CI keeps "the zip users get is the zip that was smoke-tested on this machine" — the
guarantee step 9's `cmp` check already establishes. It also needs no Gradle and no 1.5 GB IDE
download in CI, so the run is seconds. Signing is unaffected: the zip goes up unsigned exactly as the
web form sent it (`signPlugin` is `onlyIf`-guarded on a key this project doesn't configure, and five
releases were accepted this way).
**Rejected:** `./gradlew publishPlugin` — it works (IPGP 2.1.0 falls back to `buildPlugin`'s archive
when signing is unconfigured), but it is still a command to remember, which IS the problem. Also
rejected: building the zip in CI on a tag push (publishes an artifact nobody ran, and moves the
release-notes approval gate somewhere worse); and a `channel` field (Stable is what the web form did).
**Consequence, handled:** stale `changeNotes` used to be caught by the human doing the upload —
0.4.0 and 0.5.0 both shipped 0.3.3's notes. It is now step 1b of the checklist.

## 2026-08-12 — A conversation's title is derived from the WHOLE transcript, not a window
`computeTitle` reads every line: the first `TITLE_HEAD_LINES` (400) are parsed in full for the
derived title (first user message, legacy `summary`), and past that a line is rejected on a substring
unless it holds `custom-title` or `ai-title`.
**Why:** those two records are appended WHERE THEY HAPPEN, so on a long thread they are thousands of
records in — a rename on a real 10,458-line transcript sat on lines 10455-10458 and the 400-line head
scan never saw it, so the panel showed the derived title forever and the rename looked broken. The
cost is the scan `tokensOf` already pays for every session in `list()` (measured on the same 35 MB
file: 521 ms cold, 0 ms cached), so this adds no new class of work.
**Rejected:** a tail window (`tailLines`, as `lastActivityOf` uses) — 256 KB is ~75 records on a
transcript with large tool results, so the name would evaporate after ~10 more turns, which is worse
than a consistent bug; and caching the name in our own state — a second source of truth is exactly
what the byte-identical `custom-title` record exists to avoid.

## 2026-08-12 — Rename writes the CLI's own `custom-title` record, from the header title
`SessionStore.rename` appends `{type:"custom-title", customTitle: title.trim(), sessionId}` + "\n"
with O_APPEND — the CLI's writer, read verbatim from the 2.1.226 binary, three fields, no uuid or
timestamp. UI is the header title: hover pencil, click the title OR the pencil, header becomes the
editor, Enter saves / Esc cancels. Safe on the LIVE session, unlike delete.
**Why:** byte-identical writes mean a rename here and a `/rename` in the terminal are one act, so
neither client sees a name the other cannot explain. The header (not the history row) was the
user's explicit choice. Appending is safe live because the CLI opens O_APPEND per write.
**Rejected:** the `rename_session` control subtype — it answers "onRenameSession callback not
registered", dispatching to an SDK embedder's callback, unreachable over stream-json. Also
rejected: a `/rename` slash command, and rename in the conversations list (user picked the header).

## 2026-08-12 — Tool-line paths: project-relative, one line, and a CHARACTER budget for the tail
Display drops the project root (segment-boundary match only) and splits into a shrinking `.p-head`
and a never-shrinking `.p-tail`. The tail carries the filename plus its parent when the two fit
`RenderLimits.PATH_TAIL_MAX` (40 chars), else the filename alone. The absolute path rides
`dataset.path`; the click handler prefers it over `textContent`.
**Why:** a character budget needs no layout measurement — the alternative forces a synchronous
layout per tool line during a replay that renders hundreds. CSS alone cannot express "shrink the
parent only once the prefix is gone": flex distributes shrink proportionally, so any factor big
enough to save the filename also nibbles the parent on a path that had room (both regimes measured
on real JCEF). Storing the absolute separately also fixed a path past DESC_MAX being *clicked*
truncated.
**Rejected:** filename-only tail (loses the disambiguating folder); parent always in the tail
(a narrow panel then clips the FILENAME); three spans with shrink ordering (proportional shrink
cost the parent a character it didn't need to lose); `direction: rtl` for a left-side ellipsis
(`unicode-bidi: plaintext` cancels it, and without it the leading `/` reorders).

## 2026-08-12 — The context gauge is an SVG arc, drawn with the composer's Lucide geometry
A ring in front of the percentage: `pathLength="100"` so `stroke-dasharray` IS the percentage,
both strokes `currentColor`, `--ctx-pct` set beside the digits in `renderContext`.
**Why:** `currentColor` makes the ring cross the 50% warn threshold with the number for free, with
no second rule to keep in step. `pathLength` removes the 2*pi*r constant. Lucide geometry (24
viewBox, r=9, stroke 2, 19px box, 3-unit inset) makes it measure identically to the square-slash
beside it — 13.5x13.5, confirmed in DevTools.
**Rejected:** a conic-gradient pie behind a radial mask — a third of the code and no markup, but
Chromium anti-aliases neither the sweep edge nor the mask, so it rendered visibly stepped.

## 2026-08-09 — Deleting the LIVE conversation = leave it first, then delete
Every history row now offers delete, the current one included. The live path routes through
`ClaudeSessionService.deleteCurrentSession`: restart on a fresh conversation, `awaitExit(5s)`
on the OLD process off-EDT, then delete the transcript, then re-push the sessions list.
**Why:** the CLI reopens the transcript per write, so a live delete truncates rather than
removes (the original refusal reason) — and `stop()` only sends the signal, so a dying CLI
can still flush one resurrecting write; the bounded wait closes that window. Deleting the
thread you're looking at was a standing user annoyance; "make it not exist" implies leaving
it. Mid-turn delete stops the turn — identical to mid-turn "New conversation", deliberately.
**Rejected:** delete-in-place (truncates, the refusal stays as backstop); hiding delete on the
current row (the previous design — solved the corruption, not the user's need); unbounded wait
(a hung CLI would leak the pooled thread and the file).

## 2026-08-09 — Editor verdict UI is a bar UNDER the diff, card-identical, combined grant only
Accept / Accept-all / Reject live on a `FileEditorManager.addBottomComponent` bar beneath the
diff editor: centered, no prose, no tint, the panel card's exact colours (chat.css .ok/.no,
mirrored constants) and exact Lucide glyphs (bundled /icons/accept.svg, accept-all.svg,
reject.svg). The suggestion button is COMBINED: one click grants every allow-suggestion whole
("Always allow" when rules are among them, else "Accept all edits"), via the new
FILE_SAVED_ALL verdict — a permission-flow-only extension; bridge verdicts stay the reference
three. No split/partial-grant dropdown in the editor — the panel card keeps that.
**Why:** the two surfaces answer the SAME question, so they must read as one control; the
user reviewed each iteration in runIde and specified bottom placement, no text, no tint, and
"combined, not the dropdown". Partial grants are a deliberate act that belongs on the card.
**Rejected:** toolbar CONTEXT_ACTIONS icons (invisible among diff icons); toolbar text
buttons (no warning-free API across 242→262); NOTIFICATION_PROVIDERS banner (top-only, loud);
platform icons (don't match the card's glyphs); split dropdown in the bar (user's explicit
spec). Mechanics and traps in gotchas.md.

## 2026-08-09 — Edit permissions are dual-surface, first answer wins; editor close is NOT a grant
A can_use_tool for Edit/Write/MultiEdit opens the panel card AND a real editor diff (read-only,
balloon Accept/Reject); `ClaudeSessionService.respondPermission`'s pending map is the arbiter —
first remove() sends, the loser no-ops. Closing the diff without deciding leaves the card as the
sole surface.
**Why:** keyboard users keep the card (and the always-allow rules, which stay card-only); the
editor gets the reference-style surface the roadmap wanted. TAB_CLOSED deliberately DIVERGES
from the bridge flow's accept-as-proposed here: a permission must never be granted by a window
being tidied away. Read-only panes because accept answers with the ORIGINAL input — an editable
pane would silently discard typing (tweak-travel is a possible v2).
**Rejected:** editor-only surface (breaks panel-only workflows); editor-title buttons for v1
(balloon ships now, buttons can follow); TAB_CLOSED→allow parity (unsafe for permissions).

## 2026-08-09 — openDiff never writes; every review's diff tab closes by a HELD handle
DiffReview implements the reference verdict contract (FILE_SAVED + final pane text /
DIFF_REJECTED / TAB_CLOSED — the CALLER does the disk write) and opens its diff as its own
`ChainDiffVirtualFile`, holding the file reference from creation; resolution closes exactly
that tab.
**Why:** measured against both halves of the reference — the VS Code extension builds both
panes as temp documents and never touches the real file; the CLI maps the verdict to
{oldContent, newContent} and writes itself. And `FileEditorManager.openFiles` does NOT report
diff editors (measured live: visible diff tab, empty openFiles), so any find-then-close closes
nothing — the old closeAllDiffTabs filter had never worked.
**Rejected:** writing the file on accept (double-write that fights the CLI — 10.5's original
premise, corrected); find-by-name/class tab closing (provably a no-op).

## 2026-08-09 — 10.1/10.3 re-scoped: the model-facing IDE-tool allowlist is upstream policy
The CLI drops every `mcp__ide__*` tool from the model's roster except getDiagnostics +
executeCode — a filter byte-identical across 2.1.222/223/226. Items now mean their bridge
halves (verified over MCP-over-WS).
**Why:** three identical versions = deliberate policy, not a regression to wait out; VS Code
models get the same two tools, so parity is preserved by accepting it.
**Rejected:** registering the bridge under a non-"ide" server name to dodge the prefix filter —
the CLI finds its IDE client BY the literal name `"ide"` for its own IDE features (TUI
diff-in-IDE among them); the dodge breaks more than it restores.

## 2026-08-09 — Retry-line wording is a chat.html table; replay reorders late-flushed retries (9.1)
The wire's api_retry `error` is a five-code enum (network failures = literal "unknown") —
`RETRY_REASONS` translates it, unfamiliar codes degrade to the raw code, twins dedupe by
consecutive attempt/max key. Replay-side, SessionStore inserts an api_error record whose
timestamp precedes the last emitted error item BEFORE that error.
**Why:** the rich reason never rides the stream (TUI-in-process only), so translation is the
ceiling live; and the CLI flushes buffered api_error records AFTER the concluding error record —
file order lies, timestamps and the parent chain don't.
**Rejected:** surfacing "the" error reason live (doesn't exist on the wire); a Set for dedupe
(would swallow a second same-turn storm's restart at 1/10); general timestamp re-sort of replay
(risky for every other record family — the insertion is scoped to this measured pattern).

## 2026-08-09 — Model-facing tool results are suppressed by CONTENT, not tool name (7.4)
`RenderLimits.isInternalResult()` drops the OUT box for a result whose FIRST line closes with
the CLI's own "(This tool result is internal metadata …)" declaration; `stripPlumbing()` also
strips a position-0 `[harness: …]` envelope.
**Why:** the async sub-agent launch is model-facing bookkeeping end to end, but the SAME tool's
completed result is the sub-agent's report — the one thing on that line worth reading — so
RESULT_SKIP's name-keying cannot express the split; only the text can. Position-0-only for the
envelope is structural safety: the CLI escapes line-initial forgeries to `[\harness:` before
prepending its own, so an unescaped one in first position can only be real.
**Rejected:** adding Task/Agent to RESULT_SKIP (kills the completed report); stripping
`[harness:` anywhere in the text (a sub-agent quoting the marker — this project's own notes
do — would lose it).

## 2026-08-09 — Replay error parity: the CODE travels, the WORDING lives once in chat.html (8.2)
SessionStore emits `status` items carrying the raw auth error code (`icon:"auth"`); replay
resolves it through the same AUTH_BLOCKED map the live path reads. `AUTH_BLOCKED_CODES` (the
membership set) lives in RenderLimits but is deliberately NOT spliced into LIMITS.
**Why:** the per-code text is UI wording only chat.html needs; Kotlin needs only membership.
A spliced set the JS never reads is dead weight — the alignment is pinned by a test instead
(every code must appear in chat.html), and an unknown code degrades to showing the raw code
rather than nothing. The phantom-summary suppression (`reqError`) is last-record-wins, not
sticky, so a request that recovers after an error record keeps its summary.
**Rejected:** duplicating the wording into Kotlin (drift); keying suppression on the presence
of any error record (a recovered request would lose its summary).

## 2026-08-09 — Live edit diffs render OPTIMISTICALLY from tool input, superseded by the card
Edit/Write/MultiEdit now draw replay's "Applied" card live, built from the tool_use INPUT at
`tool_result` time, and a `permission_request` for the same edit marks the pending record
superseded so manual mode never double-renders.
**Why:** the live wire carries no diff data at all (no `toolUseResult`/`structuredPatch` — probed
2026-08-05), and in acceptEdits / under a saved rule / in a pre-authorized path the CLI never
sends `can_use_tool`, so the permission card — the only live diff producer — never fires. Keying
off `currentMode` instead would have missed rule-grant and scratchpad approvals; keying off the
card's absence is not knowable at `content_block_stop` (the card, if any, arrives later). Hence
optimistic-plus-supersede rather than wait-and-see.
**Rejected:** asking the CLI for the patch (nothing to ask); a bespoke live diff widget (replay's
card already exists, is mode-agnostic by design, and sidesteps the bare-`.diff` float problem).
Cost of the choice: the gutter line must be fetched PRE-apply (see gotchas) and degrades to no
line numbers when it can't be found.

## 2026-08-09 — All keyboard chords removed; the plugin binds NO shortcuts
The three webview JS keydown chords are deleted, not re-homed as IDE actions: Ctrl/Cmd+N (new
conversation), Ctrl+Alt+G (gallery), F12 (DevTools). The `"devtools"` bridge branch in
`ChatPanel.kt` went with them — nothing sends it now. Every capability keeps a route: the New
button and `/clear` both send `kind:'new'`, the gallery stays exposed as `window.__gallery()`,
and DevTools keeps the shortcut-less `ClaudeBrains.OpenDevTools` action (Find Action).
**Why:** all three were dead on this setup — Ctrl+N was swallowed by the IDE's "Go to Class",
and the Ctrl+Alt+G / F12 handlers never fired at all inside JCEF. A shortcut that silently does
nothing is worse than none.
**Rejected:** re-registering them as IDE-level actions with `<keyboard-shortcut>` (the fix
previously queued in state.md) — that trades a dead chord for a keymap collision to hunt on
every IDE and OS, for a panel whose buttons are already one click away. Closes manual-test
1.5 / 11.1 / 11.2, dropping the open-issue count from 19 to 17.

## 2026-08-08 — Manual-test pass conventions; two behaviours accepted, not fixed
The 92-item pass closed with a "tick + inline ISSUE note" convention: a box is ticked when the
behaviour was OBSERVED, with defects recorded under it rather than leaving boxes open. Two
verification methods were admitted for unforceable events, each with provenance stated in the
tick: CDP fixture injection through `onClaudeEvent` (rate-limit: recorded fixture; refusal
fallback: the VS-Code-bundle shape the handler was built from), and direct MCP-over-WS calls to
the bridge (openFile / selection / openDiff) when CLI 2.1.226 stopped exposing those tools to
the model. **Accepted, not bugs:** untitled sessions deriving "/model <x>" as their title (CLI
audit record, same family as /effort turns); replayed image chips as "file.jpg <smaller>" (the
transcript persists the bare API image block — no filename exists to show).
**Rejected:** styling the issue notes as GitHub alerts (tried, reverted — plain text wanted);
logging the name-loss as a defect (retracted after measuring the records).

## 2026-08-07 — Light theme declined; colour groundwork removed
`docs/colors.md`, `design/colors.html`, and `design/light.css` (the theming groundwork —
light.css was the draft palette, unwired from mockup.html's `<link>` + devbar theme toggle)
deleted, along with the completed `docs/port-plan.md` and the one-off comparison pages
`design/btn-variants.html` / `design/model-icon-variants.html`. The
colours-through-`:root`-tokens rule they carried moved to conventions.md; the token inventory
itself had already drifted (28 documented vs 35 in chat.css).
**Why:** the user doesn't want a light theme, and the doc duplicated what `chat.css` answers.
**Rejected:** keeping the doc trimmed to theming notes — groundwork for work that won't happen
is reload-budget spent on nothing; git history keeps it recoverable.

## 2026-08-07 — Project context lives in `.claude/context/`; CLAUDE.md abolished
The `/context` skill's file set replaces the 427-line root `CLAUDE.md` (last committed copy at
`ee7e9fc`) and the global auto-memory directory for this project. `.gitignore` un-ignores
`.claude/context/` so the memory travels with the repo.
**Why:** one portable, structured location instead of a monolithic file that competes for the
whole reload budget every session. **Rejected:** keeping CLAUDE.md alongside (two sources that
drift — the same reason the plugin doesn't rebuild terminal config). `runbook.md` deliberately
not created: release steps already live in `docs/release.md`.

## 2026-08-06 — Declined (not deferred): cost display, token/usage panel, per-record metadata
Client-parity items 17, 18, 26 declined outright, joining the already-deliberate non-features:
hidden bookkeeping records (25), stripped IDE context (27), off-window history as a consequence
of windowed replay (28), silent `/effort` turns replaying as an honest audit trail (30).
**Why:** decision, not queue position — usage display moved OUT of deferred. Keeps the panel
focused on the many-times-an-hour loop.

## 2026-08-03 — Mode switcher sends the CLI's own four modes; bypass machinery removed
`manual` / `acceptEdits` / `plan` / `auto`, all switching live via one control request.
**Why:** `auto` is a safety-checked mode; the old plugin sent `bypassPermissions` under the
label "Auto" and had to relaunch the CLI to enter it (only bypass is refused at runtime).
**Rejected:** keeping bypass + relaunch-with-resume (`__modeRestarted` is gone). A stored
`default`/`bypassPermissions` migrates in `selectedMode()`.

## 2026-07-31 — Renamed to "Claude Brains"; distribution is Path B
Plugin id `io.github.amitsidhpura.claude-brains`, packages `io.github.amitsidhpura.claudebrains.*`,
no syncroze references. Custom plugin repo on `github.com/amitsidhpura/claude-brains`, NOT the
JetBrains Marketplace (process in docs/release.md).
*Partially superseded same day:* the plugin WAS also submitted to the Marketplace on 2026-07-31
(vendor `amitsidhpura`) — both channels ship the same id; IDEs take the higher version
(docs/release.md § Marketplace listing).

## 2026-07-30 — Slash menu is an allowlist; /model and /effort deliberately hidden
Over `--input-format stream-json` there's no interactive terminal, so only turn-producing
commands work; unconfirmed ones are hidden and refused. Enabled: `/compact` (→ CLI), `/clear`
(native, = New button). `/model`/`/effort` hidden because the composer has the model chip and
effort slider. Effort rides a muted `/effort` turn (no control request exists); it reappearing
on resume is ACCEPTED as an honest audit trail — no filter planned (closed renderer-parity's
last open item).

## 2026-07-30 — Per-turn file rewind removed
Not worth the cost: needs `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1` (which also bloats
every transcript with file-history snapshots), a git repo, and client-supplied uuids.
Revival notes in gotchas.md.

## Founding — "Develop in the IDE. Configure in the Terminal."
The scope rule. Settings page and non-terminal login are the terminal's half and will NOT be
built — they are "By design" absences, never gaps or backlog. Rationale: rebuilding `~/.claude`
config in the IDE is a second implementation that can only drift.
