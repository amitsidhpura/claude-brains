# State

## Current focus
**2026-09-13 (fourteenth session, Linux): five units of work done and hand-tested, committed and
pushed at the end of the session (two commits: features + docs, then the context save).**
Released version is still **0.13.1** (tag `v0.13.1`, commit `8e19916`). Unreleased on `main` since:
1. **Re-audit 2.1.260 → 2.1.270** (checklist header, `<details>` block, twelve row folds, § 16 counts,
   nine shipped [NEW] tags dropped; `docs/slash-commands.md` `/output-style`; protocol doc § 11/12
   facts) — and **runbook step 3b**: `reference/claude-code-log` (public CHANGELOG clone) read for
   LEADS only before the diffs.
2. **1.29 Bash edit diff** — `appendBashDiff` (`webview/js/50-blocks.js`) draws one resolved edit
   card per file under the Bash IN/OUT box, live (`75-retraction.js`, reads `ev.tool_use_result ||
   ev.toolUseResult`) and replay (`SessionStore.applyToolResult` → `bashDiff`, `55-replay.js`).
   **The CLI gates the sidecar**: auto/bypassPermissions by default, any mode with user
   `bashEditDiffEnabled: true` — no panel toggle (terminal's half); release notes must say so.
3. **6.5 on the editor-tab menu** — `EditorTabPopupMenu` first + `MentionAction.files()` falls back
   to `VIRTUAL_FILE`.
4. **5.6 plan-comment draft settles on the next selection** (commit if typed, cancel if empty;
   `settleDraft` in `85-cards.js`), placeholder "Comment on this part — Enter adds, Esc cancels".
5. **3.7 reject-note field ABOVE the buttons** on ordinary cards (plan-card layout; the inline field
   had stretched the buttons), placeholder "Tell Claude what to do instead · applies to Reject".
Verified: `./gradlew test` 164/0; harness **870**/0 (fixtures to 87); hand tests MT-1.29, MT-2.15,
MT-6.9, MT-6.10 all passed with the user (§ 17). Next release = **0.14.0 (minor)**, only on the
user's ask. CLI on this box **2.1.270**; extension extraction re-synced to 2.1.270.
The sandbox PhpStorm was left RUNNING on the final build (CDP 9222, mode chip Manual).
## Open investigations
- **Model chip says "Fable (1M)" while the menu checks NO row** (user screenshot 2026-09-05;
  user: leave for future). NOT reproduced. Diagnostic next time: DevTools
  `document.getElementById('modelChip').title` = the exact id. Details: backlog § Next up.
- **Fold-verdict report NOT reproduced** (user's Windows screenshot 2026-09-04). WAITING on the
  user running the DevTools diagnostic on the Windows box + Help→About. Do not guess-fix.
- **Title tooltips never show in the panel on Linux JCEF** (2026-09-05) — backlog § Next up.
- **The sandbox PhpStorm closed cleanly on its own TWICE on 2026-09-09** (7 min and 5.5 min after
  launch, `runIde` returned BUILD SUCCESSFUL, "Save settings failed / ComposerSettings" is the
  only log line at the moment of exit). Unknown whether the user closed the window. If it recurs
  unattended, it is a real finding (gotchas § Testing).

## Testing — the standing setup
- `python3 tools/live_harness.py` baseline **870** (fixtures to **87**); `./gradlew test` **164**.
- Sandbox **PhpStorm 2024.2.6**; start (from `plugin/`; background tasks start in the REPO ROOT):
  `cd plugin && ./gradlew runIde -PskipVerifierIdes -PjcefDebugPort=9222
  --args="$HOME/Sites/claude-brains-testing"`. **`runIde` blocks until the IDE exits** and then
  says BUILD SUCCESSFUL (a kill reads as exit 1); `pgrep -f 'idea.system.pat[h]'`; kill by pid,
  wait for CDP to vanish (`until ! ss -ltn | grep -q ':9222'`). Claude may start and kill the
  sandbox on its own (user, 2026-08-29). Never run the harness and a CDP injection concurrently.
  **`pkill -f`/`pgrep -f` with a pattern that also matches your own shell kills the shell (exit
  144) — bracket one char.** A running sandbox does NOT block `test`/`buildPlugin`/`verifyPlugin`.
- **Free negative control**: write the fixture while the sandbox still runs the PRE-change build,
  run it, see the discriminating asserts fail, THEN edit sources and restart. Every fixture assert
  must be null-safe. Control builds restore the WHOLE `plugin/src/main/resources/webview/`.
  Record the control's actual readings in the fixture `provenance` (fixtures 69, 85, 86).
- **Renderer-only changes can be prototyped in node first**: wrap `20-markdown.js` with stubs for
  `esc`/`SVG_COPY`/`log` and call `renderMd` on the fixture texts (scratch `proto.js`,
  2026-09-09) — catches parser mistakes before a sandbox restart.
- **Background chains that wait on the CDP port can miss it** — one never fired on 2026-09-09;
  wait on a LOG LINE or start the harness from the port-up task itself (gotchas § Testing). A
  chain ending in `grep -c` reports exit 1 when the count is 0 (a pass that looks like a failure).
- **Transcript-by-key scripts must bound the search to ONE turn** (prompt → next user text frame);
  "longest assistant text after the prompt" picked the same list for every prompt (2026-09-09).
- **The harness has no mid-frame JS hook**: a click between frames goes in the NEXT step's `setup`.
  A Bash tool line always has an IN row — count OUT rows by their `.io-k` text.
- **Stdio probes**: `tools/probe_stdio.py` (gotchas § Testing). A plain run writes a real transcript
  under `~/.claude/projects/-tmp-…/` — delete afterwards (14 old leftovers untouched on purpose).
- **PATH-dependent sandbox launches need `./gradlew --stop` first**; the full harness baseline
  only counts against a real-CLI sandbox (gotchas § Testing).
- Real end-to-end turns over CDP: `sendTurn('<prompt>', [])`; poll `!!document.querySelector('#log
  .generating')` to `false`; `tools/cdp.py -f probe.js`; `--screenshot`. Then read the wire shape
  BY KEY from `~/.claude/projects/-home-syncroze-Sites-claude-brains-testing/` before calling a
  render "verified" — the model picks its own fence length / indentation.
- Fixture ids from page-lifetime counters (`sq1…`) must be read from the bridge tape; a
  panel-supplied slash entry (`CMD_LOCAL`) changes every fixture that COUNTS slash rows.
- **Releases**: `docs/release.md` end to end, `./gradlew test buildPlugin verifyPlugin` as ONE
  background run (~45 s warm, 8 verdict files since 263 joined the ladder 2026-09-09), then the
  step-6 gate with the FULL notes before commit/tag/push (gotchas § Build).

## Next steps
- [x] Re-audit at 2.1.270; 1.29, 6.5-tab, 5.6-draft, 3.7-layout built and hand-tested (2026-09-13).
- [ ] **Release 0.14.0 when the user asks** (`docs/release.md`; notes: Bash edit diff + its
      `bashEditDiffEnabled` gate, tab-menu mention, comment-draft settling, note-field layout).
- [ ] Renderer follow-ups, all in backlog § Housekeeping, all pre-existing and named in the 0.13.1
      release notes as known: indent-only code blocks render as paragraphs; an asterisk inside
      inline code italicises across it; mid-line fence placeholder leak; `~~~` fences.
- [ ] **Model chip / menu mismatch** — parked; capture the chip title id when it recurs.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About; the Windows CRLF
      splice check (VS Code fixed its own CRLF diff-accept at 2.1.267 — checklist 3.2 fold); Windows
      look of the 1.29 card header path.
- [ ] Testing repo carries hand-test leftovers (`hand_test.txt`, `hand_test_2.txt`, earlier modified
      files, the 2026-09-13 sandbox sessions) — the user's call whether to reset.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251).
- [ ] **User errands**: Windows `./gradlew test` + VFS click check; Marketplace screenshots 01+03,
      04+05 to `plugins.jetbrains.com/plugin/33274`; check the listing's description shows the
      plugin.xml text after the 0.13.1 upload.

## Known gaps (deliberately left)
- **The Thinking switch is INERT on Fable** — measured 2026-08-26, "document only" by decision.
- **Do not gate UI on roster capability flags** (9.10 ➖); only `supportsFastMode` is gated.
- Typing `/model` or `/clear` in the composer is refused; the chip / header New button are the
  only surfaces. No keyboard-only new conversation; no shortcuts on any card.
- Banner frames, task frames and withdrawn asks are live-only; replay draws the CLI's own auto-deny
  result. Decided Bash cards vanish on replay; a Bash card's reject note and edited command are
  live-only. A note typed before Accept on an ordinary card is dropped (the field says
  "applies to Reject" since 2026-09-13).
- Markdown: a bullet-character change does not split a list (forgiving on purpose); the offline
  highlighter colours its keyword set inside EVERY language, prose fences included (1.11, by design).
- plugin.xml "Not there yet" list unchanged: dark UI only, one conversation, no auto-context.

## Which machine — check FIRST, both are real
2026-08-26 → 2026-09-13 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check and the fold diagnostic.
