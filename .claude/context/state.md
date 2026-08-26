# State

## Current focus
**2026-08-26 (fifteenth session, two saves): effort moved into the MODEL menu with no chip suffix
(committed `def5366`), then `docs/feature-checklist.md` was re-audited against 2.1.246 (this save
commits it). Nothing is in flight.**
- **Effort slider is the last row of `#modelFooter`** (`plugin/src/main/resources/webview/chat.html`),
  under the 1M / Fast mode / Thinking switches; `#modeMenu` holds modes only; model popup header is
  **Models**. **No chip shows the effort level** — three suffix forms were rendered and rejected
  (decisions 2026-08-26). **Do not re-propose a chip suffix.** `.ef-row` is deliberately NOT
  `.tgl-row` (fixture 55 counts exactly three toggle rows).
- **Checklist is current to 2.1.246** (both references; last full re-audit 2026-08-26). Surfaces
  barely moved — the audit paragraph in the doc has the measurements. New row **9.10 🚫** records
  the declined roster-flag gate; 127 rows.
- **0.11.0 is the shipped version** (tag `v0.11.0`, commit `4c8899b`). Everything since is
  unreleased; no release was requested. Next release = a fresh bump when the user asks.

## Open work — ids verified against `docs/feature-checklist.md`
- 🟥 high rows left: **3.5** tweak-travel [LG] · **8.7** rewind/fork [LG] · **8.11** side
  question [MD, probe pre-paid] · **8.14** reloaded-webview log replay [LG] · **11.3**
  kill-background-process [MD, `stop_task` accepted].
- **Seven [DECIDE] rows** await the user: 8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2.
- `/clear [name]` decision still open (checklist 7.6).
- `docs/slash-commands.md` is labelled 2.1.233 — but the roster is MEASURED unchanged through
  2.1.246 (two audits), so the sync is a version-label edit only, not a re-verification.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **467**; `./gradlew test` **116**.
- Fixture 51 = `51-model-menu-effort-rail.json`: pins the moved row's icon against the 1M row's,
  inherits the title rail from fixture 55, step 3 pins the ABSENCE of the level on both chips.
  Three negative controls in its provenance — read before changing any assert.
- Sandbox **PhpStorm 2024.2.6**; start (must run from `plugin/`, background tasks start in the
  REPO ROOT): `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` DETACHES** — gradle's exit code says
  nothing about the IDE; check `pgrep -f 'idea.system.pat[h]'`; kill by pid, wait for CDP to vanish.
- **ALWAYS verify the running build BY CONTENT over CDP before trusting a fixture run** — a relaunch
  served the PREVIOUS build's bytes (gotchas § Testing). Control builds restore the WHOLE
  `plugin/src/main/resources/webview/` directory.
- Re-audit method (repeatable): diff extension `package.json` + `.tool("…")` roster against the
  `vscode/` extraction; diff CLI typed vocabulary via `subtype:<ident>("initialize")`; headless
  `initialize` on both binaries and diff keys/commands/models. Probe scripts live only in session
  scratchpads — rewrite from the 2026-08-26 journal if needed.

## Next steps
- [ ] Get yes / later / no on the **seven [DECIDE]** rows (8.7, 8.10, 8.11, 12.3, 12.6, 13.2, 14.2).
- [ ] Decide `/clear [name]` (checklist 7.6); relabel `docs/slash-commands.md` to 2.1.246 (label only).
- [ ] Backlog order (`backlog.md` § Next up): plan-card keyboard shortcuts → reloaded-webview
      log replay (**8.14**) → kill-background-process (**11.3**) → tweak-travel (**3.5**).
- [ ] Sync the **Marketplace web description** (hand-edited; uploads don't refresh it).
- [ ] `conventions.md` is ~50% over its briefing-tier cap — move the longest war stories to
      gotchas, leave pointers (offered 2026-08-26, not yet asked for).
- [ ] **User errands**: re-extract `vscode/` to 2.1.246 (the 2.1.241 extension dir is gone; the
      extraction is the only diff base — gotchas § Testing); gist push of the context skill
      (`gh gist edit b2d033439ba4ca5bcd018f4fe5eef773 -f SKILL.md .claude/skills/context/SKILL.md`,
      check line 1 is exactly `---`); Windows `./gradlew test` + VFS click check.

## Known gaps (deliberately left)
- **The Thinking switch is INERT on Fable** — `set_max_thinking_tokens 0` accepted, fable still
  streams thinking (measured 2026-08-26). "Document only" by user decision; do not gate it.
- **Do not gate UI on roster capability flags** (checklist 9.10 🚫): haiku carries none of the
  three effort/thinking flags yet both controls work there; Fable carries the thinking flag and the
  switch is inert. Only `supportsFastMode` is gated, and only because delivery was probed.
- Effort/model conversation MARKERS dropped 2026-08-24 (partly superseded 2026-08-25: /effort now
  shows the CLI's confirmation line). Confirm-card path wrapping declined — do not re-propose.
- Typing `/model` in the composer is refused (`cmdKind` → 'tui'); the chip is the only model surface.
- 1M switch carries NO client-side validity logic, by decision. Fast-mode "· fast" marker parked.
- 5.6 keyboard-only comment pill; plan-card keyboard shortcuts deferred 2026-08-16;
  `DiffReview.open` snapshot-only lookup parked; `/batch` verified at N=2 only.

## Which machine — check FIRST, both are real
This session ran on **Linux** (`/home/syncroze/Sites/claude-brains`). Paths for both boxes in
overview.md § External references. The Windows box still owes the CRLF splice check.
