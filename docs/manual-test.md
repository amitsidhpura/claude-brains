# Manual test pass — STANDALONE

Self-contained checklist for a full manual sweep of every shipped feature.
**Standalone checklist** — deliberately referenced nowhere else; it describes itself.
It stays in the repo after the pass as the record of what was manually verified.
Tick items by telling me the number (e.g. "3.4 done").

Setup: `cd plugin && ./gradlew runIde`, open the Claude Brains tool window.
Items marked **(hard to trigger)** need a specific situation — skip if it doesn't occur naturally.

---

## 1. Launch & chrome

- [ ] 1.1 Panel loads in the sandbox — no dead/blank webview
- [ ] 1.2 Welcome screen: brain logo, "Claude Brains v0.3.3" badge, slogan
      "Develop in the IDE. Configure in the Terminal.", `/` and `@` hint lines
- [ ] 1.3 Tool-window stripe icon: grey when closed, white while open
- [ ] 1.4 Header shows conversation title once one exists; Refresh button re-resumes the session
- [ ] 1.5 Ctrl+N starts a new conversation
- [ ] 1.6 Scroll fades at top and bottom of the conversation while content is scrollable
- [ ] 1.7 Escape closes whatever popup is open (menus, history panel, lightbox, task roster)

## 2. Composer basics

- [ ] 2.1 Ctrl+Enter sends; Enter inserts a newline
- [ ] 2.2 Reply streams in live (text appears token by token, not all at once)
- [ ] 2.3 Send button becomes Stop while busy; Stop → ⏹ Stopped line, no completion summary
- [ ] 2.4 ↑ / ↓ walk previous messages; a half-typed draft survives the walk
- [ ] 2.5 Typing while a turn runs → message held in the queue above the composer;
      it sends automatically when the turn ends
- [ ] 2.6 Queue row: click puts the text back in the composer for editing; × drops it
- [ ] 2.7 After Stop, queued messages do NOT auto-send
- [ ] 2.8 `@` opens the file-mention menu; picking one inserts the reference and the model can use it
- [ ] 2.9 Image paste and drag-drop → chip in the composer; renders in the sent user box
- [ ] 2.10 PDF / text file attach → document chip with the real filename
- [ ] 2.11 After an error ends a turn, a ↻ Retry link appears; clicking re-runs the last
      prompt including its images
- [ ] 2.12 Paperclip button opens the attach menu → native file picker → chip in the composer
- [ ] 2.13 The [/] button opens the slash-command menu, same as typing `/`

## 3. Slash commands & effort

- [ ] 3.1 `/` menu shows only the allowlist (/compact, /clear + custom commands), with descriptions
- [ ] 3.2 A TUI-only command typed by hand (e.g. /login) is refused with a status line, not sent
- [ ] 3.3 /clear wipes the view and starts fresh; the old session is still in history and resumable
- [ ] 3.4 /compact runs: "Compacting…" working verb, then a compaction marker with folded summary,
      and the context gauge resets
- [ ] 3.5 Effort slider changes the level silently — no turn appears in the conversation;
      mode chip suffix updates, e.g. "Manual (High)"
- [ ] 3.6 Menu pick runs a no-arg command immediately; a command with a required `<arg>`
      inserts `/cmd ` and waits

## 4. Mode & model

- [ ] 4.1 Mode chip shows the persisted mode on startup (survives IDE restart)
- [ ] 4.2 All four modes switch live — Manual / Edit automatically / Plan / Auto — no CLI restart
- [ ] 4.3 Composer focus border + Send button colour follow the mode
- [ ] 4.4 Edit automatically: file edits apply without a card; Bash still asks (correct, not a bug)
- [ ] 4.5 Plan mode: plan card appears; approving drops the chip back to Manual
- [ ] 4.6 Model selector: picking a model persists across restart; search filters the list
- [ ] 4.7 Custom model in `value : Display Name : Description` form shows its display name in the chip

## 5. Streaming render

- [ ] 5.1 Markdown: tables render as tables; code blocks have syntax highlighting; long code folds
- [ ] 5.2 Thinking: shimmer + live seconds timer + token count while streaming;
      collapses to "Thought for Ns" with expandable chevron
- [ ] 5.3 Working line (flower spinner) shows a live token estimate during the turn
- [ ] 5.4 Completion summary: ✻ Verb for Ns · ↓ N tokens; never "↓ 0 tokens";
      two consecutive summaries never share a verb
- [ ] 5.5 Tool lines: green dot on success, red on failure; description filled for every tool
      (Read shows the path — clickable — and its line range when partial; Grep shows the pattern)
- [ ] 5.6 Bash: IN box with the command, OUT box with the result
- [ ] 5.7 Oversized output: cut marker "⋯ +N lines · X KB not shown" — never a silent cut
- [ ] 5.8 Failed Bash: exit-code explanation note under the OUT box
- [ ] 5.9 Non-Bash tool results show a summary under the tool line (not blank, not a dump)
- [ ] 5.10 Tool-returned image renders under the tool line (ask it to Read a PNG)
- [ ] 5.11 Clicking a file path opens it in the editor; a path that no longer exists → balloon,
      not a silent nothing
- [ ] 5.12 Edit/Write diff: real line numbers in the gutter, context rows around the change;
      a huge diff caps at 400 rows with a truncation marker
- [ ] 5.13 Todo checklist renders inline when the model plans with tasks, updating as items complete
- [ ] 5.14 Auto-scroll pins to the bottom while streaming; scrolling up releases the pin;
      scroll-to-bottom button glides; submitting scrolls down
- [ ] 5.15 Code blocks have a language label and a working copy button
- [ ] 5.16 A WebSearch turn renders: tool line with the query, result summary underneath
- [ ] 5.17 A huge Bash result the CLI spills to a file shows the persisted-output note
      **(hard to trigger)**

## 6. Permissions

- [ ] 6.1 Bash card: command preview (capped, with cut marker if huge), Accept / Reject
- [ ] 6.2 Reject → ✗ Rejected on the line; the model acknowledges the refusal
- [ ] 6.3 Edit card shows a diff preview before approval
- [ ] 6.4 "Always allow" appears when the CLI suggests a rule; a compound command
      (a && b) shows ONE Always-allow that grants the whole set (split button: arrow lists parts)
- [ ] 6.5 Accept-all-edits / allow-directory suggestion buttons appear when offered and stick
      (the same prompt doesn't re-ask after granting)
- [ ] 6.6 Sandbox-escape card (blocked_path) has NO suggestion buttons — only Accept / Reject
- [ ] 6.7 AskUserQuestion: tab per question, radio vs checkbox by multiSelect, Other free-text row;
      submit sends the answers; cancel → ✗ Cancelled
- [ ] 6.8 Plan card: Approve / Keep planning; ✗ Kept planning renders on refusal

## 7. Context gauge & background tasks

- [ ] 7.1 Gauge appears after the first turn with a plausible %; orange at ≥50%
- [ ] 7.2 Clicking the gauge sends /compact
- [ ] 7.3 Background-task chip appears while a background sub-agent runs ("1 task");
      popup lists descriptions; chip disappears when they finish
- [ ] 7.4 Sub-agent (Task tool): line shows the errand, IN box shows the prompt,
      live progress line updates ("Explore · … · N tool uses · N tokens"),
      finished line shows the summary
- [ ] 7.5 During a background suspend, the turn does NOT show a completion summary early;
      one summary at the true end

## 8. Sessions & replay

- [ ] 8.1 History panel lists sessions with titles; current session marked
      (no search box — that's correct, we don't have one)
- [ ] 8.2 Resume: replayed conversation matches how it looked live — diffs (✓ Applied),
      resolved ask cards, thinking blocks with durations, plan cards, ⏹ Stopped lines,
      completion summaries with the SAME verbs as live
- [ ] 8.3 A big session opens fast and lands at the bottom
- [ ] 8.4 Scrolling up silently streams in earlier history — no viewport jump
- [ ] 8.5 A long user message stays pinned (sticky) while its turn scrolls
- [ ] 8.6 Replayed images: recent ones show bytes; old ones degrade to name-only chips
- [ ] 8.7 An error at the tail of a resumed session shows the error block + working Retry
- [ ] 8.8 /effort turns DO appear on resume (accepted audit trail — expected, not a bug)
- [ ] 8.9 "New conversation" from a resumed windowed session doesn't pull the old session's chunks
- [ ] 8.10 A session renamed in the terminal (custom title) shows that name in the history
      list, beating the derived title **(hard to trigger)**
- [ ] 8.11 Deleting a session from history: confirm step, row disappears, file gone;
      the CURRENT session's row offers no delete
- [ ] 8.12 Attachment chips: clicking an image opens the lightbox overlay (Esc or click
      closes); clicking a PDF/text chip opens a native Save As dialog

## 9. Resilience & notices

- [ ] 9.1 API retry storm: "… — retrying (n/m)" status lines instead of a silent stall **(hard to trigger)**
- [ ] 9.2 Auth failure (e.g. bad ANTHROPIC_API_KEY in the sandbox env) → clear
      "sign in from a terminal" message, not an opaque error
- [ ] 9.3 CLI process death → "claude process exited (N)" status line with stderr tail
- [ ] 9.4 MCP server that fails to start → named notice ("MCP servers: X failed to start …")
- [ ] 9.5 A hook blocking a prompt (exit 2) → visible notice, not a dead panel **(hard to trigger)**
- [ ] 9.6 Rate-limit warning with reset time **(hard to trigger — skip unless it occurs)**
- [ ] 9.7 Interrupting mid-turn then resuming later: the interrupt replays as ⏹ Stopped,
      not as a fake user message
- [ ] 9.8 Model refusal with fallback: retracted content is withdrawn from the view with a
      notice, and the session model does NOT silently change **(hard to trigger)**
- [ ] 9.9 Editing a file yourself while Claude also edits it → the tool line notes the file
      changed underneath, not a bare ✓ Applied **(hard to trigger)**
- [ ] 9.10 Commands discovered mid-session (commands_changed) refresh the / menu roster
      **(hard to trigger)**

## 10. IDE bridge

- [ ] 10.1 Model can open a file in the editor (mcp__ide__openFile)
- [ ] 10.2 Model can read editor diagnostics (ask "what errors are in this file?")
- [ ] 10.3 Current selection is available to the model
- [ ] 10.4 openDiff shows a diff view in the editor
- [ ] 10.5 Accepting / rejecting in that editor diff view is honoured (accept saves the file,
      reject leaves it untouched and the model is told)

## 11. Dev aids

- [ ] 11.1 Ctrl+Alt+G renders the gallery of transient states, no console errors (F12 to check)
- [ ] 11.2 F12 in the panel / Find Action "Claude Brains: Open DevTools" opens DevTools;
      http://localhost:9222 lists "Claude Brains — chat panel"

---

When every box is ticked I'll ask for your confirmation that the pass is complete.
