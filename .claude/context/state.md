# State

## Current focus
**2026-08-16 (second session): plan-card UI polish shipped + marketplace screenshots refreshed.**
The plan feedback input now wears the ask card's "Other" dress (shared `--warn-field` #201c1a
token, warn-border frame) with a `.plan-sep` hairline between plan and decision surface —
mockup-first, then ported to chat.html (`done()` removes the separator with the input for
replay parity). Fixture 48 grew 5 assertions; live harness **308**. Five fresh 2400×1520
listing frames replaced the 2026-07-31 trio in `design/marketplace/` (user uploads them to the
Marketplace web form manually). Earlier same day: the feedback feature itself (see below).
Register still **0 open**. Next real step is the version bump.

## How plan feedback travels (all probed on CLI 2.1.233 — see gotchas for the traps)
- **Deny** → the typed text IS the control-response `message`, delivered to the model VERBATIM
  as the ExitPlanMode tool_result → it revises without asking. Empty text → the stock
  `RenderLimits.REJECT_MESSAGE` (one copy, shared with SessionStore's replay filter).
- **Approve** → the text is APPENDED TO THE APPROVED PLAN via `updatedInput.plan` under
  `RenderLimits.PLAN_NOTES_MARKER` ("## User notes on approval") — the tool_result echoes the
  approved plan, so the model reads the note in the SAME message as the approval, before its
  first implementation call. Terminal-equivalent: the TUI's shift+tab pushes acceptFeedback as
  an extra text block on that same tool_result (internal-only path; see gotchas for why we
  cannot use it). The note also lands in the saved plan file — a deliberate trade.
- **Mode rows** park their switch in `pendingPlanMode` (chat.html) and only bridge when the
  CLI's post-approval permissionMode broadcast arrives — an immediate set_permission_mode ALWAYS
  lost to the CLI's prePlanMode restore. The chip aliases the broadcast `default` → `manual`.
- Wire path: chat.html `respondPermission(id, allow, suggIdxs, text)` → ChatPanel "perm" reads
  `text` → ClaudeSessionService → ClaudeCli builds the response. Webview is mechanism-blind.
- Replay: SessionStore captures `planFeedback` (deny = tool_result string, filtered against the
  stock message; approve = parsed back out of `toolUseResult.plan` by the marker) and maps
  mid-turn `queued_command`/`prompt` attachment records to user bubbles (deduped against
  delivered user records). Footers quote via one `fbQuote()` helper (72-char cut, live+replay).

## Testing — the standing setup
- Live harness: `python tools/live_harness.py`, baseline **308**; `./gradlew test` **107**.
  Fixture 48 (10 steps) is the first-ever plan-card coverage; its negative control was RUN
  (7 render assertions failed pre-fix; SessionStore stash-runs failed 2 and 3).
- Sandbox debug port: `./gradlew runIde -PjcefDebugPort=9223` + `CLAUDE_BRAINS_CDP_PORT` for
  cdp.py/harness — added because the real IDE held 9222. CAVEAT: the sandbox's own hand-set
  Registry value (9222) beats the property; verify which panel a port serves BY CONTENT before
  driving it (turns count / distinctive text), never by assumption.
- Probe scripts for the wire live in the session scratchpad (probe_plan_*.py, not committed);
  always run them under `timeout N` — a bare blocking readline() orphans them (see gotchas).

## Next steps
- [ ] **Release the next version** (`docs/release.md`). New since 0.6.0: custom-command menu,
      the 16-command set, three rendering fixes, three UI fixes (2026-08-15), the plan-card
      feedback feature + chip alias, and the feedback-field restyle + separator (2026-08-16).
      `changeNotesHtml` in `plugin/build.gradle.kts` must be rewritten or `buildPlugin` refuses.
- [ ] User uploads the five new `design/marketplace/` screenshots via the Marketplace web form
      (listing screenshots are manual — the workflow only re-posts the zip).
- [ ] Then backlog order: reloaded-webview log replay, kill-background-process from the panel,
      editor accept/reject v2 tweak-travel.

## Known gaps (deliberately left)
- Plan-card keyboard shortcuts (Enter = keep planning, Shift+Tab = approve with feedback)
  deferred by the user 2026-08-16; handlers slot into the same `done()` paths (backlog).
- Feedback input surfaced on plan cards only; the wire plumbing is generic for other cards.
- Paths inside free prose still not shortened; `/batch` fan-out verified at N=2 only; the
  command sweep ran against one PHP CLI repo; queue-path `.q-row` sweep still inconclusive.
- Webview reload replays the INITIALIZE-time roster (mid-session commands vanish — backlog).
- Sub-agent WORK outcome not surfaced; no kill-background-process from the panel;
  `prefers-reduced-motion` unverified against the real OS setting.

## Which machine — check FIRST, both are real
2026-08-16 ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. Slash-menu fixtures + plan-probe scratchpad exist on Linux only.
