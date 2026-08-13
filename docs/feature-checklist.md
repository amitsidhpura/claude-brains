# Claude Code for PhpStorm — feature parity checklist

Derived from the extracted VS Code extension v2.1.220 (`vscode/package.json`, `webview/index.js`,
`extension.js`). Status is for **our** PhpStorm plugin (`plugin/`).

Legend: ✅ done · 🟡 partial · ⬜ not started · ➖ N/A for JetBrains

Work order suggestion in **§ Milestones** at the bottom.

---

## 1. Core chat & streaming
- [x] ✅ Send a prompt to the CLI
- [x] ✅ Stream the assistant reply token-by-token (`stream_event` deltas)
- [x] ✅ Render user vs assistant bubbles
- [x] ✅ Tool-use chips (name shown when Claude calls a tool)
- [x] ✅ **AskUserQuestion** interactive card (radio/checkbox options → answer via updatedInput)
- [x] ✅ **Stop / interrupt** an in-flight response (Send↔Stop toggle → control_request `interrupt`)
- [x] ✅ **Retry** last turn (↻ Retry re-runs the last prompt + images)
- [x] ✅ Markdown rendering (headings, lists, links, inline code, blockquote, hr, bold/italic,
      GFM tables — recognised by the delimiter row, `:` alignment, escaped `\|`, scroll-x when a
      cell can't wrap)
- [x] 🟡 **Code blocks** with language label + copy button (no syntax highlighting yet)
- [x] ✅ **Thinking** blocks (collapsible `<details>`)
- [x] ✅ Error surfacing (result `is_error`)
- [x] 🟡 Syntax highlighting inside code blocks (offline tokenizer: keywords/strings/comments/numbers/php-vars)
- [x] ✅ Auto-scroll pinning (only follows when you're at the bottom)

## 2. Editor / IDE integration (MCP tools the CLI calls)
- [x] ✅ `getWorkspaceFolders`
- [x] ✅ `getOpenEditors`
- [x] ✅ `getCurrentSelection` / `getLatestSelection`
- [x] ✅ `openFile` (open + navigate)
- [x] ✅ `saveDocument`
- [x] ✅ `checkDocumentDirty`
- [x] ✅ `openDiff` — real `DiffManager` view, blocks for accept/reject, returns FILE_SAVED/DIFF_REJECTED
- [x] ✅ `getDiagnostics` — via `DaemonCodeAnalyzerImpl.getHighlights` (basic, warnings+)
- [x] ✅ `closeAllDiffTabs`
- [ ] 🟡 `close_tab` — best-effort (currently closes diff tabs)
- [ ] ⬜ `executeCode` (Jupyter) — likely ➖ / low priority
- [x] ✅ Tool calls dispatched async (blocking review won't stall the socket)
- [ ] ⬜ Autosave-before-read/write (VS Code `claudeCode.autosave`)
- [x] ✅ VFS/PSI reads wrapped in read actions

## 3. Diffs & edit approval
- [x] ✅ **Permission gate** via control protocol `can_use_tool` (`--permission-prompt-tool stdio`)
- [x] ✅ **Accept / Reject** card in chat before any tool runs (allow/deny → control_response)
- [x] ✅ `openDiff` real IDE diff available (Current vs Proposed) when the CLI requests it
- [x] ✅ Show the diff *inline with* the permission card (old→new for Edit, additions for Write)
- [ ] ⬜ Editor-title accept/reject buttons + shortcuts (VS Code `acceptProposedDiff`/`rejectProposedDiff`)
- [ ] ⬜ In-diff editing before accept (return user-edited text)
- [ ] ⬜ Multi-file change review

## 4. Permission modes
- [x] ✅ Permission prompts for tool use (allow / deny) — launched with the **persisted** mode
- [x] ✅ Mode switcher: **default (manual)**, **acceptEdits**, **plan** live via `set_permission_mode`;
      **bypassPermissions** by relaunch-with-resume (the CLI refuses to raise to bypass at runtime)
- [x] ✅ Mode chip driven by the CLI (`permissionMode` on `system/init`+`system/status` — follows
      plan-approval's drop back to default); refused control requests surface as error blocks
- [x] ✅ **"Don't ask again" buttons** from `can_use_tool.permission_suggestions` (accept-all-edits /
      always-allow / allow-directory → echoed as `updatedPermissions`); hidden on sandbox-escape
      (`blocked_path`) prompts, where no grant stops the re-ask
- [x] ✅ Mode persists across restarts & New/Refresh/resume (was: silently reset to `default`)
      — also covers the old `initialPermissionMode` row; a settings-page override stays deferred
- [n/a] `allowDangerouslySkipPermissions` — deliberately NOT used: the flag turns every mode into
      a bypass (probed), so Auto relaunches with `--permission-mode bypassPermissions` instead

## 5. Plan mode
- [x] ✅ Enter **Plan Mode** (via the permission-mode switcher → `plan`)
- [x] ✅ Render the plan as a **plan card** (markdown) on `ExitPlanMode`
- [x] ✅ "Approve & implement" / "Keep planning" flow (allow/deny the ExitPlanMode permission)

## 6. Context input
- [x] ✅ **@-mention** files (type `@`, fuzzy file autocomplete with keyboard nav)
- [ ] ⬜ @-mention symbols; Alt+K to mention current file
- [ ] ⬜ "Add context" picker
- [ ] ⬜ Auto-include current **selection** on ask
- [x] ✅ **File attachments** — images (base64 `image` blocks) **+ PDF / text / code** (`document`
  blocks: PDF base64, text/code decoded-text; reference-matched extension allowlist so `.php/.py/.js/…`
  with empty MIME still classify). Image chips preview in the lightbox; other chips download via the
  IDE's native save dialog. Documents carry a `title`, so replayed PDFs/text keep their real filename.
- [x] ✅ Paste from clipboard (images reliably; other files when the OS/JCEF exposes them as files)
- [x] ✅ Drag-and-drop any supported file into chat (unsupported binaries skipped)

## 7. Slash commands
- [x] ✅ **Slash-command autocomplete menu** (type `/`, keyboard nav, click/Enter to insert)
- [x] ✅ Commands fetched at startup via the `initialize` control request (before any message)
- [x] ✅ Command **descriptions** shown in the menu (`/name — description`)
- [x] ✅ Commands execute in stream-json mode (verified `/context`); output renders as markdown
- [x] ✅ `/clear` handled natively (→ new conversation, verified in `runIde` 2026-07-30);
      `/login` deferred with the settings/non-terminal-login batch

## 8. Sessions / history
- [x] ✅ **New conversation** (＋ New — restarts the CLI fresh, clears the log)
- [x] ✅ **Resume** past conversations (🕘 History list → `--resume <id>`)
- [x] ✅ Header shows the **conversation title**, from the moment the transcript can supply one:
      re-read at the first `message_start` while the thread is unnamed (the file does not exist yet
      at `system/init`, measured) and again at every `result`, since `ai-title` lands late and a
      rename can land any time. "New conversation" only until the first record is on disk — it used
      to hold for the whole of the first turn (0.5.3)
- [x] ✅ **Refresh** button — re-resumes the current session (replay from disk; also recovers a
      dead CLI). Guarded by `SessionStore.exists`: an id whose file the CLI hasn't written yet
      would make `--resume` exit 1, so that case restarts clean instead.
- [x] ✅ Past-conversations list (reads `~/.claude/projects/<enc-cwd>/*.jsonl`, titles from summary/first-user)
- [x] ✅ Resume **replays prior turns into the UI** (parses the JSONL: user/assistant text + tool chips)
- [ ] ⬜ **Reopen closed session** (Ctrl+Shift+T)
- [ ] ⬜ Multiple conversation **tabs** (`new_conversation_tab`, `rename_tab`)
- [ ] ⬜ ~~Rewind / undo file changes per turn~~ — REMOVED 2026-07-30 (feature dropped)
- [ ] ⬜ Rewind the *conversation* (resume-at a message) — not started; file rewind was removed
- [ ] ⬜ ~~Ctrl+N new conversation shortcut~~ — REMOVED 2026-08-09 (the plugin binds no
      keyboard shortcuts; New button and `/clear` cover it). Ctrl+Shift+T reopen-closed still ⬜

## 9. Model & usage
- [x] ✅ **Model** selector (populated from `initialize.models`; switches via `set_model` control)
- [x] ✅ Persist model choice (PropertiesComponent; re-applied on every CLI restart, reflected in dropdown)
- [ ] ⬜ **Usage / tokens** indicator (context left, cost) — DEFERRED to final list
- [ ] ⬜ Subagent model config (`CLAUDE_CODE_SUBAGENT_MODEL`)

## 10. Auth & account
- [ ] ⬜ **Login** flow (OAuth URL handling — `auth_url`)
- [ ] ⬜ **Logout** (`logout`)
- [ ] ⬜ Account/plan display
- [ ] ⬜ Provider modes: Bedrock / Vertex / Foundry (env passthrough)
- [ ] ⬜ `disableLoginPrompt` setting

## 11. Extensibility (MCP / plugins / skills / hooks / subagents)
- [ ] ⬜ **Install plugin** (`installPlugin`) + Manage plugins UI
- [ ] ⬜ Plugin **marketplaces**
- [ ] ⬜ **MCP** server management
- [ ] ⬜ **Skills**
- [ ] ⬜ **Hooks**
- [ ] ⬜ **Subagents** (`/agents`, `agent_metadata`)

## 12. UI placement & windows
- [x] ✅ **Right sidebar** tool window
- [ ] ⬜ Open in **new editor tab** (`editor.open`, Ctrl+Shift+Esc)
- [ ] ⬜ Open in **new window**
- [ ] ⬜ `preferredLocation` (sidebar vs panel) persistence
- [ ] ⬜ **Focus / blur** input shortcut (Ctrl+Esc)
- [ ] ⬜ Open in **terminal** mode (`useTerminal`)

## 13. Settings (parity with `claudeCode.*`)
- [ ] ⬜ `environmentVariables`
- [ ] ⬜ `useTerminal`
- [ ] ⬜ `allowDangerouslySkipPermissions`
- [x] 🟡 CLI-path resolution (`-Dclaude.executable` → PATH → installed VS Code extension binary); `claudeProcessWrapper` setting still ⬜
- [ ] ⬜ `respectGitIgnore`
- [ ] ⬜ `initialPermissionMode`
- [ ] ⬜ `disableLoginPrompt`
- [ ] ⬜ `autosave`
- [ ] ⬜ `useCtrlEnterToSend`
- [ ] ⬜ `preferredLocation`
- [ ] ⬜ `enableNewConversationShortcut`
- [ ] ⬜ `enableReopenClosedSessionShortcut`
- [ ] ⬜ `hideOnboarding`
- [ ] ➖ `usePythonEnvironment` (PhpStorm: N/A; maybe PHP interpreter analog later)
- [ ] ⬜ JSON schema validation for `.claude/settings.json` (ship `claude-code-settings.schema.json`)
- [ ] ⬜ A native **Settings page** (IDE Settings → Tools → Claude Code)

## 14. Worktrees & git
- [ ] ⬜ **Create worktree** (`createWorktree`)
- [ ] ⬜ Git-aware diff/context

## 15. Onboarding & misc
- [ ] ⬜ Walkthrough / getting-started (`openWalkthrough`)
- [ ] ⬜ Onboarding checklist (`hideOnboarding`)
- [ ] ⬜ **Show logs** (`showLogs`)
- [ ] ⬜ **Update** extension (`update`) — ➖ (JetBrains handles plugin updates)
- [ ] ⬜ **Voice input / dictation** (`audio-capture.node`) — later
- [ ] ⬜ `/share` conversation export
- [ ] ⬜ Remote control / tunnel (`/rc`, `remote-control`)

---

## Foundation (already ✅ working)
- Bridge: free-port, lockfile (`~/.claude/ide/<port>.lock`), WS MCP server + auth
- CLI spawn with `CLAUDE_CODE_SSE_PORT`, stream-json transport
- Right-sidebar JCEF chat with live streaming
- Builds clean (`buildPlugin` / `runIde`)

## Proposed milestones (work order)
1. **M1 – Edit loop** (highest value): §3 diffs + accept/reject, §2 `openDiff`/`getDiagnostics`,
   §1 stop/interrupt, autosave. → makes it a real coding agent.
2. **M2 – Chat polish**: §1 markdown/code/thinking/errors, §6 @-mentions + selection + images.
3. **M3 – Sessions**: §8 new/resume/tabs.
4. **M4 – Modes & permissions**: §4 permission prompts + modes, §5 plan mode.
5. **M5 – Config & UX**: §13 settings page + parity, §12 window placement, §10 auth/login.
6. **M6 – Extensibility**: §11 plugins/MCP/skills/hooks/subagents, §7 slash commands.
7. **M7 – Extras**: §14 worktrees, §15 voice/share/logs/walkthrough.

---

## Feature-level gaps vs VS Code 2.1.222 (recorded 2026-08-06)

From the full-bundle sweep (`docs/client-parity.md` § 32 / `docs/ide-mcp-protocol.md` §9). That
audit was about DATA the CLI emits; this list is the FEATURE remainder — everything the VS Code
extension does that we don't, once the by-design nine (client-parity group 2), the DEFERRED list
(tabs, voice, auto-include selection — `.claude/context/backlog.md`), and the "Then:" roadmap (editor accept/reject,
@-symbol mentions, worktrees, extensibility view) are excluded. Each row carries a take so the gap
is a decision, not an unknown:

- [ ] ⬜ **Rewind / checkpoints + fork conversation** — the one substantial capability gap.
  VS Code: "Rewind code to here", "Fork conversation and rewind", `enableFileCheckpointing:!0`,
  `rewind_code`/`fork_conversation`. We REMOVED per-turn rewind 2026-07-30 (revival notes in
  `.claude/context/gotchas.md` § Protocol / wire: needs `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=1`, a git repo,
  client-supplied uuids — which item 21's `stampMessage` now provides — and dry_run first).
  **Status: UNDECIDED — needs an explicit yes/later/no; currently in limbo on no list at all.**
- [x] ✅ **Session rename / delete** — both DONE. Delete 2026-08-09 (every history row, the live one
  routed through leave-first). Rename 2026-08-12, and it landed on the HEADER title rather than the
  history panel this item assumed: a hover pencil turns the header into an editor in place. Writes
  the CLI's own `custom-title` record (`SessionStore.rename`, shape read from the 2.1.226 binary),
  so it is the same act as `/rename` in the terminal — the write-half of client-parity item 34.
- [ ] ⬜ **Focus view** — hide tool noise, show prompts + responses only (Ctrl+Alt+F, host flag
  `viewMode:"focus"`). A reading mode for long sessions; the philosophy question is whether
  toggling it is an hourly act or a novelty. Take: worth a mockup pass before building.
- [ ] ⬜ **Fast mode + thinking toggles** — "Toggle fast mode (Opus only)" (`fastMode` state rides
  the initialize response and `result.fast_mode_state`, which we already receive) and an extended
  thinking on/off. Siblings of the model chip + effort slider we already ship. Take: fast mode is
  cheap and fits the composer family; a thinking toggle may be redundant beside the effort slider.
- [ ] ⬜ **Plan preview with inline comments** — VS Code opens the plan as markdown with comment
  threads (`open_markdown_preview` + `plan_comment`). Our plan card with approve/reject covers the
  decision loop; commenting is the delta. Take: revisit only if plan-heavy workflows demand it.
- [ ] ⬜ **Remote sessions (teleport) + remote control** — list claude.ai/code web sessions,
  teleport one down (`GET /v1/code/sessions/<id>/teleport-events-*`); `/remote-control` bridge
  toggle. Take: session SOURCES beyond the local disk lean configuration/infrastructure — the
  terminal's half — but this is a judgment call recorded here, not yet a hard "by design".
- [ ] ⬜ **Small chrome, leaning no**: message rating + `/feedback` UI (the terminal's half),
  prompt/proactive suggestions (sweep: "noted, not wanted yet"), Artifact auto-open, the
  "Explored" cosmetic grouping of consecutive Reads, onboarding/walkthrough/upsell banners.

Confirmed at parity or better, for the record: the edit-diff pipeline (they use SDK hooks +
baselines, we read disk fresh — different route, same rendered diff), diagnostics, injected IDE
context handling, Read ranges with range-selecting clicks, permission suggestions (ours merges
compound `addRules` into one button, theirs does not), queued messages (VS Code has none), and
the retry/compaction/informational signal set from the client-parity audit.
