# State

## Current focus
**2026-10-28 (thirty-third session, Linux): saved messages built, hand-tested by the user in the
sandbox, committed (`fc0dd74`); Marketplace screenshots reshot + three new (`d86a9c7`); pushed.**
- 7.11 Save as command: `.msg-save` hover control on a sent prompt (`addUserMessage`,
  `js/60-composer.js`) → `ui/SaveCommandDialog.kt` → `session/SavedCommands.kt` writes
  `~/.claude/commands/<name>.md` (`$CLAUDE_CONFIG_DIR` honoured) or `<project>/.claude/commands/`.
- 7.12 Recent first: `renderSlash` sorts score, then last-sent (`js/65-slash.js`); `cmdUsed` →
  `ClaudeSessionService.noteCommandUsed` → property `claudeCode.commandRecents`, seeded `__cmdRecent`.
- CLI 2.1.311 watches `~/.claude/commands` too — no `/reload-skills` needed (corrected 2026-10-28).
- Screenshots: `design/marketplace/01…08`, `tools/marketplace_shots.py` (420px panels, 2.1.293 roster).
- Fixture 105; harness **1264/1264**, `./gradlew test` **183**.

**Released version: 0.16.0 (2026-10-11), Approved on the Marketplace.** Unreleased since `v0.16.0`:
copy image (`d7466db`), Bookmarks (`872c4d9`) and saved messages (`fc0dd74`) — three features —
plus the 2026-10-26 polish (wavy date line, steady ask preview, menu hover) → **0.17.0** if the user
asks for a release.
A release starts only when the user asks. **Pending in the real PhpStorm:** none of the
post-0.16.0 work is installed there; Windows untested for copy image and bookmarks.

**[DECIDE] rows:** none.

## Open investigations
- **Where the user's Fable default comes from** — still unknown. A bare `initialize` on 2.1.296
  resolved `default` → `claude-sonnet-5-5`; the 2.1.270 binary said `claude-opus-5[1m]`.
- **Model chip "Fable (1M)" with no ✓ row** (parked, backlog § Next up); fixture 97 pins the ✓ for a
  persisted tagged value.
- **Roster per PROCESS**: 2.1.293 terminal probe 11 rows (no `fable`), 2.1.296 13 WITH it — unproven.
- **4.10 has never met a real frame** — `window.__dialogSeen` + console warning watch it.
- **Fold-verdict report NOT reproduced** (Windows, 2026-09-04) — waiting on the user's diagnostic.
- **Title tooltips never show on Linux JCEF** — backlog § Next up.
- **The user's preview box looked unfolded** (10 lines, real IDE screenshot 2026-10-26) while the
  sandbox folds previews to 3 lines — not investigated; the steady-height fix holds either way.

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **1264** (fixtures to **105**, 1264/1264 on 2026-10-28);
  `./gradlew test` **183**. A new bridge kind breaks fixtures that tape and count the bridge;
  every assert must be null-safe or a throw aborts the run with the bridge stubbed (gotchas § Testing).
- Sandbox PhpStorm 2024.2.6: `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"` (background, from the repo root). Stop it with
  `pgrep -f 'transformed/PhpStorm-2024.2.[6]' | xargs -r kill` (never `pgrep -x claude`), wait for
  `:9222` to vanish with `ss -ltn` in a bounded `until` loop. Never run the harness and a CDP
  injection concurrently. **Resource AND Kotlin changes need a restart** (packaged at launch).
  The sandbox dies with the Claude Code process that launched it — `:9222` closed at `load` is normal.
- **Free negative control**: with the sandbox on the OLD build, write the fixture, run it, then edit
  and restart. Pin list LENGTHS; `JSON.stringify(count)` is a STRING (gotchas § Testing).
- **A fresh IDE needs a minute before the full harness** (its own `__project` frame lands mid-fixture).
- **Real pointer / keys over CDP** (Input.dispatchMouseEvent / Input.dispatchKeyEvent on the panel
  target's websocket — `tools/cdp.py` only evaluates): `Input.insertText` fires NO keydown; a row
  scrolled out of a popup still reports a rect (gotchas § Testing).
- **Live hand tests at haiku prices**: `bridge({kind:'new'})`, `bridge({kind:'model', model:'haiku'})`,
  `sendTurn(prompt, [])` (SENDS a real prompt; `addUserMessage` only draws). Clipboard truth:
  `xclip -selection clipboard -o` (images: `-t TARGETS`, `-t image/png`).
- **Shell on this box**: `grep` is `ugrep`, `ls` is `eza`; a bare `sleep` is blocked (bounded
  `until` loop or `python3 -c 'import time;time.sleep(n)'`); under zsh `for s in "a b"` does not
  word-split `$s`.
- **Stdio probes that run a turn: scratch `CLAUDE_CONFIG_DIR`** (recipe gotchas § Testing); delete
  the dir in a `finally` and CHECK it is gone.
- **The harness is NOT side-effect free on the sandbox CLI** (fixture 55 `setModel`) — start a
  fresh CLI (`bridge({kind:'new'})`) before a live end-to-end, and after any run restore
  `window.__bridge` from `__realBridge101/96/84` if a run aborted mid-fixture.
- **Real-panel render for review**: `window.__gallery()` + `tg('<menu>')`, then `--screenshot`.
  Hover-only controls: inject a `!important` opacity/display style for the shot.
- **Releases**: `docs/release.md` end to end, `./gradlew test buildPlugin verifyPlugin` as ONE
  background run, the step-6 gate with the FULL notes before commit/tag/push (gotchas § Build).

## Next steps
- [x] 7.11 Save as command + 7.12 Recent first — fixture 105, hand-tested (4 steps), committed +
      pushed 2026-10-28 (`fc0dd74`). Marketplace screenshots reshot + 06/07/08 (`d86a9c7`).
- [ ] Release 0.17.0 when the user asks (notes: copy image, Bookmarks, saved messages (Save as
      command + recent-first /) as features; steady preview box, menu one-highlight / no hover
      steal, wavy date line as polish; the whole-message uuid fix).
- [ ] After that release: upload `design/marketplace/01…08.png` by hand on the Marketplace edit
      page (the zip carries no screenshots) — eight now, three of them new.
- [ ] WINDOWS: Save as command (`$CLAUDE_CONFIG_DIR`/`%USERPROFILE%\.claude\commands`, dialog look).
- [ ] WINDOWS: copy image (paste once into another app) and Bookmarks (config-path store file).
- [ ] Compaction vs bookmarks: does a compacted transcript keep the old assistant records? If not,
      a pre-compaction bookmark reads "can't be found" (by design, matches VS Code) — measure once.
- [ ] Confirm in the real PhpStorm that it took 0.16.0 and the Marketplace Overview still reads
      plugin.xml's description (user errand, standing).
- [ ] When a real `auto_mode_server_fallback` frame lands, compare with fixture 94; fix the card if
      they differ.
- [ ] 15.4 Save row: drive the native dialog by hand once.
- [ ] Leads from the audit, probe-first (backlog § Next up): 2.3, 2.10, 8.3, 9.2,
      `claude_code_version`, 1.9.
- [ ] Renderer follow-ups in backlog § Housekeeping.
- [ ] **Model chip / menu mismatch** — parked; on a recurrence capture chip title + persisted value.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About; Windows CRLF splice
      check; Windows look of the 1.29 card header; Windows `./gradlew test` + VFS click check.
- [ ] Testing repo leftovers (`hand test/…`, `plain-control.txt`, sandbox transcripts incl. the
      harness's "Session status inquiry" conversations from fixture 04's old real sendTurn) — the
      user's call whether to reset.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251).

## Known gaps (deliberately left)
- **Copy image** is on tool-returned images only; an image attached to the user's own message is
  copied from the popup its chip opens (user agreed 2026-10-26 — no chip button, a right-click item
  was offered if wanted).
- **Saved messages** save the text only (no images / selection pill); names have no `sub:` folders;
  the dialog validates on Save, not live (`initValidation()` offered, not taken); no in-panel edit
  or delete of a saved command — the file is the terminal's.
- **Bookmarks** are per text BLOCK (a text → tool → text reply is several records), no typed
  `/bookmarks`, the card is above the composer rather than VS Code's side panel (user pick).
  "Show in conversation" hides when the block is above the replayed window (no load-more jump).
- **Ask preview box** holds the tallest option's (fold-capped) preview, so a short preview sits in
  empty space; opening a folded preview by click still grows it.
- **Menus**: after a keystroke the pointer selects nothing until it moves; the background roster
  (`.nosel`) has no hover paint at all.
- **15.4**: the Monitor row reads `local_bash`; no typed `/copy` / `/export`; the export is uncapped.
- **11.7's pane lives with its roster row**; a sub-agent's row answers "Output is not available."
  on the first ask. Sub-agents across an interrupt unmeasured.
- **4.10 is live-only and schema-built**; the Fable overage consent (9.7) stays deferred.
- **Timestamps**: tool loops later in a turn get no stamp; the history list relabels only on open.
- **Before the first turn nothing on the wire names a settings `model` override** (9.1 gap).
- **The Thinking switch is INERT on Fable**. **Do not gate UI on roster capability flags** (9.10).
- **No 1M switch** (9.9, retired): the gauge seeds 200K for an untagged pick until the first result.
- Typing `/model` or `/clear` is refused; the chip / New button are the surfaces.
- Banner frames, task frames, withdrawn asks and host dialogs are live-only.
- Markdown: a bullet-character change does not split a list; the offline highlighter colours its
  keyword set inside EVERY language (1.11).
- plugin.xml "Not there yet" list: dark UI only, one conversation, selection-not-whole-file.

## Which machine — check FIRST, both are real
2026-08-26 → 2026-10-28 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`); the user's
screenshots this session came from the Windows real IDE (`D:\sites\…` paths). Paths for both boxes in
overview.md § External references.
