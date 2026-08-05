# Client parity — what the official clients show that we don't

Everything the `claude` CLI emits (or the official clients render) that the plugin currently drops
on the floor, scored against both official clients. Written 2026-08-04 against plugin `0.3.3` /
CLI+extension `2.1.220`.

> Not to be confused with `docs/renderer-parity.md`, which is an INTERNAL audit — our own three
> render paths (live / resume / mockup) against each other. This doc is EXTERNAL: us against the
> terminal TUI and the VS Code extension.

**How each column was established**

- **Us** — read out of this repo: `plugin/src/main/resources/webview/chat.html` (live renderer),
  `session/SessionStore.kt` (replay parser), `cli/ClaudeCli.kt` (stream routing),
  `ui/ChatPanel.kt` (init payload). Every claim carries a `file:line`.
- **VS Code** — grepped out of the installed bundle at
  `~/.vscode/extensions/anthropic.claude-code-2.1.220-win32-x64/webview/index.js`. Minified, so the
  evidence is a symbol or a literal string, quoted per item. This is authoritative, not recalled.
- **Terminal** — observed TUI behaviour of the same build; the CLI ships as a 265 MB native binary,
  so there is no source to cite. Items I could not confirm are marked `?` rather than guessed.
  Where a claim was checkable against strings in the binary it is noted.

**Legend** `✅` shows it · `⚠️` shows part of it · `❌` doesn't show it · `?` unverified · `—` n/a

**Take** is my recommendation, not a decision: `P0` fix, the panel is currently wrong ·
`P1` do next · `P2` worth doing · `P3` skip, or already deliberate.

## How these are prioritised

Scored against the project philosophy — *Develop in the IDE. Configure in the Terminal.*
(CLAUDE.md § Philosophy). Parity with the official clients is NOT the goal by itself; matching
them item-for-item would rebuild the terminal inside the IDE, which is the thing we said we
wouldn't do. The question for each row is the philosophy's own test: **is this reached for many
times an hour while writing code?**

Three rules fall out of that, and they are what moved the takes below:

1. **No terminal fallback → the panel must carry it.** Some information exists only inside our
   process while a turn is running: CLI stderr, rate-limit events, a hook that blocked a tool, the
   reason a session died. You cannot go and type `/status` for these — the terminal isn't running
   this session. Anything in this class outranks its raw frequency, because the alternative to
   showing it is nobody ever seeing it.
2. **The terminal already answers it → don't rebuild it.** `/mcp`, `/cost`, `/context`, `/status`
   are a few keystrokes away, already documented, and consulted occasionally rather than
   constantly. A second implementation in the IDE can only drift out of sync with the first.
   Note the shape this argues for: a *failure notice* is a during-work signal, a *management UI*
   is configuration. We take the first and decline the second.
3. **Wrong beats missing.** The panel is where the user reviews what Claude did. If it reports a
   truncated result as complete, or a killed command as a success, the core loop is broken in a
   way a missing feature never is. These are promoted to `P0` regardless of frequency.

Where this doc's take now disagrees with plain client parity, the row is marked **(philosophy)**
and the reasoning is in the item.

---

## Summary

| # | Item | Terminal | VS Code | Us | Take | Effort |
|---|------|:--------:|:-------:|:--:|------|--------|
| 10 | Truncated-output marker | ✅ | ✅ | ✅ | **DONE** 2026-08-05 | shipped — `.io-cut` / `.cmd-cut` |
| 9 | Bash exit-code explanation (was "failure detail") | ✅ | ✅ | ✅ | **DONE** 2026-08-05 · re-scored P0→P3 | shipped — `.io-note`; premise was measured wrong |
| 15 | Compaction boundary | ✅ | ✅ | ✅ | **DONE** 2026-08-05 | shipped — marker + folded summary + gauge reset |
| 21 | Model refusal fallback | ? | ✅ | ✅ | **DONE** · **VERIFIED against 2.1.222** 2026-08-06 | 2nd pass found a real bug (`scope`) + a missing subtype |
| 31 | API retry storms (`system/api_error`) | ✅ | ? | ✅ | **DONE** 2026-08-05 · FOUND BY MEASURING | 20 real records in ONE session, all invisible |
| 6 | Non-Bash tool result summaries | ✅ | ✅ | ✅ | **DONE** 2026-08-05 | shipped — structural skip rule, not per-tool shapes |
| 2 | Sub-agent final report | ✅ | ⚠️ | ✅ | **DONE** 2026-08-05 | free with 6, as predicted |
| 29 | Non-Bash tool output on replay | ✅ | ✅ | ✅ | **DONE** 2026-08-05 | shipped WITH 6, one shared skip list |
| 7 | Tool input beyond one key | ✅ | ✅ | ✅ | **DONE** 2026-08-05 | shipped — one case, not a matrix: Read line ranges |
| 5 | Blank Task/Skill tool lines | ✅ | ✅ | ✅ | **DONE** 2026-08-05 | shipped — 6 keys into `DESC_KEYS`, 74 lines fixed |
| 14 | Todo / task checklist | ✅ | ✅ | ✅ | **DONE** 2026-08-05 | `TodoWrite` (replay) + the live `Task*` list |
| 16 | Rate-limit warnings | ✅ | ✅ | ✅ | **DONE** 2026-08-05 · seen live, then corrected | first guess cried wolf on the routine case |
| 23 | CLI stderr | — | ? | ✅ | **DONE** 2026-08-05 | tail buffered, shown under the exit line |
| 20a | Auth failure → "sign in from a terminal" | ✅ | ✅ | ✅ | **DONE** 2026-08-05 · **VERIFIED against 2.1.222** | field confirmed; 1 dead check dropped, 1 code added, wording corrected |
| 3 | Sub-agent prompt (what it was asked) | ✅ | ✅ | ❌ | P2 | **S** — `ioRow('IN', …)` already exists |
| 1 | Sub-agent internals (nested tool calls) | ✅ | ✅ | ❌ | P2 | **L** — the only design pass in this doc |
| 4 | Background task roster | ✅ | ✅ | ❌ | P2 | **S** chip · **M** ⇢ real roster rides on 1 |
| 12 | Server-side tools (web search blocks) | ✅ | ✅ | ❌ | P2 | **S** — one `content_block_start` branch |
| 24 | Queued messages | ✅ | ❌ | ❌ | **P2** ↑ (philosophy) | **L** — composer state machine, not a renderer |
| 13a | MCP *failure notice* (not a server UI) | ✅ | ✅ | ✅ | **DONE** 2026-08-05 · probe corrected the source | `S` held; the "already in the init payload" claim did not |
| 22 | Hook activity | ✅ | ? | ❌ | P2 | **?** — probe the empty-ack first; unknown until then |
| 8 | Tool-returned images | ⚠️ | ✅ | ❌ | **P3** ↓ (measured) | 0 local records — `isImage` never once set |
| 19 | Real thinking-token count | ✅ | ✅ | ✅ | **DONE** 2026-08-05 · BUILT BLIND | event is live-only; chars/4 kept as fallback |
| 11 | "File was modified by the user" | ? | ✅ | ❌ | **P3** ↓ (measured) | 0 local records — `userModified` never once set |
| 17a | `modelUsage[].contextWindow` for the gauge | ✅ | ✅ | ✅ | **DONE** 2026-08-06 · probed first | `S` held; `modelUsage` is a MAP incl. side models |
| 13b | MCP server management UI | ✅ | ✅ | ❌ | **by design** | — |
| 20b | Account / plan / login display | ✅ | ✅ | ❌ | **by design** | — |
| 17 | Cost | ✅ | ✅ | ❌ | P3 | S |
| 18 | Token breakdown / usage panel | ✅ | ✅ | ⚠️ | P3 | L |
| 26 | Per-record metadata (effort/branch/model) | ⚠️ | ⚠️ | ❌ | P3 | S |
| 25 | Bookkeeping records on replay | ❌ | ❌ | ❌ | P3 | — keep as is |
| 27 | Injected IDE context | ❌ | ❌ | ❌ | P3 | — keep as is |
| 28 | Off-window history | ✅ | ✅ | ⚠️ | P3 | — keep as is |
| 30 | Silent `/effort` turns | — | — | ❌ | P3 | — keep as is |

Sorted by take, not by item number — the numbered sections below keep their original order.
`↑`/`↓` mark a take the philosophy moved; **by design** means it is now ruled out rather than
deferred, and belongs in the release description's "By design" list, not its gap list.

**Effort scale.** Sized against this codebase, not in the abstract — what makes something `M` here
is usually that it has to land in the live renderer AND the replay parser without the two drifting,
which is the failure mode `RenderLimits` and `docs/renderer-parity.md` exist to prevent.

| | Meaning |
|---|---|
| **XS** | A few lines through an idiom that already exists. No new UI, no new state. |
| **S** | One branch, one existing helper. Fits in a sitting, testable in the gallery. |
| **M** | A new renderer, or one change that must land in both paths and stay in sync. |
| **L** | Needs a design pass or new state that outlives a single event. |
| **?** | Can't be sized until something is probed. |

`⇢` marks **coupling**: that item is cheap only if it rides along with the one named. Items 2, 8, 11
and 29 all hang off the same guard as item 6 (`chat.html:1819`, `if (t.name !== 'Bash') return;`),
so doing 6 alone and the rest later means opening the same two code paths twice — five items for
roughly the cost of one and a half. Conversely 29 is not optional: ship it with 6 or replay silently
drops what live now shows.

**Where the estimates are soft**, so nobody treats them as measured:
- ~~**9**~~ — **resolved 2026-08-05 while shipping item 10.** `interrupted` / `stderr` /
  `returnCodeInterpretation` are on `toolUseResult`, and the live stream event carries no
  `toolUseResult` at all (probed in `runIde`). So live can only colour the dot; the honest fix is
  replay-side, which makes this `S` rather than `M`. Anything else wanting live tool-result
  metadata now has its answer too — read the result TEXT or do without.
- **21** — sizing assumes retraction means removing already-rendered blocks by uuid. We have the
  uuid handle, but no local session has ever produced a `refusal_fallback`, so this can be built
  and not verifiable against real data.
- **22** — `?` on purpose. The doc's own note is that the empty ack at `ClaudeCli.kt:216` may be
  answering `hook_callback` with something the CLI reads as consent. That is a behaviour question,
  possibly a bug, and it has to be settled before any UI is worth sizing.

---

## A. Sub-agents & background work

### 1. Sub-agent internals
The tool calls, thinking and text a `Task`/`Agent` sub-agent produces while it runs.

- **Terminal:** ✅ nested lines under the `Task(...)` header, collapsible, with a
  "N tool uses · M tokens" tail.
- **VS Code:** ✅ `system/tool_progress` events carrying `parent_tool_use_id` + `repl_call` are fed
  to the parent tool card via `addProgress({innerToolUseId, toolName, toolInput, phase})`; stream
  events are routed with their `parent_tool_use_id` into the assembler.
- **Us:** ❌ nothing reads `parent_tool_use_id` or `isSidechain` anywhere in the plugin, and
  `tool_progress` isn't in the `switch` at `chat.html:1658`.
- **Take: P2.** Real work — needs a nesting model in the DOM and a matching replay path. Do it after
  the cheap items below, and note the latent hazard: because nothing *filters* on
  `parent_tool_use_id` either, if the CLI ever streams child text to us it would interleave into the
  main transcript undistinguished. A one-line guard is worth adding even before the feature.

### 2. Sub-agent final report
The summary the sub-agent hands back.

- **Terminal:** ✅ rendered, expandable.
- **VS Code:** ⚠️ the `Agent` renderer returns `renderOutput(){return null}` — it shows the prompt,
  not the report (the report arrives as parent text anyway).
- **Us:** ❌ parsed and thrown away by `chat.html:1819` — `if (t.name !== 'Bash') return;`.
- **Take: DONE 2026-08-05.** Fell out of item 6 for free exactly as predicted — `Agent` is not in
  the skip set, so its report renders. 3 in the verified session, up to 7.4k characters.

### 3. Sub-agent prompt
What the sub-agent was actually asked to do.

- **Terminal:** ✅ shown on expand.
- **VS Code:** ✅ `class tme … name="Agent"` renders header `Agent: <description>` plus an `IN` row
  containing the full `prompt`, click-to-open in an editor tab.
- **Us:** ❌ only `description` reaches the tool line; `prompt` is dropped.
- **Take: P2** (down from P1). Cheap — we already have the `IN` box idiom from Bash
  (`ioRow('IN', …)`) and this is one branch in `content_block_stop` — but cheapness is a reason to
  bundle it, not a reason to rank it. Sub-agents don't appear in most turns, so this doesn't clear
  the many-times-an-hour bar the tool-result items do. Ride it along with 1 and 4.

### 4. Background task roster
Which background tasks are running while the turn is suspended.

- **Terminal:** ✅ live list; `/bashes` for shells.
- **VS Code:** ✅ `subagentTasks` map driven by `system/task_started`, `task_progress`,
  `task_notification`.
- **Us:** ❌ `background_tasks_changed` sets a *count only* (`chat.html:1692`) so `onResult` can tell
  a suspend from the true end of a request. No names, no progress, no output.
- **Take: P2.** Ships naturally with item 1; a "2 tasks running" chip is a cheap interim.

### 5. Blank Task/Skill tool lines
`TaskUpdate`, `TaskOutput`, `TaskStop`, `Skill` have none of `RenderLimits.DESC_KEYS`, so they draw
an empty description — a tool line with nothing on it.

- **Terminal:** ✅ named and described.
- **VS Code:** ✅ bespoke renderers for `TaskOutput` (`task: "<task_id>"`) and `Skill`.
- **Us:** ❌ blank. `TaskUpdate` is the most frequent offender in local transcripts.
- **Take: DONE 2026-08-05.** Cheapest fix in the document, and it held: six keys into
  `RenderLimits.DESC_KEYS` and both paths got it at once, no per-tool code.
  **Measuring first corrected the item twice.** `TaskUpdate` carries `taskId` + `status`, not
  `activeForm` — that belongs to `TaskCreate`, which was never blank because it has `description`.
  And there were 54 locally, not the ~308 claimed. Added: `skill`, `status`, `taskId`, `task_id`,
  `function`, `uri`, with `status` before `taskId` because "in_progress" says what happened where an
  opaque id does not. **Every key was collision-checked first** — the chain is global, so a generic
  key would hijack other tools' lines; each of these is owned by exactly one tool.
  74 blank lines fixed: TaskUpdate 54, playwright `browser_evaluate` 15, TaskStop 2, Skill 2,
  getDiagnostics 1. Still blank BY DESIGN: Bash 458, AskUserQuestion 49, ExitPlanMode 11,
  TodoWrite 11 (wants item 14's checklist). `todos`/`plan` stay out — stringifying a structure into
  140 characters is worse than the blank it replaces.

## B. Tool results

### 6. Non-Bash tool result summaries
Read / Grep / Glob / Search / WebFetch / WebSearch / MCP calls all render a bare tool line for us.

- **Terminal:** ✅ "Read 120 lines", "Found 17 files", first lines + `… +N lines (ctrl+o to expand)`.
- **VS Code:** ✅ per-tool bodies — `Glob` → `Found N files` / `No files found`; `Grep` →
  `N lines of output`, click opens the full output in an editor tab; `WebFetch` → `Fetched from
  <url>`; `Read` → header only, deliberately (`body(){return null}`).
- **Us:** ❌ `chat.html:1819` again.
- **Take: DONE 2026-08-05**, though not in the shape this line proposed. Measuring first showed why
  "match VS Code" would have been wrong here: of ~2100 non-Bash results in local transcripts,
  **2033 are Edit/Write success boilerplate** — "The file … has been updated successfully" — which
  would have printed directly beneath the diff that had just shown the change.
  So the rule is STRUCTURAL rather than per-tool: **if a tool renders its own card, diff or
  description, its result text is a restatement** and is skipped (`RenderLimits.RESULT_SKIP`, shared
  by both paths through the same splice as the caps). Everything else gets an OUT box through the
  existing `ioRow`, capped by `OUT_MAX` with the truncation marker from item 10.
  **An error is never skipped** — a failure is not a restatement of a success. That exception earns
  its place: on one real session it surfaced 3 failed Edits whose reasons were being dropped
  ("String to replace not found in file"), while all 489 successful ones stayed quiet. Zero false
  either way.
  Verified on session `42d09b97`: Read 105 boxes, playwright 30, Agent 3 — all previously dropped.
  This also avoids VS Code's own oddity of showing NOTHING for `Read` (`body(){return null}`), which
  is the single most common result worth reading.

### 7. Tool input beyond one key
`RenderLimits.DESC_KEYS` picks exactly one field for the whole line.

- **Terminal:** ✅ pattern + path + flags.
- **VS Code:** ✅ `Grep` header is `Grep "pattern" (in <path>, glob: <glob>, type: <type>)`;
  `Read` appends `(lines 12-40)` from `offset`/`limit` and links the filename to that exact range.
- **Us:** ⚠️ a Grep shows its pattern but not path/glob/type; a Read shows the path but not the
  line range (`chat.html:1780`, `SessionStore.kt:519`).
- **Take: DONE 2026-08-05**, and it is ONE case, not the per-tool matrix this line assumed.
  Measured: **`Grep` and `Glob` — the examples above — do not occur in local transcripts at all.**
  Bash's `command` and Edit's `old_string`/`new_string` are already on screen in the IN box and the
  diff, and `prompt` on Agent/WebFetch is item 3. What was genuinely lost is `Read`'s line range:
  **347 of 719 Reads carry `offset`/`limit`**, and without it a Read of lines 40-80 rendered
  identically to a Read of the whole file — the difference between "Claude read this" and "Claude
  read a slice of this".
  Shipped as `RenderLimits.descSuffix` (mirrored by `descSuffix` in chat.html), rendered in its own
  `.t-sfx` span: OUTSIDE the `DESC_MAX` cap so a long path cannot eat the range, and outside
  `.t-desc.path` so the click handler does not send "(lines 40-80)" to the editor as part of the
  filename. Verified on session `42d09b97`: 95 of 105 Reads gained a range, 0 other tools did.
  **Clicking the path navigates to the range and SELECTS it** — showing a range but landing at line
  1 of a file Claude only read the middle of is half a feature, and VS Code links the filename to
  the exact range too. The numbers ride on the element as `data-line`/`data-end-line` rather than
  being parsed back out of the formatted string, so there is one encoding of the fact, not two.
  Both ends are clamped to the document: a transcript outlives the file it names, so a range
  recorded against a longer version selects what still exists instead of throwing.

### 8. Tool-returned images
Playwright screenshots, `Read` on a PNG — `toolUseResult.isImage`.

- **Terminal:** ⚠️ can't render images inline.
- **VS Code:** ✅ image results render.
- **Us:** ❌ dropped entirely.
- **Take: P3** (was P2), **measured 2026-08-05: `isImage` is set on ZERO local records.** The
  wiring claim still holds — we already decode base64 for user attachments — but there is
  nothing to wire it to, and it would be a third feature built blind. Revisit when a
  screenshot-returning tool is actually used; the guard it rides on (item 6) is now open, so
  it stays cheap.

### 9. Bash failure detail
We show the concatenated `tool_result` text and colour the dot.

- **Terminal:** ✅ error output distinguished, exit status implied.
- **VS Code:** ✅ error styling plus `stderr` handling.
- **Us:** ⚠️ was scored on the assumption that all four fields were being dropped. **Measured
  2026-08-05 across 2892 local Bash results, that was wrong on three of the four:**

  | Field | Records | Actually visible before this work? |
  |---|---|---|
  | `stderr` non-empty | 151 | **Yes** — the CLI merges stderr into the `tool_result` content, 151/151 |
  | failures (`is_error`) | 223 | **Yes** — red dot + the error text |
  | `noOutputExpected` | 29 | **Yes** — content reads `(Bash completed with no output)` |
  | `interrupted: true` | **0** | never observed on any line of any local transcript |
  | `returnCodeInterpretation` | 35 | **No** — 33 of 35 said something the output did not |

  The trap that produced the wrong score: a FAILED result stores `toolUseResult` as a **string**,
  not an object, so any survey filtering on a `stdout` key misses every failure and concludes
  failures aren't handled. They are.

- **Take: P3** (was P0), and **the real gap is DONE 2026-08-05.** The rule-3 justification does not
  survive the measurement — nothing here rendered a failure as a success, so this was never a
  "panel is wrong" item. What was genuinely missing is the CLI's exit-code explanation: shipped as
  `.io-note` (`↳ No matches found`), suppressed when the output already says it, which fires on 2
  real records. `interrupted` is carried too but is **built blind** — zero local records, so it has
  never been exercised against real data, only a synthetic fixture.
  Remaining and deliberately not built: distinguishing the stderr span inside the merged content.
  It is already on screen, so that is styling, not recovery — and it would risk duplicating text.

### 10. Truncated-output marker
We cut at `RenderLimits.OUT_MAX` (2000 chars) with no indication.

- **Terminal:** ✅ `… +N lines (ctrl+o to expand)`.
- **VS Code:** ✅ click-to-open shows the whole thing.
- **Us:** ❌ silent cut; `persistedOutputPath`/`persistedOutputSize` (the CLI spilling a big result
  to a file) is ignored too.
- **DONE 2026-08-05** (was P0, rule 3). A silent truncation was a correctness problem, not a
  cosmetic one — the user could not tell a short result from a clipped one, so the panel was
  actively asserting something false. Measured before the fix: 79 blocks in one local session.
  Shipped: `.io-cut` under the OUT and IN boxes and `.cmd-cut` under the permission card's command
  preview, reading `⋯ +312 lines · 12.4 KB not shown`. The counting rule lives once in
  `RenderLimits.cut` and is mirrored by `cutInfo` in chat.html, pinned by `RenderLimitsTest`.
  The CLI's own layer came with it: `persistedOutputSize` gives the true total and
  `persistedOutputPath` opens the spilled file, emitted only when the path still resolves.
  Details and the fold-vs-marker distinction in `docs/limits.md`.
- **Live/replay symmetry — PROBED 2026-08-05 in `runIde` (`seq 1 200000`): the live stream carries
  NO `toolUseResult`.** So the spill layer is replay-only, and the two paths disagree on exactly
  those records: replay says `1.2 MB total, not shown here — open full output`, live falls back to
  our-cut-only. The core marker was never at risk — both paths hold the full text when they cut —
  and the live code reads `ev.toolUseResult` defensively, so it will pick the fields up for free if
  the CLI ever sends them.
  · **This also answers item 9**, which carried the same unknown: `interrupted` / `stderr` /
    `returnCodeInterpretation` live on `toolUseResult` too, so an interrupted-command indicator is
    likewise replay-only unless it can be read from the result text. Re-size item 9 as `S`
    (replay-only) rather than `M`.
  · **Closed the same day.** The facts ARE available live, as prose: the CLI replaces an oversized
    result with a `<persisted-output>\nOutput too large (402.7KB). Full output saved to: <path>\n\n
    Preview (first 2KB):\n…\n</persisted-output>` wrapper. `RenderLimits.persistedOutput` parses it
    (mirrored in chat.html), so live now reports the same total and the same link as replay. Both
    paths unwrap it too, which fixed a second bug the probe surfaced: the raw tag and an unclickable
    path were being shown to the user on BOTH paths. Details in `docs/renderer-parity.md`.

### 11. "File was modified by the user"
`toolUseResult.userModified`.

- **Terminal:** ? not confirmed.
- **VS Code:** ✅ present in the result model.
- **Us:** ❌ read for nothing.
- **Take: P3** (was P2), **measured 2026-08-05: `userModified` is set on ZERO local records.**
  Item 6 has now opened the path it was waiting on, so it remains cheap — but a flag never
  once observed is not worth rendering on speculation. Same call as items 8 and 21's evidence.

### 12. Server-side tools
`server_tool_use` content blocks (web search executed API-side).

- **Terminal:** ✅ shown as a search line with result counts.
- **VS Code:** ✅ the stream assembler completes `server_tool_use` blocks alongside `tool_use`.
- **Us:** ❌ `content_block_start` (`chat.html:1726`) branches on `tool_use` and `thinking` only, so
  a server-side search renders literally nothing — a silent gap in the conversation.
- **Take: P2.** Small, and it removes an unexplained hole in the timeline.

## C. Session & account state

### 13. MCP server status
Which MCP servers connected, which failed.

- **Terminal:** ✅ `/mcp`, plus a startup warning on failure.
- **VS Code:** ✅ `get_mcp_servers` / `setMcpServerEnabled` requests behind a servers UI, and MCP
  servers appear in the usage-attribution panel.
- **Us:** ❌ the `initialize` response is destructured at `ChatPanel.kt:339` for `commands` and
  `models` only; everything else is discarded.
- **Take: split by the philosophy.**
  - **13a — a failure notice: P2** (down from P1). **DONE 2026-08-05.** A server that failed to
    connect was invisible, which reads as "the model ignored my tools" — the worst kind of bug
    report. That is a during-work signal with no terminal fallback (rule 1), so it earned a place:
    one status line naming the servers that didn't come up. Not P1, because it fires once per
    session, not many times an hour, and the user can already run `/mcp`.

    **The line above this one used to say "we already receive this in the `initialize` response".
    That was wrong, and the probe was worth running before the code.** Driving 2.1.222 with a
    `--mcp-config` naming a missing binary, the initialize *control response* contains no MCP data
    of any kind — its keys are exactly:

        account · agents · available_output_styles · commands · fast_mode_disabled_reason
        fast_mode_state · ide_rc_auto_enable_gate · models · output_style · pid
        remote_control_auto_enable · remote_control_auto_on_by_default

    The data is on the `system/init` **event** instead — `mcp_servers: e.mcpClients.map(o => ({name:
    o.name, status: rVt(o.type)}))` — which the same probe confirmed end to end, returning
    `[{name:"brokenstdio",status:"failed"},{name:"brokenhttp",status:"failed"}, …]`.
    **The correction changes the feature, not just the sourcing:** `system/init` is not emitted
    until the first user turn, so the notice cannot appear "at session start" as this item
    specified. It appears from the first turn onward. There is no earlier hook to hang it on.

    Status vocabulary is the wire enum `["connected","failed","needs-auth","pending","disabled"]`.
    Only `failed` and `needs-auth` are reported, and they carry different sentences because they
    have different fixes; `pending` is still connecting and `disabled` is deliberate, so announcing
    either would be a false alarm. Servers are grouped by fault rather than listed one per line —
    five dead servers from one bad config file is one fact, not five incidents. `MCP_BAD` is
    null-prototype, so an unfamiliar future status stays silent rather than matching an inherited
    key. The notice is keyed on the failing SET, so a re-init restates a genuinely different failure
    and keeps quiet about an identical one, and `clearLogUI` resets that key — otherwise a new
    conversation would inherit the old one's "already said that" and never mention its dead servers.
  - **13b — the server list with enable/disable: by design, don't build.** VS Code's
    `get_mcp_servers` / `setMcpServerEnabled` UI is configuration, and configuration is the
    terminal's half. Rebuilding it buys a second implementation that can only drift from
    `~/.claude`. This is the clearest case in the document of parity pointing one way and the
    philosophy the other.

### 14. Todo / task checklist
`TodoWrite`-style planning state.

- **Terminal:** ✅ checkbox list, re-rendered as items complete.
- **VS Code:** ✅ `class ime … name="TodoWrite"` renders header `Update Todos` + a real checklist
  (`todoListContainer`/`todoItem`/`completed` styles), and mirrors `input.todos` into a panel-level
  `todos` signal.
- **Us:** ✅ since 2026-08-05 — was a blank tool line with the list nowhere on screen.
- **Take: DONE 2026-08-05.** The philosophy case was right — the checklist is how you follow a long
  turn without reading every tool line, glanced at continuously, with no terminal equivalent you
  would switch windows for. Measuring narrowed it to ONE tool rather than a family:
  - The `Task*` family (TaskCreate 27, TaskUpdate 54) outnumbers `TodoWrite` (11) locally and looks
    like the newer generation of the same idea — but those are increments, not a list, and a
    checklist for them needs real aggregated state. Left as its own future item.
  - **`TaskList` needed nothing:** it returns the whole list in its RESULT (`#1 [completed] …`), and
    since it is not in `RESULT_SKIP`, item 6 already renders it.
  - `TodoWrite` was the genuine gap: the list is in the INPUT, and the result is boilerplate aimed
    at the model, so the tool line was blank and the list invisible.
  It sends the COMPLETE list on every call, so the newest call is the state — no aggregation, which
  is what made this self-contained exactly as the audit predicted.
  Rendered as `.todos` at the tool line's own 22px indent: completed dims and strikes, pending is a
  hollow circle, and the one in flight takes the accent and shows `activeForm` ("Wiring the marker
  into the webview") — the CLI's present-continuous phrasing, right for the item happening now and
  wrong for every other row.
  Verified against 11 real records in session `85174406`: all 11 now carry a checklist, 25 completed
  / 9 in-progress / 16 pending items across them.

  **CAVEAT, confirmed in `runIde` 2026-08-05: `TodoWrite` is RETIRED and cannot be triggered.** Every
  local `TodoWrite` record is CLI 2.1.178; every `TaskCreate` is 2.1.197/220/222. Asked directly, a
  2.1.222 session's own `ToolSearch` reports no `TodoWrite` in its roster — the CLI split it into
  four tools (`TaskCreate`/`TaskList`/`TaskGet`/`TaskUpdate`) with stable ids, dependencies and
  ownership, none of which a whole-list rewrite can express. The string survives in the binary but
  is not offered to the model.
  So what shipped serves **replaying older sessions**, which is legitimate but is not what "follow a
  long turn at a glance" meant.

- **14b — the live checklist, and the CLI hands it to us on a plate.** Found 2026-08-05: the Tasks
  system PERSISTS TO DISK, one JSON file per task, keyed by the session id we already track:

      ~/.claude/tasks/<sessionId>/<n>.json
      { "id": "2", "subject": "Write the data model", "description": "…",
        "activeForm": "Writing the data model", "status": "completed",
        "blocks": [], "blockedBy": [] }

  Verified against a live session: 13 files matching the task list exactly, updated as the turn ran.
  This retires both workarounds considered before finding it — parsing `TaskList`'s text output, and
  aggregating `TaskCreate`/`TaskUpdate` increments. Neither is needed: the file IS the state,
  structured, authoritative, and under the same `~/.claude` root `SessionStore` already reads
  (so `SessionStore.claudeHome` makes it testable against a temp tree, same as everything else).
  It also carries `blocks`/`blockedBy`, which the old `TodoWrite` had no way to express.
  **Shipped inline**, redrawn wherever a `Task*` call lands, so the list reads as that call's own
  content — the same placement everything else in the log uses.
  The two paths take DIFFERENT sources on purpose, and it is not drift:
  - **Live** asks Kotlin for the store after any `TaskCreate`/`TaskUpdate`/`TaskList`, because the
    file is the authoritative current state at that instant.
  - **Replay reconstructs** from the transcript's increments, because the store is overwritten in
    place and cannot say what the list looked like *earlier in the turn*. `TaskCreate`'s result
    carries the id the CLI assigned ("Task #3 created successfully: …"), `TaskUpdate`'s input
    carries `taskId`+`status`, and a `TaskList` result is a full authoritative resync that also
    repairs drift from records the replay window never shipped.
  Both feed the SAME `todoList` builder that `TodoWrite` uses, so provenance differs and rendering
  cannot. Verified on the real session `4f520694`: 8 snapshots reconstructed, the first showing one
  task, the middle all five pending, the last with task 2 completed — matching the live run exactly.

### 15. Compaction boundary
Where the context got compacted, why, and how big it was before.

- **Terminal:** ✅ a boundary marker + the summary.
- **VS Code:** ✅ `system/compact_boundary` → a compact message built from
  `compact_metadata.trigger` + `pre_tokens`, then the synthetic user summary is attached to it;
  `totalTokens` resets to 0.
- **Us:** ❌ was scored on "the conversation silently loses its history with no marker". **Measured
  2026-08-05 — 23 genuine `compact_boundary` records across local transcripts — and the truth was
  the opposite, and worse: the summary was not lost, it was shown and misattributed.** The CLI
  writes it as a `user` record carrying `isCompactSummary`, not `isMeta`, so the meta filter missed
  it and it replayed as an ordinary blue user box. Probed on a real session: **two boxes of 25,137
  and 41,013 characters, presented as messages the user had typed.** Same family as the sidechain
  finding already in `docs/renderer-parity.md`.
  The other two claims were narrower than stated: there was indeed no marker (`system` records were
  skipped entirely), but the gauge was stale for ONE turn rather than permanently — `message_start`
  usage corrects it on the next request.
- **Take: DONE 2026-08-05** (was P0). A boundary now replays as a marker —
  `↺ Conversation compacted · requested · 376k before`, from `compactMetadata.trigger` /
  `preTokens` — with the summary folded beneath it, paired by `parentUuid == boundary.uuid` (a real
  link, not adjacency). Both orphan directions are handled: a boundary with no summary still shows
  its marker, and a summary with no boundary still refuses to become a user box.
  The gauge resets on the boundary live, which was the item's original complaint and remains the
  part that matters most — the user has just asked for the context to shrink. **Verified in `runIde`
  2026-08-05: the chip went 34% → cleared the moment compaction finished, and the marker rendered
  live too — so `system/compact_boundary` IS on the live wire**, unlike the `toolUseResult` fields
  behind items 9 and 10.
  Self-inflicted, as the original take noted: `/compact` is a command we enabled, so we own the
  state it leaves behind.
  Before/after on session `42d09b97`: oversized user boxes 2 → 0, compact blocks 0 → 2.

### 16. Rate-limit warnings
Approaching / hit a usage limit, and when it resets.

- **Terminal:** ✅ warning line with reset time.
- **VS Code:** ✅ a top-level `rate_limit_event` type → `rateLimitWarning`, keyed by
  `status:rateLimitType` and dismissible.
- **Us:** ❌ the event type isn't in our `switch` at all; we only see the failure once it becomes an
  API error or an `is_error` result.
- **Take: DONE 2026-08-05**, and it is the clearest cautionary tale in this document about building
  from a guessed shape.
  Shipped first as "one status line", built blind. It fired within the hour in a live session — so
  the event is real and does reach us — and it was **WRONG**: the panel announced "Approaching a
  usage limit" on a `status:"allowed"` event, which is the ROUTINE case. It cried wolf about the
  user's own account.
  Two mistakes, both from guessing rather than reading: the payload is `rate_limit_info`, not
  `rate_limit`; and `allowed` must produce NOTHING. Corrected against the CLI binary's emission site
  (`type:"rate_limit_event",rate_limit_info:…`) and the VS Code validator (2.1.222), which supply
  the whole vocabulary: `status` (`allowed`/`rejected`/warning), `rateLimitType` keyed into real
  labels (`five_hour` → "session limit", `seven_day_opus` → "weekly Opus limit", …), `utilization`,
  and `resetsAt` as UNIX SECONDS shown relatively ("in 4h") because an absolute clock time would
  imply precision the value does not have.
  Also deduped by `status:rateLimitType` the way VS Code does — it keeps ONE dismissible warning,
  while we append to a log, so without the guard an unchanged limit restates itself every turn.
  Verified: routine event silent · "You've used 82% of your session limit · resets in 4h" ·
  repeat not restated · "You've hit your weekly Opus limit" · `rejected` with no type stays quiet.

  **It cannot be verified against a record, and that is by the CLI's design**, not an accident of
  local history: the binary carries `"[sdkMessageAdapter] Ignoring rate_limit_event message"`, and a
  sweep of the project where it fired live found zero genuine records. So the shape rests on the
  binary's emission site plus VS Code's validator, and the code is written to fail in the safe
  direction instead: an unrecognised payload yields no `status`, which produces SILENCE.
  Confirmed by replaying the exact wrong shape that misfired live — it now renders nothing.
  Both spellings of the payload key are accepted (`rate_limit_info` / `rateLimitInfo`), because the
  CLI's emission sites are inconsistent about case; that is the same trap that hid the refusal
  fallback behind `originalModel` vs `original_model`.
  **The principle worth keeping:** for a claim about the USER'S OWN ACCOUNT, silence beats a guess.
  The first version had this backwards — its fallback was a generic warning, so a shape it did not
  understand became an assertion that was false.

### 17. Cost
`result.total_cost_usd`, `duration_api_ms`, `num_turns`, `modelUsage`.

- **Terminal:** ✅ `/cost` and an exit summary.
- **VS Code:** ✅ `total_cost_usd` → `usageData.totalCost`, with `modelUsage[model]` supplying the
  context window and max output tokens.
- **Us:** ❌ `onResult` (`chat.html:2000`) uses wall-clock + output tokens only.
- **Take: P3**, and the philosophy agrees with the existing deferral: cost is checked occasionally,
  `/cost` answers it, and a spend dashboard is not something you reach for while writing a line of
  code. Keep it on the DEFERRED list rather than promoting it.
- **17a — `modelUsage[].contextWindow`: P2, and separate from the above. DONE 2026-08-06.** This is
  not a usage display; it is a correctness fix for a feature we already ship. The context gauge
  derived its denominator by sniffing `[1m]` tags on the model id, with a promote-on-overflow guard
  for unknown models. The CLI hands us the real window, so the gauge now takes it and the heuristic
  is demoted to a seed.

  **Probed before building (2.1.222), and the payload had a trap the item did not mention.** The
  per-model schema is real — `contextWindow: w.number().int()` is a REQUIRED field alongside
  `maxOutputTokens`, `canonicalModel` and `provider` — but `modelUsage` is a **map, not a value**,
  and a single ordinary turn came back with two entries:

      "claude-opus-5[1m]":         contextWindow 1000000   canonicalModel "claude-opus-5"
      "claude-haiku-4-5-20251001": contextWindow  200000   canonicalModel "claude-haiku-4-5"

  The CLI runs small side models for its own errands, so the map routinely describes models the user
  never selected. Taking "the" window from it — first entry, or max, or last — would have set the
  denominator to a fifth of the truth on any turn where a side model happened to sort first, and
  driven the gauge past 100% while looking authoritative. The match is therefore to OUR model
  specifically: raw key first (it carries the `[1m]` tag exactly as `currentModel` does), then
  `canonicalModel`. **No match means no update** — the tag heuristic is a reasonable guess and a
  confident wrong number is worse than a guess.

  Two consequences of the sourcing, both accepted rather than worked around:
  - `modelUsage` rides on the **`result`** event, so the earliest it can speak is the end of the
    first turn. There is no window figure in the initialize payload at all (its keys were listed
    while probing 13a). The `[1m]` seed is what covers the gap until then, which is why it stays.
  - The read sits ABOVE `onResult`'s background-task early return, so an intermediate result from a
    suspended turn still refines the window instead of waiting for the turn to finish.

  The promote-on-overflow guard in `ctxWindow()` stays as well: it costs nothing and still covers an
  untagged future model on its first turn, before any `result` has arrived.

### 18. Token breakdown / usage panel
Input vs. cache-read vs. cache-creation vs. output, and what's eating the window.

- **Terminal:** ✅ `/context` breakdown.
- **VS Code:** ✅ a usage panel attributing consumption to agents, skills, plugins and MCP servers.
- **Us:** ⚠️ those numbers feed the context gauge denominator and nothing else.
- **Take: P3.** Same deferral as 17.

### 19. Real thinking-token count
- **Terminal:** ✅ live token count while thinking.
- **VS Code:** ✅ `system/thinking_tokens` → `estimated_tokens`, formatted `1.2k tokens`.
- **Us:** ⚠️ we show a number, but it's our own `chars/4` guess (`chat.html:1761`) — the CLI is
  telling us the real one and we ignore it.
- **Take: DONE 2026-08-05.** `system/thinking_tokens` now sets the count and `chars / 4` remains
  only as the fallback, through one `paintThinkTokens()` so the real figure and the guess cannot
  render differently.
  **BUILT BLIND, but the absence here is not evidence.** Zero genuine records exist locally — yet
  this is a LIVE event that is never written to the transcript (same class as `rate_limit_event`),
  so transcript silence says nothing about whether it fires. The binary carries the emission site
  with the exact shape: `subtype:"thinking_tokens", estimated_tokens, estimated_tokens_delta, uuid`.
  Contrast `interrupted` / `isImage` / `userModified`, which are transcript FIELDS — there, absence
  across thousands of records IS evidence. Worth keeping the two cases apart when scoring.
  The subtle part was the reset: `thinkTokReal` is cleared everywhere `thinkTok` is, or a real count
  from one block leaks into the next block's fallback and reads as fact. Verified headless — a new
  block with no event falls back to ~200 rather than inheriting the previous 137.

### 20. Account / plan / auth
- **Terminal:** ✅ `/status`, `/login`.
- **VS Code:** ✅ shows login state; handles `assistant` events with `error === "authentication_failed"`
  by opening the login flow.
- **Us:** ❌ `account` from the `initialize` response is dropped at `ChatPanel.kt:339`.
- **Take: split by the philosophy.**
  - **20a — the `authentication_failed` branch: P1** (up from a P3 aside). Right now an auth
    failure is an opaque error block with no route out of it, and the route out is *precisely*
    what the philosophy prescribes: catch the branch and say "run `claude` in a terminal and sign
    in". VS Code opens its own login flow here; we deliberately don't, so the message carries the
    whole fix. Being terminal-only makes this MORE important to handle, not less — an unexplained
    auth error in a plugin with no login UI is a dead end.

    **Verified 2026-08-05 against 2.1.222** — it had shipped BLIND, on the same basis that made
    item 16 cry wolf, and an account claim is the wrong place to guess. The field was right and two
    details around it were wrong:
    - ✅ **`ev.error` on the `assistant` event is correct.** The CLI serializes it onto every
      assistant frame unconditionally — `yield {type:"assistant", …, timestamp:n.timestamp,
      error:n.error, …}` — and VS Code's shipped consumer reads the identical thing:
      `if (e.error === "authentication_failed") this.context.showLogin()` in `webview/index.js`.
      Note the split: `extension.js` has **zero** occurrences, so grepping only the host half of the
      bundle would have wrongly concluded VS Code doesn't handle this at all.
    - ❌ **`ev.subtype === 'authentication_failed'` was a guess and is gone.** Assistant frames carry
      no `subtype`; the wire schema's neighbours are `is_meta`, `is_virtual`, `is_api_error_message`,
      `api_error_status`, `api_error`, `error_details`.
    - ❌ **`oauth_org_not_allowed` was missing** — the sibling account-blocked code, sitting directly
      beside `authentication_failed` in the CLI's own router. Its fix is the opposite of ours:
      *"org disabled OAuth — use API key or ask admin"*. Telling that user to sign in again is advice
      that cannot work.
    - ❌ **The wording asserted a cause it can't know.** `authentication_failed` also covers expired
      AWS/GCP credentials and managed keys (`Ju({error:"authentication_failed", content: …})` is
      raised from all of them), where "Not signed in" is simply false. The line is now ROUTING, not
      diagnosis — the CLI's own specific message still arrives immediately after as the `result`
      error block, so restating the cause here could only contradict it.

    Scope held deliberately: the other **eight** codes in the enum (`rate_limit`, `overloaded`,
    `billing_error`, `server_error`, `invalid_request`, `model_not_found`, `max_output_tokens`,
    `unknown`) are transient or request-shaped, not account-shaped. The `result` error block already
    states them and offers Retry; routing them through our table would replace a specific CLI
    message with a vaguer one of ours. `AUTH_BLOCKED` in chat.html holds the two that qualify, and
    is null-prototype so an unknown code can only miss — a plain literal answers
    `AUTH_BLOCKED['constructor']` with a truthy function and renders it as the fix.

    Still unverified, and honestly so: **no local transcript contains a single `isApiErrorMessage`
    record**, so neither this branch nor the replay path at `SessionStore.kt:632` has been observed
    against real data. What is confirmed is the contract both are written to, which is as far as
    reading the binary can take it.
  - **20b — account / plan display: by design, don't build.** `/status` answers it, it is read
    occasionally, and it is the login half of the split.

### 21. Model refusal fallback
- **Terminal:** ? not confirmed.
- **VS Code:** ✅ `system/model_refusal_fallback` + `refusal_fallback` message type, with
  `retracted_message_uuids` retracting the already-rendered blocks.
- **Us:** ❌ unhandled — retracted content would stay on screen.
- **Take: P3** (was P0 on severity), **measured 2026-08-05 and not built.** Two reasons, both of
  which invalidate the original take:
  1. **Zero genuine records.** Not one `refusal_fallback` or `retracted_message_uuids` exists in any
     local transcript. A substring grep appears to find ~15, but every hit is this repo's own
     session transcript containing the *docs text* being written about the feature — the same
     "merely mentions the token" trap `RenderLimits.persistedOutput` carries a guard for. Check by
     KEY, not by substring.
  2. **"Cheap to handle" is wrong.** Dropping blocks by uuid presumes we know which DOM node is
     which uuid, and **no renderer records one**. Only `reqSeed` — a request's first assistant uuid,
     kept for the summary verb — survives. Retraction needs a uuid→DOM map threaded through every
     block type: live text, thinking, tool lines, cards. That is cross-cutting infrastructure, not
     a branch in the stream `switch`.
  **BUILT ANYWAY, 2026-08-05, on your call — and flagged BUILT BLIND wherever it appears.** What
  changed the calculus: the failure mode of guessing the wire shape wrong is *does nothing*, which
  is the status quo, not corruption. Shipped:
  - Blocks carry the record uuid (`stampMessage` in chat.html; `Item.uuid` on replay), which is the
    infrastructure the old estimate said we lacked. Rendering is driven by stream deltas that have
    no uuid, so blocks are collected and stamped when the whole-message `assistant` record lands.
  - `system/model_refusal_fallback` → a notice, and the model chip FOLLOWS `fallback_model` through
    the display-only path (`setModelChip`), not `setModel` — the CLI has already switched, so
    bridging a `model` message back would tell it what it just told us.
  - **Both** eviction lanes: the refusal's `retracted_message_uuids`, and an assistant record's own
    `supersedes` array — a second door we had not accounted for at all.
  - Late arrivals: a uuid retracted before its block exists is remembered, so the block never appears.
  - Two guards on the only path that can do harm: exact `data-uuid` matches only, and **a user's own
    message is never withdrawn** — the model cannot take back what the human typed.
  - Withdrawal is ANNOUNCED ("2 messages withdrawn"). Content vanishing unexplained is the same
    defect as a silent truncation; see `docs/limits.md`.

  **What VS Code does that we deliberately do not:** a settings gate ("when off, your session will
  pause instead") and a `refusal_fallback_prompt` dialog behind an experiment flag. Both are
  configuration — the terminal's half (CLAUDE.md § Philosophy). It also repairs `replayInsertIndex`
  and `teleportedMessageCount` after eviction; we have no equivalent indices.

  **Verification pass (2026-08-05, same day):** grepping the CLI binary itself (2.1.222) found every
  wire field — `model_refusal_fallback` ×38, `retracted_message_uuids` ×7, `supersedes` ×18 — so the
  shape is the CLI's own, not just the webview's reading of it. Two findings with teeth:
  - **Spelling split, fixed:** the CLI's emission site writes camelCase (`originalModel`,
    `fallbackModel`) while the VS Code validator reads snake_case — the same split
    `compactMetadata`/`compact_metadata` has. Both paths now accept both spellings, pinned by a
    camelCase fixture case; guessing one spelling is how a built-blind handler dies silently.
  - **The CLI's own doc string for `supersedes`** (verbatim from the binary): "Wire uuids of
    previously-delivered messages that this message replaces (refusal-fallback supersede). The list
    can include tombstoned tool_result frames from the refused leg, not only assistant frames.
    Evict the named messages on arrival… Idempotent." Tool-result uuids match nothing we stamp, so
    those degrade to no-op — benign, and now known rather than discovered.

  **Second verification pass (2026-08-06)** — the first pass checked that the fields we *used*
  existed; this one read the whole wire schema for the fields we did **not** use. It found a real
  bug and a whole missing branch, which is the argument for verifying a BLIND item even after it
  looks confirmed:
  - **`scope` was ignored, and that made the panel lie.** The schema documents it in its own words:
    *"'session': the main thread fell back and the session model is swapped. 'local': a subagent /
    side-question (/btw) / background fork fell back — only that response came from the fallback
    model and the session model is unchanged. Absent from older CLIs (treat as 'session')."* We
    followed `fallback_model` into the model chip **unconditionally**, so a subagent falling back
    silently repainted the composer and left it there — every later turn then reported a model the
    session had never switched to, and nothing would ever correct it. Both paths now honour `scope`,
    defaulting to `session` exactly as the schema instructs, and the local case gets its own sentence
    ("That response came from X; the session model is unchanged") because claiming a switch that did
    not happen is the same falsehood the chip was telling. Retraction stays scope-independent.
  - **`model_refusal_no_fallback` was dropped on the floor.** A separate subtype, described as
    *"emitted when the model ends the stream with stop_reason 'refusal' and no retry runs: no
    fallback model is configured, or per-category routing declined the retry"*. It carries
    `original_model` and `content` but **no** `fallback_model` and **no** retractions — there is
    nothing to follow and nothing to evict, and the entire job is saying the turn ended and why.
    Unhandled, the panel simply stopped. Now rendered on both paths, with our own sentence behind
    `content` because one emission site really does send `content:""`.

  Note the shape of both misses: neither is a wrong field name, which is what the first pass looked
  for. They are fields that exist, that we never read, and whose absence changes what the panel
  *claims*. `direction` (`retry`/`revert`/`sticky`) is still unused, and deliberately so — we follow
  whatever `fallback_model` names, whichever direction it moved.

  **Still never OBSERVED in a live session or transcript**, and that will not change until a refusal
  actually occurs. Tests are synthetic; the live path was driven headless through `onClaudeEvent`.

### 22. Hook activity
- **Terminal:** ✅ hook output and blocked-tool reasons.
- **VS Code:** ? no `hook_` markers in the webview bundle; likely handled extension-side.
- **Us:** ❌ `ClaudeCli.kt:216` acks *every* non-`can_use_tool` control request with an empty
  response. A hook that fires, blocks, or errors is invisible.
- **Take: P2.** Note this isn't purely a display gap — the empty ack may be answering
  `hook_callback` with something the CLI reads as "no opinion". Worth probing before building UI.

### 23. CLI stderr
- **Terminal:** — it *is* the terminal.
- **VS Code:** ? extension-side.
- **Us:** ❌ `ClaudeCli.kt:138` sends it to the IDE log only.
- **Take: P1** (up from P2). Textbook rule 1: this is the one row where the Terminal column is `—`
  because *we are the terminal* for this session. There is no `/command` that recovers it and no
  other client to defer to — if we don't print it, the reason is gone. Today a CLI crash reads as
  "claude process exited (1)" with the actual cause buried in idea.log, which a normal user will
  never open. Surface stderr alongside the existing `__exit` status line on a non-zero exit.

### 24. Queued messages
Messages typed while a turn is in flight.

- **Terminal:** ✅ queued and displayed; the CLI writes `queue-operation` records.
- **VS Code:** ❌ no queue UI in the bundle (every `queue` hit is React internals).
- **Us:** ❌ no queue at all.
- **Take: P2** (up from P3). The old reasoning was "VS Code doesn't do it either" — but parity is
  not the rule, the philosophy is, and this is the row where the two most clearly part company.
  Having a follow-up thought while Claude works is a many-times-an-hour event for anyone actually
  coding, which is exactly the test. The terminal queues; we drop the keystrokes on the floor. The
  CLI already writes `queue-operation` records, so the concept exists on the wire.
  Not P1 only because it needs real composer state (a pending list, edit/cancel before it sends),
  not a renderer branch.

## D. Replay-only gaps

### 25. Bookkeeping records
`SessionStore.readTranscript` handles `user` and `assistant` only. Never replayed: `attachment`
records (`task_reminder`, `deferred_tools_delta`, `agent_listing_delta`, `skill_listing`,
`command_permissions`), `mode`, `queue-operation`, `last-prompt`.

- **Terminal / VS Code:** ❌ both hide these too.
- **Take: P3.** Correct as-is. Listed so nobody "discovers" them later and assumes they're a bug.

### 26. Per-record metadata
`effort`, `attributionSkill`, `permissionMode`, `gitBranch`, `version`, `cwd` sit on nearly every
record.

- **Terminal:** ⚠️ model + branch in the status line.
- **VS Code:** ⚠️ model shown; the rest not.
- **Us:** ❌ nothing — a resumed session can't tell you which model, effort or branch a turn ran on.
- **Take: P3** as chrome, but `effort`/model per turn would be genuinely useful on long resumed
  sessions. Park it.

### 27. Injected IDE context
`<ide_opened_file>`, `<local-command-stdout>`, `<task-notification>` — stripped by
`cleanInjected()` (`SessionStore.kt:641`).

- **Terminal / VS Code:** ❌ hidden there too.
- **Take: P3.** Keep stripping.

### 28. Off-window history
Windowed replay ships the newest ~250 blocks; earlier turns aren't in the DOM until you scroll up,
so browser find can't see them.

- **Terminal / VS Code:** ✅ full scrollback.
- **Us:** ⚠️ deliberate — it's what makes a big session open fast (`docs/limits.md`).
- **Take: P3.** Keep — the windowing is what makes a big session open fast, and scrolling back
  through old turns is not an hourly act.
  Worth flagging the adjacent feature, though: **find-in-conversation** *would* pass the
  philosophy's test (searching what Claude just did, mid-task, with no terminal equivalent — the
  CLI can't search our rendered view). It isn't listed as its own row because no client-parity gap
  drove it, but if it is ever built, this row becomes its blocker: the finder has to drive the
  chunk loader or it will only ever search the visible tail.

### 29. Non-Bash tool output on replay
Replay keeps `structuredPatch` (edits) and `answers` (ask cards); `stdout` is kept for Bash alone
(`SessionStore.kt:558`).

- **Take: DONE 2026-08-05**, shipped in the same change as item 6 — the skip list lives in
  `RenderLimits.RESULT_SKIP` and is spliced into the webview, so the two paths agree by
  construction rather than by discipline.

## E. Deliberate mutes

### 30. Silent `/effort` turns
The stream, echo and summary of an effort change are suppressed live via `effortMuted`
(`chat.html:1654`); they reappear on resume because the transcript records them.

- **Take: P3.** Already decided and documented in `CLAUDE.md` — kept as an honest audit trail.

---

## Suggested order

Four tiers, in order. The first is not a parity exercise — it is fixing things the panel currently
states incorrectly, and it should go first even though some of it is less visible than tier 2.

**Tier 0 — the panel is wrong (rule 3). CLEARED 2026-08-05.** All four items are resolved, but only
two of them turned out to be the bug the tier claimed. Worth reading before scoring anything `P0`
again:

- ~~Truncation marker (10)~~ — **real, and shipped.** Left two things the rest of the doc can reuse:
  `RenderLimits.cut` + `cutInfo` as the pattern for a rule that must hold in both languages, and a
  balloon on a failed `open` so no click in the log is silent.
- ~~Compaction boundary (15)~~ — **real, shipped, and worse than described.** The claim was that
  history vanished silently; in fact the CLI's summary was being SHOWN, as a 25k–41k character blue
  user box the human never typed. The gauge reset — the part the original take called "the bug" —
  was the smaller half.
- ~~Bash `interrupted` / `stderr` (9)~~ — **premise was wrong; re-scored P0→P3.** `stderr` was never
  dropped (the CLI merges it into the content), failures already rendered, `interrupted` has never
  occurred. Only the exit-code explanation was genuinely missing, and that shipped.
- ~~Retraction on `refusal_fallback` (21)~~ — **premise was wrong; re-scored P0→P3, not built.**
  Zero genuine records, and "cheap to handle" presumed a uuid→DOM map that does not exist.

**The lesson, in one line: two of the four `P0`s described bugs that were not there, and the one
that was real was mis-described.** A rule-3 score says "the panel states a falsehood" — that is a
factual claim about behaviour, and it is cheap to check against `~/.claude/projects/*/*.jsonl`
before it is expensive to build against. Check by KEY, not substring: item 21's phantom evidence was
this repo's own transcript quoting the field names, and item 9's "no failures" reading came from
filtering on a `stdout` key that failed results do not have.

**Tier 1 — the review loop (many times an hour).** The panel's core job: seeing what Claude did.
Items 5, 6, 2 and 29 shipped 2026-08-05; 8 and 11 were re-scored out on zero evidence.

- ~~Item 5~~ — **done.** Six keys into `RenderLimits.DESC_KEYS`, 74 blank tool lines fixed, and the
  note describing it turned out to be wrong twice (see the item). Best ratio in the document, as
  claimed.
- ~~The item-6 bundle~~ — **done, but NOT as this line planned it.** The coupling claim held (6, 2
  and 29 landed in one pass through one shared skip list), the *shape* claim did not: "match VS
  Code" would have printed ~2033 lines of Edit/Write success boilerplate under diffs that had just
  shown the change. The rule that survived measurement is structural — skip the tools whose outcome
  is already on screen, show everything else, and never skip an error.
  Items 8 and 11 were supposed to ride along free. They can't: `isImage` and `userModified` are set
  on **zero** local records. The guard they need is now open, so they stay cheap whenever a real one
  appears.
- ~~Per-tool input suffixes (7)~~ — **done.** One case, not a matrix: `Grep`/`Glob` never occur
  locally, and only `Read`'s line range was genuinely lost. Clicking the path now selects it.
- ~~Todo checklist (14)~~ — **partly.** `.todos` ships and replays correctly, but the tool it renders
  is RETIRED: `TodoWrite` is not in a 2.1.222 session's roster at all. It serves old sessions only.
  The live-relevant checklist is still open — see item 14 for the cheap `TaskList` step and the
  L-sized `TaskCreate`/`TaskUpdate` running list.

**TIER 1 IS CLEAR** — with one asterisk: item 14's renderer works but its TOOL is retired, so the
live checklist remains open (see the item). Every other P1 is shipped. What the tier taught, twice over: the
coupling estimates held, the SHAPE estimates did not. Item 6 would have printed 2033 lines of
boilerplate if built to spec, item 7 was sized as a per-tool matrix and was one case, and item 14's
family turned out to be three tools with three different answers. Measure the shape, not just the
size.

**Verifying a BLIND item is itself a task, and it pays.** Three items shipped flagged BUILT BLIND
(19, 20a, 21) because the data to check them against does not exist locally. 20a was verified on
2026-08-05 by reading the 2.1.222 binary instead of the transcripts: the field it reads was right,
and two other things about it were wrong — a `subtype` check for a field that does not exist, and a
missing sibling code whose correct advice is the opposite of ours. Neither would have surfaced from
use, because both fail *silently* in the direction of saying nothing or saying something plausible.
Two habits that did the work, both worth repeating:
- **Grep the whole bundle, not the half you expect.** `authentication_failed` appears **zero** times
  in VS Code's `extension.js` and once in `webview/index.js`. Checking only the host would have
  "proved" VS Code doesn't handle it.
- **Prefer the serializer to the schema.** The zod wire schema lists `is_api_error_message` /
  `api_error_status` / `api_error` / `error_details` and no plain `error`, which reads as though
  `ev.error` were dead. The emission site settles it — `error:n.error` is written onto every
  assistant frame. When the two disagree, the code that runs wins.

Item 21 was then verified TWICE, and the second pass is the instructive one. The first checked that
the fields we *used* existed, and cleared them. The second read the schema for the fields we did
**not** use, and found a bug plus a missing branch: `scope` was ignored, so a subagent's fallback
permanently repainted the session's model chip, and `model_refusal_no_fallback` — the refusal that
is never retried — was dropped entirely. Neither is a wrong field name, which is all the first pass
was looking for. **The dangerous field is not the one you spelled wrong; it is the one you never
read.** For a handler that makes a CLAIM — which model answered, whether you are signed in, whether
your tools loaded — the unread field is what turns a missing feature into a false statement.

**Tier 2 — signals with no terminal fallback (rule 1).** All small, and each converts a dead end
into something actionable. Together they are less work than item 6 alone; a good tier to take when
there isn't a long sitting available.
- Auth failure → "sign in from a terminal" (20a) `XS` — the whole fix is the message. `XS` held up
  for the code; the *verification* cost more than the branch did, and found two defects in it.
- ~~Rate-limit warnings (16)~~ — **done**, one status line through the existing `statusLine()`, but
  only after the first cut cried wolf on `status:"allowed"`, the routine case. Caught in use.
- ~~CLI stderr on non-zero exit (23)~~ — **done.** The cause used to be in idea.log only.
- ~~MCP connection failures (13a)~~ — **done.** A notice, explicitly not a server-management UI
  (13b). `S` was right; the *sourcing* in the item was not, and only a probe could tell — see 13a.

**TIER 2 IS CLEAR.** Its size estimates were the most accurate in the document — every item came in
at `S` or `XS`, and none grew in the building. What they got wrong was consistently the *source*:
16 guessed the field name, 20a guessed a second field that does not exist, and 13a named the wrong
payload entirely. Three of four small items were sized correctly and sourced incorrectly, which is
an argument for probing the wire before writing the branch, not for padding the estimate.

**Tier 3 — depth.** Ordered cheapest-first, since none of it is urgent:
real thinking tokens (19) `XS`, ~~`modelUsage[].contextWindow` (17a)~~ **done** — retired the `[1m]`
sniffing, server-side tool blocks (12) `S`, the sub-agent prompt (3) `S`, a "2 tasks running" chip
(4) `S`. Then the two genuinely large ones, each needing a design pass before any code: sub-agent
nesting (1) `L`, which 3 and the full roster (4) should ride on, and queued messages (24) `L`,
which is composer state rather than a renderer. Hook activity (22) is unsized — settle the
empty-ack question first, because it may be a protocol bug rather than a display gap.

**Not on the list at all:** 13b and 20b are ruled out by the philosophy, not deferred — if a
release description mentions them, they belong under "By design". Items 17, 18, 25–28 and 30 stay
P3 for the reasons in their sections.


---

## 31. API retry storms — `system/api_error` (found 2026-08-05, not in the original audit)

Surveying the `system` record subtypes for tier 2 turned up six the parser skipped entirely. One of
them matters: `api_error`, carrying
`{error:{message, formatted}, retryInMs, retryAttempt, maxRetries, source:"request_retry"}`.

- **Us:** ❌ was skipped with every other `system` record. Session `42d09b97` alone holds **20 of
  them — a `529 Overloaded` storm retried ten times** — during which the panel showed nothing at
  all. The turn simply appeared to hang.
- **Take: DONE.** Textbook rule 1: the CLI is retrying inside OUR process, so there is no terminal
  to check and no other client to defer to. Rendered as a status line on both paths —
  `529 Overloaded — retrying (3/10)`.

**The other five subtypes, deliberately left alone:** `turn_duration` (11) duplicates our own
completion summary · `local_command` (7) is the `<local-command-stdout>` family `cleanInjected`
already strips · `away_summary` (2) and `informational` (1) are one-off notices · **`stop_hook_summary`
(1) is real evidence for item 22** (hook activity), which the audit could not size — it carries
`hookCount`, `hookInfos`, `hookErrors` and `preventedContinuation`, so that item is no longer
unsizeable, only unbuilt.
