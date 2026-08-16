# State

## Current focus
**2026-08-17: two UI fixes + their fixtures on main, live-verified and pinned, NOT released.**
Shipped version stays 0.7.1 — nobody running the plugin has either fix yet. A release is the
next decision; nothing else is in flight. **Do not start release prep unasked** (see below).
1. **Effort label alignment** (`chat.css`, `.ef-label`): the mode-popup footer label sat **4px**
   left of the mode titles (14 + an 18px svg + an 8px gap = 40, against a title's 44). It now
   rides the same rail via `gap: 10px` + `flex: 0 0 20px` on the svg. CSS-only; `design/
   mockup.html` links the same sheet, so nothing to mirror. Pinned by fixture 51 (2026-08-17),
   which is also where the correction came from: the "7px" first reported was measured on a
   headless probe page WITHOUT the `#inputbar` ancestor, so `#inputbar svg {18px}` — an ID rule
   that beats both `.ef-label svg` and `.pi-ic svg` — never applied. flex-basis works precisely
   because that rule does not set it.
2. **Slash-menu pick rule** (`chat.html:2493`): `cmdNeedsArg` → `cmdTakesArg` =
   `!!(c.argumentHint||'').trim()`. Any hint, required or optional, inserts `/name ` and waits;
   only hint-less commands run on click (`data-needsarg` → `data-takesarg` at both sites).
   Trigger: clicking `/context` ran it bare instead of allowing `save`. Why + blast radius in
   decisions.md. Blast radius now MEASURED off the wire (2026-08-17, CLI 2.1.233): of the 15
   enabled commands, six insert (`/compact` `/context` `/code-review` `/simplify` `/loop`
   `/batch`) and nine still act on click; table in docs/slash-commands.md. The roster carries no
   `immediate` flag, so `argumentHint` is the only signal there is.
Both verified by the user in `runIde`, and now pinned by fixtures (2026-08-17):
`tools/fixtures/49-top-fade-at-top.json` (10 assertions) and
`50-slash-pick-insert-vs-send.json` (19). Live harness baseline is now **344** (was 308);
`./gradlew test` 106. Both negative controls were RUN — 49 against `3c86aa2~1` (the pre-FADE
build; HEAD-minus-this-session still HAS that fix), 50 against `4e351f4`. `docs/slash-commands.md`
was corrected the same day: it still described the old runs-immediately contract.
The alignment is pinned too, by `51-mode-menu-effort-rail.json` (7 assertions, control run
against 4e351f4's chat.css). Harness baseline is now **344**.
Reference facts confirmed this session: the composer sends on **Ctrl+Enter** (plain Enter =
newline); skills and command files share ONE roster with no type field, told apart only by the
`" (project)"/" (user)"` description suffix; a built-in `/context` (`[all]`) exists and is
shadowed by this repo's project skill.

**Previous (2026-08-16 fourth session): 0.7.1 RELEASED and Marketplace-Approved — the top-fade fix.**
Commit `91a6ba5`, tag `v0.7.1`; verifier Compatible 242→262, asset 200 + `cmp`-identical, feed
advertising 0.7.1, marketplace-upload run 31933586034 green in 11s, **Approved** the same day
(IDE-run row on 2026.2.1 + verifier 1.408 rows Compatible). Nothing is in flight. Tests 106/106 (state previously said 107; no test code changed — a count
difference, not a loss). The fix: `/model` on
a NEW conversation drew its stdout washed out under `#fade-top` (read as strikethrough): `body.at-top`
was toggled only by the scroll handler and the two replay paths, so an empty log that never scrolls
never hid the fade — and local-command output is the one first block with no sticky `.msg-user`
above it. Fix in `chat.html`: `updateTopFade()` now also runs from `maybeScroll()` (every `el()`
render) and `clearLogUI()`. Verified live by the user in `runIde` (`/model` on a fresh
conversation renders crisp). Fix commit `3c86aa2`; a harness assertion is optional.

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
- [x] Top-fade wash on a fresh conversation fixed and released as 0.7.1 (2026-08-16 fourth).
- [x] Fade fix verified live by the user in `runIde` (2026-08-16, `/model` on a new conversation).
- [x] 0.7.1 Approved on the Marketplace (2026-08-16, screenshot from the user).
- [x] Effort-label alignment + slash-menu insert-vs-send rule (2026-08-16 fifth), both
      live-verified and committed.
- [x] Harness assertions for the fade fix, the slash pick path AND the Effort-label rail
      (2026-08-17, fixtures 49 + 50 + 51, every control RUN against the correct pre-fix build).
      Harness **344/344**; `./gradlew test` 106/106.
- [x] Slash-hint inventory measured off the wire, not the binary (2026-08-17) — table in
      `docs/slash-commands.md`; the watch-item is closed.
- [ ] DECISION PENDING: release 0.7.2 (or fold these into a later version). Prep is NOT done —
      it was started unasked on 2026-08-16 and fully reverted; version, changeNotesHtml and
      `updatePlugins.xml` are all back at 0.7.1 and the 0.7.2 build artifacts were deleted.
      Start again from `docs/release.md` step 1 only when the user asks.
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
All five 2026-08-16 sessions (incl. the 0.7.0 and 0.7.1 releases) ran on **Linux**
(`/home/syncroze/Sites/claude-brains`). Paths for both boxes in overview.md § External
references. Slash-menu fixtures + plan-probe scratchpad + the verifier's cached IDE ladder exist
on Linux only.
