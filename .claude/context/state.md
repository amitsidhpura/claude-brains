# State

## Current focus
**2026-09-09 (twelfth session, Linux): two markdown-renderer fixes, hand-tested, committed.**
0.13.0 stays the released version (2026-09-05, Marketplace-Approved). Unreleased on `main`:
- **Code fences of 3+ backticks** (2026-09-08): a ````markdown fence used to print the literal
  line `B0 \`` and drop the block (checklist 1.11 fold; fixture 85).
- **CommonMark list parser** (2026-09-09): numbered lists no longer restart at `1.` after a blank
  line, a wrapped line, a nested bullet or an indented fence; `<ol start>`; `1. 1. 1.` counts up;
  loose items keep `<p>` (`.blk li > p`). Checklist 1.10 fold; fixture 86 (one step is the fix's
  own plan text verbatim). Hand-tested by the user live + replay + plan card, wire shapes
  confirmed by key. decisions.md 2026-09-09.
CLI on this box is **2.1.263**; the checklist header still says 2.1.260 — the re-audit is the
standing next ask. Release only when asked (these two fixes would be a **patch**, 0.13.1).

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
- `python3 tools/live_harness.py` baseline **829** (fixtures to **86**); `./gradlew test` **163**.
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

## Next steps
- [x] Fence fix (2026-09-08) and list parser (2026-09-09) built, harness 829/0, test 163/0,
      hand-tested; committed with this save on the user's ask.
- [ ] **Re-audit at 2.1.263** (runbook § Re-audit, step 8: header References/date, §2's "unchanged
      in <ver>", the [NEW] legend — the ten 0.13.0 rows lose [NEW] once a release carries them).
      Run the `update_settings` allowlist binary-grep watch during it (backlog § Next up).
- [ ] Renderer follow-ups, all in backlog § Housekeeping, all pre-existing: indent-only code
      blocks render as paragraphs (the model used one under a list item 2026-09-09); an asterisk
      inside inline code italicises across it; mid-line fence placeholder leak; `~~~` fences.
- [ ] **Model chip / menu mismatch** — parked; capture the chip title id when it recurs.
- [ ] **Waiting on the user**: Windows DevTools fold diagnostic + Help→About.
- [ ] Testing repo carries hand-test commits (`bf46eb2 banner test` and the 2026-09-09 list-test
      turns' files) — the user's call whether to reset.
- [ ] SchemaStore watch (no action until it syncs past 2.1.251).
- [ ] **User errands**: Windows `./gradlew test` + VFS click check; Marketplace screenshots 01+03,
      04+05 to `plugins.jetbrains.com/plugin/33274`; check the listing shows the 0.13.0 notes.

## Known gaps (deliberately left)
- **The Thinking switch is INERT on Fable** — measured 2026-08-26, "document only" by decision.
- **Do not gate UI on roster capability flags** (9.10 ➖); only `supportsFastMode` is gated.
- Typing `/model` or `/clear` in the composer is refused; the chip / header New button are the
  only surfaces. No keyboard-only new conversation; no shortcuts on any card.
- Banner frames, task frames and withdrawn asks are live-only; replay draws the CLI's own auto-deny
  result. Decided Bash cards vanish on replay; a Bash card's reject note and edited command are
  live-only. A note typed before Accept on an ordinary card is dropped.
- Markdown: a bullet-character change does not split a list (forgiving on purpose); the offline
  highlighter colours its keyword set inside EVERY language, prose fences included (1.11, by design).
- plugin.xml "Not there yet" list unchanged: dark UI only, one conversation, no auto-context.

## Which machine — check FIRST, both are real
2026-08-26 → 2026-09-09 sessions ran on **Linux** (`/home/syncroze/Sites/claude-brains`).
Paths for both boxes in overview.md § External references. Windows still owes the CRLF splice
check and the fold diagnostic.
