# State

## Current focus
**2026-08-29 (twenty-second session): the checklist goal (every section ✅ by 2026-08-30) is DONE a
day early — `docs/feature-checklist.md` = 82 ✅ · 46 ➖ (128 rows), all 17 headings ✅, no
[DECIDE].** Today (all pushed, last `d886879`): 8.14 declined; `/clear` removed
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

## Released — 0.12.0 (2026-08-29)
**0.12.0 is the shipped version** (tag `v0.12.0`, commit `0e1af47`): /btw, files-changed review,
tweak-travel, stop-task, settings schema, 1.21/1.23/1.24, /clear removed. Verifier 7/7 Compatible,
GitHub asset byte-identical, feed live, `marketplace-upload` green (run 33257914722); Marketplace
**Approved** the same day (user's screenshot: JetBrains' verifier Compatible 2025.3 → 2026.2.2 rc, plus an
IDE-run check with no issues).
Nothing unreleased on `main`. Change notes now carry exactly the LAST THREE versions + a GitHub
releases link (rule in build.gradle.kts kdoc and release.md 1b).

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
- [x] Marketplace page after 0.12.0 (2026-08-29): screenshots uploaded by the user; description
      VERIFIED via `api/plugins/33274` to match plugin.xml (`/btw` in, `/clear` and the diffs bullet
      out) — the "description comes from the plugin on upload" setting is confirmed working.
- [x] `vscode/` re-extracted to **2.1.251** on 2026-08-29 (from 2.1.241; 2.1.250 was still on disk —
      extension.js +14.5 KB, webview/index.js +2.4 KB between the two). **Re-audit deferred by the
      user: wait a few CLI versions past 2.1.250 before the next checklist re-audit.**
- [x] Gist `b2d033439ba4ca5bcd018f4fe5eef773` verified identical to `.claude/skills/context/SKILL.md`
      on 2026-08-29 (push again after any SKILL.md edit: `gh gist edit <id> -f SKILL.md <path>`).
- [ ] **User errands**: Windows `./gradlew test` + VFS click check.

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
