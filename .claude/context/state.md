# State

## Current focus
**2026-08-29 (twenty-second session): the checklist goal (every section ✅ by 2026-08-30) is DONE a
day early — `docs/feature-checklist.md` = 82 ✅ · 46 ➖ (128 rows), all 17 headings ✅, no
[DECIDE].** Today: 8.14 declined (pushed as `8152b03`); then **UNCOMMITTED**: `/clear` removed
from the panel (7.6 ➖ — `CMD_NATIVE` = `{'btw'}`, header New button is the panel's /clear, typed
`/clear`/`/new`/`/reset` refused; verified 134/0 + harness 566/0 + live) and 8.7 / §14 decided on
the user's principle "undo and branching depend on git, not Claude" (8.7 no, 14.2/14.4 no by
design, 14.1/14.3 later as the backlog "Worktrees bundle"). Earlier today §15 closed, 8.11 built.
- 8.11 side question (`/btw`, `webview/js/67-side.js`, `ClaudeCli.pending` request-id callbacks,
  fixture 66) — UNMEASURED corners: `synthetic:true`, `refusal_fallback`, the CLI's "Side question
  cancelled" / "Session is shutting down" errors render verbatim if they ever arrive.
- Checklist rules still in force: `**id** mark [effort] **Name** — gist; facts`; the **At a glance**
  block is hand-maintained (recount with `awk` at every change); `<details>` only in the re-audit
  paragraphs and §17 groups.
- Do not re-propose: an effort chip suffix (2026-08-26); non-red destructive hovers (2026-08-29);
  Claude-side rewind/checkpoints or host git actions (2026-08-29, decisions.md).

## Released — 0.11.1 (2026-08-26)
**0.11.1 is the shipped version** (tag `v0.11.1`, commit `979326c`). Everything since (3.5, 3.6,
11.3, 11.5, 1.21/1.23/1.24, 13.2, 8.11, /clear removal) is unreleased on `main`; the next release is a fresh
bump when the user asks. `verifyPlugin` runs BEFORE the gate, read the per-IDE verdict files.

## Open work — ids verified against `docs/feature-checklist.md`
- No open checklist rows. Wants: backlog § Next up (Worktrees bundle 14.1+14.3, 15.5 debugger
  tools) and § Deferred (conversation tabs + 8.8/8.10).

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
- [ ] Likely next: a **release** when the user asks — 0.11.1 is shipped; `docs/release.md`,
      `verifyPlugin` on EVERY release, read the per-IDE verdict files. Change notes should mention
      /clear's removal (New button) and 8.11 `/btw`; screenshots + description check on the web page.
- [ ] After the next release: confirm plugins.jetbrains.com/plugin/33274 shows plugin.xml's updated
      description (user set the Marketplace to take it from the plugin; the old "hand-synced" note
      was retired 2026-08-29).
- [ ] **At the next release** (user, 2026-08-29): upload the five reshot `design/marketplace/*.png`
      on `plugins.jetbrains.com/plugin/33274/edit` — screenshots are not in the zip. Add it to the
      release checklist for that release, not before.
      `design/marketplace/img.png` (879×177, a stray paste committed in `5b50131`) is already gone
      from disk — the deletion rides with the next commit.
- [ ] **User errands**: re-extract `vscode/` to **2.1.250** (2.1.246 and 2.1.250 dirs both on disk
      2026-08-28; the older vanishes on the next auto-update); gist push of the context skill
      (`gh gist edit b2d033439ba4ca5bcd018f4fe5eef773 -f SKILL.md .claude/skills/context/SKILL.md`,
      line 1 must be exactly `---`); Windows `./gradlew test` + VFS click check.

## Known gaps (deliberately left)
- **The Thinking switch is INERT on Fable** — measured 2026-08-26, "document only" by decision.
- **Do not gate UI on roster capability flags** (9.10 ➖); only `supportsFastMode` is gated.
- Effort/model conversation MARKERS dropped 2026-08-24; confirm-card path wrapping declined.
- Typing `/model` or `/clear` in the composer is refused (`cmdKind` → 'tui'); the chip / the header
  New button are the only surfaces. No keyboard-only new conversation (no shortcuts bound).
- 1M switch carries NO client-side validity logic. Fast-mode "· fast" marker parked.
- 5.6 keyboard-only comment pill; plan-card keyboard shortcuts deferred 2026-08-16;
  `DiffReview.open` snapshot-only lookup parked; `/batch` verified at N=2 only.
- The user's own hand-test sessions land in NO `~/.claude/projects` dir (gotchas § Testing).

## Which machine — check FIRST, both are real
The 2026-08-26 → 2026-08-29 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice check.
