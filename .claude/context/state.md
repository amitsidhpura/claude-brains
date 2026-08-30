# State

## Current focus
**2026-08-30 (twenty-fifth session): 2.1.251 re-audit run early, 9.11 built and hand-tested.** The
user asked for the audit despite the "wait a few versions" plan; its single row (9.11, chip revert
when a `PreModelSwitch` hook refuses `set_model`) was built, harness-verified and walked through
by the user in four hands-on steps (§17 MT-9.11). The settings-schema warning the user saw in
`settings.local.json` is SchemaStore lag (synced to 2.1.220 on 2026-07-27, ~monthly) — decision:
wait, no code (decisions 2026-08-30). **Then 0.12.1 was released the same session** (below).
**Nothing unreleased on `main`** — 0.12.1 shipped everything since 0.12.0 (see Released).
- 8.11 side question (`/btw`, `webview/js/67-side.js`, fixture 66) — UNMEASURED corners:
  `synthetic:true`, `refusal_fallback`, the CLI's "Side question cancelled" / "Session is shutting
  down" errors render verbatim if they ever arrive.
- Checklist rules still in force: `**id** mark [effort] **Name** — gist; facts`; the **At a glance**
  block is hand-maintained (recount with `awk` at every change); `<details>` only in the re-audit
  paragraphs and §17 groups. Checklist: 83 ✅ · 46 ➖ (129 rows), all 17 headings ✅.
- Do not re-propose: an effort chip suffix (2026-08-26); non-red destructive hovers (2026-08-29);
  Claude-side rewind/checkpoints or host git actions (2026-08-29, decisions.md); an "Effort (High)"
  bracketed label or a blue track on the effort slider (2026-08-29).
  Copilot-derived features other than terminal-output context (2026-08-29, decisions.md).
- Audit state: the checklist is measured against **2.1.251** (re-audit 2026-08-30, run early at the
  user's request; checklist header `<details>` block has the findings). Its one row, **9.11**, was
  BUILT the same day (user's yes): `set_model` carries a response callback, an error reverts the
  persisted model and pushes `__model_rejected` → webview `showModel` (display-only half of
  `setModel`) + error block. Files: `cli/ClaudeCli.kt`, `ClaudeSessionService.kt` (`revertModel`),
  `webview/js/30-menus.js`, `70-events.js`, fixture 68. Verified: harness **586/0**, `./gradlew
  test` 134/0, live with a real deny hook. Also measured: the `Set model to …` echo after an SDK
  `set_model` is gone at 2.1.251 ONLY before the first turn (a post-turn switch still draws it —
  corrected by the user's hands-on test).
  Docs touched: `feature-checklist`, `ide-mcp-protocol`, `slash-commands`.

## Released — 0.12.1 (2026-08-30)
**0.12.1 is the shipped version** (tag `v0.12.1`, commit `793639d`): links → system browser (+ bare
URL autolink), model chip follows a refused `set_model` (9.11), effort pill slider, side-question
hint. Verifier 7/7 Compatible, GitHub asset byte-identical, feed live, `marketplace-upload` green
(run 33295856635), Marketplace **Approved** the same day (user's screenshot: JetBrains' verifier
Compatible 2025.3 → 2026.2.2 rc, IDE run with no issues). 0.12.0 (2026-08-29, `0e1af47`) before it:
/btw, files-changed review, tweak-travel, stop-task, settings schema, /clear removed. Change notes
carry exactly the LAST THREE versions (0.12.1 / 0.12.0 / 0.11.1) + the GitHub releases link.

## Open work — ids verified against `docs/feature-checklist.md`
- No open checklist rows. Wants: backlog § Next up (Worktrees bundle 14.1+14.3, 15.5 debugger
  tools) and § Deferred (conversation tabs + 8.8/8.10).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **586** (fixtures to 68); `./gradlew test` **134**.
- Sandbox **PhpStorm 2024.2.6**; start (from `plugin/`; background tasks start in the REPO ROOT):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` DETACHES** — gradle's exit code says
  nothing; `pgrep -f 'idea.system.pat[h]'`; kill by pid, wait for CDP to vanish. Claude may start
  and kill the sandbox on its own (user, 2026-08-29). Never run the harness and a CDP injection
  concurrently — fixtures `__clear` the log and the injection vanishes.
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
- [x] `reference/anthropic-claude-code/` (moved from `vscode/` 2026-08-29) re-extracted to **2.1.251** on 2026-08-29 (from 2.1.241; 2.1.250 was still on disk —
      extension.js +14.5 KB, webview/index.js +2.4 KB between the two). **Re-audit deferred by the
      user: wait a few CLI versions past 2.1.250 before the next checklist re-audit.**
- [x] Gist `b2d033439ba4ca5bcd018f4fe5eef773` verified identical to `.claude/skills/context/SKILL.md`
      on 2026-08-29 (push again after any SKILL.md edit: `gh gist edit <id> -f SKILL.md <path>`).
- [x] 9.11 built, verified, hand-tested and committed 2026-08-30.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251):
      `github.com/SchemaStore/schemastore/commits/master/src/schemas/json/claude-code-settings.json`.
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
