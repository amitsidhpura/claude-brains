# State

## Current focus
**2026-08-29 (twenty-first session): goal = every checklist section ✅ by 2026-08-30 EOD. Today §15
closed (15.5 later, 15.6 no), §8 narrowed (8.8 later, 8.10 later, **8.11 side question BUILT**).
Committed and pushed at the end of the session.** Sections still ⬜: **§8** (8.14 open; 8.7 parked by
the user — "keep the revert and version control last") and **§14** (all four rows, parked with it).
- **8.11 side question** shipped 2026-08-29: `/btw` (bare = open, `/btw q` = ask) → `#sidePanel`
  floating over the composer (`webview/js/67-side.js`, CSS § side question in `chat.css`) → bridge
  `{kind:'side', id, question, history}` → `ChatPanel` → `ClaudeSessionService.askSideQuestion` →
  `ClaudeCli.askSideQuestion` = control `side_question{question, history?}` → `__side {id,
  response|null, synthetic} | {id, error}`. `ClaudeCli` now keeps request-id-keyed callbacks
  (`pending`) — the first host request whose SUCCESS payload matters; `stop()` fails them. The
  roster has NO `/btw` (54 entries, 2.1.251): `CMD_LOCAL` in `65-slash.js` supplies it, `cmdKind` →
  native. Panel: single-line growing input (Ctrl+Enter), ✕ clear, close/Escape, reset on `__clear`;
  centred on `#inputcard` (`margin: 0 auto`) and copying the composer's `paddingRight` for the
  scrollbar inset. Fixture 66 (25 asserts, three controls run). Live-verified §17 MT-7.9.
- **8.11 UNMEASURED corners**: `synthetic:true` answers and `refusal_fallback` are passed through /
  ignored, never seen; the CLI's "Side question cancelled" / "Session is shutting down" errors
  render on the row verbatim if they ever arrive.
- Checklist current to **2.1.250** (128 rows; 83 ✅ · 2 🟥 · 2 🟧 · 2 ⬜ · 39 ➖). Rules still in
  force: `**id** mark [effort] **Name** — gist; facts`; the **At a glance** block is hand-maintained
  (recount with `awk` at every change); `<details>` only in the re-audit paragraphs and §17 groups.
- Effort slider: **no chip shows the effort level** — do not re-propose a chip suffix (2026-08-26).
- Destructive hover stays RED on both `.bg-x` and `.hist-del`; every other hover-revealed control is
  white — user confirmed 2026-08-29, do not re-propose (decisions.md).

## Released — 0.11.1 (2026-08-26)
**0.11.1 is the shipped version** (tag `v0.11.1`, commit `979326c`). Everything since (3.5, 3.6,
11.3, 11.5, 1.21/1.23/1.24, 13.2, 8.11) is unreleased on `main`; the next release is a fresh
bump when the user asks. `verifyPlugin` runs BEFORE the gate, read the per-IDE verdict files.

## Open work — ids verified against `docs/feature-checklist.md`
- 🟥 rows left: **8.7** rewind/fork [LG] (parked, do last) · **8.14** reloaded-webview log replay
  [LG] (rec. later).
- **[DECIDE] rows**: 8.7, 14.2 only. §14 recommendation already given: 14.1/14.2/14.3 later as one
  "worktrees" bundle, 14.4 no — parked with 8.7 by the user.
- `/clear [name]` decision still open (checklist 7.6).
- `syncGutter` (composer scrollbar inset) only re-runs when `#log` RESIZES — a scrollbar appearing
  inside it lags until the next resize (measured 2026-08-29: 10px scrollbar, paddingRight still
  14px). Pre-existing; the side panel copies the same value so the two boxes agree. Offered as a
  separate fix, not asked for.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **566** (fixtures to 66); `./gradlew test` **134**.
- Sandbox **PhpStorm 2024.2.6**; start (from `plugin/`; background tasks start in the REPO ROOT):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` DETACHES** — gradle's exit code says
  nothing; `pgrep -f 'idea.system.pat[h]'`; kill by pid, wait for CDP to vanish.
- **ALWAYS verify the running build BY CONTENT over CDP before trusting a fixture run.** Control
  builds restore the WHOLE `plugin/src/main/resources/webview/` directory.
- Fixture ids that a page-lifetime counter produces (`sq1…`) must be read from the bridge tape,
  never written as literals — fixture 66 passed once then failed with `sq5` (gotchas § Testing).
- A panel-supplied slash entry (`CMD_LOCAL`) changes every fixture that COUNTS rendered slash
  rows (46, 50, 52 bumped +1 on 2026-08-29) — recount them when adding one.
- Side-question probe script (spawn the CLI with the panel's flags, `initialize`, then
  `side_question`): reproduce from `docs/ide-mcp-protocol.md` § side_question; the scratchpad copy
  is gone with the session.

## Next steps
**GOAL (user, 2026-08-29): every checklist section ✅ by 2026-08-30 EOD** — ✅ = every row ✅ or ➖.
One section at a time: present open rows with a recommendation, get build / later / no per row,
build the yes-rows, mark the rest ➖ with the reason, refresh the heading mark + At a glance.
- [ ] **§8** — remaining call: **8.14** (rec. later → backlog § Next up keeps it). Then only 8.7
      keeps §8 ⬜, and the user wants 8.7 + §14 ("revert and version control") LAST.
- [ ] **§14** — 14.1–14.4, recommendation given (later ×3 as a worktrees bundle, 14.4 no); parked.
- [ ] **8.7** rewind / fork — the last discussion; revival notes in gotchas § Protocol.
- [ ] After every section: heading mark, At a glance counts, [DECIDE] list, backlog.
- [ ] Decide `/clear [name]` (checklist 7.6).
- [ ] Optional: make `syncGutter` react to a scrollbar APPEARING (see Open work).
- [ ] Sync the **Marketplace web description** (hand-edited; uploads don't refresh it).
- [ ] `conventions.md` is ~50% over its briefing-tier cap — move the longest war stories to
      gotchas, leave pointers (offered 2026-08-26, not yet asked for).
- [ ] **User errands**: re-extract `vscode/` to **2.1.250** (2.1.246 and 2.1.250 dirs both on disk
      2026-08-28; the older vanishes on the next auto-update); gist push of the context skill
      (`gh gist edit b2d033439ba4ca5bcd018f4fe5eef773 -f SKILL.md .claude/skills/context/SKILL.md`,
      line 1 must be exactly `---`); Windows `./gradlew test` + VFS click check.

## Known gaps (deliberately left)
- **The Thinking switch is INERT on Fable** — measured 2026-08-26, "document only" by decision.
- **Do not gate UI on roster capability flags** (9.10 ➖); only `supportsFastMode` is gated.
- Effort/model conversation MARKERS dropped 2026-08-24; confirm-card path wrapping declined.
- Typing `/model` in the composer is refused (`cmdKind` → 'tui'); the chip is the only model surface.
- 1M switch carries NO client-side validity logic. Fast-mode "· fast" marker parked.
- 5.6 keyboard-only comment pill; plan-card keyboard shortcuts deferred 2026-08-16;
  `DiffReview.open` snapshot-only lookup parked; `/batch` verified at N=2 only.
- The user's own hand-test sessions land in NO `~/.claude/projects` dir (gotchas § Testing).

## Which machine — check FIRST, both are real
The 2026-08-26 → 2026-08-29 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice check.
