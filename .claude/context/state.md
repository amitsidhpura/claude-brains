# State

## Current focus
**2026-08-29 (twenty-fourth session): competitor audit closed, reference folder rearranged.** No
code changed. `vscode/` is now `reference/anthropic-claude-code/` (2.1.251; `/reference/` gitignored);
a GitHub Copilot Chat 0.63.0 audit (report lived in the session scratchpad only, by request) yielded
ONE candidate — backlog § Next up: terminal last command/output as attachable context, probe the
JetBrains terminal API before it becomes a row. The user found Copilot "bloat"; decisions.md
2026-08-29 says what not to re-propose from it. Docs reference no third-party client other than
`anthropic-claude-code`.
**Unreleased on `main` since 0.12.0** — three post-release fixes, all verified live in the sandbox and
by harness 575/0 + `./gradlew test` 134/0:
- **External links open in the system browser** (`webview/js/20-markdown.js` no `target=_blank` +
  bare-URL autolink; `00-core.js` document `click`/`auxclick` delegate → `browse` bridge frame;
  `ui/ChatPanel.kt` `"browse"` → `BrowserUtil.browse`, plus `onBeforePopup` AND `onBeforeBrowse`
  guards). Root cause: OSR JCEF — a `_blank` popup had no surface (blank PhpStorm window), and a
  middle-click navigated the PANEL itself. Fixture 67, two negative controls recorded in it. User
  hand-tested left / middle / Ctrl+click → Chrome, panel stays.
- **Effort selector is a pill slider** (`chat.css` `.effort` block): the `.tgl` idiom at five
  fixed 12px stops, accent fill from `--ef-fill` via `:has()`, 2px knob inset, fill = knob right
  + 2. CSS only; fixtures 51/55/58 unchanged. Four user rounds of spacing polish — decisions.md.
- Side-question placeholder now says "(Ctrl+Enter to send, Enter for newline)" like the composer.
- Next release (0.12.1 or 0.13.0) carries these; nothing else is pending for it.
- 8.11 side question (`/btw`, `webview/js/67-side.js`, fixture 66) — UNMEASURED corners:
  `synthetic:true`, `refusal_fallback`, the CLI's "Side question cancelled" / "Session is shutting
  down" errors render verbatim if they ever arrive.
- Checklist rules still in force: `**id** mark [effort] **Name** — gist; facts`; the **At a glance**
  block is hand-maintained (recount with `awk` at every change); `<details>` only in the re-audit
  paragraphs and §17 groups. Checklist: 82 ✅ · 46 ➖ (128 rows), all 17 headings ✅.
- Do not re-propose: an effort chip suffix (2026-08-26); non-red destructive hovers (2026-08-29);
  Claude-side rewind/checkpoints or host git actions (2026-08-29, decisions.md); an "Effort (High)"
  bracketed label or a blue track on the effort slider (2026-08-29).
  Copilot-derived features other than terminal-output context (2026-08-29, decisions.md).
- Audit state: the checklist is measured against **2.1.250**; 2.1.251 is only the EXTRACTION in
  `reference/anthropic-claude-code/` (a diff base) — not audited, re-audit deferred by the user.

## Released — 0.12.0 (2026-08-29)
**0.12.0 is the shipped version** (tag `v0.12.0`, commit `0e1af47`): /btw, files-changed review,
tweak-travel, stop-task, settings schema, 1.21/1.23/1.24, /clear removed. Verifier 7/7 Compatible,
GitHub asset byte-identical, feed live, `marketplace-upload` green (run 33257914722); Marketplace
**Approved** the same day (user's screenshot: JetBrains' verifier Compatible 2025.3 → 2026.2.2 rc, plus an
IDE-run check with no issues).
Unreleased on `main` since: the three fixes above (this session). Change notes now carry exactly the LAST THREE versions + a GitHub
releases link (rule in build.gradle.kts kdoc and release.md 1b).

## Open work — ids verified against `docs/feature-checklist.md`
- No open checklist rows. Wants: backlog § Next up (Worktrees bundle 14.1+14.3, 15.5 debugger
  tools) and § Deferred (conversation tabs + 8.8/8.10).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **575** (fixtures to 67); `./gradlew test` **134**.
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
