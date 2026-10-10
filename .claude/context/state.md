# State

## Current focus
**2026-10-10 (twenty-first session, Linux): the 2.1.270 → 2.1.296 re-audit ran end to end and
rows 1.30 + 1.31 were built the same day; the user's "commit and push" closes the session.**
1. **Re-audit** (runbook procedure, 26 versions): extension 2.1.296 vs the 2.1.270 extraction, CLI
   2.1.296 vs the 2.1.270 vsix binary (downloaded — `versions/` held only 294–296), CHANGELOG
   2.1.271–296 for leads. Everything measured; the audit block + rows are in
   `docs/feature-checklist.md`, the roster in `docs/slash-commands.md`, wire facts in
   `docs/ide-mcp-protocol.md`. Headline facts: control subtypes 103 → 155 (28 are the Mods `ui_*`
   family, surface `desktop|mobile|vscode` — no JetBrains); roster 13 rows with NO `[1m]` value and
   `set_model "opus[1m]"` accepted while `contextWindow` is 1M with or without the tag (9.9);
   `request_user_dialog` kind `auto_mode_server_fallback` reaches only clients declaring it in
   `initialize.supportedDialogKinds` (4.10); `update_settings` allowlist now
   `{localSettings:[outputStyle], userSettings:[effortLevel]}` (13.3); `/focus [on|off]` joined the
   roster, every built-in row carries `builtin:true`, synced skills are short names + `aliases`.
   **Four [DECIDE] rows remain: 4.10, 9.9, 11.7, 15.4** (At a glance: 96 ✅ · 3 ⬜ · 46 ➖).
2. **1.30 message timestamps + 1.31 option previews & answered summary** — built, fixtures 92/93,
   harness **952/0**, `./gradlew test` **169**, hand-tested live AND on replay in the sandbox (a
   real 2.1.296 `can_use_tool` carried `preview` on every option). User's choices: prompt + first
   reply text, always on; full card kept + summary rows (picked from a side-by-side render).
   Decisions 2026-10-10; row evidence on 1.30 / 1.31.

**Pending in the real PhpStorm: SEVEN fixes** (PATH lookup 2026-09-27, Default chip + thinking trim
2026-10-08, bold-wrapping-italic + spaced @-mentions 2026-10-10, now 1.30 + 1.31). The
`plugin/build/distributions/claude-brains-0.14.0.zip` built 11:14 PREDATES 1.30/1.31 — rebuild
(`buildPlugin`) for a disk install, or a 0.14.1 release (user's call; `verifyPlugin` every release).

**Cleanup owed, the user's (the session's permission classifier refused both):** delete the three
audit-probe transcripts `982ad0ee-aeab-4882-b005-5e34069eff71`, `60d9bd11-f732-43af-854f-de084163b0ca`,
`830d916b-f6a0-49b5-a4c4-793bba06622e` under `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/`
and set that project's `lastSessionId` in `~/.claude.json` back to
`8c92f59b-0b22-4152-95b9-1c6d4260ecab`. The hand-test session ("AskUserQuestion scratch file preview",
one Haiku turn, no file created) may stay. Also: `reference/anthropic-claude-code/` is still the
2.1.270 extraction — rsync it to 2.1.296 (runbook step 2) before the next audit.
The sandbox PhpStorm was left RUNNING on the final build (CDP 9222; dies with the Claude Code
process that launched it).

## Open investigations
- **Where the user's Fable default comes from** — still unknown. A bare `initialize` on 2.1.296
  (this Max account) resolved `default` → `claude-sonnet-5-5`; the 2.1.270 binary said
  `claude-opus-5[1m]`. The panel shows what the CLI serves; it does not change what runs.
- **Model chip "Fable (1M)" with no ✓ row** (parked) — the 9.9 measurement (tagless roster, tag
  accepted and echoed) supports the "persisted `fable[1m]` matches no tagless row" candidate.
- **Roster per PROCESS**: the 2.1.293 terminal probe listed 11 rows (no `fable` alias); the 2.1.296
  terminal probe listed 13 WITH it — a flags-fetch timing lead, unproven.
- **Fold-verdict report NOT reproduced** (Windows, 2026-09-04) — waiting on the user's diagnostic.
- **Title tooltips never show on Linux JCEF** — backlog § Next up.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **952** (fixtures to **93**); `./gradlew test` **169**.
- Sandbox PhpStorm 2024.2.6: `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"` (background, from the repo root). `runIde` blocks
  until the IDE exits. Stop it with `pkill -f 'transformed/PhpStorm-2024.2.[6]'` (never
  `pgrep -x claude`; the real snap PhpStorm's CLI is a sibling — kill the sandbox's by PARENT
  cmdline), wait for `:9222` to vanish (bounded `until` loop). Never run the harness and a CDP
  injection concurrently. A running sandbox does NOT block `test`/`buildPlugin`.
- **Shell on this box** (gotchas § Testing): `grep` is `ugrep` (GNU `\{n\}` fails → use
  `/usr/bin/grep`), `ls` is `eza` (hung a chain once), `comm` needs `LC_ALL=C sort -u`; Bash output
  past ~30 KB is persisted to a file with a 2 KB preview.
- **Probes that write transcripts cannot be cleaned by the session** — the classifier refuses the
  delete and the `lastSessionId` restore; list the ids for the user (gotchas § Testing).
- **The harness is NOT side-effect free on the sandbox CLI** (fixture 55 `setModel`) — start a
  fresh CLI (`bridge({kind:'new'})`) before a live end-to-end. Live turns over CDP:
  `sendTurn('<prompt>', [])` SENDS a real prompt; `addUserMessage(text, [], ts)` only draws.
- **Negative control is free when the fixture is written first** and run on the sandbox still
  serving the old build — record the readings in `provenance` (fixtures 92/93 did).
- **Stdio probes**: `initialize` + optional turn via a 40-line python (scratchpad `init_probe.py` /
  `onem_probe.py` shape — stdin JSON lines, read until `control_response` / `result`); a bare
  initialize writes no transcript, a turn does.
- **Releases**: `docs/release.md` end to end, `./gradlew test buildPlugin verifyPlugin` as ONE
  background run, the step-6 gate with the FULL notes before commit/tag/push (gotchas § Build).
  A release starts only when the user asks (conventions).

## Next steps
- [x] Re-audit 2.1.270 → 2.1.296 — done 2026-10-10 (checklist audit block).
- [x] 1.30 message timestamps — built 2026-10-10 (fixture 93).
- [x] 1.31 question option previews + answered summary — built 2026-10-10 (fixture 92).
- [ ] **Decide the four open rows**: 4.10 (declare `auto_mode_server_fallback` + card, [MD],
      untriggerable on demand), 9.9 (retire / hide / keep the inert 1M switch), 11.7 (task output,
      `get_task_output` success shape needs a live task), 15.4 (export + copy response, [XS]).
- [ ] Get all SEVEN fixes into the real PhpStorm — rebuild the zip (the 0.14.0 one predates
      1.30/1.31) or cut 0.14.1 (user's call).
- [ ] Cleanup owed (above): three probe transcripts + `lastSessionId`; rsync the extraction to
      2.1.296.
- [ ] Check the listing's Overview still reads plugin.xml's description text (user errand).
- [ ] Leads from the audit, probe-first (backlog § Next up): on-demand diagnostics (2.3), the
      autosave 10-minute stall (2.10), `session_title_changed` (8.3), Ultracode toggle and
      `effortLevel` persistence (9.2), `claude_code_version` for the out-of-date hint.
- [ ] Renderer follow-ups in backlog § Housekeeping (indent-only code blocks; `*` inside inline
      code; mid-line fence placeholder leak; `~~~` fences).
- [ ] **Model chip / menu mismatch** — parked; capture chip title + persisted value vs roster.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About; Windows CRLF splice
      check; Windows look of the 1.29 card header; Windows `./gradlew test` + VFS click check.
- [ ] Testing repo hand-test leftovers (`hand test/…`, `plain-control.txt`, transcripts) — the
      user's call whether to reset.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251).

## Known gaps (deliberately left)
- **Timestamps**: day labels are relative ("Today"/"Yesterday"), so a page left open past
  midnight reads stale until a clear/resume — same property as the history list. Tool loops later
  in a turn get no stamp; local-command echo turns get no reply stamp. No on/off switch.
- **Before the first turn nothing on the wire names a settings `model` override** (9.1 gap).
- **The Thinking switch is INERT on Fable** — "document only". **Do not gate UI on roster
  capability flags** (9.10); only `supportsFastMode` is gated — and the `default` row lost it at
  2.1.296 (Sonnet 5.5), so the switch is disabled on Default by design.
- **The 1M switch is inert on 2.1.296** (9.9 [DECIDE]) — reads OFF on every row.
- The picker shows the CLI's whole roster, pinned previous versions included (eight at 2.1.296).
- Typing `/model` or `/clear` is refused; the chip / New button are the surfaces. No keyboard-only
  new conversation; no shortcuts on any card.
- Banner frames, task frames and withdrawn asks are live-only; replay draws the CLI's own
  auto-deny result. A note typed before Accept on an ordinary card is dropped.
- Markdown: a bullet-character change does not split a list; the offline highlighter colours its
  keyword set inside EVERY language (1.11).
- plugin.xml "Not there yet" list unchanged: dark UI only, one conversation, no auto-context.

## Which machine — check FIRST, both are real
2026-08-26 → 2026-10-10 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check and the fold diagnostic. The PATH trap is Linux/macOS-shaped; Windows has no shell PATH layer.
