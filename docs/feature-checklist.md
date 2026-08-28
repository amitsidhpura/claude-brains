# Claude Brains — feature parity checklist

What the plugin (`plugin/`) has, what it could have, and what it has decided not to have —
one row per feature, measured against both reference clients.

**References** (both on 2.1.250, last full re-audit 2026-08-28)
- VS Code extension — `~/.vscode/extensions/anthropic.claude-code-2.1.250-linux-x64/` (the 2.1.246 dir
  was still on disk 2026-08-28 and served as the diff base; the local `vscode/` extraction is
  still 2.1.241 — re-extract when a diff needs it)
- Terminal TUI / CLI — `~/.local/share/claude/versions/2.1.250`; headless roster in `docs/slash-commands.md`
- Data-level parity audit (`docs/client-parity.md`) was closed 2026-08-06 and deleted 2026-08-28; the
  not-taken wire vocabulary lives in `docs/ide-mcp-protocol.md` § 11

**At a glance** (2.1.250, 2026-08-28) — 77 ✅ · 4 🟥 · 7 🟧 · 16 ⬜ · 18 ➖ · 6 🚫 (128 rows)
- **Next up (🟥):** 8.7 Rewind / checkpoints + fork conversation · 8.11 Side question · 8.14 Reloaded-webview log replay · 11.3 Kill a background process from the panel
- **Awaiting a decision ([DECIDE]):** 8.7 Rewind / checkpoints + fork conversation · 8.10 Session groups / sessions sidebar · 8.11 Side question · 12.3 Open in editor tab · 12.6 Focus view · 13.2 JSON schema for .claude/settings.json · 14.2 Git actions in the host

**Status marks**

| Mark | Meaning |
|---|---|
| ✅ | done |
| 🟡 | partial |
| 🟥 | open — high: next up, or the strongest candidates awaiting a decision |
| 🟧 | open — medium: roadmap, worth a pass when its turn comes |
| ⬜ | open — low: watch / probe first / polish; some lean ➖ |
| ➖ | by design — the terminal's half, or N/A in JetBrains |
| 🚫 | declined by the user (revivable; recorded in `.claude/context/`) |

**Numbering** — rows are `section.row` (`8.5`); refer to them by that id. Numbers are stable
between audits: retire a row by striking it, not by deleting it, so ids never shift.

**Effort** (on open and partial rows, after the mark): `[XS]` a few lines through an existing
idiom · `[SM]` one branch or helper, fits in a sitting · `[MD]` a new renderer, or a change that
must land in both live and replay · `[LG]` needs a design pass, a wire probe, or state that
outlives one event.

**Row tags**

| Tag | Meaning |
|---|---|
| **[NEW]** | new or newly noticed in a re-audit (2.1.233 audit 2026-08-17; the 2.1.241 audit 2026-08-23 added only 14.4; the 2.1.246 audit 2026-08-26 added none; the 2.1.250 audit 2026-08-28 added only 1.25) |
| **[DECIDE]** | open row awaiting the user's yes / later / no (yes → `state.md`, later → `backlog.md`, no → re-mark ➖/🚫) |

**Scope rule** — *"Develop in the IDE. Configure in the Terminal."* Reached for many times an
hour while writing code? Yes → panel (🟥🟧⬜ until built). No → terminal (➖).

<details><summary><b>Re-audit 2026-08-28 (2.1.246 → 2.1.250)</b></summary>

Surface: nothing new on the extension side — `package.json` contributions differ only in
`version`/`size`/`installedTimestamp`, and the 12-tool IDE-MCP roster is the same twelve
`tool("…")` registrations (diffed against the 2.1.246 dir, still on disk). The CLI's typed control
vocabulary grew four `@internal` cloud-worker subtypes (`remote_tools_announce` / `_probe`,
`remote_tool_call`, `remote_plumbing_call` — gated on `tengu_violin_wood`, "absent outside cloud
workers"; not host-facing). A headless `initialize` on both binaries: the same 15 top-level keys,
the same 5-model roster with identical capability flags, the same 6 agents and `account` keys;
the command roster grew exactly one entry, the bundled skill `/workflow-authoring` (53 → 54; no
drops, no hint or description changes) — added to `docs/slash-commands.md` as Hidden/unverified.
Behaviour (upstream CHANGELOG 2.1.247/248; 2.1.250 is "bug fixes"): `--restricted` launch flag,
`SendFeedback` tool + `/feedback`, cross-session `SendMessage`/`ListAgents`, `/usage-credits` for
Enterprise, agent-frontmatter `experimental.cacheTtl` — every one is the terminal's half or a
tool block the generic renderer already draws; no row. The one thing worth a row is in the VS
Code WEBVIEW: it now renders a usage-limit **grace** banner off `rate_limit_event`
(`rateLimitGraceActive` + `overageStatus`, fields the CLI has emitted since ≤ 2.1.246; behind
`tengu_lantern_sconce`) — new row 1.25. Also noticed, no row: the webview parses the CLI's
`/model` confirmation copy (`Set model to …` is new at 2.1.250, `Kept model as …` older) into a
"Switched model" short label — moot here, typed `/model` is refused; the extension host gained a
`resume_precheck` (gated) and an extension self-update check, both telemetry. Probe outputs in
the 2026-08-28 session scratchpad.

</details>

<details><summary><b>Re-audit 2026-08-26 (2.1.241 → 2.1.246)</b></summary>

Nothing new on either side, measured rather than
assumed. Extension: `package.json` contributions (23 commands, 9 keybindings, config, views,
menus) and the 12-tool IDE-MCP roster are identical, diffed against the 2.1.241 extraction. CLI:
the typed control vocabulary grew exactly one subtype, `upload_device_hook_template` (the
@internal device-hooks family, beside `register_device_hooks`); a headless `initialize` on both
binaries shows one new top-level scalar, `analytics_disabled`, the same 53-command roster (no
adds, no drops, no hint changes — three bundled-skill descriptions swapped an em-dash for a
hyphen), the same 5-model roster with identical capability flags, the same 6 agents, 5 output
styles and `account` keys. Consequence for `docs/slash-commands.md`: its roster has been unchanged
from 2.1.233 through 2.1.246 (this audit covers 241→246, the 2026-08-23 one 233→241), so syncing
it is a version-label edit, not a re-verification. What did move this session is BEHAVIOR, not
surface, and is folded into rows: effort moved into the model menu with no chip suffix (9.2),
the Thinking switch is measured INERT on Fable (9.5), and per-model gating on roster capability
flags was tried on paper and declined on evidence (9.10). Probe outputs in the 2026-08-26 session
scratchpad.

</details>

<details><summary><b>Re-audit 2026-08-23 (2.1.233 → 2.1.241)</b></summary>

No new feature surfaces on either side: the
extension's contributions and IDE-MCP tool set are byte-for-byte the same roster, and the CLI's
typed control vocabulary added only an @internal device-hooks call. What DID move is measured
wire fact, folded into the rows below: the stdio host accepts `stop_task` (11.3, new at 2.1.238),
`side_question` (8.11), `apply_flag_settings` incl. `effortLevel`/`fastMode` (9.2/9.4),
`set_max_thinking_tokens` (9.5), `rename_session` (8.4), `get_settings`/`list_models`/
`get_context_usage` (9.6) — every one probed live 2026-08-23. `initialize` now also reports
`current_permission_mode`, `session_state` and `fast_mode_disabled_reason`; `/clear` grew an
optional `[name]` hint (7.6). Probe transcripts in the 2026-08-23 session scratchpad.

</details>

**Work order** (from `.claude/context/backlog.md`): plan-card shortcuts (if un-deferred) →
reloaded-webview log replay → kill-background-process → editor accept/reject v2 tweak-travel →
@-symbol mentions → worktrees → extensibility status view. Last, by the user's choice: tabs,
auto-include selection, voice.

---

## 1. Core chat & streaming
- **1.1** ✅ **Send a prompt** — reply streams token-by-token (`stream_event` deltas)
- **1.2** ✅ **User / assistant bubbles** — Ctrl+Enter sends, Enter = newline (VS Code's
      `useCtrlEnterToSend` behaviour, fixed on, no toggle)
- **1.3** ✅ **Prompt history** — ArrowUp on the first line recalls earlier prompts
- **1.4** ✅ **Tool-use lines** — shared description chain (`DESC_KEYS`/`IN_KEYS` in
      `RenderLimits.kt`), project-relative middle-ellipsised paths, click-to-open with line ranges
- **1.5** ✅ **IN/OUT boxes** — tool input/output in the diff's geometry; size caps and
      `.io-cut`/`.cmd-cut` truncation markers (`docs/limits.md`)
- **1.6** ✅ **AskUserQuestion card** — radio/checkbox + free-text "other" → `updatedInput`
- **1.7** ✅ **Stop / interrupt** — Send↔Stop toggle → `interrupt`; a mid-turn Stop replays as the
      stopped line, not a user message
- **1.8** ✅ **Retry** — re-runs the last prompt with its attachments
- **1.9** ✅ **Queued messages** — typed while busy, held in `#queue`, drained at `result`; replay
      shows mid-turn steered messages as user bubbles
- **1.10** ✅ **Markdown** — headings, lists, links, code, blockquote, hr, GFM tables with alignment
- **1.11** ✅ **Code blocks** — language label, copy button, offline syntax highlighter (keywords /
      strings / comments / numbers / php-vars; deliberately not a full grammar bundle)
- **1.12** ✅ **Thinking blocks** — collapsible; real thinking-token count when the event carries it
      (chars/4 fallback)
- **1.13** ✅ **Generating line** — streaming verb + in-flight gutter dot (pulsing → green/red);
      `prefers-reduced-motion` honoured (OS propagation into JCEF unverified, backlog)
- **1.14** ✅ **Auto-scroll** — pinning keyed off scroll DIRECTION; top/bottom fades;
      scroll-to-bottom button
- **1.15** ✅ **Error surfacing** — `result.is_error`; `system/api_error` retry banner (both
      spellings, deduped double-emits); model-refusal fallback (`supersedes` / retracted uuids);
      CLI stderr tail under the exit line; auth failure → "sign in from a terminal"
- **1.16** ✅ **Compaction** — boundary marker + folded summary + gauge reset; "Compacting…" verb;
      failed `/compact` reported (`system/status`)
- **1.17** ✅ **Rate-limit warnings** — `LIMIT_LABELS`, 2.1.222 vocabulary; fringe types fall back
      to the generic label
- **1.18** ✅ **Hook activity** — blocked tools + `informational` hook output rendered
- **1.19** ✅ **Server-side tool blocks** — web search; tool-returned images (`isImage` Bash
      results) open in the lightbox
- **1.20** ✅ **Local-command output** — `<local-command-stdout>` / `-stderr>` rendered as markdown
- **1.21** ⬜ [XS] **`redacted_thinking` placeholder** — never seen locally
- **1.22** ⬜ [SM] **`tool_progress` heartbeat** — "still running · 45s"; not yet emitted to
      stream-json clients, probe first (ide-mcp-protocol.md § 11)
- **1.23** ⬜ [SM] **Permission-denied reason** — `permission_denied` / `decision_reason` on the
      card; P3 polish
- **1.24** ⬜ [SM] **Turn end reason** — `result.terminal_reason` (19 values) surfaced when a turn
      ends oddly; P3 polish
- **1.25** ⬜ [SM] **Usage-limit grace banner** [NEW] — VS Code 2.1.250 (behind
      `tengu_lantern_sconce`) shows "Usage limit reached · a little extra on us, then your credits"
      / "… · finishing up" when `rate_limit_event.rate_limit_info` carries `rateLimitGraceActive:
      true` with `overageStatus` allowed / allowed_warning (dismissable; the CLI has emitted the
      fields since ≤ 2.1.246). Our 1.17 handler treats `status:"allowed"` as silence regardless, so a
      grace window is invisible. Probe first: an inject through `onClaudeEvent` proves the render, a
      real grace window is unforceable by design (MT-9.6 pattern)

## 2. Editor / IDE integration — the IDE-MCP tool set (12 tools, unchanged through 2.1.250)
- **2.1** ✅ **Editor tools** — `getWorkspaceFolders`, `getOpenEditors`, `getCurrentSelection`,
      `getLatestSelection`, `openFile`, `saveDocument`, `checkDocumentDirty`, `closeAllDiffTabs`
- **2.2** ✅ **`openDiff`** — real `DiffManager` view; three-verdict `DiffReview` contract
      (FILE_SAVED / FILE_SAVED_ALL / DIFF_REJECTED, `docs/ide-mcp-protocol.md` § 4)
- **2.3** ✅ **`getDiagnostics`** — via `DaemonCodeAnalyzerImpl.getHighlights` (warnings and up)
- **2.4** ✅ **`close_tab`** — closes the one review opened under that `tab_name` (2026-08-17; it
      used to sweep every diff tab); both close tools reply with the reference's exact strings
      (`TAB_CLOSED` / `CLOSED_<n>_DIFF_TABS`)
- **2.5** ➖ **`executeCode`** — Jupyter; stubbed reply, no Jupyter kernel in PhpStorm's remit
- **2.6** ✅ **Bridge** — free port, `~/.claude/ide/<port>.lock`, WS MCP server + auth header +
      `mcp` subprotocol; handed to the CLI via `--mcp-config` (not env discovery — gotchas)
- **2.7** ✅ **Async tool dispatch** — VFS/PSI reads under `readLocked {}`
- **2.8** ✅ **Edited files land in open editors** — no "Reload from disk"; `CliFileSync` +
      `Vfs.refreshFromDisk` (per-tool refresh + turn-end root sweep, 2026-08-14). VS Code gets this
      from its own FS watcher; here it had to be built
- **2.9** ✅ **Login-shell environment** — captured once per IDE run (`ShellEnv.kt`) so nvm/PATH-
      dependent MCP servers start under a GUI-launched IDE
- **2.10** ✅ **Autosave before read/write** — a host-registered SDK `PreToolUse` hook on
      `Edit|Write|MultiEdit|Read` (declared on `initialize`, answered after `saveDocument` on the
      EDT; `Autosave.kt`); same mechanism as VS Code's `claudeCode.autosave`, always on. Verified
      live 2026-08-17: an unsaved `ZEBRA-43` buffer was what `Read` returned
- **2.11** ✅ **Stale lock sweep** — `~/.claude/ide/*.lock` files with a dead pid deleted on every
      lock write (the CLI's own rule; `IdeLockFile.sweepStale`); 17 → 2 on first run, 2026-08-17

## 3. Diffs & edit approval
- **3.1** ✅ **Permission gate** — `can_use_tool` via `--permission-prompt-tool stdio`
- **3.2** ✅ **Accept / Reject card** — diff inline (old→new for Edit/MultiEdit multi-hunk,
      additions for Write); under acceptEdits the diff is built optimistically from the tool input
- **3.3** ✅ **Editor accept/reject** — dual surface: the same edit shows as a card AND an IDE diff
      tab with an under-diff bar (Accept ✓ / Accept all edits / Reject ✕), first answer wins
      (2026-08-09); replaces VS Code's editor-title buttons; no keyboard shortcuts by design
- **3.4** ✅ **"File was modified by the user"** — `staleRecovered` surfaced on the tool line
- **3.5** ✅ **In-diff editing before accept** — tweak-travel: the permission diff's right pane is
      editable; an edited pane rides back as `updatedInput` in the whole-file shape VS Code sends
      (`EditProposals.tweakedInput`: Edit → old_string = whole file, Write → content, MultiEdit →
      one such edit — VS Code hands MultiEdit to the card, we don't have to). Probed 2026-08-28
      (2.1.250): the CLI applies it, the tool_result stays a one-liner, the transcript keeps the
      model's ORIGINAL `tool_use` while `toolUseResult` records what ran. The card redraws the
      diff that ran with a "edited in the IDE before accepting" note; replay detects the tweak by
      replaying both onto `originalFile` (`EditProposals.tweaked`). The pane is editable only
      through `DiffContentFactoryEx.createEditable` — `DiffContentFactory.create(text)` is a
      read-only document (found on the hand test; the bridge flow's pane had never been editable).
      Built and HAND-VERIFIED live + resumed 2026-08-28 (session cad0a74e); fixture 59;
      accept/reject v2 complete
- **3.6** ✅ **Multi-file change review** — a per-turn "✎ N files changed · a.md +1−0, … · Review"
      line under the ✻ summary; Review opens ONE diff tab holding a chain of every file the turn
      changed ("Before this turn" / "Now", read-only, prev/next between files —
      `DiffReview.openChain`). Baselines come from the PreToolUse autosave hook the CLI already
      blocks on (`Autosave` → `TurnChanges.snapshot`, first touch per turn) and settle at `result`
      (`__files_changed`). VS Code's shape is per-session via a `file_updated` notification to its
      in-process sdkMcpServer + a checkpoint store; ours is per-turn because that is what the
      hook sees cleanly. `get_workspace_diff` probed 2026-08-28: git HEAD vs working tree with
      per-file stats + structuredPatch-shaped hunks — includes the user's own edits, so NOT the
      source (fact in `ide-mcp-protocol.md`). Limits: Edit/Write/MultiEdit only (a Bash `sed` is
      invisible — same as VS Code's checkpointing); live turns only — a resumed session draws the
      line from the transcript (count + names) without Review (backlog). Built and hand-verified
      2026-08-28 (two files in one turn, chain navigation); fixture 60; `TurnChangesTest`

## 4. Permission modes
- **4.1** ✅ **Mode chip** — the CLI's own four modes via `set_permission_mode`: manual (`default`,
      aliased in the chip), acceptEdits, plan, auto (the safety-classifier mode)
- **4.2** ➖ **`bypassPermissions`** — removed 2026-08-03 with the relaunch machinery; the CLI
      refuses to be raised into it at runtime and Auto is the intended equivalent
- **4.3** ✅ **Chip follows the CLI** — driven by `permissionMode` broadcasts (init/status);
      refused control requests surface as error blocks
- **4.4** ✅ **"Don't ask again" buttons** — from `permission_suggestions`; compound `addRules`
      merged into one button (VS Code does not); hidden on `blocked_path` prompts
- **4.5** ✅ **Mode persistence** — survives restarts and New/Refresh/resume; launched with the
      persisted mode. Since 2.1.241 `initialize` also reports `current_permission_mode`, a
      reconciliation source if the persisted value ever drifts
- **4.6** ➖ **`allowDangerouslySkipPermissions` / `initialPermissionMode`** — the flag turns every
      mode into a bypass (probed); persistence covers the initial-mode need

## 5. Plan mode
- **5.1** ✅ **Plan card** — enter plan mode from the chip; the plan renders as a card on
      `ExitPlanMode`
- **5.2** ✅ **Feedback field** — rides whichever button answers: deny → verbatim tool_result;
      approve → appended under `PLAN_NOTES_MARKER` via `updatedInput.plan` (same-message
      delivery, timing-equivalent to the TUI's shift+tab; 2026-08-16)
- **5.3** ✅ **Split Approve** — approve · approve + auto-edit · approve + auto; mode rows park in
      `pendingPlanMode` and bridge only after the CLI's post-approval mode broadcast
- **5.4** ✅ **Replay** — plan + quoted feedback footer (`fbQuote`), the note parsed back out of
      `toolUseResult.plan`
- **5.5** 🚫 **Card keyboard shortcuts** — Enter = keep planning, Shift+Tab = approve; deferred by
      the user 2026-08-16, backlog § Next up
- **5.6** ✅ **Anchored plan comments** — select text in the plan card → floating Comment pill → a
      note row quoting the anchor; rows sit between the plan and the decision surface, decided
      cards keep them WITH the anchor highlights, replay parses them back identically
      (`highlightAnchors` / `planCommentRows`, shared by live and replay). Deny sends the VS Code
      client's exact wire shape (`PLAN_DENY_PREFIX` + text + `PLAN_COMMENTS_HEADER` + `[Re:
      "<anchor>"] <note>` lines, strings in `RenderLimits`); approve-with-comments rides the
      `PLAN_NOTES_MARKER` append. Two deliberate divergences: the full approve surface stays
      available with comments pending (VS Code collapses to keep-planning), and VS Code's plan-file
      preview tab is not replicated (the card body is the preview). Shipped 0.9.0 (2026-08-23);
      fixture 53; history in journal digest 2026-08-23
- **5.7** ➖ **`/ultraplan`** — cloud-drafted plan; a cloud product surface, not a panel feature

## 6. Context input
- **6.1** ✅ **@-mention files** — fuzzy autocomplete, keyboard nav, dismissal contract shared with
      the slash menu, ellipsis at the end
- **6.2** ✅ **Attachments** — images (`image` blocks) + PDF / text / code (`document` blocks with
      titles); paste; drag-and-drop via an AWT `DropTarget` (JCEF never delivers OS drags); chips
      preview in the lightbox or save via the IDE dialog
- **6.3** ✅ **Injected IDE context stripped on replay** — `<ide_selection>` etc.
- **6.4** 🟧 [MD] **@-mention symbols** — roadmap
- **6.5** ⬜ [SM] **Insert @-mention from the editor** — VS Code's Alt+K; needs a plugin action.
      The plugin binds no shortcuts today, but an unbound action the user can map is compatible
      with that
- **6.6** 🚫 **Auto-include current selection** — deferred by the user (do last)
- **6.7** ⬜ [SM] **`list_files_request` / `respectGitIgnore`** [NEW] — our picker is IDE-indexed;
      ➖ unless a real gap shows. (The CLI also answers `file_suggestions {query}` over stdio with
      the TUI's own fuzzy ranking — an alternative source if one ever does)
- **6.8** ➖ **`@terminal`** [NEW] — `get_terminal_contents`; the panel does not own a terminal

## 7. Slash commands (`docs/slash-commands.md` is the source of truth)
- **7.1** ✅ **Slash menu** — opens on `/`; keyboard nav, descriptions, source badges (project /
      user / mcp)
- **7.2** ✅ **Roster** — from the `initialize` control request; refreshed by `commands_changed`
      (project-dir watcher fires by itself; user-dir needs `/reload-skills`)
- **7.3** ✅ **Custom commands, skills, MCP prompts auto-enabled** — detected by the `" (project)"`
      / `" (user)"` description suffix (the wire has no type field)
- **7.4** ✅ **16 built-ins enabled** — each driven through the live panel; the rest Hidden by group
- **7.5** ✅ **Pick rule** — a command with ANY `argumentHint` inserts `/name ` and waits; hint-less
      runs (2026-08-16; the roster carries no `immediate` flag)
- **7.6** ✅ **`/clear`** — native (→ new conversation). 2.1.241 gave it an optional `[name]` hint
      (the TUI names the fresh session), so a menu pick now INSERTS `/clear ` instead of running
      (`cmdTakesArg` keys on the hint) and the native branch drops any typed name — decide whether
      to pass it through as the new conversation's title or pin the old pick-runs behavior
- **7.7** ✅ **Aliases** — first-class: the menu filter scores them like names, rows show them
      muted (`.pi-alias`), a typed alias resolves to its command before the allowlist gate
      (`canonicalCmd`; `/review` → `/code-review`, `/new` → `/clear`); fixture 52, 2026-08-17
- **7.8** ➖ **TUI-only commands** — never on the headless roster: `/login /logout /resume /help
      /add-dir /rewind /diff /update /theme /vim /keybindings /export /copy /bug /feedback
      /memory /permissions /hooks /mcp /plugin /agents /doctor /status /config /ide /terminal-setup
      /voice /desktop /mobile /teleport /remote-control /background /branch /fork /btw /tasks
      /skills /skill-doctor /pause-memory /alias /advisor /focus /brief /wellbeing /radio …` —
      the terminal's half by construction. Where the panel has an equivalent it is listed in its
      own section (rename, model, effort, mode, resume, tasks, focus)
- **7.9** ✅ **Panel equivalents of TUI commands** — `/rename` (header pencil), `/model` +
      `/effort` (chips), `/tasks` (bg roster, read-only), `/resume` (history), `/clear` (New)
- **7.10** ✅ **Roster survives a webview reload** — `ChatPanel` keeps the newest
      `commands_changed` frame from the current CLI and replays it after the init seed on every
      page load (cleared on a fresh `initialize`); driven live over CDP 2026-08-17, a mid-session
      command survived `location.reload()`

## 8. Sessions / history
- **8.1** ✅ **New / Resume / Refresh** — Resume = history list → `--resume <id>`; Refresh
      re-resumes and recovers a dead CLI (guarded by `SessionStore.exists`)
- **8.2** ✅ **History list** — from `~/.claude/projects/<enc-cwd>/*.jsonl`; titles from
      `custom-title` (newest rename wins) → summary → first user message
- **8.3** ✅ **Header title** — shown as soon as the transcript can name it (probe at
      `message_start`, re-read at every `result`; `seedUi()` on every load)
- **8.4** ✅ **Rename** — in place: header pencil → the CLI's own `custom-title` record (=
      `/rename`). A `rename_session {title}` control request also exists and is accepted over stdio
      (probed 2026-08-23) — a cleaner path if the record-write ever grows warts
- **8.5** ✅ **Delete** — any conversation; the live one via leave-first (`awaitExit`)
- **8.6** ✅ **Replay** — through the same block builders as live (audited; deliberate divergences
      in gotchas.md § Replay); windowed with an aligned turn-boundary cut, "N earlier blocks not
      loaded" marker and load-earlier on scroll-up; interrupt markers, sub-agent lines, plan
      feedback, steered messages all replay
- **8.7** 🟥 [LG] **Rewind / checkpoints + fork conversation** [DECIDE] — the one substantial
      capability gap vs both clients (VS Code `rewind_code` / `fork_conversation`; TUI `/rewind`,
      `/branch`, `/fork`). Per-turn file rewind was REMOVED 2026-07-30; revival notes in gotchas
      § Protocol (`CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1`, git repo, client uuids via
      `stampMessage`, dry_run first). The wire shape is a typed control now: `rewind_files
      {user_message_id, dry_run}` → `{canRewind, filesChanged, insertions, deletions}`, plus a
      `file_snapshot` / `files_persisted` record family (2026-08-23; unchanged since 2.1.233, not
      probed live — it mutates files). Status: still UNDECIDED — needs an explicit yes / later / no
- **8.8** ⬜ [MD] **Reopen closed session** — Ctrl+Shift+T; ➖ while the plugin binds no shortcuts
      and has no tabs
- **8.9** 🚫 **Multiple conversation tabs** — deferred by the user (do last)
- **8.10** ⬜ [LG] **Session groups / sessions sidebar** [NEW · DECIDE] — a
      `claude-sessions-sidebar` view, `get_session_groups` / `update_session_groups`,
      `list_sessions_request`. Take: our history popup already lists per-project sessions;
      grouping is only worth it once tabs or worktrees exist. Watch
- **8.11** 🟥 [MD] **Side question** [NEW · DECIDE] — `side_question` host message (TUI `/btw`):
      ask without disturbing the main thread. Take: hourly-plausible. Probe DONE 2026-08-23: a
      stdio control request `{subtype:"side_question", question, history?}` answered `{response,
      synthetic}` with a real model reply, no turn on the main thread. Ready to build on a yes
- **8.12** ➖ **Remote sessions / teleport / remote control** — `list_remote_sessions`,
      `teleport_session`, `toggle_remote_control` (TUI `/teleport`, `/rc`, `/session`); session
      SOURCES beyond the local disk lean infrastructure; recorded as a judgment call
- **8.13** ➖ **`generate_session_title`** [NEW] — the CLI names threads itself (`ai-title`) and we
      show it
- **8.14** 🟥 [LG] **Reloaded-webview log replay** — chrome heals via `seedUi()`, the log does not;
      backlog § Next up, no reload observed in the wild yet

## 9. Model, effort, usage
- **9.1** ✅ **Model chip** — from `initialize.models`, search + custom id → `set_model`; persisted
      and re-applied on every restart
- **9.2** ✅ **Effort slider** — low / medium / high / xhigh / max, last row of the model-menu
      footer (moved out of the mode menu 2026-08-26; the level shows only on the footer's own
      "Effort" label, on no chip — decisions 2026-08-26). Sends a muted `/effort` turn because no
      `set_effort` control exists; both paths render the CLI's confirmation line (gotchas
      § Protocol). Retire path when wanted: `apply_flag_settings {settings:{effortLevel}}` is
      accepted over stdio and takes effect (probed 2026-08-23). The CLI's `/effort` hint also
      accepts `ultracode|auto` beyond the slider's five stops
- **9.3** ✅ **Context gauge** — ring on the composer from `modelUsage[].contextWindow` (a map,
      side models included); reset on compaction
- **9.4** ✅ **Fast mode toggle** — a switch in the model-menu footer, enabled only when the roster
      item says `supportsFastMode` (Opus family); sends `apply_flag_settings {settings:{fastMode}}`.
      Optimistic click, reconciled from the CLI's `fast_mode_state` (off|on|cooldown + reason on
      the tooltip) at initialize and every `result`; pref persisted (`claudeCode.fastMode`) and
      re-applied each CLI start. Built 2026-08-24; fixture 55; history in decisions 2026-08-24
- **9.5** ✅ **Thinking on/off** — a switch in the model-menu footer, default ON, never gated.
      OFF → `set_max_thinking_tokens {max_thinking_tokens: 0}`; ON → `null` (= session default, a
      deliberate divergence from VS Code's pinned 31999 — decisions 2026-08-24). **Inert on Fable**:
      measured 2026-08-26, kept as-is by user decision ("document only"; no roster flag
      discriminates it — gotchas § Protocol). Pref persisted (`claudeCode.thinkingOff`), re-applied
      each CLI start, seeded to the webview via `__thinking` (no CLI echo exists). Built
      2026-08-24; fixture 55
- **9.6** ➖ **Cost / token breakdown / usage panel** — `get_usage`, `get_context_usage`,
      `request_usage_update` [NEW]; TUI `/usage`, `/cost`, `/context`. Declined 2026-08-06; the
      built-in `/context` is enabled as a turn for the rare look. (`get_context_usage` and
      `get_settings` both answer over stdio — probed 2026-08-23 — so the data is there if this is
      ever revived)
- **9.7** ⬜ [MD] **Fable overage gate** — `model_consent_fallback`; no `supportedDialogKinds`
      declared, so the CLI stays silent and the model chip could lie if the gate fires. Probe by
      exercising it
- **9.8** ➖ **Subagent model / cloud providers** — `CLAUDE_CODE_SUBAGENT_MODEL`, Bedrock / Vertex /
      Foundry setup; env and terminal configuration
- **9.9** ✅ **1M-context toggle** [NEW] — a switch in the model-menu footer that appends/strips
      `[1m]` on the selection and re-selects through `setModel` (persistence rides
      `claudeCode.selectedModel`; `default` resolves to `claude-opus-5[1m]`). **No client-side
      validity logic** (user decision 2026-08-24): `set_model` never rejects, an unsupported combo
      fails on the next turn with the API's own 400. The switch reconciles to the REAL window from
      `result.modelUsage[].contextWindow` after each model's first turn (`reconcileFromResult`);
      gauge denominator set explicitly on toggle. Built 2026-08-24; fixture 55; decisions
      2026-08-24
- **9.10** 🚫 **Per-model gating of effort / Thinking** — on the roster's capability flags
      (`supportsEffort` / `supportedEffortLevels` / `supportsAdaptiveThinking`); proposed and
      dropped 2026-08-26 on measurement: haiku carries none of the flags yet accepts `/effort max`
      and thinks, and Fable carries `supportsAdaptiveThinking` yet ignores the switch (9.5) — no
      flag tracks behaviour in either direction. Only `supportsFastMode` gates anything (9.4).
      Revive only if a CLI ships a flag proven to track behaviour. Gotchas § Protocol; decisions
      2026-08-26

## 10. Auth & account
- **10.1** ➖ **Login / logout / account display** — by design: sign in once by running `claude`;
      the panel reports an auth failure with a "sign in from a terminal" line
      (`disableLoginPrompt` is the terminal's). Confirmed a decision, not a missing capability
      (`claude_authenticate` + OAuth callbacks exist in the CLI)

## 11. Extensibility (MCP / plugins / skills / hooks / subagents)
- **11.1** ➖ **Plugin / MCP / hooks / agents management UI** — the terminal's half (`/plugin`,
      `/mcp`, `/hooks`, `/agents`, `~/.claude`)
- **11.2** ✅ **What the panel shows from this family** — MCP server failure notice at init
      (`mcp_servers[].status`), MCP prompts and skills in the / menu, hook output, sub-agent
      progress line + prompt + final report, background task roster (`bg` chip, reset at the CLI
      boundary), Todo/Task checklist (`TodoWrite` + the Kotlin task store)
- **11.3** 🟥 [MD] **Kill a background process from the panel** — roster rows are display-only.
      Protocol check DONE 2026-08-23: `stop_task {task_id}` is a control request the stdio host
      accepts (typed schema since ≤2.1.233, added to the remote-relay whitelist at 2.1.238; probed
      live — success, unknown ids answer without error, so verify against a real task id when
      building). A sibling `background_tasks {tool_use_id?}` backgrounds foreground tasks (Ctrl+B
      semantics). Needs only the roster-row action (conversations-list hover-gutter idiom).
      Backlog § Next up
- **11.4** 🟧 [MD] **Sub-agent work outcome** — the dot was built and withdrawn 2026-08-13; known gap
- **11.5** ⬜ [SM] **Elicitation** — `elicitation` control request; our empty ack answers it with
      neither decline nor an answer, no local MCP server elicits today. Probe if one ever does
- **11.6** 🟧 [MD] **Extensibility status view** — read-only: which MCP servers/plugins are live;
      roadmap tail, philosophy leans terminal

## 12. UI placement, windows, keys
- **12.1** ✅ **Right-anchored tool window** — JetBrains moves/floats/undocks it natively (covers
      `preferredLocation`, sidebar/panel, new window)
- **12.2** ✅ **Open DevTools action** — "Claude Brains: Open DevTools" via Find Action; no default
      chord
- **12.3** ⬜ [MD] **Open in editor tab** [NEW · DECIDE] — `editor.open` / `primaryEditor.open`; a
      second host for the same webview, only worth it if someone wants a wide chat. Watch
- **12.4** ➖ **Keyboard shortcuts** — focus/blur input, Ctrl+N new conversation, Ctrl+Shift+T: the
      plugin binds NO keyboard shortcuts (2026-08-09, chords proved unreliable across setups);
      users can map the tool window's own Show action in Keymap
- **12.5** ➖ **Terminal mode** — `useTerminal`; that is just the terminal
- **12.6** 🟧 [MD] **Focus view** [NEW · DECIDE] — VS Code `toggleFocusView` / `set_focus_view` /
      `focusView` setting, TUI `/focus`, `/brief`: hide tool noise, show prompts + responses. Take:
      worth a mockup pass before building; folded IN/OUT boxes already do half of it
- **12.7** 🚫 **Light theme / configurable colours** — decided 2026-08-07 (dark only)

## 13. Settings
- **13.1** ➖ **No settings page** — by design. CLI resolution: `-Dclaude.executable` → PATH → VS
      Code extension binary; model, mode, effort persist via `PropertiesComponent`. VS Code's
      `claudeCode.*` keys map as: `environmentVariables` / `claudeProcessWrapper` /
      `respectGitIgnore` / `usePythonEnvironment` / `disableLoginPrompt` → terminal & env;
      `useCtrlEnterToSend` → fixed on; `initialPermissionMode` → persistence; `preferredLocation`
      / shortcuts / `hideOnboarding` / `focusView` → see §12; `autosave` → §2 candidate
- **13.2** 🟧 [SM] **JSON schema for `.claude/settings.json`** [DECIDE] —
      `claude-code-settings.schema.json` shipped by VS Code; a JetBrains
      `JsonSchemaProviderFactory` is small and editor-hourly for anyone editing settings. Take:
      worth doing, unrelated to the panel

## 14. Worktrees & git
- **14.1** 🟧 [LG] **Create worktree** — `create_worktree`; TUI `/branch`; roadmap
- **14.2** ⬜ [MD] **Git actions in the host** [NEW · DECIDE] — `checkout_branch`,
      `check_git_status`, `update_skipped_branch`. Take: read the webview flow before deciding;
      probably belongs with worktrees
- **14.3** 🟧 [LG] **Git-aware diff/context** — roadmap
- **14.4** ⬜ [SM] **Workspace diff over the wire** [NEW] — `get_workspace_diff` control, the engine
      behind the TUI's thin-client `/diff` (one base ref, 5s git timeout, 50 files, 1MB/file caps);
      newly noticed 2026-08-23 (present since ≤2.1.233). The IDE has native git diff, so this
      leans ➖ unless 14.3 wants the CLI's view of the diff

## 15. Onboarding & misc
- **15.1** ➖ **Walkthrough / onboarding / upsell banners** — `dismiss_review_upsell_banner` [NEW],
      `update`, `showLogs`; JetBrains handles updates, the README is the walkthrough
- **15.2** 🚫 **Voice input** [NEW] — `start_speech_to_text`; TUI `/voice`; deferred by the user
      (do last)
- **15.3** ➖ **Small chrome** — message rating + `/feedback` / `/bug`, prompt suggestions, Artifact
      auto-open, "Explored" grouping of consecutive Reads, `/stickers`, `/radio`, `/powerup`;
      leaning no
- **15.4** ➖ **`/share` / `/export` / `/copy`** — the transcript is on disk; the terminal exports it
- **15.5** ⬜ [LG] **`ask_debugger_help`** [NEW] — VS Code hands the debug console to Claude; no
      PhpStorm analog probed, ➖ until someone asks
- **15.6** ⬜ [SM] **Chrome MCP / Jupyter MCP toggles** [NEW] — `ensure_chrome_mcp_enabled`,
      `enable_jupyter_mcp`; configuration, ➖

## 16. Quality gates (not features, but part of "what we have")
- **16.1** ✅ **Unit tests** — `./gradlew test` (130, JUnit 5 over SessionStore/RenderLimits);
      every suite's negative control RUN
- **16.2** ✅ **Live harness** — `tools/live_harness.py`: fixtures numbered to 60, 490 assertions,
      real captured wire frames replayed into the live webview over CDP
- **16.3** ✅ **Dev aids** — `./gradlew probe` (replay without the IDE); `tools/cdp.py`;
      `window.__gallery()`; DevTools action; `runIde -PjcefDebugPort` (sandbox Registry still wins
      — gotchas)
- **16.4** ✅ **Plugin Verifier + Marketplace** — 0 warnings on PhpStorm 242→262; upload automated
      on `release: published`; releases 0.4.0 → 0.11.1 all Approved
- **16.5** ✅ **Manual-test checklist completed** — 102/102 passed (the old "92" undercounted), 0
      open; the self-contained `docs/manual-test.md` was deleted 2026-08-28 and its full record is
      § 17 below

## 17. Manual verification record (from the retired docs/manual-test.md, 102/102 passed 2026-08-07→08-26)
- Source: `git show 9bd1683:docs/manual-test.md` (deleted 2026-08-28; §16.5). Setup was `cd plugin && ./gradlew runIde`, open the Claude Brains tool window; items were ticked by number ("3.4 done"); **(hard to trigger)** items carry their exact trigger recipe below.
- Defect markers were exactly two: `ISSUE (date):` = open, `RESOLVED (date) — how:` = closed, *how* ∈ fixed / removed / not a bug; `grep -c '\*\*ISSUE'` was the open count. Final state: 31 RESOLVED, 0 ISSUE.
- Undated items below were ticked on the first pass (2026-08-07/08); later dates come from the item's own note. Cross-cutting caveat (6.4): grants from 2026-08-07/08 persist in the testing project's `settings.local.json`, so permission cards only reappear for novel commands (the pass used `factor`/`mcookie`/`openssl`/`base32`, never granted).

<details><summary><b>17.1 Launch & chrome</b> — 7 items · 2 RESOLVED</summary>

- **MT-1.1** Panel loads in the sandbox, no blank webview → 12.1 · 2026-08-07
- **MT-1.2** Welcome: brain logo, "Claude Brains v0.3.3" badge, slogan "Develop in the IDE. Configure in the Terminal.", `/` and `@` hint lines → (no row) · 2026-08-07
- **MT-1.3** Stripe icon grey closed / white open → 12.1 · 2026-08-07
- **MT-1.4** Header shows title once one exists; Refresh re-resumes → 8.1, 8.3 · 2026-08-07
- **MT-1.5** New via New button and `/clear` (no shortcut by design) → 7.6, 8.1, 12.4 · 2026-08-09 · RESOLVED 2026-08-09: removed — Ctrl+N chord was swallowed by the IDE ("Go to Class"); plugin binds no shortcuts; both paths send `kind:'new'`
- **MT-1.6** Scroll fades top/bottom while scrollable → 1.14 · 2026-08-07
- **MT-1.7** Escape closes any open popup (menus, history, lightbox, task roster) → 7.1, 6.1, 4.4 · 2026-08-09 · RESOLVED 2026-08-09: not a bug, sandbox artifact — "reopening glitches after Escape" does not reproduce in installed PhpStorm 2026.2 nor over CDP (open→true, Escape→false, reopen→true, DOM byte-identical to click-close; `tg()` derives all state from `.show`); residual is IDE-level focus return to the editor in the runIde sandbox (PhpStorm 2024.2, stock keymap) · RESOLVED 2026-08-09: fixed — Escape sticks on the slash menu via `slashEscaped` (re-armed when text leaves `/^\/[\w-]*$/`, on explicit slash-button open, and by `clearComposer()`); evaluation lives in `slashAuto()` shared by typing/refocus/click-in (headless 8/8, commands seeded via `system/commands_changed`) · RESOLVED 2026-08-09: fixed — Escape closes permission-card split menus via a `closeCardMenus()` rung between lightbox and `activeMenu()` (card menus live outside `MENUS`); mirrored in `design/mockup.html`, probed `/c`→`/co` stays closed, layered card+composer menus close in order

</details>

<details><summary><b>17.2 Composer basics</b> — 14 items · 3 RESOLVED</summary>

- **MT-2.1** Ctrl+Enter sends; Enter newline → 1.2 · 2026-08-07
- **MT-2.2** Reply streams token by token → 1.1 · 2026-08-07
- **MT-2.3** Send↔Stop; Stop → ⏹ Stopped line, no completion summary → 1.7 · 2026-08-07
- **MT-2.4** ↑/↓ walk prompt history; half-typed draft survives → 1.3 · 2026-08-07
- **MT-2.5** Typing while busy → queue above composer, auto-sends at turn end → 1.9 · 2026-08-07
- **MT-2.6** Queue row click restores text to composer; × drops it → 1.9 · 2026-08-07
- **MT-2.7** After Stop, queued messages do NOT auto-send → 1.7, 1.9 · 2026-08-07
- **MT-2.8** `@` opens file-mention menu; pick inserts the reference and the model can use it → 6.1 · 2026-08-09 · RESOLVED 2026-08-09: fixed — menu opened invisibly: `#mention`/`.mi` had no CSS (`position: static` ignored `openMenu()`'s viewport left/top); CSS-only fix (`position: fixed` + popup skin, next to `.popup` in chat.css); CDP on installed IDE: 39 files fed, `@sal` → 1 row, Enter inserts `@src/Salutation.php `; added `mentionEscaped` Escape-stick · same day: dismissal contract — document click outside `#mention` hides (incl. clicks into textarea, else stale `menuStart` mis-splices), `tg()` hides on composer-popup toggle, `openMenu()` closes composer+card popups (spliced chat.html + chat.css + live `window.LIMITS`, 12/12) · same day enhancement (user request): caret return to an @-token reopens (`updateAuto()` on refocus/click-in); outside-click soft, Escape hard; `!activeMenu()` guard keeps `tg('slashMenu')`'s `input.focus()` from opening it over the slash menu (9/9)
- **MT-2.9** Image paste and drag-drop → chip; renders in sent user box → 6.2 · 2026-08-09 · RESOLVED 2026-08-09: fixed — JCEF-on-Linux OSR never delivers OS drags as DOM events (synthetic DataTransfer drop worked); added AWT `DropTarget` on the browser component (`ChatPanel.installFileDrop`, `javaFileListFlavor`, files read off the EDT, 25 MB/file `MAX_DROP_BYTES`, base64 → page `__dropFiles` with the same fileKind gate/media-type normalisation as paste); headless 4/4 (png + code chips, unsupported binary skipped, pdf normalised); DOM handler kept — remove it if a future JBR delivers native DnD (drops would arrive twice); hardware drag was the one unproven link pending `runIde`
- **MT-2.10** PDF/text attach → document chip with real filename → 6.2 · 2026-08-07
- **MT-2.11** After an error turn, ↻ Retry re-runs last prompt incl. images → 1.8 · 2026-08-07
- **MT-2.12** Paperclip → attach menu → native picker → chip → 6.2 · 2026-08-07
- **MT-2.13** [/] button opens slash menu same as typing `/` → 7.1 · 2026-08-07
- **MT-2.14** Delete forward-deletes in composer and model search (alone, with selection, Ctrl+Delete word), no stray char → 1.2, 9.1 · 2026-08-09 · RESOLVED 2026-08-09: fixed — Delete inserted a tofu char (JCEF-on-Linux sends AWT keyChar 0x7F as TEXT); document-level two-layer fix in chat.html (keydown manual forward-delete, selection-aware, Ctrl+Delete = word, native cancelled; capture-phase input filter strips control chars except \t \n, caret preserved — also kills ESC 0x1B); covers `#modelSearch`; headless 7/7 (mid, selection, Ctrl-word, end-noop, 0x7F strip, \t\n survive, mention re-filter); D1 proves no double-delete in a compliant browser; real-JCEF hardware key pending runIde

</details>

<details><summary><b>17.3 Slash commands & effort</b> — 8 items · 3 RESOLVED</summary>

- **MT-3.1** `/` menu shows only the allowlist (/compact, /clear + custom commands) with descriptions → 7.3, 7.4 · 2026-08-15 · RESOLVED 2026-08-15: fixed — CLI suffixes every custom description with " (project)"/" (user)" (2.1.228, 0 false positives across 107 built-ins); `markCustom()` strips to a badge, `cmdKind` returns 'text' for marked entries and `mcp__` prompts, built-ins checked first so a custom `clear.md` cannot shadow /clear; verified in runIde on `~/Sites/claude-brains-testing`: /dummy-cmd [project], /sub:nested-cmd [project] (":" join like the TUI), /user-probe [user], /skill-probe [user]; /dummy-cmd expands ("dummy command executed"); /login still refused; roster survives reload; `argument-hint: <name>` → pick inserts `/greet ` and `/greet Amit` → "greetings, Amit"; fixture 46 (25 asserts, 8 red on negative control); original ISSUE 2026-08-08: `cmdKind` had no custom detection · adjacent fix: an arg-less custom command as FIRST message leaked `<command-message>…</command-message>\n<command-name>` wrapper XML into the title — `cleanInjected` now matches name and args independently (SessionStoreTest)
- **MT-3.2** Hand-typed TUI-only command (/login) refused with a status line, not sent → 7.4, 7.8 · 2026-08-07
- **MT-3.3** /clear wipes view; old session still in history and resumable → 7.6, 8.1 · 2026-08-07
- **MT-3.4** /compact: "Compacting…" verb, marker + folded summary, gauge resets → 1.16, 9.3 · 2026-08-07
- **MT-3.5** Effort slider in the MODEL menu footer below the three switches changes level: CLI "Set effort level to …" line, no command bubble, footer "Effort `<b>`" label updates; NEITHER chip shows the level (bracketed and middot suffixes both rejected 2026-08-26) → 9.2, 9.5 · 2026-08-26 · KNOWN INERT, not a bug: Thinking switch does nothing on Fable — `set_max_thinking_tokens 0` accepted, fable still streams thinking ("document only" by user decision)
- **MT-3.6** Menu pick runs a no-arg command immediately; required `<arg>` inserts `/cmd ` and waits → 7.5 · 2026-08-07
- **MT-3.7** Local built-in output (`/context`, `/recap`, `/list-agents`) renders live and on resume → 1.20, 7.4 · 2026-08-15 · RESOLVED 2026-08-15: fixed — `/context` showed only "Puttered for 1s": a local command answers as a bare whole-message assistant frame with ZERO stream_events (consumed as a uuid stamp) and the transcript spells it `system/local_command` + `<local-command-stdout>` (skipped as unknown subtype); live `msgStreamed` flag renders un-streamed text through `.blk` (fixture 47; full-suite order caught a cross-conversation flag leak); replay `local_command` branch in SessionStore (stdout → assistant, stderr → error; real session `41a89e81…` probes to the block)
- **MT-3.8** Every ENABLED command renders its output — all 16 swept → 7.4, 1.20 · 2026-08-15 · RESOLVED 2026-08-15: fixed — 16 commands driven over CDP (wire tape on `onClaudeEvent`, fresh conversation each, `busy` classification): 3016 frames, zero handler errors; `/verify` 213s/25 blocks/23 tools, `/simplify` 18 blocks/12 tools (really edited), `/init` 10 tools (its `CLAUDE.md` deleted after), `/batch` declined to fan out, `/loop` printed a job id, `/compact` reset gauge to 0; defect `/security-review`: fails without `origin/HEAD` and showed an empty completion — `user` frame with STRING `content` carrying `<local-command-stderr>` dropped at `!Array.isArray(content)`; fixed live (`localCommandText`) and replay (SessionStore routes wrappers on `user` records); fixture 47 → 7 steps (4 red on negative control), session `eb8a3598…` probes to an error block; `/deep-research` SILENT-END was a harness artifact (stop ladder's `kind:'new'` wiped the log) — renders 2 blocks + "1 task" chip at 150s · caveats closed same day: `/security-review` success path with a local bare remote + vulnerable file → real report (SQLi/command injection/path traversal), 380s, 0 errors; REGRESSION found: every message after the first rendered TWICE — CLI emits an `assistant` frame PER CONTENT BLOCK, so `msgStreamed` is now TURN-level (set at `message_start`, cleared only at result/sendTurn/clearLogUI; a `message_stop` reset was tried and rejected — frames straddle it); `/batch` with plan APPROVED launched two parallel worktree agents (`.claude/worktrees/agent-*`, two branches, chip "2 tasks", `PR: none — no GitHub remote`), 9 blocks/16 tools, no duplicates

</details>

<details><summary><b>17.4 Mode & model</b> — 7 items · 1 RESOLVED</summary>

- **MT-4.1** Mode chip shows persisted mode on startup, survives IDE restart → 4.5 · 2026-08-07
- **MT-4.2** All four modes switch live, no CLI restart → 4.1, 4.3 · 2026-08-07
- **MT-4.3** Composer focus border + Send colour follow the mode → 4.1 · 2026-08-07
- **MT-4.4** Edit automatically: edits apply without a card; Bash still asks (correct) → 3.2, 4.1 · 2026-08-09 · RESOLVED 2026-08-09: fixed — auto-approved Edit/Write/MultiEdit mount the same "✓ Applied" `.card.warn` as replay, built from tool_use INPUT (wire carries no structuredPatch, probed 2026-08-05); optimistic-with-supersede: input stashed on `toolsById` at `content_block_stop`, `{kind:'lineStart'}` bridge asks Kotlin for the gutter line pre-apply, `supersedeEdit(tool, file)` on `permission_request` prevents double-render, `fillAppliedCard` mounts at `tool_result` (`previewHtml` delegates to `replayDiff`); bonus: MultiEdit was handled NOWHERE (empty card preview) — `replayDiff` `edits[]` branch, `editLineStart` uses `edits[0].old_string`; headless 8/8 live + 3/3 replay
- **MT-4.5** Plan mode: card appears; approving drops chip to Manual → 5.1, 5.3 · 2026-08-07
- **MT-4.6** Model pick persists across restart; search filters → 9.1 · 2026-08-07
- **MT-4.7** Custom model `value : Display Name : Description` shows display name in chip → 9.1 · 2026-08-07

</details>

<details><summary><b>17.5 Streaming render</b> — 17 items · 3 RESOLVED</summary>

- **MT-5.1** Tables render; code blocks highlighted; long code folds → 1.10, 1.11 · 2026-08-07
- **MT-5.2** Thinking: shimmer + live seconds + token count; collapses to "Thought for Ns" with chevron → 1.12 · 2026-08-07
- **MT-5.3** Working line (flower spinner) shows live token estimate → 1.13 · 2026-08-07
- **MT-5.4** Completion summary "✻ Verb for Ns · ↓ N tokens"; never "↓ 0 tokens"; consecutive summaries never share a verb → 1.13 · 2026-08-07
- **MT-5.5** Tool lines: green/red dot; description for every tool (Read path clickable + line range when partial; Grep pattern) → 1.4 · 2026-08-07
- **MT-5.6** Bash IN box command / OUT box result → 1.5 · 2026-08-07
- **MT-5.7** Oversized output cut marker "⋯ +N lines · X KB not shown", never silent → 1.5 · 2026-08-07
- **MT-5.8** Failed Bash: exit-code note under OUT → 1.5 · 2026-08-07
- **MT-5.9** Non-Bash results show a summary, not blank/dump → 1.4, 1.5 · 2026-08-09 · RESOLVED 2026-08-09: fixed — plumbing wrappers stripped on both paths by one rule: `RenderLimits.PLUMBING_TAGS` + `stripPlumbing()` (list rides `LIMITS.plumbingTags` to the JS mirror), applied before the cut so the OUT cap counts real output; wrapper-only result (bare `<system-reminder></system-reminder>`) renders no OUT box; tags only, inner message kept like the TUI; RenderLimitsTest + headless 4/4 (diff-style `<`/`>` Bash output untouched)
- **MT-5.10** Tool-returned image under the tool line (Read a PNG) → 1.19 · 2026-08-07
- **MT-5.11** Path click opens editor; missing path → balloon → 1.4 · 2026-08-07
- **MT-5.12** Edit/Write diff: real gutter numbers, context rows; huge diff caps at 400 rows with marker → 1.5, 3.2 · 2026-08-07
- **MT-5.13** Todo checklist inline, updating; each Task* line carries its OWN snapshot, live = replay → 11.2 · 2026-08-09 · RESOLVED 2026-08-09: fixed — live Task* checklists appended at TURN END (`todoList`'s `el()` mounted at current position; `__tasks` frame had no correlation), so parallel TaskUpdates stacked unlabelled duplicates; `tasks` bridge request now carries `tool_use_id`, `pushTasks` echoes it, handler relocates under the requesting line (`(r.io || r.el).after(box)`), no-id falls back to append; headless 6/6
- **MT-5.14** Auto-scroll pins while streaming; scroll-up releases; scroll-to-bottom glides; submit scrolls down → 1.14 · 2026-08-09 · RESOLVED 2026-08-09: fixed — (1) `pinned = atBottom()` re-derived on EVERY scroll event unpinned mid-stream when content grew between a pin-scroll and its async event; pin is now DIRECTION-based (only an upward move leaving the bottom zone releases); (2) `__tasks` re-render never called `maybeScroll()`; headless 8/8 incl. deterministic race reconstruction; feel-check on real JCEF rAF cadence pending
- **MT-5.15** Code block language label + working copy button → 1.11 · 2026-08-07
- **MT-5.16** WebSearch turn: tool line with query, summary beneath → 1.19 · 2026-08-07
- **MT-5.17** Huge Bash result spilled to a file shows the persisted-output note **(hard to trigger)** → 1.5 · 2026-08-07

</details>

<details><summary><b>17.6 Permissions</b> — 9 items · 2 RESOLVED</summary>

- **MT-6.1** Bash card: capped command preview with cut marker, Accept/Reject → 3.1, 1.5 · 2026-08-07
- **MT-6.2** Reject → ✗ Rejected; model acknowledges → 3.1 · 2026-08-07
- **MT-6.3** Edit card shows diff preview before approval → 3.2 · 2026-08-07
- **MT-6.4** "Always allow" on CLI suggestion; compound `a && b` shows ONE Always-allow split button whose arrow lists parts → 4.4 · 2026-08-09 · RESOLVED 2026-08-09: fixed — measured on CLI 2.1.226: a compound command arrives as ONE `addRules` suggestion with one rule per sub-command (`factor 97 && mcookie ; openssl rand -hex 4 && base32` → 1 suggestion, 4 rules), so `parts.length` was always 1 and the caret never showed; `parts` now per RULE with `"sugIdx.ruleIdx"` tokens, row grant echoes the suggestion NARROWED (`ClaudeSessionService.respondPermission`; wire-probed: persisted exactly `Bash(factor 97)`); live: menu clipped by `.turn-body`'s `content-visibility: auto` paint containment → `.turn-body:has(.card-menu.show) { content-visibility: visible; }`, regression pin is `elementFromPoint` (4/4); menu resized on hover → fixed `width: 310px` + `min-width: 0` (`.popup` base has 330px min-width) matching the conversations dropdown, 32px check gutter on :hover; row tooltips: trailing `*` = CLI prefix wildcard ("any command starting with…"), none = exact
- **MT-6.5** Accept-all-edits / allow-directory buttons appear and stick (no re-ask) → 4.4 · 2026-08-07
- **MT-6.6** Sandbox-escape card (blocked_path) has NO suggestion buttons → 4.4 · 2026-08-07
- **MT-6.6b** Every structured path reads the same: relative, middle-ellipsised, click intact → 1.4, 6.1 · 2026-08-15 · RESOLVED 2026-08-15: fixed (user request) — decision card printed the FULL absolute path while the tool line showed relative; `renderPermission` and `fillAppliedCard` now fill `<code>` via the same `fillPath()`, absolute path moves to `dataset.path` + `title` (click handler reads `dataset.path` FIRST); audit found three more: @-mention menu ellipsised at the END (`…/DesktopSid…`), `notebook_path` in neither `DESC_KEYS` nor `PATH_KEYS` (NotebookEdit drew no path; CliFileSync workaround now redundant), a `__gallery` fixture bypassing `fillPath`; one shared `pathParts()`; fixture 40 → 8 steps (6+2 red on negative control), mockup mirrored; left alone deliberately: paths in free prose (todos, task/progress/info/error lines, compaction summaries, IN/OUT, `.cmd`) and Bash command text
- **MT-6.7** AskUserQuestion: tab per question, radio vs checkbox by multiSelect, Other row; submit sends; cancel → ✗ Cancelled → 1.6 · 2026-08-07
- **MT-6.8** Plan card Approve / Keep planning; ✗ Kept planning on refusal → 5.1, 5.2 · 2026-08-07

</details>

<details><summary><b>17.7 Context gauge & background tasks</b> — 7 items · 4 RESOLVED</summary>

- **MT-7.1** Gauge after first turn, plausible %; orange at ≥50% → 9.3 · 2026-08-07
- **MT-7.2** Clicking the gauge sends /compact → 9.3, 1.16 · 2026-08-07
- **MT-7.3** bg chip "1 task" while a sub-agent runs; popup lists descriptions; vanishes when done → 11.2 · 2026-08-09 · RESOLVED 2026-08-09: fixed — `chip.hidden = true` beaten by `.chip-btn { display: inline-flex }` (the "lone orange dot on a new conversation" was `.bg::before`); `.chip-btn[hidden] { display: none; }` + `chip.textContent = ''`; covers `#ctxChip`; headless 6/6 on real `background_tasks_changed` frames; END-TO-END with `sleep 30 && echo finished` over CDP at 2s: chip at +23s, gone at +52s; fixture 04 gained a computed-display assertion
- **MT-7.4** Sub-agent (Task): errand line, IN box prompt, live "Explore · … · N tool uses · N tokens", finished summary → 11.2 · 2026-08-09 · RESOLVED 2026-08-09: fixed — payloads read verbatim from `~/.local/share/claude/versions/2.1.226` (never persisted); (a) `isInternalResult()` suppresses the async-launch OUT box whose first line closes with "(This tool result is internal metadata …)" — keyed on CONTENT not tool name (the completed result is the report); (b) `stripPlumbing()` drops a leading `[harness: …]` envelope at position 0 only (CLI escapes line-initial ones in agent text to `[\harness:`); RenderLimitsTest + `tools/fixtures/07-subagent-internal-metadata.json` (13/13, 5 fail pre-fix); trap: assert on `#log` never `document.body`; (a) VERIFIED on real JCEF: "Explore · <summary> · 6 tool uses · 10.6k tokens", no OUT box; (b) not discriminable live (needs the literal `.claude/settings…json` path form)
- **MT-7.5** During a background suspend no early completion summary; one at true end → 11.2, 1.13 · 2026-08-12 · scope corrected 2026-08-12: AGENT-type only — a background SHELL (`task_type "local_bash"`) does not suspend, the CLI's `result` IS the true end and the summary SHOULD appear while the chip shows the shell (see MT-8.15, fixture 44)
- **MT-7.6** bg chip reflects what is actually running across turns AND restarts → 11.2 · 2026-08-15 · RESOLVED 2026-08-15: fixed — "chip said 2 tasks but there were none": the chip was RIGHT (two orphaned `until … sleep 30 … done` waiter loops, 2h34m and 1h31m old); real defect inverse: roster reset sat in `sendTurn` (hid live shells) and `clearLogUI` never cleared it (CLI emits the level only on membership CHANGE, per-process, nothing at startup); reset moved into `clearLogUI`; fixture 04 both directions (5 red pre-fix); real CLI `sleep 120` stayed "1 task" across a second message, new conversation cleared it; the client-parity audit had claimed this since before it was true
- **MT-7.7** Roster rows leave no lingering hover highlight → 11.2 · 2026-08-15 · RESOLVED 2026-08-15: fixed — `sel` cursor painted by document `mouseover` with no `mouseout`, plus `tg()` seeding row 0; `.bg-row` opt-out (0,3,0) beat `:hover` but only TIED `.popup-item.sel` (0,2,0) and lost on order — stuck colour `rgb(44, 57, 76)`; fix: `nosel` on `#bgMenu` honoured at all three `sel` sites + `.popup-item.bg-row.sel` opt-out; roster deliberately non-interactive; verified with REAL CEF mouse events (`rgba(0,0,0,0)`, `sel` count 0), slash menu still moves its cursor on hover

</details>

<details><summary><b>17.8 Sessions & replay</b> — 16 items · 3 RESOLVED</summary>

- **MT-8.1** History lists sessions with titles, current marked; no search box (correct) → 8.2 · 2026-08-07 · NB: with a non-default model persisted, untitled sessions can derive "/model haiku" as title — ACCEPTED, not a bug; ai-title replaces it after the first real turn
- **MT-8.2** Resume matches live: ✓ Applied diffs, resolved asks, thinking durations, plan cards, ⏹ Stopped, summaries with the SAME verbs → 8.6 · 2026-08-09 · RESOLVED 2026-08-09: fixed — transcript persists the same top-level `error` enum live keys off (`"error":"rate_limit","isApiErrorMessage":true`, built via `kd({error, content})` in 2.1.226); (1) `RenderLimits.AUTH_BLOCKED_CODES` → SessionStore `status` item `icon:"auth"` carrying the CODE, resolved through live's AUTH_BLOCKED map, emitted before the error; transient codes get no status line; (2) `reqError` suppresses `flushSummary`'s phantom done item on an error-terminated request; RenderLimitsTest + `tools/fixtures/08-resumed-error-tail.json` (8/8, 3 fail pre-fix); not eyeballed on real JCEF (needs a re-auth-fail or stitched error tail)
- **MT-8.3** Big session opens fast, lands at bottom → 8.6 · 2026-08-07
- **MT-8.4** Scroll-up streams in earlier history, no viewport jump → 8.6 · 2026-08-07
- **MT-8.5** Long user message sticky while its turn scrolls → 1.14 · 2026-08-07
- **MT-8.6** Replayed images: recent show bytes, old degrade to name-only chips → 6.2, 8.6 · 2026-08-08 · NB: chips reading "file.jpg" with a smaller size are EXPECTED — the transcript persists the bare API image block, recompressed, no filename field (verified against records 2026-08-08)
- **MT-8.7** Error at the tail of a resumed session shows error block + working Retry → 1.8, 8.6 · 2026-08-09 · RESOLVED 2026-08-09: fixed by MT-8.2 — `renderTranscript` seeds Retry only when `items[last].role === 'error'` and the phantom summary was the tail; no 8.7-specific code; fixture 08
- **MT-8.8** /effort turns DO appear on resume (accepted audit trail, not a bug; superseded 2026-08-25 — now only the CLI's confirmation line, live and resumed) → 9.2 · 2026-08-07
- **MT-8.9** New conversation from a resumed windowed session doesn't pull old chunks → 8.6, 8.1 · 2026-08-07
- **MT-8.10** Session renamed in the terminal (custom title) beats the derived title in history **(hard to trigger:** run `/rename` in a terminal `claude` session on the same project, then open the history panel) → 8.2, 8.4 · 2026-08-07
- **MT-8.11** Delete from history: confirm step, row gone, file gone; CURRENT row offers no delete → 8.5 · 2026-08-07
- **MT-8.12** Image chip → lightbox (Esc/click closes); PDF/text chip → native Save As → 6.2, 1.19 · 2026-08-07
- **MT-8.13** Header rename editor: any outside click DISCARDS (like Esc and ✕); composer, open popups and neighbouring header controls stopPropagation so dismissal listens on CAPTURE; ✓/Enter commit; leaving the IDE panel leaves it open → 8.4 · 2026-08-07
- **MT-8.14** MCP `browser_evaluate` renders like Bash: blank tool line, JS body in a folded IN box scrolling sideways; long description (big ToolSearch query) on ONE line with ellipsis + hover title; path lines still ellipsise in the MIDDLE → 1.4, 1.5 · 2026-08-07
- **MT-8.15** Background shell lifecycle (`Bash run_in_background:true`): turn finalizes with summary while chip shows the task; when the shell ends the notification turn arrives with NO user frame and Stop must be reachable → 11.2, 1.7 · 2026-08-13 · Confirmed 2026-08-13 on real CLI (`sleep 30`), seconds from send: 3.54 `background_tasks_changed` (`local_bash`, "1 task") · 4.84 `result` busy→false, chip still showing · 33.56 shell exits, `task_notification` · 33.59 `system/init` (CLI opens a NEW request) · 35.84 `message_start` busy→TRUE, Stop · 36.23 `result`; 0 deltas rendered while the button read Send; two done lines 123 and 20 tokens (request-scoped reset works); fixture 44
- **MT-8.16** Header title during the FIRST turn: with the turn held open (backgrounded `sleep`, wide search) open history — header and `current` row read the same; `location.reload()` over CDP mid-session restores title, mode chip, model chip, slash menu, project-relative paths → 8.3, 7.10 · 2026-08-13 · RESOLVED 2026-08-13: fixed (user screenshot: "New conversation" beside a titled row; `D--sites-accesshealth/ccafeb52-….jsonl`, first prompt 04:39:10Z, next 05:40:30Z, no `ai-title`/`custom-title`/`summary`) — `pushTitle` only fired at `result`; fix: once-per-turn probe at `message_start` while unnamed + `seedUi()` on every load; timing on CLI 2.1.229 (`_local/title_timing.py`): at `system/init` the transcript is MISSING, by `message_start` it exists (15,047 B); negative control: `match:false`, 0 result frames, post-reload `slashCommands` 0 / `projectRoot` ""; post-fix `match:true`, 39 / real root; harness 154/154

</details>

<details><summary><b>17.9 Resilience & notices</b> — 10 items · 2 RESOLVED</summary>

- **MT-9.1** API retry storm: "… — retrying (n/m)" lines, not a silent stall **(hard to trigger:** disconnect the network mid-turn; a real network-off storm was user-verified) → 1.15 · 2026-08-09 · RESOLVED 2026-08-09: fixed, premise corrected — `api_retry`'s `error` is a five-code enum (529→`overloaded`, 429→`rate_limit`, 401|403→`authentication_failed`, ≥408→`server_error`, else `unknown` = no-status network failure); rich text is TUI-in-process only; `RETRY_REASONS` in chat.html ("API error — retrying (1/10)" for unknown), unknown codes degrade to raw; duplicate "(1/10)" = raw `api_error` + `api_retry` twin per attempt, deduped by consecutive attempt/max key (`retrySeen`, last-key not a Set so a restarted storm shows); fixture 09 (8 fail pre-fix, 11/11 after; 71/71 headless AND on real JCEF); live: ten "API error — retrying (n/10)" lines, then "Unable to connect to API (ENOTIMP)", Retry seeded, resend succeeded · replay half same day: CLI persists the concluding error BEFORE the buffered `api_error` records (session `afe39ca0…`: error at position 21 / 09:47:24, retries 24–33 / 09:44:20–09:46:45) — SessionStore inserts a retry timestamped before the last error item ahead of it (and its auth status), younger-than-error guard; replay wording richer by design (`error.formatted` persisted); pinned by `late-flushed retry records replay before the error that ended their storm`, `./gradlew probe`: user → retries 1–10 → error → resend
- **MT-9.2** Auth failure (bad `ANTHROPIC_API_KEY` in the sandbox env) → "sign in from a terminal" → 1.15, 10.1 · 2026-08-07
- **MT-9.3** CLI death → "claude process exited (N)" + stderr tail → 1.15 · 2026-08-07
- **MT-9.4** MCP server fails to start → "MCP servers: X failed to start …" → 11.2 · 2026-08-07
- **MT-9.5** Hook blocking a prompt (exit 2) → visible notice **(hard to trigger:** a `UserPromptSubmit`/`PreToolUse` hook exiting 2) → 1.18 · 2026-08-07
- **MT-9.6** Rate-limit warning with reset time **(hard to trigger — unforceable by design:** inject the `tools/fixtures/16-rate-limit-event.json` frame shape + `resetsAt` through `onClaudeEvent` in the LIVE webview via `cdp.py`) → 1.17 · 2026-08-08 · rendered "You've hit your session limit · resets in 3h"; render path only
- **MT-9.7** Interrupt mid-turn then resume: replays as ⏹ Stopped, not a fake user message → 1.7, 8.6 · 2026-08-07
- **MT-9.8** Model refusal with fallback: retracted content withdrawn with a notice; session model NOT silently changed **(hard to trigger:** inject a scope:'local' `system/model_refusal_fallback` frame — the VS-Code-bundle wire shape — via `cdp.py` against a REAL stamped assistant block) → 1.15 · 2026-08-08 · block evicted, both notices rendered, chip unchanged; render path only, no real refusal ever captured
- **MT-9.9** Editing a file yourself while Claude edits it → tool line notes the file changed underneath, not bare ✓ Applied **(hard to trigger:** modify the target in the editor between the Edit's read and its apply) → 3.4 · 2026-08-07
- **MT-9.10** Commands discovered mid-session refresh the / roster → 7.2 · 2026-08-15 · RESOLVED 2026-08-15: fixed — CLI 2.1.228 WATCHES the project commands dir and pushes `commands_changed` itself (~2.5s after a file drop, ~1s after deletion, 45s quiet wait measured) — no longer hard to trigger; `/reload-skills` (now Enabled) is the manual fallback; user pass: dropped `fresh-cmd.md` appeared badged "project" without `/reload-skills`, deletion removed it; watch is PROJECT-dir only — `~/.claude/commands/` needed `/reload-skills` ("user" badge correct); payload carries the same suffixes as initialize so roster REPLACE re-syncs enablement; corrected the 2026-08-08 "no roster change" and a same-day probe that sent `/reload-skills` inside the debounce window

</details>

<details><summary><b>17.10 IDE bridge</b> — 5 items · 3 RESOLVED</summary>

- **MT-10.1** Bridge `openFile` works over MCP-over-WS (editor tab opened 2026-08-08) → 2.1 · 2026-08-09 · RESOLVED 2026-08-09: not a bug, upstream by design, re-scoped to the bridge half — the CLI tools listing applies `!name.startsWith("mcp__ide__") || ["mcp__ide__executeCode","mcp__ide__getDiagnostics"].includes(name)`, IDENTICAL across 2.1.222/223/226 (VS Code models get the same two); sub-agents strip the whole ide client; rejected workaround: a non-"ide" server name dodges the prefix but the CLI finds its IDE client by `name === "ide"` for the TUI diff-in-IDE flow
- **MT-10.2** Model reads editor diagnostics ("what errors are in this file?") → 2.3 · 2026-08-07
- **MT-10.3** Bridge `getCurrentSelection` works over MCP-over-WS (exact highlighted text + path, 2026-08-08) → 2.1 · 2026-08-09 · RESOLVED 2026-08-09: not a bug, upstream by design — same allowlist as MT-10.1; auto-including selection is a separate deferred feature (6.6)
- **MT-10.4** openDiff shows a diff view — verified 2026-08-08 by `tools/call openDiff` over the bridge WS directly (model cannot call it on 2.1.226) → 2.2 · 2026-08-08
- **MT-10.5** Accept/reject in the editor diff honoured (accept saves, reject leaves untouched and the model is told) → 2.2, 3.3 · 2026-08-09 · RESOLVED 2026-08-09: fixed, premise corrected — "never writes" was NOT a bug: VS Code 2.1.222 builds both panes as temp-provider documents and returns FILE_SAVED + the right pane's `getText()`; the CLI 2.1.226 maps the verdict to {oldContent, newContent} and writes via its own Edit/Write (TAB_CLOSED → accept-as-proposed, DIFF_REJECTED → keep old); FILE_SAVED is the accept TOKEN; fixed in `DiffReview.kt`: TAB_CLOSED verdict didn't exist (caller blocked forever — now `onAssigned(false)`, debounced 500 ms for side-by-side↔unified switches), accept returned the proposal not the pane text (tweaks now travel), close_tab/closeAllDiffTabs now complete TAB_CLOSED, balloons expire with the future, dismissal no longer auto-rejects, dying WS / shutdown cancels reviews (`IdeMcpServer.onClose`); verified over MCP-over-WS: ["TAB_CLOSED"] / ["FILE_SAVED", pane text] / ["DIFF_REJECTED", tab_name], file untouched on disk each time, no stale balloons

</details>

<details><summary><b>17.11 Dev aids</b> — 2 items · 2 RESOLVED</summary>

- **MT-11.1** `window.__gallery()` renders all transient states, no console errors → 16.3 · 2026-08-09 · RESOLVED 2026-08-09: removed — Ctrl+Alt+G chord removed rather than fixed; call from DevTools or CDP; 19 tool lines, 5 cards, 2 asks, 2 diffs, todos, cut markers; only the benign ResizeObserver-loop warning
- **MT-11.2** Find Action "Claude Brains: Open DevTools" opens DevTools; http://localhost:9222 lists "Claude Brains — chat panel" → 12.2, 16.3 · 2026-08-09 · RESOLVED 2026-08-09: removed — F12 chord removed (this machine is a "F12 dies inside JCEF" setup per plugin.xml); Find Action is the only route; the 9222 route carried every CDP probe of the pass

</details>
