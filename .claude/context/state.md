# State

## Current focus
**2026-08-16 (fourth session): post-0.7.0 fix — top fade on a fresh conversation.** `/model` on
a NEW conversation drew its stdout washed out under `#fade-top` (read as strikethrough): `body.at-top`
was toggled only by the scroll handler and the two replay paths, so an empty log that never scrolls
never hid the fade — and local-command output is the one first block with no sticky `.msg-user`
above it. Fix in `chat.html`: `updateTopFade()` now also runs from `maybeScroll()` (every `el()`
render) and `clearLogUI()`. `node --check` clean; NOT driven in the live panel (the IDE's 9222
exposed no target) — confirm on the next `runIde` by typing `/model` on a fresh conversation, then
add a harness assertion (`body.at-top` present after a bare `.blk` at scrollTop 0). Unreleased.

**Previous (2026-08-16 third session): 0.7.0 RELEASED and Marketplace-Approved.** Commit `59d94fc`,
tag `v0.7.0`. Full `docs/release.md` run: verifier Compatible on all seven PhpStorm branches
242→262 (zero warnings), asset HTTP 200 + `cmp`-identical, feed advertising 0.7.0, upload
workflow green in 17s (run 31932129931), Approved the same day with green IDE-run + verifier
1.408 rows. The user uploaded the five new `design/marketplace/` screenshots via the web form.
Nothing is in flight; the next work is the backlog order below. Register still **0 open**.

Shipped in 0.7.0 (everything since 0.6.0): plan-card feedback field + split Approve + mode
parking + `default`→manual chip alias; custom commands / skills / MCP prompts auto-enabled in
the / menu with source badges; 16 built-ins enabled; the 2026-08-15 fixes (local-command
output, `<local-command-stderr>`, mid-turn steered messages in replay, bg-chip roster reset
at the CLI boundary, `fillPath` on cards, @-menu ellipsis end, NotebookEdit path, stuck popup
highlight); the feedback-field restyle + `.plan-sep`.

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
- [x] Release 0.7.0 — done 2026-08-16, Approved on the Marketplace.
- [x] Five new `design/marketplace/` screenshots uploaded by the user (2026-08-16).
- [x] Top-fade wash on a fresh conversation fixed (2026-08-16 fourth) — committed, unreleased.
- [ ] Verify the fade fix on a live `runIde` (`/model` on a new conversation) + harness assertion.
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts (Enter / Shift+Tab),
      reloaded-webview log replay, kill-background-process from the panel, editor accept/reject
      v2 tweak-travel.

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
All three 2026-08-16 sessions (incl. the 0.7.0 release) ran on **Linux**
(`/home/syncroze/Sites/claude-brains`). Paths for both boxes in overview.md § External
references. Slash-menu fixtures + plan-probe scratchpad + the verifier's cached IDE ladder exist
on Linux only.
