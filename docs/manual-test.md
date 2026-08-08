# Manual test pass — STANDALONE

Self-contained checklist for a full manual sweep of every shipped feature.
**Standalone checklist** — deliberately referenced nowhere else; it describes itself.
It stays in the repo after the pass as the record of what was manually verified.
Tick items by telling me the number (e.g. "3.4 done").

Setup: `cd plugin && ./gradlew runIde`, open the Claude Brains tool window.
Items marked **(hard to trigger)** need a specific situation — skip if it doesn't occur naturally.

---

## 1. Launch & chrome

- [x] 1.1 Panel loads in the sandbox — no dead/blank webview
- [x] 1.2 Welcome screen: brain logo, "Claude Brains v0.3.3" badge, slogan
      "Develop in the IDE. Configure in the Terminal.", `/` and `@` hint lines
- [x] 1.3 Tool-window stripe icon: grey when closed, white while open
- [x] 1.4 Header shows conversation title once one exists; Refresh button re-resumes the session
- [x] 1.5 Ctrl+N starts a new conversation

      **ISSUE (2026-08-07):** Ctrl+N is swallowed by the IDE — opens PhpStorm's
      "Go to Class" dialog instead of starting a new conversation.
- [x] 1.6 Scroll fades at top and bottom of the conversation while content is scrollable
- [x] 1.7 Escape closes whatever popup is open (menus, history panel, lightbox, task roster)

      **ISSUE (2026-08-07):** after closing a popup with Escape, reopening it glitches;
      click-toggle works fine.

## 2. Composer basics

- [x] 2.1 Ctrl+Enter sends; Enter inserts a newline
- [x] 2.2 Reply streams in live (text appears token by token, not all at once)
- [x] 2.3 Send button becomes Stop while busy; Stop → ⏹ Stopped line, no completion summary
- [x] 2.4 ↑ / ↓ walk previous messages; a half-typed draft survives the walk
- [x] 2.5 Typing while a turn runs → message held in the queue above the composer;
      it sends automatically when the turn ends
- [x] 2.6 Queue row: click puts the text back in the composer for editing; × drops it
- [x] 2.7 After Stop, queued messages do NOT auto-send
- [x] 2.8 `@` opens the file-mention menu; picking one inserts the reference and the model can use it

      **ISSUE (2026-08-07):** file-mention menu does not open on `@`.
- [x] 2.9 Image paste and drag-drop → chip in the composer; renders in the sent user box

      **ISSUE (2026-08-07):** paste works and renders fine in the sent user box, but
      drag-drop of a file does nothing.
- [x] 2.10 PDF / text file attach → document chip with the real filename
- [x] 2.11 After an error ends a turn, a ↻ Retry link appears; clicking re-runs the last
      prompt including its images
- [x] 2.12 Paperclip button opens the attach menu → native file picker → chip in the composer
- [x] 2.13 The [/] button opens the slash-command menu, same as typing `/`

## 3. Slash commands & effort

- [x] 3.1 `/` menu shows only the allowlist (/compact, /clear + custom commands), with descriptions

      **ISSUE (2026-08-08):** the "+ custom commands" half fails — a project command
      (`.claude/commands/dummy-cmd.md`) shows in the terminal TUI as "/dummy-cmd (project)"
      but never in the panel menu: `cmdKind` (chat.html:2034) has no custom-command
      detection, so everything outside {clear, compact} is greyed as 'tui'. Ticked
      originally with no custom command present to expose this. Possible lever: the
      "(project)"/"(user)" marker the TUI shows for custom commands.
- [x] 3.2 A TUI-only command typed by hand (e.g. /login) is refused with a status line, not sent
- [x] 3.3 /clear wipes the view and starts fresh; the old session is still in history and resumable
- [x] 3.4 /compact runs: "Compacting…" working verb, then a compaction marker with folded summary,
      and the context gauge resets
- [x] 3.5 Effort slider changes the level silently — no turn appears in the conversation;
      mode chip suffix updates, e.g. "Manual (High)"
- [x] 3.6 Menu pick runs a no-arg command immediately; a command with a required `<arg>`
      inserts `/cmd ` and waits

## 4. Mode & model

- [x] 4.1 Mode chip shows the persisted mode on startup (survives IDE restart)
- [x] 4.2 All four modes switch live — Manual / Edit automatically / Plan / Auto — no CLI restart
- [x] 4.3 Composer focus border + Send button colour follow the mode
- [x] 4.4 Edit automatically: file edits apply without a card; Bash still asks (correct, not a bug)

      **ISSUE (2026-08-07):** no card is correct, but auto-approved edits render NO diff at
      all live — the only live diff path is the permission card's `previewHtml`
      (chat.html:3348); the tool-line path never calls renderEditDiff/writeDiffHtml. The same
      edit replays WITH a diff ("✓ Applied" via `structuredPatch`), so live is poorer than
      replay. TUI/VS Code show applied diffs even when auto-approved.
- [x] 4.5 Plan mode: plan card appears; approving drops the chip back to Manual
- [x] 4.6 Model selector: picking a model persists across restart; search filters the list
- [x] 4.7 Custom model in `value : Display Name : Description` form shows its display name in the chip

## 5. Streaming render

- [x] 5.1 Markdown: tables render as tables; code blocks have syntax highlighting; long code folds
- [x] 5.2 Thinking: shimmer + live seconds timer + token count while streaming;
      collapses to "Thought for Ns" with expandable chevron
- [x] 5.3 Working line (flower spinner) shows a live token estimate during the turn
- [x] 5.4 Completion summary: ✻ Verb for Ns · ↓ N tokens; never "↓ 0 tokens";
      two consecutive summaries never share a verb
- [x] 5.5 Tool lines: green dot on success, red on failure; description filled for every tool
      (Read shows the path — clickable — and its line range when partial; Grep shows the pattern)
- [x] 5.6 Bash: IN box with the command, OUT box with the result
- [x] 5.7 Oversized output: cut marker "⋯ +N lines · X KB not shown" — never a silent cut
- [x] 5.8 Failed Bash: exit-code explanation note under the OUT box
- [x] 5.9 Non-Bash tool results show a summary under the tool line (not blank, not a dump)

      **ISSUE (2026-08-07):** failed-tool OUT boxes render CLI plumbing wrappers verbatim —
      `<tool_use_error>…</tool_use_error>` on a failed Write, raw `<system-reminder>` on an
      empty-file Read. Should be stripped (the TUI presents these without the tags).
- [x] 5.10 Tool-returned image renders under the tool line (ask it to Read a PNG)
- [x] 5.11 Clicking a file path opens it in the editor; a path that no longer exists → balloon,
      not a silent nothing
- [x] 5.12 Edit/Write diff: real line numbers in the gutter, context rows around the change;
      a huge diff caps at 400 rows with a truncation marker
- [x] 5.13 Todo checklist renders inline when the model plans with tasks, updating as items complete
- [x] 5.14 Auto-scroll pins to the bottom while streaming; scrolling up releases the pin;
      scroll-to-bottom button glides; submitting scrolls down

      **ISSUE (2026-08-07):** during the heavy task-list turn (many TaskCreate/TaskUpdate
      checklist appends), auto-scroll could not keep up with the output — the view lagged
      behind the newest content instead of staying pinned to the bottom.
- [x] 5.15 Code blocks have a language label and a working copy button
- [x] 5.16 A WebSearch turn renders: tool line with the query, result summary underneath
- [x] 5.17 A huge Bash result the CLI spills to a file shows the persisted-output note
      **(hard to trigger)**

## 6. Permissions

- [x] 6.1 Bash card: command preview (capped, with cut marker if huge), Accept / Reject
- [x] 6.2 Reject → ✗ Rejected on the line; the model acknowledges the refusal
- [x] 6.3 Edit card shows a diff preview before approval
- [x] 6.4 "Always allow" appears when the CLI suggests a rule; a compound command
      (a && b) shows ONE Always-allow that grants the whole set (split button: arrow lists parts)

      **ISSUE (2026-08-07):** whole-set grant works (one click → all suggested rule chips),
      but the split-button arrow never appeared on a compound card with multiple suggested
      rules (`git log && composer show ; node && npm ; free && lscpu | head`) — no dropdown
      to grant an individual part.
- [x] 6.5 Accept-all-edits / allow-directory suggestion buttons appear when offered and stick
      (the same prompt doesn't re-ask after granting)
- [x] 6.6 Sandbox-escape card (blocked_path) has NO suggestion buttons — only Accept / Reject
- [x] 6.7 AskUserQuestion: tab per question, radio vs checkbox by multiSelect, Other free-text row;
      submit sends the answers; cancel → ✗ Cancelled
- [x] 6.8 Plan card: Approve / Keep planning; ✗ Kept planning renders on refusal

## 7. Context gauge & background tasks

- [x] 7.1 Gauge appears after the first turn with a plausible %; orange at ≥50%
- [x] 7.2 Clicking the gauge sends /compact
- [x] 7.3 Background-task chip appears while a background sub-agent runs ("1 task");
      popup lists descriptions; chip disappears when they finish

      **ISSUE (2026-08-07):** chip appears and the popup lists the task correctly, but after
      the task finishes the chip stays at "1 task" (empty popup), and on a NEW conversation a
      lone pulsing orange dot shows. ROOT CAUSE (probed via CDP): `chip.hidden = true` is set
      correctly, but `.chip-btn { display: inline-flex }` overrides the UA `[hidden]` rule, so
      the chip never actually hides — empty text leaves just the `.bg::before` dot. Fix:
      `.chip-btn[hidden] { display: none; }` in chat.css (and clear `chip.textContent` in
      renderBgTasks' empty branch). Same defeat likely applies to `#ctxChip`, which only
      "hides" today because its text is empty.
- [x] 7.4 Sub-agent (Task tool): line shows the errand, IN box shows the prompt,
      live progress line updates ("Explore · … · N tool uses · N tokens"),
      finished line shows the summary

      **ISSUE (2026-08-07):** the async-launch OUT box renders the CLI's internal metadata
      verbatim — "Async agent launched successfully. (This tool result is internal metadata —
      never quote or paste any part of it, including the agentId below …)". Text that
      literally announces itself as internal is shown to the user; same plumbing-leak family
      as the `<tool_use_error>` wrappers under 5.9. Candidate for the NO_OUT_BOX list in
      RenderLimits (the tool line + progress line already tell the story). ALSO: the
      progress/finished line can carry a raw harness annotation as the "summary" — observed
      "Explore · [harness: subagent output matched instruction-shaped pattern(s):
      settings-json. Control tags below are neutralized …] · 5 tool uses · 14.6k tokens".
- [x] 7.5 During a background suspend, the turn does NOT show a completion summary early;
      one summary at the true end

## 8. Sessions & replay

- [x] 8.1 History panel lists sessions with titles; current session marked
      (no search box — that's correct, we don't have one)
      (NB: with a non-default model persisted, new transcripts start with the CLI's
      "/model <x>" audit record, so untitled sessions can derive "/model haiku" as their
      title — ACCEPTED, not a bug; the ai-title replaces it after the first real turn.)
- [x] 8.2 Resume: replayed conversation matches how it looked live — diffs (✓ Applied),
      resolved ask cards, thinking blocks with durations, plan cards, ⏹ Stopped lines,
      completion summaries with the SAME verbs as live

      **ISSUE (2026-08-08):** two small live-vs-replay divergences around an auth-failed
      turn: (1) the live "Authentication failed. Run `claude` in a terminal…" status line
      does not replay (only the error block does); (2) replay shows a "✻ Conjured for 1s"
      completion summary on the error-terminated turn that had NO summary live.
- [x] 8.3 A big session opens fast and lands at the bottom
- [x] 8.4 Scrolling up silently streams in earlier history — no viewport jump
- [x] 8.5 A long user message stays pinned (sticky) while its turn scrolls
- [x] 8.6 Replayed images: recent ones show bytes; old ones degrade to name-only chips
      (NB: replayed chips reading "file.jpg" with a smaller size is EXPECTED — the transcript
      persists the bare API image block, recompressed, with no filename field at all; verified
      against records 2026-08-08.)
- [x] 8.7 An error at the tail of a resumed session shows the error block + working Retry

      **ISSUE (2026-08-08):** the error block replays at the tail, but the ↻ Retry link does
      NOT — live showed "Not logged in · Please run /login" + Retry; the resumed view shows
      the error block followed by a phantom "✻ Tinkered for 1s" summary (issue also noted
      under 8.2) and no Retry, so the user cannot re-run the failed prompt after resume.
- [x] 8.8 /effort turns DO appear on resume (accepted audit trail — expected, not a bug)
- [x] 8.9 "New conversation" from a resumed windowed session doesn't pull the old session's chunks
- [x] 8.10 A session renamed in the terminal (custom title) shows that name in the history
      list, beating the derived title **(hard to trigger)**
- [x] 8.11 Deleting a session from history: confirm step, row disappears, file gone;
      the CURRENT session's row offers no delete
- [x] 8.12 Attachment chips: clicking an image opens the lightbox overlay (Esc or click
      closes); clicking a PDF/text chip opens a native Save As dialog

## 9. Resilience & notices

- [x] 9.1 API retry storm: "… — retrying (n/m)" status lines instead of a silent stall **(hard to trigger)**

      **ISSUE (2026-08-07):** behaviour correct (triggered via `nmcli networking off`), but
      every line reads "unknown — retrying (n/10)" — the error reason from the `api_retry`
      event isn't surfaced (the final error block DOES show "Unable to connect to API
      (ENOTIMP)", so the data is there). Also "(1/10)" rendered twice — duplicate first attempt.
- [x] 9.2 Auth failure (e.g. bad ANTHROPIC_API_KEY in the sandbox env) → clear
      "sign in from a terminal" message, not an opaque error
- [x] 9.3 CLI process death → "claude process exited (N)" status line with stderr tail
- [x] 9.4 MCP server that fails to start → named notice ("MCP servers: X failed to start …")
- [x] 9.5 A hook blocking a prompt (exit 2) → visible notice, not a dead panel **(hard to trigger)**
- [x] 9.6 Rate-limit warning with reset time **(hard to trigger — skip unless it occurs)**
      (Verified 2026-08-08 by injecting the recorded fixture frame
      (tools/fixtures/16-rate-limit-event.json shape + resetsAt) through onClaudeEvent in the
      LIVE webview via cdp.py — rendered "You've hit your session limit · resets in 3h".
      Render path only; a real limit is unforceable by design, per the fixture's provenance.)
- [x] 9.7 Interrupting mid-turn then resuming later: the interrupt replays as ⏹ Stopped,
      not as a fake user message
- [x] 9.8 Model refusal with fallback: retracted content is withdrawn from the view with a
      notice, and the session model does NOT silently change **(hard to trigger)**
      (Verified 2026-08-08 by injecting a scope:'local' `system/model_refusal_fallback`
      frame — the VS-Code-bundle wire shape the handler was built from — via cdp.py against
      a REAL stamped assistant block in the live webview: block evicted, both notices
      rendered, model chip unchanged. Render path only; no real refusal has ever been
      captured, per the handler's own provenance comment.)
- [x] 9.9 Editing a file yourself while Claude also edits it → the tool line notes the file
      changed underneath, not a bare ✓ Applied **(hard to trigger)**
- [x] 9.10 Commands discovered mid-session (commands_changed) refresh the / menu roster
      **(hard to trigger)**

      **ISSUE (2026-08-08):** attempted via dropping `.claude/commands/dummy-cmd.md` into a
      live session — no roster change observed, and the check is currently unobservable
      anyway because custom commands never render in the menu at all (see the 3.1 issue:
      `cmdKind` has no custom-command detection). Re-test after fixing 3.1; the fixture file
      is left in place for that.

## 10. IDE bridge

- [x] 10.1 Model can open a file in the editor (mcp__ide__openFile)

      **ISSUE (2026-08-08):** the MODEL half is dead on CLI 2.1.226 — ToolSearch finds no
      `mcp__ide__openFile` and a direct call errors "No such tool available"; only
      `mcp__ide__getDiagnostics` is model-facing now (upstream CLI policy change; openFile
      worked when the checklist was written). The BRIDGE half verified end-to-end by calling
      `tools/call openFile` over the WS directly (auth header + "mcp" subprotocol) — editor
      tab opened. Re-scope this item (and 10.4/10.5) if the CLI keeps the restriction.
- [x] 10.2 Model can read editor diagnostics (ask "what errors are in this file?")
- [x] 10.3 Current selection is available to the model

      **ISSUE (2026-08-08):** same upstream restriction as 10.1 — the model has no selection
      tool on CLI 2.1.226 and answered "I don't have visibility into your current selection".
      Bridge half verified directly: `tools/call getCurrentSelection` over the WS returned
      the exact highlighted text (`private int $count;` + file path). (Auto-including the
      selection in prompts is separately a known deferred feature, not this item.)
- [x] 10.4 openDiff shows a diff view in the editor
      (Verified 2026-08-08 by calling `tools/call openDiff` over the bridge WS directly —
      the model cannot call it on CLI 2.1.226, same upstream restriction as 10.1.)
- [x] 10.5 Accepting / rejecting in that editor diff view is honoured (accept saves the file,
      reject leaves it untouched and the model is told)

      **ISSUE (2026-08-08):** the ACCEPT path reports "FILE_SAVED" + new content to the
      caller but never writes anything — `DiffReview.kt:56` completes the future without a
      document write or `FileDocumentManager.saveDocument`, so the verdict lies to the CLI
      (editor buffer and disk both unchanged, verified). Reject path is correct
      (DIFF_REJECTED, file untouched). Also: dead callers leave the Accept/Reject
      notification balloon up forever, inviting stale-balloon misclicks; verified via
      direct WS calls since the model cannot invoke openDiff on CLI 2.1.226 (see 10.1).

## 11. Dev aids

- [x] 11.1 Ctrl+Alt+G renders the gallery of transient states, no console errors (F12 to check)

      **ISSUE (2026-08-08):** the Ctrl+Alt+G keystroke is DEAD on this setup even with the
      composer focused — the JS keydown handler (chat.html:3670) never fires; same family as
      1.5's Ctrl+N. Gallery itself verified via `window.__gallery()` over CDP: all transient
      states render (19 tool lines, 5 cards, 2 asks, 2 diffs, todos, cut markers), zero real
      console errors (only the benign ResizeObserver-loop warning).
- [x] 11.2 F12 in the panel / Find Action "Claude Brains: Open DevTools" opens DevTools;
      http://localhost:9222 lists "Claude Brains — chat panel"

      **ISSUE (2026-08-08):** F12 is DEAD even with the webview focused (chat.html:3671
      handler never fires) — this machine is one of the "F12 dies inside JCEF" setups
      plugin.xml warns about, and gotchas.md's "F12 in the panel works" note needs updating.
      Find Action route works; the 9222 route carried every CDP probe of this whole pass.
      The webview keydown chords (Ctrl+N / Ctrl+Alt+G / F12) likely need IDE-level action
      shortcuts instead of JS handlers.

---

When every box is ticked I'll ask for your confirmation that the pass is complete.
