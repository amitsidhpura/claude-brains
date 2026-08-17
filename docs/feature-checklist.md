# Claude Brains — feature parity checklist

What the plugin (`plugin/`) has, what it could have, and what it has decided not to have —
one row per feature, measured against both reference clients.

**References** (both on 2.1.233, last full re-audit 2026-08-17)
- VS Code extension — `~/.vscode/extensions/anthropic.claude-code-2.1.233-linux-x64/`
- Terminal TUI / CLI — `~/.local/share/claude/versions/2.1.233`; headless roster in `docs/slash-commands.md`
- Data-level parity (every CLI event we drop or render) lives in `docs/client-parity.md`, not here

**Status marks**

| Mark | Meaning |
|---|---|
| ✅ | done |
| 🟡 | partial |
| 🟥 | open — high: next up, or the strongest candidates awaiting a decision |
| 🟧 | open — medium: roadmap, worth a pass when its turn comes |
| 🟨 | open — low: watch / probe first / polish; some lean ➖ |
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
| **[NEW]** | new or newly noticed in the 2.1.222 → 2.1.233 re-audit |
| **[DECIDE]** | open row awaiting the user's yes / later / no (yes → `state.md`, later → `backlog.md`, no → re-mark ➖/🚫) |

**Scope rule** — *"Develop in the IDE. Configure in the Terminal."* Reached for many times an
hour while writing code? Yes → panel (🟥🟧🟨 until built). No → terminal (➖).

**Work order** (from `.claude/context/backlog.md`): plan-card shortcuts (if un-deferred) →
reloaded-webview log replay → kill-background-process → editor accept/reject v2 tweak-travel →
@-symbol mentions → worktrees → extensibility status view. Last, by the user's choice: tabs,
auto-include selection, voice.

---

## 1. Core chat & streaming
- **1.1** ✅ Send a prompt; stream the reply token-by-token (`stream_event` deltas)
- **1.2** ✅ User vs assistant bubbles; **Ctrl+Enter sends, Enter = newline** (VS Code's
      `useCtrlEnterToSend` behaviour, fixed on — no toggle)
- **1.3** ✅ Composer prompt **history** (ArrowUp on the first line recalls earlier prompts)
- **1.4** ✅ Tool-use lines with a shared description chain (`DESC_KEYS`/`IN_KEYS` in
      `RenderLimits.kt`), project-relative middle-ellipsised paths, click-to-open with line ranges
- **1.5** ✅ **IN/OUT boxes** for tool input/output with the diff's geometry, size caps and
      `.io-cut`/`.cmd-cut` truncation markers (`docs/limits.md`)
- **1.6** ✅ **AskUserQuestion** card (radio/checkbox + free-text "other" → `updatedInput`)
- **1.7** ✅ **Stop / interrupt** (Send↔Stop toggle → `interrupt`); mid-turn Stop replays as the
      stopped line, not a user message
- **1.8** ✅ **Retry** last turn (re-runs prompt + attachments)
- **1.9** ✅ **Queued messages** while busy (`#queue`, drained at `result`; replay shows mid-turn
      steered messages as user bubbles)
- **1.10** ✅ Markdown (headings, lists, links, code, blockquote, hr, GFM tables with alignment)
- **1.11** ✅ Code blocks: language label, copy button, **offline syntax highlighter** (keywords /
      strings / comments / numbers / php-vars — deliberately not a full grammar bundle)
- **1.12** ✅ **Thinking** blocks (collapsible), real thinking-token count when the event carries it
      (chars/4 fallback)
- **1.13** ✅ Streaming **"generating" verb** line + **in-flight gutter dot** (pulsing → green/red;
      `prefers-reduced-motion` honoured — OS propagation into JCEF still unverified, backlog)
- **1.14** ✅ Auto-scroll pinning keyed off scroll DIRECTION; top/bottom fades; scroll-to-bottom button
- **1.15** ✅ Error surfacing: `result.is_error`, `system/api_error` **retry banner** (both spellings,
      deduped double-emits), model-refusal fallback (`supersedes` / retracted uuids), CLI stderr
      tail under the exit line, auth failure → "sign in from a terminal"
- **1.16** ✅ **Compaction**: boundary marker + folded summary + gauge reset; "Compacting…" verb;
      failed `/compact` reported (`system/status`)
- **1.17** ✅ **Rate-limit warnings** (`LIMIT_LABELS`, 2.1.222 vocabulary; fringe types fall back to
      the generic label)
- **1.18** ✅ **Hook activity** — blocked tools + `informational` hook output rendered
- **1.19** ✅ Server-side tool blocks (web search); tool-returned **images** (`isImage` Bash results)
      in the lightbox
- **1.20** ✅ Local-command output (`<local-command-stdout>` / `-stderr>`) rendered as markdown
- **1.21** 🟨 [XS] `redacted_thinking` placeholder — never seen locally
- **1.22** 🟨 [SM] `tool_progress` heartbeat ("still running · 45s") — not yet emitted to stream-json
      clients; probe first (client-parity § not taken)
- **1.23** 🟨 [SM] `permission_denied` reason / `decision_reason` on the permission card — P3 polish
- **1.24** 🟨 [SM] `result.terminal_reason` (19 values) surfaced when a turn ends oddly — P3 polish

## 2. Editor / IDE integration — the IDE-MCP tool set (12 tools, unchanged in 2.1.233)
- **2.1** ✅ `getWorkspaceFolders`, `getOpenEditors`, `getCurrentSelection`, `getLatestSelection`,
      `openFile`, `saveDocument`, `checkDocumentDirty`, `closeAllDiffTabs`
- **2.2** ✅ `openDiff` — real `DiffManager` view; three-verdict `DiffReview` contract
      (FILE_SAVED / FILE_SAVED_ALL / DIFF_REJECTED, `docs/ide-mcp-protocol.md` § 4)
- **2.3** ✅ `getDiagnostics` — via `DaemonCodeAnalyzerImpl.getHighlights` (warnings and up)
- **2.4** ✅ `close_tab` — closes the one review opened under that `tab_name` (2026-08-17; it used
      to sweep every diff tab); both close tools reply with the reference's exact strings
      (`TAB_CLOSED` / `CLOSED_<n>_DIFF_TABS`)
- **2.5** ➖ `executeCode` (Jupyter) — stubbed reply; no Jupyter kernel in PhpStorm's remit
- **2.6** ✅ Bridge: free port, `~/.claude/ide/<port>.lock`, WS MCP server + auth header +
      `mcp` subprotocol, handed to the CLI via `--mcp-config` (not env discovery — gotchas)
- **2.7** ✅ Tool calls dispatched async; VFS/PSI reads under `readLocked {}`
- **2.8** ✅ **Files the CLI edits land in open editors** without "Reload from disk" —
      `CliFileSync` + `Vfs.refreshFromDisk` (per-tool refresh + turn-end root sweep, 2026-08-14).
      VS Code gets this from its own FS watcher; here it had to be built
- **2.9** ✅ **Login-shell environment** captured once per IDE run (`ShellEnv.kt`) so nvm/PATH-
      dependent MCP servers start under a GUI-launched IDE
- **2.10** ✅ **Autosave before read/write** — a host-registered SDK `PreToolUse` hook on
      `Edit|Write|MultiEdit|Read` (declared on `initialize`, answered after `saveDocument` on the
      EDT; `Autosave.kt`). Same mechanism as VS Code's `claudeCode.autosave`; always on. Verified
      live 2026-08-17: an unsaved `ZEBRA-43` buffer was what `Read` returned
- **2.11** ✅ Stale `~/.claude/ide/*.lock` files swept on every lock write (dead pid → delete, the
      CLI's own rule; `IdeLockFile.sweepStale`). 17 → 2 on first run, 2026-08-17

## 3. Diffs & edit approval
- **3.1** ✅ **Permission gate** via `can_use_tool` (`--permission-prompt-tool stdio`)
- **3.2** ✅ **Accept / Reject card** with the diff inline (old→new for Edit/MultiEdit multi-hunk,
      additions for Write); under acceptEdits the diff is built optimistically from the tool input
- **3.3** ✅ **Editor accept/reject** — dual surface: the same edit shows as a card AND an IDE diff
      tab with an under-diff bar (Accept ✓ / Accept all edits / Reject ✕); first answer wins
      (2026-08-09). Replaces VS Code's editor-title buttons; **no keyboard shortcuts by design**
- **3.4** ✅ "File was modified by the user" (`staleRecovered`) surfaced
- **3.5** 🟥 [LG] **In-diff editing before accept** (tweak-travel: pane edits ride `updatedInput` on
      accept) — roadmap, second half of accept/reject v2
- **3.6** 🟧 [LG] **Multi-file change review** (VS Code `open_file_diffs`) **[NEW]** — roadmap; a per-turn "files
      changed" surface would be the shape

## 4. Permission modes
- **4.1** ✅ Mode chip with the CLI's own four modes — **manual** (`default`, aliased in the chip),
      **acceptEdits**, **plan**, **auto** (the safety-classifier mode) via `set_permission_mode`
- **4.2** ➖ `bypassPermissions` — removed 2026-08-03 with the relaunch machinery; the CLI refuses
      to be raised into it at runtime and Auto is the intended equivalent
- **4.3** ✅ Chip driven by the CLI's `permissionMode` broadcasts (init/status); refused control
      requests surface as error blocks
- **4.4** ✅ **"Don't ask again" buttons** from `permission_suggestions` (compound `addRules` merged
      into one button — VS Code does not); hidden on `blocked_path` prompts
- **4.5** ✅ Mode persists across restarts and New/Refresh/resume; launched with the persisted mode
- **4.6** ➖ `allowDangerouslySkipPermissions` / `initialPermissionMode` settings — the flag turns
      every mode into a bypass (probed); persistence covers the initial-mode need

## 5. Plan mode
- **5.1** ✅ Enter plan mode from the chip; plan rendered as a **plan card** on `ExitPlanMode`
- **5.2** ✅ **Feedback field on the card** riding whichever button answers: deny → verbatim
      tool_result; approve → appended under `PLAN_NOTES_MARKER` via `updatedInput.plan`
      (same-message delivery, timing-equivalent to the TUI's shift+tab; 2026-08-16)
- **5.3** ✅ **Split Approve**: approve · approve + auto-edit · approve + auto — mode rows park in
      `pendingPlanMode` and bridge only after the CLI's post-approval mode broadcast
- **5.4** ✅ Replay: plan + quoted feedback footer (`fbQuote`), the note parsed back out of
      `toolUseResult.plan`
- **5.5** 🚫 Keyboard shortcuts on the card (Enter = keep planning, Shift+Tab = approve) — deferred
      by the user 2026-08-16; backlog § Next up
- **5.6** 🟧 [LG] **Plan preview with inline comments** (VS Code `open_markdown_preview` +
      `plan_comment` / `get_plan_comments` / `remove_plan_comment`). Take: the feedback field
      covers the decision loop; revisit only if plan-heavy workflows demand threaded comments
- **5.7** ➖ `/ultraplan` (cloud-drafted plan) — a cloud product surface, not a panel feature

## 6. Context input
- **6.1** ✅ **@-mention files** (fuzzy autocomplete, keyboard nav, dismissal contract shared with
      the slash menu, ellipsis at the end)
- **6.2** ✅ **Attachments**: images (`image` blocks) + PDF / text / code (`document` blocks with
      titles); paste; **drag-and-drop** via an AWT `DropTarget` (JCEF never delivers OS drags);
      chips preview in the lightbox or save via the IDE dialog
- **6.3** ✅ Injected IDE context (`<ide_selection>` etc.) stripped on replay
- **6.4** 🟧 [MD] @-mention **symbols** — roadmap
- **6.5** 🟨 [SM] **Alt+K / "Insert @-mention" from the editor** — needs a plugin action; the plugin
      binds no shortcuts today, but an unbound action the user can map is compatible with that
- **6.6** 🚫 Auto-include current **selection** on ask — deferred by the user (do last)
- **6.7** 🟨 [SM] VS Code `list_files_request` **[NEW]** / `respectGitIgnore` in the picker — our picker is
      IDE-indexed; ➖ unless a real gap shows
- **6.8** ➖ `@terminal` (`get_terminal_contents`) **[NEW]** — the panel does not own a terminal

## 7. Slash commands (`docs/slash-commands.md` is the source of truth)
- **7.1** ✅ Menu on `/`, keyboard nav, descriptions, source badges (project / user / mcp)
- **7.2** ✅ Roster from the `initialize` control request; refreshed by `commands_changed`
      (project-dir watcher fires by itself; user-dir needs `/reload-skills`)
- **7.3** ✅ **Custom commands, skills, MCP prompts auto-enabled** (detected by the `" (project)"` /
      `" (user)"` suffix — the wire has no type field)
- **7.4** ✅ **16 built-ins enabled**, each driven through the live panel; the rest Hidden by group
- **7.5** ✅ Pick rule: a command with ANY `argumentHint` inserts `/name ` and waits; hint-less runs
      (2026-08-16; the roster carries no `immediate` flag)
- **7.6** ✅ `/clear` native (→ new conversation)
- **7.7** ✅ `aliases` are first-class: the menu filter scores them like names, rows show them
      muted (`.pi-alias`), and a typed alias resolves to its command before the allowlist gate
      (`canonicalCmd`; `/review` → `/code-review`, `/new` → `/clear`). Fixture 52, 2026-08-17
- **7.8** ➖ TUI-only commands (never on the headless roster): `/login /logout /resume /help
      /add-dir /rewind /diff /update /theme /vim /keybindings /export /copy /bug /feedback
      /memory /permissions /hooks /mcp /plugin /agents /doctor /status /config /ide /terminal-setup
      /voice /desktop /mobile /teleport /remote-control /background /branch /fork /btw /tasks
      /skills /skill-doctor /pause-memory /alias /advisor /focus /brief /wellbeing /radio …` —
      the terminal's half by construction. Where the panel has an equivalent it is listed in
      its own section (rename, model, effort, mode, resume, tasks, focus)
- **7.9** ✅ TUI commands that already have a panel equivalent: `/rename` (header pencil), `/model` +
      `/effort` (chips), `/tasks` (bg roster, read-only), `/resume` (history), `/clear` (New)
- **7.10** ✅ Reloaded-webview roster: `ChatPanel` keeps the newest `commands_changed` frame from
      the current CLI and replays it after the init seed on every page load (cleared on a fresh
      `initialize`). Driven live over CDP 2026-08-17: a mid-session command survived `location.reload()`

## 8. Sessions / history
- **8.1** ✅ New conversation; **Resume** (history list → `--resume <id>`); **Refresh** (re-resume,
      recovers a dead CLI, guarded by `SessionStore.exists`)
- **8.2** ✅ History list from `~/.claude/projects/<enc-cwd>/*.jsonl` — titles from `custom-title`
      (newest rename wins) → summary → first user message
- **8.3** ✅ Header shows the title as soon as the transcript can name it (probe at `message_start`,
      re-read at every `result`; `seedUi()` on every load)
- **8.4** ✅ **Rename** in place (header pencil → the CLI's own `custom-title` record = `/rename`)
- **8.5** ✅ **Delete** any conversation, the live one via leave-first (`awaitExit`)
- **8.6** ✅ Replay through the same block builders as live (`docs/renderer-parity.md`); windowed
      with an aligned turn-boundary cut, "N earlier blocks not loaded" marker and **load-earlier
      on scroll-up**; interrupt markers, sub-agent lines, plan feedback, steered messages all replay
- **8.7** 🟥 [LG] **Rewind / checkpoints + fork conversation** **[DECIDE]** — the one substantial capability gap
      vs both clients (VS Code `rewind_code` / `fork_conversation`; TUI `/rewind`, `/branch`,
      `/fork`). Per-turn file rewind was REMOVED 2026-07-30; revival notes in gotchas § Protocol
      (`CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1`, git repo, client uuids via `stampMessage`,
      dry_run first). **Status: still UNDECIDED — needs an explicit yes / later / no**
- **8.8** 🟨 [MD] Reopen closed session (Ctrl+Shift+T) — ➖ while the plugin binds no shortcuts and has
      no tabs
- **8.9** 🚫 Multiple conversation **tabs** — deferred by the user (do last)
- **8.10** 🟨 [LG] **Session groups / sessions sidebar** **[NEW · DECIDE]** — a `claude-sessions-sidebar`
      view, `get_session_groups` / `update_session_groups`, `list_sessions_request`. Take: our
      history popup already lists per-project sessions; grouping is only worth it once tabs or
      worktrees exist. Watch
- **8.11** 🟥 [MD] **Side question** **[NEW · DECIDE]** (`side_question` host message; TUI `/btw`) — ask without disturbing
      the main thread. Take: hourly-plausible; needs a probe of the wire shape first. Candidate
- **8.12** ➖ **Remote sessions / teleport / remote control** (`list_remote_sessions`,
      `teleport_session`, `toggle_remote_control`; TUI `/teleport`, `/rc`, `/session`) — session
      SOURCES beyond the local disk lean infrastructure; recorded as a judgment call
- **8.13** ➖ `generate_session_title` **[NEW]** — the CLI names threads itself (`ai-title`) and we show it
- **8.14** 🟥 [LG] Reloaded-webview **log** replay (chrome heals via `seedUi()`, the log does not) —
      backlog § Next up; no reload observed in the wild yet

## 9. Model, effort, usage
- **9.1** ✅ **Model chip** (from `initialize.models`, search + custom id) → `set_model`; persisted
      and re-applied on every restart
- **9.2** ✅ **Effort slider** (low / medium / high / xhigh / max) — sends a muted `/effort` turn
      because no `set_effort` control exists; the resumed transcript shows the turn (accepted
      audit trail). Watch-item: `apply_flag_settings{effortLevel}` could retire the hack
- **9.3** ✅ **Context gauge** ring on the composer — `modelUsage[].contextWindow` (a map, side
      models included), reset on compaction
- **9.4** 🟥 [SM] **Fast mode toggle** **[DECIDE]** (`fastMode` on `initialize` + `result.fast_mode_state`, both already
      received; TUI `/fast`, Opus only). Take: cheap, sibling of the model chip — candidate
- **9.5** 🟧 [SM] **Thinking level** **[NEW · DECIDE]** — host message `set_thinking_level`. Take: probe
      whether it is a control request we can send; likely redundant beside the effort slider
- **9.6** ➖ Cost / token breakdown / usage panel (`get_usage`, `get_context_usage`,
      `request_usage_update` **[NEW]**; TUI `/usage`, `/cost`, `/context`) — declined 2026-08-06; the
      built-in `/context` is enabled as a turn for the rare look
- **9.7** 🟨 [MD] Fable overage / `model_consent_fallback` gate — no `supportedDialogKinds` declared, so
      the CLI stays silent; the model chip could lie if the gate fires. Probe by exercising it
- **9.8** ➖ Subagent model (`CLAUDE_CODE_SUBAGENT_MODEL`), Bedrock / Vertex / Foundry setup — env
      and terminal configuration

## 10. Auth & account
- **10.1** ➖ Login / logout / account display / `disableLoginPrompt` — **by design**: sign in once by
      running `claude`; the panel reports an auth failure with a "sign in from a terminal" line.
      Confirmed a decision, not a missing capability (`claude_authenticate` + OAuth callbacks
      exist in the CLI)

## 11. Extensibility (MCP / plugins / skills / hooks / subagents)
- **11.1** ➖ Plugin install / marketplaces / MCP server management / hooks / agents UI — the
      terminal's half (`/plugin`, `/mcp`, `/hooks`, `/agents`, `~/.claude`)
- **11.2** ✅ What the panel DOES show from this family: MCP server failure notice at init
      (`mcp_servers[].status`), MCP prompts in the / menu, skills in the / menu, hook output,
      **sub-agent progress line + prompt + final report**, **background task roster** (`bg` chip,
      reset at the CLI boundary), Todo/Task checklist (`TodoWrite` + the Kotlin task store)
- **11.3** 🟥 [MD] **Kill a background process from the panel** — roster rows are display-only; the CLI
      has no host-side control request for it (only the model's `TaskStop`). Backlog § Next up;
      protocol check first
- **11.4** 🟧 [MD] Sub-agent WORK outcome (the dot was built and withdrawn 2026-08-13) — known gap
- **11.5** 🟨 [SM] Elicitation (`elicitation` control request) — our empty ack answers it with neither
      decline nor an answer; no local MCP server elicits today. Probe if one ever does
- **11.6** 🟧 [MD] Extensibility **status view** (read-only: which MCP servers/plugins are live) — roadmap
      tail; philosophy leans terminal

## 12. UI placement, windows, keys
- **12.1** ✅ Right-anchored tool window (JetBrains moves/floats/undocks it natively — covers
      `preferredLocation`, sidebar/panel, new window)
- **12.2** ✅ Dev aid: "Claude Brains: Open DevTools" action (Find Action; no default chord)
- **12.3** 🟨 [MD] Open in **editor tab** **[NEW · DECIDE]** (`editor.open` / `primaryEditor.open`) — a
      second host for the same webview; only worth it if someone wants a wide chat. Watch
- **12.4** ➖ Focus / blur input shortcut, Ctrl+N new conversation, Ctrl+Shift+T — **the plugin binds
      NO keyboard shortcuts** (2026-08-09, chords proved unreliable across setups). Users can
      map the tool window's own Show action in Keymap
- **12.5** ➖ Open in **terminal** mode (`useTerminal`) — that is just the terminal
- **12.6** 🟧 [MD] **Focus view** **[NEW · DECIDE]** (VS Code `toggleFocusView` / `set_focus_view` / `focusView` setting; TUI
      `/focus`, `/brief`) — hide tool noise, show prompts + responses. Take: worth a mockup pass
      before building; folded IN/OUT boxes already do half of it
- **12.7** 🚫 Light theme / configurable colours — decided 2026-08-07 (dark only)

## 13. Settings
- **13.1** ➖ **No settings page by design.** CLI resolution: `-Dclaude.executable` → PATH → VS Code
      extension binary. Model, mode, effort persist via `PropertiesComponent`. VS Code's
      `claudeCode.*` keys map as: `environmentVariables` / `claudeProcessWrapper` /
      `respectGitIgnore` / `usePythonEnvironment` / `disableLoginPrompt` → terminal & env;
      `useCtrlEnterToSend` → fixed on; `initialPermissionMode` → persistence; `preferredLocation`
      / shortcuts / `hideOnboarding` / `focusView` → see §12; `autosave` → §2 candidate
- **13.2** 🟧 [SM] JSON schema for `.claude/settings.json` **[DECIDE]** (`claude-code-settings.schema.json` shipped by
      VS Code) — a JetBrains `JsonSchemaProviderFactory` is small and editor-hourly for anyone
      editing settings. Take: worth doing, unrelated to the panel

## 14. Worktrees & git
- **14.1** 🟧 [LG] **Create worktree** (`create_worktree`; TUI `/branch`) — roadmap
- **14.2** 🟨 [MD] Git actions in the host **[NEW · DECIDE]** — `checkout_branch`, `check_git_status`,
      `update_skipped_branch`. Take: read the webview flow before deciding; probably belongs
      with worktrees
- **14.3** 🟧 [LG] Git-aware diff/context — roadmap

## 15. Onboarding & misc
- **15.1** ➖ Walkthrough / onboarding checklist / upsell and terminal banners (`dismiss_review_upsell_banner` **[NEW]**) / `update` /
      `showLogs` — JetBrains handles updates; the README is the walkthrough
- **15.2** 🚫 Voice input / speech-to-text **[NEW]** (`start_speech_to_text`; TUI `/voice`) — deferred by the
      user (do last)
- **15.3** ➖ Message rating + `/feedback` / `/bug`, prompt suggestions, Artifact auto-open, "Explored"
      grouping of consecutive Reads, `/stickers`, `/radio`, `/powerup` — small chrome, leaning no
- **15.4** ➖ `/share` / `/export` / `/copy` — the transcript is on disk; the terminal exports it
- **15.5** 🟨 [LG] `ask_debugger_help` **[NEW]** (VS Code hands the debug console to Claude) — no
      PhpStorm analog probed; ➖ until someone asks
- **15.6** 🟨 [SM] Chrome MCP / Jupyter MCP toggles **[NEW]** (`ensure_chrome_mcp_enabled`, `enable_jupyter_mcp`) —
      configuration; ➖

## 16. Quality gates (not features, but part of "what we have")
- **16.1** ✅ `./gradlew test` (106, JUnit 5 over SessionStore/RenderLimits) — every suite's negative
      control RUN
- **16.2** ✅ Live harness `tools/live_harness.py` — 21 fixtures (numbered to 51), **344** assertions, real captured
      wire frames replayed into the live webview over CDP
- **16.3** ✅ `./gradlew probe` (replay without the IDE); `tools/cdp.py`; `window.__gallery()`;
      DevTools action; `runIde -PjcefDebugPort` (sandbox Registry still wins — gotchas)
- **16.4** ✅ Plugin Verifier 0 warnings on PhpStorm 242→262; Marketplace upload automated on
      `release: published`; releases 0.4.0 → 0.7.2 all Approved
- **16.5** ✅ Standing manual-test checklist (`docs/manual-test.md`, 92 items, register 0 open)
