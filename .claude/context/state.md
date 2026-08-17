# State

## Current focus
**2026-08-17 (seventh session): the checklist's low-tier mark changed 🟨 → ⬜; state ids corrected.**
Nothing in flight. No code touched — docs and context only.
- `docs/feature-checklist.md` open-low is now **⬜** (16 rows + the status-mark key + the scope-rule
  line). Full set: ✅ done / 🟡 partial / 🟥 high / 🟧 medium / ⬜ low / ➖ by design / 🚫 declined.
  The earlier "no ⬜" rule from the 2026-08-17 register decision is explicitly lifted for the low
  tier (see the newest `decisions.md` entry) — do not "correct" it back. 🟨 survives only in
  `journal.md` and the superseded decisions entry, on purpose.
- **Ids in this file had drifted from the register and are now re-derived from it.** If you
  paraphrase `docs/feature-checklist.md` here again, read the ids out of the file — never copy
  them forward from a previous save.

**Previous (2026-08-17 sixth): checklist rows 7.7 + 7.10, committed as `a66fb17`.**
- **7.7 slash aliases** — `canonicalCmd()` in chat.html maps a roster alias to its command;
  `renderSlash` scores aliases like names (rank 0/1/2) and shows them muted on the row
  (`.pi-alias`, chat.css); `submit()` resolves a typed alias BEFORE `cmdKind` and rewrites the
  turn to the canonical name (`/review …` → `/code-review …`; `/new`/`/reset` → `/clear`'s native
  branch). Fixture **52-slash-aliases** (12 assertions), negative control RUN against eb52eb1's
  chat.html: 7 DISCRIMINATING fail / 5 GUARD pass — and the control corrected the fixture (the
  first "review → /code-review first" row was not a discriminator: `review` is a substring of the
  name; `/reset → /clear` is the rank-0 proof now). Live harness baseline **356** (was 344).
- **7.10 reloaded-webview roster** — `ChatPanel.lastCommandsChanged` keeps the newest raw
  `commands_changed` frame from the current CLI (cleared in `onInit`) and `seedUi()` replays it
  after `pushInitMeta` (REPLACE semantics, same handler). Verified live over CDP: a command file
  dropped mid-session → roster 54 with `reload-probe` (project badge) → `location.reload()` →
  still 54 with it, `renderSlash('reload')` lists it. No fixture (the harness cannot reload the
  page it drives); the CDP recipe is in journal 2026-08-17 sixth.

## Open work — ids re-derived from `docs/feature-checklist.md` on 2026-08-17 (seventh)
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG, undecided since
  2026-07-30] · **8.11** side question [MD, NEW] · **8.14** reloaded-webview **log** replay [LG]
  (the roster half shipped as 7.10; the log half has not) · **9.4** fast-mode toggle [SM] ·
  **11.3** kill-background-process [MD].
- **Nine [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 9.4, 9.5, 12.3, 12.6, 13.2, 14.2.
- Five features on main UNRELEASED since 0.7.2 (2.4, 2.10, 2.11, 7.7, 7.10). `plugin.xml`
  description still says only `/compact` + `/clear` are enabled — fix in the release-notes pass.
  **A release starts only when the user asks.**

**Previous (2026-08-17 fifth): rows 2.10 + 2.11.** Autosave-before-read/write as a host SDK hook
(`initialize` declares `PreToolUse Edit|Write|MultiEdit|Read → autosave`; `hook_callback` answered
from the EDT after `saveDocument`, `{continue:true}` on every path; `Autosave.kt`). Verified
headlessly and live (unsaved buffer is what Read returned). Stale `~/.claude/ide/*.lock` swept
dead-pid on every lock write (`IdeLockFile.sweepStale`, 17 → 2).

**Previous (2026-08-17 fourth): feature checklist re-audited against 2.1.233; `close_tab` finished
(row 2.4).** `docs/feature-checklist.md` is now 16 sections, 124 rows numbered `section.row`
(ids STABLE — retire by striking, never delete), marks ✅ / 🟥🟧⬜ open by importance / ➖ by
design / 🚫 declined, effort `[XS|SM|MD|LG]` on open rows, tags **[NEW]** / **[DECIDE]**, all meta
in the header. 2.4: `close_tab` resolves only the review opened under that `tab_name`; both close
tools reply reference-exact (`TAB_CLOSED` / `CLOSED_<n>_DIFF_TABS`); verified over the bridge WS
with two open diffs.

**Releases so far — all Approved on the Marketplace the same day they went up** (detail per
release in `journal.md`; process in `docs/release.md`):
- **0.7.2** (2026-08-17, `40bc060` / `v0.7.2`) — / menu insert-vs-send rule (`cmdTakesArg`) and
  the Effort-label rail (`.ef-label`; the real gap was 4px not 7 — the headless probe lacked the
  `#inputbar` ancestor). Fixtures 49/50/51.
- **0.7.1** (2026-08-16, `91a6ba5` / `v0.7.1`, fix `3c86aa2`) — the top-fade wash: `body.at-top`
  was toggled only by the scroll handler and the two replay paths, so a log that never scrolls
  never hid `#fade-top`, and local-command output is the one first block with no sticky
  `.msg-user` above it. `updateTopFade()` now also runs from `maybeScroll()` and `clearLogUI()`.
- **0.7.0** (2026-08-16, `59d94fc` / `v0.7.0`) — plan-card feedback field + split Approve + mode
  parking + `default`→manual chip alias; custom commands / skills / MCP prompts auto-enabled in
  the / menu with source badges; 16 built-ins enabled; the 2026-08-15 fixes; feedback-field
  restyle + `.plan-sep`. Five new `design/marketplace/` screenshots uploaded by the user.
- Manual-test issue register: **0 open / 25 resolved**. Tests 106; harness 356.

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
- Live harness: `python tools/live_harness.py`, baseline **356** (fixtures to 52); `./gradlew test` **106**.
  Fixture 48 (10 steps) is the first-ever plan-card coverage; its negative control was RUN
  (7 render assertions failed pre-fix; SessionStore stash-runs failed 2 and 3).
- Sandbox debug port: `./gradlew runIde -PjcefDebugPort=9223` + `CLAUDE_BRAINS_CDP_PORT` for
  cdp.py/harness — added because the real IDE held 9222. CAVEAT: the sandbox's own hand-set
  Registry value (9222) beats the property; verify which panel a port serves BY CONTENT before
  driving it (turns count / distinctive text), never by assumption.
- Probe scripts for the wire live in the session scratchpad (probe_plan_*.py, not committed);
  always run them under `timeout N` — a bare blocking readline() orphans them (see gotchas).

## Next steps
Done items live in `journal.md`; this list is only what is still open.
- [ ] Get the user's yes / later / no on the **nine [DECIDE]** rows (8.7, 8.10, 8.11, 9.4, 9.5,
      12.3, 12.6, 13.2, 14.2) in `docs/feature-checklist.md`.
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts (Enter / Shift+Tab) →
      reloaded-webview **log** replay (**8.14**) → kill-background-process (**11.3**) →
      tweak-travel (**3.5**). **9.4** fast-mode toggle [SM] is the cheapest 🟥 left.
- [ ] `plugin.xml` description: the "/compact, /clear only" line is stale — fix in the next release.
- [ ] Consolidation pass on the context files: `decisions.md` (761 lines) and `gotchas.md` (729)
      are far over the ~100-line target — promote, then cut. Flagged 2026-08-17 (seventh).

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
