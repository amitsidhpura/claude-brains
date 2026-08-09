# Manual test pass — STANDALONE

Self-contained checklist for a full manual sweep of every shipped feature.
**Standalone checklist** — deliberately referenced nowhere else; it describes itself.
It stays in the repo after the pass as the record of what was manually verified.
Tick items by telling me the number (e.g. "3.4 done").

Setup: `cd plugin && ./gradlew runIde`, open the Claude Brains tool window.
Items marked **(hard to trigger)** need a specific situation — skip if it doesn't occur naturally.

Defect notes use exactly two markers: "ISSUE (date):" = open, "RESOLVED (date) — how:" = closed,
where *how* is one of fixed / removed / not a bug. `grep -c '\*\*ISSUE'` is always the open
count (this header deliberately avoids the bold pattern so it doesn't count itself).

---

## 1. Launch & chrome

- [x] 1.1 Panel loads in the sandbox — no dead/blank webview
- [x] 1.2 Welcome screen: brain logo, "Claude Brains v0.3.3" badge, slogan
      "Develop in the IDE. Configure in the Terminal.", `/` and `@` hint lines
- [x] 1.3 Tool-window stripe icon: grey when closed, white while open
- [x] 1.4 Header shows conversation title once one exists; Refresh button re-resumes the session
- [x] 1.5 New conversation via the New button and `/clear` (no keyboard shortcut — by design)

      **RESOLVED (2026-08-09) — removed:** the Ctrl+N chord was removed rather than fixed. It was
      swallowed by the IDE ("Go to Class"), and the plugin now binds no shortcuts at all.
      The New button and `/clear` both send `kind:'new'`.
- [x] 1.6 Scroll fades at top and bottom of the conversation while content is scrollable
- [x] 1.7 Escape closes whatever popup is open (menus, history panel, lightbox, task roster)

      **RESOLVED (2026-08-09) — not a bug, sandbox artifact:** the "reopening glitches after
      Escape" note from 2026-08-07 does not reproduce in the installed PhpStorm 2026.2, nor
      over CDP. Probed the live panel through the real handlers: Escape and click-toggle leave
      byte-identical DOM state, and reopening works after either (open→true, Escape→false,
      reopen→true, matching the click-close control exactly). `tg()` derives everything from
      the `.show` class, so removing it IS a complete close — there is no second state flag for
      Escape to miss. The remaining candidate is IDE-level focus: a real Escape passes through
      IntelliJ's key dispatcher, which returns focus to the editor, so the next click is spent
      restoring panel focus instead of hitting the chip. That is invisible over CDP (synthetic
      events bypass the IDE) and differs between the runIde sandbox (PhpStorm 2024.2, stock
      keymap, fresh config) and a real install. Treat as a sandbox symptom, not a plugin defect.

      **RESOLVED (2026-08-09) — fixed:** Escape now sticks on the slash menu. A `slashEscaped` flag is
      set when Escape closes it; the `input` handler skips its `.show` re-assert while the
      flag holds. Re-armed when the text leaves the `/^\/[\w-]*$/` shape, when the slash
      button opens the menu explicitly, and by `clearComposer()` (programmatic clears fire no
      input event, so the composer would otherwise wake up escaped). Same-day follow-up: the
      slash menu also got the @-menu's soft-reopen contract (see the 2.8 enhancement note) —
      the evaluation now lives in `slashAuto()`, shared by typing, refocus, and
      click-into-composer; outside-click reopens on return, Escape still sticks (headless
      harness 8/8, commands seeded via `system/commands_changed`).

      **RESOLVED (2026-08-09) — fixed:** Escape now closes permission-card split-button menus — a
      `closeCardMenus()` rung sits in the Escape chain between the lightbox and `activeMenu()`
      (card menus are transient DOM outside `MENUS`, so the existing walk couldn't see them).
      Both fixes mirrored in `design/mockup.html` and verified there via a headless-Chrome
      probe of the mirrored logic: Escape-then-typing stays closed through `/c`→`/co`, leaving
      the shape re-arms, button-open overrides, and a card menu + composer menu layered pair
      closes in order (card first, `.open` cleared, composer on the second press). Live-panel
      re-check in the next `runIde` session.

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

      **RESOLVED (2026-08-09) — fixed:** the menu was OPENING invisibly all along — `#mention`
      and `.mi` had no CSS anywhere, so the div was `position: static`, which ignores the
      viewport `left/top` that `openMenu()` sets and rendered it as an unstyled transparent
      block in normal flow below `#app`. The JS/Kotlin pipeline was healthy end-to-end (CDP
      probe on the installed IDE: 39 files fed, regex matched, 20 rows built). Fix is
      CSS-only: `#mention` gets `position: fixed` + the standard popup skin, `.mi` rows the
      standard row/hover/sel treatment (chat.css, next to `.popup`). Verified live by
      injecting the new rules into the running panel over CDP: menu renders above the
      composer (screenshot), `@sal` filters to 1 row, ArrowDown moves the highlight, Enter
      inserts `@src/Salutation.php ` and closes, Escape closes. While here, gave the mention
      menu the same Escape-stick contract as the slash menu (`mentionEscaped` flag —
      `updateAuto()` reopens on every keystroke otherwise). The mockup's static `#mention`
      fixture picks the new styles up from the shared chat.css automatically.

      **Follow-up, same day:** once visible, the menu turned out to have no dismissal
      contract — no outside-click close (the document click handler never knew `#mention`)
      and it stacked with composer popups (user screenshot: attach menu open on top of the
      file list). Fixed by joining it to the exclusive popup layer: the document click
      handler hides it on any click outside `#mention` (including clicks into the textarea,
      where a caret move would stale `menuStart` and a later pick() would splice wrong),
      `tg()` hides it whenever a composer popup toggles, and `openMenu()` closes composer +
      card popups when the @-menu opens. Verified against the REAL chat.html — spliced with
      chat.css and live-captured `window.LIMITS` exactly as `loadUi()` does, run headless
      with the file list fed through `window.onClaudeEvent` — 12/12 assertions: outside
      click closes, both stacking directions exclusive, row-click pick intact, composer
      popups unaffected, and the `mentionEscaped` Escape-stick contract verified the same
      way (upgraded from "pending runIde"; only real-JCEF confirmation remains).

      **Enhancement, same day (user request):** returning the caret to an @-token brings the
      menu back — refocusing the composer or clicking into the textarea runs `updateAuto()`,
      which recomputes the token at the new caret (open in a token, closed in plain text,
      nothing stale). The two dismissals deliberately differ in weight: outside-click is
      soft (reopens on return), Escape is hard (`mentionEscaped` sticks until the token
      shape changes). A `!activeMenu()` guard on the focus path keeps `tg('slashMenu')`'s
      `input.focus()` from opening the @-menu over the slash menu the user just requested.
      Headless harness 9/9: refocus reopens / plain text doesn't / click-in re-evaluates /
      Escape sticks across both return paths / attach-menu guard + click-in takeover /
      slash-button conflict. The slash menu shares the whole contract via `slashAuto()`
      (see the 1.7 note) — the two text-driven menus behave identically on dismissal and
      return, and re-evaluation on return proved them mutually exclusive by shape.
- [x] 2.9 Image paste and drag-drop → chip in the composer; renders in the sent user box

      **RESOLVED (2026-08-09) — fixed:** drag-drop was dead because JCEF-on-Linux never
      turns an OS file drag into DOM drag events for an OSR surface — the page's own `drop`
      handler was correct all along (proven: a synthetic DataTransfer drop on the live panel
      produced a chip instantly). Fix adds the missing delivery layer: an AWT `DropTarget`
      on the browser component (`ChatPanel.installFileDrop`) accepts `javaFileListFlavor`,
      reads files off the EDT (25 MB/file cap, `MAX_DROP_BYTES`), base64s them, and calls
      the page's new `__dropFiles`, which applies the same fileKind gate and media-type
      normalisation as paste/picker. Headless harness 4/4: png + code-file chips, unsupported
      binary skipped, pdf normalised, DOM drop path regression-clean. The DOM handler stays
      wired — if a future JBR delivers native DnD, drops would arrive twice; the comment on
      installFileDrop says to remove the DOM path then. NEEDS a hardware drag in the next
      `runIde` — AWT delivery over the OSR component is the one unproven link (synthetic
      events cannot cross the OS→AWT boundary).
- [x] 2.10 PDF / text file attach → document chip with the real filename
- [x] 2.11 After an error ends a turn, a ↻ Retry link appears; clicking re-runs the last
      prompt including its images
- [x] 2.12 Paperclip button opens the attach menu → native file picker → chip in the composer
- [x] 2.13 The [/] button opens the slash-command menu, same as typing `/`
- [x] 2.14 Delete key forward-deletes in the composer and model search (alone, with a
      selection, and Ctrl+Delete for a word) — no stray character appears

      **RESOLVED (2026-08-09) — fixed:** user-reported post-pass: Delete inserted a tofu
      char that only backspace removed, replacing any selection. Matches JCEF-on-Linux
      sending the AWT keyChar 0x7F through as TEXT instead of forward-deleting (inferred
      from the symptom triple; the char was backspaced away before it could be probed).
      Two-layer fix in chat.html, document-level so `#modelSearch` is covered too:
      keydown does the forward-delete manually (selection-aware, Ctrl+Delete = word) and
      cancels the native event; a capture-phase input filter strips all control chars
      except \t \n from any text field with the caret preserved — correct even if JCEF
      ignores the cancel (delete happens, tofu stripped) and kills same-family siblings
      (e.g. an ESC 0x1B). Verified via the spliced-chat.html headless harness, 7/7: mid,
      selection, Ctrl-word, end-noop, 0x7F strip, \t\n survive, mention-menu re-filter
      through the dispatched input event. D1 doubles as proof the cancel prevents
      double-delete in a compliant browser. Needs a real-JCEF hardware-key check next
      runIde (synthetic events can't reproduce the native path).

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

      **RESOLVED (2026-08-09) — fixed:** auto-approved Edit/Write/MultiEdit now mount the
      SAME "✓ Applied" `.card.warn` replay draws, built from the tool_use INPUT (the live
      wire carries no structuredPatch — probed 2026-08-05). Design is optimistic-with-
      supersede so it covers every auto-approval source (acceptEdits/auto mode, saved
      allow-rules, pre-authorized scratchpad paths), not just mode state: at
      `content_block_stop` the parsed input is stashed on the `toolsById` record and a new
      `{kind:'lineStart'}` bridge round-trip asks Kotlin for the gutter line (pre-apply is
      the only moment `old_string` is still findable; a late/null answer degrades to no
      gutter). If a `permission_request` arrives, `supersedeEdit(tool, file)` marks the
      pending record card-owned (frames carry no tool_use_id; tool+file is the correlation)
      so manual mode never double-renders. At `tool_result`, un-superseded non-error edits
      mount the card via `fillAppliedCard` — extracted from `replayCard` so live, replay,
      and the permission preview share one diff producer (`previewHtml` now delegates to
      `replayDiff`). Bonus fixes riding along: MultiEdit was handled NOWHERE — its
      permission card showed an EMPTY preview (approve a multi-hunk edit blind) and live
      showed a bare tool line; `replayDiff` gained an `edits[]` branch (per-edit sections,
      ⋯ separator, gutter on the first) and `editLineStart` a MultiEdit branch
      (edits[0].old_string). Verified on the spliced headless harness 8/8 live scenarios
      (auto Edit with gutter, RESULT_SKIP intact, manual no-double-render, Write, MultiEdit,
      MultiEdit card preview, error edit, late answer) + 3/3 replay regression (old/new,
      structuredPatch hunks, MultiEdit patch; zero JS errors). Re-verify visually next
      runIde in acceptEdits mode against a replay of the same session.
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

      **RESOLVED (2026-08-09) — fixed:** the plumbing wrappers are now stripped on BOTH
      paths through one rule: `RenderLimits.PLUMBING_TAGS` + `stripPlumbing()` (Kotlin owns
      it; the tag list rides `LIMITS.plumbingTags` so the JS mirror in chat.html cannot
      disagree). Applied before the cut on each side — SessionStore for replay, onUserEvent
      for live — so the OUT cap counts real output, and a wrapper-only-empty result (bare
      `<system-reminder></system-reminder>`) now renders no OUT box at all. Tags only; the
      inner message is kept, matching the TUI. Pinned by RenderLimitsTest (round-trip
      literal, LIM.plumbingTags usage, strip unit test with a negative control for ordinary
      angle-bracket output) and headless harness 4/4 (failed-Write strip, empty-file-Read
      strip on a non-error result, empty suppression, diff-style `<`/`>` Bash output
      untouched). The 7.4 async-launch metadata leak is a separate note — it needs
      suppression, not stripping.
- [x] 5.10 Tool-returned image renders under the tool line (ask it to Read a PNG)
- [x] 5.11 Clicking a file path opens it in the editor; a path that no longer exists → balloon,
      not a silent nothing
- [x] 5.12 Edit/Write diff: real line numbers in the gutter, context rows around the change;
      a huge diff caps at 400 rows with a truncation marker
- [x] 5.13 Todo checklist renders inline when the model plans with tasks, updating as items
      complete; each Task* tool line carries its OWN snapshot, live matching replay

      **RESOLVED (2026-08-09) — fixed** (user-reported post-pass): live Task* checklists
      appended at the TURN'S END — `todoList`'s `el()` mounts at the current position, and
      the `__tasks` frame carried no correlation to the tool call that asked. A parallel-call
      turn (two TaskUpdates in one assistant message) streamed both tool lines first, then
      both result snapshots stacked below the last line as detached, unlabelled duplicates —
      while replay correctly showed each labelled line (completed / in_progress) with its own
      list. Fix: the `tasks` bridge request now carries the `tool_use_id`, `pushTasks` echoes
      it on the `__tasks` frame, and the handler relocates the built box under the requesting
      line (`(r.io || r.el).after(box)`) — replay's layout, produced live. A frame without an
      id (evicted line) falls back to append-at-end. The stale todoList comment claiming
      Task* "isn't handled here" is rewritten. Headless harness 6/6: the reconstructed
      screenshot turn renders line→todos→line→todos with differing snapshots, id rides the
      request, no-id fallback renders, TodoWrite's inline path unchanged, replay records
      alternate correctly. This stacking was also a chunk of 5.14's heavy-turn DOM load.
- [x] 5.14 Auto-scroll pins to the bottom while streaming; scrolling up releases the pin;
      scroll-to-bottom button glides; submitting scrolls down

      **RESOLVED (2026-08-09) — fixed:** two defects stacked. (1) The pin was re-derived
      from position on EVERY scroll event (`pinned = atBottom()`), so whenever content grew
      between a programmatic pin-scroll and its async scroll event, the handler measured the
      new taller height, read "not at bottom", and silently unpinned mid-stream — the heavy
      task-list turn made that race constant. The pin is now DIRECTION-based: only an upward
      move that actually leaves the bottom zone releases it (user intent — programmatic
      pin-scrolls only ever go down, content growth never moves scrollTop, and renderEarlier
      only ever increases it), while any route back to the bottom re-pins. (2) The `__tasks`
      checklist re-render never called `maybeScroll()` at all, so checklist growth stalled
      the view even with a healthy pin — it does now, matching the content_block_stop
      pattern. Verified on the headless harness 8/8, including a deterministic
      reconstruction of the race (grow-after-pin + late scroll event → pin survives),
      user-scroll-up still releasing and staying released while streaming continues,
      re-pin on return, `__tasks` growth following, and clearLogUI landing pinned.
      Feel-check the heavy-turn behaviour next runIde (headless has no compositor; real
      JCEF's rAF cadence is the production case).
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

      **RESOLVED (2026-08-09) — fixed:** the caret never appeared because the code assumed a
      compound command arrives as one suggestion per sub-command and built its split-menu
      `parts` per SUGGESTION. MEASURED against CLI 2.1.226 (headless stream-json probes, real
      `can_use_tool` captures): the rules arrive as ONE `addRules` suggestion whose `rules[]`
      holds one rule per sub-command (`factor 97 && mcookie ; openssl rand -hex 4 && base32`
      → one suggestion, four rules) — so `parts.length` was always 1 and the `< 2` gate
      suppressed the caret. Mixed payloads also exist (a Bash command rule and a `Read
      //etc/**` path rule as separate one-rule suggestions). Fix: `parts` is now one entry
      per RULE with `"sugIdx.ruleIdx"` pick tokens; a menu-row grant echoes the suggestion
      NARROWED to the picked rule(s) — `ClaudeSessionService.respondPermission` clones the
      suggestion with the rules subset, whole-button grants unchanged. The subset echo was
      wire-PROBED before building on it: allowing with `rules` narrowed to one entry
      persisted exactly `Bash(factor 97)` to settings.local.json and nothing else. Headless
      UI harness: caret + 4 rows for the real captured payload, row click sends "0.2",
      whole-set sends "0", mixed payload rows pick "1.0", duplicate rules still fold (one
      part → plain button, correctly no caret). Live check (2026-08-09, runIde): the caret
      RENDERED — and exposed a second defect: the open menu was clipped to a sliver at the
      card edge. Cause: `.turn-body`'s `content-visibility: auto` applies PAINT CONTAINMENT,
      and the card menu is the one popup living inside a turn-body — everything painting past
      the turn's box is cut. Fixed with `.turn-body:has(.card-menu.show) { content-visibility:
      visible; }` — containment lifts only while a menu is open. Lesson recorded in the CSS
      comment: containment-clipped elements still pass DOM/synthetic-click assertions, so the
      regression pin is `elementFromPoint` (containment blocks hit-testing too) — probe 4/4:
      containment auto at rest, visible while open, last menu row hit-testable at its center,
      Escape close restores containment. Second live-check polish, same day: the menu resized
      on hover — the 32px check-icon gutter is reserved on :hover only, which is exactly the
      conversations-dropdown idiom, but that idiom needs a FIXED box: the history panel is
      310px wide, while this menu shrink-wrapped its short rules, so the hover padding grew
      the whole dropdown per mouseover. Per user preference the menu now matches the
      conversations dropdown: fixed `width: 310px` (plus `min-width: 0` — the `.popup` base's
      330px min-width silently overrides a smaller width without it), hover gutter unchanged,
      long rules ellipsise inside the constant box (probed: 310px for short and long rule
      sets alike). Menu rows also gained scope tooltips — a trailing `*` is the CLI's prefix
      wildcard shown verbatim ("any command starting with…"), no `*` grants exactly that
      command (probed: both tooltip texts). NOTE for
      future passes: the 2026-08-07/08 grants persist in the testing project's
      settings.local.json — permission cards won't reappear for those commands until the
      rules are removed or novel commands are used (this pass's probes used factor/mcookie/
      openssl/base32 for that reason, and never granted them).
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

      **RESOLVED (2026-08-09) — fixed:** exactly as diagnosed on 2026-08-07 — `chip.hidden =
      true` was set correctly but `.chip-btn { display: inline-flex }` beat the UA's
      `[hidden] { display: none }`, so the chip never actually hid; with empty text only the
      `.bg::before` pulsing dot remained (the "lone orange dot on a new conversation").
      Fix: `.chip-btn[hidden] { display: none; }` in chat.css next to the .chip-btn rule,
      plus `chip.textContent = ''` in renderBgTasks' empty branch so stale "1 task" can't
      flash on a later re-show. Covers `#ctxChip` too (same class, same defeat — its hidden
      state now computes to none instead of relying on empty text). Verified 6/6 on the
      headless harness driving real `background_tasks_changed` frames: initial state truly
      hidden, "1 task" shows, emptying hides (computed display:none, text cleared, roster
      popup closed), "2 tasks" re-shows, re-empties, ctxChip family confirmed. The live
      panel had the defect ACTIVE when probed (hidden=true, computed display:flex) — the
      injected rule fixed it in place; the shipped CSS takes over on the next build.
      Confirmed END-TO-END same day with a REAL background task (`sleep 30 &&
      echo finished` via background Bash, watched over CDP at 2s resolution): idle
      display:none → chip "1 task" at +23s with the CLI's own description on the roster →
      full vanish (hidden + display:none + text cleared) at +52s, right when the sleep
      ended. Fixture 04 also gained a computed-display assertion — the original
      `hidden`-property check was structurally unable to catch this bug.
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

- [x] 11.1 `window.__gallery()` renders the gallery of transient states, no console errors

      **RESOLVED (2026-08-09) — removed:** the Ctrl+Alt+G chord was removed rather than fixed; the
      gallery is reached by calling `window.__gallery()` from DevTools or over CDP. Verified
      that way: all transient states render (19 tool lines, 5 cards, 2 asks, 2 diffs, todos,
      cut markers), zero real console errors (only the benign ResizeObserver-loop warning).
- [x] 11.2 Find Action "Claude Brains: Open DevTools" opens DevTools;
      http://localhost:9222 lists "Claude Brains — chat panel"

      **RESOLVED (2026-08-09) — removed:** the F12 chord was removed rather than fixed — this machine
      is one of the "F12 dies inside JCEF" setups plugin.xml warns about. Find Action is now
      the only route in; the 9222 route carried every CDP probe of this whole pass.

---

When every box is ticked I'll ask for your confirmation that the pass is complete.
